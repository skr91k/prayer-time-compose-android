package shakir.swalah.reminder

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import java.io.File

/** Sounds shipped with the app, plus helpers for system ringtones and imported files. */
object Sounds {
    data class BuiltIn(val rawName: String, val title: String)

    const val ADHAN = "athan"
    const val TONE = "message_tone"
    const val IQAMA_TONE = "mixkit_access_allowed_tone_2869"
    const val BEEP = "beep_beep_beep"
    const val ALERT = "panic"
    const val SIGNAL = "signal"
    const val VOYAGER = "voyager"

    val builtIns = listOf(
        BuiltIn(ADHAN, "Adhan"),
        BuiltIn(TONE, "Message tone"),
        BuiltIn(IQAMA_TONE, "Iqama tone"),
        BuiltIn(BEEP, "Beep beep"),
        BuiltIn(ALERT, "Alert"),
        BuiltIn(SIGNAL, "Signal"),
        BuiltIn(VOYAGER, "Voyager"),
    )

    /** By name, not R id: ids change between builds, names don't. */
    fun rawUri(context: Context, rawName: String): String = "android.resource://${context.packageName}/raw/$rawName"

    /** Imported audio files live in device-protected storage so they play before first unlock too. */
    fun importDir(context: Context): File =
        File(context.createDeviceProtectedStorageContext().filesDir, "sounds").apply { mkdirs() }

    fun title(context: Context, uri: String?): String {
        if (uri == null) return "Default alarm sound"
        builtIns.firstOrNull { uri == rawUri(context, it.rawName) }?.let { return it.title }
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") return parsed.lastPathSegment?.substringBeforeLast('.') ?: "Audio file"
        return runCatching { RingtoneManager.getRingtone(context, parsed)?.getTitle(context) }.getOrNull() ?: "Custom sound"
    }
}
