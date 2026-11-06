package com.talkie.ai.ui.zuno

import com.talkie.ai.ui.zuno.components.LessonStatus

sealed interface ChatTurn {
    data class User(
        val message: String,
        val feedback: String? = null,
        val showSender: Boolean = true,
    ) : ChatTurn

    data class Assistant(
        val message: String,
        val translation: String? = null,
        val glossary: List<String> = emptyList(),
    ) : ChatTurn
}

enum class SessionKind { Topic, Roleplay, FreeTalk }

data class SessionSummary(val title: String, val kind: SessionKind, val meta: String)

data class Lesson(val number: Int, val title: String, val status: LessonStatus, val meta: String? = null)

data class Topic(
    val title: String,
    val translation: String,
    val lessons: List<Lesson>,
) {
    val completed: Int get() = lessons.count { it.status == LessonStatus.Done }
    val progress: Float get() = if (lessons.isEmpty()) 0f else completed / lessons.size.toFloat()
    val currentLesson: Lesson? get() = lessons.firstOrNull { it.status == LessonStatus.Current }
}

/** Placeholder content mirroring the Figma screens until real data is wired. */
object ZunoSampleData {
    const val userName = "Noah Carter"
    const val userFirstName = "Noah"
    const val learningDetail = "Spanish · B1"

    val busStopTopic = Topic(
        title = "Encuentra la parada de autobús",
        translation = "Find the bus stop",
        lessons = listOf(
            Lesson(1, "Vocabulario básico", LessonStatus.Done, meta = "Done"),
            Lesson(2, "Pedir ayuda a locales", LessonStatus.Current, meta = "Up next"),
            Lesson(3, "Elige el autobús adecuado", LessonStatus.Upcoming),
            Lesson(4, "Nombra tu destino exacto", LessonStatus.Upcoming),
            Lesson(5, "Saber dónde bajarse", LessonStatus.Upcoming),
        ),
    )

    val recentSessions = listOf(
        SessionSummary(busStopTopic.title, SessionKind.Topic, "Topic · 20%"),
        SessionSummary("Ordering at a café", SessionKind.Roleplay, "Roleplay"),
        SessionSummary("Juego de Pollito Saltarín", SessionKind.FreeTalk, "Free Talk"),
        SessionSummary("Job interview basics", SessionKind.Topic, "Topic · 60%"),
        SessionSummary("Airport check-in", SessionKind.Roleplay, "Roleplay"),
        SessionSummary("Weekend plans", SessionKind.FreeTalk, "Free Talk"),
    )

    val cafeConversation: List<ChatTurn> = listOf(
        ChatTurn.User(
            message = "¡Hola! Quiero pedir un café con leche y un croissant.",
            feedback = "Natural Spanish · Native tip: “¿Me pones un café…?”",
        ),
        ChatTurn.Assistant(
            message = "¡Claro que sí! ¿Lo quieres para tomar aquí o para llevar? Tenemos también recién horneados.",
            translation = "Of course! Would you like it for here or to go? We also have fresh croissants.",
            glossary = listOf("para llevar — to go", "recién horneado — freshly baked"),
        ),
        ChatTurn.User(
            message = "Para llevar, por favor. ¿Cuánto cuesta en total?",
            feedback = "96% pronunciation · Great rhythm",
            showSender = false,
        ),
    )
}
