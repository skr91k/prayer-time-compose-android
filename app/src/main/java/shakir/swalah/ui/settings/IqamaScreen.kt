package shakir.swalah.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import shakir.swalah.ui.common.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import shakir.swalah.data.AppSettings
import shakir.swalah.data.IqamaRule
import shakir.swalah.data.SettingsStore
import shakir.swalah.prayer.HomeTimes
import shakir.swalah.prayer.Prayer
import shakir.swalah.prayer.PrayerCalculator
import shakir.swalah.prayer.TimeFormat
import shakir.swalah.ui.common.AppIcons
import shakir.swalah.ui.common.AppScaffold
import shakir.swalah.ui.common.SettingItem
import shakir.swalah.ui.reminders.WheelTimePicker
import java.time.Instant
import java.time.ZoneId

/**
 * Iqama times used by the green countdown on the home screen. Iqama *alarms* are separate
 * reminders with their own offset and are not changed here.
 */
@Composable
fun IqamaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val s = settings ?: AppSettings()
    val format = remember(s) { TimeFormat(context, s) }
    val today = remember(s) { PrayerCalculator.from(s)?.today() }
    var pickingFixed by remember { mutableStateOf<Prayer?>(null) }

    fun setRule(prayer: Prayer, rule: IqamaRule) = SettingsStore.update(context) { cur ->
        cur.copy(iqama = cur.iqama.toMutableList().also { it[Prayer.WITH_IQAMA.indexOf(prayer)] = rule })
    }

    AppScaffold(title = "Iqama settings", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingItem(
                "Iqama countdown on home",
                "Counts down from adhan to iqama in green",
                onClick = { SettingsStore.update(context) { it.copy(iqamaCountdown = !it.iqamaCountdown) } },
            ) { AppSwitch(s.iqamaCountdown, { v -> SettingsStore.update(context) { it.copy(iqamaCountdown = v) } }) }
            Text(
                "Iqama alarms are set separately in Reminders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Prayer.WITH_IQAMA.forEach { p ->
                val rule = s.iqamaFor(p) ?: IqamaRule()
                val iqamaAt = today?.let { HomeTimes.iqamaTime(s, it, p) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.arabic, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        Text(
                            "‎" + (iqamaAt?.let { "Iqama " + format.format(it) } ?: ""),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(rule.afterAdhan, { setRule(p, rule.copy(afterAdhan = true)) }, SegmentedButtonDefaults.itemShape(0, 2)) {
                            Text("After adhan")
                        }
                        SegmentedButton(!rule.afterAdhan, {
                            setRule(p, rule.copy(afterAdhan = false))
                            if (rule.fixedMinuteOfDay < 0) pickingFixed = p
                        }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("At fixed time") }
                    }
                    if (rule.afterAdhan) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.weight(1f))
                            FilledTonalIconButton(onClick = { setRule(p, rule.copy(minutes = (rule.minutes - 1).coerceAtLeast(0))) }) {
                                Icon(AppIcons.Remove, "One minute less")
                            }
                            Text("${rule.minutes} min", fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.width(100.dp))
                            FilledTonalIconButton(onClick = { setRule(p, rule.copy(minutes = (rule.minutes + 1).coerceAtMost(180))) }) {
                                Icon(Icons.Default.Add, "One minute more")
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    } else {
                        TextButton(onClick = { pickingFixed = p }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (rule.fixedMinuteOfDay >= 0) "‎" + format.formatMinuteOfDay(rule.fixedMinuteOfDay) else "Pick a time",
                                fontSize = 22.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    pickingFixed?.let { p ->
        val rule = s.iqamaFor(p) ?: IqamaRule()
        val start = if (rule.fixedMinuteOfDay >= 0) rule.fixedMinuteOfDay
        else today?.let { Instant.ofEpochMilli(it[p] + 30 * 60_000L).atZone(ZoneId.systemDefault()).let { t -> t.hour * 60 + t.minute } } ?: 12 * 60
        var hour by remember(p) { mutableIntStateOf(start / 60) }
        var minute by remember(p) { mutableIntStateOf(start % 60) }
        AlertDialog(
            onDismissRequest = { pickingFixed = null },
            title = { Text("${p.arabic} iqama") },
            text = { WheelTimePicker(hour, minute, format.is24Hour, onChange = { h, m -> hour = h; minute = m }) },
            confirmButton = {
                TextButton(onClick = {
                    setRule(p, rule.copy(afterAdhan = false, fixedMinuteOfDay = hour * 60 + minute))
                    pickingFixed = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingFixed = null }) { Text("Cancel") } },
        )
    }
}
