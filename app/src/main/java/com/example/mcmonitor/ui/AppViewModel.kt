package com.example.mcmonitor.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mcmonitor.data.SettingsRepository
import com.example.mcmonitor.data.ServerRepository
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mcmonitor.data.PasswordVault
import com.example.mcmonitor.data.ServerEntry
import com.example.mcmonitor.data.StatusResult
import com.example.mcmonitor.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 列表里一行的 UI 状态：收藏信息 + 最近一次查询结果 + 是否正在查询
data class ServerUiItem(
    val entry: ServerEntry,
    val status: StatusResult?,
    val loading: Boolean,
)

// 整个应用共享的 UI 状态
data class AppUiState(
    val items: List<ServerUiItem> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoRefresh: Boolean = true,
    val refreshing: Boolean = false, // 是否有任何查询在进行
)

// 继承 AndroidViewModel 可以直接拿到 Application，省去 ViewModelProvider.Factory 样板代码
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val api = ServerRepository()

    // 查询结果缓存：服务器 id -> 最新状态；设置/收藏走 DataStore，这种临时数据放内存即可
    private val statuses = MutableStateFlow<Map<Long, StatusResult>>(emptyMap())
    private val loading = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<AppUiState> = combine(
        settings.servers,
        settings.themeMode,
        settings.autoRefresh,
        statuses,
        loading,
    ) { servers, theme, auto, stats, loads ->
        AppUiState(
            items = servers.map { ServerUiItem(it, stats[it.id], it.id in loads) },
            themeMode = theme,
            autoRefresh = auto,
            refreshing = loads.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        // 打开应用时（且开了自动刷新）把没有结果的服务器都查一遍；新增收藏也会流经这里
        viewModelScope.launch {
            combine(settings.servers, settings.autoRefresh) { servers, auto -> servers to auto }
                .collectLatest { (servers, auto) ->
                    if (auto) servers.filter { statuses.value[it.id] == null }.forEach { query(it) }
                }
        }
    }

    fun refreshAll() {
        viewModelScope.launch { settings.servers.first().forEach { query(it) } }
    }

    fun query(entry: ServerEntry) {
        viewModelScope.launch {
            loading.update { it + entry.id }
            // 查询失败不抛出中断界面，转成带 error 的结果展示
            val result = runCatching { api.query(entry.address) }.getOrElse {
                StatusResult(source = "查询异常", error = it.message ?: it.javaClass.simpleName)
            }
            statuses.update { it + (entry.id to result) }
            loading.update { it - entry.id }
        }
    }

    fun addServer(name: String, address: String, modPort: Int = 0, modPassword: String? = null) {
        viewModelScope.launch {
            val entry = ServerEntry(
                id = System.currentTimeMillis(),
                name = name.trim(),
                address = normalizeAddress(address),
                modPort = modPort.coerceIn(0, 65535),
                modHasPassword = !modPassword.isNullOrEmpty(),
            )
            // 密码只以 Keystore 加密密文形式落盘
            if (!modPassword.isNullOrEmpty()) {
                settings.savePasswordBlob(entry.id, PasswordVault.encrypt(modPassword))
            }
            settings.saveServers(settings.servers.first() + entry)
            if (!settings.autoRefresh.first()) query(entry) // 自动刷新关着时手动查一次
        }
    }

    fun removeServer(id: Long) {
        viewModelScope.launch {
            settings.saveServers(settings.servers.first().filterNot { it.id == id })
            settings.removePasswordBlob(id)
            statuses.update { it - id }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val old = settings.servers.first()
            old.forEach { settings.removePasswordBlob(it.id) }
            settings.saveServers(emptyList())
            statuses.value = emptyMap()
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.saveThemeMode(mode) }

    fun setAutoRefresh(value: Boolean) = viewModelScope.launch { settings.saveAutoRefresh(value) }

    // 容错：用户可能粘贴完整 URL 或带空格的地址
    private fun normalizeAddress(raw: String): String =
        raw.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
}

// base64 -> ImageBitmap，失败返回 null（界面显示占位）
fun decodeIcon(base64: String?) = base64?.let {
    runCatching {
        val bytes = Base64.decode(it, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

// 服务器图标组件：查询到的用真图，没有的显示"MC"占位
@Composable
fun ServerIcon(base64: String?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(base64) { decodeIcon(base64) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Text("MC", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}
