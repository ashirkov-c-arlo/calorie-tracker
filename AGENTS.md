# AGENTS.md

Instructions for AI agents working on this repository.
Read it fully before your first change. If an instruction here conflicts with your
general habits, this file wins.

All code, comments, commit messages, and documentation are written in **English**.

---

## 1. Product

**Kcal** — a minimalist Android app for tracking calories and macros, where food is
logged in natural language and an LLM converts it into structured data.

### Core flows
1. On first launch, the user must complete one profile form: current weight, height, age,
   formula variant, habitual activity level, target weight, and intended weight-loss rate.
   The same values remain editable in Settings.
2. User types food in any language → LLM returns food items with weights and macros →
   user confirms/edits → persisted.
3. User adds a plate photo to non-blank text in any language → same confirmation
   pipeline. The photo is transient and is never attached to the saved meal.
4. User logs morning weight → raw points, a 7-day moving average, and updated daily targets.
5. User manually requests daily/weekly text insights from locally computed aggregates.

### In scope
- Calories + protein/fat/carbs tracking against a locally calculated daily goal
- Required first-run profile form; no defaults for calculator inputs
- Profile settings: current weight, height, age, formula variant, habitual activity level,
  target weight, and weight-loss rate
- Automated targets for generally healthy adults aged 18+
- Metric and imperial body measurements; canonical metric storage
- English and Russian UI; food input itself is not restricted by language
- Monochrome **White** and **Black** palettes plus a system-following mode
- Weight tracking with raw points and a 7-day moving average
- Chronological meal journal without breakfast/lunch/dinner categories
- History grouped by day and ISO week, preserving the target active on each date
- Manually generated LLM insights for completed days/weeks (post-v1)
- Manual editing of any record (the LLM is wrong sometimes; the user always overrides)

### Non-goals — do not propose or implement without explicit approval
- Accounts, auth, cloud sync, sharing
- Barcode scanning, USDA/OpenFoodFacts databases
- Workouts, steps, Health Connect integration, or adding workout calories on top of PAL
- Athlete-specific macro periodization, carb loading, or training plans
- Meal categories or automatic meal classification
- Multi-step onboarding wizards, gamification, streaks, push notifications
- Persisting meal photos or showing them in History
- Automatic/background insight generation
- Medical advice or treatment recommendations
- Additional languages, color palettes, or unit systems
- Ads, analytics, crash reporting, Firebase

### Defaults and release boundary
- App language: system language when it is Russian or English; English otherwise.
- Unit system: metric.
- Theme mode: follow system, resolving to Black/White; White is the fallback. Dynamic
  color is never used.
- Formula variant, activity, and all other calculator inputs have no defaults. Missing any
  required input keeps the user on the single first-run profile form.
- v1 ends with History. Manually generated Insights are planned for v1.1.

**Minimalism rule:** if a feature can be left out, leave it out. Every new screen,
dependency, and setting requires explicit human approval.

---

## 2. Two environments: yours vs. the human's

| | **Sandbox container (you)** | **Host: Bazzite + Android Studio (human)** |
|---|---|---|
| Edit code | ✅ | ✅ |
| Gradle build, `compileKotlin`, `assembleDebug` | ✅ | ✅ |
| JVM unit tests, Robolectric | ✅ | ✅ |
| Roborazzi screenshot tests | ✅ | ✅ |
| Lint, ktlint/spotless | ✅ | ✅ |
| Compose Preview (visual render in IDE) | ❌ | ✅ |
| Emulator (no `/dev/kvm`) | ❌ | ✅ |
| `adb`, physical Pixel 9a | ❌ | ✅ |
| `connectedAndroidTest` (instrumented) | ❌ | ✅ |
| Layout Inspector, Profiler, debugger | ❌ | ✅ |
| Real LLM proxy / AWS Bedrock calls | ❌ (no creds, no network) | ✅ |

**Consequence:** you cannot see how the UI looks or behaves, and you cannot call the
live LLM path. Your job is to bring the code to a state of "compiles, covered by tests,
ready for visual and live verification" and hand it off per §12.

### Rules for sharing the repo with the host
- The repository is bind-mounted into the container — these are **the same files** the
  human has open in Android Studio.
- **Do not share `GRADLE_USER_HOME` with the host.** In the container use
  `GRADLE_USER_HOME=/home/agent/.gradle`. Otherwise you get cache lock contention and
  conflicting JDK/SDK paths.
- Never commit `build/`, `.gradle/`, `.idea/`. Avoid gratuitous `clean` — if Studio is
  open on the host it triggers a full re-index for the human.
- `local.properties` is **not** committed. In the container the SDK path comes from
  `ANDROID_SDK_ROOT`; do not create `local.properties` yourself.
- If the container has no network, build with `--offline`. Do not add dependencies that
  aren't already in the warmed cache — the build will fail for you and "work" for the
  human, or vice versa.

---

## 3. Pinned versions — do not change

