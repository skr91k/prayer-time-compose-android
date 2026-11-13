package shakir.swalah.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import shakir.swalah.data.AppSettings
import shakir.swalah.data.SettingsStore
import shakir.swalah.prayer.Prayer
import shakir.swalah.prayer.PrayerCalculator
import shakir.swalah.prayer.TimeFormat
import shakir.swalah.ui.common.AppScaffold
import shakir.swalah.ui.common.CenteredMessage
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PAGES = 100
private const val CURRENT_PAGE = 50

/** A month of prayer times per page; swipe for previous / next months. */
@Composable
fun MonthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val s = settings ?: AppSettings()
    val calc = remember(s) { PrayerCalculator.from(s) }
    val format = remember(s) { TimeFormat(context, s) }
    val pager = rememberPagerState(initialPage = CURRENT_PAGE) { PAGES }

    AppScaffold(title = "Month View", onBack = onBack) { padding ->
        if (calc == null) {
            CenteredMessage("Set your location first", Modifier.padding(padding))
            return@AppScaffold
        }
        HorizontalPager(pager, Modifier.padding(padding).fillMaxSize()) { page ->
            MonthPage(YearMonth.now().plusMonths((page - CURRENT_PAGE).toLong()), calc, format)
        }
    }
}

private class DayRow(val date: LocalDate, val times: List<String>)

@Composable
private fun MonthPage(month: YearMonth, calc: PrayerCalculator, format: TimeFormat) {
    val rows by produceState<List<DayRow>?>(null, month, calc) {
        value = withContext(Dispatchers.Default) {
            (1..month.lengthOfMonth()).map { d ->
                val date = month.atDay(d)
                val day = calc.day(date)
                DayRow(date, Prayer.entries.map { format.formatCompact(day[it]) })
            }
        }
    }
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize()) {
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
        TableRow(listOf("Date") + Prayer.entries.map { it.arabic }, header = true, highlight = false)
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows.orEmpty()) { row ->
                TableRow(listOf(row.date.dayOfMonth.toString()) + row.times, header = false, highlight = row.date == today)
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean, highlight: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        cells.forEachIndexed { i, text ->
            Text(
                text,
                fontSize = 13.sp,
                fontWeight = if (header || highlight) FontWeight.SemiBold else FontWeight.Normal,
                color = if (header) MaterialTheme.colorScheme.primary else Color.White,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(if (i == 0) 0.6f else 1f),
            )
        }
    }
}
