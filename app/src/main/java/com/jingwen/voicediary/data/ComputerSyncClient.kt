package com.jingwen.voicediary.data

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class DiscoveredComputer(val name: String, val address: String)

class ComputerSyncClient(context: Context, dao: DiaryDao) {
    private val appContext = context.applicationContext
    private val archive = DiarySyncArchive(appContext, dao)

    suspend fun discover(): DiscoveredComputer = withContext(Dispatchers.IO) {
        val request = DISCOVERY_REQUEST.toByteArray(Charsets.UTF_8)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = DISCOVERY_TIMEOUT_MS
            val targets = linkedSetOf(InetAddress.getByName("255.255.255.255"))
            Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { network ->
                network.interfaceAddresses.mapNotNullTo(targets) { it.broadcast }
            }
            targets.forEach { address ->
                runCatching { socket.send(DatagramPacket(request, request.size, address, DISCOVERY_PORT)) }
            }
            val buffer = ByteArray(2048)
            while (true) {
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val json = JSONObject(String(response.data, 0, response.length, Charsets.UTF_8))
                if (json.optString("service") == SERVICE_NAME) {
                    val port = json.optInt("port", DEFAULT_HTTP_PORT)
                    return@withContext DiscoveredComputer(
                        name = json.optString("name", "我的电脑"),
                        address = "${response.address.hostAddress}:$port"
                    )
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("没有找到电脑同步程序。")
        }
    }

    suspend fun upload(address: String, code: String): SyncSummary = withContext(Dispatchers.IO) {
        validateCode(code)
        val temp = File(appContext.cacheDir, "diary-sync-upload.tmp")
        try {
            val summary = archive.create(temp)
            val connection = open(address, "/v1/backup", code).apply {
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Content-Type", "application/vnd.mydiary.sync+zip")
                setFixedLengthStreamingMode(temp.length())
            }
            connection.outputStream.use { output -> FileInputStream(temp).use { it.copyTo(output) } }
            requireSuccess(connection)
            summary
        } finally {
            temp.delete()
        }
    }

    suspend fun downloadAndRestore(address: String, code: String): SyncSummary = withContext(Dispatchers.IO) {
        validateCode(code)
        val temp = File(appContext.cacheDir, "diary-sync-download.tmp")
        try {
            val connection = open(address, "/v1/backup", code).apply { requestMethod = "GET" }
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalArgumentException(readError(connection, status))
            FileOutputStream(temp).use { output -> connection.inputStream.use { it.copyTo(output) } }
            archive.restore(temp)
        } finally {
            temp.delete()
        }
    }

    private fun open(address: String, path: String, code: String): HttpURLConnection {
        val base = normalizeAddress(address)
        return (URL("http://$base$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10 * 60_000
            useCaches = false
            setRequestProperty("X-Diary-Code", code.trim())
            setRequestProperty("X-Diary-Protocol", "1")
        }
    }

    private fun requireSuccess(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status !in 200..299) throw IllegalArgumentException(readError(connection, status))
        connection.inputStream.use { it.readBytes() }
    }

    private fun readError(connection: HttpURLConnection, status: Int): String {
        val raw = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        val serverMessage = runCatching { JSONObject(raw).optString("message") }.getOrNull().orEmpty()
        return when {
            serverMessage.isNotBlank() -> serverMessage
            status == 401 -> "配对码不正确。"
            status == 404 -> "电脑中还没有日记备份。"
            else -> "电脑同步失败（错误码 $status）。"
        }
    }

    private fun normalizeAddress(value: String): String {
        var address = value.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        require(address.isNotBlank() && '/' !in address && ' ' !in address) { "电脑地址格式不正确。" }
        if (':' !in address) address += ":$DEFAULT_HTTP_PORT"
        return address
    }

    private fun validateCode(code: String) {
        require(code.trim().matches(Regex("[0-9]{6}"))) { "请输入电脑程序显示的 6 位配对码。" }
    }

    companion object {
        const val DEFAULT_HTTP_PORT = 8765
        const val DISCOVERY_PORT = 8766
        const val SERVICE_NAME = "MY_DIARY_PC_SYNC"
        const val DISCOVERY_REQUEST = "MY_DIARY_DISCOVER_V1"
        private const val DISCOVERY_TIMEOUT_MS = 3_000
    }
}
