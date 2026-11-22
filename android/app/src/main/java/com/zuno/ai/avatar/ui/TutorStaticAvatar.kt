package com.zuno.ai.avatar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.zuno.ai.avatar.catalog.TutorAvatarCatalog
import com.zuno.ai.avatar.model.TutorAvatarProfile
import com.zuno.ai.avatar.model.TutorPosterSource
import com.zuno.ai.ui.theme.ZunoColors

/**
 * Lightweight static tutor portrait for chat headers, conversation rows, and list icons.
 * Never initializes video decoders or OpenGL contexts.
 */
@Composable
fun TutorStaticAvatar(
    modifier: Modifier = Modifier,
    tutor: TutorAvatarProfile = TutorAvatarCatalog.DEFAULT_TUTOR,
    size: Dp = 44.dp,
    shape: Shape = CircleShape,
    borderColor: Color = ZunoColors.BorderDefault,
    borderWidth: Dp = 1.dp,
    contentDescription: String? = tutor.displayName,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .border(borderWidth, borderColor, shape),
    ) {
        when (val poster = tutor.thumbPoster) {
            is TutorPosterSource.DrawableRes -> {
                Image(
                    painter = painterResource(poster.resId),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is TutorPosterSource.RemoteUrl -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(poster.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
