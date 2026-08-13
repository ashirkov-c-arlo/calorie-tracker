# Kcal Android Implementation Plan

Status: **final client implementation plan; stages 1-7 implemented, stages 8-9 not started**.

> **Open approval — stage order.** Stage 7 (History) was implemented before stage 6 (weight
> Trends) on explicit request, and stage 6 followed on the `feat/weight-trends` branch. History
> depends only on stored meals and daily target snapshots, so it needed nothing from stage 6,
> but `AGENTS.md` §15 requires stages to run in order. Merging `feat/history` and
> `feat/weight-trends` in that order still needs human approval.
>
> **Open approval — no Vico.** Stage 6 renders the raw points and the 7-day average with a
> Compose `Canvas` (`feature/trends/components/WeightChart.kt`) instead of adding the Vico
> dependency. This deviates from `AGENTS.md` §4, which assigns Vico to the weight chart and
> leaves hand-drawn `Canvas` work to simple sparklines, and from §15 of this plan. It is not
> approved yet: either grant the deviation or approve the dependency, and Vico then replaces
> `WeightChart` behind the unchanged `TrendsUiState`.

Normative inputs:

- [`AGENTS.md`](../AGENTS.md) — product, architecture, safety, and delivery rules;
- [`docs/llm-proxy-contract.md`](llm-proxy-contract.md) — future proxy HTTP contract.

This plan covers the Android repository only. The proxy implementation is a separate
future task.

---

## 1. Release scope

### v1

- required single-screen profile setup on first launch;
- local calorie and macro target calculation;
- metric/imperial body measurements;
- English/Russian interface;
- System/White/Black theme modes using only monochrome White/Black palettes;
- manual meal logging;
- text and text+photo nutrition parsing through the proxy contract;
- editable confirmation before persistence;
- Today dashboard;
- weight logging;
- raw weight chart plus calendar-window 7-day moving average;
- chronological day and ISO-week History with immutable daily target snapshots.

### v1.1

- manually requested daily/weekly LLM insights;
- no background generation;
- no LLM arithmetic.

### Explicitly excluded

Accounts, synchronization, meal categories, persisted photos, workouts, steps, Health
Connect, barcode/food databases, notifications, analytics, ads, and additional themes or
languages.

---

## 2. Fixed product decisions

- Package/application ID: `app.kcal`.
- Target device: Google Pixel 9a.
- Calculator inputs have no defaults and must be entered on first launch.
- Initial app language follows system Russian/English and otherwise falls back to English.
- Initial unit system is metric.
- Initial theme mode follows the system and resolves only to White or Black; White is the
  fallback and dynamic color is disabled.
- The UI calls `EnergyEquationSex` the **formula variant**.
- Current weight is the latest Room `WeightEntry`, not a duplicated preference.
- Nutrition remains kcal and grams in both unit systems.
- Meals are chronological and have no breakfast/lunch/dinner labels.
- Historical progress uses the target saved for that date, not current settings.
- LLM human-readable output always uses the interface language.
- Photos are transient request inputs and are never part of a saved meal.
- Insights are generated only after an explicit user action.

---

## 3. Delivery rules

1. Implement stages in order; do not start the next stage before sandbox gates and human
   confirmation for the current stage.
2. Keep one Gradle module and package boundaries from `AGENTS.md`.
3. Add a dependency only in the first stage that uses it.
4. Every non-trivial domain branch gets a focused test.
5. Every touched screen gets White/Black previews for its primary states.
6. Every schema change after Room v1 uses an explicit, tested migration.
7. Never require network access in tests.
8. Never claim emulator, Pixel, camera, Photo Picker, or live proxy verification from the
   sandbox.

Each implementation stage ends with:

```bash
GRADLE_USER_HOME=/home/agent/.gradle ./gradlew \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:verifyRoborazziDebug \
  spotlessCheck
```

Use `--offline` when required and do not run `clean` without cause.

---

## 4. Persistent data design

### Room schema v1

#### `meal_entries`

| Column | Type | Notes |
|---|---|---|
| `id` | Long | Auto-generated primary key |
| `local_date_epoch_day` | Int | Indexed user-local date |
| `at_epoch_millis` | Long | UTC instant |
| `raw_user_input` | String? | Nullable for manual entries |
| `source` | String | `MANUAL`, `LLM_TEXT`, or `LLM_PHOTO` |

