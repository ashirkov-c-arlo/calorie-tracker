"""Request validation, the Bedrock Converse call, and the contract response.

Order (plan §9): validate -> converse -> extract toolUse -> normalize -> validate ->
at most one repair -> sanitize -> contract JSON. No nutrition arithmetic happens here;
the app owns every calculation.
"""

from __future__ import annotations

import base64
import binascii
import math
import random
import re
import time
import unicodedata
from dataclasses import dataclass, field
from typing import Any

from botocore.exceptions import ClientError, ConnectTimeoutError, ReadTimeoutError

from .config import Config
from .prompt import CANARY, MAX_ITEMS, MAX_QUESTION_CHARS, build_messages, build_system, tool_config


class ProxyError(Exception):
    """The only way a failure reaches the client: contract §6 status + code."""

    def __init__(self, status: int, code: str, retry_after: int | None = None, detail: str = "") -> None:
        super().__init__(f"{status} {code} {detail}".strip())
        self.status = status
        self.code = code
        self.retry_after = retry_after
        self.detail = detail  # logged, never returned

    @property
    def billable(self) -> bool:
        """CONTENT_BLOCKED/INVALID_RESPONSE mean the model ran and money was spent."""
        return self.code in ("CONTENT_BLOCKED", "INVALID_RESPONSE")


@dataclass
class ParseRequest:
    text: str
    image_bytes: bytes | None = None
    question: str = ""
    answer: str = ""


@dataclass
class Meta:
    """Log allow-list. Structurally cannot hold user text or image bytes."""

    text_len: int = 0
    image_bytes: int = 0
    model_id: str = ""
    bedrock_attempts: int = 0
    # True as soon as the model returned anything: the request is paid for and the
    # reserved quota unit must never be refunded, whatever happens afterwards.
    model_answered: bool = False
    repair_used: bool = False
    input_tokens: int = 0
    output_tokens: int = 0
    flags: list[str] = field(default_factory=list)


# --------------------------------------------------------------------------------------
# L0: request validation
# --------------------------------------------------------------------------------------

_CONTROL = {c for c in map(chr, range(0x20)) if c != "\n"} | {"\x7f"}


def _clean_text(raw: str) -> str:
    text = unicodedata.normalize("NFC", raw).replace("\r\n", "\n").replace("\r", "\n")
    return "".join(c for c in text if c not in _CONTROL)


def _bad_request(detail: str) -> ProxyError:
    return ProxyError(400, "INVALID_REQUEST", detail=detail)


def _required_text(body: dict, key: str, limit: int) -> str:
    value = body.get(key)
    if not isinstance(value, str):
        raise _bad_request(f"{key} missing or not a string")
    value = _clean_text(value)
    if not value.strip():
        raise _bad_request(f"{key} blank")
    if len(value) > limit:
        raise _bad_request(f"{key} longer than {limit}")
    return value


def validate_request(body: Any, cfg: Config) -> ParseRequest:
    if not isinstance(body, dict):
        raise _bad_request("body is not an object")
    req = ParseRequest(text=_required_text(body, "text", cfg.max_text_chars))

    if "image" in body:  # contract §3.3: omit the key, never send null
        image = body["image"]
        if not isinstance(image, dict):
            raise _bad_request("image is not an object")
        if image.get("media_type") != "image/jpeg":
            raise _bad_request("image.media_type must be image/jpeg")
        data = image.get("data_base64")
        if not isinstance(data, str) or not data.strip():
            raise _bad_request("image.data_base64 missing")
        if len(data) > cfg.max_base64_chars:
            raise ProxyError(413, "PAYLOAD_TOO_LARGE", detail="base64 too long")
        try:
            decoded = base64.b64decode(data, validate=True)
        except (binascii.Error, ValueError) as exc:
            raise _bad_request("image.data_base64 is not standard Base64") from exc
        if not decoded:
            raise _bad_request("image decodes to nothing")
        if len(decoded) > cfg.max_image_bytes:
            raise ProxyError(413, "PAYLOAD_TOO_LARGE", detail="image too large")
        if not (decoded.startswith(b"\xff\xd8\xff") and decoded.rstrip(b"\x00").endswith(b"\xff\xd9")):
            raise _bad_request("image is not a JPEG")
        req.image_bytes = decoded

    if "clarification" in body:
        clarification = body["clarification"]
        if not isinstance(clarification, dict):
            raise _bad_request("clarification is not an object")
        req.question = _required_text(clarification, "question", cfg.max_clarification_chars)
        req.answer = _required_text(clarification, "answer", cfg.max_clarification_chars)
    return req