| Component | Value |
|---|---|
| `compileSdk` / `targetSdk` | **37** |
| `minSdk` | **26** (`java.time` without desugaring) |
| JVM toolchain | **17** |
| Programming language | Kotlin, `explicitApi()` off, warnings-as-errors on |
| Target device | Google Pixel 9a (reference for manual verification) |
| Emulator | 37.1.11 (host only) |

Library versions live **only** in `gradle/libs.versions.toml`. That file is the single
source of truth.

**Forbidden without human approval:** bumping or downgrading AGP, Kotlin, Compose BOM,
`compileSdk`, `minSdk`, JDK; adding dependencies; touching `signingConfig`,
`applicationId`, `buildTypes`.

---

## 4. Stack

- **UI:** Jetpack Compose + Material 3, single-Activity, edge-to-edge, fixed monochrome
  White/Black palettes with a system-following mode; no dynamic color
- **Navigation:** Navigation Compose, type-safe routes (`@Serializable` objects)
- **DI:** Hilt
- **Database:** Room + KSP. Explicit migrations only; `fallbackToDestructiveMigration`
  is **banned**
- **Preferences:** DataStore Preferences for profile and UI preferences; Room remains the
  source of truth for weight history
- **Localization:** Android string resources + approved AndroidX AppCompat app-locale APIs
  (`AppCompatDelegate.setApplicationLocales`); no custom translation layer
- **Networking:** Ktor Client + `kotlinx.serialization` (`ContentNegotiation`, `HttpTimeout`)
- **Images:** transient capture/selection via `ActivityResultContracts.TakePicture` and
  `PickVisualMedia`; Coil only if needed for the entry preview. **No CameraX**
- **Charts:** hand-drawn on Compose `Canvas` — raw weight points, the 7-day moving average,
  and sparklines. No charting dependency; Vico is not used
- **Async:** Coroutines + Flow. `StateFlow` for state, `Channel` for one-shot events
- **Tests:** JUnit4, `kotlin.test`, Turbine, Robolectric, Roborazzi. Prefer hand-written
  fakes over mocks; MockK only where a fake is genuinely impractical

---

## 5. Project structure

Single module `:app`. Do not split into Gradle modules unless asked. Boundaries are
enforced by packages.

```
app/src/main/java/app/kcal/
├─ KcalApplication.kt
├─ MainActivity.kt
├─ core/
│  ├─ designsystem/      # Theme, Color, Type, Shape, atoms
│  ├─ common/            # Result wrappers, DispatcherProvider, Clock/ZoneId provider
│  └─ ui/                # shared composables (EmptyState, LoadingRow, ErrorCard)
├─ domain/
│  ├─ model/             # pure Kotlin models, no Room/Ktor annotations
│  └─ usecase/           # aggregation, validation, business rules
├─ data/
│  ├─ db/                # Entity, Dao, KcalDatabase, migrations/
│  ├─ prefs/             # DataStore
│  └─ repository/        # implementations of domain interfaces
├─ llm/
│  ├─ NutritionParser.kt      # interface
│  ├─ InsightGenerator.kt     # interface
│  ├─ remote/                 # proxy client, request/response DTOs, mappers
│  └─ fake/                   # FakeNutritionParser, FakeInsightGenerator
└─ feature/
   ├─ today/            # today's day screen
   ├─ entry/            # text or text+photo input + confirmation sheet
   ├─ trends/           # weight + charts
   ├─ history/          # days/weeks
   ├─ insights/
   └─ settings/
```

Inside each feature package: `XxxScreen.kt` (UI only, stateless where possible),
`XxxViewModel.kt`, `XxxUiState.kt`, plus `components/` when needed.

### Layer rules
- `domain` knows nothing about Android, Room, Ktor, Compose, or AWS. Kotlin + kotlinx only.
- `feature` never touches `data` or `llm` implementations directly — only stable interfaces.
- `data` and `llm` know nothing about Compose or ViewModels.
- Daily-target calculation and canonical-unit conversion are deterministic domain logic;
  an LLM never performs either.
- Room `Entity` ≠ domain model. Always map.
- Remote DTO ≠ domain model. Always map **and validate**.

---

## 6. Domain: models and invariants

```kotlin
data class Macros(
    val kcal: Int,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
)

data class FoodItem(
    val name: String,
    val grams: Double?,          // null = model could not estimate mass
    val macros: Macros,
    val confidence: Float,       // 0f..1f
)

enum class EntrySource { MANUAL, LLM_TEXT, LLM_PHOTO }

data class MealEntry(
    val id: Long,
    val localDate: LocalDate,    // the user's day, not UTC
    val at: Instant,
    val items: List<FoodItem>,
    val rawUserInput: String?,
    val source: EntrySource,
    val summary: String?,   // one-line meal name for the journal; null = fall back to item names
)

data class WeightEntry(val localDate: LocalDate, val kg: Double)

data class DailyTargetSnapshot(
    val localDate: LocalDate,
    val targets: Macros,
    val effectiveLossRateKgPerWeek: Double,
)

enum class EnergyEquationSex { FEMALE, MALE }
enum class ActivityLevel { SEDENTARY, LIGHT, MODERATE, HIGH }
enum class ThemeMode { SYSTEM, WHITE, BLACK }
```

