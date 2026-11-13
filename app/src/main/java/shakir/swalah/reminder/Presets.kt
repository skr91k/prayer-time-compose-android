package shakir.swalah.reminder

import android.content.Context
import shakir.swalah.prayer.Prayer

/**
 * Built-in reminders created on first run:
 *  - adhan for the five prayers: on (Dhuhr every day except Friday); Shurooq: off
 *  - Jumua: Dhuhr - 1 h on Fridays: off
 *  - iqama alarms (Fajr/Dhuhr/Asr/Isha +20, Maghrib +5): on
 *  - Ishraq (Shurooq + 10 min), Tahajjud (Fajr - 30 min) and Suhoor (Fajr - 1 h): off
 * Every one plays its sound once and then dismisses itself (no snooze, no math).
 * Iqama alarms keep their own offset; they don't follow the iqama countdown settings.
 */
object Presets {
    /** Day bit for Friday (Monday = bit 0). */
    private const val FRIDAY = 1 shl 4

    private val IQAMA_MINUTES = mapOf(
        Prayer.FAJR to 20, Prayer.DHUHR to 20, Prayer.ASR to 20, Prayer.MAGHRIB to 5, Prayer.ISHA to 20,
    )

    fun all(context: Context): List<Reminder> {
        val adhans = Prayer.entries.map { p ->
            Reminder(
                label = p.arabic,
                prayer = p,
                // Fajr gets the full adhan; the others keep the app's short tone, as before.
                ringtoneUri = Sounds.rawUri(context, if (p == Prayer.FAJR) Sounds.ADHAN else Sounds.TONE),
                // Shurooq is not a prayer: off unless turned on.
                enabled = p != Prayer.SUNRISE,
                // Fajr opens the ringing screen (often asleep); the others stay a heads-up notification.
                fullScreen = p == Prayer.FAJR,
                vibrate = false,
                autoStopSeconds = Reminder.PLAY_ONCE,
                // Friday's Dhuhr is covered by the Jumua reminder.
                days = if (p == Prayer.DHUHR) Reminder.EVERY_DAY and FRIDAY.inv() else Reminder.EVERY_DAY,
                preset = "adhan_${p.name}",
            )
        }
        val jumua = Reminder(
            label = "الجمعة Jumua",
            prayer = Prayer.DHUHR,
            offsetMinutes = -60,
            days = FRIDAY,
            ringtoneUri = Sounds.rawUri(context, Sounds.TONE),
            enabled = false,
            fullScreen = false,
            vibrate = false,
            autoStopSeconds = Reminder.PLAY_ONCE,
            preset = "jumua",
        )
        val iqamas = Prayer.WITH_IQAMA.map { p ->
            Reminder(
                label = "${p.arabic} (الإقامة)",
                prayer = p,
                offsetMinutes = IQAMA_MINUTES.getValue(p),
                ringtoneUri = Sounds.rawUri(context, Sounds.IQAMA_TONE),
                enabled = true,
                fullScreen = false,
                vibrate = false,
                autoStopSeconds = Reminder.PLAY_ONCE,
                preset = "iqama_${p.name}",
            )
        }
        val tahajjud = Reminder(
            label = "التهجد Tahajjud",
            prayer = Prayer.FAJR,
            offsetMinutes = -30,
            ringtoneUri = Sounds.rawUri(context, Sounds.BEEP),
            enabled = false,
            autoStopSeconds = Reminder.PLAY_ONCE,
            preset = "tahajjud",
        )
        val ishraq = Reminder(
            label = "الإشراق Ishraq",
            prayer = Prayer.SUNRISE,
            // The old app treated the first 10 minutes after Shurooq as the "just passed" window.
            offsetMinutes = 10,
            ringtoneUri = Sounds.rawUri(context, Sounds.TONE),
            enabled = false,
            fullScreen = false,
            vibrate = false,
            autoStopSeconds = Reminder.PLAY_ONCE,
            preset = "ishraq",
        )
        val suhoor = Reminder(
            label = "سَحُورٌ Suhoor",
            prayer = Prayer.FAJR,
            offsetMinutes = -60,
            ringtoneUri = Sounds.rawUri(context, Sounds.ALERT),
            enabled = false,
            autoStopSeconds = Reminder.PLAY_ONCE,
            preset = "suhoor",
        )
        return adhans + jumua + iqamas + ishraq + tahajjud + suhoor
    }
}
