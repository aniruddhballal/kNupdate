package com.knupdate.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class RelayAccessException(message: String) : IOException(message)

object RelaySettings {
    fun url(context: Context): String = context.getSharedPreferences("relay", 0)
        .getString("url", BuildConfig.RELAY_URL).orEmpty()

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

    fun save(context: Context, value: String) {
        check(context.getSharedPreferences("relay", 0).edit().putString("url", normalizeUrl(value)).commit())
    }
}

class RelayClient(private val baseUrl: String) {
    @Volatile private var pollConnection: HttpURLConnection? = null

    suspend fun health() = request("GET", "/health").also {
        require(it.optString("service") == "knupdate-relay" && it.optInt("protocol") == 1) { "This URL is not a kNupdate relay." }
    }
    suspend fun publish(record: ActivityRecord, session: String?) = request("POST", "/v1/updates", record.wireJson(), session)
    suspend fun poll(session: String): JSONArray = request("GET", "/v1/poll", session = session, polling = true).getJSONArray("updates")
    suspend fun acknowledge(session: String, ids: List<String>) = request("POST", "/v1/ack", JSONObject().put("ids", JSONArray(ids)), session)
    suspend fun close(session: String) = request("DELETE", "/v1/poll", session = session)
    fun cancelPoll() { pollConnection?.disconnect() }

    private suspend fun request(method: String, path: String, body: JSONObject? = null,
        session: String? = null, polling: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        if (polling) pollConnection = connection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = if (polling) 35_000 else 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            if (session != null) connection.setRequestProperty("X-Relay-Session", session)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (connection.getHeaderField("cf-mitigated") == "challenge") {
                throw RelayAccessException("Relay hosting requires browser verification. This endpoint must allow app requests.")
            }
            if (code !in 200..299) throw IOException("Relay request failed (HTTP $code).")
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
