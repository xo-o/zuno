package com.zuno.ai.data

import androidx.annotation.StringRes
import com.zuno.ai.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * UI & tutor explanation language ("App language" in Settings · 4).
 * [nativeLabel] is the endonym shown as the row title; [subtitleRes] is the localized language name.
 */
enum class AppLanguage(
    val code: String,
    val bcp47: String,
    val nativeLabel: String,
    @param:StringRes val subtitleRes: Int,
) {
    English("en", "en-US", "English", R.string.settings_lang_en_us),
    Spanish("es", "es", "Español", R.string.settings_lang_es),
    French("fr", "fr", "Français", R.string.settings_lang_fr),
    German("de", "de", "Deutsch", R.string.settings_lang_de),
    Italian("it", "it", "Italiano", R.string.settings_lang_it),
    Portuguese("pt", "pt-BR", "Português", R.string.settings_lang_pt_br),
    Dutch("nl", "nl", "Nederlands", R.string.settings_lang_nl),
    Polish("pl", "pl", "Polski", R.string.settings_lang_pl);

    companion object {
        fun fromCode(code: String?): AppLanguage {
            if (code.isNullOrBlank()) return English
            val primary = code.lowercase().substringBefore('-').substringBefore('_')
            return entries.firstOrNull { it.code == primary } ?: English
        }
    }
}

val Levels = listOf("A1", "A2", "B1", "B2", "C1", "C2")

val TutorVoices = listOf("warm", "calm", "energetic")
val TeachingStyles = listOf("balanced", "gentle", "immersive")
val Focuses = listOf("conversation", "travel", "work", "exams")
val DailyGoals = listOf(5, 10, 15, 20, 30)

/** Settings · Preferences and learning goals; read by the tutor on the server. */
data class Preferences(
    val tutorVoice: String = "warm",
    val teachingStyle: String = "balanced",
    val strictMode: Boolean = false,
    val handsFree: Boolean = true,
    val suggestReplies: Boolean = true,
    val showTranslations: Boolean = true,
    val teachInTarget: Boolean = false,
    val dailyGoalMin: Int = 15,
    val focus: String = "conversation",
)

/** Where the learner is in a language they've studied (restored when switching back). */
data class LanguageProgress(val language: String, val level: String, val accent: String?, val levelChecked: Boolean)

data class Profile(
    val name: String,
    val email: String,
    /** Learning language code ("es", "ja", …); see [LearningLanguages]. */
    val targetLanguage: String,
    val level: String,
    val appLanguage: AppLanguage = AppLanguage.English,
    val accent: String? = null,
    /** False until the level for the current language was checked or picked. */
    val levelChecked: Boolean = true,
    val preferences: Preferences = Preferences(),
    val plan: String = "free",
    /** Profile photo (a short-lived signed URL for uploaded photos, or the Google picture). */
    val imageUrl: String? = null,
    val providers: List<String> = emptyList(),
    val languages: List<LanguageProgress> = emptyList(),
) {
    /** Empty when the account has no name (e.g. magic-link sign-ups), so greetings can omit it. */
    val firstName: String get() = name.trim().substringBefore(' ')
    val displayName: String get() = name.trim().ifBlank { email.substringBefore('@') }
    val language: LearningLanguage get() = LearningLanguages.find(targetLanguage)
    val detail: String get() = "${language.englishName} · $level"
    val isPro: Boolean get() = plan == "pro"
    val signedInWithGoogle: Boolean get() = "google" in providers
}

data class NotificationPrefs(
    val dailyReminder: Boolean = true,
    /** Local "HH:mm". */
    val reminderTime: String = "19:00",
    val timezone: String = "UTC",
    val scheduledSessions: Boolean = true,
    val progressMilestones: Boolean = true,
    val newContent: Boolean = false,
    val weeklySummaryEmail: Boolean = true,
    val productNewsEmail: Boolean = false,
)

enum class TopicStatus { NotStarted, InProgress, Completed }

enum class LessonState { Done, Current, Upcoming }

