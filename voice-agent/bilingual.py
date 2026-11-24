import re
from typing import AsyncIterable, AsyncIterator, Optional

_XML_LANG_TAG_RE = re.compile(
    r"<lang\b([^>]*)>([\s\S]*?)</lang>",
    re.IGNORECASE,
)
_BRACKET_LANG_TAG_RE = re.compile(
    r"\[([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)\]([\s\S]*?)\[/\1\]",
    re.IGNORECASE,
)
_STRAY_TAG_TOKEN_RE = re.compile(
    r"(?:</?lang\b[^>]*>|\[/?[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?\])",
    re.IGNORECASE,
)
_NON_LATIN_CHAR_RE = re.compile(
    r"[\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F"
    r"\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]"
)
_STAGE_DIRECTION_RE = re.compile(
    r"\[(?:pause|laughs?|sighs?|smiles?|chuckles?|clears throat|nods?)\]"
    r"|\((?:pause|laughs?|sighs?|smiles?|chuckles?|clears throat|nods?)\)",
    re.IGNORECASE,
)


def strip_lang_tags(text: str) -> str:
    """Strips legacy `<lang code="en">...</lang>`, `<lang="en">...</lang>`, and `[en]...[/en]` tags."""
    if not text:
        return ""
    cleaned = _XML_LANG_TAG_RE.sub(r"\2", text)
    cleaned = _BRACKET_LANG_TAG_RE.sub(r"\2", cleaned)
    cleaned = _STRAY_TAG_TOKEN_RE.sub("", cleaned)
    return re.sub(r"\s{2,}", " ", cleaned).strip()


async def strip_lang_tags_stream(text_stream: AsyncIterable) -> AsyncIterator:
    """Strips any `<lang...>` or `[xx]` tags across streaming chunks so UI captions stay clean."""
    buf = ""
    partial_re = re.compile(r"(?:<[^>]{0,64}|\[/?[a-zA-Z0-9_-]{0,8})$")
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
        r"([\u0400-\u04FF\u0590-\u05FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF])"
        r"\s*\([A-Za-z0-9\s,.\'’\-–—]+\)"
    )
    return re.sub(non_latin_pattern, r"\1", text).strip()


def strip_parenthetical_notes(text: str) -> str:
    """Strips grammar/clarification notes in parentheses like '(plural)', '(formal)', '(masculine)'."""
    if not text:
        return ""
    return re.sub(r"\s*\([A-Za-z0-9\s,.'’\-–—/]+\)", "", text).strip()


def ensure_terminal_punct(text: str, default_punct: str = ".") -> str:
    """Ensures a spoken sentence has clean terminal punctuation for natural TTS prosody."""
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


def clean_text_for_tts(text: str) -> str:
    """Cleans legacy tags, romanization, and markdown while keeping mixed-language sentences intact."""
    if not text:
        return ""
    return strip_speech_artifacts(strip_romanization(strip_lang_tags(text))).strip()


_STREAM_SENTENCE_BOUNDARY_RE = re.compile(r'([.?!。？！]+["”\'’)]*)\s+')


async def iter_streaming_sentences(
    text_stream: AsyncIterable[str],
    *,
    min_chars: int = 36,
) -> AsyncIterator[str]:
    """Yields complete sentences from a live LLM stream without splitting mixed-language clauses.

    ElevenLabs `eleven_v4_turbo` handles multilingual code-switching natively in `Auto` mode, so
    short turns (e.g. 'Hola, how are you? Estoy bien.') stay in a single continuous generation, while
    longer multi-sentence replies flush after the first full sentence (>= `min_chars`) for low TTFB.
    """
    buf = ""
    partial_tag_tail_re = re.compile(r"(?:<[^>]{0,64}|\[/?[a-zA-Z0-9_-]{0,8})$")

    async for delta in text_stream:
        if not isinstance(delta, str) or not delta:
            continue
        buf += delta

        while buf:
            safe_end = len(buf)
            m_tail = partial_tag_tail_re.search(buf)
            if m_tail:
                safe_end = m_tail.start()

            inspect = buf[:safe_end]
            split_match = None
            for m in _STREAM_SENTENCE_BOUNDARY_RE.finditer(inspect):
                prefix = inspect[: m.end()]
                # Never cut inside an unclosed inline quote
                if prefix.count('"') % 2 == 1 or prefix.count("“") > prefix.count("”"):
                    continue
                candidate = clean_text_for_tts(prefix)
                if len(candidate) >= min_chars:
                    split_match = (m, candidate)
                    break

            if split_match is not None:
                m, cleaned = split_match
                buf = buf[m.end() :]
                if cleaned:
                    yield cleaned
                continue
            break

    rem = clean_text_for_tts(buf)
    if rem:
        yield rem


FUNCTION_WORDS: dict[str, set[str]] = {
    "en": {
        "the", "a", "an", "to", "in", "for", "of", "and", "or", "is", "are", "was", "were",
        "say", "after", "repeat", "me", "listen", "this", "that", "you", "your", "with",
        "now", "ask", "if", "please", "means", "mean", "word", "phrase", "try", "can",
        "how", "what", "when", "where", "why", "here", "sure", "let's", "lets", "don't", "dont",
        "pronounce", "sound", "sounds", "talk", "about", "anything", "today", "would", "like",
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
}

_DISTINCT_LANG_MARKERS: dict[str, re.Pattern[str]] = {
    "es": re.compile(r"[¿¡ñÑ]"),
    "de": re.compile(r"[ßäöüÄÖÜ]"),
    "fr": re.compile(r"[œæçÇ]|(?:\b(?:qu'|c'|j'|n'|s'|d'|l'))", re.IGNORECASE),
    "pt": re.compile(r"[ãõçÃÕÇ]"),
}

_NON_LATIN_LANGS = {
    "ar", "bn", "el", "gu", "he", "hi", "ja", "ka", "kn", "ko", "ml",
    "mr", "or", "pa", "ru", "ta", "te", "th", "uk", "ur", "fa", "zh",
}


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


def detect_clause_language(
    clause_text: str,
    native_lang: str = "en",
    target_lang: str = "es",
    default_lang: Optional[str] = None,
) -> str:
    """Lightweight text language detector used by the UI `Translate` action in Free Talk."""
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

    t_pat = _DISTINCT_LANG_MARKERS.get(norm_target)
    n_pat = _DISTINCT_LANG_MARKERS.get(norm_native)
    target_markers = bool(t_pat and t_pat.search(clause_text))
    native_markers = bool(n_pat and n_pat.search(clause_text))
    if target_markers and not native_markers:
        return norm_target
    if native_markers and not target_markers:
        return norm_native

    words = clause_text.split()
    native_score = _score_language(words, norm_native)
    target_score = _score_language(words, norm_target)

    if norm_default == norm_native:
        if target_score >= 2 and native_score == 0:
            return norm_target
        if target_score >= 3 and target_score >= native_score * 3:
            return norm_target
        if native_score >= 1:
            return norm_native
        return norm_default

    if native_score >= 2 and target_score == 0:
        return norm_native
    if native_score >= 3 and native_score >= target_score * 3:
        return norm_native
    if target_score >= 1:
        return norm_target

    return norm_default