#### `food_items`

| Column | Type | Notes |
|---|---|---|
| `id` | Long | Auto-generated primary key |
| `meal_entry_id` | Long | Indexed FK, cascade delete |
| `position` | Int | Stable item order within meal |
| `name` | String | Non-blank |
| `grams` | Double? | Nullable when unknown |
| `kcal` | Int | Non-negative |
| `protein_g` | Double | Non-negative |
| `fat_g` | Double | Non-negative |
| `carbs_g` | Double | Non-negative |
| `confidence` | Float | `0..1` |
| `needs_review` | Boolean | Derived from soft sanity bounds |

Use a unique constraint on `(meal_entry_id, position)`. Store items relationally rather
than as JSON so edits, ordering, cascade deletion, and day aggregation remain simple.

#### `weight_entries`

| Column | Type | Notes |
|---|---|---|
| `local_date_epoch_day` | Int | Primary key; one entry per date |
| `kg` | Double | Canonical value |

#### `daily_target_snapshots`

| Column | Type | Notes |
|---|---|---|
| `local_date_epoch_day` | Int | Primary key |
| `kcal` | Int | Calculated target |
| `protein_g` | Double | Calculated target |
| `fat_g` | Double | Calculated target |
| `carbs_g` | Double | Calculated target |
| `effective_loss_rate_kg_week` | Double | Effective guarded rate |

Rules:

- Completing the profile creates today's snapshot.
- A same-day weight/profile change updates today's snapshot.
- Saving a meal ensures today's snapshot exists.
- Past snapshots are immutable.
- No table contains a photo path, URI, or bytes.

### DataStore preferences

- height in centimetres;
- age in whole years;
- formula variant;
- habitual activity level;
- target weight in kilograms;
- requested loss rate in kilograms/week;
- unit system;
- explicit app-language override, if selected;
- `ThemeMode`.

Profile completeness is derived from required fields plus the latest Room weight. Do not
store `onboardingComplete`.

### Room v2 for v1.1

Add the insight table only when Stage 9 starts. Do not put speculative insight columns in
v1.

---

## 5. Navigation and first-launch flow

```text
App start
  -> required profile complete?
       no  -> ProfileSetup
       yes -> Main navigation

Main navigation
  -> Today
  -> Trends
  -> History

Today top bar -> Settings
Today FAB     -> Entry
```

`ProfileSetup` is one required form, not a wizard. It reuses the same field components and
validation as Settings. Main navigation is unavailable until all calculator fields and a
current weight are valid.

Saving the initial profile spans DataStore and Room, so it cannot be one database
transaction. Make the operation idempotent and repair a missing current-day snapshot on
next collection instead of pretending cross-store atomicity.

---

## 6. Stage 1 — project skeleton

### Work

- Create root Gradle files, `:app`, wrapper, and version catalog.
- Apply pinned SDK/JDK/Kotlin settings from `AGENTS.md`.
- Configure warnings as errors, KSP, Compose, BuildConfig, Room schema export, lint,
  Spotless, Robolectric, and Roborazzi.
- Add only dependencies needed now:
  - Compose + Material 3;
  - Activity Compose and lifecycle Compose;
  - Navigation Compose;
  - Hilt;
  - Room + KSP;
  - DataStore Preferences;
  - AppCompat locale support;
  - approved test libraries.
- Read optional LLM values from `local.properties` with safe empty debug defaults; never
  create or commit the file.
- Add `KcalApplication`, `MainActivity`, edge-to-edge setup, and Hilt wiring.
- Add White/Black palettes and System mode; do not use dynamic colors.
- Add English and Russian resources and AppCompat app-locale wiring.
- Add typed routes and placeholder Today/Trends/History/Settings screens.
- Create Room v1 entities/DAOs/database from §4 and an empty DataStore repository.
- Update `.gitignore` for Android/Gradle/IDE/local-secret artifacts without touching
  existing `.idea/` files.

### Acceptance

- App builds and opens on the profile gate.
- System theme resolves to White/Black.
- System `ru`/`en` resolves correctly; unsupported system language displays English.
- Room v1 opens and exports its schema.
- Placeholder screens have EN/RU strings and White/Black previews.
- Full sandbox gate passes.

