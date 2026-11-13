package shakir.swalah.reminder

import org.json.JSONObject
import shakir.swalah.prayer.HijriDate
import shakir.swalah.prayer.Prayer
import shakir.swalah.prayer.PrayerCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneId

/** How a reminder's time of day is defined. */
enum class TimeBase {
    /** A clock time ([Reminder.hour]:[Reminder.minute]). */
    FIXED,
    /** [Reminder.prayer]'s adhan time on that day, shifted by [Reminder.offsetMinutes] (negative = before). */
    PRAYER,
}

/**
 * A prayer reminder / alarm. Same model as a NeverMiss alarm, but the time can follow a prayer.
 *
 * Repeat is one of:
 *  - [date] set: once on that exact date (ISO yyyy-MM-dd), then switches off - or with [yearly] every year.
 *  - [monthDay] 1..31: every month on that day; shorter months use their last day.
 *  With [hijri], [date] is a Hijri date (yyyy-MM-dd in the Umm al-Qura calendar) and [monthDay]
 *  a Hijri day (1..30), so it can follow e.g. 1 Ramadan or the 13th of every Hijri month.
 *  - [days] bitmask, Monday = bit 0 ... Sunday = bit 6: weekly (all bits = daily).
 *  - none of these: once, at the next occurrence.
 *
 * For prayer-based reminders "the day" is the day of the prayer, so "Fajr - 30 min on Fridays"
 * rings before Friday's Fajr even if that is Thursday evening for a large offset.
 *
 * @param autoStopSeconds 0 = rings until dismissed (wake-up alarm), > 0 = stops after that many
 *        seconds, [PLAY_ONCE] = plays the sound one time (a full adhan) and stops.
 * @param preset built-in reminder this one was created from (see [Presets]); null for user-made ones.
 * @param scheduledAt the trigger currently armed in AlarmManager.
 * @param lastFiredAt trigger of the last occurrence that actually rang.
 */
