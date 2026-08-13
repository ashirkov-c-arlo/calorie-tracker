"""Standalone HTTP entry point: `python -m kcal_proxy`.

Replaces API Gateway + Lambda from the implementation plan. Every response, including
every failure, is contract JSON — there is no other writer of the response body.

Put TLS in front of it (Caddy/nginx/Traefik): the contract requires HTTPS, and terminating
it in the reverse proxy the homelab already runs is less code than doing it here.
"""

from __future__ import annotations

import json
import secrets
import sys
import threading
import time
import traceback
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

import boto3
from botocore.config import Config as BotoConfig

from .config import Config
from .parse import Meta, ProxyError, run_parse, validate_request
from .prompt import PROMPT_VERSION, TOOLS_VERSION
from .quota import Quota, QuotaExceeded, RateLimiter, key_hash

PARSE_PATH = "/v1/nutrition/parse"
INSIGHTS_PATH = "/v1/insights/generate"
HEALTH_PATH = "/healthz"

# A stalled client must not hold a worker thread (or an unread body) open forever, and a
# connection flood must not spawn unbounded threads.
SOCKET_TIMEOUT_S = 20
MAX_CONNECTIONS = 64


def log(**fields: Any) -> None:
    """Allow-list logger: callers pass named metadata only, never a request body."""
    sys.stdout.write(json.dumps(fields, ensure_ascii=False, separators=(",", ":")) + "\n")
    sys.stdout.flush()


