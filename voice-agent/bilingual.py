import dataclasses
import re
from typing import AsyncIterable, AsyncIterator, Optional


@dataclasses.dataclass
class SpeechSegment:
    text: str
    language: str
    speed: float = 1.0
    pause_after_ms: int = 160


# SpeechClause is an alias for SpeechSegment
SpeechClause = SpeechSegment

_XML_LANG_TAG_RE = re.compile(
    r'<lang(?:\s+code)?\s*=\s*["\']?([a-zA-Z_-]+)["\']?\s*>([\s\S]*?)</lang>',
    re.IGNORECASE,
)
_BRACKET_LANG_TAG_RE = re.compile(
    r'\[([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)\]([\s\S]*?)\[/\1\]',
    re.IGNORECASE,
)
_COMBINED_LANG_TAG_RE = re.compile(
    r'(?:<lang(?:\s+code)?\s*=\s*["\']?([a-zA-Z_-]+)["\']?\s*>([\s\S]*?)</lang>'
    r'|\[([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)\]([\s\S]*?)\[/\3\])',
    re.IGNORECASE,
)
_STRAY_TAG_TOKEN_RE = re.compile(
    r'(?:</?lang(?:\s+code\s*=\s*["\']?[a-zA-Z_-]*["\']?|\s*=\s*["\']?[a-zA-Z_-]*["\']?)?\s*>'
    r'|\[/?[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?\])',
    re.IGNORECASE,
)
_NON_LATIN_CHAR_RE = re.compile(
    r'[\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F'
    r'\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]'
)
_STAGE_DIRECTION_RE = re.compile(
    r'\[(?:pause|laughs?|sighs?|smiles?|chuckles?|clears throat|nods?)\]'
    r'|\((?:pause|laughs?|sighs?|smiles?|chuckles?|clears throat|nods?)\)',
    re.IGNORECASE,
)


def strip_lang_tags(text: str) -> str:
    """Strips `<lang code="en">...</lang>`, `<lang="en">...</lang>`, and `[en]...[/en]` tags."""
    if not text:
        return ""
    cleaned = _XML_LANG_TAG_RE.sub(r"\2", text)
    cleaned = _BRACKET_LANG_TAG_RE.sub(r"\2", cleaned)
    cleaned = _STRAY_TAG_TOKEN_RE.sub("", cleaned)
    return re.sub(r"\s{2,}", " ", cleaned).strip()


async def strip_lang_tags_stream(text_stream: AsyncIterable) -> AsyncIterator:
    """Strips `<lang...>` and `[xx]` tags cleanly across arbitrary streaming LLM token boundaries."""
    buf = ""
    partial_re = re.compile(r"(?:<[^>]{0,40}|\[/?[a-zA-Z0-9_-]{0,8})$")
    async for delta in text_stream:
        if not isinstance(delta, str):
            if buf:
                cleaned_buf = _STRAY_TAG_TOKEN_RE.sub("", buf)
                if cleaned_buf:
                    yield cleaned_buf
                buf = ""
            yield delta
            continue

        buf += delta
        buf = _STRAY_TAG_TOKEN_RE.sub("", buf)

        m = partial_re.search(buf)
        if m:
            safe = buf[: m.start()]
            buf = buf[m.start() :]
            if safe:
                yield safe
        else:
            if buf:
                yield buf
                buf = ""

    if buf:
        cleaned_buf = _STRAY_TAG_TOKEN_RE.sub("", buf)
        if cleaned_buf:
            yield cleaned_buf


def strip_speech_artifacts(text: str) -> str:
    """Removes markdown formatting and stage directions so TTS speaks natural prose only."""
    if not text:
        return ""
    cleaned = _STAGE_DIRECTION_RE.sub(" ", text)
    cleaned = re.sub(r"[*_`#~]+", "", cleaned)
    cleaned = re.sub(r"^\s*[-•]\s+", "", cleaned, flags=re.MULTILINE)
    return re.sub(r"\s{2,}", " ", cleaned).strip()


def strip_romanization(text: str) -> str:
    """Strips parenthesized Latin transliterations following non-Latin scripts (e.g. 'こんにちは (konnichiwa)' -> 'こんにちは')."""
    if not text:
        return ""
    non_latin_pattern = (
        r'([\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF])'
        r'\s*\([A-Za-z0-9\s,.\'’\-–—]+\)'
    )
    return re.sub(non_latin_pattern, r"\1", text).strip()