### Profile, defaults, and units
- DataStore stores height in centimetres, age in whole years, `EnergyEquationSex`,
  `ActivityLevel`, target weight in kilograms, intended loss rate in kilograms per week,
  unit system, app language, and `ThemeMode`.
- Calculator fields have no defaults. Profile completeness is derived from required values;
  do not persist a separate `onboardingComplete` flag.
- The user-facing field is a **formula variant selector**. Internally,
  `EnergyEquationSex` selects one of the two validated Mifflin–St Jeor branches; it is not
  a gender-identity field. Never average the branch constants.
- **Target weight input:** a slider whose advisory bounds are the weight interval for a body
  mass index of 18.5–24.9 at the entered height, quantised to 0.5 kg. The bounds are shown as
  a factual reference, never as advice, and a value outside them is never silently coerced:
  the slider widens to keep it reachable.
- **Weight-loss rate input:** three derived options — slow, moderate and fast — equal to
  0.25%, 0.5% and 0.75% of current body weight per week. They are offered as soon as a
  current weight exists, including while the target itself is unavailable, so no rate is ever
  fabricated. The selected value is stored unchanged as the user's intent: the guardrails in
  the calculator still apply afterwards and any difference is explained. A stored rate that
  matches no option keeps its value and leaves the options unselected.
- Do **not** store a second "current weight" preference. Current weight is the latest
  `WeightEntry`; changing it in Settings upserts today's entry using the injected clock
  and zone.
- Persist canonical values only: kilograms, centimetres, grams, and kilocalories. Metric
  and imperial conversion happens at input/display boundaries and never migrates stored
  data.
- Metric body measurements use kg and cm; imperial body measurements use lb and ft/in.
  Nutrition remains kcal and grams in both modes.
- Format values with the selected app locale. Accept the locale decimal separator and the
  alternate `.`/`,` separator when pasting user input.
- Resolve the initial app language from the system (`ru`/`en`, otherwise `en`), default to
  metric units, and default `ThemeMode` to `SYSTEM`. System dark/light maps only to the
  Black/White palettes; unsupported resolution falls back to White.

### Daily-target calculation

This calculator is an estimate for generally healthy adults aged 18+. The UI labels it as
an estimate and states that it is not suitable for pregnancy, breastfeeding, eating
disorders, therapeutic diets, or conditions such as kidney or liver disease. For an age
below 18, return an unavailable reason instead of a target.

All arithmetic is deterministic domain logic. Use the Mifflin–St Jeor resting metabolic
rate (RMR), with weight `w` in kg, height `h` in cm, and age `a` in whole years:

```text
FEMALE: RMR = 10w + 6.25h - 5a - 161
MALE:   RMR = 10w + 6.25h - 5a + 5
```

Estimate total daily energy expenditure with one habitual-activity multiplier:

| `ActivityLevel` | PAL | User-facing meaning |
|---|---:|---|
| `SEDENTARY` | 1.20 | Mostly seated, little intentional activity |
| `LIGHT` | 1.375 | Light activity about 1–3 days/week |
| `MODERATE` | 1.55 | Moderate activity about 3–5 days/week |
| `HIGH` | 1.725 | Hard activity most days or a physical job |

```text
TDEE = RMR × PAL
```

The user selects PAL from their habitual week; never infer it from one day. PAL already
represents habitual activity. Do not add exercise calories separately or infer a protein
target from PAL alone.

For weight loss, use the requested pace as an intent, then apply conservative product
guardrails:

```text
safeRateKgPerWeek = min(requestedRateKgPerWeek, 1.0, currentWeightKg × 0.01)
requestedDeficit = safeRateKgPerWeek × 7700 / 7
cappedDeficit = min(requestedDeficit, TDEE × 0.20, 750)
minimumIntake = 1200 for FEMALE, 1500 for MALE

if currentWeightKg <= targetWeightKg:
    targetKcal = TDEE
else:
    targetKcal = min(TDEE, max(TDEE - cappedDeficit, minimumIntake))

effectiveRateKgPerWeek = max(0, TDEE - targetKcal) × 7 / 7700
```

The `7700 kcal/kg` conversion is an initial approximation, not a promise. If a rate,
deficit cap, or intake floor changes the requested pace, preserve the user's input but
show the effective estimated pace and a localized explanation. Recalculate from the
latest weight. Round only the final displayed/stored targets, not intermediate values.

Calculate macros from the calorie target using a weight-based protein target while
keeping all percentages inside general adult reference ranges:

```text
referenceWeightKg = min(currentWeightKg, targetWeightKg)
proteinCandidateG = 1.2 × referenceWeightKg
proteinG = clamp(proteinCandidateG, targetKcal × 0.10 / 4, targetKcal × 0.30 / 4)
fatG = targetKcal × 0.25 / 9
carbsG = (targetKcal - proteinG × 4 - fatG × 9) / 4
```

