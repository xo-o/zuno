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

val TutorIds = listOf("sofia", "valeria", "claire", "mateo", "lucas", "ethan")
val TutorVoices = listOf("warm", "calm", "crisp", "energetic", "steady", "casual")
val TeachingStyles = listOf("balanced", "gentle", "immersive")
val Focuses = listOf("conversation", "travel", "work", "exams", "culture")
val DailyGoals = listOf(5, 10, 15, 20, 30)

data class RecommendedTopic(
    val id: String,
    val title: String,
    val translation: String,
    val reason: String,
    val prompt: String,
)

/** Settings · Preferences and learning goals; read by the tutor on the server. */
data class Preferences(
    val tutorId: String = "sofia",
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
    val onboardingCompleted: Boolean = true,
    val interests: List<String> = emptyList(),
    val goalNote: String? = null,
    val recommendedTopics: List<RecommendedTopic> = emptyList(),
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
    val language: String = "es",
    val category: String = "daily_life",
    val visibility: String = "private",
    val sourceKind: String = "user",
    val shareUrl: String? = null,
    val sourceTopicId: String? = null,
    val authorHandle: String? = null,
    val learnersCount: Int = 0,
    val pinned: Boolean = false,
) {
    val isPublic: Boolean get() = visibility == "public"
    val isUserOwned: Boolean get() = sourceKind == "user"
    val effectiveShareUrl: String?
        get() = shareUrl ?: if (!isUserOwned && sourceTopicId != null) "https://api.heyzuno.app/t/$sourceTopicId" else null
}

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

data class OnboardingTurnResult(
    val step: String,
    val valid: Boolean,
    val name: String = "",
    val level: String = "B1",
    val focus: String = "conversation",
    val interests: List<String> = emptyList(),
    val goalNote: String = "",
    val retryPrompt: String? = null,
)

data class OnboardingCompleteResult(
    val profile: Profile,
    val starterTopic: TopicDetail,
    val recommendedTopics: List<RecommendedTopic>,
    val pathSubtitle: String,
)

data class OnboardingVoiceTurnEvent(
    val step: String,
    val transcript: String,
    val name: String = "",
    val level: String = "",
    val focus: String = "conversation",
    val interests: List<String> = emptyList(),
    val goalNote: String = "",
    val nextStep: String = "",
    val nextPrompt: String = "",
)

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
    val contextNote: String = "",
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
        val goalLabel: String = "",
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
    val blankLengths: List<Int> = emptyList(),
)

data class RoleplayGoalUi(
    val id: String,
    val index: Int,
    val label: String,
    val description: String,
    val samplePhrase: String = "",
    val completed: Boolean = false,
    val current: Boolean = false,
)

data class LessonTurnUi(
    val id: String,
    val isUser: Boolean,
    val senderLabel: String,
    val text: String,
    val translation: String? = null,
    val actions: List<String> = emptyList(),
)