def strip_parenthetical_notes(text: str) -> str:
    """Strips grammar/clarification notes in parentheses like '(plural)', '(formal)', '(masculine)'."""
    if not text:
        return ""
    return re.sub(r"\s*\([A-Za-z0-9\s,.'’\-–—/]+\)", "", text).strip()


def ensure_terminal_punct(text: str, default_punct: str = ".") -> str:
    """Ensures a transcript chunk has a clean terminal punctuation mark for Cartesia prosody."""
    cleaned = text.strip()
    if not cleaned:
        return ""
    cleaned = re.sub(r"[:\-–—]+$", "", cleaned).strip()
    cleaned = re.sub(r"\.{3,}$|…$", "", cleaned).strip()
    if not cleaned:
        return ""
    if cleaned[-1] not in ".?!;:¡¿。？！":
        cleaned += default_punct
    return cleaned


LEAD_IN_REGEX = re.compile(
    r"^(repeat after me|say after me|listen and repeat|to say this|to say|you can say|"
    r"try saying|try asking|in english|that means|it means|say|listen|ask):?\s+",
    re.IGNORECASE,
)

FUNCTION_WORDS: dict[str, set[str]] = {
    "en": {
        "the", "a", "an", "to", "in", "for", "of", "and", "or", "is", "are", "was", "were",
        "say", "after", "repeat", "me", "listen", "this", "that", "you", "your", "with",
        "now", "ask", "if", "please", "means", "mean", "word", "phrase", "try", "can",
        "how", "what", "when", "where", "why", "here", "sure", "let's", "lets", "don't", "dont",
    },
    "es": {
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "en", "por", "para",
        "con", "y", "o", "es", "son", "está", "están", "este", "esta", "estos", "estas", "va",
        "al", "cómo", "dónde", "qué", "quién", "cuánto", "cuántos", "disculpe", "gracias",
        "hola", "buenos", "buenas", "quiero", "puedo", "tiene", "tienes", "muy", "pero", "sí",
    },
    "fr": {
        "le", "la", "les", "un", "une", "des", "du", "de", "en", "pour", "avec", "et", "ou",
        "est", "sont", "ce", "cette", "ces", "comment", "où", "merci", "bonjour", "salut",
        "je", "tu", "vous", "nous", "oui", "non", "très", "mais", "dans", "sur", "quel", "quelle",
    },
    "de": {
        "der", "die", "das", "den", "dem", "ein", "eine", "einen", "einem", "einer", "und", "oder",
        "in", "mit", "für", "zu", "ist", "sind", "bitte", "danke", "wie", "wo", "was", "ich",
        "du", "sie", "wir", "nicht", "auch", "auf", "haben", "möchte", "guten", "hallo",
    },
    "it": {
        "il", "lo", "la", "i", "gli", "le", "un", "uno", "una", "di", "da", "in", "con", "su",
        "per", "tra", "fra", "e", "o", "è", "sono", "questo", "questa", "grazie", "ciao",
        "buongiorno", "vorrei", "dove", "come", "che", "non", "più", "molto",
    },
    "pt": {
        "o", "a", "os", "as", "um", "uma", "uns", "umas", "de", "do", "da", "em", "no", "na",
        "por", "para", "com", "e", "ou", "é", "são", "este", "esta", "obrigado", "obrigada",
        "olá", "bom", "boa", "você", "eu", "não", "muito", "onde", "como", "quero", "pode",
    },
    "nl": {
        "de", "het", "een", "en", "van", "in", "is", "op", "te", "dat", "die", "voor", "met",
        "niet", "zijn", "ik", "je", "u", "wij", "dank", "alsjeblieft", "goedemorgen", "hoe", "waar",
    },
    "pl": {
        "i", "w", "na", "z", "do", "nie", "to", "że", "co", "jak", "czy", "jest", "proszę",
        "dziękuję", "cześć", "dzień", "dobry", "gdzie", "kiedy", "bardzo",
    },
    "tr": {
        "bir", "ve", "bu", "için", "ile", "de", "da", "mi", "mı", "mu", "mü", "ne", "nasıl",
        "nerede", "merhaba", "teşekkürler", "lütfen", "evet", "hayır", "çok",
    },
    "sv": {
        "och", "i", "att", "det", "som", "en", "på", "är", "för", "med", "till", "inte",
        "jag", "du", "hej", "tack", "hur", "var", "vad",
    },
    "id": {
        "yang", "dan", "di", "ke", "dari", "ini", "itu", "untuk", "dengan", "tidak", "saya",
        "anda", "apa", "bagaimana", "dimana", "terima", "kasih", "tolong", "halo",
    },
}

