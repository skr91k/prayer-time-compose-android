package shakir.swalah.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import shakir.swalah.ui.common.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import shakir.swalah.prayer.Hijri
import shakir.swalah.prayer.HijriDate
import shakir.swalah.prayer.Prayer
import shakir.swalah.prayer.PrayerCalculator
import shakir.swalah.prayer.TimeFormat
import shakir.swalah.reminder.Reminder
import shakir.swalah.reminder.Sounds
import shakir.swalah.reminder.TimeBase
import shakir.swalah.ui.common.AppIcons
import shakir.swalah.ui.common.DAY_LETTERS
import shakir.swalah.ui.common.daysLabel
import shakir.swalah.ui.common.formatDateTime
import shakir.swalah.ui.common.minutesLabel
import shakir.swalah.ui.common.offsetLabel
import shakir.swalah.ui.common.ringsIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private const val MAX_OFFSET = 6 * 60

/** Create / edit a reminder: time from a prayer ± minutes, or a fixed clock time. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderEditor(
    initial: Reminder,
    calc: PrayerCalculator?,
    format: TimeFormat,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
) {
    val context = LocalContext.current
    var base by remember { mutableStateOf(initial.base) }
    var prayer by remember { mutableStateOf(initial.prayer) }
    var offset by remember { mutableIntStateOf(initial.offsetMinutes) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var days by remember { mutableIntStateOf(initial.days) }
    var mode by remember { mutableStateOf(RepeatMode.of(initial)) }
    var date by remember { mutableStateOf(initial.exactDate ?: LocalDate.now().plusDays(1)) }
    var monthDay by remember { mutableIntStateOf(if (initial.monthDay != 0 && !initial.hijri) initial.monthDay else LocalDate.now().dayOfMonth) }
    // Date / Monthly / Yearly can follow the Hijri calendar instead.
    var hijri by remember { mutableStateOf(initial.hijri) }
    val hijriToday = remember { HijriDate.from(LocalDate.now()) ?: HijriDate(1448, 1, 1) }
    var hijriDate by remember { mutableStateOf(initial.hijriDate ?: HijriDate.from(LocalDate.now().plusDays(1)) ?: hijriToday) }
    var hijriMonthDay by remember { mutableIntStateOf(if (initial.hijri && initial.monthDay != 0) initial.monthDay else hijriToday.day) }
    var showHijriPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf(initial.label) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var fullScreen by remember { mutableStateOf(initial.fullScreen) }
    var math by remember { mutableStateOf(initial.mathChallenge) }
    var snooze by remember { mutableIntStateOf(initial.snoozeMinutes) }
    var ringtone by remember { mutableStateOf(initial.ringtoneUri) }
    var autoStop by remember { mutableIntStateOf(initial.autoStopSeconds) }
    var autoSnooze by remember { mutableIntStateOf(initial.autoSnoozeSeconds) }
    var showSounds by remember { mutableStateOf(false) }
    val ringtoneTitle = remember(ringtone) { Sounds.title(context, ringtone) }

    if (showSounds) {
        SoundPickerDialog(current = ringtone, onDismiss = { showSounds = false }, onPick = { ringtone = it; showSounds = false })
    }
    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showHijriPicker) {
        HijriDatePickerDialog(
            initial = hijriDate,
            withYear = mode == RepeatMode.DATE,
            onDismiss = { showHijriPicker = false },
            onPick = { hijriDate = it; showHijriPicker = false },
        )
    }

    fun build(): Reminder = buildRepeat(initial, mode, days, date, monthDay, hijri, hijriDate, hijriMonthDay).copy(
        base = base, prayer = prayer, offsetMinutes = offset, hour = hour, minute = minute,
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    listOf(TimeBase.PRAYER to "Prayer time ±", TimeBase.FIXED to "Fixed time").forEachIndexed { i, (b, title) ->
                        SegmentedButton(
                            selected = base == b,
                            onClick = { base = b },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(title) }
                    }
                }
                Spacer(Modifier.height(16.dp))

                when (base) {
                    TimeBase.FIXED -> WheelTimePicker(
                        hour = hour, minute = minute, is24Hour = format.is24Hour,
                        onChange = { h, m -> hour = h; minute = m },
                    )
                    TimeBase.PRAYER -> PrayerOffsetPicker(
                        prayer = prayer, offset = offset, calc = calc, format = format,
                        onPrayer = { prayer = it }, onOffset = { offset = it },
                    )
                }

                val preview = build()
                val trigger = preview.nextTrigger(calc)
                val inPast = trigger != null && trigger <= System.currentTimeMillis()
                Text(
                    when {
                        trigger == null -> "Set your location to use prayer times"
                        inPast -> "That date and time has already passed"
                        else -> "Rings ${formatDateTime(format, trigger)} - ${ringsIn(trigger)}"
                    },
                    color = if (trigger == null || inPast) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )

                SectionLabel("Repeat")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    RepeatMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.title) }) }
                }
                Spacer(Modifier.height(10.dp))
                if (mode == RepeatMode.DATE || mode == RepeatMode.MONTHLY || mode == RepeatMode.YEARLY) {
                    SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                        listOf(false to "Gregorian", true to "Hijri").forEachIndexed { i, (h, title) ->
                            SegmentedButton(selected = hijri == h, onClick = { hijri = h }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(title) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                when {
                    hijri && (mode == RepeatMode.DATE || mode == RepeatMode.YEARLY) -> TextButton(onClick = { showHijriPicker = true }) {
                        Text(hijriDate.format(withYear = mode == RepeatMode.DATE), fontSize = 18.sp)
                    }
                    hijri && mode == RepeatMode.MONTHLY -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..30).chunked(7).forEach { week ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                week.forEach { d -> DayCircle(d.toString(), d == hijriMonthDay) { hijriMonthDay = d } }
                            }
                        }
                    }
                    else -> when (mode) {
                    RepeatMode.ONCE, RepeatMode.DAILY -> {}
                    RepeatMode.WEEKLY -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DAY_LETTERS.forEachIndexed { i, letter -> DayCircle(letter, days and (1 shl i) != 0) { days = days xor (1 shl i) } }
                    }
                    RepeatMode.DATE, RepeatMode.YEARLY -> TextButton(onClick = { showDatePicker = true }) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), fontSize = 18.sp)
                    }
                    RepeatMode.MONTHLY -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..31).chunked(7).forEach { week ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                week.forEach { d -> DayCircle(d.toString(), d == monthDay) { monthDay = d } }
                            }
                        }
                    }
                    }
                }
                Text(
                    daysLabel(preview) + if (mode == RepeatMode.DATE) " - switches off after it rings" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Spacer(Modifier.height(12.dp))
                if (initial.isCoreBuiltIn) {
                    // Adhan / Shurooq keep their name; only the timing and sound are editable.
                    Text(initial.label, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                } else {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label") },
                        placeholder = { Text(defaultLabel(base, prayer, offset)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }

                SettingRow("Sound", ringtoneTitle) { TextButton(onClick = { showSounds = true }) { Text("Change") } }
                SettingRow("Vibrate", null) { AppSwitch(vibrate, { vibrate = it }) }
                SettingRow("Show ringing screen", if (fullScreen) "Opens over the lock screen" else "Heads-up notification only") {
                    AppSwitch(fullScreen, { fullScreen = it })
                }

                SectionLabel("Plays")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    listOf(Reminder.PLAY_ONCE, 5, 15, 30, 60, 0).forEach { s ->
                        FilterChip(selected = autoStop == s, onClick = { autoStop = s }, label = { Text(playsLabel(s)) })
                    }
                }
                Text(
                    when {
                        autoStop == Reminder.PLAY_ONCE -> "Plays the sound once (a full adhan), then stops"
                        autoStop == 0 -> "Rings until you dismiss it (wake-up alarm)"
                        else -> "Rings $autoStop s, then stops by itself"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp),
                )
                if (autoStop == 0) {
                    SettingRow("Solve math to dismiss", "Keeps you from turning it off half asleep") { AppSwitch(math, { math = it }) }
                    SectionLabel("Snooze length")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10, 15).forEach { m -> FilterChip(selected = snooze == m, onClick = { snooze = m }, label = { Text("$m min") }) }
                    }
                    SectionLabel("Auto-snooze after")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 180, 300).forEach { s ->
                            FilterChip(selected = autoSnooze == s, onClick = { autoSnooze = s }, label = { Text(secondsLabel(s)) })
                        }
                    }
                    Text(
                        "If nobody touches it, it snoozes after ringing ${secondsLabel(autoSnooze)}, then rings again",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(enabled = !inPast && (mode != RepeatMode.WEEKLY || days != 0), onClick = {
                        onSave(
                            build().copy(
                                label = if (initial.isCoreBuiltIn) initial.label else label.trim().ifEmpty { defaultLabel(base, prayer, offset) },
                                vibrate = vibrate,
                                fullScreen = fullScreen,
                                mathChallenge = math,
                                snoozeMinutes = snooze,
                                ringtoneUri = ringtone,
                                autoStopSeconds = autoStop,
                                autoSnoozeSeconds = autoSnooze,
                                enabled = true,
                            ),
                        )
                    }) { Text("Save") }
                }
            }
        }
    }
}

/** Prayer chips + "before / after" minutes stepper. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PrayerOffsetPicker(
    prayer: Prayer,
    offset: Int,
    calc: PrayerCalculator?,
    format: TimeFormat,
    onPrayer: (Prayer) -> Unit,
    onOffset: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        Prayer.entries.forEach { p ->
            FilterChip(selected = prayer == p, onClick = { onPrayer(p) }, label = { Text("${p.english} · ${p.arabic}") })
        }
    }
    Spacer(Modifier.height(12.dp))
    val direction = offset.sign()
    SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 24.dp)) {
        listOf(-1 to "Before", 0 to "At", 1 to "After").forEachIndexed { i, (d, title) ->
            SegmentedButton(
                selected = direction == d,
                onClick = {
                    val size = abs(offset).takeIf { it > 0 } ?: 10
                    onOffset(d * size)
                },
                shape = SegmentedButtonDefaults.itemShape(i, 3),
            ) { Text(title) }
        }
    }
    if (direction != 0) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            fun change(by: Int) {
                val size = (abs(offset) + by).coerceIn(1, MAX_OFFSET)
                onOffset(direction * size)
            }
            TextButton(onClick = { change(-5) }) { Text("−5") }
            FilledTonalIconButton(onClick = { change(-1) }) { Icon(AppIcons.Remove, "1 minute less") }
            Text(
                minutesLabel(abs(offset)),
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(130.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            FilledTonalIconButton(onClick = { change(1) }) { Icon(Icons.Default.Add, "1 minute more") }
            TextButton(onClick = { change(5) }) { Text("+5") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            listOf(5, 10, 15, 20, 30, 60).forEach { m ->
                FilterChip(selected = abs(offset) == m, onClick = { onOffset(direction * m) }, label = { Text(minutesLabel(m)) })
            }
        }
    }
    val today = calc?.today()
    Text(
        offsetLabel(prayer.english, offset) +
            (today?.let { " · today ${format.format(it[prayer] + offset * 60_000L)} (${prayer.english} ${format.format(it[prayer])})" } ?: ""),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun Int.sign(): Int = when {
    this > 0 -> 1
    this < 0 -> -1
    else -> 0
}

private fun defaultLabel(base: TimeBase, prayer: Prayer, offset: Int): String = when (base) {
    TimeBase.FIXED -> "Reminder"
    TimeBase.PRAYER -> if (offset == 0) prayer.arabic else offsetLabel(prayer.english, offset)
}

private fun playsLabel(s: Int): String = when (s) {
    Reminder.PLAY_ONCE -> "Once"
    0 -> "Until dismissed"
    else -> "$s s"
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
private fun SettingRow(title: String, subtitle: String?, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

enum class RepeatMode(val title: String) {
    ONCE("Once"), DAILY("Daily"), WEEKLY("Weekly"), DATE("Date"), MONTHLY("Monthly"), YEARLY("Yearly");

    companion object {
        fun of(r: Reminder) = when {
            r.date != null && r.yearly -> YEARLY
            r.date != null -> DATE
            r.monthDay != 0 -> MONTHLY
            r.days == Reminder.EVERY_DAY -> DAILY
            r.days != 0 -> WEEKLY
            else -> ONCE
        }
    }
}

/** Sets exactly one repeat kind on [base] and clears the others. */
private fun buildRepeat(
    base: Reminder, mode: RepeatMode, days: Int, date: LocalDate, monthDay: Int,
    hijri: Boolean, hijriDate: HijriDate, hijriMonthDay: Int,
): Reminder = when (mode) {
    RepeatMode.ONCE -> base.copy(days = 0, date = null, monthDay = 0, yearly = false, hijri = false)
    RepeatMode.DAILY -> base.copy(days = Reminder.EVERY_DAY, date = null, monthDay = 0, yearly = false, hijri = false)
    RepeatMode.WEEKLY -> base.copy(days = days, date = null, monthDay = 0, yearly = false, hijri = false)
    RepeatMode.DATE -> base.copy(days = 0, date = if (hijri) hijriDate.toString() else date.toString(), monthDay = 0, yearly = false, hijri = hijri)
    RepeatMode.YEARLY -> base.copy(days = 0, date = if (hijri) hijriDate.toString() else date.toString(), monthDay = 0, yearly = true, hijri = hijri)
    RepeatMode.MONTHLY -> base.copy(days = 0, date = null, monthDay = if (hijri) hijriMonthDay else monthDay, yearly = false, hijri = hijri)
}

