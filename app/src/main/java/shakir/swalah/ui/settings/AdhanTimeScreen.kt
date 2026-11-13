package shakir.swalah.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import shakir.swalah.data.SettingsStore
import shakir.swalah.prayer.CalculationMethod
import shakir.swalah.prayer.Prayer
import shakir.swalah.prayer.PrayerCalculator
import shakir.swalah.prayer.TimeFormat
import shakir.swalah.ui.common.AppIcons
import shakir.swalah.ui.common.AppScaffold
import shakir.swalah.ui.common.ChoiceDialog
import shakir.swalah.ui.common.SectionTitle
import shakir.swalah.ui.common.SettingItem

/** Calculation method, per-prayer minute adjustments and the time format. */
@Composable
fun AdhanTimeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val s = settings ?: AppSettings()
    val format = remember(s) { TimeFormat(context, s) }
    val today = remember(s) { PrayerCalculator.from(s)?.today() }
    var dialog by remember { mutableStateOf<String?>(null) }
    val method = CalculationMethod.at(s.methodIndex)

    AppScaffold(title = "Adhan time settings", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionTitle("Calculation")
            SettingItem(method.title, method.details, onClick = { dialog = "method" })

            SectionTitle("Adjust each time (minutes)")
            Prayer.entries.forEach { p ->
                val adj = s.adjustments[p.ordinal]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.arabic, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text("‎" + (today?.let { format.format(it[p]) } ?: "--:--"), fontSize = 18.sp, modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { setAdjustment(context, p, adj - 1) }) { Icon(AppIcons.Remove, "Minus one minute") }
                    Text(
                        (if (adj > 0) "+" else "") + adj,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        color = if (adj == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(48.dp),
                    )
                    FilledTonalIconButton(onClick = { setAdjustment(context, p, adj + 1) }) { Icon(Icons.Default.Add, "Plus one minute") }
                }
            }

            SectionTitle("Time format")
            val is24 = s.use24Hour ?: DateFormat.is24HourFormat(context)
            SettingItem("Clock", if (is24) "24 Hour" else "12 Hour", onClick = { dialog = "clock" })
            SettingItem(
                "Show AM / PM",
                "‎" + format.format(System.currentTimeMillis()),
                enabled = !is24,
                onClick = { dialog = "ampm" },
            )
        }
    }

    when (dialog) {
        "method" -> ChoiceDialog(
            title = "Calculation methods",
            options = CalculationMethod.entries.toList(),
            selected = method,
            label = { it.title },
            description = { it.details },
            onDismiss = { dialog = null },
            onSelect = { m -> SettingsStore.update(context) { it.copy(methodIndex = m.ordinal) }; dialog = null },
        )
        "clock" -> ChoiceDialog(
            title = "Clock", options = listOf(false, true), selected = s.use24Hour ?: DateFormat.is24HourFormat(context),
            label = { if (it) "24 Hour" else "12 Hour" },
            onDismiss = { dialog = null },
            onSelect = { v -> SettingsStore.update(context) { it.copy(use24Hour = v) }; dialog = null },
        )
        "ampm" -> ChoiceDialog(
            title = "Show AM / PM", options = listOf(true, false), selected = s.showAmPm,
            label = { if (it) "Show" else "Hide" },
            onDismiss = { dialog = null },
            onSelect = { v -> SettingsStore.update(context) { it.copy(showAmPm = v) }; dialog = null },
        )
    }
}

private fun setAdjustment(context: android.content.Context, prayer: Prayer, value: Int) {
    SettingsStore.update(context) { s ->
        s.copy(adjustments = s.adjustments.toMutableList().also { it[prayer.ordinal] = value.coerceIn(-120, 120) })
    }
}
