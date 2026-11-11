import dataclasses
import re
import unicodedata
from typing import Optional


@dataclasses.dataclass
class SpeechSegment:
    text: str
    language: str
    speed: float = 1.0
    pause_after_ms: int = 0


def strip_lang_tags(text: str) -> str:
    """Strips bracket tags like `[en]...[/en]` or `<lang="en">...</lang>` from text."""
    if not text:
        return ""
    cleaned = re.sub(r'<lang="?([a-zA-Z_-]+)"?>([\s\S]*?)</lang>', r"\2", text)
    cleaned = re.sub(r'\[([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)\]([\s\S]*?)\[/\1\]', r"\2", cleaned)
    return re.sub(r'\s{2,}', ' ', cleaned).strip()


def strip_romanization(text: str) -> str:
    """Strips parenthesized Latin transliterations following non-Latin scripts (e.g. 'こんにちは (konnichiwa)' -> 'こんにちは')."""
    if not text:
        return ""
    non_latin_pattern = (
        r'([\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF])'
        r'\s*\([A-Za-z0-9\s,.\'’\-–—]+\)'
    )
    return re.sub(non_latin_pattern, r"\1", text).strip()


LEAD_IN_REGEX = re.compile(
    r'^(repeat after me|say after me|listen and repeat|to say this|to say|you can say|say|listen|ask):?\s+',
    re.IGNORECASE,
)