This yields protein at `10%..30%`, fat at `25%`, and carbohydrates at `45%..65%` of
energy. Increased PAL raises TDEE; protein remains primarily weight-based and the extra
energy goes mostly to carbohydrates. Sports-specific protein or carbohydrate targets
require explicit future approval.

### Daily target snapshots and journal shape
- Room stores one `DailyTargetSnapshot` per local date.
- Completing the first-run profile creates today's snapshot. Changes to weight or profile
  inputs update today's snapshot only; past snapshots are immutable.
- Saving a meal ensures today's snapshot exists in the same logical operation.
- History uses the stored snapshot, never today's settings, to render past progress.
- Meal entries are chronological only. Do not add meal-type columns or infer categories.
- Photos and temporary image identifiers never appear in Room or `MealEntry`.

### Invariants — enforce in `domain/usecase`, cover with tests
- Persisted numeric values must be finite. Macros, `kcal`, grams, and loss rate are
  non-negative; body measurements, target weight, and age are positive; confidence is in
  `0f..1f`.
- Missing calculator inputs, age below 18, or non-positive/non-finite RMR or TDEE produce
  an unavailable result, never guessed defaults or partially calculated targets.
- Calculated calories and macro grams must be finite and non-negative. Their energy sum
  must match the calorie target within the documented final-rounding tolerance.
- Rate, deficit, and intake guardrails are never silent: return the requested and effective
  rates plus a warning whenever they differ.
- A parsed meal must contain at least one item. Missing fields, non-finite/negative values,
  invalid confidence, and empty items are hard-invalid LLM responses.
- Sanity bounds are soft review warnings: per-item `kcal` ∈ `0..5000`, weight ∈
  `20.0..400.0` kg, and `grams` ∈ `0.0..5000.0`. A violation keeps the editable draft
  and marks it "needs review"; it is not silently coerced, discarded, or persisted.
- **Day boundaries:** `localDate` is derived from injected `Clock` + `ZoneId`, never from
  a bare `LocalDate.now()` — otherwise tests are non-deterministic.
- **Weeks:** ISO-8601, Monday start. Use `WeekFields.ISO` explicitly.
- One `WeightEntry` per date: re-entry upserts by `localDate`.
- For date `d`, the 7-day moving average is the arithmetic mean of available entries whose
  local dates are in the inclusive calendar window `[d - 6 days, d]`; do not interpolate
  missing dates or substitute the last seven measurements.
- Time storage: `Instant` as UTC epoch millis, plus a separate `localDate` column
  (epochDay `Int`) for fast day grouping.

---

## 7. LLM layer — AWS Bedrock

### 7.1 Domain-facing contracts (stable, transport-agnostic)

```kotlin
data class ClarificationAnswer(val question: String, val answer: String)

sealed interface UserInput {
    data class Text(
        val text: String,
        val clarification: ClarificationAnswer? = null,
    ) : UserInput

    data class TextWithPhoto(
        val text: String,
        val temporaryPhotoPath: String,
        val clarification: ClarificationAnswer? = null,
    ) : UserInput
}

sealed interface ParseResult {
    data class Success(val items: List<FoodItem>, val note: String?, val summary: String?) : ParseResult
    data class NeedsClarification(val question: String) : ParseResult
    data class Failure(val reason: FailureReason, val cause: Throwable? = null) : ParseResult
}

enum class FailureReason {
    NO_NETWORK, TIMEOUT, THROTTLED, INVALID_REQUEST,
    PAYLOAD_TOO_LARGE, INVALID_RESPONSE, CONTENT_BLOCKED,
    AUTH, QUOTA, UNKNOWN,
}

interface NutritionParser { suspend fun parse(input: UserInput): ParseResult }
```

`domain` and `feature` depend on these interfaces only. Proxy-specific code stays inside
`llm/remote/`. Swapping the model, region, or transport must not touch any other package.

### 7.2 Transport: thin AWS proxy (default)

**Default architecture:** the app talks HTTPS+JSON to a thin AWS-side endpoint
(API Gateway → Lambda) which holds the IAM role and calls Bedrock. The app does
**not** contain AWS credentials and does **not** sign requests.

Rationale, non-negotiable:
- Long-lived IAM keys must never ship in an APK — they are trivially extractable.
- No SigV4 signing on device, no AWS SDK in the APK (smaller build, fewer conflicts).
- Model ID, region, prompts, and quotas are server-side config — changeable without an
  app release.
- Abuse controls, rate limits, and cost caps are enforced server-side.

The normative HTTP/JSON contract is [`docs/llm-proxy-contract.md`](docs/llm-proxy-contract.md).
It fixes the discriminated response envelopes, JSON Base64 image transport, error/status
mapping, retry ownership, privacy rules, and versioning.

```text
POST {LLM_API_BASE_URL}/v1/nutrition/parse     # approved v1 contract
POST {LLM_API_BASE_URL}/v1/insights/generate  # reserved until v1.1 contract
```

The app performs no Bedrock-shaped parsing. Proxy implementation is **out of scope for
this repository**. Implement the nutrition client against the contract and fixtures
(§7.7); do not call the reserved insights path before its schema is approved.