data class TopicSummary(
    val id: String,
    val title: String,
    val translation: String,
    val totalLessons: Int,
    val completedLessons: Int,
    val estimatedMinutesLeft: Int,
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

// ── 03g Interactive Voice Lesson & Evaluation Models ────────────────────────

enum class LessonStageKind(val wireName: String) {
    TeachModel("teach_model"),
    ShadowDrill("shadow_drill"),
    BuildingBlocks("building_blocks"),
    ActiveRecall("active_recall"),
    Roleplay("roleplay");

    companion object {
        fun fromWire(value: String?): LessonStageKind =
            entries.firstOrNull { it.wireName == value } ?: TeachModel
    }
}

data class LessonPillUi(
    val icon: String,
    val role: String,
    val goal: String,
)

sealed interface LessonCardUi {
    data class TeachCard(
        val setupPrompt: String,
        val phrase: String,
        val translation: String,
        val focusWord: String,
        val syllables: String,
        val cue: String,
        val chunks: List<String>,
    ) : LessonCardUi

    data class ScaffoldCard(
        val prompt: String,
        val newWord: String,
        val meaning: String,
        val stemHint: String,
    ) : LessonCardUi
}

data class RecallPromptUi(
    val eyebrow: String,
    val cue: String,
)

data class LessonStageUi(
    val stage: LessonStageKind,
    val pill: LessonPillUi,
    val card: LessonCardUi?,
    val recallPrompt: RecallPromptUi?,
    val rescueChips: List<String>,
)

data class PronunciationEvalUi(
    val score: Int,
    val passed: Boolean,
    val focusWord: String,
    val feedbackText: String,
    val showChunks: Boolean,
)

data class RescuePayloadUi(
    val action: String,
    val text: String,
)

data class LessonSessionTicket(
    val token: String,
    val url: String,
    val room: String,
    val conversationId: String,
    val topicId: String,
    val lessonId: String,
    val position: Int,
    val totalLessons: Int,
    val lessonTitle: String,
    val lessonTranslation: String,
)

data class CoachNote(
    val category: String,
    val correction: String,
    val ttsSampleText: String,
    val kind: String, // "grammar" | "pronunciation"
)

data class PhraseResult(
    val phrase: String,
    val status: String, // "mastered" | "practice"
) {
    val isMastered: Boolean get() = status != "practice"
}

data class LessonEvaluation(
    val id: String = "",
    val lessonId: String,
    val topicId: String = "",
    val lessonPosition: Int = 1,
    val totalLessons: Int = 5,
    val lessonTitle: String = "",
    val lessonTranslation: String = "",
    val durationSec: Int,
    val pronunciationScore: Int,
    val goalsCompleted: Int,
    val goalsTotal: Int,
    val fluencyLabel: String,
    val coachNotes: List<CoachNote>,
    val phraseResults: List<PhraseResult>,
    val nextLessonId: String? = null,
)

data class CompleteLessonResult(
    val evaluation: LessonEvaluation,
    val topic: TopicDetail?,
    val nextLessonId: String?,
)

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
    val pinned: Boolean = false,
    val public: Boolean = false,
    val shareUrl: String? = null,
    val levelCheck: Boolean = false,
)

data class ShareResult(val public: Boolean, val shareUrl: String?)

enum class ReportReason(val code: String) {
    WrongCorrection("wrong_correction"),
    WrongLanguageLevel("wrong_language_level"),
    Offensive("offensive"),
    Unsafe("unsafe"),
    Other("other"),
}

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
    /** Set when a level check finished: the learner's new CEFR level. */
    val levelAssessed: String? = null,
)

enum class RecentKind { FreeTalk, Topic, Roleplay }

data class RecentSession(
    val id: String,
    val kind: RecentKind,
    val title: String,
    val meta: String,
    val pinned: Boolean = false,
    /** The pinned conversation behind a topic or roleplay row. */
    val conversationId: String? = null,
)

enum class ScheduleRepeat(val code: String) {
    Once("once"), Daily("daily"), Weekdays("weekdays"), Weekly("weekly"), Monthly("monthly");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: Daily
    }
}

