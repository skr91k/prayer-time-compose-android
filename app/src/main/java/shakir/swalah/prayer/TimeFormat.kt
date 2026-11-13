package shakir.swalah.prayer

import android.content.Context
import android.text.format.DateFormat
import shakir.swalah.data.AppSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats prayer times the way the user chose in Adhan time settings. */
class TimeFormat(context: Context, settings: AppSettings) {
    val is24Hour: Boolean = settings.use24Hour ?: DateFormat.is24HourFormat(context)
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
        when {
            is24Hour -> "HH:mm"
            settings.showAmPm -> "hh:mm a"
            else -> "hh:mm"
        },
        Locale.ENGLISH,
    )
    /** Never shows AM/PM - for narrow tables (month view). */
    private val compact: DateTimeFormatter = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "hh:mm", Locale.ENGLISH)

    fun format(millis: Long): String = formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    fun formatCompact(millis: Long): String = compact.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** Time of day given in minutes since midnight. */
    fun formatMinuteOfDay(minuteOfDay: Int): String =
        formatter.format(java.time.LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60))
}
