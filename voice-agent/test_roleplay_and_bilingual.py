import asyncio
import os
import sys
import unittest
from unittest.mock import AsyncMock

sys.path.insert(0, os.path.dirname(__file__))

import livekit.plugins.cartesia as cartesia
from bilingual import (
    detect_clause_language,
    iter_streaming_clauses,
    segment_bilingual_text,
    strip_lang_tags,
    strip_lang_tags_stream,
)
from roleplay_flow import RoleplayOrchestrator
from voices import BilingualCartesiaSynthesizer, resolve_cartesia_locale, stream_bilingual_tts


class BilingualAndRoleplayTest(unittest.TestCase):
    def test_strip_lang_tags_variants(self):
        self.assertEqual(
            strip_lang_tags('<lang code="en">Sure, that means bill.</lang> <lang code="es">¿Te traigo la cuenta?</lang>'),
            "Sure, that means bill. ¿Te traigo la cuenta?",
        )
        self.assertEqual(
            strip_lang_tags('[en]Try saying:[/en] [es]Un café con leche[/es]'),
            "Try saying: Un café con leche",
        )
        self.assertEqual(
            strip_lang_tags('<lang="fr">Bonjour !</lang>'),
            "Bonjour !",
        )

    def test_strip_lang_tags_stream_across_chunk_boundaries(self):
        deltas = [
            "<lang",
            ' code="en">',
            "Sure, ",
            "that means check.</la",
            "ng> <lang code=",
            '"es">¿La cuenta?</lang>',
        ]

        async def _run():
            async def _gen():
                for d in deltas:
                    yield d

            out = []
            async for chunk in strip_lang_tags_stream(_gen()):
                out.append(chunk)
            return "".join(out)

        result = asyncio.run(_run())
        self.assertEqual(result, "Sure, that means check. ¿La cuenta?")

    def test_double_quotes_regression_and_untagged_bilingual_fallback(self):
        # Regression test for `.endswith` in Python
        segs = segment_bilingual_text(
            'Try asking me: "¿Tienen leche de avena?"',
            native_lang="en",
            target_lang="es",
            default_lang="es",
        )
        self.assertEqual(len(segs), 2)
        self.assertEqual(segs[0].language, "en")
        self.assertEqual(segs[1].language, "es")
        self.assertIn("leche de avena", segs[1].text)

        # Untagged multi-sentence clarification + Spanish continuation
        untagged = "Sure, that word means receipt in English. ¿Quieres el recibo ahora?"
        segs2 = segment_bilingual_text(
            untagged,
            native_lang="en",
            target_lang="es",
            default_lang="es",
        )
        self.assertEqual(len(segs2), 2)
        self.assertEqual(segs2[0].language, "en")
        self.assertEqual(segs2[1].language, "es")

        # Pure target language stays 100% target language
        pure_es = "¡Hola! Buenos días, ¿qué te pongo para tomar?"
        segs3 = segment_bilingual_text(
            pure_es,
            native_lang="en",
            target_lang="es",
            default_lang="es",
        )
        self.assertEqual(len(segs3), 1)
        self.assertEqual(segs3[0].language, "es")
        self.assertEqual(detect_clause_language("Perfecto, un momento.", "en", "es", "es"), "es")

    def test_streaming_clauses_and_sonic36_locale_synthesis(self):
        synth = BilingualCartesiaSynthesizer(
            voice_id="ec1e269e-9ca0-402f-8a18-58e0e022355a",
            tutor_voice="warm",
            accent="spain",
        )
        self.assertEqual(resolve_cartesia_locale("es", "spain", "warm"), "es-ES")
        self.assertEqual(resolve_cartesia_locale("en", "spain", "warm"), "en-US")

        calls: list[tuple[str, str, float]] = []

        async def _fake_fetch(text: str, language: str, speed: float = 1.0) -> bytes:
            calls.append((text, language, speed))
            return b"\x00\x01" * 960  # 40ms of 24kHz mono PCM

        synth.fetch_pcm_bytes = _fake_fetch  # type: ignore[method-assign]

        async def _run():
            async def _tokens():
                for tok in [
                    '<lang code="en">That means oat milk. Try asking:</lang> ',
                    '<lang code="es">¿Tenéis leche de avena?</lang>',
                ]:
                    yield tok

            frames = []
            async for fr in stream_bilingual_tts(
                synth,
                _tokens(),
                native_lang="en",
                target_lang="es",
                default_lang="es",
                speed=1.0,
            ):
                frames.append(fr)
            return frames

        frames = asyncio.run(_run())
        self.assertTrue(len(frames) > 0)
        langs = [c[1] for c in calls]
        self.assertIn("en", langs)
        self.assertIn("es", langs)
        self.assertEqual(langs[-1], "es")

    def test_roleplay_orchestrator_goals_and_rescue_actions(self):
        events = []

        async def _broadcast(ev):
            events.append(ev)

        tts = cartesia.TTS(api_key="test-key", voice="ec1e269e-9ca0-402f-8a18-58e0e022355a", language="es")
        synth = BilingualCartesiaSynthesizer(voice_id="ec1e269e-9ca0-402f-8a18-58e0e022355a", tutor_voice="warm", accent="latam")
        meta = {
            "mode": "roleplay",
            "roleplayId": "rp_1",
            "conversationId": "conv_1",
            "nativeLanguage": "en",
            "nativeLanguageName": "English",
            "targetLanguage": "es",
            "targetLanguageName": "Spanish",
            "varietyName": "Latin America Spanish",
            "level": "B1",
            "roleplay": {
                "id": "rp_1",
                "title": "Ordering at a café",
                "scenario": "Ordering coffee and handling out-of-stock milk",
                "userRole": "Customer",
                "aiRole": "Barista",
                "setting": "Busy morning café in Mexico City.",
                "goals": [
                    {"id": "goal_1", "label": "Order coffee", "description": "Order a drink politely", "samplePhrase": "¿Me das un café con leche, por favor?"},
                    {"id": "goal_2", "label": "Ask alternative", "description": "Ask for another milk option", "samplePhrase": "¿Tienen leche de almendra?"},
                ],
            },
        }
        orch = RoleplayOrchestrator(meta=meta, tts=tts, synthesizer=synth, broadcast=_broadcast)
        orch._quick_llm_json = AsyncMock(side_effect=[
            {"suggestion": "¿Tienen leche de almendra?", "translation": "Do you have almond milk?"},
            {"translation": "Hi! What can I get for you today?"},
        ])

        async def _run():
            await orch.broadcast_stage()
            await orch.emit_turn(
                speaker="agent",
                sender_label=orch.sender_role_label,
                text="¡Hola! ¿Qué te sirvo hoy?",
                actions=["translate", "listen_slow"],
            )
            msg = await orch.on_goal_completed("goal_1", "Yo quiero cafe", "Me gustaría un café")
            self.assertIn('<lang code="es">', msg)
            await orch.handle_client_action("hint")
            await orch.handle_client_action("translate")

        asyncio.run(_run())
        stage_events = [e for e in events if e.get("type") == "lesson_stage"]
        self.assertEqual(len(stage_events), 2)
        self.assertTrue(stage_events[1]["roleplayGoals"][0]["completed"])
        self.assertTrue(stage_events[1]["roleplayGoals"][1]["current"])

        rescue_events = [e for e in events if e.get("type") == "rescue_payload"]
        actions = [e.get("action") for e in rescue_events]
        self.assertIn("recast", actions)
        self.assertIn("hint", actions)
        self.assertIn("translate", actions)

    def test_free_talk_user_language_default_and_slow_pronunciation_tags(self):
        from agent import ConversationalAgent, can_use_multi_stt

        # 1. Strip `<lang code="es" speed="slow">...</lang>` cleanly across chunks
        tagged_pron = (
            '<lang code="en">You pronounce it</lang> '
            '<lang code="es">ferrocarril.</lang> '
            '<lang code="en">Slowly:</lang> '
            '<lang code="es" speed="slow">fe, rro, ca, rril.</lang>'
        )
        self.assertEqual(
            strip_lang_tags(tagged_pron),
            "You pronounce it ferrocarril. Slowly: fe, rro, ca, rril.",
        )

        segs = segment_bilingual_text(
            tagged_pron,
            native_lang="en",
            target_lang="es",
            default_lang="en",
        )
        self.assertEqual([(s.language, s.speed) for s in segs], [
            ("en", 1.0),
            ("es", 1.0),
            ("en", 1.0),
            ("es", 0.76),
        ])

        # 2. Symmetric `detect_clause_language`: English sentence with shared words ("a", "no") stays "en" when default_lang="en"
        self.assertEqual(
            detect_clause_language("No problem, tell me about a trip!", "en", "es", "en"),
            "en",
        )
        # While Spanish sentence still flips to "es" even when default_lang="en"
        self.assertEqual(
            detect_clause_language("¡Claro! ¿Cómo estás hoy?", "en", "es", "en"),
            "es",
        )
        # And English gloss quote after "means" stays "en"
        gloss_segs = segment_bilingual_text(
            'That word literally means "little coffee" in everyday conversation.',
            native_lang="en",
            target_lang="es",
            default_lang="en",
        )
        self.assertTrue(all(s.language == "en" for s in gloss_segs))

        # 3. ConversationalAgent Free Talk defaults to user language & supports non-multi STT switching
        self.assertTrue(can_use_multi_stt("es", "en"))
        self.assertFalse(can_use_multi_stt("ko", "en"))

        fake_stt = unittest.mock.MagicMock()
        agent = ConversationalAgent(
            stt=fake_stt,
            language="ko",
            target_language_name="Korean",
            native_language="en",
            native_language_name="English",
            conversation_kind="freetalk",
            teach_in_target=False,
            multi_stt_active=False,
        )
        self.assertTrue(agent.is_free_talk)
        self.assertEqual(agent.default_spoken_lang, "en")
        asyncio.run(agent.set_listening_language("ko"))
        fake_stt.update_options.assert_called_with(language="ko")

    def test_tutor_catalog_six_tutors_1_to_1_mapping(self):
        from voices import TUTOR_CATALOG, resolve_cartesia_voice, resolve_tutor_name

        self.assertEqual(len(TUTOR_CATALOG), 6)
        voice_ids = {spec["voice_id"] for spec in TUTOR_CATALOG.values()}
        self.assertEqual(len(voice_ids), 6)
        self.assertEqual(resolve_tutor_name("lucas"), "Lucas")
        self.assertEqual(resolve_tutor_name("ethan"), "Ethan")
        self.assertEqual(resolve_tutor_name("claire"), "Claire")
        self.assertEqual(resolve_tutor_name("elena"), "Sofía")
        self.assertEqual(
            resolve_cartesia_voice(tutor_id="lucas"),
            "8c254787-4eb4-4577-bd3d-fb3c273baea2",
        )


if __name__ == "__main__":
    unittest.main()
