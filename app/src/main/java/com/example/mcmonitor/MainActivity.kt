package com.example.mcmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mcmonitor.ui.AppUiState
import com.example.mcmonitor.ui.AppViewModel
import com.example.mcmonitor.ui.ConsoleScreen
import com.example.mcmonitor.ui.ServerDetailSheet
import com.example.mcmonitor.ui.ServerListScreen
import com.example.mcmonitor.ui.SettingsScreen
import com.example.mcmonitor.ui.theme.McMonitorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // collectAsStateWithLifecycle：界面在后台时自动停止收集，省电且不泄漏
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            McMonitorTheme(state.themeMode) {
                McMonitorApp(viewModel, state)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McMonitorApp(viewModel: AppViewModel, state: AppUiState) {
    var showSettings by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var consoleId by remember { mutableStateOf<Long?>(null) }

    // 系统返回键：先退设置页，再交还系统
    BackHandler(enabled = showSettings) { showSettings = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "设置" else "MC 服务器监控") },
                actions = {
                    // 手动刷新全部（自动刷新关闭时的主要刷新入口）
                    if (!showSettings) {
                        IconButton(onClick = { viewModel.refreshAll() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新全部")
                        }
                    }
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(
                            imageVector = if (showSettings) Icons.Filled.Close else Icons.Filled.Settings,
                            contentDescription = if (showSettings) "关闭设置" else "打开设置",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showSettings) {
                SettingsScreen(viewModel, state)
            } else {
                ServerListScreen(
                    viewModel, state,
                    onOpenDetail = { detailId = it },
                    onOpenConsole = { consoleId = it },
                )
            }
        }
    }

    // 控制台：独立整页，盖在列表之上
    consoleId?.let { id ->
        val consoleItem = state.items.find { it.entry.id == id }
        if (consoleItem != null) {
            ConsoleScreen(entry = consoleItem.entry, onBack = { consoleId = null })
        }
    }

    // 详情面板直接引用列表里的实时条目：后台刷新完成后内容会跟着更新
    state.items.find { it.entry.id == detailId }?.let { item ->
        ServerDetailSheet(item = item, onDismiss = { detailId = null })
    }
}