FUNCTION_WORDS: dict[str, set[str]] = {
    "en": {"the", "a", "an", "to", "in", "for", "of", "and", "or", "is", "are", "say", "after", "repeat", "me", "listen", "this", "that", "you", "with", "now", "ask", "if", "please"},
    "es": {"el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "en", "por", "para", "con", "y", "o", "es", "son", "este", "esta", "estos", "estas", "va", "al", "cómo", "dónde", "qué", "disculpe", "gracias", "por favor"},
    "fr": {"le", "la", "les", "un", "une", "des", "du", "de", "en", "pour", "avec", "et", "ou", "est", "sont", "ce", "cette", "ces", "comment", "où", "merci"},
    "de": {"der", "die", "das", "ein", "eine", "einen", "einem", "einer", "eines", "und", "oder", "in", "mit", "für", "zu", "ist", "sind", "bitte", "danke", "wie", "wo", "was"},
    "it": {"il", "lo", "la", "i", "gli", "le", "un", "uno", "una", "di", "da", "in", "con", "su", "per", "tra", "fra", "e", "o", "è", "sono", "questo", "questa", "grazie"},
    "pt": {"o", "a", "os", "as", "um", "uma", "uns", "umas", "de", "do", "da", "em", "no", "na", "por", "para", "com", "e", "ou", "é", "são", "este", "esta", "obrigado"},
}


def _score_language(words: list[str], lang: str) -> int:
    norm_lang = lang.lower().split("-")[0]
    words_set = FUNCTION_WORDS.get(norm_lang, set())
    if not words_set:
        return 0
    score = 0
    for w in words:
        clean = re.sub(r'^[^\w]+|[^\w]+$', '', w.lower())
        if clean in words_set:
            score += 1
    return score


def _merge_adjacent(segments: list[SpeechSegment]) -> list[SpeechSegment]:
    if len(segments) <= 1:
        return segments
    merged: list[SpeechSegment] = []
    for s in segments:
        if merged and merged[-1].language == s.language and merged[-1].speed == s.speed:
            merged[-1].text = f"{merged[-1].text} {s.text}".strip()
            if s.pause_after_ms > merged[-1].pause_after_ms:
                merged[-1].pause_after_ms = s.pause_after_ms
        else:
            merged.append(dataclasses.replace(s))
    return merged


def segment_bilingual_text(
    raw_text: str,
    native_lang: str = "en",
    target_lang: str = "es",
    default_lang: Optional[str] = None,
    speed: float = 1.0,
) -> list[SpeechSegment]:
    """Splits an utterance into SpeechSegments tagged with their appropriate Cartesia language."""
    text = strip_romanization(raw_text).strip()
    if not text:
        return []

    norm_native = (native_lang or "en").lower().split("-")[0]
    norm_target = (target_lang or default_lang or "es").lower().split("-")[0]
    norm_default = (default_lang or target_lang or native_lang or "es").lower().split("-")[0]

    # 1. Explicit tags: [en]...[/en] or <lang="en">...</lang>
    tag_regex = re.compile(
        r'(?:<lang="?([a-zA-Z_-]+)"?>([\s\S]*?)</lang>|\[([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)\]([\s\S]*?)\[/\3\])'
    )
    matches = list(tag_regex.finditer(text))
    if matches:
        segments: list[SpeechSegment] = []
        last_idx = 0
        for m in matches:
            if m.start() > last_idx:
                prefix = text[last_idx : m.start()].strip()
                if prefix:
                    segments.append(SpeechSegment(text=prefix, language=norm_target, speed=speed))
            tag_lang = (m.group(1) or m.group(3)).lower().split("-")[0]
            tag_content = (m.group(2) or m.group(4)).strip()
            if tag_content:
                segments.append(SpeechSegment(text=tag_content, language=tag_lang, speed=speed))
            last_idx = m.end()
        if last_idx < len(text):
            suffix = text[last_idx:].strip()
            if suffix:
                segments.append(SpeechSegment(text=suffix, language=norm_target, speed=speed))
        return _merge_adjacent(segments)

    # 2. Quotes enclosing target language phrases (e.g. Try saying: "Un café, por favor")
    if norm_native != norm_target and re.search(r'["“\'][^"”\']+?["”\']', text):
        parts = re.split(r'(["“\'][^"”\']+?["”\'])', text)
        if len(parts) > 1:
            segments = []
            for p in parts:
                p_trimmed = p.strip()
                if not p_trimmed:
                    continue
                is_quoted = (
                    (p_trimmed.startswith('"') and p_trimmed.endswith('"'))
                    or (p_trimmed.startswith('“') and p_trimmed.endswith('”'))
                    or (p_trimmed.startswith("'") and p_trimmed.endswith("'"))
                )
                if is_quoted:
                    clean = re.sub(r'^[“"\']|[”"\']$', '', p_trimmed).strip()
                    if clean:
                        segments.append(SpeechSegment(text=clean, language=norm_target, speed=speed))
                else:
                    clean = re.sub(r':\s*$', '', p_trimmed).strip()
                    if clean:
                        segments.append(SpeechSegment(text=p_trimmed, language=norm_native, speed=speed))
            if segments:
                return _merge_adjacent(segments)

    # 3. Lead-in colons / dashes (e.g. "Repeat after me: Un elefante")
    colon_idx = text.find(":")
    if colon_idx > 0 and colon_idx < len(text) - 1 and norm_native != norm_target:
        left = text[:colon_idx].strip()
        right = text[colon_idx + 1 :].strip()
        if left and right and (LEAD_IN_REGEX.match(left + ": ") or len(left.split()) <= 6):
            left_native_score = _score_language(left.split(), norm_native)
            right_target_score = _score_language(right.split(), norm_target)
            if LEAD_IN_REGEX.match(left + ": ") or left_native_score > 0 or right_target_score > 0:
                return [
                    SpeechSegment(text=left, language=norm_native, speed=speed, pause_after_ms=180),
                    SpeechSegment(text=right, language=norm_target, speed=speed),
                ]

    # 4. Non-Latin target script with Latin native language
    is_non_latin = bool(re.search(r'[\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]', text))
    if is_non_latin and bool(re.match(r'^[a-z]', norm_native, re.I)):
        script_pattern = re.compile(
            r'([\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]+(?:[\s\u3000-\u303F\uFF00-\uFFEF\W]*[\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]+)*)'
        )
        pieces = script_pattern.split(text)
        if len(pieces) > 1:
            segments = []
            for pc in pieces:
                c = pc.strip()
                if not c:
                    continue
                has_nl = bool(re.search(r'[\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]', c))
                segments.append(
                    SpeechSegment(
                        text=c,
                        language=norm_target if has_nl else norm_native,
                        speed=speed,
                    )
                )
            return _merge_adjacent(segments)

    # Default: single segment
    return [SpeechSegment(text=text, language=norm_default, speed=speed)]
