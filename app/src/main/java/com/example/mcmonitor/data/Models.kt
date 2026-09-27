package com.example.mcmonitor.data

import kotlinx.serialization.Serializable

// 一条收藏的服务器（本地持久化的最小单位）
@Serializable
data class ServerEntry(
    val id: Long,        // 唯一标识，用添加时的时间戳
    val name: String,    // 备注名，为空时界面显示地址
    val address: String, // 服务器地址，如 hypixel.net 或 play.example.com:25565
    val modPort: Int = 0,             // 服务端 mod 监控端口，0=未配置
    val modHasPassword: Boolean = false, // 本机是否已加密保存控制台密码
)

// 一次查询的归一化结果：直连协议与备用 API 都转成这个
data class StatusResult(
    val online: Boolean = false,
    val version: String? = null,
    val playersOnline: Int = 0,
    val playersMax: Int = 0,
    val motdLines: List<String> = emptyList(),
    val iconBase64: String? = null, // 服务器图标，PNG 的 base64 内容
    val latencyMs: Long = 0,        // 直连=真实往返延迟；API=查询耗时
    val direct: Boolean = false,    // 是否为直连协议查询
    val source: String = "",        // 数据来源
    val error: String? = null,      // 非 null 表示这次查询失败了
)

// 主题设置
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色"),
}
