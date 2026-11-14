package shakir.swalah.reminder

import android.content.Context
import shakir.swalah.prayer.Prayer

/**
 * Built-in reminders created on first run:
 *  - adhan for the five prayers: on (Dhuhr every day except Friday); Shurooq: off
 *  - Jumua: Dhuhr - 1 h on Fridays: off
 *  - iqama alarms (Fajr/Dhuhr/Asr/Isha +20, Maghrib +5): on
 *  - Ishraq (Shurooq + 10 min), Tahajjud (Fajr - 30 min) and Suhoor (Fajr - 1 h): off
 *  - "Get ready for work": fixed 8:00 every day, a wake-up alarm (rings until dismissed,
 *    snooze 5 min, auto-snooze after 1 min, ringing screen): off
 * The others play their sound once and then dismiss themselves (no snooze, no math).
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
                // Fajr gets the full adhan; the other adhans (and Shurooq) the short "Signal" tone.
                ringtoneUri = Sounds.rawUri(context, if (p == Prayer.FAJR) Sounds.ADHAN else Sounds.SIGNAL),
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
            ringtoneUri = Sounds.rawUri(context, Sounds.SIGNAL),
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
                ringtoneUri = Sounds.rawUri(context, Sounds.VOYAGER),
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
        val work = Reminder(
            label = "Get ready for work",
            base = TimeBase.FIXED,
            hour = 8,
            minute = 0,
            ringtoneUri = Sounds.rawUri(context, Sounds.ALERT),
            enabled = false,
            fullScreen = true,
            autoStopSeconds = 0,
            snoozeMinutes = 5,
            autoSnoozeSeconds = 60,
            preset = "work",
        )
        return adhans + jumua + iqamas + ishraq + tahajjud + suhoor + work
    }
}
