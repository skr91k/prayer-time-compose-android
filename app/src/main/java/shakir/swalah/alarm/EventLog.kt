package shakir.swalah.alarm

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent log of everything the reminder pipeline does (device-protected storage, survives
 * reboots). Read it with:
 * adb shell run-as shakir.swalah cat /data/user_de/0/shakir.swalah/files/events.log
 */
object EventLog {
    private const val FILE = "events.log"
    private const val MAX_BYTES = 256 * 1024
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    private fun file(context: Context) = File(context.createDeviceProtectedStorageContext().filesDir, FILE)

    @Synchronized
    fun log(context: Context, message: String, error: Throwable? = null) {
        val line = "${fmt.format(Date())}  $message" + (error?.let { "  !! ${it.javaClass.simpleName}: ${it.message}" } ?: "")
        Log.i("PrayerAlarm", line, error)
        try {
            val f = file(context)
            if (f.length() > MAX_BYTES) f.writeText(f.readText().takeLast(MAX_BYTES / 2))
            f.appendText(line + "\n")
        } catch (_: Exception) {
        }
    }

    fun time(millis: Long): String = if (millis <= 0) "-" else fmt.format(Date(millis))
}
