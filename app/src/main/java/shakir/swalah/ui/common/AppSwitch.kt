package shakir.swalah.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * On/off switch in the Material 3 look that shows its real state from the first frame.
 * (The stock Switch can animate from "off" when a list row scrolls into view.) The
 * animate*AsState values start at their target, so only a change made while visible animates.
 */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val thumbSize by animateDpAsState(if (checked) 24.dp else 16.dp, label = "thumbSize")
    val thumbOffset by animateDpAsState(if (checked) 24.dp else 4.dp, label = "thumbOffset")
    val track by animateColorAsState(if (checked) colors.primary else colors.surfaceContainerHighest, label = "track")
    val thumb by animateColorAsState(if (checked) colors.onPrimary else colors.outline, label = "thumb")
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.38f)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                else Modifier,
            )
            .padding(vertical = 8.dp)
            .size(52.dp, 32.dp)
            .background(track, CircleShape)
            .then(if (checked) Modifier else Modifier.border(2.dp, colors.outline, CircleShape)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset + (24.dp - thumbSize) / 2)
                .size(thumbSize)
                .background(thumb, CircleShape),
        )
    }
}
