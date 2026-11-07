package com.talkie.ai.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

enum class TargetLanguage(val code: String, val label: String, val nativeCode: String) {
    Spanish("es", "Spanish", "en"),
    English("en", "English", "es");

    companion object {
        fun fromCode(code: String?): TargetLanguage = entries.firstOrNull { it.code == code } ?: Spanish
    }
}

val Levels = listOf("A1", "A2", "B1", "B2", "C1", "C2")

data class Profile(
    val name: String,
    val email: String,
    val targetLanguage: TargetLanguage,
    val level: String,
) {
    /** Empty when the account has no name (e.g. magic-link sign-ups), so greetings can omit it. */
    val firstName: String get() = name.trim().substringBefore(' ')
    val displayName: String get() = name.trim().ifBlank { email.substringBefore('@') }
    val detail: String get() = "${targetLanguage.label} · $level"
}

enum class TopicStatus { NotStarted, InProgress, Completed }

enum class LessonState { Done, Current, Upcoming }

data class TopicSummary(
    val id: String,
    val title: String,
    val translation: String,
    val totalLessons: Int,
    val completedLessons: Int,
    val progress: Int,
    val status: TopicStatus,
)

data class TopicLesson(
    val id: String,
    val position: Int,
    val title: String,
    val translation: String,
    val objective: String,
    val state: LessonState,
)

data class TopicDetail(val summary: TopicSummary, val lessons: List<TopicLesson>) {
    val nextLesson: TopicLesson? get() = lessons.firstOrNull { it.state == LessonState.Current }
}

data class Roleplay(
    val id: String,
    val title: String,
    val scenario: String,
    val userRole: String,
    val aiRole: String,
)

enum class ConversationKind { FreeTalk, Roleplay, Lesson }

data class Conversation(
    val id: String,
    val kind: ConversationKind,
    val title: String,
    val topicId: String?,
    val lessonId: String?,
    val roleplayId: String?,
    val createdAtMillis: Long,
)

enum class AttachmentKind { Image, Document, Link }

enum class AttachmentStatus { Pending, Processing, Ready, Failed }

/**
 * A file stored on the server, or a shared link. For files [url] is a short-lived signed link — cache
 * images by [id], not by URL. For links it is the page or video address and [fileName] is its title.
 */
data class Attachment(
    val id: String,
    val kind: AttachmentKind,
    val mimeType: String,
    val fileName: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val status: AttachmentStatus,
    val error: String?,
    val url: String?,
    val pageCount: Int? = null,
    val sectionCount: Int? = null,
)

/** A sub-topic Zuno found in learning material ("Found in your material"). */
data class MaterialPart(val label: String, val suggested: Boolean)

data class Message(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val translation: String?,
    val feedback: String?,
    val glossary: List<String>,
    val attachments: List<Attachment> = emptyList(),
    /** "Read menu photo · 4 items found" — what the tutor read from the learner's attachments. */
    val contextNote: String? = null,
)

/** A presigned R2 upload for a newly created attachment. */
data class UploadTicket(val attachment: Attachment, val uploadUrl: String)

data class ConversationThread(val conversation: Conversation, val messages: List<Message>)

data class SendResult(
    val conversation: Conversation,
    val userMessage: Message,
    val reply: Message,
    val lessonCompleted: Boolean,
)

enum class RecentKind { FreeTalk, Topic, Roleplay }

data class RecentSession(val id: String, val kind: RecentKind, val title: String, val meta: String)

// ── JSON mapping ────────────────────────────────────────────────────────────

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

internal fun JSONObject.toProfile(): Profile {
    val user = getJSONObject("user")
    val profile = getJSONObject("profile")
    return Profile(
        name = user.optString("name"),
        email = user.optString("email"),
        targetLanguage = TargetLanguage.fromCode(profile.optString("targetLanguage")),
        level = profile.optString("level", "B1"),
    )
}

internal fun JSONObject.toTopicSummary() = TopicSummary(
    id = getString("id"),
    title = optString("title"),
    translation = optString("translation"),
    totalLessons = optInt("totalLessons"),
    completedLessons = optInt("completedLessons"),
    progress = optInt("progress"),
    status = when (optString("status")) {
        "completed" -> TopicStatus.Completed
        "in_progress" -> TopicStatus.InProgress
        else -> TopicStatus.NotStarted
    },
)

