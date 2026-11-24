import asyncio
import os
import sys
import unittest
from unittest.mock import AsyncMock

sys.path.insert(0, os.path.dirname(__file__))

from bilingual import (
    clean_text_for_tts,
    detect_clause_language,
    iter_streaming_sentences,
    strip_lang_tags,
    strip_lang_tags_stream,
    strip_romanization,
)
from roleplay_flow import RoleplayOrchestrator
from voices import (
    ElevenLabsSynthesizer,
    TUTOR_CATALOG,
    create_elevenlabs_tts,
    resolve_elevenlabs_voice,
    resolve_tutor_name,
    stream_llm_tts,
)


class ElevenLabsAndRoleplayTest(unittest.TestCase):
    def test_strip_legacy_lang_tags_and_romanization(self):
        self.assertEqual(
            strip_lang_tags('<lang code="en">Sure, that means bill.</lang> <lang code="es">¿Te traigo la cuenta?</lang>'),
            "Sure, that means bill. ¿Te traigo la cuenta?",
        )
        self.assertEqual(
            strip_lang_tags("[en]Try saying:[/en] [es]Un café con leche[/es]"),
            "Try saying: Un café con leche",
        )
        self.assertEqual(
            strip_romanization("こんにちは (konnichiwa)"),
            "こんにちは",
        )
        self.assertEqual(
            clean_text_for_tts("Hola, how are you? Estoy bien"),
            "Hola, how are you? Estoy bien",
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

    def test_mixed_language_utterance_synthesizes_in_single_pass_without_dual_speak(self):
        synth = ElevenLabsSynthesizer(api_key="test-key", tutor_id="sofia")
        calls: list[tuple[str, float]] = []

        async def _fake_stream_pcm(text: str, speed: float = 1.0):
            calls.append((text, speed))
            yield b"\x00\x01" * 960  # 40ms of 24kHz mono PCM

        synth.stream_pcm_chunks = _fake_stream_pcm  # type: ignore[method-assign]

        async def _run():
            async def _tokens():
                # Short mixed greeting merges into a single unified ElevenLabs synthesis call
                for tok in ["Hola, ", "how are you? ", "Estoy bien."]:
                    yield tok

            frames = []
            async for fr in stream_llm_tts(synth, _tokens(), speed=1.0):
                frames.append(fr)
            return frames

        frames = asyncio.run(_run())
        self.assertTrue(len(frames) > 0)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][0], "Hola, how are you? Estoy bien.")

    def test_streaming_sentences_keeps_inline_quotes_intact(self):
        async def _run():
            async def _tokens():
                yield 'Try asking the barista: "¿Tienen leche de avena?" '
                yield "Then listen for their reply."

            out = []
            async for s in iter_streaming_sentences(_tokens()):
                out.append(s)
            return out

        sentences = asyncio.run(_run())
        self.assertEqual(len(sentences), 2)
        self.assertEqual(sentences[0], 'Try asking the barista: "¿Tienen leche de avena?"')
        self.assertEqual(sentences[1], "Then listen for their reply.")

    def test_roleplay_orchestrator_goals_and_rescue_actions(self):
        events = []

        async def _broadcast(ev):
            events.append(ev)

        synth = ElevenLabsSynthesizer(api_key="test-key", tutor_id="sofia")
        tts = create_elevenlabs_tts(synthesizer=synth)
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
            self.assertNotIn("<lang", msg)
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

    def test_free_talk_user_language_default_and_clean_instructions(self):
        from agent import ConversationalAgent, can_use_multi_stt

        self.assertEqual(
            detect_clause_language("No problem, tell me about a trip!", "en", "es", "en"),
            "en",
        )
        self.assertEqual(
            detect_clause_language("¡Claro! ¿Cómo estás hoy?", "en", "es", "en"),
            "es",
        )
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
        self.assertNotIn('<lang code="', agent.instructions)
        asyncio.run(agent.set_listening_language("ko"))
        fake_stt.update_options.assert_called_with(language="ko")

    def test_tutor_catalog_six_elevenlabs_tutors_1_to_1_mapping(self):
        self.assertEqual(len(TUTOR_CATALOG), 6)
        voice_ids = {spec["voice_id"] for spec in TUTOR_CATALOG.values()}
        self.assertEqual(len(voice_ids), 6)
        self.assertEqual(resolve_tutor_name("sofia"), "Sofía")
        self.assertEqual(resolve_tutor_name("lucas"), "Lucas")
        self.assertEqual(resolve_tutor_name("ethan"), "Ethan")
        self.assertEqual(resolve_tutor_name("claire"), "Claire")
        self.assertEqual(resolve_tutor_name("elena"), "Sofía")
        self.assertEqual(
            resolve_elevenlabs_voice(tutor_id="sofia"),
            "DODLEQrClDo8wCz460ld",
        )
        self.assertEqual(
            resolve_elevenlabs_voice(tutor_id="lucas"),
            "cjVigY5qzO86Huf0OWal",
        )


if __name__ == "__main__":
    unittest.main()
