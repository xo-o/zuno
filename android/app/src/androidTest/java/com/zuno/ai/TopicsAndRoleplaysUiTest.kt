package com.zuno.ai

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zuno.ai.data.BuilderTurnMessage
import com.zuno.ai.data.DiscoverRoleplay
import com.zuno.ai.data.DiscoverRoleplaysResult
import com.zuno.ai.data.DiscoverTopic
import com.zuno.ai.data.DiscoverTopicLesson
import com.zuno.ai.data.DiscoverTopicsResult
import com.zuno.ai.data.LessonState
import com.zuno.ai.data.Roleplay
import com.zuno.ai.data.RoleplayDraft
import com.zuno.ai.data.TopicDetail
import com.zuno.ai.data.TopicDraft
import com.zuno.ai.data.TopicDraftLesson
import com.zuno.ai.data.TopicLesson
import com.zuno.ai.data.TopicStatus
import com.zuno.ai.data.TopicSummary
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoShareSheet
import com.zuno.ai.ui.screens.CommunityRoleplayPreviewScreen
import com.zuno.ai.ui.screens.CommunityTopicPreviewScreen
import com.zuno.ai.ui.screens.ExploreRoleplaysScreen
import com.zuno.ai.ui.screens.ExploreTopicsScreen
import com.zuno.ai.ui.screens.NewRoleplayScreen
import com.zuno.ai.ui.screens.NewTopicScreen
import com.zuno.ai.ui.screens.RoleplaySettingsScreen
import com.zuno.ai.ui.screens.RoleplaysScreen
import com.zuno.ai.ui.screens.TopicScreen
import com.zuno.ai.ui.screens.TopicSettingsScreen
import com.zuno.ai.ui.screens.TopicsScreen
import com.zuno.ai.ui.theme.ZunoTheme
import com.zuno.ai.viewmodel.Loadable
import com.zuno.ai.viewmodel.RoleplayBuilderState
import com.zuno.ai.viewmodel.TopicBuilderState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class TopicsAndRoleplaysUiTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun screenshotDir(): File =
        File(instrumentation.targetContext.filesDir, "topics_roleplays_verify")

    private fun saveScreenshot(name: String) {
        Thread.sleep(450)
        val dir = screenshotDir().apply { mkdirs() }
        val file = File(dir, "$name.png")
        val bmp = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Failed to capture $name")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bmp.recycle()
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) collectTexts(node.getChild(i), out)
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, expected: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == expected || node.contentDescription?.toString() == expected) return node
        for (i in 0 until node.childCount) {
            findNodeByText(node.getChild(i), expected)?.let { return it }
        }
        return null
    }

    private fun visibleTexts(): List<String> {
        val list = mutableListOf<String>()
        collectTexts(instrumentation.uiAutomation.rootInActiveWindow, list)
        return list
    }

    private fun waitForText(expected: String, substring: Boolean = false, timeoutMs: Long = 10_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val texts = visibleTexts()
            val matched = if (substring) texts.any { it.contains(expected) } else texts.any { it == expected }
            if (matched) return
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for '$expected'. Visible: ${visibleTexts()}")
    }

    private fun tapText(label: String) {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: throw AssertionError("No active window")
        val target = findNodeByText(root, label) ?: throw AssertionError("No node found for '$label'. Visible: ${visibleTexts()}")
        val rect = Rect()
        target.getBoundsInScreen(rect)
        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        instrumentation.uiAutomation.injectInputEvent(down, true)
        instrumentation.uiAutomation.injectInputEvent(up, true)
        down.recycle()
        up.recycle()
        Thread.sleep(250)
    }

    @Test
    fun verifyTopicsAndRoleplaysFullFlows() {
        val officialTopic = DiscoverTopic(
            id = "seed-topic-es-cafe",
            title = "Pedir café de especialidad",
            translation = "Ordering specialty coffee",
            language = "es",
            category = "food",
            sourceKind = "system",
            authorHandle = null,
            level = "B1",
            learnersCount = 312,
            totalLessons = 5,
            shareUrl = "https://heyzuno.app/t/seed-topic-es-cafe",
            alreadySaved = false,
            lessons = listOf(
                DiscoverTopicLesson(1, "Saludar y pedir mesa", "Greeting & getting a table", "Greet barista"),
                DiscoverTopicLesson(2, "Elegir café y tipo de leche", "Choosing coffee & milk", "Order milk"),
                DiscoverTopicLesson(3, "Acompañar con algo dulce", "Adding pastries", "Order pastry"),
                DiscoverTopicLesson(4, "Preguntar por ingredientes", "Asking ingredients", "Check allergens"),
                DiscoverTopicLesson(5, "Pedir la cuenta y pagar", "Asking for the bill", "Pay"),
            ),
        )
        val communityTopic = DiscoverTopic(
            id = "seed-topic-es-tapas",
            title = "Café y tapas en Madrid",
            translation = "Ordering coffee & tapas naturally",
            language = "es",
            category = "food",
            sourceKind = "community",
            authorHandle = "@carlos.v",
            level = "B1",
            learnersCount = 142,
            totalLessons = 5,
            shareUrl = "https://heyzuno.app/t/tapas-madrid",
            alreadySaved = false,
            lessons = listOf(
                DiscoverTopicLesson(1, "Pedir un café con leche", "Ordering coffee", "Order coffee"),
                DiscoverTopicLesson(2, "Elegir leche y tamaño", "Milk & size", "Choose milk"),
                DiscoverTopicLesson(3, "Pedir tapas para compartir", "Tapas to share", "Order tapas"),
                DiscoverTopicLesson(4, "Preguntar por ingredientes", "Ingredients", "Ingredients"),
                DiscoverTopicLesson(5, "Pedir la cuenta al camarero", "Settling the bill", "Bill"),
            ),
        )

        val userTopics = listOf(
            TopicSummary(
                id = "t1",
                title = "Encuentra la parada de autobús",
                translation = "Find the bus stop",
                totalLessons = 5,
                completedLessons = 2,
                estimatedMinutesLeft = 15,
                progress = 40,
                status = TopicStatus.InProgress,
                visibility = "private",
                sourceKind = "user",
            ),
            TopicSummary(
                id = "t2",
                title = "Job interview basics",
                translation = "Conceptos básicos de entrevista",
                totalLessons = 5,
                completedLessons = 3,
                estimatedMinutesLeft = 10,
                progress = 60,
                status = TopicStatus.InProgress,
                visibility = "public",
                sourceKind = "user",
                shareUrl = "https://heyzuno.app/t/job-interview",
                learnersCount = 34,
            ),
            TopicSummary(
                id = "t3",
                title = "Saludos y presentaciones",
                translation = "Greetings & introductions",
                totalLessons = 5,
                completedLessons = 5,
                estimatedMinutesLeft = 0,
                progress = 100,
                status = TopicStatus.Completed,
                visibility = "private",
                sourceKind = "community",
                sourceTopicId = "seed-topic-es-tapas",
                authorHandle = "@carlos.v",
            ),
        )

        val sampleTopicDraft = TopicDraft(
            title = "Madrid Trip Essentials",
            translation = "Esenciales para viajar a Madrid",
            category = "travel",
            prompt = "Madrid trip next week — work, cafés & hotel check-in",
            lessons = listOf(
                TopicDraftLesson("Metro desde el aeropuerto", "Metro from the airport", "Take metro"),
                TopicDraftLesson("Pedir café y desayuno", "Café & breakfast orders", "Order coffee"),
                TopicDraftLesson("Check-in en el hotel", "Hotel check-in", "Check into hotel"),
                TopicDraftLesson("Moverse por el centro", "Getting around downtown", "Directions"),
                TopicDraftLesson("Cena de equipo", "Team dinner", "Order dinner"),
            ),
        )

        val createdTopicDetail = TopicDetail(
            summary = TopicSummary(
                id = "t-madrid",
                title = "Madrid Trip Essentials",
                translation = "Esenciales para viajar a Madrid",
                totalLessons = 5,
                completedLessons = 0,
                estimatedMinutesLeft = 25,
                progress = 0,
                status = TopicStatus.NotStarted,
                category = "travel",
                visibility = "public",
                sourceKind = "user",
                shareUrl = "https://heyzuno.app/t/madrid-trip",
                learnersCount = 0,
            ),
            lessons = sampleTopicDraft.lessons.mapIndexed { idx, l ->
                TopicLesson(
                    id = "l-${idx + 1}",
                    position = idx + 1,
                    title = l.title,
                    translation = l.translation,
                    objective = l.objective,
                    state = if (idx == 0) LessonState.Current else LessonState.Upcoming,
                )
            },
        )

        val officialRoleplay = DiscoverRoleplay(
            id = "seed-rp-es-cafe",
            title = "Ordering at a café",
            scenario = "Order coffee and breakfast at a busy café in Madrid",
            userRole = "Customer",
            aiRole = "Barista",
            setting = "You step up to the counter at a specialty café in Malasaña.",
            language = "es",
            category = "food",
            sourceKind = "system",
            authorHandle = null,
            level = "B1",
            practicedCount = 418,
            shareUrl = "https://heyzuno.app/r/seed-rp-es-cafe",
            alreadySaved = false,
        )
        val communityRoleplay = DiscoverRoleplay(
            id = "seed-rp-es-luggage",
            title = "Lost luggage at Barajas",
            scenario = "Report a delayed suitcase and arrange hotel delivery",
            userRole = "Traveler",
            aiRole = "Airline Agent",
            setting = "Your suitcase didn’t arrive on your flight to Madrid. Describe your bag, file a claim, and arrange hotel delivery.",
            language = "es",
            category = "travel",
            sourceKind = "community",
            authorHandle = "@mateo.r",
            level = "B1",
            practicedCount = 214,
            shareUrl = "https://heyzuno.app/r/lost-luggage",
            alreadySaved = false,
        )

        val userRoleplays = listOf(
            Roleplay(
                id = "rp1",
                title = "Renting an apartment",
                scenario = "View a flat in Madrid",
                userRole = "Tenant",
                aiRole = "Landlord",
                setting = "Touring a flat in Chamberí.",
                category = "housing",
                visibility = "public",
                sourceKind = "user",
                shareUrl = "https://heyzuno.app/r/renting-madrid",
                practicedCount = 42,
            ),
            Roleplay(
                id = "rp2",
                title = "Doctor’s visit",
                scenario = "Explain cold symptoms",
                userRole = "Patient",
                aiRole = "Doctor",
                setting = "At a local clinic.",
                category = "health",
                visibility = "private",
                sourceKind = "user",
            ),
        )

        val sampleRoleplayDraft = RoleplayDraft(
            title = "Startup Product Interview",
            scenario = "Friendly product interview in Madrid",
            userRole = "Candidate",
            aiRole = "Hiring Lead",
            setting = "First-round product interview at a Madrid startup.",
            category = "work",
        )

        val createdRoleplay = Roleplay(
            id = "rp-startup",
            title = "Startup Product Interview",
            scenario = "Friendly product interview in Madrid",
            userRole = "Candidate",
            aiRole = "Hiring Lead",
            setting = "First-round product interview at a Madrid startup.",
            category = "work",
            visibility = "public",
            sourceKind = "user",
            shareUrl = "https://heyzuno.app/r/startup-interview",
            practicedCount = 0,
        )

        var step by mutableStateOf("01_topics_empty")

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                )
                activity.setContent {
                    ZunoTheme {
                        when (step) {
                            "01_topics_empty" -> TopicsScreen(
                                topics = emptyList(),
                                loading = false,
                                error = null,
                                discoverPreview = listOf(officialTopic, communityTopic),
                                menuForTopic = { emptyList() },
                                onRetry = {},
                                onOpen = {},
                                onCreate = {},
                                onExplore = {},
                                onOpenPreview = {},
                                onNavTabSelected = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "02_topics_list" -> TopicsScreen(
                                topics = userTopics,
                                loading = false,
                                error = null,
                                discoverPreview = listOf(officialTopic, communityTopic),
                                menuForTopic = { summary ->
                                    buildList {
                                        add(ZunoMenuAction("Start next lesson", icon = R.drawable.zuno_ic_t_arrow_right) {})
                                        add(ZunoMenuAction("Schedule practice", icon = R.drawable.zuno_ic_t_clock) {})
                                        add(ZunoMenuAction("Pin", icon = R.drawable.zuno_ic_t_pin) {})
                                        if (summary.isPublic || !summary.isUserOwned) {
                                            add(ZunoMenuAction("Share", icon = R.drawable.zuno_ic_t_share) {})
                                        }
                                        if (summary.isUserOwned) {
                                            add(ZunoMenuAction("Topic settings", icon = R.drawable.zuno_ic_t_settings) {})
                                        } else {
                                            add(ZunoMenuAction("Report", icon = R.drawable.zuno_ic_t_flag) {})
                                        }
                                        add(
                                            ZunoMenuAction(
                                                if (summary.isUserOwned) "Delete" else "Remove",
                                                icon = R.drawable.zuno_ic_t_trash,
                                                destructive = true,
                                                dividerBefore = true,
                                            ) {},
                                        )
                                    }
                                },
                                onRetry = {},
                                onOpen = {},
                                onCreate = {},
                                onExplore = {},
                                onOpenPreview = {},
                                onNavTabSelected = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "03_topics_explore" -> ExploreTopicsScreen(
                                discover = Loadable(DiscoverTopicsResult(listOf(officialTopic), listOf(communityTopic))),
                                initialQuery = "café",
                                languageName = "Spanish",
                                onBack = {},
                                onFilterChanged = { _, _ -> },
                                onOpenPreview = {},
                                onCreateFromQuery = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "04_topics_explore_empty" -> ExploreTopicsScreen(
                                discover = Loadable(DiscoverTopicsResult(emptyList(), emptyList())),
                                initialQuery = "astrophysics",
                                languageName = "Spanish",
                                onBack = {},
                                onFilterChanged = { _, _ -> },
                                onOpenPreview = {},
                                onCreateFromQuery = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "05_topic_preview" -> CommunityTopicPreviewScreen(
                                preview = Loadable(communityTopic),
                                busy = false,
                                onBack = {},
                                onRetry = {},
                                onAddOrOpen = {},
                                onShare = {},
                                onReport = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "06_topic_builder_greeting" -> NewTopicScreen(
                                builder = TopicBuilderState(),
                                firstName = "Noah",
                                busy = false,
                                attachments = emptyList(),
                                onBack = {},
                                onSendTurn = {},
                                onAcceptDraft = {},
                                onAttach = {},
                                onRemoveAttachment = {},
                                onRetryAttachment = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "07_topic_builder_draft" -> NewTopicScreen(
                                builder = TopicBuilderState(
                                    turns = listOf(
                                        BuilderTurnMessage("1", isUser = true, text = "Madrid trip next week — work & cafés."),
                                        BuilderTurnMessage("2", isUser = false, text = "Nice! Focus on metro & cafés, or got an itinerary?"),
                                        BuilderTurnMessage(
                                            "3",
                                            isUser = true,
                                            text = "Here’s my PDF — add hotel check-in too.",
                                            attachmentNames = listOf("Madrid_Itinerary.pdf"),
                                        ),
                                        BuilderTurnMessage(
                                            "4",
                                            isUser = false,
                                            text = "Added check-in from your PDF! Ready?",
                                            contextNote = "Read Madrid_Itinerary.pdf · 4 themes found",
                                        ),
                                    ),
                                    draft = sampleTopicDraft,
                                ),
                                firstName = "Noah",
                                busy = false,
                                attachments = emptyList(),
                                onBack = {},
                                onSendTurn = {},
                                onAcceptDraft = {},
                                onAttach = {},
                                onRemoveAttachment = {},
                                onRetryAttachment = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "08_topic_settings" -> TopicSettingsScreen(
                                topic = createdTopicDetail,
                                languageName = "Spanish",
                                level = "B1",
                                busy = false,
                                onBack = {},
                                onSelectPublic = {},
                                onShare = {},
                                onCopyLink = {},
                                onPrimaryAction = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "09_topic_in_progress" -> TopicScreen(
                                topic = createdTopicDetail,
                                busy = false,
                                menu = listOf(
                                    ZunoMenuAction("Start next lesson", icon = R.drawable.zuno_ic_t_arrow_right) {},
                                    ZunoMenuAction("Schedule practice", icon = R.drawable.zuno_ic_t_clock) {},
                                    ZunoMenuAction("Pin", icon = R.drawable.zuno_ic_t_pin) {},
                                    ZunoMenuAction("Share", icon = R.drawable.zuno_ic_t_share) {},
                                    ZunoMenuAction("Topic settings", icon = R.drawable.zuno_ic_t_settings) {},
                                    ZunoMenuAction("Delete", icon = R.drawable.zuno_ic_t_trash, destructive = true, dividerBefore = true) {},
                                ),
                                onBack = {},
                                onRetry = {},
                                onStartLesson = {},
                                onOpenEvaluation = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "10_roleplays_empty" -> RoleplaysScreen(
                                roleplays = emptyList(),
                                loading = false,
                                error = null,
                                discoverPreview = listOf(officialRoleplay, communityRoleplay),
                                menuForRoleplay = { emptyList() },
                                onRetry = {},
                                onOpen = {},
                                onCreate = {},
                                onExplore = {},
                                onOpenPreview = {},
                                onNavTabSelected = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "11_roleplays_list" -> RoleplaysScreen(
                                roleplays = userRoleplays,
                                loading = false,
                                error = null,
                                discoverPreview = listOf(officialRoleplay, communityRoleplay),
                                menuForRoleplay = { rp ->
                                    buildList {
                                        add(ZunoMenuAction("Start over", icon = R.drawable.zuno_ic_t_rotate_ccw) {})
                                        add(ZunoMenuAction("Schedule practice", icon = R.drawable.zuno_ic_t_clock) {})
                                        add(ZunoMenuAction("Pin", icon = R.drawable.zuno_ic_t_pin) {})
                                        if (rp.isPublic) {
                                            add(ZunoMenuAction("Share", icon = R.drawable.zuno_ic_t_share) {})
                                        }
                                        add(ZunoMenuAction("Roleplay settings", icon = R.drawable.zuno_ic_t_settings) {})
                                        add(ZunoMenuAction("Delete", icon = R.drawable.zuno_ic_t_trash, destructive = true, dividerBefore = true) {})
                                    }
                                },
                                onRetry = {},
                                onOpen = {},
                                onCreate = {},
                                onExplore = {},
                                onOpenPreview = {},
                                onNavTabSelected = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "12_roleplays_explore" -> ExploreRoleplaysScreen(
                                discover = Loadable(DiscoverRoleplaysResult(listOf(officialRoleplay), listOf(communityRoleplay))),
                                initialQuery = "airport",
                                onBack = {},
                                onFilterChanged = { _, _ -> },
                                onOpenPreview = {},
                                onCreateFromQuery = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "13_roleplays_explore_empty" -> ExploreRoleplaysScreen(
                                discover = Loadable(DiscoverRoleplaysResult(emptyList(), emptyList())),
                                initialQuery = "space station",
                                onBack = {},
                                onFilterChanged = { _, _ -> },
                                onOpenPreview = {},
                                onCreateFromQuery = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "14_roleplay_preview" -> CommunityRoleplayPreviewScreen(
                                preview = Loadable(communityRoleplay),
                                languageName = "Spanish",
                                busy = false,
                                onBack = {},
                                onRetry = {},
                                onStartLive = {},
                                onSave = {},
                                onShare = {},
                                onReport = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "15_roleplay_builder_greeting" -> NewRoleplayScreen(
                                builder = RoleplayBuilderState(),
                                firstName = "Noah",
                                busy = false,
                                attachments = emptyList(),
                                onBack = {},
                                onSendTurn = {},
                                onAcceptDraft = {},
                                onAttach = {},
                                onRemoveAttachment = {},
                                onRetryAttachment = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "16_roleplay_builder_draft" -> NewRoleplayScreen(
                                builder = RoleplayBuilderState(
                                    turns = listOf(
                                        BuilderTurnMessage("1", isUser = true, text = "Friendly product interview in Madrid."),
                                        BuilderTurnMessage("2", isUser = false, text = "Who’s interviewing you, or got a job post?"),
                                        BuilderTurnMessage(
                                            "3",
                                            isUser = true,
                                            text = "Here’s the JD — focus on past projects.",
                                            attachmentNames = listOf("Startup_JD_Madrid.pdf"),
                                        ),
                                        BuilderTurnMessage(
                                            "4",
                                            isUser = false,
                                            text = "Set Sofía as Hiring Lead from your PDF!",
                                            contextNote = "Read Startup_JD_Madrid.pdf · 3 key skills extracted",
                                        ),
                                    ),
                                    draft = sampleRoleplayDraft,
                                ),
                                firstName = "Noah",
                                busy = false,
                                attachments = emptyList(),
                                onBack = {},
                                onSendTurn = {},
                                onAcceptDraft = {},
                                onAttach = {},
                                onRemoveAttachment = {},
                                onRetryAttachment = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "17_roleplay_settings" -> RoleplaySettingsScreen(
                                roleplay = createdRoleplay,
                                languageName = "Spanish",
                                level = "B1",
                                busy = false,
                                onBack = {},
                                onSelectPublic = {},
                                onShare = {},
                                onCopyLink = {},
                                onStartLive = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            "18_share_sheet" -> {
                                ZunoShareSheet(
                                    title = "Share topic",
                                    onlyMeDescription = "Private in your library",
                                    publicTitle = "Public to community",
                                    publicDescription = "Searchable by learners · Anyone with link",
                                    linkSubtitle = "heyzuno.app/t/job-interview · 34 learners",
                                    isPublic = true,
                                    shareUrl = "https://heyzuno.app/t/job-interview",
                                    busy = false,
                                    onSelectPublic = {},
                                    onShare = {},
                                    onCopyLink = {},
                                    onDismiss = {},
                                )
                            }
                        }
                    }
                }
            }

            // 1. 06 · Topics — Empty
            waitForText("No topics yet")
            waitForText("Create topic")
            waitForText("Explore")
            waitForText("Pedir café de especialidad")
            // Assert NO ownership filter clutter on root Topics
            assertFalse(visibleTexts().contains("Mine"))
            saveScreenshot("01_topics_empty")

            // 2. 01 · Topics — List + 07 · Add Topic Sheet
            step = "02_topics_list"
            waitForText("Encuentra la parada de autobús")
            waitForText("3 of 5 lessons · Public")
            saveScreenshot("02_topics_list")

            // Tap "+" in Top Bar (contentDescription = "Add topic") to open 07 · Add Choice Sheet
            tapText("Add topic")
            waitForText("Create a new topic")
            waitForText("Find community topics")
            saveScreenshot("03_topics_add_sheet")

            // 3. 08 · Explore Topics
            step = "03_topics_explore"
            waitForText("Explore topics")
            waitForText("ZUNO OFFICIAL")
            waitForText("COMMUNITY TOPICS")
            waitForText("Café y tapas en Madrid")
            saveScreenshot("04_topics_explore")

            // 4. 12 · Explore Topics Empty
            step = "04_topics_explore_empty"
            waitForText("No topics for “astrophysics”")
            waitForText("Create “astrophysics”")
            saveScreenshot("05_topics_explore_empty")

            // 5. 09 · Community Topic Preview
            step = "05_topic_preview"
            waitForText("Community topic")
            waitForText("Add to my Topics")
            waitForText("Syllabus · 5 lessons")
            saveScreenshot("06_topic_preview")

            // 6. 10a · Topic Builder Greeting
            step = "06_topic_builder_greeting"
            waitForText("New topic")
            waitForText("Hey Noah!", substring = true)
            waitForText("Madrid trip")
            saveScreenshot("07_topic_builder_greeting")

            // 7. 10b · Topic Builder Draft Ready (with mid-chat PDF)
            step = "07_topic_builder_draft"
            waitForText("DRAFT TOPIC · 5 LESSONS")
            waitForText("Madrid Trip Essentials")
            waitForText("Refine")
            waitForText("Create topic")
            saveScreenshot("08_topic_builder_draft")

            // 8. 10c · Topic Settings
            step = "08_topic_settings"
            waitForText("Topic settings")
            waitForText("TOPIC CREATED · 5 LESSONS")
            waitForText("Public to community")
            waitForText("Start Lesson 1")
            saveScreenshot("09_topic_settings")

            // 9. 02 · Topic — In progress (with Public badge + updated overflow menu)
            step = "09_topic_in_progress"
            waitForText("0 of 5 lessons · 25 min left · Public")
            saveScreenshot("10_topic_detail_public")

            // 10. Roleplays · 1 · Empty
            step = "10_roleplays_empty"
            waitForText("No roleplays yet")
            waitForText("New roleplay")
            waitForText("Explore")
            waitForText("Ordering at a café")
            saveScreenshot("11_roleplays_empty")

            // 11. Roleplays · 2 · List + 4 · Add Roleplay Sheet
            step = "11_roleplays_list"
            waitForText("Renting an apartment")
            waitForText("Tenant / Landlord · Public")
            saveScreenshot("12_roleplays_list")

            tapText("Add roleplay")
            waitForText("Create a new roleplay")
            waitForText("Find community roleplays")
            saveScreenshot("13_roleplays_add_sheet")

            // 12. Roleplays · 5 · Explore Roleplays
            step = "12_roleplays_explore"
            waitForText("Explore roleplays")
            waitForText("COMMUNITY SCENARIOS")
            waitForText("Housing")
            saveScreenshot("14_roleplays_explore")

            // 13. Roleplays · 9 · Explore Roleplays Empty
            step = "13_roleplays_explore_empty"
            waitForText("No scenarios for “space station”")
            waitForText("Create “space station”")
            saveScreenshot("15_roleplays_explore_empty")

            // 14. Roleplays · 6 · Community Preview
            step = "14_roleplay_preview"
            waitForText("Community roleplay")
            waitForText("Lost luggage at Barajas")
            waitForText("Start live roleplay")
            waitForText("Save")
            waitForText("Share")
            saveScreenshot("16_roleplay_preview")

            // 15. Roleplays · 3a · Chat Builder Greeting
            step = "15_roleplay_builder_greeting"
            waitForText("New roleplay")
            waitForText("Startup interview")
            saveScreenshot("17_roleplay_builder_greeting")

            // 16. Roleplays · 3b · Chat Builder Draft Ready
            step = "16_roleplay_builder_draft"
            waitForText("DRAFT ROLEPLAY")
            waitForText("Startup Product Interview")
            waitForText("You: Candidate · Sofía: Hiring Lead")
            waitForText("Create roleplay")
            saveScreenshot("18_roleplay_builder_draft")

            // 17. Roleplays · 3c · Roleplay Settings
            step = "17_roleplay_settings"
            waitForText("Roleplay settings")
            waitForText("ROLEPLAY READY")
            waitForText("Start live roleplay")
            saveScreenshot("19_roleplay_settings")

            // 18. 11 · Share & Visibility Sheet
            step = "18_share_sheet"
            waitForText("Share topic")
            waitForText("Public to community")
            waitForText("Copy link")
            saveScreenshot("20_share_sheet")
        }

        val captured = screenshotDir().listFiles()?.map { it.name }.orEmpty()
        assertTrue("Expected 20 verification screenshots, got: $captured", captured.size >= 20)
    }
}