---

## 7. Stage 2 — profile, settings, and target calculator

### Domain

- Add profile models, `UnitSystem`, `ThemeMode`, `EnergyEquationSex`, `ActivityLevel`, and
  calculation result types.
- Implement `CalculateDailyTargets` exactly as specified in `AGENTS.md`:
  - Mifflin–St Jeor;
  - PAL;
  - requested-rate, deficit, and intake guardrails;
  - effective loss rate;
  - weight-based protein with percentage bounds;
  - fat at 25%;
  - carbohydrates as the remainder.
- Return `Success(targets, effectiveRate, warnings)` or `Unavailable(reason)`.
- Implement pure kg/lb, cm/ft+in, and kg/week/lb/week conversions.
- Implement locale-aware numeric formatting and parsing accepting both decimal separators.

### Data

- Implement profile preferences.
- Implement weight upsert/latest lookup.
- Implement current-day target snapshot creation/update.
- Combine Room and DataStore flows in repositories without exposing Android types to the
  domain layer.

### UI

- Implement required `ProfileSetup` and editable Settings.
- Use the user-facing label “Formula variant” / localized Russian equivalent.
- Give formula variant and activity no preselected values.
- Take target weight from a slider bounded by the reference body mass index interval for the
  entered height, and the loss rate from three derived paces, as specified in `AGENTS.md`.
  Neither input coerces a stored value, and the selected pace is saved as the user's intent.
- Show estimate/medical-scope text and any effective-rate guardrail warning.
- Apply language, units, and theme changes immediately through their supported APIs.

### Tests

- Both Mifflin–St Jeor branches.
- All PAL values.
- Missing fields and age below 18.
- Target reached and zero requested loss.
- 1%/week, 20% TDEE, 750 kcal, and intake-floor guards.
- Macro ranges and energy sum.
- Metric/imperial round trips.
- Dot/comma parsing.
- First-launch routing and all defaults.
- Snapshot creation and same-day replacement.

### Acceptance

A fresh install cannot reach Today before a valid profile is saved. Afterwards, Settings
can update the profile and today's target without changing past snapshots.

---

## 8. Stage 3 — manual meals and Today

### Work

- Implement meal/item DAOs and transactional repository operations.
- Implement domain validation and daily aggregation.
- Build manual entry with editable item rows:
  - name;
  - optional grams;
  - kcal;
  - protein/fat/carbs;
  - add/remove item;
  - explicit Save/Cancel.
- Build Today:
  - calorie and macro targets/progress;
  - consumed totals;
  - chronological meal list;
  - edit/delete;
  - empty/error/loading states;
  - FAB to Entry.
- Keep meal categories and photos out of the schema and UI.
- Ensure saving a meal and its items is one Room transaction and ensures today's target
  snapshot exists.

### Tests

- Single/multiple item aggregation.
- Multiple meals and stable ordering.
- Edit/delete and cascade deletion.
- Hard-invalid and soft-review values.
- Day boundaries with injected clock/zone.
- DAO transactions and ViewModel state sequences.
- Today/manual-entry screenshots for all primary states and both palettes.

### Acceptance

The app is a complete offline manual calorie/macro tracker. This is the first independently
usable milestone and remains functional when the proxy is unavailable.

---

## 9. Stage 4 — text LLM client

### 4A. Contract-first flow with fakes

- Add `NutritionParser` and deterministic fake implementations.
- Commit response fixtures matching `docs/llm-proxy-contract.md`.
- Implement DTO deserialization, hard validation, soft review warnings, and mapping.
- Implement Entry states:
  - idle;
  - parsing;
  - clarification;
  - editable confirmation;
  - failure with Retry/manual fallback.
- Resubmit explicit clarification question/answer context without server-side session state.
- Persist only after Confirm.

### 4B. Remote transport

- Add Ktor only now.
- Implement the proxy request/response DTOs and headers exactly from the contract.
- Keep connect/request timeouts at 10/30 seconds.
- Do not add a second automatic retry loop; the future proxy owns upstream retries and the
  app exposes explicit Retry.