internal fun JSONObject.toTopicDetail() = TopicDetail(
    summary = toTopicSummary(),
    lessons = getJSONArray("lessons").objects().map {
        TopicLesson(
            id = it.getString("id"),
            position = it.optInt("position"),
            title = it.optString("title"),
            translation = it.optString("translation"),
            objective = it.optString("objective"),
            state = when (it.optString("status")) {
                "done" -> LessonState.Done
                "current" -> LessonState.Current
                else -> LessonState.Upcoming
            },
        )
    },
)

internal fun JSONObject.toRoleplay() = Roleplay(
    id = getString("id"),
    title = optString("title"),
    scenario = optString("scenario"),
    userRole = optString("userRole"),
    aiRole = optString("aiRole"),
)

internal fun JSONObject.toConversation() = Conversation(
    id = getString("id"),
    kind = when (optString("kind")) {
        "roleplay" -> ConversationKind.Roleplay
        "lesson" -> ConversationKind.Lesson
        else -> ConversationKind.FreeTalk
    },
    title = optString("title"),
    topicId = optStringOrNull("topicId"),
    lessonId = optStringOrNull("lessonId"),
    roleplayId = optStringOrNull("roleplayId"),
    createdAtMillis = runCatching { Instant.parse(optString("createdAt")).toEpochMilli() }.getOrDefault(System.currentTimeMillis()),
)

internal fun JSONObject.toAttachment() = Attachment(
    id = getString("id"),
    kind = when (optString("kind")) {
        "image" -> AttachmentKind.Image
        "link" -> AttachmentKind.Link
        else -> AttachmentKind.Document
    },
    mimeType = optString("mimeType"),
    fileName = optString("fileName"),
    sizeBytes = optLong("sizeBytes"),
    width = if (isNull("width")) null else optInt("width"),
    height = if (isNull("height")) null else optInt("height"),
    status = when (optString("status")) {
        "ready" -> AttachmentStatus.Ready
        "processing" -> AttachmentStatus.Processing
        "failed" -> AttachmentStatus.Failed
        else -> AttachmentStatus.Pending
    },
    error = optStringOrNull("error"),
    url = optStringOrNull("url"),
    pageCount = if (isNull("pageCount")) null else optInt("pageCount").takeIf { it > 0 },
    sectionCount = if (isNull("sectionCount")) null else optInt("sectionCount").takeIf { it > 0 },
)

internal fun JSONObject.toUploadTicket() = UploadTicket(
    attachment = getJSONObject("attachment").toAttachment(),
    uploadUrl = getString("uploadUrl"),
)

internal fun JSONObject.toMessage(): Message {
    val glossary = optJSONArray("glossary")
    return Message(
        id = getString("id"),
        isUser = optString("role") == "user",
        text = optString("text"),
        translation = optStringOrNull("translation"),
        feedback = optStringOrNull("feedback"),
        glossary = if (glossary == null) emptyList() else List(glossary.length()) { glossary.getString(it) },
        attachments = optJSONArray("attachments")?.objects()?.map { it.toAttachment() }.orEmpty(),
        contextNote = optStringOrNull("contextNote"),
    )
}

internal fun JSONObject.toThread() = ConversationThread(
    conversation = getJSONObject("conversation").toConversation(),
    messages = getJSONArray("messages").objects().map { it.toMessage() },
)

internal fun JSONObject.toSendResult() = SendResult(
    conversation = getJSONObject("conversation").toConversation(),
    userMessage = getJSONObject("userMessage").toMessage(),
    reply = getJSONObject("reply").toMessage(),
    lessonCompleted = optBoolean("lessonCompleted"),
)

internal fun JSONObject.toRecentSession() = RecentSession(
    id = getString("id"),
    kind = when (optString("kind")) {
        "topic" -> RecentKind.Topic
        "roleplay" -> RecentKind.Roleplay
        else -> RecentKind.FreeTalk
    },
    title = optString("title"),
    meta = optString("meta"),
)

internal fun JSONObject.listOf(key: String): List<JSONObject> = getJSONArray(key).objects()
