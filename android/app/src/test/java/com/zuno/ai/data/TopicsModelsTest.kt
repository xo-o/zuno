package com.zuno.ai.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicsModelsTest {

    @Test
    fun parseLessonStage_readsActiveRecallBlanksAndRoleplayGoals() {
        val recallJson = JSONObject(
            """
            {
              "type": "lesson_stage",
              "stage": "active_recall",
              "pill": {
                "icon": "rotate-ccw",
                "role": "Recall",
                "goal": "Speak from memory",
                "contextNote": "Target hidden"
              },
              "card": null,
              "recallPrompt": {
                "eyebrow": "Say in Spanish",
                "cue": "The doors open. Ask the driver which bus goes downtown.",
                "blankLengths": [5, 4, 6]
              },
              "rescueChips": ["first_word", "reveal", "slow"]
            }
            """.trimIndent(),
        )
        val recallStage = recallJson.toLessonStageUi()
        assertEquals(LessonStageKind.ActiveRecall, recallStage.stage)
        assertEquals("Target hidden", recallStage.pill.contextNote)
        assertEquals(listOf(5, 4, 6), recallStage.recallPrompt?.blankLengths)

        val roleplayJson = JSONObject(
            """
            {
              "type": "lesson_stage",
              "stage": "roleplay",
              "pill": {
                "icon": "users",
                "role": "Bus driver",
                "goal": "Goal 2 of 2 · Ask stops"
              },
              "card": null,
              "recallPrompt": null,
              "roleplayGoals": [
                {
                  "id": "goal_1",
                  "index": 1,
                  "label": "Ask route",
                  "description": "Ask if this bus goes downtown",
                  "samplePhrase": "¿Disculpe, este autobús va al centro?",
                  "completed": true,
                  "current": false
                },
                {
                  "id": "goal_2",
                  "index": 2,
                  "label": "Ask stops",
                  "description": "Ask how many stops to downtown",
                  "samplePhrase": "¿Cuántas paradas son hasta el centro?",
                  "completed": false,
                  "current": true
                }
              ],
              "rescueChips": ["hint", "translate", "slow"]
            }
            """.trimIndent(),
        )
        val rpStage = roleplayJson.toLessonStageUi()
        assertEquals(LessonStageKind.Roleplay, rpStage.stage)
        assertEquals(2, rpStage.roleplayGoals.size)
        assertTrue(rpStage.roleplayGoals[0].completed)
        assertFalse(rpStage.roleplayGoals[0].current)
        assertFalse(rpStage.roleplayGoals[1].completed)
        assertTrue(rpStage.roleplayGoals[1].current)
        assertEquals("¿Cuántas paradas son hasta el centro?", rpStage.roleplayGoals[1].samplePhrase)
    }

    @Test
    fun parseLessonTurn_handlesOptionalTranslationAndActions() {
        val turnJson = JSONObject(
            """
            {
              "id": "agent-1",
              "speaker": "agent",
              "senderLabel": "Sofía · Speaking",
              "text": "Disculpe, ¿este autobús va al centro?",
              "translation": "Excuse me, does this bus go downtown?",
              "actions": ["listen_normal", "listen_slow"]
            }
            """.trimIndent(),
        )
        val turn = turnJson.toLessonTurnUi()
        assertFalse(turn.isUser)
        assertEquals("Sofía · Speaking", turn.senderLabel)
        assertEquals("Excuse me, does this bus go downtown?", turn.translation)
        assertEquals(listOf("listen_normal", "listen_slow"), turn.actions)

        val userTurn = JSONObject(
            """
            {
              "id": "user-1",
              "speaker": "user",
              "senderLabel": "You · 88% match",
              "text": "“Disculpe, ¿este autobús va al centro?”"
            }
            """.trimIndent(),
        ).toLessonTurnUi()
        assertTrue(userTurn.isUser)
        assertNull(userTurn.translation)
        assertEquals("You · 88% match", userTurn.senderLabel)
    }
}