`LLM_API_KEY` in an APK is extractable. It is a backend routing/quota identifier, not a
secret, user identity, or authentication boundary. Do not add accounts, device
fingerprinting, or attestation without explicit approval.

**Alternative (requires explicit approval, do not implement unprompted):** direct
`bedrock-runtime` calls from the device via `aws.sdk.kotlin:bedrockruntime` with
Cognito Identity Pool temporary credentials. It adds a large dependency, a Cognito
setup, and an unauthenticated-identity abuse surface. If approved, the seam in §7.1
stays identical and only its transport implementation changes.

### 7.3 Backend Bedrock rules (implemented outside this repository)

- Use the **Converse API**, not raw `InvokeModel`. Converse gives a single
  request/response shape across models and first-class multimodal + tool-use support.
  Never hand-code model-specific request bodies.
- **Model ID is backend configuration and is never shipped in app code.** Many models
  are reachable only through cross-region inference profiles, and availability differs
  by region — exactly why this stays server-side.
- **`ConverseStream` is not used.** Responses are structured JSON, not chat tokens.
- Retry only on throttling and transient 5xx: exponential backoff with jitter,
  max 2 retries, overall budget under the request timeout. Never retry validation or
  authorization failures.
- Map service errors to `FailureReason`, never leak raw AWS exception text into the UI.
- Record `usage.inputTokens` / `usage.outputTokens` when returned; log in debug builds
  only, and persist them on insights so cost is observable.
- Bedrock Guardrails are out of scope for now.

### 7.4 Structured output — mandatory

Free-form model text is never parsed. The backend obtains structured output through
Converse **tool use**. It declares both versioned tools and requires one tool call with
`toolChoice = any`; it accepts exactly one known tool-use block:

- `log_food` → `{ items: [{ name, grams, kcal, protein_g, fat_g, carbs_g, confidence }], summary, note }`
- `ask_clarification` → `{ question }` (used instead of guessing wildly)

The backend extracts the selected tool input and wraps it in the contract's required
`type = success` or `type = clarification` envelope. The app never parses Converse or
model-specific response envelopes.

App-side pipeline, in order:
1. Deserialize with `kotlinx.serialization`, `ignoreUnknownKeys = true`.
2. Apply the hard validation rules in §6.
3. Map to domain models and derive soft sanity warnings for the confirmation UI.

Deserialization or hard-validation failure → `Failure(INVALID_RESPONSE)`. A sanity-bound
violation remains `Success` with a "needs review" draft. Never coerce garbage into
plausible-looking numbers.

**One repair attempt only, backend-side.** If tool input is unparseable or hard-invalid,
the backend may send one repair request carrying the validation error, then gives up. The
app does not retry an invalid payload.

### 7.5 Hard product rules

1. **Nothing reaches the database without explicit user confirmation.** LLM output lands
   in an editable bottom sheet. This is a requirement, not a preference. Both input modes
   require non-blank text; photo-only parsing is not implemented.
2. **Parsing is language-agnostic.** Send UTF-8 input unchanged; do not detect, translate,
   reject, or route text by language on device. `Accept-Language` is always the selected
   interface language and controls item names, notes, clarifications, and insights even
   when the input uses another language. Schema keys and numeric semantics stay fixed. At
   minimum, test English, Russian, and mixed/non-UI-language input.
3. **Daily targets and insights never do arithmetic in the LLM.** Daily targets and all
   insight aggregates (averages, deltas, trends) are computed locally in domain use cases.
   The model only extracts meal data or phrases finished statistics.
4. **Profile data is not meal-parser context.** Do not upload age, height, weight,
   energy-equation sex, activity level, target weight, or loss rate to parse food.
5. **Prompts are backend-owned and versioned.** The insight response carries its prompt
   version; persist it in `Insight.promptVersion`. Do not duplicate backend prompts in the
   app.
6. **Insights contain no medical advice, diagnosis, or prescription.** Neutral tone,
   ~600 characters max, disclaimer rendered beneath the card in the UI.
7. **Photos are ephemeral:** never store a photo path in Room or attach a photo to a meal.
   Read the OS-provided URI into temporary app cache as needed, downscale to 1024 px on the
   long edge, encode JPEG at quality 80, and thereby strip EXIF before JSON Base64 upload
   defined by the proxy contract. Keep local temporary files only while the entry
   request/retry/clarification flow is active. Delete them immediately after a final
   `Success` or when the flow is cancelled/closed; keep them through
   `NeedsClarification` only to resubmit with the answer, and clean crash leftovers on the
   next app start. Check current provider limits before raising image size or dimensions.
8. **Timeouts:** 30 s request, 10 s connect. Offline → a clear error plus the option to
   log the meal manually.
9. **Config:** `LLM_API_BASE_URL` and `LLM_API_KEY` come from `local.properties` →
   `BuildConfig`. No keys, account IDs, or real endpoint URLs in git, tests, logs, or this
   file. Ever.
