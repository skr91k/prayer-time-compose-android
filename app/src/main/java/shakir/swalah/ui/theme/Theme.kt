package shakir.swalah.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

/** Colours of the original app: dark grey gradient, purple-tinted prayer rows, gold accents. */
object AppColors {
    val BgStart = Color(0xFF2B2B2B)
    val BgCenter = Color(0xFF313335)
    val BgEnd = Color(0xFF3C3F41)
    val PrayerRow = Color(0x25673AB7)
    val Overlay = Color(0xD0000000)
    val Gold = Color(0xFFFFB300)
    val CountdownGreen = Color(0xFF00FF00)
    val CountdownRed = Color(0xFFFF0000)
    /** Old speed-dial main button: colorAccent. */
    val FabAccent = Color(0xFFD81B60)
    val FabMini = Color(0xFF1A1A1A)
    /** Reminder cards: built-in adhan, iqama, everything else (incl. Shurooq). */
    val ReminderAdhan = Color(0xFF34304A)
    val ReminderIqama = Color(0xFF24393A)
    val ReminderOther = Color(0xFF35383B)
}

private val colors = darkColorScheme(
    primary = AppColors.Gold,
    onPrimary = Color(0xFF1B1200),
    primaryContainer = Color(0xFF3A2E00),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFF8FB4FF),
    background = AppColors.BgStart,
    surface = AppColors.BgStart,
    surfaceVariant = Color(0xFF3A3D40),
    surfaceContainer = Color(0xFF35383B),
    surfaceContainerHigh = Color(0xFF3C3F42),
    surfaceContainerHighest = Color(0xFF45484B),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF4A1515),
)

@Composable
fun AdhanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

/** The 315° grey gradient used behind every screen. */
fun Modifier.appBackground(): Modifier = background(
    Brush.linearGradient(
        0f to AppColors.BgStart, 0.5f to AppColors.BgCenter, 1f to AppColors.BgEnd,
        start = Offset(Float.POSITIVE_INFINITY, 0f), end = Offset(0f, Float.POSITIVE_INFINITY),
    ),
)
