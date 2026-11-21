package shakir.swalah.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import shakir.swalah.ui.Routes
import shakir.swalah.ui.common.AppIcons

/**
 * Every page that can sit in the bottom bar. Home and More are always there; the user picks up to
 * [MAX_PICKED] of the rest (see BottomBarScreen). A [url] page opens in the browser.
 */
enum class NavDest(val title: String, val route: String?, val url: String? = null, val tint: Boolean = true) {
    HOME("Home", Routes.HOME),
    REMINDERS("Reminders", Routes.REMINDERS),
    COUNTER("Counter", null, url = "https://utilitykit.web.app/counter"),
    QIBLA("Qibla", Routes.QIBLA),
    BOOKS("Books", null, url = "https://islamicbooks2.web.app/"),
    MONTH("Month", Routes.MONTH),
    HIJRI("Hijri", Routes.HIJRI),
    MORE("More", Routes.SETTINGS);

    /** Longer name for the More page list. */
    val longTitle: String
        get() = when (this) {
            REMINDERS -> "Reminders | التنبيهات"
            COUNTER -> "Counter | عداد"
            QIBLA -> "Qibla | قِبْلَة"
            BOOKS -> "Islamic Books | مكتبة الكتب"
            MONTH -> "Month View"
            HIJRI -> "Hijri Calendar"
            else -> title
        }

    @Composable
    fun icon(): Painter = when (this) {
        HOME -> rememberVectorPainter(Icons.Default.Home)
        REMINDERS -> rememberVectorPainter(AppIcons.Alarm)
        COUNTER -> rememberVectorPainter(AppIcons.MoreHoriz)
        QIBLA -> rememberVectorPainter(AppIcons.Compass)
        BOOKS -> rememberVectorPainter(AppIcons.Book)
        MONTH -> rememberVectorPainter(AppIcons.Calendar)
        HIJRI -> rememberVectorPainter(Icons.Default.DateRange)
        MORE -> rememberVectorPainter(AppIcons.Menu)
    }

    companion object {
        /** Bar = Home + picked + More, at most 5 items. */
        const val MAX_PICKED = 3
        val PICKABLE = listOf(REMINDERS, COUNTER, QIBLA, BOOKS, MONTH, HIJRI)
        val DEFAULT_PICKED = listOf(REMINDERS, COUNTER)

        fun bar(picked: List<String>): List<NavDest> =
            listOf(HOME) + PICKABLE.filter { it.name in picked }.take(MAX_PICKED) + MORE
    }
}
