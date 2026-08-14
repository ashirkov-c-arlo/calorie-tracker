# Kcal LLM Proxy Contract

Status: **nutrition parse v1 approved; insights reserved for v1.1**.

This document is the normative HTTP contract between the Android app and a future thin
LLM proxy. The proxy implementation is outside this repository. The app never calls AWS
Bedrock directly and never contains AWS credentials.

The keywords **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative.

---

## 1. Architecture and scope

```text
Android app
    -> HTTPS/JSON proxy
    -> AWS Bedrock Converse API
```

The v1 contract defines food parsing from:

- non-blank text;
- non-blank text plus one transient JPEG image.

Photo-only parsing is not supported. The insights path is reserved in §10 but its payload
is deliberately deferred until the v1.1 `BuildPeriodStats` model is finalized.

---

## 2. Base URL and common headers

The base URL comes from `BuildConfig.LLM_API_BASE_URL`. No real URL is committed.

Every request uses HTTPS and sends:

```http
Content-Type: application/json
Accept: application/json
Accept-Language: en | ru
X-Api-Key: <BuildConfig.LLM_API_KEY>
```

Rules:

- `Accept-Language` is the selected interface language, regardless of the input language.
- The proxy MUST return all human-readable fields in that language.
- Schema keys and enum values remain English.
- `X-Api-Key` is an extractable routing/quota identifier, not user authentication or a
  security boundary.
- Successful and contract-defined error responses use UTF-8 JSON.

---

## 3. `POST /v1/nutrition/parse`

### 3.1 Text request

```json
{
  "text": "omelette with three eggs and cheese"
}
```

### 3.2 Text and image request

```json
{
  "text": "chicken with rice",
  "image": {
    "media_type": "image/jpeg",
    "data_base64": "<standard-base64-without-data-url-prefix>"
  }
}
```

### 3.3 Clarification follow-up request

The proxy is stateless. After a `type = clarification` response, the app resends the
original text, optional image, and explicit question/answer context:

```json
{
  "text": "chicken with rice",
  "image": {
    "media_type": "image/jpeg",
    "data_base64": "<standard-base64-without-data-url-prefix>"
  },
  "clarification": {
    "question": "Approximately how large was the serving?",
    "answer": "About 250 grams"
  }
}
```

### 3.4 Request fields

| Field | Type | Required | Rules |
|---|---|---:|---|
| `text` | string | yes | Must contain at least one non-whitespace Unicode character |
| `image` | object | no | Omit for text-only input; do not send `null` |
| `image.media_type` | string | with image | Exactly `image/jpeg` in v1 |
| `image.data_base64` | string | with image | Standard Base64, no `data:` prefix, decodes to non-empty JPEG bytes |
| `clarification` | object | no | Send only as a response to the immediately preceding clarification |
| `clarification.question` | string | with clarification | Exact non-blank question returned by the proxy |
| `clarification.answer` | string | with clarification | Non-blank user answer |

The app MUST send the user's UTF-8 text and clarification answer unchanged. It may trim
only to validate that they are non-blank. No server-side conversation ID or session state
is required.

Before encoding an image, the app MUST:

1. downscale it to at most 1024 px on the long edge;
2. encode it as JPEG at quality 80;
3. strip metadata by re-encoding;
4. keep it only in temporary cache for the active request/retry flow.

The proxy MUST accept an image produced by those rules or publish a smaller hard limit
before implementation. A request exceeding the deployed transport limit returns `413`.

---

## 4. Successful parse response

HTTP status: `200 OK`.

```json
{
  "type": "success",
  "items": [
    {
      "name": "Chicken breast",
      "grams": 180.0,
      "kcal": 297,
      "protein_g": 55.8,
      "fat_g": 6.5,
      "carbs_g": 0.0,
      "confidence": 0.91
    }
  ],
  "summary": "chicken breast with boiled rice",
  "note": null,
  "usage": {
    "input_tokens": 420,
    "output_tokens": 96
  }
}
```

### 4.1 Response fields

| Field | Type | Required | Rules |
|---|---|---:|---|
| `type` | string | yes | Exactly `success` |
| `items` | array | yes | At least one item |
| `items[].name` | string | yes | Non-blank; localized to `Accept-Language` |
| `items[].grams` | number or null | yes | Null only when mass cannot be estimated |
| `items[].kcal` | integer | yes | Non-negative |
| `items[].protein_g` | number | yes | Non-negative |
| `items[].fat_g` | number | yes | Non-negative |
| `items[].carbs_g` | number | yes | Non-negative |
| `items[].confidence` | number | yes | Inclusive range `0.0..1.0` |
| `summary` | string or null | yes | One line naming the meal, at most 10 words; localized to `Accept-Language` |
| `note` | string or null | yes | Localized to `Accept-Language` |
| `usage` | object | no | Omitted when the provider does not return usage |
| `usage.input_tokens` | integer | with usage | Non-negative |
| `usage.output_tokens` | integer | with usage | Non-negative |

JSON numbers MUST be finite. The proxy MUST NOT emit `NaN`, positive infinity, or negative
infinity.

The proxy enforces schema validity. The app independently validates the response. App-side
sanity bounds such as `kcal > 5000` remain editable `needs review` warnings rather than
transport failures.

