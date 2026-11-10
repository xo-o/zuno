package com.zuno.ai.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zuno.ai.R
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoTheme
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.viewmodel.ToastEvent
import com.zuno.ai.viewmodel.ToastIcon
import kotlinx.coroutines.delay

private const val TOAST_DURATION_MS = 4_000L
private const val TOAST_WITH_ACTION_DURATION_MS = 6_000L

@DrawableRes
fun ToastIcon.drawable(): Int = when (this) {
    ToastIcon.Check -> R.drawable.zuno_ic_t_check_circle
    ToastIcon.Pin -> R.drawable.zuno_ic_t_pin
    ToastIcon.Trash -> R.drawable.zuno_ic_t_trash
    ToastIcon.SignOut -> R.drawable.zuno_ic_t_log_out
    ToastIcon.Link -> R.drawable.zuno_ic_t_link
    ToastIcon.Alert -> R.drawable.zuno_ic_t_info
    ToastIcon.Calendar -> R.drawable.zuno_ic_t_calendar
    ToastIcon.Bell -> R.drawable.zuno_ic_t_bell
}

/** Toast — inverse pill with an icon, message and optional action ("Reminder set · Tonight 9:00 PM  Undo"). */
@Composable
fun ZunoToast(
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.zuno_ic_t_check_circle,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(ZunoColors.SurfaceInverse)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = ZunoSpace.s16, end = if (action != null) ZunoSpace.s8 else ZunoSpace.s20),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(icon, 20.dp, Modifier.padding(vertical = ZunoSpace.s12), tint = ZunoColors.IconOnInverse)
        Text(
            message,
            style = ZunoType.LabelMedium,
            color = ZunoColors.TextOnInverse,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        action?.let {
            Text(
                it,
                style = ZunoType.LabelMedium,
                color = ZunoColors.TextOnInverseSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s12),
            )
        }
    }
}

/** Holds the toast currently on screen. One at a time; a new toast replaces the old one. */
@Stable
class ToastController {
    var current by mutableStateOf<ToastEvent?>(null)
        private set

    fun show(event: ToastEvent) {
        current = event
    }

    fun dismiss(event: ToastEvent? = null) {
        if (event == null || current?.id == event.id) current = null
    }
}

@Composable
fun rememberToastController() = remember { ToastController() }

/** App-level overlay that shows [controller]'s toast above the composer (or under the status bar). */
@Composable
fun ZunoToastHost(controller: ToastController, modifier: Modifier = Modifier) {
    val event = controller.current
    var shown by remember { mutableStateOf<ToastEvent?>(null) }
    if (event != null) shown = event
    LaunchedEffect(event?.id) {
        if (event != null) {
            delay(if (event.actionLabel != null) TOAST_WITH_ACTION_DURATION_MS else TOAST_DURATION_MS)
            controller.dismiss(event)
        }
    }
    val atTop = shown?.atTop == true
    // Read the status bar height from the view as well: the Compose insets can arrive as zero for a
    // root-level overlay during a navigation transition.
    val view = LocalView.current
    val density = LocalDensity.current
    val statusBar = with(density) {
        maxOf(
            WindowInsets.statusBars.getTop(this),
            ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0,
        ).toDp()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (atTop) Modifier.padding(top = statusBar + ZunoSpace.s12)
                else Modifier.navigationBarsPadding().imePadding().padding(bottom = 80.dp)
            )
            .padding(horizontal = ZunoSpace.s24),
        contentAlignment = if (atTop) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = event != null,
            enter = fadeIn() + slideInVertically { if (atTop) -it else it / 2 },
            exit = fadeOut() + slideOutVertically { if (atTop) -it else it / 2 },
        ) {
            shown?.let { toast ->
                ZunoToast(
                    message = toast.message,
                    icon = toast.icon.drawable(),
                    action = toast.actionLabel,
                    onAction = {
                        controller.dismiss(toast)
                        toast.onAction?.invoke()
                    },
                )
            }
        }
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun ZunoToastPreview() {
    ZunoTheme {
        ZunoToast("Scheduled · Daily at 9:00 AM", icon = R.drawable.zuno_ic_t_check_circle, action = "Undo")
    }
}