10. **Logging:** request/response bodies only behind `BuildConfig.DEBUG`. Never log text
    input, photos, age, height, weight, energy-equation sex, activity level, targets, loss
    rate, or generated output in release.
11. **Insights are user-initiated.** Never generate them in a background job or merely
    because a day/week ended.

### 7.6 Working without credentials

You have no network and no AWS access. Develop against `FakeNutritionParser` and
`FakeInsightGenerator` in `llm/fake/`, which return deterministic fixtures. All pipeline
tests run on fakes and on committed JSON fixtures. Live verification is the human's job.

### 7.7 Fixtures

`app/src/test/resources/llm/` holds contract payloads, committed and scrubbed of
identifiers:

```
llm/
├─ parse_text_success.json
├─ parse_text_success_ru.json
├─ parse_text_success_mixed_language.json
├─ parse_photo_success.json
├─ parse_clarification.json
├─ parse_clarification_ru.json
├─ parse_invalid_schema.json      # missing required field
├─ parse_out_of_range.json        # kcal = 99999; valid draft, needs review
├─ parse_empty_items.json
└─ error_throttling.json
```

Until the proxy exists, `docs/llm-proxy-contract.md` and its examples are authoritative.
Once implemented, captured scrubbed proxy responses verify or replace these fixtures. Raw
Converse envelopes are backend fixtures and do not belong here. Insight fixtures are added
only when the reserved v1.1 contract is finalized.

---

## 8. UI and Compose

### Navigation
Bottom navigation, 3 tabs + FAB:
- **Today** — daily macro progress, list of meals
- **Trends** — weight chart, weekly aggregates
- **History** — days/weeks list, insight cards
- **FAB** on Today → input screen (text or text + photo)
- **Settings** — icon in the Today top bar; profile, formula variant selector, habitual
  activity, target-loss rate, unit system, interface language, and System/White/Black
  theme mode

Before required profile data exists, launch directly into the single profile form and do
not expose the main navigation. Later edits use the same fields in Settings. If the
calculator is not applicable, show a localized explanation instead of a fabricated goal.

### Conventions
- `UiState` is an immutable `data class`, one per screen, exposed as `StateFlow`.
- Collect with `collectAsStateWithLifecycle()`.
- UI composables are **stateless**: they take `uiState` plus callback lambdas. A stateful
  wrapper (`TodayRoute`) resolves the ViewModel and calls the stateless `TodayScreen`.
- Collections in state are `kotlinx.collections.immutable.PersistentList`, not `List`
  (recomposition stability).
- One-shot events (snackbar, navigation) go through a `Channel`, not state flags.
- No business logic in composables. `remember` is for UI state only.
- **Material 3 only.** No custom component framework, no Material 2.
- Themes use fixed monochrome **White** and **Black** palettes. `ThemeMode.SYSTEM` follows
  system light/dark using those palettes only. Do not add dynamic color or a third palette.
- Colors, typography, and spacing come from `core/designsystem` only. Scattered
  `Color(0xFF...)` and ad-hoc `16.dp` literals are not allowed.
- Detailed visual references will arrive later. Until then, use plain Material 3 layouts
  and do not invent decorative patterns, motion systems, or extra components.
- `@Preview` is mandatory for every screen and every non-trivial component: White + Black,
  and loading/empty/error/content states. Previews are the primary visual handoff channel.
- Accessibility: `contentDescription` on all meaningful icons, touch targets ≥ 48.dp,
  large-font support (no fixed heights on text blocks).
- Default resources are English in `values/`; Russian translations are in `values-ru/`.
  Keep key sets complete and use `AppCompatDelegate.setApplicationLocales()` rather than
  custom translation maps or `Locale.setDefault`.
- All user-visible text, including errors and content descriptions, lives in resources.
  Hardcoded strings in composables are forbidden.

### XML
No layout XML. Exceptions: `themes.xml`, `strings.xml`, `AndroidManifest.xml`, splash
resources, locale config, and a minimal `FileProvider` paths XML if `TakePicture` needs it.

---

## 9. Commands

```bash
# fast compile check (the most frequent loop)
./gradlew :app:compileDebugKotlin

# build APK
./gradlew :app:assembleDebug

# unit tests
./gradlew :app:testDebugUnitTest

# single test
./gradlew :app:testDebugUnitTest --tests "app.kcal.domain.usecase.BuildPeriodStatsTest"

# screenshot tests
./gradlew :app:verifyRoborazziDebug          # verify
./gradlew :app:recordRoborazziDebug          # re-record baselines (deliberate only!)

# static analysis and formatting
./gradlew :app:lintDebug
./gradlew spotlessApply

# full pre-handoff gate
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
          :app:verifyRoborazziDebug spotlessCheck
```

Add `--offline` when the container has no network.
Do not run `connectedAndroidTest`, `installDebug`, or any `adb` command in the container —
they will fail, and that is expected.

---

## 10. Testing

Mandatory coverage:

