package com.example.mcmonitor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// DataStore 单例：进程内共享一个实例（官方要求）
private val Context.dataStore by preferencesDataStore(name = "mcmonitor")

// 负责"收藏列表 + 设置"的读写。收藏列表整体序列化成一个 JSON 存进一个键，数量少时最简单可靠。
class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serverListSerializer = ListSerializer(ServerEntry.serializer())

    private companion object {
        val KEY_SERVERS = stringPreferencesKey("servers_json")
        val KEY_THEME = intPreferencesKey("theme_mode")
        val KEY_AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
    }

    val servers: Flow<List<ServerEntry>> = context.dataStore.data.map { p ->
        p[KEY_SERVERS]
            ?.let { raw -> runCatching { json.decodeFromString(serverListSerializer, raw) }.getOrDefault(emptyList()) }
            ?: emptyList()
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        ThemeMode.entries.getOrElse(p[KEY_THEME] ?: 0) { ThemeMode.SYSTEM }
    }

    val autoRefresh: Flow<Boolean> = context.dataStore.data.map { p ->
        p[KEY_AUTO_REFRESH] ?: true
    }

    suspend fun saveServers(list: List<ServerEntry>) {
        context.dataStore.edit { it[KEY_SERVERS] = json.encodeToString(serverListSerializer, list) }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.ordinal }
    }

    suspend fun saveAutoRefresh(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_REFRESH] = value }
    }

    // ---------- 控制台密码（Keystore 加密后的密文 blob，本体不落盘） ----------
    private fun passwordKey(id: Long) = stringPreferencesKey("pw_blob_$id")

    fun passwordBlob(id: Long): Flow<String?> = context.dataStore.data.map { it[passwordKey(id)] }

    suspend fun savePasswordBlob(id: Long, blob: String) {
        context.dataStore.edit { it[passwordKey(id)] = blob }
    }

    suspend fun removePasswordBlob(id: Long) {
        context.dataStore.edit { it.remove(passwordKey(id)) }
    }
}
