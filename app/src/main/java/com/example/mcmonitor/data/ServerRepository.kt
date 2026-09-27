package com.example.mcmonitor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// 负责"给一个地址，换回状态"。网络请求放在 IO 调度器上，避免卡住界面。
class ServerRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // mcsrvstat.us v3 响应（只声明用到的字段，未知字段一律忽略）
    @Serializable
    private data class McSrvStat(
        val online: Boolean = false,
        val version: String? = null,
        val icon: String? = null,
        val players: Players = Players(),
        val motd: Motd = Motd(),
    )

    @Serializable
    private data class Players(val online: Int = 0, val max: Int = 0)

    @Serializable
    private data class Motd(val clean: List<String> = emptyList())

    // mcstatus.io v2 响应（备用源），字段名和结构都不同
    @Serializable
    private data class McStatusIo(
        val online: Boolean = false,
        val icon: String? = null,
        val version: Version? = null,
        val players: PlayersAlt = PlayersAlt(),
        val motd: MotdAlt = MotdAlt(),
    )

    @Serializable
    private data class Version(val name_clean: String? = null)

    @Serializable
    private data class PlayersAlt(val online: Int = 0, val max: Int = 0)

    @Serializable
    private data class MotdAlt(val clean: String? = null)

    suspend fun query(address: String): StatusResult = withContext(Dispatchers.IO) {
        // 首选：直连服务器走 MC 查询协议，拿真实延迟，不依赖第三方
        runCatching { McPing.ping(address) }
            .onFailure {
                android.util.Log.w("McMonitor", "直连查询失败 $address: ${it.javaClass.simpleName}: ${it.message}")
            }
            .getOrElse {
                // 直连失败（离线/网络受限）再走公开 API 兜底
                val start = System.currentTimeMillis()
                try {
                    val r = json.decodeFromString<McSrvStat>(fetch("https://api.mcsrvstat.us/3/$address"))
                    StatusResult(
                        online = r.online,
                        version = r.version,
                        playersOnline = r.players.online,
                        playersMax = r.players.max,
                        motdLines = r.motd.clean,
                        iconBase64 = r.icon,
                        latencyMs = System.currentTimeMillis() - start,
                        source = "mcsrvstat.us",
                    )
                } catch (e: Exception) {
                    val start2 = System.currentTimeMillis()
                    val r = json.decodeFromString<McStatusIo>(fetch("https://api.mcstatus.io/v2/status/java/$address"))
                    StatusResult(
                        online = r.online,
                        version = r.version?.name_clean,
                        playersOnline = r.players.online,
                        playersMax = r.players.max,
                        motdLines = r.motd.clean?.split("\n").orEmpty().filter { it.isNotBlank() },
                        iconBase64 = r.icon,
                        latencyMs = System.currentTimeMillis() - start2,
                        source = "mcstatus.io",
                    )
                }
            }
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "McMonitorApp/1.0")
            .build()
        client.newCall(request).execute().use { resp ->
            check(resp.code == 200) { "HTTP ${resp.code}" }
            return resp.body?.string() ?: error("空响应")
        }
    }
}