_DISTINCT_LANG_MARKERS: dict[str, re.Pattern[str]] = {
    "es": re.compile(r"[¿¡ñÑ]"),
    "de": re.compile(r"[ßäöüÄÖÜ]"),
    "fr": re.compile(r"[œæçÇ]|(?:\b(?:qu'|c'|j'|n'|s'|d'|l'))", re.IGNORECASE),
    "pt": re.compile(r"[ãõçÃÕÇ]"),
}

_NON_LATIN_LANGS = {"ar", "bn", "el", "gu", "he", "hi", "ja", "ka", "kn", "ko", "ml", "mr", "or", "pa", "ru", "ta", "te", "th", "uk", "ur", "fa", "zh"}


def _score_language(words: list[str], lang: str) -> int:
    norm_lang = lang.lower().split("-")[0]
    words_set = FUNCTION_WORDS.get(norm_lang, set())
    if not words_set:
        return 0
    score = 0
    for w in words:
        clean = re.sub(r"^[^\w]+|[^\w]+$", "", w.lower())
        if clean in words_set:
            score += 1
    return score


def _has_distinct_markers(text: str, lang: str) -> bool:
    norm = lang.lower().split("-")[0]
    pat = _DISTINCT_LANG_MARKERS.get(norm)
    return bool(pat and pat.search(text))


def detect_clause_language(
    clause_text: str,
    native_lang: str = "en",
    target_lang: str = "es",
    default_lang: Optional[str] = None,
) -> str:
    """Determines whether an untagged single-sentence clause is in `target_lang` or `native_lang`.

    Conservative by design: defaults strictly to `default_lang` (`target_lang`), only switching
    to `native_lang` when clear script or lexical evidence shows a user-language explanation.
    """
    norm_native = (native_lang or "en").lower().split("-")[0]
    norm_target = (target_lang or default_lang or "es").lower().split("-")[0]
    norm_default = (default_lang or norm_target).lower().split("-")[0]

    if norm_native == norm_target or not clause_text.strip():
        return norm_default

    has_non_latin = bool(_NON_LATIN_CHAR_RE.search(clause_text))
    target_is_non_latin = norm_target in _NON_LATIN_LANGS
    native_is_non_latin = norm_native in _NON_LATIN_LANGS

    if target_is_non_latin and not native_is_non_latin:
        return norm_target if has_non_latin else norm_native
    if native_is_non_latin and not target_is_non_latin:
        return norm_native if has_non_latin else norm_target

    if _has_distinct_markers(clause_text, norm_target):
        return norm_target
    if _has_distinct_markers(clause_text, norm_native) and not _has_distinct_markers(clause_text, norm_target):
        return norm_native

    if LEAD_IN_REGEX.match(clause_text.strip()):
        return norm_native

    words = clause_text.split()
    native_score = _score_language(words, norm_native)
    target_score = _score_language(words, norm_target)

    # Require unmistakable native function-word dominance with zero target markers
    if native_score >= 2 and target_score == 0:
        return norm_native
    if native_score >= 3 and native_score >= target_score * 3:
        return norm_native
    if target_score >= 1:
        return norm_target

    return norm_default


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


