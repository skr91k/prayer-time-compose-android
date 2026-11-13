package shakir.swalah.prayer

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

/** Umm al-Qura Hijri date in Arabic, e.g. "الجمعة، ١٢ جمادى الأولى ١٤٤٨ هـ". */
object Hijri {
    private val MONTHS = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )
    val MONTHS_EN = listOf(
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah",
    )

    /** Monday first, like java.time.DayOfWeek. */
    private val DAYS = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

    fun arabic(date: LocalDate = LocalDate.now()): String = runCatching {
        val h = HijrahDate.from(date)
        val day = h.get(ChronoField.DAY_OF_MONTH)
        val month = h.get(ChronoField.MONTH_OF_YEAR)
        val year = h.get(ChronoField.YEAR)
        "${DAYS[date.dayOfWeek.value - 1]}،  ${arabicDigits(day)} ${MONTHS[month - 1]} ${arabicDigits(year)} هـ"
    }.getOrDefault("")

    private fun arabicDigits(n: Int): String = n.toString().map { '٠' + (it - '0') }.joinToString("")
}

/** A date in the Umm al-Qura Hijri calendar (same calendar as the home screen). */
data class HijriDate(val year: Int, val month: Int, val day: Int) {

    /** The Gregorian day; a day 30 in a 29-day month falls on that month's last day. Null if out of range. */
    fun toGregorian(): LocalDate? = runCatching {
        val first = HijrahDate.of(year, month, 1)
        LocalDate.from(first.plus((minOf(day, first.lengthOfMonth()) - 1).toLong(), ChronoUnit.DAYS))
    }.getOrNull()

    /** Same day number, [n] Hijri months later (or earlier). */
    fun plusMonths(n: Int): HijriDate {
        val total = year * 12 + (month - 1) + n
        return HijriDate(total / 12, total % 12 + 1, day)
    }

    /** Stored form: "1448-09-01". */
    override fun toString(): String = "%04d-%02d-%02d".format(year, month, day)

    fun format(withYear: Boolean = true): String =
        "$day ${Hijri.MONTHS_EN[month - 1]}" + if (withYear) " $year AH" else ""

    companion object {
        fun parse(s: String): HijriDate? = runCatching {
            val p = s.split("-").map(String::toInt)
            HijriDate(p[0], p[1], p[2])
        }.getOrNull()

        fun from(date: LocalDate): HijriDate? = runCatching {
            val h = HijrahDate.from(date)
            HijriDate(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
        }.getOrNull()
    }
}