# --------------------------------------------------------------------------------------
# Bedrock invocation and error mapping (plan §10.1)
# --------------------------------------------------------------------------------------

_RETRYABLE = {
    "ThrottlingException",
    "TooManyRequestsException",
    "ServiceUnavailableException",
    "ModelNotReadyException",
    "InternalServerException",
    "ModelTimeoutException",
}

_ERROR_MAP: dict[str, tuple[int, str, int | None]] = {
    "ThrottlingException": (429, "THROTTLED", 2),
    "TooManyRequestsException": (429, "THROTTLED", 2),
    "ServiceQuotaExceededException": (429, "QUOTA", None),
    "ModelTimeoutException": (504, "TIMEOUT", None),
    "ServiceUnavailableException": (503, "UNKNOWN", 5),
    "ModelNotReadyException": (503, "UNKNOWN", 5),
    "InternalServerException": (503, "UNKNOWN", 5),
    "ModelStreamErrorException": (503, "UNKNOWN", 5),
}

_BLOCKED_PHRASES = ("content filter", "blocked by", "safety", "responsible ai", "guardrail")


def map_client_error(exc: ClientError) -> ProxyError:
    code = exc.response.get("Error", {}).get("Code", "Unknown")
    message = exc.response.get("Error", {}).get("Message", "").lower()
    if code == "ValidationException" and any(p in message for p in _BLOCKED_PHRASES):
        return ProxyError(422, "CONTENT_BLOCKED", detail=code)
    status, contract, retry = _ERROR_MAP.get(code, (500, "UNKNOWN", None))
    return ProxyError(status, contract, retry, detail=code)


def _attempt_budget(cfg: Config) -> float:
    """Worst case for one Converse attempt: botocore timeouts are per client, not per call."""
    return cfg.connect_timeout_s + cfg.read_timeout_s


def _converse(client, cfg: Config, model_id: str, system, messages, tools, deadline: float, meta: Meta):
    last: ProxyError | None = None
    # Never start an attempt that could outlive the deadline (and the app's 30 s limit).
    budget = _attempt_budget(cfg)
    for attempt in range(1, cfg.bedrock_max_attempts + 1):
        if time.monotonic() + budget > deadline:
            raise last or ProxyError(504, "TIMEOUT", detail="deadline before attempt")
        # Last attempt may switch to the configured fallback model.
        used_model = model_id
        if attempt == cfg.bedrock_max_attempts and cfg.model_fallback and last is not None:
            used_model = cfg.model_fallback
        meta.model_id = used_model
        meta.bedrock_attempts += 1
        try:
            response = client.converse(
                modelId=used_model,
                system=system,
                messages=messages,
                inferenceConfig={"maxTokens": cfg.max_tokens, "temperature": cfg.temperature},
                toolConfig=tools,
            )
        except ClientError as exc:
            error = map_client_error(exc)
            code = exc.response.get("Error", {}).get("Code", "Unknown")
            if code not in _RETRYABLE:
                raise error from exc
            last = error
        except (ReadTimeoutError, ConnectTimeoutError) as exc:
            last = ProxyError(504, "TIMEOUT", detail="socket timeout")
            if attempt >= cfg.bedrock_max_attempts:
                raise last from exc
        else:
            meta.model_answered = True
            return response
        if attempt < cfg.bedrock_max_attempts:
            backoff = 0.3 * (3 ** (attempt - 1)) + random.uniform(0, 0.2)
            if time.monotonic() + backoff >= deadline:
                raise last
            time.sleep(backoff)
    raise last or ProxyError(500, "UNKNOWN", detail="no attempt ran")


