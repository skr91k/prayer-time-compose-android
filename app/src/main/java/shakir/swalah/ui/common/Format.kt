package shakir.swalah.ui.common

import shakir.swalah.prayer.TimeFormat
import shakir.swalah.reminder.Reminder
import shakir.swalah.reminder.TimeBase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "Tue 05:12" */
fun formatDayTime(format: TimeFormat, millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + format.format(millis)
}

/** "Fri 12 Dec, 05:12" - for reminders that may be weeks away. */
fun formatDateTime(format: TimeFormat, millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return t.format(DateTimeFormatter.ofPattern("EEE d MMM")) + ", " + format.format(millis)
}

/** "Fajr", "20 min after Fajr", "1 h before Fajr" */
fun offsetLabel(reminder: Reminder): String = when (reminder.base) {
    TimeBase.FIXED -> "Fixed time"
    TimeBase.PRAYER -> offsetLabel(reminder.prayer.english, reminder.offsetMinutes)
}

fun offsetLabel(prayerName: String, offsetMinutes: Int): String = when {
    offsetMinutes == 0 -> "At $prayerName"
    offsetMinutes > 0 -> "${minutesLabel(offsetMinutes)} after $prayerName"
    else -> "${minutesLabel(-offsetMinutes)} before $prayerName"
}

fun minutesLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

fun daysLabel(reminder: Reminder): String {
    reminder.hijriDate?.let {
        return if (reminder.yearly) "Yearly on ${it.format(withYear = false)} (Hijri)" else "On ${it.format()}"
    }
    if (reminder.hijri && reminder.monthDay != 0) {
        return "Monthly on the ${ordinal(reminder.monthDay)} (Hijri)" + if (reminder.monthDay > 29) " (or last day)" else ""
    }
    reminder.exactDate?.let {
        return if (reminder.yearly) "Yearly on " + it.format(DateTimeFormatter.ofPattern("d MMMM"))
        else "On " + it.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))
    }
    if (reminder.monthDay != 0) {
        return "Monthly on the ${ordinal(reminder.monthDay)}" + if (reminder.monthDay > 28) " (or last day)" else ""
    }
    return when (reminder.days) {
        0 -> "Once"
        Reminder.EVERY_DAY -> "Every day"
        0b0011111 -> "Weekdays"
        0b1100000 -> "Weekends"
        else -> (0..6).filter { reminder.hasDay(it) }.joinToString(", ") { DAY_NAMES[it] }
    }
}

fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

fun ringsIn(millis: Long, now: Long = System.currentTimeMillis()): String {
    val totalMin = ((millis - now + 59_999) / 60_000).coerceAtLeast(0)
    val d = totalMin / (60 * 24)
    val h = (totalMin / 60) % 24
    val m = totalMin % 60
    return buildString {
        append("in ")
        if (d > 0) append("${d}d ")
        if (d > 0 || h > 0) append("${h}h ")
        append("${m}min")
    }
}
