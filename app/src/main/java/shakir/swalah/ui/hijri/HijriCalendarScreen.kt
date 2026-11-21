package shakir.swalah.ui.hijri

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import shakir.swalah.data.SettingsStore
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shakir.swalah.prayer.Hijri
import shakir.swalah.prayer.HijriDate
import shakir.swalah.ui.common.AppScaffold
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val PAGES = 241
private const val CURRENT_PAGE = 120
private val WEEK = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val SHORT_MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

/** A Hijri month spans two Gregorian months: each gets its own colour, in the title and on its days. */
private val GREGORIAN_COLORS = listOf(Color(0xFF64B5F6), Color(0xFFF48FB1))

private fun gregorianColor(date: LocalDate, first: LocalDate): Color =
    if (date.month == first.month) GREGORIAN_COLORS[0] else GREGORIAN_COLORS[1]

/** A Hijri month per page with the Gregorian date of each day; swipe for other months. */
@Composable
fun HijriCalendarScreen(onBack: () -> Unit, onAddReminder: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val offset = settings?.hijriOffset ?: 0
    val today = remember { LocalDate.now() }
    val thisMonth = remember(offset) { (HijriDate.from(today) ?: HijriDate(1448, 1, 1)).copy(day = 1) }
    val pager = rememberPagerState(initialPage = CURRENT_PAGE) { PAGES }
    AppScaffold(title = "Hijri Calendar", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                HijriMonth(thisMonth.plusMonths(page - CURRENT_PAGE), today, offset, onAddReminder)
            }
            OffsetRow(offset) { new -> SettingsStore.update(context) { it.copy(hijriOffset = new) } }
        }
    }
}

/** Moves every Hijri date (calendar, home screen, Hijri reminders) by a day or two. */
@Composable
private fun OffsetRow(offset: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Adjust Hijri date", color = Color.White, fontWeight = FontWeight.Medium)
            Text(
                when {
                    offset == 0 -> "As calculated"
                    offset > 0 -> "+$offset day" + if (offset > 1) "s" else ""
                    else -> "$offset day" + if (offset < -1) "s" else ""
                },
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onChange(offset - 1) }, enabled = offset > -Hijri.MAX_OFFSET) {
            Text("−", fontSize = 24.sp)
        }
        Text(if (offset > 0) "+$offset" else "$offset", color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.width(32.dp))
        IconButton(onClick = { onChange(offset + 1) }, enabled = offset < Hijri.MAX_OFFSET) {
            Text("+", fontSize = 24.sp)
        }
    }
}

@Composable
private fun HijriMonth(month: HijriDate, today: LocalDate, offset: Int, onAddReminder: (LocalDate) -> Unit) {
    val first = remember(month, offset) { month.toGregorian() } ?: return
    val length = remember(month, offset) {
        (HijriDate(month.year, month.month, 30).toGregorian()?.let { ChronoUnit.DAYS.between(first, it).toInt() + 1 } ?: 29)
    }
    val last = first.plusDays(length - 1L)
    val lead = first.dayOfWeek.value % 7 // Sunday first
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Text(
            "${Hijri.MONTHS_EN[month.month - 1]} ${month.year} AH",
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Text(
            buildAnnotatedString {
                append("${Hijri.monthArabic(month.month)} · ")
                withStyle(SpanStyle(color = gregorianColor(first, first))) {
                    append(first.format(if (first.year == last.year) SHORT_MONTH else MONTH_YEAR))
                }
                append(" – ")
                withStyle(SpanStyle(color = gregorianColor(last, first))) { append(last.format(MONTH_YEAR)) }
            },
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            WEEK.forEachIndexed { i, d ->
                Text(
                    d, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    color = if (i == 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val cells = lead + length
        for (week in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 7) {
                    val index = week * 7 + col - lead
                    Box(Modifier.weight(1f).aspectRatio(0.85f).padding(vertical = 2.dp)) {
                        if (index in 0 until length) {
                            val date = first.plusDays(index.toLong())
                            DayCell(index + 1, date, today, col == 5, gregorianColor(date, first), onAddReminder)
                        }
                    }
                }
            }
        }
        Text(
            "Dates follow the Umm al-Qura calendar and may differ by a day from local moon sighting. " +
                "Use Adjust below to match your area. Tap a day to add a reminder on it. " +
                "Please check with your local masjid or community for the exact Hijri date.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Composable
private fun DayCell(hijriDay: Int, date: LocalDate, today: LocalDate, friday: Boolean, gregorianColor: Color, onAddReminder: (LocalDate) -> Unit) {
    val isToday = date == today
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isToday) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f))
            // Today or later: tap to add a reminder on that day.
            .then(if (date >= today) Modifier.clickable { onAddReminder(date) } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            hijriDay.toString(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            color = when {
                isToday -> Color.Black
                friday -> MaterialTheme.colorScheme.primary
                else -> Color.White
            },
        )
        Text(
            if (date.dayOfMonth == 1 || hijriDay == 1) "${date.dayOfMonth} ${date.format(SHORT_MONTH)}" else date.dayOfMonth.toString(),
            fontSize = 11.sp, maxLines = 1,
            color = if (isToday) Color.Black.copy(alpha = 0.7f) else gregorianColor,
        )
    }
}
