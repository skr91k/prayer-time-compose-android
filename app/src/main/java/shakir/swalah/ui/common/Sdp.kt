package shakir.swalah.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The old home layout used the "sdp" library: sizes that scale with the screen's smallest width
 * (1sdp = 1dp on a 300dp-wide screen). These reproduce it so the home screen keeps its proportions.
 */

private const val SDP_BASE_WIDTH = 300f

/** Scalable dp, like `@dimen/_Nsdp`. */
val Int.sdp: Dp
    @Composable @ReadOnlyComposable
    get() = (this * LocalConfiguration.current.smallestScreenWidthDp / SDP_BASE_WIDTH).dp

/** Text sized in sdp (the old layouts set textSize in sdp, so it ignored the font-scale setting). */
val Int.sdpText: TextUnit
    @Composable @ReadOnlyComposable
    get() = (this * LocalConfiguration.current.smallestScreenWidthDp / SDP_BASE_WIDTH / LocalDensity.current.fontScale).sp

/** Text sized in plain dp, like the old `textSize="30dp"`. */
val Int.dpText: TextUnit
    @Composable @ReadOnlyComposable
    get() = (this / LocalDensity.current.fontScale).sp
