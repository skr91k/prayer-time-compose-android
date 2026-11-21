package shakir.swalah.net

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import shakir.swalah.alarm.AlarmHistory
import shakir.swalah.alarm.EventLog
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors

/** Background work against [PrayerApi]: adhan audio downloads, the daily device log and the alarm report. */
object NetSync {
    private const val PREFS = "net_sync"
    private const val KEY_AUDIO_SYNCED = "audio_synced_at"
    private const val KEY_LOGGED = "logged_at"
    private const val KEY_INSTALL_ID = "install_id"
    private const val AUDIO_EVERY_MS = 12 * 60 * 60_000L
    private const val LOG_EVERY_MS = 24 * 60 * 60_000L
    /** Logs go a few seconds after the app opens, once re-arming has found any missed alarms. */
    private const val LOG_DELAY_MS = 5_000L
    /** Most alarm entries per request; the rest go next time. */
    private const val MAX_ALARMS_PER_LOG = 200

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null
    private val delayedLogs = Runnable { appContext?.let { app -> executor.execute { sendLogs(app) } } }

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

    /**
     * On app open: audio at most every 12 h. After [LOG_DELAY_MS]: the daily log (at most daily,
     * carrying every alarm entry not sent yet), or else an "alarm_report" if any alarm was missed.
     * [force] re-checks audio now.
     */
    fun onAppOpen(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        executor.execute {
            if (force || System.currentTimeMillis() - prefs(app).getLong(KEY_AUDIO_SYNCED, 0) > AUDIO_EVERY_MS) syncAudio(app)
        }
        appContext = app
        main.removeCallbacks(delayedLogs)
        main.postDelayed(delayedLogs, LOG_DELAY_MS)
    }

    private fun sendLogs(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val alarms = AlarmHistory.pending(context).take(MAX_ALARMS_PER_LOG)
        val event = when {
            now - p.getLong(KEY_LOGGED, 0) > LOG_EVERY_MS -> "daily"
            alarms.any { it.optString("status") == AlarmHistory.MISSED } -> "alarm_report"
            else -> return
        }
        val sent = sendLog(context, event) {
            put("alarms", JSONArray(alarms.map { JSONObject(it.toString()).apply { remove("seq") } }))
            put("alarmCount", alarms.size)
            put("missedCount", alarms.count { it.optString("status") == AlarmHistory.MISSED })
            put("lateCount", alarms.count { it.optString("status") == AlarmHistory.LATE })
        }
        if (!sent) return
        AlarmHistory.markSent(context, alarms)
        if (event == "daily") p.edit().putLong(KEY_LOGGED, now).apply()
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
     * "daily" once a day, "alarm_report" when alarms were missed, "audio_downloaded" after each
     * downloaded adhan file.
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