def _segment_single_untagged_sentence(
    sentence: str,
    norm_native: str,
    norm_target: str,
    norm_default: str,
    speed: float,
) -> list[SpeechSegment]:
    text = sentence.strip()
    if not text:
        return []

    # 1. Double-quoted target phrase: e.g. Try saying: "Un café con leche, por favor"
    if norm_native != norm_target and re.search(r'["“][^"”]{2,}?["”]', text):
        parts = re.split(r'(["“][^"”]{2,}?["”])', text)
        if len(parts) > 1:
            segments: list[SpeechSegment] = []
            for p in parts:
                p_trimmed = p.strip()
                if not p_trimmed:
                    continue
                is_double_quoted = (
                    (p_trimmed.startswith('"') and p_trimmed.endswith('"'))
                    or (p_trimmed.startswith("“") and p_trimmed.endswith("”"))
                )
                if is_double_quoted:
                    clean = re.sub(r'^[“"]|[”"]$', "", p_trimmed).strip()
                    if clean:
                        q_lang = detect_clause_language(clean, norm_native, norm_target, norm_target)
                        segments.append(SpeechSegment(text=clean, language=q_lang, speed=speed))
                else:
                    clean = re.sub(r":\s*$", "", p_trimmed).strip()
                    if clean:
                        outer_lang = detect_clause_language(clean, norm_native, norm_target, norm_native)
                        segments.append(SpeechSegment(text=p_trimmed, language=outer_lang, speed=speed))
            if segments:
                return _merge_adjacent(segments)

    # 2. Lead-in colons: e.g. "Repeat after me: Un elefante"
    colon_idx = text.find(":")
    if 0 < colon_idx < len(text) - 1 and norm_native != norm_target:
        left = text[:colon_idx].strip()
        right = text[colon_idx + 1 :].strip()
        if left and right and len(right.split()) >= 1 and len(right) >= 2:
            left_native_score = _score_language(left.split(), norm_native)
            right_target_score = _score_language(right.split(), norm_target)
            if LEAD_IN_REGEX.match(left + ": ") or (left_native_score > 0 and right_target_score > 0):
                return [
                    SpeechSegment(text=left, language=norm_native, speed=speed, pause_after_ms=180),
                    SpeechSegment(text=right, language=norm_target, speed=speed),
                ]

    # 3. Non-Latin script mixed with Latin script
    is_non_latin = bool(_NON_LATIN_CHAR_RE.search(text))
    if is_non_latin and (norm_native not in _NON_LATIN_LANGS) != (norm_target not in _NON_LATIN_LANGS):
        script_pattern = re.compile(
            r'([\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]+'
            r'(?:[\s\u3000-\u303F\uFF00-\uFFEF\W]*'
            r'[\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]+)*)'
        )
        pieces = script_pattern.split(text)
        if len(pieces) > 1:
            non_latin_lang = norm_target if norm_target in _NON_LATIN_LANGS else norm_native
            latin_lang = norm_native if non_latin_lang == norm_target else norm_target
            segments = []
            for pc in pieces:
                c = pc.strip()
                if not c or not re.search(r"\w", c):
                    continue
                has_nl = bool(_NON_LATIN_CHAR_RE.search(c))
                segments.append(
                    SpeechSegment(
                        text=c,
                        language=non_latin_lang if has_nl else latin_lang,
                        speed=speed,
                    )
                )
            if segments:
                return _merge_adjacent(segments)

    detected = detect_clause_language(text, norm_native, norm_target, norm_default)
    return [SpeechSegment(text=text, language=detected, speed=speed)]


def segment_bilingual_text(
    raw_text: str,
    native_lang: str = "en",
    target_lang: str = "es",
    default_lang: Optional[str] = None,
    speed: float = 1.0,
) -> list[SpeechSegment]:
    """Splits a tagged or untagged utterance into SpeechSegments tagged with their Cartesia language."""
    text = strip_speech_artifacts(strip_romanization(raw_text)).strip()
    if not text:
        return []

    norm_native = (native_lang or "en").lower().split("-")[0]
    norm_target = (target_lang or default_lang or "es").lower().split("-")[0]
    norm_default = (default_lang or target_lang or native_lang or "es").lower().split("-")[0]

    # 1. Explicit tags: <lang code="en">...</lang>, <lang="en">...</lang>, or [en]...[/en]
    matches = list(_COMBINED_LANG_TAG_RE.finditer(text))
    if matches:
        segments: list[SpeechSegment] = []
        last_idx = 0
        for m in matches:
            if m.start() > last_idx:
                prefix = _STRAY_TAG_TOKEN_RE.sub("", text[last_idx : m.start()]).strip()
                if prefix:
                    segments.extend(
                        segment_bilingual_text(prefix, norm_native, norm_target, norm_default, speed)
                    )
            tag_lang = (m.group(1) or m.group(3)).lower().split("-")[0]
            tag_content = _STRAY_TAG_TOKEN_RE.sub("", (m.group(2) or m.group(4))).strip()
            if tag_content:
                segments.append(SpeechSegment(text=tag_content, language=tag_lang, speed=speed))
            last_idx = m.end()
        if last_idx < len(text):
            suffix = _STRAY_TAG_TOKEN_RE.sub("", text[last_idx:]).strip()
            if suffix:
                segments.extend(
                    segment_bilingual_text(suffix, norm_native, norm_target, norm_default, speed)
                )
        return _merge_adjacent(segments)

    cleaned = _STRAY_TAG_TOKEN_RE.sub("", text).strip()
    if not cleaned:
        return []

    # 2. Split multi-sentence turns at sentence boundaries so untagged clarifications + target follow-ups
    #    get independent language classification.
    sentences = [s.strip() for s in re.split(r"(?<=[.?!。？！])\s+", cleaned) if s.strip()]
    if len(sentences) > 1:
        all_segs: list[SpeechSegment] = []
        for sent in sentences:
            all_segs.extend(
                _segment_single_untagged_sentence(sent, norm_native, norm_target, norm_default, speed)
            )
        return _merge_adjacent(all_segs)

    return _segment_single_untagged_sentence(cleaned, norm_native, norm_target, norm_default, speed)


