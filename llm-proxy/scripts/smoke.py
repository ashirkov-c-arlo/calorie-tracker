#!/usr/bin/env python3
"""Smoke test against a running proxy. Uses the real Bedrock path, so it spends quota.

    python3 scripts/smoke.py --base-url https://kcal.example.net --api-key "$KEY"
    python3 scripts/smoke.py ... --photo ~/plate.jpg     # adds the vision case

stdlib only, no dependencies. Never send a real personal photo: use a throwaway image.
"""

from __future__ import annotations

import argparse
import base64
import json
import sys
import urllib.error
import urllib.request

PASS, FAIL = "PASS", "FAIL"
failures = 0


def call(base_url: str, path: str, body: dict | None, api_key: str, lang: str = "en",
         raw: bytes | None = None) -> tuple[int, dict | str]:
    payload = raw if raw is not None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(base_url.rstrip("/") + path, data=payload, method="POST")
    request.add_header("Content-Type", "application/json")
    request.add_header("Accept", "application/json")
    request.add_header("Accept-Language", lang)
    request.add_header("X-Api-Key", api_key)
    try:
        with urllib.request.urlopen(request, timeout=40) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        raw_body = error.read().decode("utf-8", "replace")
        try:
            return error.code, json.loads(raw_body)
        except json.JSONDecodeError:
            return error.code, raw_body


def check(name: str, ok: bool, detail: str = "") -> None:
    global failures
    if not ok:
        failures += 1
    print(f"{PASS if ok else FAIL}  {name}{('  ' + detail) if detail else ''}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--api-key", required=True)
    parser.add_argument("--photo", help="path to a small throwaway JPEG")
    args = parser.parse_args()

    with urllib.request.urlopen(args.base_url.rstrip("/") + "/healthz", timeout=10) as response:
        check("healthz", response.status == 200, json.load(response).get("status", ""))

    status, body = call(args.base_url, "/v1/nutrition/parse",
                        {"text": "180 g grilled chicken breast and 220 g boiled rice"}, args.api_key)
    ok = status == 200 and body.get("type") == "success" and len(body.get("items", [])) >= 1
    check("text EN", ok, f"{status} {json.dumps(body, ensure_ascii=False)[:160]}")

    status, body = call(args.base_url, "/v1/nutrition/parse",
                        {"text": "200 г творога 5% и 30 г грецких орехов"}, args.api_key, lang="ru")
    names = " ".join(item.get("name", "") for item in body.get("items", []))
    check("text RU is answered in Russian", status == 200 and any("\u0400" <= c <= "\u04ff" for c in names),
          f"{status} {names[:120]}")

    status, body = call(args.base_url, "/v1/nutrition/parse",
                        {"text": "I had some nuts"}, args.api_key)
    if body.get("type") == "clarification":
        status2, body2 = call(args.base_url, "/v1/nutrition/parse", {
            "text": "I had some nuts",
            "clarification": {"question": body["question"], "answer": "about 30 grams"},
        }, args.api_key)
        check("clarification round trip", status2 == 200 and body2.get("type") == "success",
              f"{status2} {body2.get('type')}")
    else:
        check("ambiguous input answered directly (acceptable)", status == 200, str(status))

    if args.photo:
        with open(args.photo, "rb") as handle:
            data = base64.b64encode(handle.read()).decode()
        status, body = call(args.base_url, "/v1/nutrition/parse", {
            "text": "my lunch plate",
            "image": {"media_type": "image/jpeg", "data_base64": data},
        }, args.api_key)
        check("text + photo", status == 200 and body.get("type") == "success",
              f"{status} {json.dumps(body, ensure_ascii=False)[:160]}")
    else:
        print("SKIP  text + photo (pass --photo)")

    status, body = call(args.base_url, "/v1/nutrition/parse", {"text": "x"}, "definitely-wrong-key")
    check("bad key -> 403 AUTH", (status, body) == (403, {"type": "error", "code": "AUTH"}), str(status))

    status, body = call(args.base_url, "/v1/nutrition/parse", {}, args.api_key)
    check("blank body -> 400 INVALID_REQUEST",
          (status, body) == (400, {"type": "error", "code": "INVALID_REQUEST"}), str(status))

    status, body = call(args.base_url, "/v1/nutrition/parse", None, args.api_key,
                        raw=json.dumps({"text": "x" * 2_000_000}).encode())
    check("2 MB body -> 413 PAYLOAD_TOO_LARGE",
          (status, body) == (413, {"type": "error", "code": "PAYLOAD_TOO_LARGE"}), str(status))

    status, body = call(args.base_url, "/v1/insights/generate", {"stats_version": 1}, args.api_key)
    check("insights -> 501 UNKNOWN",
          (status, body) == (501, {"type": "error", "code": "UNKNOWN"}), str(status))

    print(f"\n{failures} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
