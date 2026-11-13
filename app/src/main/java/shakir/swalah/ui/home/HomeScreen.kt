package shakir.swalah.ui.home

import android.Manifest
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import shakir.swalah.ui.common.dpText
import shakir.swalah.ui.common.sdp
import shakir.swalah.ui.common.sdpText
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import shakir.swalah.R
import shakir.swalah.alarm.AlarmService
import shakir.swalah.alarm.Reliability
import shakir.swalah.prayer.CountdownPhase
import shakir.swalah.ui.Routes
import shakir.swalah.ui.common.AppIcons
import shakir.swalah.ui.theme.AppColors
import shakir.swalah.ui.theme.appBackground

private const val BOOKS_URL = "https://islamicbooks2.web.app/"
private const val COUNTER_URL = "https://utilitykit.web.app/counter"

@Composable
fun HomeScreen(
    resumeTick: Int,
    onOpen: (route: String) -> Unit,
    onOpenUrl: (String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Only the problems that really stop an adhan; the rest is in the reminders page.
    val critical = remember(resumeTick) {
        Reliability.checks(context).any { !it.ok && (it.title.startsWith("Exact") || it.title.startsWith("Notifications")) }
    }
    var fabOpen by rememberSaveable { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Asked here, not at launch: on first run the location screen asks for GPS, and two
    // permission prompts at once make Android deny the second one.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Like the old app: keep the screen on while an iqama countdown is running (e.g. a mosque display).
    val view = LocalView.current
    val keepOn = state.phase != CountdownPhase.NEXT_ADHAN
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    Box(
        Modifier
            .fillMaxSize()
            .appBackground()
            .pointerInput(Unit) {
                // Any touch stops a playing adhan (without consuming the touch).
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        AlarmService.stopIfNotWakeUp(context)
                    }
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                // Old paddingTopPortAndLand: 30sdp portrait, 8sdp landscape.
                .padding(top = if (landscape) 8.sdp else 30.sdp, bottom = 96.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.sdp)
                    .clickable { onOpen(Routes.LOCATION) }
                    .padding(vertical = 5.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(5.sdp))
                Text(state.locationName, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                Spacer(Modifier.width(16.dp))
                Text(state.hijriDate, color = Color.White, fontSize = 16.dpText, textAlign = TextAlign.Center)
            }
            if (critical) {
                Surface(
                    onClick = { onOpen(Routes.REMINDERS) },
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.sdp, vertical = 5.sdp),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Adhan may not play on time - tap to fix", fontSize = 14.sp)
                    }
                }
            }

            if (landscape) LandscapeTimes(state) else PortraitTimes(state)
        }

        SpeedDial(
            open = fabOpen,
            onToggle = { fabOpen = it },
            actions = listOf(
                FabAction("Reminders | التنبيهات", rememberVectorPainter(AppIcons.Alarm)) { onOpen(Routes.REMINDERS) },
                FabAction("Qibla | قِبْلَة", painterResource(R.drawable.qibla_button), tint = false) { onOpen(Routes.QIBLA) },
                FabAction("Settings | الإعدادات", rememberVectorPainter(Icons.Default.Settings)) { onOpen(Routes.SETTINGS) },
                FabAction("Month View", rememberVectorPainter(AppIcons.Calendar)) { onOpen(Routes.MONTH) },
                FabAction("Islamic Books | مكتبة الكتب", rememberVectorPainter(AppIcons.Book)) { onOpenUrl(BOOKS_URL) },
                FabAction("Counter | عداد", rememberVectorPainter(AppIcons.MoreHoriz)) { onOpenUrl(COUNTER_URL) },
            ),
        )
    }
}

