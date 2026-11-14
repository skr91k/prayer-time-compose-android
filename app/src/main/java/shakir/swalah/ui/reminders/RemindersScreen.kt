package shakir.swalah.ui.reminders

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import shakir.swalah.ui.common.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import shakir.swalah.BuildConfig
import shakir.swalah.alarm.AlarmService
import shakir.swalah.alarm.Reliability
import shakir.swalah.alarm.ReminderController
import shakir.swalah.data.AppSettings
import shakir.swalah.data.SettingsStore
import shakir.swalah.prayer.PrayerCalculator
import shakir.swalah.prayer.TimeFormat
import shakir.swalah.reminder.Reminder
import shakir.swalah.reminder.ReminderStore
import shakir.swalah.reminder.TimeBase
import shakir.swalah.ui.RingingActivity
import shakir.swalah.ui.common.AppScaffold
import shakir.swalah.ui.common.ReliabilityCard
import shakir.swalah.ui.theme.AppColors
import shakir.swalah.ui.common.daysLabel
import shakir.swalah.ui.common.formatDayTime
import shakir.swalah.ui.common.offsetLabel
import shakir.swalah.ui.common.ringsIn
import java.time.LocalDate

/** All adhan, iqama and custom reminders, in the list style of the alarm app. */
@Composable
fun RemindersScreen(resumeTick: Int, onBack: () -> Unit, onSetLocation: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val s = settings ?: AppSettings()
    val calc = remember(s) { PrayerCalculator.from(s) }
    val format = remember(s) { TimeFormat(context, s) }
    val version by ReminderStore.version.collectAsStateWithLifecycle()
    var localTick by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val all = remember(version, resumeTick) { ReminderStore.all(context).filter { it.id != Reminder.TEST_ID } }
    // Ordered by when they ring today, so Tahajjud, Suhoor, Fajr, Fajr iqama... read in day order.
    val reminders = remember(all, calc, now) {
        val today = LocalDate.now()
        all.sortedBy { r ->
            val at = r.occurrenceOn(today, calc) ?: return@sortedBy r.hour * 60 + r.minute
            java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
        }
    }
    val checks = remember(version, resumeTick, localTick) { Reliability.checks(context) }
    val ringingState by AlarmService.ringing.collectAsStateWithLifecycle()
    val ringing = ringingState?.takeIf { !it.preview }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    /** Non-null while the "Restore built-in reminders?" dialog is open: the ones it would add back. */
    var confirmRestore by remember { mutableStateOf<List<Reminder>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun toastScheduled(r: Reminder) {
        if (!r.enabled) return
        val next = r.nextTrigger(calc)
        val text = if (next == null) "Saved - set your location so it can ring" else "Reminder set ${ringsIn(next)}"
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    /** Deletes at once; "Undo" for 3 seconds puts it back exactly as it was. */
    fun deleteWithUndo(r: Reminder) {
        ReminderController.delete(context, r.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = withTimeoutOrNull(3_000) {
                snackbar.showSnackbar("${r.label.ifBlank { "Reminder" }} deleted", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
            }
            if (result == SnackbarResult.ActionPerformed) toastScheduled(ReminderController.save(context, r))
        }
    }

    AppScaffold(
        title = "Reminders",
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Restore built-in reminders") }, onClick = {
                        menuOpen = false
                        confirmRestore = ReminderStore.missingPresets(context)
                    })
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Reminder(label = "", fullScreen = true) }) { Icon(Icons.Default.Add, "Add reminder") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (calc == null) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Set your location so prayer-based reminders can ring.", modifier = Modifier.weight(1f))
                        TextButton(onClick = onSetLocation) { Text("Set") }
                    }
                }
            }
            item { NextReminderCard(all, calc, format, now) }
            item {
                ReliabilityCard(
                    checks,
                    onFix = { check ->
                        val intent = check.fixIntent ?: return@ReliabilityCard
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                    },
                    onManualDone = { key -> Reliability.markManualDone(context, key); localTick++ },
                )
            }
            items(reminders, key = { it.id }) { r ->
                val isRinging = ringing?.id == r.id
                val locked = isRinging || (r.enabled && r.snoozedUntil > now)
                ReminderRow(
                    r, calc, format, now, locked, isRinging,
                    onToggle = { enabled -> toastScheduled(ReminderController.save(context, r.copy(enabled = enabled))) },
                    onAction = { action ->
                        when (action) {
                            RowAction.EDIT -> editing = r
                            RowAction.PREVIEW -> if (!AlarmService.preview(context, r.id)) {
                                Toast.makeText(context, "Another reminder is ringing", Toast.LENGTH_SHORT).show()
                            }
                            RowAction.DUPLICATE -> editing = r.duplicate()
                            RowAction.DELETE -> deleteWithUndo(r)
                            RowAction.SKIP -> {
                                val saved = ReminderController.skipNext(context, r)
                                val next = saved.nextTrigger(calc)
                                Toast.makeText(
                                    context,
                                    "Skipping ${formatDayTime(format, saved.skippedTrigger)}" + (next?.let { " - next ${formatDayTime(format, it)}" } ?: ""),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            RowAction.UNDO_SKIP -> toastScheduled(ReminderController.undoSkip(context, r))
                            RowAction.DISMISS -> context.startActivity(
                                if (isRinging) RingingActivity.intent(context) else RingingActivity.dismissIntent(context, r.id),
                            )
                        }
                    },
                    onClick = {
                        when {
                            isRinging -> context.startActivity(RingingActivity.intent(context))
                            locked -> context.startActivity(RingingActivity.dismissIntent(context, r.id))
                            else -> editing = r
                        }
                    },
                )
            }
            if (BuildConfig.DEBUG) item {
                OutlinedButton(
                    onClick = {
                        ReminderController.scheduleTest(context)
                        Toast.makeText(context, "Test reminder in 20 seconds - lock your phone now", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Test reminder (rings in 20 s)") }
            }
        }
    }

    confirmRestore?.let { missing ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(if (missing.isEmpty()) "Nothing to restore" else "Restore built-in reminders?") },
            text = {
                Text(
                    if (missing.isEmpty()) "All built-in reminders are already in the list."
                    else "These deleted reminders will be added back with their default settings:\n\n" +
                        missing.joinToString("\n") { "• " + it.label } +
                        "\n\nYour other reminders are not changed.",
                )
            },
            confirmButton = {
                if (missing.isEmpty()) {
                    TextButton(onClick = { confirmRestore = null }) { Text("OK") }
                } else {
                    TextButton(onClick = {
                        confirmRestore = null
                        val added = ReminderStore.restoreMissingPresets(context)
                        added.forEach { ReminderController.save(context, it) }
                        Toast.makeText(context, "Restored ${added.size}", Toast.LENGTH_SHORT).show()
                    }) { Text("Restore") }
                }
            },
            dismissButton = { if (missing.isNotEmpty()) TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }

    editing?.let { r ->
        ReminderEditor(
            initial = r,
            calc = calc,
            format = format,
            onDismiss = { editing = null },
            onSave = {
                toastScheduled(ReminderController.save(context, it))
                editing = null
            },
        )
    }
}

@Composable
private fun NextReminderCard(all: List<Reminder>, calc: PrayerCalculator?, format: TimeFormat, now: Long) {
    val next = all.filter { it.enabled }.mapNotNull { r -> r.nextTrigger(calc, now)?.let { r to it } }.minByOrNull { it.second }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Next reminder", color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (next == null) {
                Text("None set", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(formatDayTime(format, next.second), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("${next.first.label.ifBlank { "Reminder" }} · ${ringsIn(next.second, now)}", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

private enum class RowAction { EDIT, PREVIEW, DUPLICATE, SKIP, UNDO_SKIP, DISMISS, DELETE }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReminderRow(
    r: Reminder,
    calc: PrayerCalculator?,
    format: TimeFormat,
    now: Long,
    locked: Boolean,
    isRinging: Boolean,
    onToggle: (Boolean) -> Unit,
    onAction: (RowAction) -> Unit,
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    /** Non-null while the "Restore built-in reminders?" dialog is open: the ones it would add back. */
    var confirmRestore by remember { mutableStateOf<List<Reminder>?>(null) }
    val next = if (r.enabled) r.nextTrigger(calc, now) else null
    // Big time: when it rings today (prayer-based times move every day).
    val shownTime = when (r.base) {
        TimeBase.FIXED -> format.formatMinuteOfDay(r.hour * 60 + r.minute)
        TimeBase.PRAYER -> r.occurrenceOn(LocalDate.now(), calc)?.let(format::format) ?: "--:--"
    }
    // Adhan, iqama and other reminders each get their own background.
    val background = when {
        r.preset?.startsWith("adhan_") == true -> AppColors.ReminderAdhan
        r.preset?.startsWith("iqama_") == true -> AppColors.ReminderIqama
        else -> AppColors.ReminderOther
    }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        val dim = if (r.enabled) 1f else 0.45f
        Column(Modifier.padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‎" + shownTime,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        fun item(text: String, action: RowAction) = @Composable {
                            DropdownMenuItem(text = { Text(text) }, onClick = { menuOpen = false; onAction(action) })
                        }
                        if (locked) {
                            item(if (isRinging) "Open ringing reminder" else "Dismiss…", RowAction.DISMISS)()
                        } else {
                            item("Edit", RowAction.EDIT)()
                            item("Preview", RowAction.PREVIEW)()
                            if (r.enabled && r.isRepeating) {
                                if (r.isSkipping(calc, now)) item("Undo skip", RowAction.UNDO_SKIP)()
                                else r.regularTrigger(calc, now)?.let { item("Skip next (${formatDayTime(format, it)})", RowAction.SKIP)() }
                            }
                        }
                        item("Duplicate", RowAction.DUPLICATE)()
                        if (!locked && !r.isCoreBuiltIn) item("Delete", RowAction.DELETE)()
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (r.label.isNotBlank()) {
                        Text(
                            r.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        listOf(offsetLabel(r), daysLabel(r)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                    )
                    val status = when {
                        isRinging -> "Ringing - tap to open"
                        locked -> "Snoozed until ${formatDayTime(format, r.snoozedUntil)}"
                        r.enabled && r.isSkipping(calc, now) -> "Skipping ${formatDayTime(format, r.skippedTrigger)}"
                        else -> null
                    }
                    if (status == null && next != null) {
                        Text(
                            "Next: ${formatDayTime(format, next)} · ${ringsIn(next, now)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                        )
                    }
                    if (status != null) {
                        Text(status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (locked) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked until dismissed", tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                AppSwitch(checked = r.enabled, onCheckedChange = onToggle, enabled = !locked)
            }
        }
    }
}