/** A planned practice session. [weekdays] use 0 = Monday … 6 = Sunday. */
data class Schedule(
    val id: String,
    val isRoleplay: Boolean,
    val topicId: String?,
    val roleplayId: String?,
    val title: String,
    /** "Topic · Lesson 2" / "You: Customer · Zuno: Barista" */
    val meta: String,
    val repeat: ScheduleRepeat,
    val timeOfDay: String,
    val date: String?,
    val weekdays: List<Int>,
    val monthDay: Int?,
    val timezone: String,
    val durationMin: Int,
    val active: Boolean,
    val remindBeforeMin: Int?,
    val notifyEmail: Boolean,
    val nextRunAtMillis: Long?,
)

data class ScheduleTopic(
    val id: String,
    val title: String,
    val totalLessons: Int,
    val nextLessonPosition: Int?,
    val completed: Boolean,
    val durationMin: Int,
)

data class ScheduleRoleplay(val id: String, val title: String, val userRole: String, val aiRole: String, val durationMin: Int)

data class ScheduleTargets(val topics: List<ScheduleTopic>, val roleplays: List<ScheduleRoleplay>)

/** Body for creating or editing a schedule. */
data class ScheduleDraft(
    val isRoleplay: Boolean,
    val targetId: String,
    val repeat: ScheduleRepeat,
    val timeOfDay: String,
    val date: String?,
    val weekdays: List<Int>,
    val monthDay: Int?,
    val timezone: String,
    val durationMin: Int,
    val active: Boolean,
    val remindBeforeMin: Int?,
    val notifyEmail: Boolean,
)

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
        targetLanguage = profile.optString("targetLanguage", "es"),
        level = profile.optString("level", "B1"),
        appLanguage = AppLanguage.fromCode(profile.optString("nativeLanguage", "en")),
        accent = profile.optStringOrNull("accent"),
        levelChecked = profile.optBoolean("levelChecked", true),
        preferences = Preferences(
            tutorVoice = profile.optString("tutorVoice", "warm"),
            teachingStyle = profile.optString("teachingStyle", "balanced"),
            strictMode = profile.optBoolean("strictMode", false),
            handsFree = profile.optBoolean("handsFree", true),
            suggestReplies = profile.optBoolean("suggestReplies", true),
            showTranslations = profile.optBoolean("showTranslations", true),
            teachInTarget = profile.optBoolean("teachInTarget", false),
            dailyGoalMin = profile.optInt("dailyGoalMin", 15),
            focus = profile.optString("focus", "conversation"),
        ),
        plan = profile.optString("plan", "free"),
        imageUrl = user.optStringOrNull("image"),
        providers = optJSONArray("providers")?.let { array -> List(array.length()) { array.getString(it) } }.orEmpty(),
        languages = optJSONArray("languages")?.objects()?.map {
            LanguageProgress(it.optString("language"), it.optString("level", "B1"), it.optStringOrNull("accent"), it.optBoolean("levelChecked"))
        }.orEmpty(),
    )
}

internal fun JSONObject.toNotificationPrefs() = NotificationPrefs(
    dailyReminder = optBoolean("dailyReminder", true),
    reminderTime = optString("reminderTime", "19:00"),
    timezone = optString("timezone", "UTC"),
    scheduledSessions = optBoolean("scheduledSessions", true),
    progressMilestones = optBoolean("progressMilestones", true),
    newContent = optBoolean("newContent", false),
    weeklySummaryEmail = optBoolean("weeklySummaryEmail", true),
    productNewsEmail = optBoolean("productNewsEmail", false),
)

internal fun JSONObject.toLearningLanguage() = LearningLanguage(
    code = getString("code"),
    englishName = optString("englishName"),
    nativeName = optString("nativeName"),
    accents = optJSONArray("accents")?.objects()?.map { Accent(it.optString("id"), it.optString("label")) }.orEmpty(),
    rtl = optBoolean("rtl"),
)

private fun parseInstant(value: String?): Long? =
    value?.takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