data class LessonStageUi(
    val stage: LessonStageKind,
    val pill: LessonPillUi,
    val card: LessonCardUi?,
    val recallPrompt: RecallPromptUi?,
    val rescueChips: List<String>,
    val roleplayGoals: List<RoleplayGoalUi> = emptyList(),
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

data class RoleplaySessionTicket(
    val token: String,
    val url: String,
    val room: String,
    val conversationId: String,
    val roleplayId: String,
    val roleplay: Roleplay,
    val goals: List<RoleplayGoalUi> = emptyList(),
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
    val setting: String = "",
    val language: String = "es",
    val category: String = "social",
    val visibility: String = "private",
    val sourceKind: String = "user",
    val shareUrl: String? = null,
    val sourceRoleplayId: String? = null,
    val authorHandle: String? = null,
    val practicedCount: Int = 0,
    val pinned: Boolean = false,
) {
    val isPublic: Boolean get() = visibility == "public"
    val isUserOwned: Boolean get() = sourceKind == "user"
    val effectiveShareUrl: String?
        get() = shareUrl ?: if (!isUserOwned && sourceRoleplayId != null) "https://api.heyzuno.app/r/$sourceRoleplayId" else null
}

// ── 03 · Topics & 04 · Roleplays Discovery, Preview & Chat Builder Models ───

data class DiscoverTopicLesson(
    val position: Int,
    val title: String,
    val translation: String,
    val objective: String,
)

data class DiscoverTopic(
    val id: String,
    val title: String,
    val translation: String,
    val language: String,
    val category: String,
    val sourceKind: String, // "system" | "community"
    val authorHandle: String?,
    val level: String,
    val learnersCount: Int,
    val totalLessons: Int,
    val shareUrl: String?,
    val alreadySaved: Boolean,
    val savedTopicId: String? = null,
    val lessons: List<DiscoverTopicLesson> = emptyList(),
) {
    val isOfficial: Boolean get() = sourceKind == "system"
}

data class DiscoverTopicsResult(
    val official: List<DiscoverTopic>,
    val community: List<DiscoverTopic>,
)

data class DiscoverRoleplay(
    val id: String,
    val title: String,
    val scenario: String,
    val userRole: String,
    val aiRole: String,
    val setting: String,
    val language: String,
    val category: String,
    val sourceKind: String, // "system" | "community"
    val authorHandle: String?,
    val level: String,
    val practicedCount: Int,
    val shareUrl: String?,
    val alreadySaved: Boolean,
    val savedRoleplayId: String? = null,
) {
    val isOfficial: Boolean get() = sourceKind == "system"
}

data class DiscoverRoleplaysResult(
    val official: List<DiscoverRoleplay>,
    val community: List<DiscoverRoleplay>,
)

data class TopicDraftLesson(
    val title: String,
    val translation: String,
    val objective: String,
)

data class TopicDraft(
    val title: String,
    val translation: String,
    val category: String,
    val prompt: String,
    val lessons: List<TopicDraftLesson>,
)

data class TopicBuilderTurnResult(
    val reply: String,
    val ready: Boolean,
    val draft: TopicDraft?,
    val contextNote: String?,
)

data class RoleplayDraft(
    val title: String,
    val scenario: String,
    val userRole: String,
    val aiRole: String,
    val setting: String,
    val category: String,
)

data class RoleplayBuilderTurnResult(
    val reply: String,
    val ready: Boolean,
    val draft: RoleplayDraft?,
    val contextNote: String?,
)

data class BuilderTurnMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val attachmentNames: List<String> = emptyList(),
    val contextNote: String? = null,
)

data class TopicShareResult(
    val public: Boolean,
    val shareUrl: String?,
    val learnersCount: Int,
    val topic: TopicDetail,
)

