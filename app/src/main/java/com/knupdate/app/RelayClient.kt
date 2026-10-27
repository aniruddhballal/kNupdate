package com.knupdate.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64

class RelayAccessException(message: String) : IOException(message)
class RelayHttpException(val statusCode: Int) : IOException("Relay request failed (HTTP $statusCode).")

object RelaySettings {
    fun url(): String = BuildConfig.RELAY_URL

    fun normalizeUrl(value: String): String {
        val uri = URI(value.trim())
        require(uri.host != null && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.path.isNullOrEmpty() || uri.path == "/")) { "Enter the relay base URL, without a path or credentials." }
        val localHost = uri.host == "localhost" || uri.host == "127.0.0.1" || uri.host == "10.0.2.2" ||
            uri.host.startsWith("192.168.") || uri.host.startsWith("10.") ||
            (uri.host.startsWith("172.") && (uri.host.split('.').getOrNull(1)?.toIntOrNull() ?: 0) in 16..31)
        require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http" && localHost)) {
            "Use HTTPS. Debug builds also allow HTTP on a local development network."
        }
        return uri.toString().trimEnd('/')
    }

}

class RelayClient(private val baseUrl: String) {
    @Volatile private var pollConnection: HttpURLConnection? = null

    suspend fun health() = request("GET", "/health").also {
        require(it.optString("service") == "knupdate-relay" && it.optInt("protocol") == 1) { "This URL is not a kNupdate relay." }
    }
    suspend fun publish(record: ActivityRecord, session: String?) = request("POST", "/v1/updates", record.wireJson(), session, receiptKey = record.receiptKey)
    suspend fun poll(session: String, name: String? = null): JSONArray = request("GET", "/v1/poll", session = session, polling = true, listenerName = name).getJSONArray("updates")
    suspend fun listeners(session: String?) = request("GET", "/v1/listeners", session = session)
    suspend fun receipts(id: String, key: String, revision: Int) = request("GET", "/v1/receipts/$id?after=$revision", receiptKey = key, longPolling = true)
    suspend fun forgetReceipts(id: String, key: String) = request("DELETE", "/v1/receipts/$id", receiptKey = key)
    suspend fun acknowledge(session: String, ids: List<String>) = request("POST", "/v1/ack", JSONObject().put("ids", JSONArray(ids)), session)
    suspend fun close(session: String) = request("DELETE", "/v1/poll", session = session)
    fun cancelPoll() { pollConnection?.disconnect() }

    private suspend fun request(method: String, path: String, body: JSONObject? = null,
        session: String? = null, polling: Boolean = false, listenerName: String? = null,
        receiptKey: String? = null, longPolling: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        if (polling) pollConnection = connection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = if (polling || longPolling) 35_000 else 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            if (session != null) connection.setRequestProperty("X-Relay-Session", session)
            if (receiptKey != null) connection.setRequestProperty("X-Receipt-Key", receiptKey)
            if (listenerName != null) {
                require(ListenerNames.valid(listenerName))
                connection.setRequestProperty("X-Relay-Name", Base64.getEncoder().encodeToString(listenerName.trim().toByteArray(Charsets.UTF_8)))
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (connection.getHeaderField("cf-mitigated") == "challenge") {
                throw RelayAccessException("Relay hosting requires browser verification. This endpoint must allow app requests.")
            }
            if (code !in 200..299) throw RelayHttpException(code)
            val content = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 256 * 1024) { "Relay response is too large." }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
            JSONObject(content)
        } finally {
            if (polling && pollConnection === connection) pollConnection = null
            connection.disconnect()
        }
    }
}