# --------------------------------------------------------------------------------------
# Normalization and hard validation (plan §9.2, §9.3)
# --------------------------------------------------------------------------------------

def as_number(value: Any) -> float | None:
    if isinstance(value, bool) or value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value) if math.isfinite(float(value)) else None
    if isinstance(value, str):
        try:
            number = float(value.strip().replace(",", "."))
        except ValueError:
            return None
        return number if math.isfinite(number) else None
    return None


def normalize_log_food(payload: Any) -> tuple[list[dict], str | None, list[str]]:
    """Returns (items, note, problems). A non-empty problems list is hard-invalid."""
    problems: list[str] = []
    if not isinstance(payload, dict):
        return [], None, ["tool input is not an object"]
    raw_items = payload.get("items")
    if not isinstance(raw_items, list) or not raw_items:
        return [], None, ["items missing or empty"]

    items: list[dict] = []
    for index, raw in enumerate(raw_items):
        if not isinstance(raw, dict):
            problems.append(f"items[{index}] is not an object")
            continue
        name = raw.get("name")
        if not isinstance(name, str) or not name.strip():
            problems.append(f"items[{index}].name must be a non-blank string")
            name = ""
        name = name.strip()
        kcal = as_number(raw.get("kcal"))
        if kcal is None or kcal < 0:
            problems.append(f"items[{index}].kcal must be a non-negative number")
            kcal = 0.0
        elif kcal != round(kcal):
            problems.append(f"items[{index}].kcal must be a whole number")
        macros: dict[str, float] = {}
        for key in ("protein_g", "fat_g", "carbs_g"):
            number = as_number(raw.get(key))
            if number is None or number < 0:
                problems.append(f"items[{index}].{key} must be a non-negative number")
                number = 0.0
            macros[key] = round(number, 1)
        confidence = as_number(raw.get("confidence"))
        if confidence is None or not 0.0 <= confidence <= 1.0:
            problems.append(f"items[{index}].confidence must be between 0 and 1")
            confidence = min(1.0, max(0.0, confidence or 0.0))
        # A missing key and a value the schema forbids are both invalid; only an explicit
        # null means "mass cannot be estimated".
        raw_grams = raw.get("grams")
        grams = None if raw_grams is None else as_number(raw_grams)
        if "grams" not in raw or (raw_grams is not None and (grams is None or grams < 0)):
            problems.append(f"items[{index}].grams must be a non-negative number or null")
            grams = None
        items.append({
            "name": name,
            "grams": None if grams is None else round(grams, 1),
            "kcal": round(kcal),
            **macros,
            "confidence": round(confidence, 2),
        })

    merged: dict[str, dict] = {}
    for item in items:
        key = item["name"].casefold()
        if key in merged:
            target = merged[key]
            target["kcal"] += item["kcal"]
            for macro in ("protein_g", "fat_g", "carbs_g"):
                target[macro] = round(target[macro] + item[macro], 1)
            # A merged mass is only meaningful when every part is known, and the merged
            # confidence is the weakest one, otherwise the order of the items decides.
            known = target["grams"] is not None and item["grams"] is not None
            target["grams"] = round(target["grams"] + item["grams"], 1) if known else None
            target["confidence"] = min(target["confidence"], item["confidence"])
        else:
            merged[key] = dict(item)

    note = payload.get("note")
    if "note" not in payload or not isinstance(note, (str, type(None))):
        problems.append("note must be a string or null")
    note = None if note in (None, "", "null") else str(note).strip() or None
    if len(merged) > MAX_ITEMS:
        problems.append(f"return at most {MAX_ITEMS} items, merging duplicates")
    return list(merged.values()), note, problems


def normalize_ask_clarification(payload: Any, already_answered: bool) -> tuple[str, list[str]]:
    """Returns (question, problems), the ask_clarification counterpart of normalize_log_food.

    Shared with run_eval.py so a model can never pass the eval on an answer production
    would reject.
    """
    if already_answered:
        # One question per entry: the answer is already in this request.
        return "", ["the clarification is already answered, call log_food with your best assumption"]
    question = payload.get("question") if isinstance(payload, dict) else None
    if not isinstance(question, str) or len(question.strip()) < 3:
        return "", ["question must be a non-blank sentence"]
    question = question.strip()
    if len(question) > MAX_QUESTION_CHARS:
        return question, [f"question must be at most {MAX_QUESTION_CHARS} characters"]
    return question, []