data class RoleplayShareResult(
    val public: Boolean,
    val shareUrl: String?,
    val practicedCount: Int,
    val roleplay: Roleplay,
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
    val kind: String = "text",
    val callDurationSec: Int? = null,
    val callIsVideo: Boolean = false,
    val callTipsCount: Int = 0,
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

internal fun JSONObject.toRecommendedTopic() = RecommendedTopic(
    id = optString("id"),
    title = optString("title"),
    translation = optString("translation"),
    reason = optString("reason"),
    prompt = optString("prompt"),
)

internal fun JSONObject.toOnboardingTurnResult() = OnboardingTurnResult(
    step = optString("step"),
    valid = optBoolean("valid", true),
    name = optString("name"),
    level = optString("level", "B1"),
    focus = optString("focus", "conversation"),
    interests = optJSONArray("interests")?.let { arr -> List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
    goalNote = optString("goalNote"),
    retryPrompt = optStringOrNull("retryPrompt"),
)

internal fun JSONObject.toOnboardingCompleteResult(): OnboardingCompleteResult {
    val parsedProfile = toProfile()
    val recs = optJSONArray("recommendedTopics")?.objects()?.map { it.toRecommendedTopic() } ?: parsedProfile.recommendedTopics
    return OnboardingCompleteResult(
        profile = parsedProfile.copy(recommendedTopics = recs),
        starterTopic = getJSONObject("starterTopic").toTopicDetail(),
        recommendedTopics = recs,
        pathSubtitle = optString("pathSubtitle"),
    )
}

internal fun JSONObject.toOnboardingVoiceTurnEvent() = OnboardingVoiceTurnEvent(
    step = optString("step"),
    transcript = optString("transcript"),
    name = optString("name"),
    level = optString("level"),
    focus = optString("focus", "conversation"),
    interests = optJSONArray("interests")?.let { arr -> List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
    goalNote = optString("goalNote"),
    nextStep = optString("nextStep"),
    nextPrompt = optString("nextPrompt"),
)

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
        onboardingCompleted = profile.optBoolean("onboardingCompleted", false),
        interests = profile.optJSONArray("interests")?.let { arr -> List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
        goalNote = profile.optStringOrNull("goalNote"),
        recommendedTopics = profile.optJSONArray("recommendedTopics")?.objects()?.map { it.toRecommendedTopic() }.orEmpty(),
        preferences = run {
            val rawVoice = profile.optString("tutorVoice", "warm")
            val fallbackId = when (rawVoice) {
                "calm" -> "valeria"
                "crisp" -> "claire"
                "energetic" -> "mateo"
                "steady" -> "lucas"
                "casual" -> "ethan"
                else -> "sofia"
            }
            val rawId = profile.optString("tutorId", fallbackId).ifBlank { fallbackId }
            Preferences(
                tutorId = if (rawId == "elena") "sofia" else rawId,
                tutorVoice = rawVoice,
                teachingStyle = profile.optString("teachingStyle", "balanced"),
                strictMode = profile.optBoolean("strictMode", false),
                handsFree = profile.optBoolean("handsFree", true),
                suggestReplies = profile.optBoolean("suggestReplies", true),
                showTranslations = profile.optBoolean("showTranslations", true),
                teachInTarget = profile.optBoolean("teachInTarget", false),
                dailyGoalMin = profile.optInt("dailyGoalMin", 15),
                focus = profile.optString("focus", "conversation"),
            )
        },
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
        language = optString("language", "es"),
        category = optString("category", "daily_life"),
        visibility = optString("visibility", "private"),
        sourceKind = optString("sourceKind", "user"),
        shareUrl = optStringOrNull("shareUrl"),
        sourceTopicId = optStringOrNull("sourceTopicId"),
        authorHandle = optStringOrNull("authorHandle"),
        learnersCount = optInt("learnersCount", 0),
        pinned = optBoolean("pinned", false),
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
    setting = optString("setting", ""),
    language = optString("language", "es"),
    category = optString("category", "social"),
    visibility = optString("visibility", "private"),
    sourceKind = optString("sourceKind", "user"),
    shareUrl = optStringOrNull("shareUrl"),
    sourceRoleplayId = optStringOrNull("sourceRoleplayId"),
    authorHandle = optStringOrNull("authorHandle"),
    practicedCount = optInt("practicedCount", 0),
    pinned = optBoolean("pinned", false),
)

internal fun JSONObject.toDiscoverTopic() = DiscoverTopic(
    id = getString("id"),
    title = optString("title"),
    translation = optString("translation"),
    language = optString("language", "es"),
    category = optString("category", "daily_life"),
    sourceKind = optString("sourceKind", "community"),
    authorHandle = optStringOrNull("authorHandle"),
    level = optString("level", "B1"),
    learnersCount = optInt("learnersCount", 0),
    totalLessons = optInt("totalLessons", 5),
    shareUrl = optStringOrNull("shareUrl"),
    alreadySaved = optBoolean("alreadySaved", false),
    savedTopicId = optStringOrNull("savedTopicId"),
    lessons = optJSONArray("lessons")?.objects()?.map {
        DiscoverTopicLesson(
            position = it.optInt("position"),
            title = it.optString("title"),
            translation = it.optString("translation"),
            objective = it.optString("objective"),
        )
    }.orEmpty(),
)

internal fun JSONObject.toDiscoverRoleplay() = DiscoverRoleplay(
    id = getString("id"),
    title = optString("title"),
    scenario = optString("scenario"),
    userRole = optString("userRole"),
    aiRole = optString("aiRole"),
    setting = optString("setting", ""),
    language = optString("language", "es"),
    category = optString("category", "social"),
    sourceKind = optString("sourceKind", "community"),
    authorHandle = optStringOrNull("authorHandle"),
    level = optString("level", "B1"),
    practicedCount = optInt("practicedCount", 0),
    shareUrl = optStringOrNull("shareUrl"),
    alreadySaved = optBoolean("alreadySaved", false),
    savedRoleplayId = optStringOrNull("savedRoleplayId"),
)

internal fun JSONObject.toTopicBuilderTurnResult() = TopicBuilderTurnResult(
    reply = optString("reply"),
    ready = optBoolean("ready", false),
    draft = optJSONObject("draft")?.let { d ->
        TopicDraft(
            title = d.optString("title"),
            translation = d.optString("translation"),
            category = d.optString("category", "daily_life"),
            prompt = d.optString("prompt", d.optString("title")),
            lessons = d.optJSONArray("lessons")?.objects()?.map { l ->
                TopicDraftLesson(
                    title = l.optString("title"),
                    translation = l.optString("translation"),
                    objective = l.optString("objective"),
                )
            }.orEmpty(),
        )
    },
    contextNote = optStringOrNull("contextNote"),
)

internal fun JSONObject.toRoleplayBuilderTurnResult() = RoleplayBuilderTurnResult(
    reply = optString("reply"),
    ready = optBoolean("ready", false),
    draft = optJSONObject("draft")?.let { d ->
        RoleplayDraft(
            title = d.optString("title"),
            scenario = d.optString("scenario"),
            userRole = d.optString("userRole"),
            aiRole = d.optString("aiRole"),
            setting = d.optString("setting"),
            category = d.optString("category", "social"),
        )
    },
    contextNote = optStringOrNull("contextNote"),
)

internal fun TopicDraft.toJson(): JSONObject = JSONObject()
    .put("title", title)
    .put("translation", translation)
    .put("category", category)
    .put("prompt", prompt)
    .put(
        "lessons",
        org.json.JSONArray().apply {
            lessons.forEach { l ->
                put(
                    JSONObject()
                        .put("title", l.title)
                        .put("translation", l.translation)
                        .put("objective", l.objective),
                )
            }
        },
    )

internal fun RoleplayDraft.toJson(): JSONObject = JSONObject()
    .put("title", title)
    .put("scenario", scenario)
    .put("userRole", userRole)
    .put("aiRole", aiRole)
    .put("setting", setting)
    .put("category", category)

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
    val meta = optJSONObject("metadata")
    return Message(
        id = getString("id"),
        isUser = optString("role") == "user",
        text = optString("text"),
        translation = optStringOrNull("translation"),
        feedback = optStringOrNull("feedback"),
        glossary = if (glossary == null) emptyList() else List(glossary.length()) { glossary.getString(it) },
        attachments = optJSONArray("attachments")?.objects()?.map { it.toAttachment() }.orEmpty(),
        contextNote = optStringOrNull("contextNote"),
        kind = optString("kind", "text"),
        callDurationSec = meta?.let { if (it.has("durationSec")) it.optInt("durationSec") else null },
        callIsVideo = meta?.optBoolean("isVideo", false) ?: false,
        callTipsCount = meta?.optInt("tipsCount", 0) ?: 0,
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

internal fun JSONObject.toRoleplaySessionTicket(): RoleplaySessionTicket {
    val rp = getJSONObject("roleplay").toRoleplay()
    val goals = optJSONArray("goals")?.objects()?.mapIndexed { idx, g ->
        RoleplayGoalUi(
            id = g.optString("id", "goal_${idx + 1}"),
            index = g.optInt("index", idx + 1),
            label = g.optString("label"),
            description = g.optString("description"),
            samplePhrase = g.optString("samplePhrase"),
            completed = false,
            current = idx == 0,
        )
    }.orEmpty()
    return RoleplaySessionTicket(
        token = getString("token"),
        url = getString("url"),
        room = getString("room"),
        conversationId = getString("conversationId"),
        roleplayId = optString("roleplayId").ifBlank { rp.id },
        roleplay = rp,
        goals = goals,
    )
}

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
            goalLabel = cardObj.optString("goalLabel"),
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
        val blanks = it.optJSONArray("blankLengths")?.let { arr ->
            List(arr.length()) { idx -> arr.optInt(idx, 5) }
        }.orEmpty()
        RecallPromptUi(
            eyebrow = it.optString("eyebrow", "Say from memory"),
            cue = it.optString("cue"),
            blankLengths = blanks,
        )
    }
    val chips = optJSONArray("rescueChips")?.let { arr -> List(arr.length()) { arr.getString(it) } }.orEmpty()
    val goals = optJSONArray("roleplayGoals")?.objects()?.mapIndexed { idx, g ->
        RoleplayGoalUi(
            id = g.optString("id", "goal_${idx + 1}"),
            index = g.optInt("index", idx + 1),
            label = g.optString("label"),
            description = g.optString("description"),
            samplePhrase = g.optString("samplePhrase"),
            completed = g.optBoolean("completed", false),
            current = g.optBoolean("current", idx == 0),
        )
    }.orEmpty()
    return LessonStageUi(
        stage = LessonStageKind.fromWire(optString("stage")),
        pill = LessonPillUi(
            icon = pillObj.optString("icon", "book-open"),
            role = pillObj.optString("role", "Learn"),
            goal = pillObj.optString("goal"),
            contextNote = pillObj.optString("contextNote"),
        ),
        card = card,
        recallPrompt = recallPrompt,
        rescueChips = chips,
        roleplayGoals = goals,
    )
}

internal fun JSONObject.toLessonTurnUi(): LessonTurnUi {
    val actions = optJSONArray("actions")?.let { arr -> List(arr.length()) { arr.optString(it) } }.orEmpty()
    val speakerStr = optString("speaker", "agent")
    return LessonTurnUi(
        id = optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        isUser = speakerStr == "user",
        senderLabel = optString("senderLabel").ifBlank { if (speakerStr == "user") "You" else "Sofía" },
        text = optString("text"),
        translation = optStringOrNull("translation"),
        actions = actions,
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

// ── Unified Chats, Peer Messaging & 1:1 Call Rooms ──────────────────────────

enum class ChatsFilter { All, AiTutor, People, Open }

data class PeerLearner(
    val id: String,
    val name: String,
    val shortName: String,
    val initial: String,
    val imageUrl: String?,
    val language: String,
    val languageName: String,
    val level: String,
    val nativeLanguage: String,
    val streakDays: Int,
    val sessionsCount: Int,
    val interests: List<String>,
    val openToSpeak: Boolean,
    val isPartner: Boolean,
    val activeLabel: String,
    val isSeeded: Boolean = false,
)

enum class UnifiedChatKind { Ai, Peer }

data class UnifiedChatItem(
    val id: String,
    val kind: UnifiedChatKind,
    val conversationId: String?,
    val peerConversationId: String?,
    val partnerId: String?,
    val title: String,
    val badge: String?,
    val isVoice: Boolean,
    val openToSpeak: Boolean,
    val snippet: String,
    val unread: Boolean,
    val pinned: Boolean,
    val updatedAtMillis: Long,
    val avatarInitial: String,
    val avatarUrl: String?,
)

data class ChatsOverview(
    val openToSpeak: Boolean,
    val language: String,
    val languageName: String,
    val level: String,
    val openSpeakers: List<PeerLearner>,
    val partners: List<PeerLearner>,
    val conversations: List<UnifiedChatItem>,
)

data class PeerMessage(
    val id: String,
    val peerConversationId: String,
    val senderId: String,
    val isMine: Boolean,
    val kind: String, // "text" | "call_invite" | "missed_invite" | "call_summary"
    val text: String,
    val callRoomId: String?,
    val cameraEnabled: Boolean,
    val durationSec: Int?,
    val createdAtMillis: Long,
)

data class PeerCallRoom(
    val id: String,
    val peerConversationId: String,
    val hostUserId: String,
    val guestUserId: String,
    val livekitRoom: String,
    val status: String, // "waiting" | "active" | "ended" | "declined" | "missed"
    val cameraEnabled: Boolean,
    val startedAtMillis: Long?,
    val durationSec: Int,
)

data class PeerConversationMeta(
    val id: String,
    val language: String,
    val muted: Boolean,
    val partnerId: String,
)

data class PeerConversationThread(
    val conversation: PeerConversationMeta,
    val partner: PeerLearner?,
    val messages: List<PeerMessage>,
    val activeCall: PeerCallRoom?,
)

data class LivekitTicket(
    val token: String,
    val url: String,
    val room: String,
    val identity: String,
)

data class StartPeerCallResult(
    val call: PeerCallRoom,
    val livekit: LivekitTicket,
    val message: PeerMessage,
)

data class JoinPeerCallResult(
    val call: PeerCallRoom,
    val livekit: LivekitTicket,
)

data class CoachPromptItem(
    val phrase: String,
    val translation: String,
)

data class CoachPrompts(
    val answers: List<CoachPromptItem> = emptyList(),
    val followUps: List<CoachPromptItem> = emptyList(),
    val questions: List<CoachPromptItem> = emptyList(),
)

internal fun JSONObject.toPeerLearner() = PeerLearner(
    id = getString("id"),
    name = optString("name"),
    shortName = optString("shortName"),
    initial = optString("initial", "L"),
    imageUrl = optStringOrNull("image"),
    language = optString("language", "es"),
    languageName = optString("languageName", "Spanish"),
    level = optString("level", "B1"),
    nativeLanguage = optString("nativeLanguage", "English"),
    streakDays = optInt("streakDays", 7),
    sessionsCount = optInt("sessionsCount", 12),
    interests = optJSONArray("interests")?.let { arr -> List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
    openToSpeak = optBoolean("openToSpeak", false),
    isPartner = optBoolean("isPartner", false),
    activeLabel = optString("activeLabel"),
    isSeeded = optBoolean("isSeeded", false),
)

internal fun JSONObject.toUnifiedChatItem() = UnifiedChatItem(
    id = getString("id"),
    kind = if (optString("kind") == "peer") UnifiedChatKind.Peer else UnifiedChatKind.Ai,
    conversationId = optStringOrNull("conversationId"),
    peerConversationId = optStringOrNull("peerConversationId"),
    partnerId = optStringOrNull("partnerId"),
    title = optString("title"),
    badge = optStringOrNull("badge"),
    isVoice = optBoolean("isVoice", false),
    openToSpeak = optBoolean("openToSpeak", false),
    snippet = optString("snippet"),
    unread = optBoolean("unread", false),
    pinned = optBoolean("pinned", false),
    updatedAtMillis = parseInstant(optString("updatedAt")) ?: System.currentTimeMillis(),
    avatarInitial = optString("avatarInitial", "Z"),
    avatarUrl = optStringOrNull("avatarUrl"),
)

internal fun JSONObject.toChatsOverview() = ChatsOverview(
    openToSpeak = optBoolean("openToSpeak", true),
    language = optString("language", "es"),
    languageName = optString("languageName", "Spanish"),
    level = optString("level", "B1"),
    openSpeakers = optJSONArray("openSpeakers")?.objects()?.map { it.toPeerLearner() }.orEmpty(),
    partners = optJSONArray("partners")?.objects()?.map { it.toPeerLearner() }.orEmpty(),
    conversations = optJSONArray("conversations")?.objects()?.map { it.toUnifiedChatItem() }.orEmpty(),
)

internal fun JSONObject.toPeerMessage(): PeerMessage {
    val meta = optJSONObject("metadata")
    return PeerMessage(
        id = getString("id"),
        peerConversationId = optString("peerConversationId"),
        senderId = optString("senderId"),
        isMine = optBoolean("isMine", false),
        kind = optString("kind", "text"),
        text = optString("text"),
        callRoomId = optStringOrNull("callRoomId"),
        cameraEnabled = meta?.optBoolean("cameraEnabled", false) ?: false,
        durationSec = meta?.let { if (it.has("durationSec")) it.optInt("durationSec") else null },
        createdAtMillis = parseInstant(optString("createdAt")) ?: System.currentTimeMillis(),
    )
}

internal fun JSONObject.toPeerCallRoom() = PeerCallRoom(
    id = getString("id"),
    peerConversationId = optString("peerConversationId"),
    hostUserId = optString("hostUserId"),
    guestUserId = optString("guestUserId"),
    livekitRoom = optString("livekitRoom"),
    status = optString("status", "waiting"),
    cameraEnabled = optBoolean("cameraEnabled", false),
    startedAtMillis = parseInstant(optStringOrNull("startedAt")),
    durationSec = optInt("durationSec", 0),
)

internal fun JSONObject.toPeerConversationThread(): PeerConversationThread {
    val convObj = getJSONObject("conversation")
    return PeerConversationThread(
        conversation = PeerConversationMeta(
            id = convObj.getString("id"),
            language = convObj.optString("language", "es"),
            muted = convObj.optBoolean("muted", false),
            partnerId = convObj.optString("partnerId"),
        ),
        partner = optJSONObject("partner")?.toPeerLearner(),
        messages = optJSONArray("messages")?.objects()?.map { it.toPeerMessage() }.orEmpty(),
        activeCall = optJSONObject("activeCall")?.toPeerCallRoom(),
    )
}

internal fun JSONObject.toLivekitTicket() = LivekitTicket(
    token = getString("token"),
    url = getString("url"),
    room = getString("room"),
    identity = optString("identity"),
)

internal fun JSONObject.toStartPeerCallResult() = StartPeerCallResult(
    call = getJSONObject("call").toPeerCallRoom(),
    livekit = getJSONObject("livekit").toLivekitTicket(),
    message = getJSONObject("message").toPeerMessage(),
)

internal fun JSONObject.toJoinPeerCallResult() = JoinPeerCallResult(
    call = getJSONObject("call").toPeerCallRoom(),
    livekit = getJSONObject("livekit").toLivekitTicket(),
)

internal fun JSONObject.toCoachPrompts(): CoachPrompts {
    fun list(key: String) = optJSONArray(key)?.objects()?.map {
        CoachPromptItem(it.optString("phrase"), it.optString("translation"))
    }.orEmpty()
    return CoachPrompts(
        answers = list("answers"),
        followUps = list("followUps"),
        questions = list("questions"),
    )
}

// ── 10 · Account Privacy & Blocked Learners ─────────────────────────────────

data class BlockedLearner(val id: String, val name: String)

data class PrivacySettings(
    val openToSpeak: Boolean = true,
    val allowCallInvites: Boolean = true,
    val whoCanMessage: String = "partners", // "partners" | "everyone" | "nobody"
    val blockedLearners: List<BlockedLearner> = emptyList(),
)

internal fun JSONObject.toPrivacySettings() = PrivacySettings(
    openToSpeak = optBoolean("openToSpeak", true),
    allowCallInvites = optBoolean("allowCallInvites", true),
    whoCanMessage = optString("whoCanMessage", "partners"),
    blockedLearners = optJSONArray("blockedLearners")?.objects()?.map {
        BlockedLearner(it.getString("id"), it.optString("name"))
    }.orEmpty(),
)

// ── 12 · Vocabulary SRS Library ─────────────────────────────────────────────

enum class VocabBucket(val wire: String) {
    Due("due"),
    Learning("learning"),
    Mastered("mastered");

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: Due
    }
}

data class VocabPhrase(
    val id: String,
    val language: String = "es",
    val phrase: String,
    val translation: String,
    val source: String,
    val contextNote: String? = null,
    val exampleSentence: String? = null,
    val exampleTranslation: String? = null,
    val syllableStress: String? = null,
    val recallCue: String? = null,
    val streak: Int = 0,
    val intervalDays: Int = 0,
    val intervalLabel: String = "Due now",
    val bucket: VocabBucket = VocabBucket.Due,
    val dueNow: Boolean = true,
)

data class VocabCounts(
    val due: Int = 0,
    val learning: Int = 0,
    val mastered: Int = 0,
    val total: Int = 0,
)

data class VocabLibrary(
    val phrases: List<VocabPhrase>,
    val counts: VocabCounts,
)

data class VocabPreview(
    val translation: String,
    val exampleSentence: String,
    val exampleTranslation: String,
    val syllableStress: String,
    val recallCue: String,
)

internal fun JSONObject.toVocabPhrase() = VocabPhrase(
    id = getString("id"),
    language = optString("language", "es"),
    phrase = optString("phrase"),
    translation = optString("translation"),
    source = optString("source", "Added manually"),
    contextNote = optStringOrNull("contextNote"),
    exampleSentence = optStringOrNull("exampleSentence"),
    exampleTranslation = optStringOrNull("exampleTranslation"),
    syllableStress = optStringOrNull("syllableStress"),
    recallCue = optStringOrNull("recallCue"),
    streak = optInt("streak", 0),
    intervalDays = optInt("intervalDays", 0),
    intervalLabel = optString("intervalLabel", "Due now"),
    bucket = VocabBucket.fromWire(optString("bucket")),
    dueNow = optBoolean("dueNow", true),
)

internal fun JSONObject.toVocabLibrary(): VocabLibrary {
    val phrases = optJSONArray("phrases")?.objects()?.map { it.toVocabPhrase() }.orEmpty()
    val c = optJSONObject("counts")
    val counts = VocabCounts(
        due = c?.optInt("due", phrases.count { it.dueNow }) ?: phrases.count { it.dueNow },
        learning = c?.optInt("learning", phrases.count { !it.dueNow && it.bucket == VocabBucket.Learning }) ?: 0,
        mastered = c?.optInt("mastered", phrases.count { !it.dueNow && it.bucket == VocabBucket.Mastered }) ?: 0,
        total = c?.optInt("total", phrases.size) ?: phrases.size,
    )
    return VocabLibrary(phrases = phrases, counts = counts)
}

internal fun JSONObject.toVocabPreview() = VocabPreview(
    translation = optString("translation"),
    exampleSentence = optString("exampleSentence"),
    exampleTranslation = optString("exampleTranslation"),
    syllableStress = optString("syllableStress"),
    recallCue = optString("recallCue"),
)

// ── 15 · Live Rooms ─────────────────────────────────────────────────────────

data class LiveRoomMember(
    val id: String,
    val name: String,
    val initial: String,
    val imageUrl: String? = null,
    val isHost: Boolean = false,
    val ready: Boolean = true,
    val isYou: Boolean = false,
)

data class LiveRoom(
    val id: String,
    val title: String,
    val description: String = "",
    val language: String = "es",
    val level: String = "B1",
    val maxMembers: Int = 4,
    val visibility: String = "public",
    val code: String? = null,
    val sourceKind: String = "user", // "system" | "user"
    val status: String = "lobby", // "lobby" | "live" | "ended"
    val hostUserId: String = "",
    val hostName: String = "Zuno",
    val isHost: Boolean = false,
    val discussionPrompts: List<String> = emptyList(),
    val currentRound: Int = 1,
    val members: List<LiveRoomMember> = emptyList(),
) {
    val isSystem: Boolean get() = sourceKind == "system"
    val isPublic: Boolean get() = visibility == "public"
    val isLive: Boolean get() = status == "live"
}

data class LiveRoomsBrowse(
    val featured: LiveRoom?,
    val rooms: List<LiveRoom>,
)

data class JoinLiveRoomResult(
    val room: LiveRoom,
    val livekit: LivekitTicket?,
)

internal fun JSONObject.toLiveRoomMember() = LiveRoomMember(
    id = getString("id"),
    name = optString("name"),
    initial = optString("initial", "Z"),
    imageUrl = optStringOrNull("imageUrl"),
    isHost = optBoolean("isHost", false),
    ready = optBoolean("ready", true),
    isYou = optBoolean("isYou", false),
)

internal fun JSONObject.toLiveRoom() = LiveRoom(
    id = getString("id"),
    title = optString("title"),
    description = optString("description"),
    language = optString("language", "es"),
    level = optString("level", "B1"),
    maxMembers = optInt("maxMembers", 4),
    visibility = optString("visibility", "public"),
    code = optStringOrNull("code"),
    sourceKind = optString("sourceKind", "user"),
    status = optString("status", "lobby"),
    hostUserId = optString("hostUserId"),
    hostName = optString("hostName", "Zuno"),
    isHost = optBoolean("isHost", false),
    discussionPrompts = optJSONArray("discussionPrompts")?.let { arr ->
        List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    }.orEmpty(),
    currentRound = optInt("currentRound", 1),
    members = optJSONArray("members")?.objects()?.map { it.toLiveRoomMember() }.orEmpty(),
)

internal fun JSONObject.toLiveRoomsBrowse() = LiveRoomsBrowse(
    featured = optJSONObject("featured")?.toLiveRoom(),
    rooms = optJSONArray("rooms")?.objects()?.map { it.toLiveRoom() }.orEmpty(),
)

internal fun JSONObject.toJoinLiveRoomResult() = JoinLiveRoomResult(
    room = getJSONObject("room").toLiveRoom(),
    livekit = optJSONObject("livekit")?.toLivekitTicket(),
)