| Area | Test type |
|---|---|
| Domain aggregation, validation, day/week boundaries | JVM unit, no Android |
| Daily-target formula and metric/imperial conversion | JVM unit, table-driven |
| Daily-target snapshot creation/update/history immutability | JVM + Room Robolectric |
| Raw weight + calendar-window 7-day moving average | JVM unit |
| Remote DTO → domain mapping, malformed, review, and multilingual fixtures | JVM unit + fixtures |
| Transient image processing and cleanup | JVM/Robolectric |
| Error mapping (throttling, timeout, auth) → `FailureReason` | JVM unit |
| Profile/default persistence, required first-run routing, current-weight source | JVM/Robolectric |
| ViewModel state sequences | JVM unit + Turbine |
| Room DAOs and **every** migration | Robolectric + `MigrationTestHelper` |
| Screens: rendering of all primary states | Robolectric + `createAndroidComposeRule` |
| White/Black visual regression | Roborazzi (baselines in `app/screenshots/`) |

Rules:
- Time and zone are always injected (`Clock`, `ZoneId`). A test that depends on the real
  current date is a bug.
- Locale and unit system are explicit test inputs; tests never depend on machine defaults.
- Cover both Mifflin–St Jeor branches, all four PAL values, target reached, rate/deficit
  caps, intake floors, and an under-18 unavailable result.
- Assert macro energy sums within rounding tolerance and protein/fat/carbohydrate shares
  remain in the specified ranges.
- Cover decimal comma/dot input and metric ↔ imperial round trips within a stated tolerance.
- Verify initial language fallback, metric default, System→White/Black theme resolution,
  and that calculator fields never receive fabricated defaults.
- Verify no Room entity contains a photo path; temporary files survive only an active
  clarification/retry flow and are removed after final success, cancellation, and startup
  crash cleanup.
- Include English and Russian resource/render checks without multiplying every screenshot
  across every locale; one Russian text-heavy state per touched screen is enough.
- No test performs network I/O.
- Roborazzi emits PNGs — if you can inspect images, use them to self-check layout before
  handoff.
- New logic without a test is not done.

---

## 11. Definition of Done

For code, resource, or schema changes, a task is complete only when all of these hold.
For documentation-only changes, run `git diff --check`, mark build/visual checks as
"not run — docs only", and never report an unrun check as passed.

- [ ] `./gradlew :app:assembleDebug` succeeds with no new warnings
- [ ] `./gradlew :app:testDebugUnitTest` green; new logic covered
- [ ] `./gradlew :app:lintDebug` — no new findings
- [ ] `spotlessCheck` clean
- [ ] `verifyRoborazziDebug` green, or baselines deliberately re-recorded and called out
- [ ] `@Preview` added/updated for all touched screens (White + Black + every state)
- [ ] No new dependencies (or the addition was separately approved)
- [ ] English/Russian resource keys match; sizes/colors come from the design system
- [ ] No secrets, API keys, AWS account IDs, model IDs, or real endpoints in the diff
- [ ] Handoff written per §12

---

## 12. Handoff protocol

You cannot verify UI or the live LLM path. End every task with this block:

```
## Ready for on-device verification

**Branch:** <current branch>
**What changed:** <2–4 lines>

**Verified in sandbox:**
- assembleDebug <✅ or not run — reason>
- testDebugUnitTest <✅ with count or not run — reason>
- lintDebug <✅ or not run — reason>
- verifyRoborazziDebug <✅ or not run — reason>
- spotlessCheck <✅ or not run — reason>

**Could not verify:**
- real proxy/Bedrock call (no credentials, no network) — exercised via
  FakeNutritionParser and fixtures in app/src/test/resources/llm/
- system camera and Photo Picker behaviour
- gestures / scrolling / animations on real hardware

**Please check manually on Pixel 9a:**
1. Trends → chart with 1, 2, and 30 points
2. System/White/Black modes; no dynamic color leakage
3. First launch requires every calculator input; no fabricated profile defaults
4. Switch English/Russian and metric/imperial settings; verify they persist after relaunch
5. Check both formula variants and all activity levels against unit-test examples
6. Enter weight with comma and dot decimal separators
7. Confirm temporary photos disappear after parse success/cancel
8. Rotate the confirmation sheet — state must survive

**Compose Previews for quick visual review:**
- `feature/trends/components/WeightChart.kt` → `WeightChartPreview`, `WeightChartEmptyPreview`

**Fixtures needed (please capture and commit):**
- <e.g. scrubbed Russian and mixed-language proxy responses>

**Risks / open questions:**
- <what might break, which decisions you made on your own>
```

If you are unsure about a UX decision, **do not invent one** — ask in this block and
leave a TODO.

---

## 13. Git

- Branches: `feat/…`, `fix/…`, `refactor/…`, `chore/…`
- Commits: Conventional Commits (`feat(trends): add weight chart`)
- One commit = one logical change. Never mix refactoring with a feature.
- Formatting/import changes go in a separate `style:` commit.
- Forbidden: `push --force`, working on `main`, rebasing published branches, committing
  `build/`, `.gradle/`, `local.properties`, `*.jks`, `.env`, or anything with credentials.

