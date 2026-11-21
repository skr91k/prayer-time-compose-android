package shakir.swalah.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import shakir.swalah.ui.nav.NavDest
import shakir.swalah.ui.nav.BottomBarScreen
import shakir.swalah.data.AppSettings
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import shakir.swalah.PrayerApp
import shakir.swalah.alarm.AlarmService
import shakir.swalah.alarm.ReminderScheduler
import shakir.swalah.net.NetSync
import shakir.swalah.data.SettingsStore
import shakir.swalah.ui.hijri.HijriCalendarScreen
import shakir.swalah.ui.home.HomeScreen
import shakir.swalah.ui.location.LocationScreen
import shakir.swalah.ui.month.MonthScreen
import shakir.swalah.ui.qibla.QiblaScreen
import shakir.swalah.ui.reminders.RemindersScreen
import shakir.swalah.ui.settings.AdhanTimeScreen
import shakir.swalah.ui.settings.IqamaScreen
import shakir.swalah.ui.settings.SettingsScreen
import shakir.swalah.ui.theme.AdhanTheme

object Routes {
    const val HOME = "home"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
    const val ADHAN_TIME = "adhan_time"
    const val IQAMA = "iqama"
    const val LOCATION = "location"
    const val LOCATION_FIRST = "location_first"
    const val MONTH = "month"
    const val QIBLA = "qibla"
    const val HIJRI = "hijri"
    const val BOTTOM_BAR = "bottom_bar"
    /** Reminders with a new one-day reminder open, e.g. "reminders_new/2026-12-01". */
    const val REMINDER_ON_DATE = "reminders_new/{date}"
    fun reminderOnDate(date: java.time.LocalDate) = "reminders_new/$date"
}

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so screens re-read permission / reliability state. */
    private val resumeTick = mutableIntStateOf(0)

    /** Screen a launcher shortcut asked for (res/xml/shortcuts.xml "route" extra); null once handled. */
    private val shortcutRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val start = if (SettingsStore.get(this).location == null) Routes.LOCATION_FIRST else Routes.HOME
        if (savedInstanceState == null) shortcutRoute.value = intent.shortcutRoute()
        if (savedInstanceState == null && intent.fromLauncherIcon()) NetSync.checkUpdate(this)
        setContent {
            AdhanTheme {
                AppNavigation(start, resumeTick.intValue, shortcutRoute.value) { shortcutRoute.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.shortcutRoute()?.let { shortcutRoute.value = it }
    }

    /** Started by tapping the app icon (not a shortcut, notification, or recents). */
    private fun Intent.fromLauncherIcon(): Boolean =
        action == Intent.ACTION_MAIN && hasCategory(Intent.CATEGORY_LAUNCHER) &&
            flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0

    private fun Intent.shortcutRoute(): String? =
        getStringExtra(EXTRA_ROUTE)?.takeIf { it == Routes.QIBLA || it == Routes.REMINDERS }

    override fun onResume() {
        super.onResume()
        // Covers the first unlock after a direct-boot start, then: cheap insurance, re-arm all.
        // Off the main thread: re-arming reads/writes every reminder and would delay the first frame.
        val app = application as PrayerApp
        Thread {
            app.prepareData()
            ReminderScheduler.rescheduleAll(app, "app opened")
        }.start()
        // Adhan audio downloads (only what's missing) and the daily device log.
        NetSync.onAppOpen(this)
        resumeTick.intValue++
    }

    /** Like the old app: a volume key stops a playing adhan. */
    companion object {
        const val EXTRA_ROUTE = "route"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            AlarmService.stopIfNotWakeUp(this)
        ) return true
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun AppNavigation(start: String, resumeTick: Int, shortcutRoute: String?, onShortcutHandled: () -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    // Shortcut: open the screen on top of Home (so Back lands on Home). Ignored until a location is set.
    LaunchedEffect(shortcutRoute) {
        val route = shortcutRoute ?: return@LaunchedEffect
        onShortcutHandled()
        if (nav.currentDestination?.route == Routes.LOCATION_FIRST) return@LaunchedEffect
        nav.navigate(route) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val bar = NavDest.bar((settings ?: AppSettings()).bottomBar)
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    // The bar shows on Home and on the pages it holds, not on deeper pages (editors, location...).
    val selected = bar.firstOrNull { it.route != null && it.route == current }
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (selected != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    bar.forEach { d ->
                        NavigationBarItem(
                            selected = d == selected,
                            onClick = {
                                when {
                                    d.url != null -> openInBrowser(context, d.url)
                                    d == NavDest.HOME -> nav.popBackStack(Routes.HOME, inclusive = false)
                                    d != selected -> nav.navigate(d.route!!) {
                                        popUpTo(Routes.HOME)
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = {
                                if (d.tint) Icon(d.icon(), d.title, modifier = Modifier.size(24.dp))
                                else Image(d.icon(), d.title, modifier = Modifier.size(24.dp))
                            },
                            label = { Text(d.title, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
    NavHost(navController = nav, startDestination = start, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
        composable(Routes.HOME) {
            HomeScreen(
                resumeTick = resumeTick,
                onOpen = { route -> nav.navigate(route) },
                onOpenUrl = { openInBrowser(context, it) },
            )
        }
        composable(Routes.REMINDERS) { RemindersScreen(resumeTick, onBack = { nav.close(Routes.REMINDERS) }, onSetLocation = { nav.navigate(Routes.LOCATION) }) }
        composable(Routes.SETTINGS) { SettingsScreen(resumeTick, onBack = null, onOpen = { nav.navigate(it) }) }
        composable(Routes.BOTTOM_BAR) { BottomBarScreen(onBack = { nav.close(Routes.BOTTOM_BAR) }) }
        composable(Routes.ADHAN_TIME) { AdhanTimeScreen(onBack = { nav.close(Routes.ADHAN_TIME) }) }
        composable(Routes.IQAMA) { IqamaScreen(onBack = { nav.close(Routes.IQAMA) }) }
        composable(Routes.MONTH) { MonthScreen(onBack = { nav.close(Routes.MONTH) }) }
        composable(Routes.HIJRI) {
            HijriCalendarScreen(onBack = { nav.close(Routes.HIJRI) }, onAddReminder = { nav.navigate(Routes.reminderOnDate(it)) })
        }
        composable(Routes.REMINDER_ON_DATE) { entry ->
            val date = entry.arguments?.getString("date")?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            RemindersScreen(
                resumeTick, onBack = { nav.close(Routes.REMINDER_ON_DATE) },
                onSetLocation = { nav.navigate(Routes.LOCATION) }, newOnDate = date,
            )
        }
        composable(Routes.QIBLA) { QiblaScreen(onBack = { nav.close(Routes.QIBLA) }) }
        composable(Routes.LOCATION) { LocationScreen(firstRun = false, onDone = { nav.close(Routes.LOCATION) }) }
        composable(Routes.LOCATION_FIRST) {
            LocationScreen(firstRun = true, onDone = { nav.goHomeFromFirstRun() })
        }
    }
    }
}

/**
 * Closes [route] only if it is on the back stack. A plain popBackStack() fired twice (e.g. a tap
 * while the screen is already closing) would also pop Home and leave a blank window.
 */
private fun NavHostController.close(route: String) {
    popBackStack(route, inclusive = true)
}

private fun NavHostController.goHomeFromFirstRun() {
    if (currentDestination?.route != Routes.LOCATION_FIRST) return
    navigate(Routes.HOME) { popUpTo(Routes.LOCATION_FIRST) { inclusive = true } }
}

/** Opens in Chrome when installed (as before), otherwise in any browser. */
fun openInBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    val chrome = Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.chrome")
    try {
        context.startActivity(chrome)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
        }
    }
}
