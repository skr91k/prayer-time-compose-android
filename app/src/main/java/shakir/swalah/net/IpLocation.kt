package shakir.swalah.net

import org.json.JSONObject
import shakir.swalah.BuildConfig
import shakir.swalah.data.AppLocation
import java.net.HttpURLConnection
import java.net.URL

/**
 * Approximate position from the phone's IP address (ip-api.com Pro, HTTPS; key from api.properties).
 * Used by the location picker as a tappable suggestion and to order the city list when there
 * is no GPS fix. Fetched at most once per process.
 */
object IpLocation {
    /** fields=58335: status, country, countryCode, region, regionName, city, lat, lon, timezone, isp, query. */
    private val URL_JSON = "https://pro.ip-api.com/json?key=${BuildConfig.IPAPI_KEY}&fields=58335"

    @Volatile private var cached: AppLocation? = null

    /** "City, Country" with its coordinates, or null when offline / the lookup fails. Blocking. */
    fun fetch(): AppLocation? {
        cached?.let { return it }
        if (BuildConfig.IPAPI_KEY.isBlank()) return null
        return runCatching {
            val c = URL(URL_JSON).openConnection() as HttpURLConnection
            c.connectTimeout = 5_000
            c.readTimeout = 5_000
            try {
                if (c.responseCode != 200) return null
                val o = JSONObject(c.inputStream.bufferedReader().readText())
                if (o.optString("status") != "success" || !o.has("lat") || !o.has("lon")) return null
                val name = listOf(o.optString("city"), o.optString("country")).filter { it.isNotBlank() }.joinToString(", ")
                AppLocation(name.ifBlank { "Approximate location" }, o.getDouble("lat"), o.getDouble("lon"))
            } finally {
                c.disconnect()
            }
        }.getOrNull()?.also { cached = it }
    }
}