---

## 14. Forbidden

- Changing AGP / Kotlin / Compose BOM / `compileSdk` / `minSdk` / JDK versions
- Adding dependencies without approval
- Embedding AWS access keys or session tokens in the app; hand-rolling SigV4 signing
- Adding AWS SDK, Amplify, or Cognito without explicit approval (see §7.2)
- Using raw `InvokeModel` with model-specific bodies, or `ConverseStream`
- Hardcoding model IDs, regions, endpoints, or account IDs in app code
- `fallbackToDestructiveMigration()`; deleting or editing an already-shipped migration
- XML layouts, `findViewById`, Fragments, `LiveData`, RxJava
- `GlobalScope`, `runBlocking` in production code, direct `Dispatchers.*` usage
  (go through `DispatcherProvider`)
- `!!`, empty `catch`, `catch (e: Exception)` without handling
- `LocalDate.now()` / `Instant.now()` / `System.currentTimeMillis()` outside the time provider
- Hardcoded UI strings, custom translation maps, or magic numbers outside the design system
- Dynamic color or palettes beyond White/Black; `SYSTEM` may only select between them
- Fabricated defaults for required profile/calculator inputs or a separate onboarding flag
- Changing the approved target formula/constants without approval, calculating targets in
  the LLM, adding workout calories on top of PAL, or duplicating current weight in DataStore
- Meal categories, automatic meal classification, or mutable past target snapshots
- Persisting photos, photo paths, or image bytes in Room/domain records
- Client-side language detection/translation as a prerequisite for parsing food
- Writing LLM output to the database without user confirmation
- Automatic/background insight generation
- Logging text input, generated output, photos, or profile/weight data in release builds
- Analytics, crash reporting, Firebase, ads, or any unapproved third-party SDK
- Manifest permissions beyond `INTERNET` and `CAMERA` (the latter only once photo input
  lands); `READ_EXTERNAL_STORAGE` is not needed — Photo Picker is used
- Creating `local.properties`, editing `.idea/` (shared code style excepted), running
  `clean` without cause
- Drive-by refactoring — implement only what was asked

---

## 15. Roadmap

The detailed execution plan is [`docs/implementation-plan.md`](docs/implementation-plan.md).
Do not skip stages. Each stage ends with a handoff and human confirmation.

1. **Skeleton:** Application + Hilt, monochrome White/Black M3 palettes with System mode,
   AppCompat English/Russian locales, navigation, Room schema v1, DataStore
2. **Required profile/settings:** single first-run form, formula variant, habitual activity,
   current-weight upsert, unit conversion, language, and theme
3. **Daily targets + manual food entry:** tested Mifflin–St Jeor/PAL/deficit/macro use case,
   daily target snapshots, chronological journal, Today progress, and manual logging
4. **Text parsing via proxy:** `NutritionParser`, multilingual fixture-driven mappers and
   validation, confirmation/edit sheet. Fakes first, wiring second
5. **Text + transient photo:** Photo Picker + TakePicture, downscale/strip EXIF, upload,
   and deterministic cleanup; no persisted or photo-only mode
6. **Weight tracking:** raw points + 7-day moving average on Trends
7. **v1 — History:** days and ISO weeks using immutable daily target snapshots
8. **v1 polish after visual references arrive:** a11y, empty states, error handling,
   edge-to-edge, final spacing and typography
9. **v1.1 — manual Insights:** finalize its proxy contract, then local aggregates → model
   phrasing → localized cards with `promptVersion`; no background generation

---

## Appendix A. Host notes (for the human; agents do not execute these)

Host: **Bazzite** (Fedora Atomic, immutable). Android Studio via JetBrains Toolbox.

- Keep the Android SDK in `~/Android/Sdk` (inside `$HOME`, to avoid the immutable root).
- The emulator needs KVM: check `ls -l /dev/kvm` and `groups | grep kvm`. If the group is
  missing: `sudo usermod -aG kvm $USER`, then re-login.
- Prefer installing Toolbox from the native archive into `~/.local/share/JetBrains` rather
  than Flatpak — the Flatpak sandbox complicates access to `~/Android/Sdk`, `/dev/kvm`,
  and `adb`.
- `adb` lives in `~/Android/Sdk/platform-tools`. A physical Pixel 9a may need a udev rule
  for vendor id `18d1`, or layer `rpm-ostree install android-udev-rules` + reboot.
- Wayland: if the IDE or emulator renders with artifacts, launch it under XWayland.
- Do not layer a separate JDK — use the JBR bundled with Android Studio.
- AVD: Pixel 9a, API 37, `-gpu host`.
- `local.properties` on the host holds `sdk.dir=/var/home/<user>/Android/Sdk`,
  `LLM_API_BASE_URL=…`, and `LLM_API_KEY=…`. It is gitignored and never mounted into the
  container.
- App fixtures are captured from the proxy configured in `local.properties`. AWS
  credentials stay in `~/.aws/` only for backend diagnostics and manual Bedrock testing.
  Never mount `~/.aws` into the agent container.
