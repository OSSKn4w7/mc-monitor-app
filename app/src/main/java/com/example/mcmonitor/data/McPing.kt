package com.example.mcmonitor.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

// Minecraft Java 版 Server List Ping：客户端直连服务器，握手后要回状态 JSON。
// 往返耗时就是"服务器延迟"，不依赖任何第三方 API。
object McPing {

    private val json = Json { ignoreUnknownKeys = true }

    // "play.x.com:25565" -> ("play.x.com", 25565)；不写端口默认 25565
    fun splitAddress(address: String): Pair<String, Int> {
        val a = address.trim()
        val idx = a.lastIndexOf(':')
        return if (idx > 0 && idx > a.lastIndexOf(']')) {
            a.substring(0, idx) to (a.substring(idx + 1).toIntOrNull() ?: 25565)
        } else {
            a to 25565
        }
    }

    fun ping(address: String): StatusResult {
        val (host, port) = splitAddress(address)
        val start = System.currentTimeMillis()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5_000)
            socket.soTimeout = 5_000
            val out = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            // handshake 包：包ID 0x00 + 协议版本(-1 占位) + 主机名 + 端口 + 下一个状态(1=status)
            val handshake = ByteArrayOutputStream()
            DataOutputStream(handshake).apply {
                writeVarInt(0x00)
                writeVarInt(-1)
                val hostBytes = host.toByteArray(Charsets.UTF_8)
                writeVarInt(hostBytes.size)
                write(hostBytes)
                writeShort(port)
                writeVarInt(1)
            }
            sendPacket(out, handshake.toByteArray())
            // status request 包：只有包ID，无内容
            sendPacket(out, byteArrayOf(0x00))

            val body = readPacket(input)
            val resp = DataInputStream(ByteArrayInputStream(body))
            check(resp.readVarInt() == 0x00) { "非预期的响应包" }
            val payload = ByteArray(resp.readVarInt())
            resp.readFully(payload)

            val root = json.parseToJsonElement(String(payload, Charsets.UTF_8)).jsonObject
            val players = root["players"] as? JsonObject
            return StatusResult(
                online = true,
                version = (root["version"] as? JsonObject)
                    ?.get("name")?.let { (it as? JsonPrimitive)?.content },
                playersOnline = players?.intOf("online") ?: 0,
                playersMax = players?.intOf("max") ?: 0,
                motdLines = extractText(root["description"]).split("\n").filter { it.isNotBlank() },
                iconBase64 = (root["favicon"] as? JsonPrimitive)
                    ?.content?.removePrefix("data:image/png;base64,"),
                latencyMs = System.currentTimeMillis() - start,
                direct = true,
                source = "直连查询",
            )
        }
    }

    private fun JsonObject.intOf(key: String): Int =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0

    private fun sendPacket(out: DataOutputStream, body: ByteArray) {
        out.writeVarInt(body.size)
        out.write(body)
        out.flush()
    }

    private fun readPacket(input: DataInputStream): ByteArray {
        val payload = ByteArray(input.readVarInt())
        input.readFully(payload)
        return payload
    }

    private fun DataOutputStream.writeVarInt(v: Int) {
        var value = v
        while (true) {
            if (value and 0x7F.inv() == 0) {
                writeByte(value)
                return
            }
            writeByte(value and 0x7F or 0x80)
            value = value ushr 7
        }
    }

    private fun DataInputStream.readVarInt(): Int {
        var value = 0
        var shift = 0
        while (true) {
            val b = readUnsignedByte()
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return value
            shift += 7
            check(shift <= 35) { "VarInt 过长" }
        }
    }

    // MOTD 是"文本组件"JSON：可能是字符串、{text, extra[]} 对象，或 1.21.5+ 的数组。递归把 text 拼出来
    private fun extractText(el: JsonElement?): String = when (el) {
        null -> ""
        is JsonPrimitive -> el.content
        is JsonObject ->
            ((el["text"] as? JsonPrimitive)?.content ?: "") +
                (el["extra"] as? JsonArray)?.joinToString("") { extractText(it) }.orEmpty()
        is JsonArray -> el.joinToString("") { extractText(it) }
    }
}
