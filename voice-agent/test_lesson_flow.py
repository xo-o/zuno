import asyncio
import unittest
from unittest.mock import AsyncMock

import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

from lesson_flow import LessonOrchestrator, _compute_blank_lengths
from voices import ElevenLabsSynthesizer, create_elevenlabs_tts


class LessonFlowTest(unittest.TestCase):
    def _make_orch(self):
        events = []

        async def _broadcast(ev):
            events.append(ev)

        synth = ElevenLabsSynthesizer(api_key="test-key", tutor_id="sofia")
        tts = create_elevenlabs_tts(synthesizer=synth)
        meta = {
            "lessonId": "les_1",
            "lessonTitle": "Elige el autobús adecuado",
            "nativeLanguage": "en",
            "targetLanguage": "es",
            "targetLanguageName": "Spanish",
            "nativeLanguageName": "English",
            "level": "B1",
            "curriculum": {
                "targetPhrases": [
                    {
                        "id": "p1",
                        "setupNative": "To politely ask if this bus goes downtown, say:",
                        "phrase": "Disculpe, ¿este autobús va al centro?",
                        "translation": "Excuse me, does this bus go downtown?",
                        "focusWord": "autobús",
                        "syllables": "au · to · BÚS",
                        "pronunciationCue": "Stress final syllable",
                        "chunks": ["va al centro", "este autobús"],
                    }
                ],
                "buildingBlocks": [
                    {
                        "promptNative": "Now ask if this bus goes to the train station.",
                        "newWord": "la estación",
                        "meaning": "train station",
                        "stemHint": "¿Este autobús…?",
                        "expectedTarget": "¿Este autobús va a la estación?",
                    }
                ],
                "recallPrompts": [
                    {
                        "situationNative": "The doors open. Ask the driver which bus goes downtown.",
                        "firstWordHint": "Disculpe, ¿qué…",
                        "expectedTargets": ["Disculpe, ¿este autobús va al centro?"],
                    }
                ],
                "roleplay": {
                    "aiRole": "Bus driver",
                    "setting": "Bus terminal",
                    "goals": [
                        {"id": "goal_1", "label": "Ask route", "description": "Ask if this bus goes downtown"},
                        {"id": "goal_2", "label": "Ask stops", "description": "Ask how many stops to downtown"},
                    ],
                },
            },
        }
        return LessonOrchestrator(meta=meta, tts=tts, synthesizer=synth, broadcast=_broadcast), events

    def test_compute_blank_lengths(self):
        lengths = _compute_blank_lengths("Disculpe, ¿este autobús va al centro?")
        self.assertEqual(lengths, [8, 4, 7, 2, 2])

    def test_roleplay_goals_payload_tracks_completion(self):
        orch, _ = self._make_orch()
        initial = orch._roleplay_goals_payload()
        self.assertEqual(len(initial), 2)
        self.assertTrue(initial[0]["current"])
        self.assertFalse(initial[0]["completed"])
        self.assertFalse(initial[1]["current"])

        orch.completed_goal_ids.add("goal_1")
        after = orch._roleplay_goals_payload()
        self.assertTrue(after[0]["completed"])
        self.assertFalse(after[0]["current"])
        self.assertTrue(after[1]["current"])

    def test_teach_model_speaks_bilingual_prompt_in_single_call(self):
        orch, _ = self._make_orch()
        orch._speak = AsyncMock()

        async def _run():
            await orch.start()

        asyncio.run(_run())
        orch._speak.assert_awaited_once()
        spoken_arg = orch._speak.call_args.args[0]
        self.assertIn("To politely ask if this bus goes downtown, say:", spoken_arg)
        self.assertIn("Disculpe, ¿este autobús va al centro?", spoken_arg)
        self.assertEqual(orch.stage, "shadow_drill")

    def test_intent_classification_and_unified_question_reply(self):
        orch, events = self._make_orch()
        orch.stage = "shadow_drill"
        orch._quick_llm_json = AsyncMock(side_effect=[
            {"intent": "question"},
            {"answer": "‘Centro’ means downtown, as in va al centro."},
        ])
        orch._speak = AsyncMock()

        async def _run():
            await orch.handle_structured_user_turn("What does centro mean?", 0.95)

        asyncio.run(_run())
        self.assertEqual(orch.phrase_retries, 0)
        self.assertEqual(len(orch.pronunciation_scores), 0)
        orch._speak.assert_awaited_once()
        turn_events = [e for e in events if e.get("type") == "lesson_turn"]
        self.assertEqual(len(turn_events), 2)
        self.assertEqual(turn_events[0]["speaker"], "user")
        self.assertEqual(turn_events[1]["speaker"], "agent")
        self.assertIn("downtown", turn_events[1]["text"])


if __name__ == "__main__":
    unittest.main()
