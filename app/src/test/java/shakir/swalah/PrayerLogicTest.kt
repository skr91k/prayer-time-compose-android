package shakir.swalah

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import shakir.swalah.data.AppLocation
import shakir.swalah.data.AppSettings
import shakir.swalah.data.IqamaRule
import shakir.swalah.prayer.CalculationMethod
import shakir.swalah.prayer.CountdownPhase
import shakir.swalah.prayer.HomeTimes
import shakir.swalah.prayer.Prayer
import shakir.swalah.prayer.PrayerCalculator
import shakir.swalah.reminder.Reminder
import shakir.swalah.reminder.TimeBase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class PrayerLogicTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val london = ZoneId.of("Europe/London")
    private val noAdj = List(6) { 0 }

    private fun local(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

    @Test
    fun timesAreOrderedAndPlausible() {
        val calc = PrayerCalculator(11.25, 75.78, CalculationMethod.KARACHI_SHAFI, noAdj, kolkata)
        val day = calc.day(LocalDate.of(2026, 11, 13))
        val times = Prayer.entries.map { day[it] }
        assertEquals(times.sorted(), times)
        val dhuhr = local(day[Prayer.DHUHR], kolkata)
        assertTrue("dhuhr $dhuhr", dhuhr.hour == 12 && dhuhr.minute in 5..30)
        println("Kozhikode 2026-11-13: " + Prayer.entries.joinToString { "${it.english} ${local(day[it], kolkata)}" })
    }

    @Test
    fun daylightSavingIsAppliedPerDate() {
        val calc = PrayerCalculator(51.5, -0.12, CalculationMethod.MWL, noAdj, london)
        val summer = local(calc.day(LocalDate.of(2026, 7, 1))[Prayer.DHUHR], london)
        val winter = local(calc.day(LocalDate.of(2026, 12, 1))[Prayer.DHUHR], london)
        assertEquals(13, summer.hour)
        assertEquals(11, winter.hour) // ~11:55 GMT in early December
    }

    @Test
    fun adjustmentsShiftTimes() {
        val base = PrayerCalculator(11.25, 75.78, CalculationMethod.KARACHI_SHAFI, noAdj, kolkata)
        val adj = PrayerCalculator(11.25, 75.78, CalculationMethod.KARACHI_SHAFI, listOf(2, 0, 0, 0, -3, 0), kolkata)
        val d = LocalDate.of(2026, 11, 13)
        assertEquals(2 * 60_000L, adj.day(d)[Prayer.FAJR] - base.day(d)[Prayer.FAJR])
        assertEquals(-3 * 60_000L, adj.day(d)[Prayer.MAGHRIB] - base.day(d)[Prayer.MAGHRIB])
    }

    @Test
    fun prayerRelativeReminderRingsBeforeFajr() {
        TimeZone.setDefault(TimeZone.getTimeZone(kolkata))
        val calc = PrayerCalculator(11.25, 75.78, CalculationMethod.KARACHI_SHAFI, noAdj, kolkata)
        val today = LocalDate.of(2026, 11, 13)
        val tahajjud = Reminder(base = TimeBase.PRAYER, prayer = Prayer.FAJR, offsetMinutes = -30)
        // Asked at noon: today's slot is gone, so it's tomorrow's Fajr - 30 min.
        val noon = today.atTime(12, 0).atZone(kolkata).toInstant().toEpochMilli()
        assertEquals(calc.day(today.plusDays(1))[Prayer.FAJR] - 30 * 60_000L, tahajjud.nextTrigger(calc, noon))
        // Asked at 1 am: still today.
        val night = today.atTime(1, 0).atZone(kolkata).toInstant().toEpochMilli()
        assertEquals(calc.day(today)[Prayer.FAJR] - 30 * 60_000L, tahajjud.nextTrigger(calc, night))
    }

    @Test
    fun weeklyPrayerReminderUsesThePrayersDay() {
        TimeZone.setDefault(TimeZone.getTimeZone(kolkata))
        val calc = PrayerCalculator(11.25, 75.78, CalculationMethod.KARACHI_SHAFI, noAdj, kolkata)
        // Friday = bit 4. 2026-11-13 is a Friday; ask on Saturday the 14th.
        val jumuah = Reminder(base = TimeBase.PRAYER, prayer = Prayer.DHUHR, offsetMinutes = -45, days = 1 shl 4)
        val sat = LocalDate.of(2026, 11, 14).atTime(9, 0).atZone(kolkata).toInstant().toEpochMilli()
        val next = jumuah.nextTrigger(calc, sat)!!
        assertEquals(LocalDate.of(2026, 11, 20), Instant.ofEpochMilli(next).atZone(kolkata).toLocalDate())
        assertEquals(calc.day(LocalDate.of(2026, 11, 20))[Prayer.DHUHR] - 45 * 60_000L, next)
    }

    @Test
    fun fixedReminderAndNoLocation() {
        TimeZone.setDefault(TimeZone.getTimeZone(kolkata))
        val fixed = Reminder(base = TimeBase.FIXED, hour = 21, minute = 30)
        val now = LocalDate.of(2026, 11, 13).atTime(20, 0).atZone(kolkata).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 13).atTime(21, 30).atZone(kolkata).toInstant().toEpochMilli(), fixed.nextTrigger(null, now))
        assertEquals(null, Reminder(base = TimeBase.PRAYER).nextTrigger(null, now))
    }

    @Test
    fun countdownPhases() {
        TimeZone.setDefault(TimeZone.getTimeZone(kolkata))
        val calc = PrayerCalculator(11.25, 75.78, CalculationMethod.KARACHI_SHAFI, noAdj, kolkata)
        val settings = AppSettings(location = AppLocation("x", 11.25, 75.78))
        val d = calc.day(LocalDate.of(2026, 11, 13))
        val asr = d[Prayer.ASR]
        val min = 60_000L
        assertEquals(CountdownPhase.BEFORE_IQAMA, HomeTimes.countdown(asr + 5 * min, calc, settings).phase)
        assertEquals(asr + 20 * min, HomeTimes.countdown(asr + 5 * min, calc, settings).target)
        assertEquals(CountdownPhase.IQAMA_PASSED, HomeTimes.countdown(asr + 25 * min, calc, settings).phase)
        assertEquals(CountdownPhase.NEXT_ADHAN, HomeTimes.countdown(asr + 40 * min, calc, settings).phase)
        assertEquals(Prayer.MAGHRIB, HomeTimes.countdown(asr + 40 * min, calc, settings).prayer)
        // Sunrise has no iqama: red for 10 min, then white.
        assertEquals(CountdownPhase.ADHAN_PASSED, HomeTimes.countdown(d[Prayer.SUNRISE] + 5 * min, calc, settings).phase)
        // Countdown off: straight to adhan-passed red, then next adhan.
        val off = settings.copy(iqamaCountdown = false)
        assertEquals(CountdownPhase.ADHAN_PASSED, HomeTimes.countdown(asr + 5 * min, calc, off).phase)
        // Fixed iqama at 16:30.
        val fixed = settings.copy(iqama = settings.iqama.toMutableList().also { it[2] = IqamaRule(afterAdhan = false, fixedMinuteOfDay = 16 * 60 + 30) })
        val target = HomeTimes.countdown(asr + 1 * min, calc, fixed).target
        assertEquals(LocalDate.of(2026, 11, 13).atTime(16, 30).atZone(kolkata).toInstant().toEpochMilli(), target)
    }

    @Test
    fun dhuhrExceptFridaySkipsToSaturday() {
        TimeZone.setDefault(TimeZone.getTimeZone(kolkata))
        val calc = PrayerCalculator(11.25, 75.78, CalculationMethod.KARACHI_SHAFI, noAdj, kolkata)
        val dhuhr = Reminder(base = TimeBase.PRAYER, prayer = Prayer.DHUHR, days = Reminder.EVERY_DAY and (1 shl 4).inv())
        // Thursday 2026-11-12 after Dhuhr: Friday is skipped, next is Saturday the 14th.
        val thu = LocalDate.of(2026, 11, 12).atTime(14, 0).atZone(kolkata).toInstant().toEpochMilli()
        assertEquals(calc.day(LocalDate.of(2026, 11, 14))[Prayer.DHUHR], dhuhr.nextTrigger(calc, thu))
    }

    @Test
    fun hijriDatesMatchUmmAlQura() {
        assertEquals(shakir.swalah.prayer.HijriDate(1448, 6, 3), shakir.swalah.prayer.HijriDate.from(LocalDate.of(2026, 11, 13)))
        val ramadan = shakir.swalah.prayer.HijriDate(1448, 9, 1).toGregorian()!!
        println("1 Ramadan 1448 = $ramadan")
        assertEquals(shakir.swalah.prayer.HijriDate(1448, 9, 1), shakir.swalah.prayer.HijriDate.from(ramadan))
    }

    @Test
    fun hijriYearlyAndMonthlyReminders() {
        TimeZone.setDefault(TimeZone.getTimeZone(kolkata))
        val calc = PrayerCalculator(11.25, 75.78, CalculationMethod.KARACHI_SHAFI, noAdj, kolkata)
        val now = LocalDate.of(2026, 11, 13).atTime(15, 0).atZone(kolkata).toInstant().toEpochMilli()
        // Suhoor-style: Fajr - 1 h on 1 Ramadan every year.
        val ramadan = Reminder(base = TimeBase.PRAYER, prayer = Prayer.FAJR, offsetMinutes = -60, days = 0, date = "1448-09-01", yearly = true, hijri = true)
        val day = shakir.swalah.prayer.HijriDate(1448, 9, 1).toGregorian()!!
        assertEquals(calc.day(day)[Prayer.FAJR] - 60 * 60_000L, ramadan.nextTrigger(calc, now))
        // 13th of every Hijri month at 20:00: today is the 3rd, so the 13th of this month.
        val monthly = Reminder(base = TimeBase.FIXED, hour = 20, minute = 0, days = 0, monthDay = 13, hijri = true)
        val thirteenth = shakir.swalah.prayer.HijriDate(1448, 6, 13).toGregorian()!!
        assertEquals(thirteenth.atTime(20, 0).atZone(kolkata).toInstant().toEpochMilli(), monthly.nextTrigger(calc, now))
        // A past one-off Hijri date gives a past trigger (the scheduler then switches it off).
        val past = Reminder(base = TimeBase.FIXED, hour = 9, minute = 0, days = 0, date = "1448-01-10", hijri = true)
        assertTrue(past.nextTrigger(calc, now)!! < now)
    }

    @Test
    fun apiKeyShape() {
        // The real value depends on the private secret (api.properties), so only the format is checked:
        // one AES block, base64url without padding, different every second.
        val k1 = shakir.swalah.net.PrayerApi.apiKey(1700000000)
        val k2 = shakir.swalah.net.PrayerApi.apiKey(1700000001)
        assertEquals(22, k1.length)
        assertTrue(k1.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(k1 != k2)
    }
}
