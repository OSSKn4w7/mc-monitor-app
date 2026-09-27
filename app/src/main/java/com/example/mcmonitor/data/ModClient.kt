package com.example.mcmonitor.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// 与服务端 mod 监控通道的 TCP 客户端。
// 鉴权与服务端 PasswordAuth 相同算法，密码不出设备：
//   verifier = PBKDF2WithHmacSHA256(密码, salt, iters, 256bit)
//   response = HmacSHA256(key = verifier, message = nonce)
class ModClient(private val host: String, private val port: Int) {

    class ProtocolException(userMessage: String) : Exception(userMessage)

    sealed interface State {
        data object Connecting : State
        data object Connected : State
        data class Failed(val message: String) : State
    }

    data class ConsoleStatus(
        val online: Int,
        val max: Int,
        val players: List<String>,
        val tps: Double,
        val uptime: Long,
        val version: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val stateFlow = MutableStateFlow<State>(State.Connecting)
    val state: StateFlow<State> = stateFlow.asStateFlow()

    private val logsFlow = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = logsFlow.asStateFlow()

    private val statusFlow = MutableStateFlow<ConsoleStatus?>(null)
    val status: StateFlow<ConsoleStatus?> = statusFlow.asStateFlow()

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private val writeLock = Any()
    private var scope: CoroutineScope? = null

    // 协议用法是顺序请求，单请求等待槽即可
    private var pendingTypes: Set<String> = emptySet()
    private var pendingContinuation: kotlin.coroutines.Continuation<JsonObject>? = null
    private val pendingLock = Any()

    suspend fun connectAndLogin(password: String) = withContext(Dispatchers.IO) {
        stateFlow.value = State.Connecting
        try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 5_000)
            s.tcpNoDelay = true
            s.soTimeout = 180_000
            socket = s
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            val myScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = myScope
            myScope.launch { readerLoop(reader) }

            // 1) 要挑战
            val challenge = withTimeout(15_000) {
                request(buildJsonObject { put("type", "auth") }, setOf("challenge"))
            }
            val salt = Base64.getDecoder().decode(challenge.str("salt"))
            val iters = challenge.str("iters").toInt()
            val keyBits = challenge.str("keyBits").toIntOrNull() ?: 256
            val nonce = Base64.getDecoder().decode(challenge.str("nonce"))
            // 2) 本地算 verifier + HMAC，回响应（密码不出设备）
            val verifier = pbkdf2(password, salt, iters, keyBits)
            val response = hmacSha256(verifier, nonce)
            withTimeout(15_000) {
                request(
                    buildJsonObject {
                        put("type", "auth")
                        put("response", Base64.getEncoder().encodeToString(response))
                    },
                    setOf("ok"),
                )
            }

            stateFlow.value = State.Connected

            // 3) 订阅实时日志、拉最近日志、拉状态、起保活与定时状态刷新
            withTimeout(15_000) {
                request(buildJsonObject { put("type", "logsub"); put("enable", true) }, setOf("logsub"))
                val logsResp = request(buildJsonObject { put("type", "logs"); put("tail", 300) }, setOf("logs"))
                logsFlow.value = logsResp["lines"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                request(buildJsonObject { put("type", "status") }, setOf("status"))
            }
            myScope.launch {
                while (isActive) {
                    delay(30_000)
                    runCatching { sendJson(buildJsonObject { put("type", "ping") }) }
                }
            }
            myScope.launch {
                while (isActive) {
                    delay(10_000)
                    runCatching {
                        val st = withTimeout(10_000) {
                            request(buildJsonObject { put("type", "status") }, setOf("status"))
                        }
                        statusFlow.value = toStatus(st)
                    }
                }
            }
        } catch (e: Exception) {
            stateFlow.value = State.Failed(e.message ?: e.javaClass.simpleName)
            close()
        }
    }

    suspend fun runCommand(command: String): String {
        val resp = withContext(Dispatchers.IO) {
            withTimeout(20_000) {
                request(buildJsonObject { put("type", "cmd"); put("command", command) }, setOf("cmdresult"))
            }
        }
        val msg = resp.str("msg")
        // 命令与回显进入本地日志视图，便于回看
        logsFlow.update { (it + "» /$command" + msg.split("\n")).takeLast(500) }
        return msg
    }

    fun close() {
        runCatching { scope?.cancel() }
        runCatching { socket?.close() }
        socket = null
    }

    // 防御性入口：本地缺密码等场景直接置失败态
    fun markFailed(message: String) {
        stateFlow.value = State.Failed(message)
    }

    // ---------- 内部 ----------
    private fun readerLoop(reader: BufferedReader) {
        try {
            var line: String? = reader.readLine()
            while (line != null) {
                runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()?.let { dispatch(it) }
                line = reader.readLine()
            }
            onDisconnected("连接已断开")
        } catch (e: Exception) {
            onDisconnected("连接中断: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun dispatch(obj: JsonObject) {
        val type = obj["type"]?.jsonPrimitive?.content ?: return
        synchronized(pendingLock) {
            val cont = pendingContinuation
            if (cont != null) {
                if (type in pendingTypes) {
                    pendingContinuation = null
                    pendingTypes = emptySet()
                    cont.resume(obj)
                    return
                }
                if (type == "err") {
                    pendingContinuation = null
                    pendingTypes = emptySet()
                    cont.resumeWithException(ProtocolException(obj.str("msg")))
                    return
                }
            }
        }
        when (type) {
            "logpush" -> logsFlow.update { (it + obj.str("line")).takeLast(500) }
            "status" -> statusFlow.value = toStatus(obj)
        }
    }

    private suspend fun request(payload: JsonObject, expect: Set<String>): JsonObject {
        sendJson(payload)
        return await(expect)
    }

    private suspend fun await(expect: Set<String>): JsonObject =
        suspendCancellableCoroutine { cont ->
            synchronized(pendingLock) {
                pendingTypes = expect
                pendingContinuation = cont
            }
            cont.invokeOnCancellation {
                synchronized(pendingLock) {
                    pendingTypes = emptySet()
                    pendingContinuation = null
                }
            }
        }

    private fun sendJson(payload: JsonObject) {
        synchronized(writeLock) {
            val w = writer ?: throw ProtocolException("连接未建立")
            w.write(payload.toString())
            w.write("\n")
            w.flush()
        }
    }

    private fun onDisconnected(reason: String) {
        if (stateFlow.value !is State.Failed) stateFlow.value = State.Failed(reason)
        close()
    }

    private fun toStatus(obj: JsonObject): ConsoleStatus = ConsoleStatus(
        online = obj.str("online").toIntOrNull() ?: 0,
        max = obj.str("max").toIntOrNull() ?: 0,
        players = obj["players"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
        tps = obj.str("tps").toDoubleOrNull() ?: 0.0,
        uptime = obj.str("uptime").toLongOrNull() ?: 0,
        version = obj.str("version"),
    )

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.let {
            runCatching { it.content }.getOrDefault(it.toString())
        } ?: ""

    // 与服务端 core 的 PasswordAuth 相同算法
    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }
}