def resolve_language(header: str | None) -> str:
    """Accept-Language -> en | ru, English for anything else (contract §2)."""
    for part in (header or "").split(","):
        tag = part.split(";")[0].strip().lower()
        if tag.startswith("ru"):
            return "ru"
        if tag.startswith("en"):
            return "en"
    return "en"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "kcal-proxy"
    sys_version = ""
    timeout = SOCKET_TIMEOUT_S  # applied to the socket by StreamRequestHandler.setup

    # --- plumbing --------------------------------------------------------------------

    @property
    def cfg(self) -> Config:
        return self.server.cfg  # type: ignore[attr-defined]

    def log_message(self, *args: Any) -> None:  # noqa: D102 - silence the default access log
        pass

    def handle(self) -> None:
        """One thread per connection, but never more than MAX_CONNECTIONS of them."""
        if not self.server.slots.acquire(blocking=False):  # type: ignore[attr-defined]
            return
        try:
            super().handle()
        finally:
            self.server.slots.release()  # type: ignore[attr-defined]

    def _send(self, status: int, body: dict, retry_after: int | None = None) -> None:
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Request-Id", self.request_id)
        # One request per connection: an early response (auth, route, kill switch, rate
        # limit) leaves the request body unread, and reusing the socket would parse that
        # body as the next request. Closing is cheaper than draining it.
        self.send_header("Connection", "close")
        if retry_after is not None:
            self.send_header("Retry-After", str(retry_after))
        self.end_headers()
        self.wfile.write(payload)

    def _error(self, error: ProxyError) -> None:
        self._send(error.status, {"type": "error", "code": error.code}, error.retry_after)

    def _client_ip(self) -> str:
        if self.cfg.trust_forwarded_for:
            forwarded = self.headers.get("X-Forwarded-For")
            if forwarded:
                return forwarded.split(",")[-1].strip()
        return self.client_address[0]

    def _read_body(self) -> Any:
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError as exc:
            raise ProxyError(400, "INVALID_REQUEST", detail="bad Content-Length") from exc
        if length <= 0:
            raise ProxyError(400, "INVALID_REQUEST", detail="empty body")
        if length > self.cfg.max_body_bytes:
            raise ProxyError(413, "PAYLOAD_TOO_LARGE", detail="Content-Length too large")
        try:
            raw = self.rfile.read(length)
        except OSError as exc:  # includes the socket timeout of a stalled upload
            raise ProxyError(400, "INVALID_REQUEST", detail="body read failed") from exc
        try:
            return json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ProxyError(400, "INVALID_REQUEST", detail="body is not UTF-8 JSON") from exc

    def _authorize(self) -> str:
        presented = self.headers.get("X-Api-Key") or ""
        for key in self.cfg.api_keys:
            if secrets.compare_digest(presented, key):
                return key_hash(key)
        raise ProxyError(403, "AUTH", detail="unknown api key")

    # --- routes ----------------------------------------------------------------------

    def do_GET(self) -> None:  # noqa: N802
        self.request_id = uuid.uuid4().hex
        if self.path.split("?")[0] == HEALTH_PATH:
            self._send(200, {"status": "ok" if self.cfg.enabled else "disabled"})
        else:
            self._error(ProxyError(400, "INVALID_REQUEST", detail="unknown path"))

    def do_POST(self) -> None:  # noqa: N802
        started = time.monotonic()
        self.request_id = (self.headers.get("X-Request-Id") or "")[:64] or uuid.uuid4().hex
        path = self.path.split("?")[0]
        lang = resolve_language(self.headers.get("Accept-Language"))
        meta = Meta()
        reserved: list[str] = []
        result, code, detail = "error", "UNKNOWN", ""
        try:
            key_id = self._authorize()
            if path == INSIGHTS_PATH:
                raise ProxyError(501, "UNKNOWN", detail="insights reserved for v1.1")
            if path != PARSE_PATH:
                raise ProxyError(400, "INVALID_REQUEST", detail="unknown path")
            if not self.cfg.enabled:
                raise ProxyError(503, "UNKNOWN", 30, detail="kill switch")
            if not self.server.limiter.allow():  # type: ignore[attr-defined]
                raise ProxyError(429, "THROTTLED", 2, detail="local rate limit")

            request = validate_request(self._read_body(), self.cfg)
            try:
                reserved = self.server.quota.reserve(key_id, key_hash(self._client_ip()))  # type: ignore[attr-defined]
            except QuotaExceeded as exhausted:
                raise ProxyError(429, "QUOTA", exhausted.retry_after, detail=exhausted.scope) from exhausted

            body, meta = run_parse(self.server.bedrock, self.cfg, request, lang, meta)  # type: ignore[attr-defined]
            result, code = body["type"], ""
            self._send(200, body)
        except ProxyError as error:
            code, detail = error.code, error.detail
            # Once the model has answered, the call is paid for: keep the unit spent even
            # if a later step (repair, delivery) fails.
            if reserved and not meta.model_answered and not error.billable:
                self.server.quota.refund(reserved)  # type: ignore[attr-defined]
            self._error(error)
        except OSError as broken:  # the client vanished mid-response: nowhere to answer
            result, code = "error", "UNKNOWN"
            detail = f"delivery failed: {type(broken).__name__}"
            if reserved and not meta.model_answered:
                self.server.quota.refund(reserved)  # type: ignore[attr-defined]
            self.close_connection = True
        except Exception as unexpected:  # noqa: BLE001 - single funnel, contract body only
            frame = traceback.extract_tb(unexpected.__traceback__)[-1]
            code, detail = "UNKNOWN", f"{type(unexpected).__name__} at {frame.filename}:{frame.lineno}"
            if reserved and not meta.model_answered:
                self.server.quota.refund(reserved)  # type: ignore[attr-defined]
            self._error(ProxyError(500, "UNKNOWN"))
        finally:
            log(
                request_id=self.request_id,
                route=path,
                lang=lang,
                result=result,
                code=code,
                detail=detail,
                has_image=meta.image_bytes > 0,
                text_len=meta.text_len,
                image_bytes=meta.image_bytes,
                model_id=meta.model_id,
                prompt_version=PROMPT_VERSION,
                tools_version=TOOLS_VERSION,
                bedrock_attempts=meta.bedrock_attempts,
                repair_used=meta.repair_used,
                input_tokens=meta.input_tokens,
                output_tokens=meta.output_tokens,
                flags=meta.flags,
                latency_ms=int((time.monotonic() - started) * 1000),
            )


def build_server(cfg: Config, bedrock=None) -> ThreadingHTTPServer:
    server = ThreadingHTTPServer((cfg.host, cfg.port), Handler)
    server.daemon_threads = True
    server.cfg = cfg  # type: ignore[attr-defined]
    server.slots = threading.BoundedSemaphore(MAX_CONNECTIONS)  # type: ignore[attr-defined]
    server.limiter = RateLimiter(cfg.rate_per_second, cfg.rate_burst)  # type: ignore[attr-defined]
    server.quota = Quota(  # type: ignore[attr-defined]
        cfg.db_path, cfg.daily_request_cap, cfg.monthly_request_cap, cfg.per_ip_daily_cap
    )
    server.bedrock = bedrock or boto3.client(  # type: ignore[attr-defined]
        "bedrock-runtime",
        region_name=cfg.region,
        config=BotoConfig(
            connect_timeout=cfg.connect_timeout_s,
            read_timeout=cfg.read_timeout_s,
            retries={"max_attempts": 0},  # retries are owned by run_parse
        ),
    )
    return server


def main() -> None:
    cfg = Config.from_env()
    server = build_server(cfg)
    log(event="listening", host=cfg.host, port=cfg.port, region=cfg.region,
        prompt_version=PROMPT_VERSION, tools_version=TOOLS_VERSION, enabled=cfg.enabled)
    server.serve_forever()


if __name__ == "__main__":
    main()
