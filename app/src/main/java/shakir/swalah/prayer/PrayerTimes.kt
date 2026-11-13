package shakir.swalah.prayer

import com.azan.Azan
import com.azan.astrologicalCalc.Location
import com.azan.astrologicalCalc.SimpleDate
import shakir.swalah.data.AppSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Adhan times of one calendar day, as epoch millis, indexed by [Prayer]. */
class DayTimes(val date: LocalDate, private val millis: LongArray) {
    operator fun get(prayer: Prayer): Long = millis[prayer.ordinal]
}

/**
 * Prayer times for a fixed location / method / adjustment set.
 * Pure and cheap to create; results are cached per date.
 */
class PrayerCalculator(
    private val latitude: Double,
    private val longitude: Double,
    private val method: CalculationMethod,
    private val adjustments: List<Int>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val cache = HashMap<LocalDate, DayTimes>()

    @Synchronized
    fun day(date: LocalDate): DayTimes = cache.getOrPut(date) { compute(date) }

    private fun compute(date: LocalDate): DayTimes {
        // The UTC offset of *that* day (handles daylight saving per date, half-hour zones too).
        val noon = date.atTime(12, 0).atZone(zone).toInstant()
        val offsetHours = zone.rules.getOffset(noon).totalSeconds / 3600.0
        val azan = Azan(Location(latitude, longitude, offsetHours, 0), method.method)
        val times = azan.getPrayerTimes(SimpleDate(date.dayOfMonth, date.monthValue, date.year)).times
        val start = date.atStartOfDay()
        val millis = LongArray(Prayer.entries.size) { i ->
            val t = times[i]
            val local = start.plusHours(t.hour.toLong()).plusMinutes(t.minute.toLong()).plusSeconds(t.second.toLong())
                .plusMinutes(adjustments.getOrElse(i) { 0 }.toLong())
            local.atZone(zone).toInstant().toEpochMilli()
        }
        return DayTimes(date, millis)
    }

    fun today(now: Long = System.currentTimeMillis()): DayTimes = day(localDate(now))

    fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    companion object {
        /** Null until the user has picked a location. */
        fun from(settings: AppSettings): PrayerCalculator? {
            val loc = settings.location ?: return null
            return PrayerCalculator(loc.latitude, loc.longitude, CalculationMethod.at(settings.methodIndex), settings.adjustments)
        }
    }
}