- Map HTTP/contract failures to `FailureReason` without showing raw bodies.
- Restrict request/response logging to debug and never log sensitive bodies in release.
- Add HTTP engine tests; no test performs network I/O.

### Acceptance

- English, Russian, and mixed-language input produce output in the interface language.
- Clarification and Retry preserve user state.
- Invalid response, throttling, timeout, auth, quota, content-blocked, and offline paths are
  localized and offer manual logging.
- The remote client is contract-complete even if no live proxy exists.

### External release blocker

A production v1 cannot offer live LLM parsing until a separate proxy implementation,
endpoint, quota policy, and key exist. This repository does not implement that backend.

---

## 10. Stage 5 — transient text+photo input

### Work

- Add Photo Picker and `TakePicture`; do not add CameraX or storage permissions.
- Use a minimal `FileProvider` paths resource only if required by `TakePicture`.
- Keep non-blank text mandatory.
- Process on the injected IO dispatcher:
  - decode safely;
  - account for orientation while decoding;
  - resize long edge to 1024 px;
  - encode JPEG quality 80;
  - strip metadata by re-encoding;
  - Base64-encode according to the proxy contract.
- Use Coil only if a transient preview is necessary; otherwise do not add it.
- Keep temporary files through active request, Retry, and clarification only.
- Delete files after final success, cancellation/navigation away, and startup crash cleanup.
- Never put URI/path/bytes into Room or `MealEntry`.

### Tests

- Resize/orientation/JPEG output.
- No retained EXIF.
- Base64 request serialization.
- Cleanup after success/cancel/startup.
- Retention through Retry/clarification.
- Process recreation state where feasible without persisting the image as meal data.

### Host verification

- Pixel 9a camera and Photo Picker.
- User cancellation and permission/error paths.
- Large portrait/landscape images.
- Temporary-file disappearance after final response/cancel.

---

## 11. Stage 6 — weight Trends

### Work

- Add Vico only now. **Deviation:** not added; the chart is a Compose `Canvas` (see the
  open approval above).
- Add weight entry/edit UI with one upsert per local date. Trends logs the current local date
  and lists every logged day, and selecting a day edits that entry, so a wrong historical
  weight can be corrected. Deleting a weight entry is not offered.
- Keep the untouched input field synchronized with the stored value for the edited date, so a
  change made elsewhere cannot be written back, and resolve the save date from the clock while
  the editor follows today, so a screen left open across midnight logs the new day.
- Guard the persisted-weight invariant in `domain/usecase/LogWeight`, not in the screen.
- Render raw weight points.
- Implement the agreed calendar-window trend:

```text
trend(d) = mean(weight entries with localDate in [d - 6 days, d])
```

Do not interpolate missing dates and do not substitute the last seven measurements.
- Display kg/lb according to preferences while retaining kg in Room.
- Recalculate and replace today's target snapshot after today's weight changes.
- Add empty, one-point, two-point, and many-point chart states.

### Tests

- Calendar gaps.
- Month/year transitions.
- One entry per day and same-day replacement.
- Past-entry correction, day rollover with and without a lifecycle event, and a weight changed
  elsewhere.
- Serialized saves, and a draft started for another date while a save is in flight.
- A failed save reported for its own date, inline or as a snackbar naming that date.
- Real clicks on a history row, including the already selected day, reaching the editor.
- Rejected non-finite/non-positive weights.
- Unit conversion.
- Trend values and chart ViewModel states.
- White/Black visual regression.

### Acceptance

The raw series remains visible, the trend is deterministic, and changing units never
changes stored values.

---

## 12. Stage 7 — v1 History

### Work

- Query and group entries by local day.
- Group weeks with `WeekFields.ISO` and Monday start.
- Show daily/weekly consumed totals.
- Show daily progress against each immutable `DailyTargetSnapshot`.
- Add day detail with meal editing/deletion.
- Do not show meal categories or photos.
- Add localized empty/error/loading states.

### Tests

- ISO week across month/year boundaries.
- Time-zone/day-boundary behavior.
- Past snapshots remain unchanged after profile/weight/activity edits.
- Day/week totals after meal edits/deletes.
- DAO, ViewModel, Compose, and Roborazzi coverage.

### v1 acceptance gate

