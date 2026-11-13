package shakir.swalah.net

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import shakir.swalah.alarm.EventLog
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors

/** Background work against [PrayerApi]: adhan audio downloads and the daily device log. */
object NetSync {
    private const val PREFS = "net_sync"
    private const val KEY_AUDIO_SYNCED = "audio_synced_at"
    private const val KEY_LOGGED = "logged_at"
    private const val KEY_INSTALL_ID = "install_id"
    private const val AUDIO_EVERY_MS = 12 * 60 * 60_000L
    private const val LOG_EVERY_MS = 24 * 60 * 60_000L

    private val executor = Executors.newSingleThreadExecutor()

    /** Null when idle, otherwise a short status for the sound picker ("Downloading Azan…"). */
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    /** Bumped when the downloaded sounds change, so the picker re-lists them. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Downloaded adhan files (device-protected, so they also play before the first unlock). */
    fun downloadDir(context: Context): File =
        File(context.createDeviceProtectedStorageContext().filesDir, "sounds/downloaded").apply { mkdirs() }

    fun downloadedSounds(context: Context): List<File> =
        downloadDir(context).listFiles { f -> f.isFile && !f.name.endsWith(".part") }?.sortedBy { it.name }.orEmpty()

    /** On app open: audio at most every 12 h, log at most daily. [force] re-checks audio now. */
    fun onAppOpen(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        executor.execute {
            val now = System.currentTimeMillis()
            val p = prefs(app)
            if (force || now - p.getLong(KEY_AUDIO_SYNCED, 0) > AUDIO_EVERY_MS) syncAudio(app)
            if (now - p.getLong(KEY_LOGGED, 0) > LOG_EVERY_MS && sendLog(app, "daily")) p.edit().putLong(KEY_LOGGED, now).apply()
        }
    }

    /** Downloads only files that are missing or whose size differs from the server's. */
    private fun syncAudio(context: Context) {
        val list = PrayerApi.listAudio() ?: run {
            EventLog.log(context, "audio sync: no server reachable")
            return
        }
        val dir = downloadDir(context)
        var changed = false
        for (audio in list) {
            val dest = File(dir, audio.file)
            if (dest.isFile && dest.length() == audio.size) continue
            _status.value = "Downloading ${audio.name}…"
            val started = System.currentTimeMillis()
            if (PrayerApi.download(audio, dest)) {
                changed = true
                sendLog(context, "audio_downloaded") {
                    put("file", audio.file)
                    put("size", audio.size)
                    put("durationMs", System.currentTimeMillis() - started)
                }
            } else {
                EventLog.log(context, "audio sync: download failed ${audio.file}")
            }
        }
        _status.value = null
        if (changed) _version.value++
        prefs(context).edit().putLong(KEY_AUDIO_SYNCED, System.currentTimeMillis()).apply()
    }

    /**
     * Device info (as the old app logged, without its client-side IP lookup) plus an [event]:
     * "daily" once a day, "audio_downloaded" after each downloaded adhan file.
     */
    private fun sendLog(context: Context, event: String, extra: JSONObject.() -> Unit = {}): Boolean {
        val p = prefs(context)
        val installId = p.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            p.edit().putString(KEY_INSTALL_ID, it).apply()
        }
        val dm = context.resources.displayMetrics
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
        val body = JSONObject()
            .put("event", event)
            .put("installId", installId)
            .put("timestamp", System.currentTimeMillis())
            .put("timezone", TimeZone.getDefault().id)
            .put("language", Locale.getDefault().toLanguageTag())
            .put("screenWidth", dm.widthPixels)
            .put("screenHeight", dm.heightPixels)
            .put("appVersion", version)
            .put("androidVersion", Build.VERSION.SDK_INT)
            .put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
            .apply(extra)
        return PrayerApi.postLog(body).also { EventLog.log(context, "log '$event' sent=$it") }
    }
}
