package com.talkie.ai.ui.components

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.talkie.ai.R
import com.talkie.ai.data.Attachment
import com.talkie.ai.data.AttachmentFiles
import com.talkie.ai.data.AttachmentKind
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoRadius
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType
import com.talkie.ai.viewmodel.DraftAttachment
import com.talkie.ai.viewmodel.DraftUploadState
import kotlin.math.roundToInt

/**
 * "Add to this chat" — camera, photo picker and document picker, plus learning actions:
 * paste a link and (in a conversation) "Remind me to continue". Null callbacks hide their row.
 */
@Composable
fun ZunoAttachSheet(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onPasteLink: (() -> Unit)? = null,
    onRemind: (() -> Unit)? = null,
    title: String = "Add to this chat",
) {
    ZunoSheet(onDismiss = onDismiss, title = title) {
        Row(
            modifier = Modifier.padding(horizontal = ZunoSpace.s12),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            AttachTile("Camera", R.drawable.zuno_ic_camera, onCamera, Modifier.weight(1f))
            AttachTile("Photos", R.drawable.zuno_ic_image, onPhotos, Modifier.weight(1f))
            AttachTile("Files", R.drawable.zuno_ic_file, onFiles, Modifier.weight(1f))
        }
        if (onPasteLink != null || onRemind != null) {
            Column {
                ZunoSectionHeader(title = "More", modifier = Modifier.padding(horizontal = ZunoSpace.s12))
                onPasteLink?.let {
                    SheetRow(R.drawable.zuno_ic_link, "Paste a link", "Article or video to discuss", it)
                }
                onRemind?.let {
                    SheetRow(
                        R.drawable.zuno_ic_alarm,
                        "Remind me to continue",
                        "Get a nudge later — reopens this chat as you left it",
                        it,
                        chevron = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachTile(label: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(
        modifier = modifier
            .clip(shape)
            .background(ZunoColors.SurfaceDefault)
            .border(1.dp, ZunoColors.BorderSubtle, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s8, end = ZunoSpace.s8, top = ZunoSpace.s20, bottom = ZunoSpace.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
    ) {
        ZunoIcon(icon, 24.dp)
        Text(label, style = ZunoType.LabelMedium, color = ZunoColors.TextPrimary, maxLines = 1)
    }
}

/** Sheet Row — 22dp icon, Body/Large title, footnote description, optional chevron. */
@Composable
private fun SheetRow(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
    chevron: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(ZunoRadius.md))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(icon, 22.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(title, style = ZunoType.BodyLarge, color = ZunoColors.TextPrimary)
            Text(description, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
        }
        if (chevron) ZunoIcon(R.drawable.zuno_ic_chevron_right_muted, 20.dp)
    }
}

private val UrlPattern = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

/** "Paste a link" — a URL field pre-filled from the clipboard when it holds a link. */
@Composable
fun ZunoPasteLinkSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var url by rememberSaveable {
        mutableStateOf(clipboard.getText()?.text?.let { UrlPattern.find(it)?.value }.orEmpty())
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val valid = url.trim().let { it.length >= 4 && '.' in it && ' ' !in it }
    fun submit() {
        if (valid) onAdd(url.trim())
    }
    ZunoSheet(onDismiss = onDismiss, title = "Paste a link") {
        Text(
            "Share an article or a YouTube video. Zuno reads it so you can talk about it together.",
            style = ZunoType.BodySmall,
            color = ZunoColors.TextSecondary,
            modifier = Modifier.padding(horizontal = ZunoSpace.s12),
        )
        ZunoTextField(
            value = url,
            onValueChange = { url = it.take(2048) },
            placeholder = "https://…",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.padding(horizontal = ZunoSpace.s12).focusRequester(focusRequester),
        )
        ZunoButton(
            label = "Add link",
            onClick = ::submit,
            enabled = valid,
            modifier = Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s12),
        )
    }
}

/** A reminder time choice in [ZunoReminderSheet]. [label] is used in the chip, [summary] in the toast. */
data class ReminderOption(
    val label: String,
    val summary: String,
    val atMillis: Long,
    @param:DrawableRes val icon: Int? = null,
    /** "Tonight · 9:00 PM" — preselected when offered, as in the design. */
    val isTonight: Boolean = false,
)

/** "Remind me to continue" — quick times, a custom time, then "Set reminder". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZunoReminderSheet(
    options: List<ReminderOption>,
    onPickTime: () -> Unit,
    onDismiss: () -> Unit,
    onSet: (ReminderOption) -> Unit,
    custom: ReminderOption? = null,
) {
    // A picked custom time is selected; otherwise "Tonight" when it's still ahead, as in the design.
    var selected by remember(custom) {
        mutableStateOf(custom ?: options.firstOrNull { it.isTonight } ?: options.first())
    }
    ZunoSheet(onDismiss = onDismiss, title = "Remind me to continue") {
        Text(
            "We’ll send you a reminder. Tapping it reopens this chat exactly as you left it — same history, same attachments.",
            style = ZunoType.BodySmall,
            color = ZunoColors.TextSecondary,
            modifier = Modifier.padding(horizontal = ZunoSpace.s12),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s12),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            options.forEach { option ->
                ZunoChip(option.label, onClick = { selected = option }, selected = option == selected)
            }
            ZunoChip(
                label = custom?.label ?: "Pick a time",
                onClick = onPickTime,
                leadingIcon = R.drawable.zuno_ic_clock_small,
                selected = custom != null && custom == selected,
            )
        }
        ZunoButton(
            label = "Set reminder",
            onClick = { onSet(selected) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s12),
        )
    }
}

/**
 * A pending attachment in the composer: image thumbnail, file card or link card, with upload states.
 * [wide] cards fill the width (topic material) and describe what Zuno read ("Read · 12 pages").
 */
@Composable
fun ZunoDraftAttachment(
    draft: DraftAttachment,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    wide: Boolean = false,
) {
    val failed = draft.state is DraftUploadState.Failed
    val busy = draft.state is DraftUploadState.Preparing ||
        draft.state is DraftUploadState.Uploading ||
        draft.state is DraftUploadState.Processing
    if (!draft.isLink && (draft.isImage || (draft.state == DraftUploadState.Preparing && draft.name.isEmpty()))) {
        val shape = RoundedCornerShape(ZunoRadius.md)
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(shape)
                .background(ZunoColors.SurfaceStrong)
                .border(1.dp, if (failed) ZunoColors.BorderFocus else ZunoColors.BorderSubtle, shape)
                .clickable(enabled = failed, role = Role.Button, onClick = onRetry),
        ) {
            draft.preview?.let { preview ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(preview)
                        .apply { draft.serverId?.takeIf { preview.scheme?.startsWith("http") == true }?.let { memoryCacheKey(it).diskCacheKey(it) } }
                        .build(),
                    contentDescription = draft.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (busy || failed) {
                Box(Modifier.fillMaxSize().background(ZunoColors.BgScrim), contentAlignment = Alignment.Center) {
                    if (failed) ZunoIcon(R.drawable.zuno_ic_alert_circle, 20.dp, contentDescription = "Failed — tap to retry")
                    else ZunoSpinner(size = 20.dp)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ZunoColors.BgElevated)
                    .border(1.dp, ZunoColors.BorderStrong, CircleShape)
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(R.drawable.zuno_ic_x_small, 12.dp, contentDescription = "Remove")
            }
        }
    } else {
        val meta = when (val state = draft.state) {
            is DraftUploadState.Failed -> "Failed · Tap to retry"
            is DraftUploadState.Uploading -> "Uploading · ${(state.progress * 100).roundToInt()}%"
            DraftUploadState.Processing -> if (draft.isLink) "Reading link…" else "Reading…"
            DraftUploadState.Preparing -> "Preparing…"
            DraftUploadState.Ready -> when {
                draft.isLink -> linkMeta(draft.mimeType, draft.url)
                wide -> readMeta(draft.pageCount, draft.sectionCount) ?: AttachmentFiles.describe(draft.mimeType, draft.sizeBytes)
                else -> AttachmentFiles.describe(draft.mimeType, draft.sizeBytes)
            }
        }
        FileCard(
            name = draft.name,
            meta = meta,
            icon = if (draft.isLink) R.drawable.zuno_ic_link else R.drawable.zuno_ic_file_text,
            modifier = if (wide) Modifier.fillMaxWidth() else Modifier.width(224.dp),
            failed = failed,
            busy = busy,
            onClick = if (failed) onRetry else null,
            onRemove = onRemove,
        )
    }
}

/** "Read · 12 pages · 3 sections" once Zuno has read a document. */
private fun readMeta(pages: Int?, sections: Int?): String? {
    val parts = listOfNotNull(
        pages?.let { if (it == 1) "1 page" else "$it pages" },
        sections?.let { if (it == 1) "1 section" else "$it sections" },
    )
    return if (parts.isEmpty()) null else (listOf("Read") + parts).joinToString(" · ")
}

/** "Video · youtube.com" / "Article · elpais.com" */
private fun linkMeta(mimeType: String, url: String?): String {
    val host = url?.let { runCatching { Uri.parse(if ("://" in it) it else "https://$it").host }.getOrNull() }
        ?.removePrefix("www.")
    val type = if (mimeType.startsWith("video/")) "Video" else "Article"
    return listOfNotNull(type, host).joinToString(" · ")
}

@Composable
private fun FileCard(
    name: String,
    meta: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.zuno_ic_file_text,
    failed: Boolean = false,
    busy: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(ZunoRadius.md)
    Row(
        modifier = modifier
            .height(64.dp)
            .clip(shape)
            .background(ZunoColors.SurfaceDefault)
            .border(1.dp, if (failed) ZunoColors.BorderFocus else ZunoColors.BorderDefault, shape)
            .clickable(enabled = onClick != null, role = Role.Button) { onClick?.invoke() }
            .padding(ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(ZunoRadius.sm)).background(ZunoColors.SurfaceStrong),
            contentAlignment = Alignment.Center,
        ) {
            when {
                failed -> ZunoIcon(R.drawable.zuno_ic_alert_circle, 20.dp)
                busy -> ZunoSpinner(size = 16.dp)
                else -> ZunoIcon(icon, 20.dp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = ZunoType.LabelSmall, color = ZunoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                meta,
                style = ZunoType.FootnoteRegular,
                color = if (failed) ZunoColors.TextSecondary else ZunoColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        onRemove?.let {
            Box(
                Modifier.size(24.dp).clip(CircleShape).clickable(role = Role.Button, onClick = it),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(R.drawable.zuno_ic_x_muted, 16.dp, contentDescription = "Remove")
            }
        }
    }
}

private val MessageAttachmentShape = RoundedCornerShape(
    topStart = ZunoRadius.xl,
    topEnd = ZunoRadius.xl,
    bottomEnd = ZunoRadius.xs,
    bottomStart = ZunoRadius.xl,
)

/** Files sent in a user turn: photos as 220dp-wide previews, documents and links as cards. */
@Composable
fun ZunoMessageAttachments(
    attachments: List<Attachment>,
    onOpen: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
        attachments.forEach { attachment ->
            when (attachment.kind) {
                AttachmentKind.Image -> SentImage(
                    model = attachment.url,
                    cacheKey = attachment.id,
                    aspect = attachment.aspect,
                    description = attachment.fileName,
                    onClick = { onOpen(attachment) },
                )
                AttachmentKind.Link -> FileCard(
                    name = attachment.fileName,
                    meta = linkMeta(attachment.mimeType, attachment.url),
                    icon = R.drawable.zuno_ic_link,
                    modifier = Modifier.width(240.dp),
                    onClick = { onOpen(attachment) },
                )
                AttachmentKind.Document -> FileCard(
                    name = attachment.fileName,
                    meta = AttachmentFiles.describe(attachment.mimeType, attachment.sizeBytes),
                    modifier = Modifier.width(240.dp),
                    onClick = { onOpen(attachment) },
                )
            }
        }
    }
}

/** Optimistic version of [ZunoMessageAttachments] while the message is being sent. */
@Composable
fun ZunoPendingAttachments(drafts: List<DraftAttachment>, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
        drafts.forEach { draft ->
            val preview = draft.preview
            when {
                draft.isImage && preview != null -> SentImage(
                    model = preview,
                    cacheKey = draft.serverId?.takeIf { preview.scheme?.startsWith("http") == true },
                    aspect = null,
                    description = draft.name,
                    onClick = null,
                )
                draft.isLink -> FileCard(
                    name = draft.name,
                    meta = linkMeta(draft.mimeType, draft.url),
                    icon = R.drawable.zuno_ic_link,
                    modifier = Modifier.width(240.dp),
                )
                else -> FileCard(
                    name = draft.name,
                    meta = AttachmentFiles.describe(draft.mimeType, draft.sizeBytes),
                    modifier = Modifier.width(240.dp),
                )
            }
        }
    }
}

private val Attachment.aspect: Float?
    get() = if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else null

@Composable
private fun SentImage(model: Any?, cacheKey: String?, aspect: Float?, description: String, onClick: (() -> Unit)?) {
    val height = aspect?.let { (220f / it).coerceIn(140f, 280f) } ?: 165f
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            .apply { if (cacheKey != null) memoryCacheKey(cacheKey).diskCacheKey(cacheKey) }
            .crossfade(true)
            .build(),
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 220.dp, height = height.dp)
            .clip(MessageAttachmentShape)
            .background(ZunoColors.SurfaceStrong)
            .border(1.dp, ZunoColors.BorderSubtle, MessageAttachmentShape)
            .clickable(enabled = onClick != null, role = Role.Image) { onClick?.invoke() },
    )
}

/** "Read menu photo · 4 items found ›" above a reply that used the learner's attachments. */
@Composable
fun ZunoContextStatus(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ZunoRadius.xs))
            .clickable(enabled = onClick != null, role = Role.Button) { onClick?.invoke() },
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(R.drawable.zuno_ic_file_text_small, 16.dp)
        Text(label, style = ZunoType.FootnoteStrong, color = ZunoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        ZunoIcon(R.drawable.zuno_ic_chevron_right_small, 14.dp)
    }
}

/** Upload Area — dashed drop zone with Files / Photos / Camera sources (New topic material). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZunoUploadArea(
    onFiles: () -> Unit,
    onPhotos: () -> Unit,
    onCamera: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Add learning material",
    description: String = "Class notes, a PDF chapter or photos of your textbook. Up to 20 MB each.",
) {
    val radius = ZunoRadius.lg
    val dashColor = ZunoColors.BorderStrong
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(ZunoColors.SurfaceSubtle)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = dashColor,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(start = ZunoSpace.s20, end = ZunoSpace.s20, top = ZunoSpace.s24, bottom = ZunoSpace.s20),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(ZunoColors.SurfaceStrong), contentAlignment = Alignment.Center) {
            ZunoIcon(R.drawable.zuno_ic_upload, 22.dp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
            Text(title, style = ZunoType.LabelLarge, color = ZunoColors.TextPrimary, textAlign = TextAlign.Center)
            Text(description, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary, textAlign = TextAlign.Center)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8, Alignment.CenterHorizontally)) {
            ZunoChip("Files", onFiles, leadingIcon = R.drawable.zuno_ic_file_small)
            ZunoChip("Photos", onPhotos, leadingIcon = R.drawable.zuno_ic_image_small)
            ZunoChip("Camera", onCamera, leadingIcon = R.drawable.zuno_ic_camera_small)
        }
    }
}

/** Full-screen photo viewer. */
@Composable
fun ZunoImageViewer(attachment: Attachment, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(role = Role.Button, onClick = onDismiss),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(attachment.url)
                    .memoryCacheKey(attachment.id)
                    .diskCacheKey(attachment.id)
                    .build(),
                contentDescription = attachment.fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            ZunoIconButton(
                icon = R.drawable.zuno_ic_x_small,
                onClick = onDismiss,
                style = ZunoIconButtonStyle.Subtle,
                iconSize = 16.dp,
                contentDescription = "Close",
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(ZunoSpace.s16),
            )
        }
    }
}
