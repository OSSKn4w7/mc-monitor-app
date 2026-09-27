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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// 主界面：收藏列表 + 刷新 + 添加入口
@Composable
fun ServerListScreen(
    viewModel: AppViewModel,
    state: AppUiState,
    onOpenDetail: (Long) -> Unit,
    onOpenConsole: (Long) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加服务器")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.items.isEmpty()) {
                EmptyContent(
                    onAdd = { showAddDialog = true },
                    onQuickAdd = { viewModel.addServer("", it) },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.entry.id }) { item ->
                        ServerCard(
                            item = item,
                            onOpenDetail = onOpenDetail,
                            onOpenConsole = onOpenConsole,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, address, modPort, password ->
                viewModel.addServer(name, address, modPort, password)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun ServerCard(
    item: ServerUiItem,
    onOpenDetail: (Long) -> Unit,
    onOpenConsole: (Long) -> Unit,
    viewModel: AppViewModel,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(onClick = { onOpenDetail(item.entry.id) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerIcon(item.status?.iconBase64, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.entry.name.ifBlank { item.entry.address },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                StatusLine(item)
            }
            // 配置了服务端 mod 的条目提供控制台直达入口
            if (item.entry.modPort > 0) {
                TextButton(onClick = { onOpenConsole(item.entry.id) }) {
                    Text("控制台", style = MaterialTheme.typography.labelMedium)
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("立即刷新") },
                        onClick = { menuOpen = false; viewModel.query(item.entry) },
                    )
                    if (item.entry.modPort > 0) {
                        DropdownMenuItem(
                            text = { Text("打开控制台") },
                            onClick = { menuOpen = false; onOpenConsole(item.entry.id) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { menuOpen = false; viewModel.removeServer(item.entry.id) },
                    )
                }
            }
        }
    }
}

// 一行状态文字：查询中 / 未查询 / 失败 / 离线 / 在线
@Composable
private fun StatusLine(item: ServerUiItem) {
    val s = item.status
    when {
        item.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text("查询中…", style = MaterialTheme.typography.bodySmall)
        }
        s == null -> Text(
            "点右上角 ↻ 刷新",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Warning, contentDescription = null,
                modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "查询失败：${s.error}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        !s.online -> Text(
            "离线",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        else -> Text(
            "在线 · ${s.latencyMs}ms · ${s.playersOnline}/${s.playersMax} 人 · ${s.version ?: "未知版本"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// 空状态：引导添加 + 示例服务器一键加入
@Composable
private fun EmptyContent(onAdd: () -> Unit, onQuickAdd: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("还没有收藏的服务器", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "点右下角 + 添加；或直接试试示例：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onQuickAdd("hypixel.net") }, label = { Text("hypixel.net") })
            AssistChip(onClick = { onQuickAdd("play.cubecraft.net") }, label = { Text("play.cubecraft.net") })
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onAdd) { Text("手动添加") }
    }
}

@Composable
private fun AddServerDialog(onDismiss: () -> Unit, onConfirm: (String, String, Int, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var modPort by remember { mutableStateOf("") }
    var modPassword by remember { mutableStateOf("") }

    val portValue = modPort.trim().toIntOrNull() ?: 0
    // 配了端口就必须有密码；没配端口忽略密码
    val consoleConfigured = modPort.isNotBlank() && portValue > 0
    val valid = address.isNotBlank() && (!consoleConfigured || modPassword.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加服务器") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("备注名（可选）") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("例如 hypixel.net") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modPort,
                    onValueChange = { modPort = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("监控端口（可选，装了配套 mod 才填）") },
                    placeholder = { Text("默认 25580") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (consoleConfigured) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = modPassword,
                        onValueChange = { modPassword = it },
                        label = { Text("控制台密码（必填）") },
                        supportingText = { Text("服务端 /mcmonitor setpass 设置的密码，密文保存在本机") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        name,
                        address,
                        if (consoleConfigured) portValue else 0,
                        if (consoleConfigured) modPassword else null,
                    )
                },
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
