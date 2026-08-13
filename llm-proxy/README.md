# Kcal LLM proxy

A standalone Python service that sits between the Kcal Android app and AWS Bedrock.
It implements `docs/llm-proxy-contract.md` (nutrition parse v1) and nothing else:
no accounts, no storage of user content, no nutrition arithmetic.

```text
Android app --HTTPS/JSON--> your reverse proxy (TLS) --HTTP--> kcal-proxy --> Bedrock Converse
```

## Why this is not the AWS deployment in the plan

`Kcal LLM Proxy — Implementation Plan.md` targets API Gateway + Lambda + DynamoDB + SSM +
CDK. This implementation is the same pipeline hosted on your own hardware, so the managed
pieces collapse into the process:

| Plan | Here |
|---|---|
| API Gateway REST + Gateway Responses | the service is the only writer of a response body, so every failure is already contract JSON |
| Lambda | one `ThreadingHTTPServer` process |
| DynamoDB quota table | SQLite file (`DB_PATH`), one row per counter |
| API Gateway usage plan (rps/burst) | in-process token bucket |
| SSM Parameter Store | environment variables; restart to change them |
| CDK, IAM roles, GitHub OIDC | `compose.yaml` and your AWS credential chain |
| CloudWatch EMF, alarms, dashboard, Budgets | one structured JSON log line per request on stdout |
| TLS | your reverse proxy (Caddy/nginx/Traefik) |

Deliberately **not** implemented: the price table and `est_micro_usd` (the hard request caps
already bound spend), Bedrock Guardrails (`GUARDRAIL_ID` was an inert hook), prompt caching,
and `/v1/insights/generate`, which answers `501` until its v1.1 contract is approved.

## Run it

```bash
cp .env.example .env      # set API_KEYS and MODEL_TEXT
docker compose up -d --build
curl -s localhost:8080/healthz
```

Without Docker:

```bash
pip install -r requirements.txt
API_KEYS=dev-key MODEL_TEXT=eu.anthropic.claude-haiku-4-5-20251001-v1:0 \
  DB_PATH=./kcal_proxy.sqlite3 python3 -m kcal_proxy
```

AWS credentials come from the standard boto3 chain (env vars, a mounted read-only
`~/.aws`, or an instance role). The IAM policy needs only `bedrock:InvokeModel` on the
model or inference-profile ARNs you configured.

The Android app expects HTTPS. Terminate TLS in the reverse proxy and point
`LLM_API_BASE_URL` in the app's `local.properties` at it (no trailing `/v1`):

```caddy
kcal.example.net {
    reverse_proxy 127.0.0.1:8080
}
```

Set `TRUST_FORWARDED_FOR=true` only when such a proxy is in front, otherwise a client can
spoof `X-Forwarded-For` and evade `PER_IP_DAILY_CAP`.

## Endpoints

| Method | Path | Behaviour |
|---|---|---|
| POST | `/v1/nutrition/parse` | contract §3–§6: text, or text + one transient JPEG |
| POST | `/v1/insights/generate` | `501 {"type":"error","code":"UNKNOWN"}` (reserved for v1.1) |
| GET | `/healthz` | `{"status":"ok"}` / `{"status":"disabled"}`, no API key required |

Every other path and every failure returns `{"type":"error","code":...}` with the status
from the contract table. `X-Api-Key` is compared in constant time; it is a routing and quota
identifier, not authentication.

## Configuration

See `.env.example`. The caps that actually bound the AWS bill:

| Variable | Default | Meaning |
|---|---:|---|
| `DAILY_REQUEST_CAP` | 100 | billable requests per API key per UTC day |
| `MONTHLY_REQUEST_CAP` | 3000 | billable requests per API key per UTC month |
| `PER_IP_DAILY_CAP` | 40 | so one leaked key cannot burn the whole day |
| `RATE_PER_SECOND` / `RATE_BURST` | 2 / 5 | token bucket, `429 THROTTLED` |
| `ENABLED` | true | kill switch: `false` answers `503` and the app falls back to manual logging |

Only requests that reach the model are counted; validation failures, auth failures and
kill-switch refusals are free. A throttle, a 503 or a deadline hit before any model output
refunds the unit. A clarification round trip costs two units, as designed.

## Pipeline

`kcal_proxy/parse.py`, in order: validate the request (L0) → Converse with
`toolChoice = any` (retry only throttling and transient 5xx, at most 2 extra attempts, one
optional fallback model) → extract exactly one `toolUse` → normalize numbers → hard-validate
→ at most **one** repair turn → sanitize output (markdown, URLs, contacts, length, prompt-leak
canary, aggregate output-language check) → contract JSON.

Soft findings (`kcal > 5000`, 4/9/4 energy mismatch, low confidence) never fail the request:
they appear as `flags` in the log line, and the app shows an editable "needs review" draft.

The system prompt lives in `kcal_proxy/PROMPT.md` and is the same text `run_eval.py`
evaluates — a test fails if the two drift. Review prompt changes like a formula change.

## Logging and privacy

One JSON line per request, stdout, allow-listed fields only:

```json
{"request_id":"…","route":"/v1/nutrition/parse","lang":"ru","result":"success","code":"",
 "has_image":false,"text_len":38,"image_bytes":0,"model_id":"…","prompt_version":"parse-v1",
 "bedrock_attempts":1,"repair_used":false,"input_tokens":420,"output_tokens":96,
 "flags":[],"latency_ms":2840}
```

There is no verbose mode that dumps bodies: user text, clarification answers, image bytes
and model output are never passed to the logger. Keep Bedrock model invocation logging
disabled in your account — enabling it would export prompts and images to S3/CloudWatch.

## Tests

```bash
python3 -m unittest discover -s tests -t .        # 55 tests, no network
python3 scripts/smoke.py --base-url https://… --api-key "$KEY" [--photo plate.jpg]
python3 run_eval.py --csv eval-text-cases.csv     # model selection, needs AWS creds
```

The contract tests assert that responses are byte-identical to the fixtures the Android repo
already ships in `app/src/test/resources/llm/`, and that the synthetic negative fixtures
(`parse_empty_items.json`, `parse_invalid_schema.json`) stay hard-invalid — the proxy can
never emit them.