data class Reminder(
    val id: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val base: TimeBase = TimeBase.PRAYER,
    val hour: Int = 7,
    val minute: Int = 0,
    val prayer: Prayer = Prayer.FAJR,
    val offsetMinutes: Int = 0,
    val days: Int = EVERY_DAY,
    val date: String? = null,
    val monthDay: Int = 0,
    val yearly: Boolean = false,
    val hijri: Boolean = false,
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val fullScreen: Boolean = true,
    val snoozeMinutes: Int = 5,
    val mathChallenge: Boolean = false,
    /** Default: play the sound once, then dismiss by itself (no snooze, no math). */
    val autoStopSeconds: Int = PLAY_ONCE,
    val autoSnoozeSeconds: Int = 60,
    val preset: String? = null,
    // Runtime state.
    val snoozedUntil: Long = 0L,
    val scheduledAt: Long = 0L,
    val lastFiredAt: Long = 0L,
    val autoSnoozes: Int = 0,
    val skippedTrigger: Long = 0L,
) {
    val isRepeating: Boolean get() = if (date != null) yearly else (days != 0 || monthDay != 0)

    /** The built-in adhan and Shurooq reminders: can't be deleted, and their name can't change. */
    val isCoreBuiltIn: Boolean get() = preset?.startsWith("adhan_") == true

    /** Plays until dismissed, with snooze. */
    val isWakeUp: Boolean get() = autoStopSeconds == 0

    val exactDate: LocalDate? get() = if (hijri) null else date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    val hijriDate: HijriDate? get() = if (hijri) date?.let(HijriDate::parse) else null

    fun hasDay(dayIndex: Int): Boolean = days and (1 shl dayIndex) != 0

    fun isSkipping(calc: PrayerCalculator?, now: Long = System.currentTimeMillis()): Boolean =
        isRepeating && skippedTrigger > now && regularTrigger(calc, now) == skippedTrigger

    /** A fresh copy for "Duplicate": same settings, no runtime state. */
    fun duplicate(): Reminder = Reminder(
        label = label, base = base, hour = hour, minute = minute, prayer = prayer, offsetMinutes = offsetMinutes,
        days = days, date = date, monthDay = monthDay, yearly = yearly, hijri = hijri, ringtoneUri = ringtoneUri,
        vibrate = vibrate, fullScreen = fullScreen, snoozeMinutes = snoozeMinutes, mathChallenge = mathChallenge,
        autoStopSeconds = autoStopSeconds, autoSnoozeSeconds = autoSnoozeSeconds,
    )

    /** When it rings on [day]; null if it depends on prayer times and no location is set. */
    fun occurrenceOn(day: LocalDate, calc: PrayerCalculator?, zone: ZoneId = ZoneId.systemDefault()): Long? = when (base) {
        TimeBase.FIXED -> day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        TimeBase.PRAYER -> calc?.day(day)?.get(prayer)?.plus(offsetMinutes * 60_000L)
    }

    /** Next ring including snooze and skip; null if it can't be computed (no location). */
    fun nextTrigger(calc: PrayerCalculator?, now: Long = System.currentTimeMillis()): Long? {
        if (snoozedUntil > now) return snoozedUntil
        val next = regularTrigger(calc, now) ?: return null
        return if (isRepeating && skippedTrigger != 0L && next == skippedTrigger) regularTrigger(calc, next) else next
    }

    /** Next occurrence from the repeat rule alone. May be in the past for a one-date reminder. */
    fun regularTrigger(calc: PrayerCalculator?, now: Long = System.currentTimeMillis()): Long? {
        if (base == TimeBase.PRAYER && calc == null) return null
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (hijri) return hijriTrigger(calc, now, today, zone)
        exactDate?.let { d ->
            if (yearly) {
                val md = MonthDay.from(d)
                var year = today.year - 1
                repeat(4) {
                    val candidate = occurrenceOn(md.atYear(year), calc, zone)
                    if (candidate != null && candidate > now) return candidate
                    year++
                }
            }
            return occurrenceOn(d, calc, zone)
        }
        if (monthDay != 0) {
            var month = YearMonth.from(today).minusMonths(1)
            repeat(15) {
                val candidate = occurrenceOn(month.atDay(minOf(monthDay, month.lengthOfMonth())), calc, zone)
                if (candidate != null && candidate > now) return candidate
                month = month.plusMonths(1)
            }
        }
        // Start a day early: a large negative offset puts a prayer's reminder on the evening before.
        var day = today.minusDays(1)
        repeat(10) {
            val candidate = occurrenceOn(day, calc, zone)
            if (candidate != null && candidate > now && (days == 0 || hasDay(day.dayOfWeek.value - 1))) return candidate
            day = day.plusDays(1)
        }
        return null
    }

    private fun hijriTrigger(calc: PrayerCalculator?, now: Long, today: LocalDate, zone: ZoneId): Long? {
        val hToday = HijriDate.from(today) ?: return null
        hijriDate?.let { d ->
            if (yearly) {
                for (year in hToday.year - 1..hToday.year + 2) {
                    val candidate = HijriDate(year, d.month, d.day).toGregorian()?.let { occurrenceOn(it, calc, zone) }
                    if (candidate != null && candidate > now) return candidate
                }
            }
            return d.toGregorian()?.let { occurrenceOn(it, calc, zone) }
        }
        if (monthDay != 0) {
            var month = HijriDate(hToday.year, hToday.month, monthDay).plusMonths(-1)
            repeat(15) {
                val candidate = month.toGregorian()?.let { occurrenceOn(it, calc, zone) }
                if (candidate != null && candidate > now) return candidate
                month = month.plusMonths(1)
            }
        }
        return null
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("label", label); put("enabled", enabled)
        put("base", base.name); put("hour", hour); put("minute", minute)
        put("prayer", prayer.name); put("offsetMinutes", offsetMinutes)
        put("days", days); put("date", date ?: ""); put("monthDay", monthDay); put("yearly", yearly); put("hijri", hijri)
        put("ringtoneUri", ringtoneUri ?: ""); put("vibrate", vibrate); put("fullScreen", fullScreen)
        put("snoozeMinutes", snoozeMinutes); put("mathChallenge", mathChallenge)
        put("autoStopSeconds", autoStopSeconds); put("autoSnoozeSeconds", autoSnoozeSeconds)
        put("preset", preset ?: "")
        put("snoozedUntil", snoozedUntil); put("scheduledAt", scheduledAt); put("lastFiredAt", lastFiredAt)
        put("autoSnoozes", autoSnoozes); put("skippedTrigger", skippedTrigger)
    }

    companion object {
        const val EVERY_DAY = 0b1111111
        const val PLAY_ONCE = -1
        /** Hidden reminder used by the debug "Test" button. */
        const val TEST_ID = 999_999

        fun fromJson(o: JSONObject) = Reminder(
            id = o.getInt("id"),
            label = o.optString("label"),
            enabled = o.optBoolean("enabled", true),
            base = runCatching { TimeBase.valueOf(o.optString("base")) }.getOrDefault(TimeBase.FIXED),
            hour = o.optInt("hour", 7),
            minute = o.optInt("minute"),
            prayer = runCatching { Prayer.valueOf(o.optString("prayer")) }.getOrDefault(Prayer.FAJR),
            offsetMinutes = o.optInt("offsetMinutes"),
            days = o.optInt("days"),
            date = o.optString("date").ifEmpty { null },
            monthDay = o.optInt("monthDay"),
            yearly = o.optBoolean("yearly"),
            hijri = o.optBoolean("hijri"),
            ringtoneUri = o.optString("ringtoneUri").ifEmpty { null },
            vibrate = o.optBoolean("vibrate", true),
            fullScreen = o.optBoolean("fullScreen", true),
            snoozeMinutes = o.optInt("snoozeMinutes", 5),
            mathChallenge = o.optBoolean("mathChallenge"),
            autoStopSeconds = o.optInt("autoStopSeconds"),
            autoSnoozeSeconds = o.optInt("autoSnoozeSeconds", 60),
            preset = o.optString("preset").ifEmpty { null },
            snoozedUntil = o.optLong("snoozedUntil"),
            scheduledAt = o.optLong("scheduledAt"),
            lastFiredAt = o.optLong("lastFiredAt"),
            autoSnoozes = o.optInt("autoSnoozes"),
            skippedTrigger = o.optLong("skippedTrigger"),
        )
    }
}
