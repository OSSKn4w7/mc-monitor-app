@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mcmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mcmonitor.data.ModClient
import com.example.mcmonitor.data.PasswordVault
import com.example.mcmonitor.data.ServerEntry
import com.example.mcmonitor.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 控制台界面：连接服务端 mod 监控通道，看实时日志 + 发服务器命令
@Composable
fun ConsoleScreen(entry: ServerEntry, onBack: () -> Unit) {
    val context = LocalContext.current
    val client = remember(entry.id) {
        // 监控通道主机 = 服务器地址的 host 部分；端口来自条目配置
        val host = entry.address.substringBeforeLast(':').trim()
        ModClient(host, entry.modPort)
    }
    val state by client.state.collectAsStateWithLifecycle()
    val logs by client.logs.collectAsStateWithLifecycle()
    val status by client.status.collectAsStateWithLifecycle()

    var command by remember { mutableStateOf("") }
    val sendScope = rememberCoroutineScope()

    // 进入界面：从 Keystore 密文中恢复密码并连接（密码只短暂存在于内存）
    LaunchedEffect(entry.id) {
        val blob = SettingsRepository(context).passwordBlob(entry.id).first()
        val password = blob?.let { PasswordVault.decrypt(it) }
        if (password.isNullOrEmpty()) {
            // 理论上不会发生：有端口必有密码；防御性提示
            client.markFailed("本机没有保存该服务器的控制台密码")
        } else {
            client.connectAndLogin(password)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("控制台 · ${entry.name.ifBlank { entry.address }}", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            StatusBanner(state, status)

            // 日志区：新日志自动滚到底部
            val listState = rememberLazyListState()
            LaunchedEffect(logs.size) {
                if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(logs) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (line.startsWith("» ")) MaterialTheme.colorScheme.primary
                        else if (line.startsWith("[审计]")) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // 命令输入行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入命令，如 list / say 你好", fontSize = 13.sp) },
                    singleLine = true,
                    enabled = state is ModClient.State.Connected,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val cmd = command.trim()
                        if (cmd.isNotEmpty()) {
                            command = ""
                            sendScope.launch { runCatching { client.runCommand(cmd) } }
                        }
                    },
                    enabled = state is ModClient.State.Connected && command.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送命令")
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(state: ModClient.State, status: ModClient.ConsoleStatus?) {
    Surface(
        color = when (state) {
            is ModClient.State.Connected -> MaterialTheme.colorScheme.primaryContainer
            is ModClient.State.Failed -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        when (state) {
            is ModClient.State.Connected -> Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "● 已连接",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    status?.let { "在线 ${it.online}/${it.max} · TPS ${"%.1f".format(it.tps)} · ${it.version}" }
                        ?: "获取状态中…",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            is ModClient.State.Failed -> Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "连接失败",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            else -> Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("正在连接…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
