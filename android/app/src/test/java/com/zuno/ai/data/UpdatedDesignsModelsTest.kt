package com.zuno.ai.data

import com.zuno.ai.ui.components.parseSyllables
import com.zuno.ai.ui.screens.HomeCopy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatedDesignsModelsTest {

    @Test
    fun parseVocabLibrary_readsPhrasesAndCounts() {
        val json = JSONObject(
            """
            {
              "phrases": [
                {
                  "id": "vp-1",
                  "language": "es",
                  "phrase": "pedir la cuenta",
                  "translation": "to ask for the bill",
                  "source": "Café roleplay",
                  "exampleSentence": "¿Nos podría traer la cuenta?",
                  "exampleTranslation": "Could you bring us the bill?",
                  "syllableStress": "pe·DIR la CUEN·ta",
                  "recallCue": "Asking to pay at a restaurant",
                  "streak": 2,
                  "intervalDays": 3,
                  "intervalLabel": "Due now",
                  "bucket": "due",
                  "dueNow": true
                }
              ],
              "counts": { "due": 1, "learning": 0, "mastered": 0, "total": 1 }
            }
            """.trimIndent(),
        )
        val lib = json.toVocabLibrary()
        assertEquals(1, lib.phrases.size)
        assertEquals(1, lib.counts.due)
        assertEquals(VocabBucket.Due, lib.phrases.first().bucket)
        assertEquals("pe·DIR la CUEN·ta", lib.phrases.first().syllableStress)
        assertTrue(lib.phrases.first().dueNow)
    }

    @Test
    fun parseLiveRoomsBrowse_readsFeaturedAndMembers() {
        val json = JSONObject(
            """
            {
              "featured": {
                "id": "sys-featured-es",
                "title": "Café culture & morning routines",
                "description": "System-guided topic room.",
                "language": "es",
                "level": "B1",
                "maxMembers": 4,
                "visibility": "public",
                "sourceKind": "system",
                "status": "live",
                "hostUserId": "seed:lucia",
                "hostName": "Lucía M.",
                "isHost": false,
                "discussionPrompts": ["Your go-to order", "Favorite cafés"],
                "currentRound": 2,
                "members": [
                  { "id": "seed:lucia", "name": "Lucía M.", "initial": "L", "isHost": true, "ready": true, "isYou": false },
                  { "id": "u-1", "name": "Noah", "initial": "N", "isHost": false, "ready": true, "isYou": true }
                ]
              },
              "rooms": []
            }
            """.trimIndent(),
        )
        val browse = json.toLiveRoomsBrowse()
        assertNotNull(browse.featured)
        assertEquals("Café culture & morning routines", browse.featured?.title)
        assertTrue(browse.featured?.isSystem == true)
        assertTrue(browse.featured?.isLive == true)
        assertEquals(2, browse.featured?.currentRound)
        assertEquals(2, browse.featured?.members?.size)
        assertTrue(browse.featured?.members?.last()?.isYou == true)
    }

    @Test
    fun parsePrivacySettings_readsFlagsAndBlockedLearners() {
        val json = JSONObject(
            """
            {
              "openToSpeak": false,
              "allowCallInvites": true,
              "whoCanMessage": "everyone",
              "blockedLearners": [
                { "id": "u-9", "name": "Alex P." }
              ]
            }
            """.trimIndent(),
        )
        val privacy = json.toPrivacySettings()
        assertFalse(privacy.openToSpeak)
        assertTrue(privacy.allowCallInvites)
        assertEquals("everyone", privacy.whoCanMessage)
        assertEquals(1, privacy.blockedLearners.size)
        assertEquals("Alex P.", privacy.blockedLearners.first().name)
    }

    @Test
    fun homeCopy_usesFullLanguageGreeting() {
        val copy = HomeCopy.forLanguage(LearningLanguages.Spanish, "Noah", "latam")
        assertEquals("Hola Noah, ¿qué practicamos hoy?", copy.fullGreeting)
        assertEquals("Hola, Noah", copy.shortGreeting)
        assertEquals("es-419", copy.speechTag)
    }

    @Test
    fun parseSyllables_detectsStressedUppercaseSyllable() {
        val parsed = parseSyllables("au·to·BÚS")
        assertEquals(3, parsed.size)
        assertEquals("au" to false, parsed[0])
        assertEquals("to" to false, parsed[1])
        assertEquals("BÚS" to true, parsed[2])
    }

    @Test
    fun parseSyllables_singleUppercaseWordIsNotHighlightedAsMultiStress() {
        val parsed = parseSyllables("MEET")
        assertEquals(1, parsed.size)
        assertEquals("MEET" to false, parsed[0])
    }

    @Test
    fun parseProfile_readsRealUserStatsAndNullableLeagueRank() {
        val json = JSONObject(
            """
            {
              "user": { "id": "u-1", "name": "Dany", "email": "dany@example.com", "image": null },
              "profile": { "targetLanguage": "es", "nativeLanguage": "en", "level": "B1", "onboardingCompleted": true },
              "languages": [],
              "providers": [],
              "stats": { "streakDays": 4, "dailyMinutesDone": 12, "activeDaysTotal": 9, "weeklyXp": 340, "leagueRank": 2 }
            }
            """.trimIndent(),
        )
        val p = json.toProfile()
        assertEquals(4, p.stats.streakDays)
        assertEquals(12, p.stats.dailyMinutesDone)
        assertEquals(9, p.stats.activeDaysTotal)
        assertEquals(340, p.stats.weeklyXp)
        assertEquals(2, p.stats.leagueRank)
    }

    @Test
    fun parseProgressOverview_readsRealMetricsAndHeatmap() {
        val json = JSONObject(
            """
            {
              "language": "es",
              "level": "B1",
              "cefrProgressPercent": 32,
              "streakDays": 3,
              "totalSpokenMinutes": 45,
              "wordsCount": 14,
              "hoursUntilReset": 5,
              "dailyGoals": [
                { "id": "speak", "title": "Speak for 15 minutes", "current": 6, "target": 15, "unit": "m" }
              ],
              "skills": { "hasData": false, "pronunciation": 0, "listening": 0, "vocabulary": 0, "fluency": 0, "grammar": 0, "wordStress": 0, "focusArea": null, "focusScore": null },
              "weeklyXp": 210,
              "avgMinutesPerDay": 9,
              "weeklyBars": [{ "dayLabel": "M", "date": "2026-11-23", "minutes": 10, "xp": 100, "isToday": false }],
              "activeDaysLast12Weeks": 6,
              "heatmapMonths": ["Sep", "Oct", "Nov"],
              "heatmapDays": [{ "date": "2026-11-25", "level": 2, "minutes": 12 }]
            }
            """.trimIndent(),
        )
        val prog = json.toProgressOverview()
        assertEquals(3, prog.streakDays)
        assertEquals(45, prog.totalSpokenMinutes)
        assertEquals(14, prog.wordsCount)
        assertFalse(prog.skills.hasData)
        assertEquals(1, prog.dailyGoals.size)
        assertEquals("6 / 15m", prog.dailyGoals.first().valueLabel)
        assertEquals(6, prog.activeDaysLast12Weeks)
    }

    @Test
    fun parseLeagueOverview_handlesEmptyAndRankedStandings() {
        val json = JSONObject(
            """
            {
              "language": "es",
              "weekStartDate": "2026-11-23",
              "resetsAt": "2026-11-30T00:00:00.000Z",
              "myRank": 1,
              "myXp": 420,
              "myMinutesSpoken": 35,
              "totalParticipants": 2,
              "standings": [
                { "rank": 1, "userId": "u-1", "name": "Dany B.", "initial": "D", "imageUrl": null, "minutesSpoken": 35, "xp": 420, "isYou": true },
                { "rank": 2, "userId": "u-2", "name": "Ana M.", "initial": "A", "imageUrl": null, "minutesSpoken": 20, "xp": 210, "isYou": false }
              ]
            }
            """.trimIndent(),
        )
        val league = json.toLeagueOverview()
        assertEquals(1, league.myRank)
        assertEquals(2, league.standings.size)
        assertTrue(league.standings.first().isYou)
        assertNotNull(league.resetsAtMillis)
    }

    @Test
    fun parseLiveRoomsBrowse_zeroMemberSystemRoomCountsAsZeroActive() {
        val json = JSONObject(
            """
            {
              "featured": {
                "id": "sys-featured-es",
                "title": "Café culture & morning routines",
                "language": "es",
                "level": "B1",
                "maxMembers": 4,
                "visibility": "public",
                "sourceKind": "system",
                "status": "live",
                "hostUserId": "",
                "hostName": "Zuno",
                "isHost": false,
                "discussionPrompts": ["Order coffee"],
                "starterPhrases": ["Siempre pido…"],
                "currentRound": 1,
                "members": []
              },
              "rooms": [],
              "activeRoomsCount": 0
            }
            """.trimIndent(),
        )
        val browse = json.toLiveRoomsBrowse()
        assertEquals(0, browse.activeRoomsCount)
        assertEquals(0, browse.featured?.members?.size)
        assertFalse(browse.featured?.hasActiveParticipants == true)
        assertEquals(listOf("Siempre pido…"), browse.featured?.starterPhrases)
    }

    @Test
    fun parsePeerLearner_defaultsStreakAndSessionsToZero() {
        val json = JSONObject("""{ "id": "u-5", "name": "New Learner" }""")
        val peer = json.toPeerLearner()
        assertEquals(0, peer.streakDays)
        assertEquals(0, peer.sessionsCount)
        assertFalse(peer.online)
    }
}
