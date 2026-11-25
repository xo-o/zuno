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
}
