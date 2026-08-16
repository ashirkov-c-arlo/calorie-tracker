"""Runtime configuration.

Everything comes from the environment, so no key, model id or endpoint lives in git.
The homelab deployment replaces SSM Parameter Store from the implementation plan: a
restart is the "hot swap", which is acceptable for a single-process service.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field


def _int(name: str, default: int) -> int:
    return int(os.environ.get(name, "").strip() or default)


def _float(name: str, default: float) -> float:
    return float(os.environ.get(name, "").strip() or default)


def _bool(name: str, default: bool) -> bool:
    raw = os.environ.get(name, "").strip().lower()
    return default if raw == "" else raw in ("1", "true", "yes", "on")


def _str(name: str, default: str = "") -> str:
    return os.environ.get(name, "").strip() or default


_DEFAULT_PRICES = {
    "anthropic.claude-haiku-4-5": (1.0, 5.0),
    "qwen.qwen3-vl-235b-a22b": (0.53, 2.66),
}


def _parse_price_table(raw: str) -> dict[str, tuple[float, float]]:
    """Parse PRICE_TABLE env: JSON {"model_prefix": [input, output]} or use defaults."""
    if not raw:
        return dict(_DEFAULT_PRICES)
    try:
        parsed = json.loads(raw)
        return {k: (float(v[0]), float(v[1])) for k, v in parsed.items()}
    except (json.JSONDecodeError, TypeError, IndexError, ValueError):
        return dict(_DEFAULT_PRICES)


@dataclass(frozen=True)
class Config:
    # --- transport -------------------------------------------------------------------
    host: str = "0.0.0.0"
    port: int = 8080
    # Comma separated. Contract §2: a routing/quota identifier, not authentication.
    api_keys: tuple[str, ...] = ()
    # Only enable behind a reverse proxy you control: a spoofed header defeats the per-IP cap.
    trust_forwarded_for: bool = False

    # --- kill switch -----------------------------------------------------------------
    enabled: bool = True

    # --- bedrock ---------------------------------------------------------------------
    region: str = "eu-west-1"
    model_text: str = ""
    model_vision: str = ""
    model_fallback: str = ""

    # --- published request limits (plan §8) ------------------------------------------
    # 1 400 000 Base64 chars cannot fit in a 1 MiB body, so the body limit is the one
    # that actually has to be larger; the decoded image stays at 1 MiB.
    max_body_bytes: int = 1_500_000
    max_base64_chars: int = 1_400_000
    max_image_bytes: int = 1_048_576
    max_text_chars: int = 1000
    max_clarification_chars: int = 400

    # --- time budget (plan §9.1) -----------------------------------------------------
    request_deadline_s: float = 24.0
    connect_timeout_s: float = 5.0
    read_timeout_s: float = 12.0
    repair_min_remaining_s: float = 6.0

    # --- quota (plan §11.2) ----------------------------------------------------------
    daily_request_cap: int = 100
    monthly_request_cap: int = 3000
    per_ip_daily_cap: int = 40
    rate_per_second: float = 2.0
    rate_burst: int = 5
    db_path: str = "/data/kcal_proxy.sqlite3"

    # --- inference -------------------------------------------------------------------
    max_tokens: int = 1024
    temperature: float = 0.2
    bedrock_max_attempts: int = 3

    # --- pricing (USD per 1M tokens) ------------------------------------------------
    # JSON: {"model_id": [input_price, output_price], ...}
    # Partial match: longest model_id prefix wins.
    price_table: dict[str, tuple[float, float]] = field(default_factory=dict)

    log_level: str = "info"
    extra: dict = field(default_factory=dict)

    @classmethod
    def from_env(cls) -> "Config":
        model_text = _str("MODEL_TEXT")
        cfg = cls(
            host=_str("HOST", "0.0.0.0"),
            port=_int("PORT", 8080),
            api_keys=tuple(k.strip() for k in _str("API_KEYS").split(",") if k.strip()),
            trust_forwarded_for=_bool("TRUST_FORWARDED_FOR", False),
            enabled=_bool("ENABLED", True),
            region=_str("AWS_REGION", "eu-west-1"),
            model_text=model_text,
            model_vision=_str("MODEL_VISION", model_text),
            model_fallback=_str("MODEL_FALLBACK"),
            max_body_bytes=_int("MAX_BODY_BYTES", 1_500_000),
            max_text_chars=_int("MAX_TEXT_CHARS", 1000),
            request_deadline_s=_float("REQUEST_DEADLINE_S", 24.0),
            daily_request_cap=_int("DAILY_REQUEST_CAP", 100),
            monthly_request_cap=_int("MONTHLY_REQUEST_CAP", 3000),
            per_ip_daily_cap=_int("PER_IP_DAILY_CAP", 40),
            rate_per_second=_float("RATE_PER_SECOND", 2.0),
            rate_burst=_int("RATE_BURST", 5),
            db_path=_str("DB_PATH", "/data/kcal_proxy.sqlite3"),
            price_table=_parse_price_table(_str("PRICE_TABLE")),
            log_level=_str("LOG_LEVEL", "info"),
        )
        if not cfg.api_keys:
            raise SystemExit("API_KEYS is required (comma separated)")
        if not cfg.model_text:
            raise SystemExit("MODEL_TEXT is required, see .env.example")
        return cfg
