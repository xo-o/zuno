package com.zuno.ai.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.zuno.ai.R
import com.zuno.ai.data.CoachPromptItem
import com.zuno.ai.data.CoachPrompts
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.RendererCommon

/**
 * Member avatar with optional green/mint Open-to-speak halo ring and circular mic badge.
 */
@Composable
fun ZunoOpenToSpeakAvatar(
    initial: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: Dp = 44.dp,
    openToSpeak: Boolean = false,
    inverseInner: Boolean = false,
    textStyle: TextStyle = when {
        size >= 64.dp -> ZunoType.Headline
        size >= 44.dp -> ZunoType.BodyMediumStrong
        else -> ZunoType.LabelSmall
    },
) {
    val ringInset = if (openToSpeak) 3.dp else 0.dp
    val badgeSize = when {
        size >= 64.dp -> 20.dp
        size >= 44.dp -> 16.dp
        else -> 14.dp
    }
    val badgeIconSize = when {
        size >= 64.dp -> 11.dp
        else -> 9.dp
    }
    Box(modifier = modifier.size(size)) {
        if (openToSpeak) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.5.dp, ZunoColors.StatusSpeaking, CircleShape),
            )
        }
        val innerBg = if (inverseInner) ZunoColors.SurfaceInverse else ZunoColors.SurfaceStrong
        val innerFg = if (inverseInner) ZunoColors.TextOnInverse else ZunoColors.TextPrimary
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(ringInset)
                .clip(CircleShape)
                .background(innerBg)
                .border(1.dp, ZunoColors.BorderSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial.take(1).uppercase(), style = textStyle, color = innerFg)
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .apply { imageUrl.substringBefore('?').let { memoryCacheKey(it).diskCacheKey(it) } }
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (openToSpeak) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(ZunoColors.SurfaceInverse)
                    .border(1.5.dp, ZunoColors.BgCanvas, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(R.drawable.zuno_ic_t_mic, badgeIconSize, tint = ZunoColors.IconOnInverse)
            }
        }
    }
}

/**
 * Sofía AI Tutor circular avatar with the inverse sparkles badge at bottom-right.
 */
