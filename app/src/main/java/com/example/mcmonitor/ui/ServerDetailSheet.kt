@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mcmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// 服务器详情：点列表卡片弹出。item 是实时状态，刷新完成会自动更新内容
@Composable
fun ServerDetailSheet(item: ServerUiItem, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ServerIcon(item.status?.iconBase64, 72.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = item.entry.name.ifBlank { item.entry.address },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = item.entry.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            val s = item.status
            when {
                item.loading -> CircularProgressIndicator()
                s == null || s.error != null -> Text(
                    text = if (s?.error != null) "查询失败：${s.error}" else "还没有查询结果",
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailRow("状态", if (s.online) "在线" else "离线")
                    DetailRow("玩家", "${s.playersOnline} / ${s.playersMax}")
                    DetailRow("版本", s.version ?: "未知")
                    DetailRow(if (s.direct) "服务器延迟" else "查询耗时", "${s.latencyMs} ms")
                    DetailRow("数据来源", s.source)
                    if (s.motdLines.isNotEmpty()) {
                        Column {
                            Text("MOTD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = s.motdLines.joinToString("\n"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