/** Hijri day / month (and year for a one-off date) picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HijriDatePickerDialog(initial: HijriDate, withYear: Boolean, onDismiss: () -> Unit, onPick: (HijriDate) -> Unit) {
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.month) }
    var day by remember { mutableIntStateOf(initial.day) }
    val picked = HijriDate(year, month, day)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (withYear) "Hijri date" else "Every year on (Hijri)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                if (withYear) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { year-- }) { Icon(AppIcons.Remove, "Previous year") }
                        Text("$year AH", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
                        FilledTonalIconButton(onClick = { year++ }) { Icon(Icons.Default.Add, "Next year") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    Hijri.MONTHS_EN.forEachIndexed { i, name ->
                        FilterChip(selected = month == i + 1, onClick = { month = i + 1 }, label = { Text(name) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 6 per row: 7 don't fit the narrower alert dialog.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..30).chunked(6).forEach { week ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            week.forEach { d -> DayCircle(d.toString(), d == day) { day = d } }
                        }
                    }
                }
                // The picked day in full (for Yearly: its coming occurrence), Hijri first, then Gregorian.
                val occurrence = if (withYear) picked else nextYearly(picked)
                val greg = occurrence?.toGregorian()
                if (occurrence != null && greg != null) {
                    Text(
                        occurrence.format(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        greg.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(picked) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The coming occurrence of a yearly Hijri day (this Hijri year, or next if it has passed). */
private fun nextYearly(d: HijriDate): HijriDate? {
    val today = HijriDate.from(LocalDate.now()) ?: return null
    val thisYear = HijriDate(today.year, d.month, d.day)
    val greg = thisYear.toGregorian() ?: return null
    return if (greg.isBefore(LocalDate.now())) HijriDate(today.year + 1, d.month, d.day) else thisYear
}

@Composable
private fun DayCircle(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        Text(text, fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun secondsLabel(s: Int): String = if (s % 60 == 0) "${s / 60} min" else "$s s"