`summary` is display-only: it names the meal in the app's journal and never carries numbers.
The proxy caps it at 10 words and collapses whitespace, so the app can render it on one line.
Because nothing numeric depends on it, a response whose `summary` is missing or unusable stays
a `success` with `summary = null`, and the app falls back to listing item names.

---

## 5. Clarification response

HTTP status: `200 OK`.

```json
{
  "type": "clarification",
  "question": "Approximately how large was the serving?",
  "usage": {
    "input_tokens": 210,
    "output_tokens": 24
  }
}
```

| Field | Type | Required | Rules |
|---|---|---:|---|
| `type` | string | yes | Exactly `clarification` |
| `question` | string | yes | Non-blank; localized to `Accept-Language` |
| `usage` | object | no | Same shape as §4 |

A clarification response contains no `items` and is never persisted as a meal.

---

## 6. Error response

All contract-defined non-2xx responses use:

```json
{
  "type": "error",
  "code": "THROTTLED"
}
```

No raw AWS/provider message, prompt, model identifier, stack trace, or user input is
returned.

| HTTP | `code` | Meaning | Automatic proxy retry |
|---:|---|---|---:|
| 400 | `INVALID_REQUEST` | Request violates §3 | no |
| 401/403 | `AUTH` | Missing or rejected API key | no |
| 413 | `PAYLOAD_TOO_LARGE` | Deployed request limit exceeded | no |
| 422 | `CONTENT_BLOCKED` | Provider refused the content | no |
| 429 | `THROTTLED` | Temporary provider/proxy throttling | yes |
| 429 | `QUOTA` | Configured cost quota exhausted | no |
| 500/501 | `UNKNOWN` | Unclassified failure or reserved endpoint | no |
| 502 | `INVALID_RESPONSE` | Provider output remained invalid after one repair | no |
| 503 | `UNKNOWN` | Temporary upstream unavailability | yes |
| 504 | `TIMEOUT` | Upstream request exceeded its budget | yes |

For `429` or `503`, the proxy SHOULD include a standard `Retry-After` header when known.
The proxy owns Bedrock retries: exponential backoff with jitter, at most two retries, all
inside the request timeout. The Android app MUST NOT stack another automatic retry loop;
it offers an explicit Retry action instead. `NO_NETWORK` is an app-local failure and is
never returned by the proxy.

A gateway or network failure may not contain contract JSON. The app maps an unknown or
unparseable error body to `UNKNOWN` without displaying its raw contents.

---

## 7. Backend structured-output requirements

The future proxy MUST:

1. use the Bedrock Converse API, never raw model-specific `InvokeModel` payloads;
2. declare the versioned `log_food` and `ask_clarification` tools;
3. require exactly one tool call with `toolChoice = any`;
4. accept exactly one known tool-use block;
5. validate the tool input;
6. make at most one repair attempt for malformed or hard-invalid tool input;
7. wrap valid tool input in the `type`-discriminated app response from §4 or §5;
8. map service errors to §6.

Tool input shapes:

```text
log_food:
{
  items: [{ name, grams, kcal, protein_g, fat_g, carbs_g, confidence }],
  summary,
  note
}

ask_clarification:
{
  question
}
```

Model ID, region, prompt text, and prompt version are backend configuration and never
appear in app requests or responses for nutrition parsing.

---

## 8. Privacy and transient images

The Android app MUST NOT persist selected/captured photos in Room or meal records.
Temporary local files are retained only while the request or explicit Retry UI remains
active. They are deleted:

- immediately after a final `type = success` response;
- when the user cancels or leaves the entry flow;
- on next startup if a crash left stale cache files.

A `type = clarification` response keeps the photo only for the active clarification flow,
so the app can resubmit it with the user's answer.

The future proxy MUST NOT persist or log request bodies, user text, or decoded image bytes.
API Gateway/Lambda body logging must be disabled. Operational logs may contain only
non-sensitive metadata such as status code, latency, retry count, and token counts.

---

## 9. Timeouts and compatibility

- Android connect timeout: 10 seconds.
- End-to-end Android request timeout: 30 seconds.
- The proxy keeps its Bedrock retry budget below the client timeout.
- Clients ignore unknown JSON fields.
- The proxy may add optional fields without changing the URL version.
- Removing fields, changing required-field semantics, or changing enum meanings requires
  `/v2`.
- Unknown response `type` or error `code` maps to `UNKNOWN`.

---

## 10. Reserved insights endpoint

```http
POST /v1/insights/generate
```

The path is reserved for v1.1. The app MUST NOT call it until a normative request schema
based on the finalized local `BuildPeriodStats` model is added to this document. No
arithmetic will be delegated to the model. Until then, a proxy implementation SHOULD
return `501 Not Implemented` with `{ "type": "error", "code": "UNKNOWN" }`.

This deferral avoids freezing a speculative statistics schema before History is complete.

---

## 11. Contract fixtures

Before the proxy exists, examples in this document are authoritative. App tests should use
committed JSON fixtures matching this contract. Once the proxy is implemented, captured
and scrubbed proxy responses replace or verify those fixtures.

Required nutrition fixtures:

```text
parse_text_success.json
parse_text_success_ru.json
parse_text_success_mixed_language.json
parse_photo_success.json
parse_clarification.json
parse_clarification_ru.json
parse_invalid_schema.json
parse_out_of_range.json
parse_empty_items.json
error_throttling.json
```

Fixtures contain no endpoint, API key, account ID, model ID, request identifier, user
identifier, real photo, or other secret/personal data.