- Full sandbox gate is green.
- No secrets or real endpoint are committed.
- Manual Pixel 9a pass covers first launch, both locales, all theme modes, both unit
  systems, camera/picker, Today, Trends, History, rotation, and process recreation.
- Formula wording receives clinical review before being presented as a recommendation.
- Live LLM paths remain blocked until the external proxy exists; manual tracking must stay
  fully usable without it.

---

## 13. Stage 8 — v1 polish after visual references

- Apply approved references using existing screens and design-system tokens.
- Do not add features while restyling.
- Verify edge-to-edge and system bars.
- Verify large fonts, TalkBack labels, keyboard navigation, touch targets, long food names,
  and localized text expansion.
- Finalize White/Black Roborazzi baselines deliberately.
- Profile startup, Room queries, image memory, and chart performance only if a measured
  issue exists.

If references are not yet available, retain plain Material 3 rather than inventing a visual
language.

---

## 14. Stage 9 — v1.1 manual Insights

### Contract gate

Before code, extend `docs/llm-proxy-contract.md` with the exact
`POST /v1/insights/generate` request/response schema based on the finalized domain
`BuildPeriodStats`. Do not design it from model-provider payloads.

### Work

- Implement `BuildPeriodStats`; all arithmetic stays local.
- Add `InsightGenerator` fake and fixtures first, then remote transport.
- Add an explicit Generate action for completed day/week periods.
- Persist localized text, period, prompt version, token usage, and generation timestamp.
- Add Room v1→v2 migration and migration test.
- Render the medical disclaimer beneath every insight.
- Never schedule WorkManager/background generation.

### Acceptance

- The LLM receives finished numbers only.
- Generated text is in the interface language and within the approved length/tone.
- Failures do not affect journal/history data.
- Repeated generation occurs only after another explicit user action.

---

## 15. Dependency introduction order

| Stage | New dependency group |
|---|---|
| 1 | Compose, Navigation, Hilt, Room/KSP, DataStore, AppCompat, base tests |
| 4 | Ktor client/content negotiation/timeouts and test engine |
| 5 | Coil only if transient preview needs it |
| 6 | Vico — **not added**, the chart is hand-drawn on `Canvas` |
| 9 | No new production dependency expected |

Do not preload later-stage dependencies into the skeleton.

---

## 16. External inputs and blockers

| Input | Needed by | Status |
|---|---|---|
| Final visual references | Stage 8 | Not provided yet |
| Proxy backend implementation and endpoint | Live Stage 4/5 release | Not in scope yet |
| Proxy API key/quota policy | Live Stage 4/5 release | Not available yet |
| Captured scrubbed proxy responses | Backend integration verification | Contract examples used meanwhile |
| Clinical wording/formula review | v1 release | Required before release claims |
| Final insights stats contract | Stage 9 | Deliberately deferred |

Stages 1–3 and fake-driven Stage 4A are not blocked by any external input.

---

## 17. Main risks and mitigations

- **Predictive calorie error:** label targets as estimates, apply guardrails, expose
  effective rate, and allow profile updates.
- **Cross-store profile save:** use idempotent writes and repair today's missing snapshot;
  do not build a fake distributed transaction.
- **Historical drift:** immutable daily target snapshots.
- **LLM hallucination:** structured contract, validation, editable confirmation, no write
  before Confirm.
- **Proxy abuse:** static API key is not authentication; future backend must enforce quotas
  and cost caps.
- **Sensitive image retention:** temporary cache only, deterministic cleanup, no Room field,
  no body logging.
- **Locale recreation:** ViewModel/SavedStateHandle retains active form state while
  AppCompat applies locales.
- **Scope growth:** v1 stops at History; Insights and their schema remain v1.1.

---

## 18. Completion definition

The Android v1 client is complete when:

- Stages 1–8 are accepted in order;
- manual tracking works fully offline;
- first-run setup has no fabricated calculator values;
- historical goals do not change retroactively;
- no image survives its active request flow;
- EN/RU, metric/imperial, and System/White/Black modes pass manual verification;
- all required tests, lint, formatting, and screenshots pass;
- no secret, real endpoint, account/model identifier, or personal fixture is committed;
- remaining external blockers are explicitly listed in the final handoff.
