package shakir.swalah.prayer

import shakir.swalah.data.AppSettings
import shakir.swalah.data.ThirdOfNight
import java.time.ZoneId

enum class CountdownPhase {
    /** Counting down to the next adhan (white). */
    NEXT_ADHAN,
    /** Adhan was a few minutes ago, counting up (red). */
    ADHAN_PASSED,
    /** Between adhan and iqama, counting down (green). */
    BEFORE_IQAMA,
    /** Iqama was a few minutes ago, counting up (red). */
    IQAMA_PASSED,
}

/** [target] may be in the past: the screen then shows the time elapsed since it. */
data class Countdown(val target: Long, val prayer: Prayer, val isIqama: Boolean, val phase: CountdownPhase) {
    val title: String get() = prayer.arabic + if (isIqama) " (الإقامة) " else ""
}

data class NightTimes(val midnight: Long, val lastThird: Long?)

object HomeTimes {
    private const val MIN = 60_000L
    private const val IQAMA_RED_MS = 10 * MIN

    /** Iqama of [prayer] on the day of [day], per the user's iqama settings. */
    fun iqamaTime(settings: AppSettings, day: DayTimes, prayer: Prayer, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val rule = settings.iqamaFor(prayer) ?: return null
        val adhan = day[prayer]
        return when {
            rule.afterAdhan -> adhan + rule.minutes * MIN
            rule.fixedMinuteOfDay >= 0 -> day.date.atStartOfDay(zone).plusMinutes(rule.fixedMinuteOfDay.toLong()).toInstant().toEpochMilli()
            else -> adhan + 30 * MIN
        }
    }

    fun countdown(now: Long, calc: PrayerCalculator, settings: AppSettings): Countdown {
        val today = calc.localDate(now)
        val events = listOf(today.minusDays(1), today, today.plusDays(1))
            .map(calc::day)
            .flatMap { d -> Prayer.entries.map { p -> Triple(d, p, d[p]) } }
        val current = events.last { it.third <= now }
        val next = events.first { it.third > now }
        val (day, prayer, adhan) = current

        val iqama = if (settings.iqamaCountdown) iqamaTime(settings, day, prayer) else null
        val adhanRedMs = (if (prayer == Prayer.SUNRISE) 10 else 20) * MIN
        return when {
            iqama != null && now > iqama && now <= iqama + IQAMA_RED_MS -> Countdown(iqama, prayer, true, CountdownPhase.IQAMA_PASSED)
            iqama != null && now <= iqama -> Countdown(iqama, prayer, true, CountdownPhase.BEFORE_IQAMA)
            now <= adhan + adhanRedMs -> Countdown(adhan, prayer, false, CountdownPhase.ADHAN_PASSED)
            else -> Countdown(next.third, next.second, false, CountdownPhase.NEXT_ADHAN)
        }
    }

    /** Midnight and last third of the night that is current, or coming next if it's daytime. */
    fun night(now: Long, calc: PrayerCalculator, mode: ThirdOfNight): NightTimes {
        val today = calc.localDate(now)
        val todayTimes = calc.day(today)
        val (evening, morning) =
            if (now < todayTimes[Prayer.FAJR]) calc.day(today.minusDays(1)) to todayTimes
            else todayTimes to calc.day(today.plusDays(1))
        val fajr = morning[Prayer.FAJR]
        val midnight = (evening[Prayer.MAGHRIB] + fajr) / 2
        val start = when (mode) {
            ThirdOfNight.ISHA_BASED -> evening[Prayer.ISHA]
            ThirdOfNight.MAGHRIB_BASED -> evening[Prayer.MAGHRIB]
            ThirdOfNight.HIDE -> null
        }
        return NightTimes(midnight, start?.let { fajr - (fajr - it) / 3 })
    }
}