@Composable
fun ZunoAiTutorAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val badgeSize = if (size >= 44.dp) 16.dp else 14.dp
    Box(modifier = modifier.size(size)) {
        Image(
            painter = painterResource(R.drawable.sofia_tutor_avatar),
            contentDescription = stringResource(R.string.onboarding_sender_sofia),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(1.dp, ZunoColors.BorderDefault, CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(badgeSize)
                .clip(CircleShape)
                .background(ZunoColors.SurfaceInverse)
                .border(1.5.dp, ZunoColors.BgCanvas, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(R.drawable.zuno_ic_t_sparkles, 9.dp, tint = ZunoColors.IconOnInverse)
        }
    }
}

/**
 * Renders a LiveKit [VideoTrack] inside Compose using [TextureViewRenderer].
 */
@Composable
fun LiveKitVideoFeed(
    room: Room?,
    videoTrack: VideoTrack?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
) {
    val context = LocalContext.current
    val renderer = remember(room) {
        TextureViewRenderer(context).apply {
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setMirror(mirror)
            runCatching { room?.initVideoRenderer(this) }
        }
    }

    DisposableEffect(renderer, videoTrack, mirror) {
        renderer.setMirror(mirror)
        videoTrack?.addRenderer(renderer)
        onDispose {
            runCatching { videoTrack?.removeRenderer(renderer) }
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            runCatching { renderer.release() }
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier,
    )
}

/**
 * Tabbed Coach Card (`2860:16734`, `2861:16709`, `2862:15717`) with Answers, Follow-ups,
 * and Questions tabs plus 2 tappable target-language prompt cards with TTS playback.
 */
@Composable
fun ZunoTabbedCoachCard(
    prompts: CoachPrompts,
    onSpeakPhrase: (String) -> Unit,
    modifier: Modifier = Modifier,
    defaultTab: Int = 1,
) {
    var selectedTab by remember { mutableIntStateOf(defaultTab) }
    val cardShape = RoundedCornerShape(ZunoRadius.xxxl)
    val items: List<CoachPromptItem> = when (selectedTab) {
        0 -> prompts.answers
        2 -> prompts.questions
        else -> prompts.followUps
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(ZunoColors.SurfaceDefault)
            .border(1.dp, ZunoColors.BorderDefault, cardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
        ) {
            ZunoChip(
                label = stringResource(R.string.call_coach_answers),
                leadingIcon = R.drawable.zuno_ic_t_message_circle,
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
            )
            ZunoChip(
                label = stringResource(R.string.call_coach_follow_ups),
                leadingIcon = R.drawable.zuno_ic_sparkles,
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
            )
            ZunoChip(
                label = stringResource(R.string.call_coach_questions),
                leadingIcon = R.drawable.zuno_ic_t_message_circle,
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
            items.take(2).forEach { item ->
                val rowShape = RoundedCornerShape(ZunoRadius.lg)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(ZunoColors.SurfaceDefault)
                        .border(1.dp, ZunoColors.BorderDefault, rowShape)
                        .clickable(role = Role.Button, onClick = { onSpeakPhrase(item.phrase) })
                        .padding(horizontal = ZunoSpace.s12, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "“${item.phrase}”",
                            style = ZunoType.FootnoteStrong,
                            color = ZunoColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        ZunoIcon(R.drawable.zuno_ic_t_volume, 14.dp, tint = ZunoColors.IconSecondary)
                    }
                    Text(
                        text = item.translation,
                        style = ZunoType.Micro,
                        color = ZunoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 5-button Call Controls Bar (`2860:16751`, `2861:16785`, `2862:15734`):
 * Mic + Camera on left, End call pill centered, Speaker + Captions/Coach on right.
 */
@Composable
fun ZunoCallControlsBar(
    isMuted: Boolean,
    isCameraOn: Boolean,
    isSpeakerOn: Boolean,
    isCaptionsOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onEndCall: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCaptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ZunoSpace.s24, vertical = ZunoSpace.s12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
            RoundCallControlButton(
                icon = if (isMuted) R.drawable.zuno_ic_mic_off else R.drawable.zuno_ic_t_mic,
                active = isMuted,
                onClick = onToggleMute,
                contentDescription = stringResource(if (isMuted) R.string.voice_unmute else R.string.voice_mute),
            )
            RoundCallControlButton(
                icon = if (isCameraOn) R.drawable.zuno_ic_t_video else R.drawable.zuno_ic_t_video_off,
                active = isCameraOn,
                activeSubtle = true,
                onClick = onToggleCamera,
                contentDescription = stringResource(R.string.call_toggle_camera),
            )
        }

        ZunoButton(
            label = stringResource(R.string.voice_end_call),
            onClick = onEndCall,
            variant = ZunoButtonVariant.Secondary,
            size = ZunoButtonSize.Medium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
            RoundCallControlButton(
                icon = R.drawable.zuno_ic_t_volume,
                active = !isSpeakerOn,
                onClick = onToggleSpeaker,
                contentDescription = stringResource(R.string.call_toggle_speaker),
            )
            RoundCallControlButton(
                icon = R.drawable.zuno_ic_captions,
                active = isCaptionsOn,
                activeSubtle = true,
                onClick = onToggleCaptions,
                contentDescription = stringResource(if (isCaptionsOn) R.string.voice_hide_captions else R.string.voice_show_captions),
            )
        }
    }
}

@Composable
private fun RoundCallControlButton(
    @DrawableRes icon: Int,
    active: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    activeSubtle: Boolean = false,
) {
    val bg = when {
        active && activeSubtle -> ZunoColors.SurfaceStronger
        active -> ZunoColors.SurfaceInverse
        else -> ZunoColors.SurfaceDefault
    }
    val tint = if (active && !activeSubtle) ZunoColors.IconOnInverse else ZunoColors.IconPrimary
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (active && activeSubtle) Modifier.border(1.dp, ZunoColors.BorderDefault, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ZunoIcon(icon, 22.dp, tint = tint)
    }
}