@Composable
private fun PrayerRow(row: PrayerRowUi) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.sdp)
            .background(AppColors.PrayerRow, RoundedCornerShape(8.dp))
            .border(BorderStroke(Dp.Hairline, Color.White), RoundedCornerShape(8.dp))
            .padding(8.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = TextStyle(color = Color.White, fontSize = 18.sdpText)
        Text(row.prayer.arabic, style = style, modifier = Modifier.weight(1f))
        Text(":", style = style)
        Text("\u200E" + row.time, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** Old portrait layout: one column of six rows, 20sdp side margins for everything. */
@Composable
private fun PortraitTimes(state: HomeUiState) {
    Column(Modifier.padding(horizontal = 20.sdp)) {
        // Arabic names read right-to-left: name on the right, time on the left.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column { state.rows.forEach { PrayerRow(it) } }
        }
        state.midnight?.let { NightLine("Mid night", it) }
        state.lastThird?.let { NightLine("Third night", it) }
        Countdown(state, landscape = false)
    }
}

/** Old landscape layout: two columns (Fajr/Dhuhr/Maghrib | Shurooq/Asr/Isha), countdown beside its name. */
@Composable
private fun LandscapeTimes(state: HomeUiState) {
    Column {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row {
                Column(Modifier.weight(1f).padding(start = 20.sdp, end = 10.sdp)) {
                    state.rows.filterIndexed { i, _ -> i % 2 == 0 }.forEach { PrayerRow(it) }
                }
                Column(Modifier.weight(1f).padding(start = 10.sdp, end = 20.sdp)) {
                    state.rows.filterIndexed { i, _ -> i % 2 == 1 }.forEach { PrayerRow(it) }
                }
            }
        }
        Row(Modifier.padding(start = 20.sdp, end = 20.sdp, top = 4.dp)) {
            Box(Modifier.weight(1f)) { state.midnight?.let { NightLine("Mid night", it) } }
            Box(Modifier.weight(1f)) { state.lastThird?.let { NightLine("Third night", it) } }
            Spacer(Modifier.weight(1f))
        }
        Countdown(state, landscape = true)
    }
}

@Composable
private fun NightLine(label: String, time: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 10.sdpText, modifier = Modifier.width(100.sdp))
        Text(time, color = Color.White, fontSize = 10.sdpText, modifier = Modifier.width(50.sdp))
    }
}

/**
 * Old countdown.xml: the time is 70dp (its auto-size never applied because the view is
 * wrap_content), the name 30dp. Portrait stacks them; landscape puts them side by side.
 */
@Composable
private fun Countdown(state: HomeUiState, landscape: Boolean) {
    val color = when (state.phase) {
        CountdownPhase.NEXT_ADHAN -> Color.White
        CountdownPhase.BEFORE_IQAMA -> AppColors.CountdownGreen
        CountdownPhase.ADHAN_PASSED, CountdownPhase.IQAMA_PASSED -> AppColors.CountdownRed
    }
    val time = @Composable {
        Text(state.countdown, color = color, fontSize = 70.dpText, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
    val name = @Composable { Text(state.countdownTitle, color = color, fontSize = 30.dpText, textAlign = TextAlign.Center) }
    if (landscape) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                time()
                Spacer(Modifier.width(16.dp))
                name()
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            time()
            name()
        }
    }
}

private class FabAction(val label: String, val icon: Painter, val tint: Boolean = true, val onClick: () -> Unit)

/** Expandable FAB menu with a dimmed overlay, like the old speed-dial. */
@Composable
private fun SpeedDial(open: Boolean, onToggle: (Boolean) -> Unit, actions: List<FabAction>) {
    BackHandler(enabled = open) { onToggle(false) }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AppColors.Overlay)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle(false) },
            )
        }
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AnimatedVisibility(open, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    actions.forEach { action ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = { onToggle(false); action.onClick() },
                                color = AppColors.FabMini,
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(action.label, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            SmallFloatingActionButton(
                                onClick = { onToggle(false); action.onClick() },
                                containerColor = AppColors.FabMini,
                                contentColor = Color.White,
                            ) {
                                if (action.tint) Icon(action.icon, action.label, modifier = Modifier.size(22.dp))
                                else Image(action.icon, action.label, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { onToggle(!open) },
                containerColor = AppColors.FabAccent,
                contentColor = Color.White,
                modifier = Modifier.padding(end = 0.dp).height(56.dp),
            ) {
                Icon(if (open) Icons.Default.Close else AppIcons.Menu, if (open) "Close menu" else "Menu")
            }
        }
    }
}