internal fun JSONObject.toSchedule() = Schedule(
    id = getString("id"),
    isRoleplay = optString("targetKind") == "roleplay",
    topicId = optStringOrNull("topicId"),
    roleplayId = optStringOrNull("roleplayId"),
    title = optString("title"),
    meta = optString("meta"),
    repeat = ScheduleRepeat.fromCode(optString("repeat")),
    timeOfDay = optString("timeOfDay", "09:00"),
    date = optStringOrNull("date"),
    weekdays = optJSONArray("weekdays")?.let { array -> List(array.length()) { array.getInt(it) } }.orEmpty(),
    monthDay = if (isNull("monthDay")) null else optInt("monthDay"),
    timezone = optString("timezone", "UTC"),
    durationMin = optInt("durationMin", 15),
    active = optBoolean("active", true),
    remindBeforeMin = if (isNull("remindBeforeMin")) null else optInt("remindBeforeMin"),
    notifyEmail = optBoolean("notifyEmail"),
    nextRunAtMillis = if (isNull("nextRunAt")) null else parseInstant(optString("nextRunAt")),
)

internal fun JSONObject.toScheduleTargets() = ScheduleTargets(
    topics = getJSONArray("topics").objects().map {
        val next = it.optJSONObject("nextLesson")
        ScheduleTopic(
            id = it.getString("id"),
            title = it.optString("title"),
            totalLessons = it.optInt("totalLessons", 5),
            nextLessonPosition = next?.optInt("position"),
            completed = it.optBoolean("completed"),
            durationMin = it.optInt("durationMin", 15),
        )
    },
    roleplays = getJSONArray("roleplays").objects().map {
        ScheduleRoleplay(it.getString("id"), it.optString("title"), it.optString("userRole"), it.optString("aiRole"), it.optInt("durationMin", 10))
    },
)

internal fun JSONObject.toTopicSummary(): TopicSummary {
    val total = optInt("totalLessons")
    val completed = optInt("completedLessons")
    return TopicSummary(
        id = getString("id"),
        title = optString("title"),
        translation = optString("translation"),
        totalLessons = total,
        completedLessons = completed,
        estimatedMinutesLeft = if (has("estimatedMinutesLeft")) optInt("estimatedMinutesLeft") else (total - completed).coerceAtLeast(0) * 5,
        progress = optInt("progress"),
        status = when (optString("status")) {
            "completed" -> TopicStatus.Completed
            "in_progress" -> TopicStatus.InProgress
            else -> TopicStatus.NotStarted
        },
    )
}

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
    pinned = optBoolean("pinned"),
    public = optString("visibility") == "public",
    shareUrl = optStringOrNull("shareUrl"),
    levelCheck = optBoolean("levelCheck"),
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
    levelAssessed = optStringOrNull("levelAssessed"),
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
    pinned = optBoolean("pinned"),
    conversationId = optStringOrNull("conversationId"),
)

internal fun JSONObject.listOf(key: String): List<JSONObject> = getJSONArray(key).objects()

internal fun JSONObject.toLessonSessionTicket() = LessonSessionTicket(
    token = getString("token"),
    url = getString("url"),
    room = getString("room"),
    conversationId = getString("conversationId"),
    topicId = getString("topicId"),
    lessonId = getString("lessonId"),
    position = optInt("position", 1),
    totalLessons = optInt("totalLessons", 5),
    lessonTitle = optString("lessonTitle"),
    lessonTranslation = optString("lessonTranslation"),
)

