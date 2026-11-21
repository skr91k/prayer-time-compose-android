package shakir.swalah.ui.nav

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import shakir.swalah.data.AppSettings
import shakir.swalah.data.SettingsStore
import shakir.swalah.ui.common.AppScaffold

/** Picks which pages sit in the bottom bar between Home and More. */
@Composable
fun BottomBarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val picked = (settings ?: AppSettings()).bottomBar
    val count = NavDest.PICKABLE.count { it.name in picked }
    AppScaffold(title = "Bottom bar", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                "Home and More are always shown. Pick up to ${NavDest.MAX_PICKED} more pages ($count chosen).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            listOf(NavDest.HOME).forEach { Item(it, checked = true, enabled = false) {} }
            NavDest.PICKABLE.forEach { d ->
                val on = d.name in picked
                Item(d, checked = on, enabled = on || count < NavDest.MAX_PICKED) {
                    SettingsStore.update(context) { s ->
                        s.copy(bottomBar = if (on) s.bottomBar - d.name else s.bottomBar + d.name)
                    }
                }
            }
            Item(NavDest.MORE, checked = true, enabled = false) {}
        }
    }
}

@Composable
private fun Item(d: NavDest, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val alpha = if (enabled || checked) 1f else 0.4f
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (d.tint) Icon(d.icon(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), modifier = Modifier.size(24.dp))
        else Image(d.icon(), null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(d.longTitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
