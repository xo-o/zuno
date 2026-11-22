package com.zuno.ai.avatar.ui

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.zuno.ai.avatar.audio.AvatarSpeechSink
import com.zuno.ai.avatar.catalog.TutorAvatarCatalog
import com.zuno.ai.avatar.model.TutorAvatarProfile
import com.zuno.ai.avatar.model.TutorPosterSource
import com.zuno.ai.avatar.renderer.AvatarRenderController
import com.zuno.ai.avatar.repository.AvatarBundleLoader

/**
 * Large interactive speaking stage for an AI tutor avatar.
 *
 * Displays the tutor's static poster immediately (zero black flash) and transitions seamlessly
 * to the hardware-decoded 1280x720 idle loop + OpenGL ES 3.0 zero-blur lip-sync compositor
 * driven by [speechSink]. Automatically pauses on `ON_STOP` and falls back to the static
 * poster if the device lacks GLES 3.0 or bundle loading fails.
 */
@Composable
fun TutorStageAvatar(
    modifier: Modifier = Modifier,
    tutor: TutorAvatarProfile = TutorAvatarCatalog.DEFAULT_TUTOR,
    speechSink: AvatarSpeechSink? = null,
    contentDescription: String? = tutor.displayName,
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val bundleSource = tutor.bundleSource

    var firstFrameRendered by remember(tutor.id, bundleSource?.cacheKey) { mutableStateOf(false) }
    var glFailed by remember(tutor.id, bundleSource?.cacheKey) { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (!isInspection && bundleSource != null && !glFailed) {
            val controller = remember(tutor.id, bundleSource.cacheKey, speechSink) {
                AvatarRenderController(
                    speechSink = speechSink,
                    onFirstFrameRendered = { firstFrameRendered = true },
                    onFatalError = { err ->
                        Log.w("TutorStageAvatar", "Falling back to static poster for ${tutor.id}", err)
                        glFailed = true
                    },
                )
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(controller, lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> controller.setPaused(false)
                        Lifecycle.Event.ON_STOP -> controller.setPaused(true)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    controller.release()
                }
            }

            LaunchedEffect(controller, bundleSource.cacheKey) {
                runCatching {
                    AvatarBundleLoader.load(context, bundleSource)
                }.onSuccess { loaded ->
                    controller.setBundle(loaded)
                }.onFailure { err ->
                    Log.w("TutorStageAvatar", "Failed to load avatar bundle ${bundleSource.cacheKey}", err)
                    glFailed = true
                }
            }

            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isOpaque = true
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                controller.attachOutputSurface(surface, width, height)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                controller.onOutputSurfaceSizeChanged(width, height)
                            }

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                controller.detachOutputSurface()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Static poster stays visible until the first GL frame has swapped onto the TextureView
        val posterAlpha by animateFloatAsState(
            targetValue = if (firstFrameRendered && !glFailed) 0f else 1f,
            animationSpec = tween(durationMillis = 160),
            label = "tutorPosterFade",
        )
        if (posterAlpha > 0.005f) {
            val posterModifier = Modifier
                .fillMaxSize()
                .alpha(posterAlpha)
            when (val poster = tutor.stagePoster) {
                is TutorPosterSource.DrawableRes -> {
                    Image(
                        painter = painterResource(poster.resId),
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = posterModifier,
                    )
                }
                is TutorPosterSource.RemoteUrl -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(poster.url)
                            .crossfade(true)
                            .build(),
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = posterModifier,
                    )
                }
            }
        }
    }
}
