package shakir.swalah.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import shakir.swalah.BuildConfig
import shakir.swalah.alarm.Reliability
import shakir.swalah.data.AppSettings
import shakir.swalah.data.SettingsStore
import shakir.swalah.data.ThirdOfNight
import shakir.swalah.reminder.ReminderStore
import shakir.swalah.ui.Routes
import shakir.swalah.ui.common.AppIcons
import shakir.swalah.ui.common.AppScaffold
import shakir.swalah.ui.common.ChoiceDialog
import shakir.swalah.ui.common.ReliabilityCard
import shakir.swalah.ui.common.SectionTitle
import shakir.swalah.ui.common.SettingItem

@Composable
fun SettingsScreen(resumeTick: Int, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val s = settings ?: AppSettings()
    val reminderVersion by ReminderStore.version.collectAsStateWithLifecycle()
    val reminders = remember(reminderVersion, resumeTick) { ReminderStore.all(context) }
    var localTick by remember { mutableIntStateOf(0) }
    val checks = remember(resumeTick, localTick) { Reliability.checks(context) }
    var dialog by remember { mutableStateOf<String?>(null) }

    AppScaffold(title = "Settings | الإعدادات", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionTitle("Prayer")
            SettingItem(
                "Reminders & alarms",
                "${reminders.count { it.enabled }} of ${reminders.size} on - adhan, iqama, Suhoor, Tahajjud, your own",
                icon = AppIcons.Alarm,
                onClick = { onOpen(Routes.REMINDERS) },
            )
            SettingItem("Adhan time settings", "Calculation method, adjustments, time format", icon = AppIcons.Tune, onClick = { onOpen(Routes.ADHAN_TIME) })
            SettingItem("Iqama settings", "Iqama times for the home countdown", icon = AppIcons.Mosque, onClick = { onOpen(Routes.IQAMA) })
            SettingItem("Location", s.location?.name?.ifBlank { "My Location" } ?: "Not set", icon = Icons.Default.LocationOn, onClick = { onOpen(Routes.LOCATION) })

            SectionTitle("Home screen")
            SettingItem("Mid night", if (s.showMidnight) "Show" else "Hide", icon = AppIcons.Night, onClick = { dialog = "midnight" })
            SettingItem("Third of the night", s.thirdOfNight.title, icon = AppIcons.Night, onClick = { dialog = "third" })

            SectionTitle("Alarm reliability")
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
                onManualDone = { Reliability.markManualDone(context, it); localTick++ },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SectionTitle("App")
            SettingItem("Share", "Tell others about the app", icon = AppIcons.Share, onClick = {
                val link = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
                val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, " أَذَان ‎ Lite \nPrayer Time and Notifications,  Qibla: \n$link")
                context.startActivity(Intent.createChooser(share, "Share via"))
            })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            SettingItem("Version", BuildConfig.VERSION_NAME, icon = Icons.Default.Info)
        }
    }

    when (dialog) {
        "midnight" -> ChoiceDialog(
            title = "Mid night", options = listOf(true, false), selected = s.showMidnight,
            label = { if (it) "Show" else "Hide" },
            onDismiss = { dialog = null },
            onSelect = { v -> SettingsStore.update(context) { it.copy(showMidnight = v) }; dialog = null },
        )
        "third" -> ChoiceDialog(
            title = "Third of the night", options = ThirdOfNight.entries.toList(), selected = s.thirdOfNight,
            label = { it.title },
            onDismiss = { dialog = null },
            onSelect = { v -> SettingsStore.update(context) { it.copy(thirdOfNight = v) }; dialog = null },
        )
    }
}