_SENTENCE_END_RE = re.compile(r"([.?!。？！]+)(?:\s+|$)")
_OPEN_TAG_RE = re.compile(
    r"(?:<lang(?:\s+code)?\s*=\s*[\"']?[a-zA-Z_-]+[\"']?\s*>|\[[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?\])",
    re.IGNORECASE,
)


async def iter_streaming_clauses(
    text_stream: AsyncIterable[str],
    *,
    native_lang: str = "en",
    target_lang: str = "es",
    default_lang: Optional[str] = None,
    speed: float = 1.0,
) -> AsyncIterator[SpeechSegment]:
    """Yields SpeechSegments incrementally from a live LLM token stream at tag or sentence boundaries.

    Achieves low Time-To-First-Audio (first sentence/tag synthesizes while the LLM generates the next)
    without ever splitting inside an unclosed `<lang>` / `[xx]` tag.
    """
    norm_native = (native_lang or "en").lower().split("-")[0]
    norm_target = (target_lang or default_lang or "es").lower().split("-")[0]
    norm_default = (default_lang or target_lang or "es").lower().split("-")[0]

    buf = ""
    partial_tag_tail_re = re.compile(r"(?:<[^>]{0,40}|\[/?[a-zA-Z0-9_-]{0,8})$")

    async for delta in text_stream:
        if not isinstance(delta, str) or not delta:
            continue
        buf += delta

        while buf:
            # 1. If there is a complete `<lang>...</lang>` or `[xx]...[/xx]` block in `buf`, emit everything up to its end
            m_tag = _COMBINED_LANG_TAG_RE.search(buf)
            if m_tag:
                if m_tag.start() > 0:
                    prefix = buf[: m_tag.start()].strip()
                    if prefix:
                        for seg in segment_bilingual_text(prefix, norm_native, norm_target, norm_default, speed):
                            yield seg
                tag_lang = (m_tag.group(1) or m_tag.group(3)).lower().split("-")[0]
                tag_body = strip_speech_artifacts(strip_romanization(m_tag.group(2) or m_tag.group(4))).strip()
                tag_body = _STRAY_TAG_TOKEN_RE.sub("", tag_body).strip()
                if tag_body:
                    # Split long tagged paragraphs by sentence so first sentence plays immediately
                    sub_sents = [s.strip() for s in re.split(r"(?<=[.?!。？！])\s+", tag_body) if s.strip()]
                    for sub in sub_sents:
                        yield SpeechSegment(text=sub, language=tag_lang, speed=speed)
                buf = buf[m_tag.end() :]
                continue

            # 2. If there is an unclosed `<lang...>` or `[xx]` tag in `buf`, wait for its closing tag
            if _OPEN_TAG_RE.search(buf):
                break

            # 3. Avoid cutting a partial `<la` or `[e` at the very end of `buf`
            safe_end = len(buf)
            m_tail = partial_tag_tail_re.search(buf)
            if m_tail:
                safe_end = m_tail.start()

            inspect = buf[:safe_end]
            # Wait if there is an unclosed double quote in `inspect`
            if inspect.count('"') % 2 == 1 or inspect.count("“") > inspect.count("”"):
                break

            m_sent = _SENTENCE_END_RE.search(inspect)
            if m_sent and m_sent.end() >= 8:
                ready = buf[: m_sent.end()].strip()
                buf = buf[m_sent.end() :]
                if ready:
                    for seg in segment_bilingual_text(ready, norm_native, norm_target, norm_default, speed):
                        yield seg
                continue
            break

    rem = buf.strip()
    if rem:
        rem_clean = _STRAY_TAG_TOKEN_RE.sub("", rem).strip()
        if rem_clean:
            for seg in segment_bilingual_text(rem_clean, norm_native, norm_target, norm_default, speed):
                yield seg