# --------------------------------------------------------------------------------------
# L3 output sanitizer (plan §9.5)
# --------------------------------------------------------------------------------------

_MARKDOWN_RE = re.compile(r"(```+|\*\*|__|^\s*#{1,6}\s+)", re.MULTILINE)
_CONTACT_RE = re.compile(
    r"(https?://\S+|www\.\S+|\S+@\S+\.\w+|(?<!\d)\+?\d[\d\s().-]{7,}\d(?!\d))",
    re.IGNORECASE,
)
_CYRILLIC_RE = re.compile(r"[\u0400-\u04FF]")
_LATIN_RE = re.compile(r"[A-Za-z]")
_LETTER_RE = re.compile(r"[^\W\d_]", re.UNICODE)
_LEAK_PHRASES = (CANARY, "OUTPUT CONTRACT", "PORTION ESTIMATION", "system prompt", "системный промпт")


def sanitize_string(value: str, limit: int) -> str:
    cleaned = _CONTACT_RE.sub(" ", _MARKDOWN_RE.sub(" ", value))
    cleaned = re.sub(r"\s+", " ", cleaned).strip(" -–—:;,")
    if len(cleaned) <= limit:
        return cleaned
    head = cleaned[:limit].rsplit(" ", 1)[0]
    return (head or cleaned[:limit]).rstrip(" -–—:;,")


def sanitize_payload(tool: str, items: list[dict], note: str | None, question: str) -> tuple[list[dict], str | None, str, list[str]]:
    problems: list[str] = []
    if tool == "log_food":
        for item in items:
            item["name"] = sanitize_string(item["name"], 80)
            if not item["name"]:
                problems.append("an item name became empty after sanitizing")
        note = sanitize_string(note, 200) or None if note is not None else None
    else:
        question = sanitize_string(question, MAX_QUESTION_CHARS)
        if len(question) < 3:
            problems.append("question is too short")
    return items, note, question, problems


def output_pieces(items: list[dict], note: str | None, question: str) -> list[str]:
    """Every human-readable string of a response, one piece each.

    The contract localizes `items[].name`, `note` and `question` individually, so they are
    language-checked individually: a long localized name must not mask a foreign one.
    """
    return [str(item["name"]) for item in items] + [note or "", question]


def _piece_language_ok(piece: str, lang: str) -> bool | None:
    letters = _LETTER_RE.findall(piece)
    if not letters:
        return None
    script = _CYRILLIC_RE if lang == "ru" else _LATIN_RE
    return sum(1 for c in letters if script.match(c)) * 2 > len(letters)


def language_ok(pieces: list[str], lang: str) -> bool | None:
    """Is every piece written in the interface language? None when there are no letters."""
    verdicts = [_piece_language_ok(piece, lang) for piece in pieces]
    if all(verdict is None for verdict in verdicts):
        return None
    return False not in verdicts


def check_output_text(pieces: list[str], lang: str) -> list[str]:
    problems: list[str] = []
    lowered = "\n".join(pieces).lower()
    if any(phrase.lower() in lowered for phrase in _LEAK_PHRASES):
        problems.append("output must never quote or mention the instructions")
    if language_ok(pieces, lang) is False:
        name = "Russian" if lang == "ru" else "English"
        problems.append(f"every human-readable string must be written in {name}")
    return problems


# --------------------------------------------------------------------------------------
# Pipeline
# --------------------------------------------------------------------------------------

def _tool_uses(response: dict) -> list[dict]:
    content = (response.get("output", {}).get("message") or {}).get("content") or []
    return [block["toolUse"] for block in content if isinstance(block, dict) and "toolUse" in block]