internal fun JSONObject.toLessonStageUi(): LessonStageUi {
    val pillObj = optJSONObject("pill") ?: JSONObject()
    val cardObj = optJSONObject("card")
    val card: LessonCardUi? = when (cardObj?.optString("kind")) {
        "teach_card" -> LessonCardUi.TeachCard(
            setupPrompt = cardObj.optString("setupPrompt"),
            phrase = cardObj.optString("phrase"),
            translation = cardObj.optString("translation"),
            focusWord = cardObj.optString("focusWord"),
            syllables = cardObj.optString("syllables"),
            cue = cardObj.optString("cue"),
            chunks = cardObj.optJSONArray("chunks")?.let { arr -> List(arr.length()) { arr.getString(it) } }.orEmpty(),
        )
        "scaffold_card" -> LessonCardUi.ScaffoldCard(
            prompt = cardObj.optString("prompt"),
            newWord = cardObj.optString("newWord"),
            meaning = cardObj.optString("meaning"),
            stemHint = cardObj.optString("stemHint"),
        )
        else -> null
    }
    val recallObj = optJSONObject("recallPrompt")
    val recallPrompt = recallObj?.let {
        RecallPromptUi(
            eyebrow = it.optString("eyebrow", "Say from memory"),
            cue = it.optString("cue"),
        )
    }
    val chips = optJSONArray("rescueChips")?.let { arr -> List(arr.length()) { arr.getString(it) } }.orEmpty()
    return LessonStageUi(
        stage = LessonStageKind.fromWire(optString("stage")),
        pill = LessonPillUi(
            icon = pillObj.optString("icon", "book-open"),
            role = pillObj.optString("role", "Learn"),
            goal = pillObj.optString("goal"),
        ),
        card = card,
        recallPrompt = recallPrompt,
        rescueChips = chips,
    )
}

internal fun JSONObject.toPronunciationEvalUi() = PronunciationEvalUi(
    score = optInt("score", 0),
    passed = optBoolean("passed", false),
    focusWord = optString("focusWord"),
    feedbackText = optString("feedbackText"),
    showChunks = optBoolean("showChunks", false),
)

internal fun JSONObject.toRescuePayloadUi() = RescuePayloadUi(
    action = optString("action"),
    text = optString("text"),
)

internal fun JSONObject.toLessonEvaluation(fallbackLessonId: String = ""): LessonEvaluation {
    val notes = optJSONArray("coachNotes")?.objects()?.map {
        CoachNote(
            category = it.optString("category"),
            correction = it.optString("correction"),
            ttsSampleText = it.optString("ttsSampleText"),
            kind = it.optString("kind", "grammar"),
        )
    }.orEmpty()
    val phrases = optJSONArray("phraseResults")?.objects()?.map {
        PhraseResult(
            phrase = it.optString("phrase"),
            status = it.optString("status", "mastered"),
        )
    }.orEmpty()
    return LessonEvaluation(
        id = optString("id"),
        lessonId = optString("lessonId").ifBlank { fallbackLessonId },
        topicId = optString("topicId"),
        lessonPosition = optInt("lessonPosition", 1),
        totalLessons = optInt("totalLessons", 5),
        lessonTitle = optString("lessonTitle"),
        lessonTranslation = optString("lessonTranslation"),
        durationSec = optInt("durationSec", 300),
        pronunciationScore = optInt("pronunciationScore", 90),
        goalsCompleted = optInt("goalsCompleted", 5),
        goalsTotal = optInt("goalsTotal", 5).coerceAtLeast(1),
        fluencyLabel = optString("fluencyLabel", "Natural"),
        coachNotes = notes,
        phraseResults = phrases,
        nextLessonId = optStringOrNull("nextLessonId"),
    )
}

internal fun LessonEvaluation.toRequestBody(conversationId: String?): JSONObject {
    val notesArr = JSONArray().apply {
        coachNotes.forEach { n ->
            put(
                JSONObject()
                    .put("category", n.category)
                    .put("correction", n.correction)
                    .put("ttsSampleText", n.ttsSampleText)
                    .put("kind", n.kind),
            )
        }
    }
    val phrasesArr = JSONArray().apply {
        phraseResults.forEach { p ->
            put(
                JSONObject()
                    .put("phrase", p.phrase)
                    .put("status", p.status),
            )
        }
    }
    return JSONObject().apply {
        conversationId?.let { put("conversationId", it) }
        put("durationSec", durationSec.coerceAtLeast(1))
        put("pronunciationScore", pronunciationScore.coerceIn(0, 100))
        put("goalsCompleted", goalsCompleted.coerceAtLeast(0))
        put("goalsTotal", goalsTotal.coerceAtLeast(1))
        put("fluencyLabel", fluencyLabel)
        put("coachNotes", notesArr)
        put("phraseResults", phrasesArr)
    }
}
