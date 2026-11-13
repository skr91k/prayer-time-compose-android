package shakir.swalah.net

import org.json.JSONArray
import shakir.swalah.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Client for the app's own API (server-code/prayer_api.py).
 * Every request carries `X-Api-Key` = AES-256-ECB(unix seconds, SHA-256(secret)), base64url;
 * the server accepts it for +-3 s. The first server that answers /prayer/ping is used.
 * Every response (a 404 too) carries `X-Server-Time`; the difference to the phone's clock is kept
 * per server and applied to the key, so a phone whose clock is off still gets in.
 */
object PrayerApi {
    /** Tried in this order. Both come from the git-ignored api.properties at build time. */
    private val SERVERS = BuildConfig.API_SERVERS.split(",").filter { it.isNotBlank() }
    private val SECRET = BuildConfig.API_SECRET
    private const val SERVER_CACHE_MS = 10 * 60_000L

    private val aesKey = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(SECRET.toByteArray()), "AES")

    @Volatile private var server: String? = null
    @Volatile private var serverCheckedAt = 0L
    /** Server clock minus phone clock, in seconds, per server. */
    private val clockOffset = java.util.concurrent.ConcurrentHashMap<String, Long>()

    data class RemoteAudio(val name: String, val file: String, val size: Long)

    /** The key for [epochSeconds]; must match prayer_api.encrypt_timestamp(). */
    fun apiKey(epochSeconds: Long = System.currentTimeMillis() / 1000): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val raw = cipher.doFinal(epochSeconds.toString().toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    /** First server answering /prayer/ping, or null if none is reachable. Blocking. */
    @Synchronized
    fun availableServer(): String? {
        val cached = server
        if (cached != null && System.currentTimeMillis() - serverCheckedAt < SERVER_CACHE_MS) return cached
        server = SERVERS.firstOrNull { base ->
            runCatching {
                val code = open(base, "/prayer/ping", timeoutMs = 4_000).use { it.responseCode }
                // A 404 here usually means our clock is off: the offset was just learned, try once more.
                code == 200 || (code == 404 && open(base, "/prayer/ping", timeoutMs = 4_000).use { it.responseCode } == 200)
            }.getOrDefault(false)
        }
        serverCheckedAt = System.currentTimeMillis()
        return server
    }

    fun listAudio(): List<RemoteAudio>? {
        val base = availableServer() ?: return null
        return runCatching {
            open(base, "/prayer/audio").use { c ->
                if (c.responseCode != 200) return null
                val arr = JSONArray(c.connection.inputStream.bufferedReader().readText())
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    RemoteAudio(o.getString("name"), o.getString("file"), o.getLong("size"))
                }
            }
        }.getOrNull()
    }

    /** Downloads into [dest] via a temp file, so a broken download never replaces a good file. */
    fun download(audio: RemoteAudio, dest: File): Boolean {
        val base = availableServer() ?: return false
        val tmp = File(dest.parentFile, dest.name + ".part")
        return runCatching {
            val path = URLEncoder.encode(audio.file, "UTF-8").replace("+", "%20")
            open(base, "/prayer/audio/$path", timeoutMs = 60_000).use { c ->
                if (c.responseCode != 200) return false
                c.connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            }
            tmp.length() == audio.size && tmp.renameTo(dest)
        }.getOrDefault(false).also { if (!it) tmp.delete() }
    }

    fun postLog(body: JSONObject): Boolean {
        val base = availableServer() ?: return false
        return runCatching {
            open(base, "/prayer/log", method = "POST", body = body.toString()).use { it.responseCode == 200 }
        }.getOrDefault(false)
    }

    private class Conn(val connection: HttpURLConnection, private val base: String) : AutoCloseable {
        /** Reading the status also learns the server's clock from X-Server-Time. */
        val responseCode: Int
            get() = connection.responseCode.also { learnClock(base, connection.getHeaderField("X-Server-Time")) }
        override fun close() = connection.disconnect()
    }

    /** Phone time corrected to [base]'s clock. */
    private fun serverNow(base: String): Long = System.currentTimeMillis() / 1000 + (clockOffset[base] ?: 0L)

    private fun learnClock(base: String, header: String?) {
        val serverTime = header?.toLongOrNull() ?: return
        val diff = serverTime - System.currentTimeMillis() / 1000
        // Within a second is just latency / rounding; don't chase it.
        clockOffset[base] = if (kotlin.math.abs(diff) <= 1) 0L else diff
    }

    private fun open(base: String, path: String, method: String = "GET", body: String? = null, timeoutMs: Int = 15_000): Conn {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        // Fresh key per request: it is only valid for a few seconds.
        c.setRequestProperty("X-Api-Key", apiKey(serverNow(base)))
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        return Conn(c, base)
    }
}