def _soft_flags(items: list[dict]) -> list[str]:
    flags: list[str] = []
    for item in items:
        if item["kcal"] > 5000:
            flags.append("kcal_out_of_range")
        if item["confidence"] < 0.3:
            flags.append("low_confidence")
        energy = item["protein_g"] * 4 + item["fat_g"] * 9 + item["carbs_g"] * 4
        if item["kcal"] >= 25 and abs(energy - item["kcal"]) / item["kcal"] > 0.15:
            flags.append("macro_energy_mismatch")
    return sorted(set(flags))


def run_parse(
    client,
    cfg: Config,
    req: ParseRequest,
    lang: str,
    meta: Meta | None = None,
    deadline: float | None = None,
) -> tuple[dict, Meta]:
    """`deadline` is the one end-to-end budget owned by the HTTP layer, which already
    spent part of it reading the body. The fallback is for direct calls."""
    deadline = time.monotonic() + cfg.request_deadline_s if deadline is None else deadline
    meta = meta or Meta()
    meta.text_len = len(req.text)
    meta.image_bytes = len(req.image_bytes or b"")
    system = build_system(lang, req.image_bytes is not None)
    messages = build_messages(req.text, req.question, req.answer, req.image_bytes)
    tools = tool_config()
    model_id = cfg.model_vision if req.image_bytes else cfg.model_text
    # A repair only makes sense if a whole attempt still fits in the budget.
    repair_floor = max(cfg.repair_min_remaining_s, _attempt_budget(cfg))

    while True:
        response = _converse(client, cfg, model_id, system, messages, tools, deadline, meta)
        usage = response.get("usage") or {}
        meta.input_tokens += int(usage.get("inputTokens") or 0)
        meta.output_tokens += int(usage.get("outputTokens") or 0)

        stop_reason = response.get("stopReason", "")
        if stop_reason in ("guardrail_intervened", "content_filtered"):
            raise ProxyError(422, "CONTENT_BLOCKED", detail=stop_reason)

        problems: list[str] = []
        uses = _tool_uses(response)
        tool = uses[0].get("name", "") if uses else ""
        payload = uses[0].get("input") if uses else None
        items: list[dict] = []
        note: str | None = None
        question = ""

        if len(uses) != 1:
            problems.append("respond with exactly one tool call")
        elif tool not in ("log_food", "ask_clarification"):
            problems.append("call log_food or ask_clarification")
        elif stop_reason == "max_tokens":
            problems.append("the tool call was truncated, answer with fewer items")
        elif tool == "log_food":
            items, note, problems = normalize_log_food(payload)
        else:
            question, problems = normalize_ask_clarification(payload, bool(req.question))

        if not problems:
            items, note, question, problems = sanitize_payload(tool, items, note, question)
            problems += check_output_text(output_pieces(items, note, question), lang)

        if not problems:
            meta.flags = _soft_flags(items) if tool == "log_food" else []
            body: dict[str, Any] = (
                {"type": "success", "items": items, "note": note}
                if tool == "log_food"
                else {"type": "clarification", "question": question}
            )
            if meta.input_tokens or meta.output_tokens:
                body["usage"] = {
                    "input_tokens": meta.input_tokens,
                    "output_tokens": meta.output_tokens,
                }
            return body, meta

        remaining = deadline - time.monotonic()
        if meta.repair_used or remaining < repair_floor:
            raise ProxyError(502, "INVALID_RESPONSE", detail="; ".join(problems[:3]))

        meta.repair_used = True  # set before the attempt, so a failed repair is still logged
        complaint = "Validation failed: " + "; ".join(problems[:5]) + ". Call the tool again with corrected values only."
        tool_use_ids = [use["toolUseId"] for use in uses if use.get("toolUseId")]
        if tool_use_ids:
            # Converse requires one toolResult per toolUse block in the answered message.
            messages = messages + [
                response["output"]["message"],
                {"role": "user", "content": [
                    {"toolResult": {"toolUseId": tool_use_id, "status": "error",
                                    "content": [{"text": complaint}]}}
                    for tool_use_id in tool_use_ids
                ]},
            ]
        else:
            # No tool block means there is no toolUseId to answer: restate the demand.
            messages = messages + [
                {"role": "assistant", "content": [{"text": "I will call exactly one tool."}]},
                {"role": "user", "content": [{"text": complaint}]},
            ]
