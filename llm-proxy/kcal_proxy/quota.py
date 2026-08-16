"""Request caps and rate limiting.

Replaces the plan's DynamoDB table and API Gateway usage plan. One SQLite file, one
table of counters, one lock: the caps only need to be exact within a single process.

ponytail: single-process only. Move the counters to Postgres/Redis if the proxy is ever
run with more than one worker.
"""

from __future__ import annotations

import hashlib
import sqlite3
import threading
import time
from calendar import monthrange
from datetime import datetime, timedelta, timezone

DAY_SECONDS = 86_400


class QuotaExceeded(Exception):
    def __init__(self, scope: str, retry_after: int) -> None:
        super().__init__(scope)
        self.scope = scope
        self.retry_after = retry_after


def key_hash(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:16]


def _seconds_to_utc_midnight(now: datetime) -> int:
    tomorrow = (now + timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
    return max(1, int((tomorrow - now).total_seconds()))


def _seconds_to_next_month(now: datetime) -> int:
    days = monthrange(now.year, now.month)[1]
    first = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0) + timedelta(days=days)
    return max(1, min(DAY_SECONDS, int((first - now).total_seconds())))


class RateLimiter:
    """Token bucket, the cheap stand-in for the gateway usage plan's rps/burst."""

    def __init__(self, per_second: float, burst: int, clock=time.monotonic) -> None:
        self._per_second = per_second
        self._burst = float(burst)
        self._clock = clock
        self._tokens = float(burst)
        self._updated = clock()
        self._lock = threading.Lock()

    def allow(self) -> bool:
        with self._lock:
            now = self._clock()
            self._tokens = min(self._burst, self._tokens + (now - self._updated) * self._per_second)
            self._updated = now
            if self._tokens < 1.0:
                return False
            self._tokens -= 1.0
            return True


class Quota:
    """Daily / monthly / per-IP request caps, counted only for billable calls."""

    def __init__(
        self,
        db_path: str,
        daily_cap: int,
        monthly_cap: int,
        per_ip_daily_cap: int,
        now=lambda: datetime.now(timezone.utc),
    ) -> None:
        self._conn = sqlite3.connect(db_path, check_same_thread=False)
        self._conn.execute(
            "CREATE TABLE IF NOT EXISTS counters (bucket TEXT PRIMARY KEY, n INTEGER NOT NULL DEFAULT 0)"
        )
        self._conn.execute(
            "CREATE TABLE IF NOT EXISTS usage_log ("
            "  id INTEGER PRIMARY KEY AUTOINCREMENT,"
            "  ts TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),"
            "  has_image INTEGER NOT NULL DEFAULT 0,"
            "  input_tokens INTEGER NOT NULL DEFAULT 0,"
            "  output_tokens INTEGER NOT NULL DEFAULT 0,"
            "  est_micro_usd INTEGER NOT NULL DEFAULT 0,"
            "  model_id TEXT NOT NULL DEFAULT ''"
            ")"
        )
        self._conn.commit()
        self._lock = threading.Lock()
        self._daily = daily_cap
        self._monthly = monthly_cap
        self._per_ip = per_ip_daily_cap
        self._now = now

    def _buckets(self, api_key_hash: str, ip_hash: str, now: datetime) -> list[tuple[str, int, int]]:
        day = now.strftime("%Y-%m-%d")
        month = now.strftime("%Y-%m")
        return [
            (f"d:{api_key_hash}:{day}", self._daily, _seconds_to_utc_midnight(now)),
            (f"m:{api_key_hash}:{month}", self._monthly, _seconds_to_next_month(now)),
            (f"ip:{ip_hash}:{day}", self._per_ip, _seconds_to_utc_midnight(now)),
        ]

    def reserve(self, api_key_hash: str, ip_hash: str) -> list[str]:
        """Reserve one billable request across all caps, or raise QuotaExceeded."""
        buckets = self._buckets(api_key_hash, ip_hash, self._now())
        with self._lock:
            cur = self._conn.execute(
                "SELECT bucket, n FROM counters WHERE bucket IN (?, ?, ?)",
                tuple(b for b, _, _ in buckets),
            )
            used = dict(cur.fetchall())
            for bucket, cap, retry_after in buckets:
                if cap >= 0 and used.get(bucket, 0) >= cap:
                    raise QuotaExceeded(bucket.split(":", 1)[0], retry_after)
            self._conn.executemany(
                "INSERT INTO counters (bucket, n) VALUES (?, 1) "
                "ON CONFLICT(bucket) DO UPDATE SET n = n + 1",
                [(b,) for b, _, _ in buckets],
            )
            self._conn.commit()
        return [b for b, _, _ in buckets]

    def refund(self, buckets: list[str]) -> None:
        """Best effort (plan §11.1): nothing was spent, so give the units back."""
        if not buckets:
            return
        with self._lock:
            self._conn.executemany(
                "UPDATE counters SET n = MAX(0, n - 1) WHERE bucket = ?", [(b,) for b in buckets]
            )
            self._conn.commit()

    def used(self, bucket: str) -> int:
        cur = self._conn.execute("SELECT n FROM counters WHERE bucket = ?", (bucket,))
        row = cur.fetchone()
        return row[0] if row else 0

    def record_usage(self, has_image: bool, input_tokens: int, output_tokens: int,
                     est_micro_usd: int, model_id: str) -> None:
        """Persist token usage for historical stats. Best effort."""
        with self._lock:
            self._conn.execute(
                "INSERT INTO usage_log (has_image, input_tokens, output_tokens, est_micro_usd, model_id) "
                "VALUES (?, ?, ?, ?, ?)",
                (int(has_image), input_tokens, output_tokens, est_micro_usd, model_id),
            )
            self._conn.commit()

    def avg_cost(self) -> dict:
        """Average cost per request, split by text vs photo."""
        cur = self._conn.execute(
            "SELECT has_image, COUNT(*), SUM(est_micro_usd) FROM usage_log GROUP BY has_image"
        )
        result = {"text": {"requests": 0, "avg_usd": "0.00000"},
                  "photo": {"requests": 0, "avg_usd": "0.00000"}}
        for has_image, count, total in cur.fetchall():
            key = "photo" if has_image else "text"
            avg = total / count / 1_000_000 if count else 0.0
            result[key] = {"requests": count, "avg_usd": f"{avg:.5f}"}
        return result

    def close(self) -> None:
        self._conn.close()
