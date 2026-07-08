# Pitfalls Research

**Domain:** Android port (Kotlin + Compose + Room + Health Connect, local-only, no backend) of a shipped privacy-first iOS endometriosis tracker
**Researched:** 2026-07-08
**Confidence:** MEDIUM (Play policy + Health Connect + Room + crypto findings cross-checked against official Google docs and Play Console help; engine-port numeric specifics partly from language-spec knowledge, tagged inline)

Phase references use the 8 locked phases from `ANDROID-PORT-PLAN.md` Section 4:
P1 `:insights` port · P2 Persistence · P3 UI shell + consent · P4 Tracking screens · P5 Health Connect import · P6 Explore + charts · P7 PDF report · P8 Play Store prep.

> **One scheduling gap surfaced by this research:** the encrypted export/import mechanism (ANDROID-PORT-PLAN Section 2) is in v1 scope but has **no explicit home among the 8 phases**. The crypto/SAF pitfalls below (Pitfalls 12–13) assume it lands in **Phase 2 (format + identity) plus a dedicated slice before Phase 8**. The roadmap should make that home explicit rather than letting export become an untested Phase 8 afterthought.

---

## Critical Pitfalls

### Pitfall 1: Health Connect rationale activity wired for only ONE Android generation

**What goes wrong:**
The Health Connect permission dialog silently never appears (the permission launcher returns an empty grant set), or Play rejects the build. Teams declare the `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` intent filter and stop — but Android 14+ (where HC is built into the OS) uses a *different* hook: an `<activity-alias>` handling `android.intent.action.VIEW_PERMISSION_USAGE` with category `android.intent.category.HEALTH_PERMISSIONS`. Miss either one and one whole OS generation of users can't grant permissions; there is no crash, just a dead permission flow.

**Why it happens:**
Most tutorials predate Android 14; the two manifest declarations look redundant so one gets "cleaned up." The failure is invisible on the developer's own test device if it happens to be the covered generation.

**How to avoid:**
- Declare **both**: the rationale `<activity>` with the `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` filter (Android 13-) *and* the `<activity-alias>` with `VIEW_PERMISSION_USAGE` + `HEALTH_PERMISSIONS` category (Android 14+), pointing at the same privacy-policy screen.
- The rationale screen shows the actual privacy policy content — Play reviewers open it. It must match the Play Console privacy policy URL content (see Pitfall 4).
- Add a JVM structural test that parses the merged manifest and asserts both declarations exist (same spirit as the iOS structural source scans).

**Warning signs:**
Permission launcher resolves instantly with zero grants on one test device but works on another; "grant" button in Health Connect settings does nothing; Play pre-launch report flags the health permission flow.

**Phase to address:** P5 (build + structural test), re-verified in P8 (pre-launch report on both an Android 13 and an Android 14+ device).

---

### Pitfall 2: Treating the Play "health gauntlet" as a Phase-8 form-filling exercise

**What goes wrong:**
The app is code-complete and then stalls for weeks in rejection loops: "Inaccurate Health Apps Declaration," "Excessive data access for declared feature," missing medical disclaimer. Since the **January 2026 policy wave**, three items are hard blockers that cannot be fixed the week of submission:

1. **Individual developer accounts can no longer host health-category apps** (organization account + D-U-N-S verification required, deadline was 2026-01-28). If this project ships from a personal account, that is a milestone-level blocker, not a form field.
2. Non-certified apps must carry the disclaimer "This app is not a medical device and does not diagnose, treat, or prevent any condition" **in the first paragraph of the store description** — missing/buried disclaimers reject updates too.
3. Every requested Health Connect data type must be provably **essential to the primary function** ("incidental access" is banned). Reviewers compare the declaration form, the manifest permissions, and the observed app behavior.

**Why it happens:**
On iOS this project's compliance surface was App Review + usage strings; teams assume Play is similar-or-lighter. It is heavier for health: declaration form + Data safety form + HC access request + store-listing copy rules all have to agree with the manifest and with each other.

**How to avoid:**
- **At milestone start (not P8):** confirm the Play developer account type (organization + D-U-N-S). This is the single highest-latency item.
- Keep the HC permission set to exactly the four mapped read types (menstruation, sleep, exercise, nutrition) — each one maps 1:1 to a visible feature the reviewer can find (the import screen shows all four). Do not add "while we're here" read types; `NutritionRecord` especially invites the essentiality question, so the declaration rationale should name the food-logging feature explicitly.
- Draft the declaration-form answers and store description **during P5** while the permission rationale copy is being written — they must tell the same story.
- Store-listing copy goes through the same ClaimsPolicy audit as in-app copy (already planned) — the medical disclaimer sentence itself must be allow-listed since it contains words ("diagnose," "treat," "prevent") the forbidden-substring gate likely flags.

**Warning signs:**
Developer account shows "individual"; any manifest health permission without a screen that visibly uses it; store description draft where the disclaimer appears after the fold.

**Phase to address:** P8 owns the submission, but the account-type check happens at milestone kickoff, and the permission-minimalism discipline lands in P5.

---

### Pitfall 3: Health Connect's 30-day historical read window silently truncates the import

**What goes wrong:**
By default an app can only read the **past 30 days** of data other apps wrote to Health Connect (measured from first grant). For an endometriosis tracker whose whole pitch is longitudinal cycle history, a user who has 18 months of menstruation data in HC imports… one cycle. Development never notices because test data is freshly seeded.

**Why it happens:**
The restriction is on *other apps' historical data*, so any test where the developer writes samples right before reading passes. The extra permission (`PERMISSION_READ_HEALTH_DATA_HISTORY`) is easy to not know exists.

**How to avoid:**
- Decide explicitly in P5: request `READ_HEALTH_DATA_HISTORY` (justifiable — cycle history is the primary function) **and** write its justification into the Play declaration, or accept the 30-day window and say so honestly in the import UI ("imports the last 30 days").
- Given this app's nil-honest posture, the wrong outcome is a silent 30-day truncation presented as a complete import. Either request history access or label the window.
- Test with a seeded HC store whose records are older than 30 days (adjustable via the HC toolbox/dev options or a companion writer app).

**Warning signs:**
Import "works" but the earliest imported day is suspiciously recent; cycle-phase insights that had months of runway on iOS report under-sample withholding on Android for the same user.

**Phase to address:** P5 (permission decision + old-data test fixture), declaration consequences in P8.

---

### Pitfall 4: Data safety form vs. reality mismatches — for an app whose truth is "nothing leaves the device"

**What goes wrong:**
Two opposite failures:
(a) Over-declaring: marking health data as "collected" out of caution. Play's definition of *collected* is **transmitted off the device** — on-device-only processing is explicitly not collection. Over-declaring puts scary "shares health data" labels on a privacy-first app and invites review questions.
(b) Under-corroborating: declaring "no data collected" while the APK contains anything that phones home (a stray dependency, a crash SDK, even a WebView loading remote content). Play's automated analysis diffs the form against observed traffic and manifest — mismatch = rejection ("Data safety section" rejections are a common community complaint).

**Why it happens:**
The form's vocabulary ("collect," "share," "ephemeral") doesn't match engineering intuition, and third-party SDK traffic counts as *your* collection even when it goes to the SDK vendor's server.

**How to avoid:**
- Declare truthfully: **no data collected, no data shared** — this app qualifies cleanly *if* the dependency catalog stays as planned (no analytics, no backend).
- Strongest possible corroboration, available to almost no other app: **ship with no `INTERNET` permission at all.** Nothing in the locked plan needs it (Health Connect is local IPC, SAF is local, PDF share is an Intent to another app, privacy policy can open via the browser's intent, not a WebView). Add a manifest test asserting `INTERNET` is absent — it becomes the auditable artifact for the whole Data safety story, alongside the already-planned zero-`WRITE_*`-health-permissions test.
- User-initiated PDF/export sharing via `ACTION_SEND` does **not** count as collection/sharing (user-initiated, user-chosen destination) — don't let it scare the form answers.
- Health Connect data must still be described per the User Data policy in the privacy policy, and the privacy policy URL must be **identical and reachable** across Play Console, the in-app rationale screen, and the website (a public web page — PDFs are no longer accepted).

**Warning signs:**
Any line in `libs.versions.toml` whose artifact you can't explain; pre-launch report showing network calls; privacy-policy link that 404s or differs between Console and the rationale activity.

**Phase to address:** P8 (form + consistency check), with the no-`INTERNET` manifest test born in P3 when the manifest stabilizes.

---

### Pitfall 5: Health Connect availability assumed instead of runtime-gated

**What goes wrong:**
On Android 13 and below, Health Connect is a **separate installable app**, not part of the OS; on some devices it's absent or outdated. Calling `HealthConnectClient.getOrCreate()` without checking `getSdkStatus()` crashes or produces a dead import button for a large slice of the minSdk-26 audience. There are three states to handle, not two: available, **update required** (`SDK_STATUS_UPDATE_REQUIRED` — send the user to Play to update the provider), and unavailable.

**Why it happens:**
Developer devices run Android 14/15/16 where HC is built-in; the installable-app path never executes locally.

**How to avoid:**
- Gate every HC entry point on `getSdkStatus()`; on `UPDATE_REQUIRED` deep-link to the Health Connect Play listing; on `UNAVAILABLE` hide/soften the import feature entirely — manual logging is the primary path by design (locked decision), so the absence state must read as normal, not broken.
- Re-check status on each app foreground, not once at startup — the user can uninstall/update the provider between sessions.
- Emulator matrix must include one API 26–33 image without HC installed.

**Warning signs:**
`getOrCreate()` called anywhere without a preceding status check; import UI with only a two-state (available/absent) design.

**Phase to address:** P5.

---

### Pitfall 6: Caching Health Connect grant state, and hammering the API into rate limits

**What goes wrong:**
Two related failures.
(a) HC permissions can be revoked at any time from the Health Connect settings app, and Android's unused-app permission auto-reset can revoke them wholesale — an app that caches "granted" at onboarding throws `SecurityException` weeks later mid-import.
(b) HC enforces **periodic and daily API-call quotas** (stricter in background). An import that re-reads the full date range on every app open — the naive port of the iOS anchored-query mindset — burns quota and starts failing intermittently for exactly the heaviest users.

**Why it happens:**
HealthKit habits transfer badly: HealthKit authorization is ask-once and reads are effectively unmetered; HC is neither.

**How to avoid:**
- Call `getGrantedPermissions()` before every read session; treat a shrunken grant set as the graceful-denial path (already a locked UX decision — the plumbing must match it).
- Import incrementally: initial ranged read once, then **changes-token (`getChangesToken`/`getChanges`) differential sync** on subsequent foreground imports. Persist the token; handle token expiry by falling back to one ranged read.
- Since v1 has no background sync (locked), do **not** request `READ_HEALTH_DATA_IN_BACKGROUND` at all — it's extra declaration surface for a feature the plan excludes, and foreground-only stays under the friendlier quota.
- Wrap reads with a retry-after policy on `RemoteException`/rate-limit signals; an import that half-completes must be resumable, not duplicative (idempotent upsert by HC record `metadata.id` — see Pitfall 7).

**Warning signs:**
Any persisted boolean like `hcAuthorized`; import code path that issues one `readRecords` per day-of-range (classic quota burner); `READ_HEALTH_DATA_IN_BACKGROUND` appearing in the manifest.

**Phase to address:** P5.

---

### Pitfall 7: Dropping provenance and record identity on imported samples

**What goes wrong:**
The iOS design labels imported data ("from Apple Health") and enforces manual-wins merge. On Android, each HC record carries `metadata.dataOrigin` (source package) and a stable `metadata.id`. Ports that map HC records straight to domain values and discard metadata lose: (a) the per-source labels in UI and PDF report (an iOS invariant), (b) idempotent re-import (same record re-imported → duplicate rows → double-counted days corrupting the insights engine), and (c) the ability to delete-by-origin when the user disconnects import.

**Why it happens:**
The `insightsbridge` boundary (Room → `DaySnapshot`) is deliberately metadata-free, so it's tempting to strip metadata one layer too early — at ingestion instead of at the bridge.

**How to avoid:**
- The `ImportedSample`-equivalent Room row stores `hcRecordId` (unique index — Room allows it now, use the freedom) + `dataOrigin` package + record type. Upsert on `hcRecordId`.
- Manual-wins merge and "from <app>" labels read `dataOrigin`; the PDF per-source labels (P7) consume the same column.
- Changes-API deletions (`DeletionChange`) must delete the corresponding imported rows — HC data the user deleted at the source must not linger (privacy posture).

**Warning signs:**
Import round-trip test only run once (never re-run to prove idempotency); severity/sleep numbers that grow after re-importing the same range; import table without a unique index on the HC record id.

**Phase to address:** P5 (schema + idempotency test), consumed by P6 (labels) and P7 (report labels).

---

### Pitfall 8: Room schema-v1 discipline skipped because "we're greenfield"

**What goes wrong:**
Three compounding mistakes made in week one become unfixable by ship time:
1. `exportSchema = false` (or no `room.schemaLocation` KSP arg) → no schema JSON history → **auto-migrations impossible and `MigrationTestHelper` has no baseline forever after**. The planned "Room migration test scaffold" (P2 exit criterion) literally cannot be built later if schema 1 was never exported.
2. `fallbackToDestructiveMigration()` left in from a tutorial → first post-launch schema bump **silently wipes the user's entire symptom history**. For this app that's not a bug, it's a GDPR-relevant data-loss incident and an app-review-worthy betrayal.
3. KSP version drift: the KSP plugin version must match the Kotlin compiler version exactly (`<kotlinVersion>-x.y.z` scheme); mismatches produce baffling build errors typically at the worst moment (a Kotlin upgrade mid-milestone).

**Why it happens:**
Greenfield projects have no migrations, so migration discipline feels like premature ceremony — but the exported schema history must start at v1, and destructive fallback only detonates after the first real users exist.

**How to avoid:**
- Day one: `exportSchema = true`, `room.schemaLocation` wired through KSP, `schemas/` directory **committed to git**, and registered as an androidTest asset source-set for `MigrationTestHelper`.
- Ban `fallbackToDestructiveMigration*` with a structural JVM test (grep the source, same pattern as the iOS notification scan). There is no acceptable use in a health app.
- Pin Kotlin + KSP + Room versions together in `libs.versions.toml` with a comment noting the coupling.
- Never enable `allowMainThreadQueries()`; all DAO methods `suspend` or `Flow` from the start — retrofitting threading discipline into a repository consumed by Compose is much costlier than starting right.

**Warning signs:**
No `schemas/` directory in git after the first P2 commit; `fallbackToDestructiveMigration` anywhere; a Kotlin version bump PR that doesn't touch the KSP version.

**Phase to address:** P2 (all of it — the exit criterion already names the migration scaffold; these are its prerequisites).

---

### Pitfall 9: Delete-day cascade rebuilt as N sequential DAO calls instead of one transaction

**What goes wrong:**
The iOS lesson (orphaned `FactorReading` after `deleteDay` — a real GDPR Art. 17 bug, fixed in iOS 04-02) gets carried over as a checklist ("delete every day-scoped type") but implemented as sequential suspend calls. A process death or exception mid-cascade leaves exactly the orphan the iOS bug produced — the checklist was honored, the atomicity wasn't. Room adds a second trap iOS didn't have: `ForeignKey.CASCADE` makes deletion look free, but it only covers rows that actually declare the FK, and silently stops protecting new tables added without one.

**Why it happens:**
The lesson is remembered as a *coverage* problem (which types) when it was also an *atomicity* problem waiting to happen.

**How to avoid:**
- One `@Transaction` DAO method owns the entire cascade.
- The planned born-complete cascade test gets a twin: a **completeness meta-test** that reflects over all `@Entity` classes, identifies day-scoped ones (by a marker interface or a `dayEpoch` column convention), and fails when a new day-scoped entity isn't covered by the cascade — so the test can't go stale when P4/P5 add tables (`ImportedSample` is day-scoped too: delete-day must purge imported rows for that day).
- Definition-survival guard (Medication survives) as already planned.

**Warning signs:**
Cascade implemented in the repository layer rather than a `@Transaction` DAO; cascade test list hand-enumerating entities; `ImportedSample` missing from the cascade when P5 lands.

**Phase to address:** P2 (transactional cascade + meta-test), re-verified when P5 adds imported rows.

---

### Pitfall 10: Engine port passes happy-path parity but diverges on numeric edge cases

**What goes wrong:**
The Kotlin `:insights` port passes all ported tests, then produces *different insight output* than iOS for real users, because the two platforms disagree exactly where the test fixtures are thin:

- **NaN ordering:** Swift's `<` leaves NaN unordered (sorting a NaN-bearing array via `<` is undefined-ish behavior); Kotlin's `sortedBy`/`compareTo` **totally orders** NaN greater than `+∞`, and `-0.0 < 0.0`. A rank-based statistic (Mann-Whitney U ranks!) that ever sees a NaN behaves differently per platform.
- **Equality semantics:** Kotlin `data class` `equals()` uses `Double.equals` boxing semantics — `NaN == NaN` is **true**, `0.0 == -0.0` is **false** — while Swift `==` on Double is IEEE (`NaN != NaN`, `0.0 == -0.0`). Any de-dup, `distinct()`, or map-key use of a Double-bearing value type can diverge. (Confidence: HIGH — language-spec level.)
- **Sort stability:** both Swift's sort (Timsort since Swift 5) and Kotlin's `sortedBy` (JVM Timsort) are stable for *objects* — but Kotlin's **primitive** `DoubleArray.sort()` is dual-pivot quicksort, *not stable*. Tie-breaks in "top correlation" or "strongest trigger" selection can differ if the port drops to primitive arrays for speed. Determinism of pick-the-max among ties must be pinned by an explicit secondary sort key, never by sort-order luck.
- **Rounding ties:** Swift `rounded()` is half-away-from-zero; JVM `Math.round` is half-up (toward +∞ — differs for negative halves); `Math.rint` is half-even. The JVM default matches Swift's on **none** of the tie cases across signs. Severity averages displayed as "3.5" can flip to "3" vs "4" per platform.
- **Locale:** `String.format("%.1f", x)` on a German device renders `3,5`; Swift code used explicit formatting. Any formatted number inside registered copy strings must pass `Locale.ROOT` (or the UI locale *deliberately*), or the copy audit and PDF diverge by device language.

**Why it happens:**
Ported test suites port the *fixtures* too — and the iOS fixtures were written against Swift's semantics, so they never probed the disagreement zone.

**How to avoid:**
- Phase 1's "case-count parity" exit criterion is necessary but not sufficient. Add a small **cross-platform divergence fixture pack**: NaN-in-series, ±0.0, exact .5 ties (positive and negative), tied correlation strengths, empty/single-element series. Where iOS behavior is intentional, encode it; where NaN should be impossible, assert the engine *rejects/filters* NaN at the boundary instead (the stronger fix — make NaN unrepresentable in `DaySnapshot`).
- Prefer boxed/stable `sortedBy` with explicit tie-break keys over primitive sorts everywhere ranks or selections happen.
- Ban raw `String.format` without a locale via a structural test (same grep-test pattern the project already uses).

**Warning signs:**
Parity suite green but zero fixtures containing NaN/±0.0/.5 ties; `DoubleArray.sort()` in ranking code; `String.format(` without a `Locale` argument.

**Phase to address:** P1 (divergence fixtures + NaN-unrepresentable boundary), locale-format ban re-checked in P6/P7 where numbers become copy.

---

### Pitfall 11: Day-boundary and DST math re-derived from epochs instead of `java.time` day identity

**What goes wrong:**
The iOS `CyclePhaseMapper` handles day-boundary math explicitly with `Calendar`. Kotlin ports that reach for `epochMillis / 86_400_000` or `Instant.plus(1, DAYS)` reintroduce every bug that code existed to prevent: DST-shift days are 23 or 25 hours; in some zones (e.g. Brazil historically) **midnight itself doesn't exist** on spring-forward day; UTC-day bucketing shifts a user's evening log to "tomorrow." Cycle-day arithmetic being off by one shifts every phase window — a silent correctness failure in the app's centerpiece.

There is a second, subtler port hazard: Health Connect records arrive as `Instant` + optional `zoneOffset`, while HealthKit gave local-time-anchored samples. A sleep session spanning midnight, or a menstruation record written by an app in another timezone (travel), must bucket to days using **the same rule iOS uses**, or the two platforms will disagree about which day a period started — visible to any user comparing devices.

**Why it happens:**
Epoch math looks harmless in a pure-JVM module with no Android imports (the boundary lint even encourages avoiding platform types — and `java.time` *is* allowed: it's JDK, not `android.*`, and minSdk 26 supports it natively with no desugaring).

**How to avoid:**
- Day identity throughout `:insights` is `LocalDate` (or an epoch-**day** integer derived from `LocalDate.toEpochDay()`), never a timestamp. Conversions from instants happen only at the `:app` boundary (`insightsbridge`, `health/`) via `instant.atZone(zone).toLocalDate()`; start-of-day only via `LocalDate.atStartOfDay(zone)` (handles nonexistent midnights).
- Extract the iOS bucketing rule for HC-imported spans (which zone? session-start-based or overlap-based?) into a written rule + shared test vectors including: DST spring-forward day, fall-back day, midnight-spanning sleep session, and a record with a `zoneOffset` different from the device zone.
- Add a structural test to `:insights` banning `System.currentTimeMillis` and `86400` literals.

**Warning signs:**
Any `86400`/`86_400_000` constant; `Instant` appearing inside `:insights` domain types; cycle-day tests that only run in UTC (CI default!) — set at least one test run to a DST-observing zone like `Europe/Berlin` and one to `Pacific/Apia`.

**Phase to address:** P1 (day-identity types + DST fixtures), P5 (HC instant→day bucketing rule + travel/DST vectors).

---

### Pitfall 12: AES-GCM export that encrypts correctly and still fails users — nonce, KDF, and format-versioning mistakes

**What goes wrong:**
The classic trio for passphrase-encrypted export files:
1. **Nonce reuse:** reusing a GCM nonce under the same key is catastrophic (auth-subkey recovery → tag forgery, plaintext XOR leakage). Passphrase-derived keys make this worse: if the salt is fixed, the *key* repeats across exports, so the nonce must never repeat across all exports ever made.
2. **Weak KDF parameters:** a default-ish PBKDF2 iteration count (10k) makes the export crackable offline; the file's whole threat model is "user parks it in their own Drive," i.e., offline attack is *the* attack. OWASP's current PBKDF2-SHA256 guidance is ~600k iterations; `PBKDF2WithHmacSHA256` is available from API 26 (matching minSdk). Argon2id is stronger but requires a dependency — a real tension with the zero-dependency posture that should be decided consciously, not defaulted.
3. **No self-describing header:** an export without `{formatVersion, kdf, iterations, salt, nonce}` in a plaintext header can never evolve its crypto without breaking old files — and import can't distinguish "wrong passphrase" from "corrupt file" from "future version," which matters for honest UX (wrong-passphrase must be a calm, specific message, GCM tag failure is the signal).

**Why it happens:**
Crypto examples online optimize for brevity; the versioned-envelope discipline is boring and invisible until the second release.

**How to avoid:**
- Fresh random 12-byte nonce **and** fresh random 16-byte salt per export from `SecureRandom` (fresh salt → fresh key → nonce-reuse risk collapses); both stored in the header.
- Pick KDF params by measured time on a low-end device (target ≳ a few hundred ms), record them in the header per-file so future param bumps don't strand old exports.
- Do not use Jetpack Security `EncryptedFile` (deprecated, and Keystore-bound keys defeat the purpose — the file must decrypt on a *different* device from the passphrase alone).
- Encrypt/decrypt streaming or chunked if exports can exceed a few MB of JSON; a single `doFinal` on a huge byte array invites OOM on 2GB devices.
- Golden-file test: a committed fixture export (fixed key/nonce for the fixture only) that every future version must still import — the format contract test.

**Warning signs:**
`ByteArray(12)` zero-IV or IV derived from anything deterministic; iteration count under six figures without a written rationale; export bytes that start with `{"` (header is fine plaintext, ciphertext must not be recognizable JSON); no committed golden export fixture.

**Phase to address:** P2 defines the format + UUID identity (already locked); the crypto implementation needs an explicit roadmap home (recommend a P2 extension or dedicated slice before P8 — see the note at top).

---

### Pitfall 13: SAF write quirks that corrupt or duplicate the export file

**What goes wrong:**
Two SAF behaviors bite exactly this feature:
1. `ACTION_CREATE_DOCUMENT` **cannot overwrite**: picking an existing filename yields a new document `export (1).json.enc` — users doing weekly backups accumulate confusing siblings, or worse, restore from a stale one.
2. `contentResolver.openOutputStream(uri)` with the default mode **may not truncate** an existing document on all providers: writing a *shorter* ciphertext over a longer old one leaves trailing stale bytes → GCM tag failure on import → the user is told their backup is corrupt when the app corrupted it. Use mode `"wt"` (write-truncate) explicitly, and treat providers that reject `"wt"` by writing to a fresh document instead.

**Why it happens:**
SAF providers (Drive vs. local vs. Samsung MyFiles) implement modes inconsistently; the happy path works on the emulator's local provider.

**How to avoid:**
- Always `openOutputStream(uri, "wt")`; verify by writing a long file then a short one in an instrumented test.
- Suggest date-stamped default filenames (`endo-export-2026-07-08.enc`) so "(1)" collisions don't arise and stale-restore confusion drops.
- After writing, read back and verify the GCM tag (a full decrypt round-trip) *before* telling the user the backup succeeded — the only honest success message.
- Import side: never trust the filename/extension; sniff the header magic + version.

**Warning signs:**
`openOutputStream` with no mode argument; export tests that only ever write to a fresh URI; success toast shown before the stream is flushed/closed/verified.

**Phase to address:** Same home as Pitfall 12 (the export slice); the read-back verification doubles as the format contract test.

---

### Pitfall 14: Compose chart layer recomposes the world on every scrub frame

**What goes wrong:**
The Explore surface (multi-lane overlays + scrub tooltip) is the perf-riskiest UI in the port. Three compounding Compose traps:
1. `List<DayPoint>` parameters are **unstable** to the Compose compiler → every chart recomposes whenever its parent does, even with identical data.
2. Scrub gestures held in `mutableStateOf` read during *composition* → 60–120 recompositions/sec of the whole chart while the finger moves — the exact "recomposition storm" the question anticipates.
3. Rebuilding `Path` objects inside `DrawScope` every frame → allocation churn and jank on long series.

This is also precisely the failure mode that would trigger the plan's Canvas→Vico escape hatch *prematurely*: the symptoms look like "Canvas is too slow" when they're actually recomposition misuse — Vico would not fix them.

**Why it happens:**
Chart data naturally flows as `List` from the repository; gesture→tooltip is naturally modeled as state; both defaults are wrong for draw-heavy surfaces.

**How to avoid:**
- Chart inputs are `@Immutable`-annotated holder classes (or kotlinx-immutable collections) produced once by the chart data-prep layer — which conveniently is the same layer the honesty-rule tests already pin (P6 exit criterion), so stability and honesty live in one tested seam.
- Scrub position lives in state that is **read only inside the draw phase** (`drawWithCache`/`drawBehind` lambda or `graphicsLayer` lambda), so finger movement invalidates *draw*, not composition/layout. Tooltip text updates via `derivedStateOf` snapping to day granularity (the iOS day-change haptic boundary maps to the same snap).
- Build lane `Path`s in `drawWithCache` keyed on the series + size; only the scrub rule/lollipop draws per-frame.
- Verify with Layout Inspector recomposition counts + the compiler metrics report *before* invoking the Vico escape hatch; the escape hatch decision (locked as revisitable) should cite these numbers.

**Warning signs:**
Recomposition counts climbing during scrub in Layout Inspector; compiler metrics marking chart composables "skippable: false"; `Path()` constructed inside `onDraw`; frame drops only on the longest-history test profile.

**Phase to address:** P6 (and the data-prep stability contract is testable there alongside the honesty rules).

---

### Pitfall 15: Monorepo coexistence — the Android build quietly breaks iOS guarantees (or CI builds everything, always)

**What goes wrong:**
- Without CI path filters, every Android commit runs the full iOS lane (macOS runners — the expensive ones) and vice versa; feedback slows and people start skipping CI.
- A root-level `.gitignore` tuned for Xcode starts ignoring Android's `local.properties`… or *fails* to ignore `android/.gradle/`, `android/app/build/`, and megabytes of build junk land in a commit.
- Shared-vocabulary drift: the plan's core monorepo rationale is that **ClaimsPolicy substring lists must not drift between platforms** — but if the Kotlin port copies the Swift lists by hand, they drift silently on the first iOS copy-policy update, and both test suites stay green while enforcing different policies. The monorepo's main benefit evaporates without a mechanism.
- The "iOS untouched" guarantee is asserted, not enforced: an Android-lane tool (formatter, license header bot, aggressive IDE) touches `EndoApp/` and nobody notices until the next iOS build.

**Why it happens:**
Coexistence is nobody's feature; it's configured once in P1 by someone focused on the engine port.

**How to avoid:**
- P1's very first commits: `android/`-scoped `.gitignore` section (`.gradle/`, `build/`, `local.properties`, `.kotlin/`), committed Gradle wrapper, and CI workflows with `paths:` filters (`android/**` → Android lane; existing iOS lane gets `paths-ignore: android/**`).
- **Single source of truth for the claims vocabulary:** extract the substring lists to a platform-neutral data file (JSON/plaintext) at repo root or `shared/`, loaded by the Swift `CopyAuditTests` *and* the Kotlin `CopyAudit` test — or, if touching iOS is truly forbidden this milestone, a cheaper cross-check test on the Kotlin side that parses `ClaimsPolicy.swift` and asserts list equality, failing loudly when iOS updates its lists. Either way: drift must be red, not a convention. (Also carry the user-memory lesson: new Android user-facing copy must both dodge the substrings *and* register for audit in the same change.)
- A lightweight CI guard on Android-lane PRs asserting no diffs under `EndoApp/`, `EndoInsights/`, `project.yml` (enforces "iOS untouched" mechanically).
- Keep JDK/AGP versions pinned in the repo (`libs.versions.toml` + `.sdkmanrc`/toolchain block) so the Android build doesn't depend on machine state the iOS-focused contributors don't have.

**Warning signs:**
macOS CI minutes spiking after Android work starts; `local.properties` or `build/` in `git status`; an iOS ClaimsPolicy change merged with no corresponding Android-side failure.

**Phase to address:** P1 (CI, gitignore, wrapper, claims single-source), guard re-verified every phase by construction.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| `fallbackToDestructiveMigration()` during development | No migration writing while schema churns | Ships accidentally → wipes user health history on first schema bump | Never in committed code; use fresh installs/emulator wipes during dev instead |
| Skipping schema export "until the schema settles" | Less git noise in P2 | Migration tests impossible forever (no v1 baseline) | Never — export from the first `@Database` version |
| Hand-copying ClaimsPolicy substrings into Kotlin | Unblocks P1 without touching shared tooling | Silent policy drift between platforms — defeats the monorepo's stated purpose | Acceptable for days, only with the list-equality cross-check test landing in the same phase |
| Caching HC grant state in DataStore | Fewer async calls before reads | `SecurityException` after settings-app revocation or auto-reset | Never — `getGrantedPermissions()` is cheap and per-session |
| Requesting `READ_HEALTH_DATA_HISTORY` "later, if users complain" | Smaller P8 declaration | Users' multi-year cycle history silently truncated to 30 days — invisible complaint | Only if the import UI explicitly labels the 30-day window |
| Single `cipher.doFinal(wholeJson)` for export | Simple crypto code | OOM on low-RAM devices as history grows over years | Fine at v1 sizes **if** a size guard + streaming fallback note exists |
| Chart data as plain `List` params "optimize later" | Faster P6 start | Recomposition storms diagnosed late look like "Canvas too slow" → premature Vico switch | Acceptable through first chart spike; must be fixed before scrub lands |
| One CI workflow, no path filters | One YAML file | Every Android push burns macOS minutes; slow feedback normalizes skipping CI | First week only |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Health Connect (permissions) | Only the Android-13-era rationale intent filter declared | Both `ACTION_SHOW_PERMISSIONS_RATIONALE` activity **and** Android-14 `VIEW_PERMISSION_USAGE` + `HEALTH_PERMISSIONS` activity-alias; structural manifest test |
| Health Connect (reads) | Full-range re-read every app open | Initial ranged read once → persisted changes-token differential sync; retry-after on rate limit |
| Health Connect (history) | Assuming reads cover all history | 30-day window without `READ_HEALTH_DATA_HISTORY`; decide + declare in P5/P8 |
| Health Connect (identity) | Mapping records to domain rows without HC `metadata.id`/`dataOrigin` | Unique-indexed `hcRecordId` upsert + stored `dataOrigin` for labels, manual-wins merge, and deletion propagation |
| Play Console (health declaration) | Filling forms after code-freeze | Draft declaration + store description during P5; disclaimer in first paragraph; org-account check at kickoff |
| Play Console (Data safety) | Over-declaring on-device processing as "collected" | "Collected" = leaves the device; declare none; corroborate with no-`INTERNET`-permission manifest test |
| SAF | `openOutputStream(uri)` default mode over existing file | Mode `"wt"`; read-back decrypt verification before success message |
| Share Intent (PDF) | Exposing the PDF via `file://` or world-readable path | `FileProvider` + `FLAG_GRANT_READ_URI_PERMISSION`, file in `cacheDir`, cleaned up after share |
| Room + KSP | KSP plugin version drifting from Kotlin version | Pin together in `libs.versions.toml`; bump in the same PR always |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Unstable chart params → full recomposition | Layout Inspector counts climb on unrelated UI changes | `@Immutable` holders from the tested data-prep layer | Any chart with a recomposing parent (i.e., immediately) |
| Scrub state read in composition | Jank while finger moves; batteries of recompositions per second | Read scrub state only in draw phase; `derivedStateOf` day-snapping for tooltip text | First scrub gesture on a long series |
| `Path` rebuilt per frame | GC churn, dropped frames on multi-lane Explore | `drawWithCache` keyed on series+size | ~1 year of daily logs across 4+ lanes |
| Per-day HC `readRecords` calls | Intermittent import failures (rate limit), long import times | Ranged reads + changes tokens | Heavy HC users / long histories — the app's best users |
| PBKDF2 on main thread | ANR on export/import (600k iterations ≈ hundreds of ms) | KDF on `Dispatchers.Default`; progress UI | Every export on mid/low-end devices |
| Insights recompute on main thread | UI freeze opening dashboard as history grows | Compute in `:insights` off-main via coroutines; cache like iOS `CachedInsight` | Years of data + correlation matrix |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| GCM nonce reuse (fixed IV / deterministic IV) with passphrase-derived key | Tag forgery + plaintext leakage across a user's backups | Fresh random salt **and** nonce per export, both in header; test asserts two exports of same data differ everywhere past the header |
| Low PBKDF2 iterations / SHA-1 default | Offline cracking of a file designed to live in cloud storage | ≥ OWASP-current iterations (~600k PBKDF2-SHA256) measured on low-end hardware; params recorded in header |
| Keystore-bound key for export (e.g., `EncryptedFile`) | Backup undecryptable on the replacement phone — defeats the feature | Passphrase-derived key only; document that passphrase loss = data loss (honest UX) |
| Health DB included in Android Auto Backup | Opaque Google-account-tied copy of special-category data without deliberate consent | `dataExtractionRules`/`fullBackupContent` excluding the Room DB (locked decision — enforce with a test parsing the backup rules XML) |
| PDF left world-accessible after share | Other apps read the health report from external storage | `cacheDir` + `FileProvider` URI grants only; delete after share completes |
| Exported file recognizable/greppable as health data | Cloud scanners / shoulder-surfers identify condition data | Ciphertext body (header carries only crypto params, no data type names); neutral filename default |
| Adding `INTERNET` permission "for the privacy policy WebView" | Undermines the strongest no-data-leaves-device audit artifact | Open policy URL via browser Intent; manifest test asserts no `INTERNET` |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Import silently truncated to 30 days | User concludes the app "lost" their history; trust gone | Request history permission with rationale, or label the window explicitly |
| Nagging after HC permission denial (Android *can* detect denial, unlike iOS) | Pressure on users managing a stigmatized condition | Locked decision honored in code: denial renders gently, manual logging unaffected, no re-prompt loops — use denial knowledge only to skip pointless reads |
| "Backup saved" before write verified | User's only backup is corrupt; discovered at restore time (new phone, old one gone) | Read-back decrypt verification before the success state |
| Wrong-passphrase vs corrupt-file conflated on import | Panic ("my data is gone") when it's a typo | GCM tag failure + header checks mapped to distinct calm messages |
| HC-unavailable state rendered as an error | Android-13 users feel the app is broken | Import presented as optional enhancement; absence state is quiet, manual logging is the hero path |
| Play pre-launch robo-crawler stuck at consent gate | Empty pre-launch report screenshots; missed real issues | Fine to keep the gate (consent is non-negotiable); optionally provide pre-launch credentials/deep-link config, and don't panic-read the report as failure |

## "Looks Done But Isn't" Checklist

- [ ] **HC permission flow:** works on the dev phone — verify on *both* an Android 13 (installable HC) and Android 14+ (built-in) device, plus a no-HC emulator.
- [ ] **Import:** first import works — verify **re-import idempotency** (run twice, counts identical) and **deletion propagation** (delete at source → gone after sync).
- [ ] **Import completeness:** works with fresh test data — verify with records **older than 30 days**.
- [ ] **Migration scaffold:** schema compiles — verify `schemas/1.json` is committed and `MigrationTestHelper` opens it in androidTest.
- [ ] **Delete-day:** cascade test green — verify it's a single `@Transaction` and the meta-test fails when a fake new day-scoped entity is added.
- [ ] **Engine parity:** case counts match — verify divergence fixtures (NaN, ±0.0, .5 ties, tied ranks) exist and pass.
- [ ] **Charts:** look right — verify Layout Inspector recomposition counts are flat during scrub, and compiler metrics show chart composables skippable.
- [ ] **Export:** encrypt/decrypt round-trips — verify shorter-over-longer SAF rewrite, wrong-passphrase message, and the committed golden fixture.
- [ ] **PDF:** renders — verify text is selectable (vector) in a PDF viewer, per-source labels present, file not world-readable post-share.
- [ ] **Play forms:** submitted — verify privacy-policy URL identical in Console / rationale activity / website, disclaimer in first store-description paragraph, org account verified.
- [ ] **Manifest invariants:** app builds — verify tests assert: zero `WRITE_*` health permissions, no `INTERNET`, no `READ_HEALTH_DATA_IN_BACKGROUND`, both rationale declarations present, backup rules exclude the DB.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Shipped without schema export | HIGH | Reconstruct v1 schema JSON from the shipped `@Entity` definitions at the exact release commit; commit as baseline; audit any interim change by hand |
| Destructive migration wiped user data | UNRECOVERABLE (per user) | Only mitigation is prior: export/backup feature + the structural ban. If it ships: pull release, honest disclosure |
| Play rejection loop (health declaration) | MEDIUM | Respond via the declaration appeal with a feature-to-permission mapping table + screen recordings of each HC data type's visible use; strip any permission you can't screencast |
| Individual account blocks health listing | HIGH (weeks) | Create org account + D-U-N-S, transfer app — start this the moment it's discovered; nothing else unblocks it |
| Nonce/KDF flaw found post-ship | MEDIUM | Header version bump: new exports use fixed crypto; importer keeps reading old version but re-export prompts; publish honest note |
| Engine divergence found post-parity | LOW-MEDIUM | Add the failing case to the shared divergence fixture pack first, then fix — the fixture pack is the cross-platform contract |
| Canvas perf wall in P6 | MEDIUM | Exercise the *locked* Vico escape hatch — but only after Layout Inspector proves it's draw cost, not recomposition misuse (Pitfall 14) |
| Rate-limited mid-import | LOW | Persist changes-token progress; resume on next foreground; never restart from scratch |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1. Dual rationale declarations | P5 | Manifest structural test; permission dialog appears on Android 13 & 14+ devices (P8 pre-launch) |
| 2. Play health gauntlet as afterthought | Kickoff + P5 + P8 | Org account verified at kickoff; declaration drafted in P5; P8 exit = forms consistent with manifest |
| 3. 30-day history truncation | P5 (P8 declaration) | Import test fixture with >30-day-old records; explicit permission-or-label decision recorded |
| 4. Data safety mismatch | P3 (manifest test) + P8 | No-`INTERNET` manifest test green; form states no collection; pre-launch shows zero network traffic |
| 5. HC availability gating | P5 | Three-state handling test (available/update/absent); no-HC emulator in matrix |
| 6. Grant caching + rate limits | P5 | No persisted grant flag (structural scan); changes-token sync test; no background-read permission in manifest |
| 7. Provenance/identity loss | P5 (used by P6, P7) | Re-import idempotency test; dataOrigin label rendered in UI + PDF; deletion propagation test |
| 8. Room v1 discipline | P2 | `schemas/1.json` committed; destructive-fallback ban test; KSP/Kotlin pinned together |
| 9. Cascade atomicity | P2 (re-checked P5) | `@Transaction` cascade + entity-reflection meta-test; ImportedSample covered when P5 lands |
| 10. Numeric divergence | P1 (P6/P7 locale) | Divergence fixture pack (NaN/±0.0/ties) green; no-`String.format`-without-Locale scan |
| 11. Day-boundary/DST math | P1 + P5 | `LocalDate`-only day identity in `:insights`; DST/travel test vectors; tests run in non-UTC zones |
| 12. Export crypto | P2 + export slice (pre-P8) | Header-versioned format; two-exports-differ test; measured KDF params; golden fixture |
| 13. SAF write quirks | Export slice | `"wt"` mode; shorter-over-longer rewrite test; read-back verification before success UI |
| 14. Chart recomposition storms | P6 | Layout Inspector flat during scrub; compiler metrics skippable; `@Immutable` data-prep contract tested with honesty rules |
| 15. Monorepo/CI/claims drift | P1 (continuous) | Path-filtered workflows; iOS-untouched CI guard; claims-list single source or equality cross-check test |

## Sources

- [Play Console Help — Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en) — declaration requirements — MEDIUM (official, via search)
- [My App Monitor — Google Play Health Apps Update: January 2026 Requirements](https://myappmonitor.com/blog/google-play-health-apps-update-2026-requirements) — org-account deadline, first-paragraph disclaimer, essentiality rule, privacy-URL consistency — LOW-MEDIUM (secondary; cross-check items in Play Console before P8)
- Google Play Developer Community threads — [repeated "Inaccurate Health Apps Declaration" rejections](https://support.google.com/googleplay/android-developer/thread/288434034), [HC permissions rejected during app analysis](https://support.google.com/googleplay/android-developer/thread/363615739), ["Excessive data access for declared feature"](https://support.google.com/googleplay/android-developer/thread/398224811) — real rejection patterns — MEDIUM (primary reports)
- [Provide information for Google Play's Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) — "collected = off-device", SDK traffic counts — MEDIUM (official)
- [Health Connect — Get started](https://developer.android.com/health-and-fitness/health-connect/get-started) — dual rationale declarations (13- activity / 14+ activity-alias) — MEDIUM (official)
- [Health Connect — Read raw data](https://developer.android.com/health-and-fitness/health-connect/read-data) & [Plan to avoid rate limiting](https://developer.android.com/health-and-fitness/health-connect/rate-limiting) — 30-day window, `READ_HEALTH_DATA_HISTORY`, background permission/feature check, quota + changes-token guidance — MEDIUM (official)
- [Android Health Permissions: Guidance and FAQs](https://support.google.com/googleplay/android-developer/answer/12991134?hl=en) — permission approval process — MEDIUM (official)
- [Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions) — exportSchema, auto-migration limits, destructive fallback, IllegalStateException — MEDIUM (official)
- [ProAndroidDev — Overcoming Common Performance Pitfalls in Jetpack Compose](https://proandroiddev.com/overcoming-common-performance-pitfalls-in-jetpack-compose-98e6b155fbb4) + [Compose graphics modifiers docs](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers) — unstable collections, draw-phase state reads, drawWithCache — MEDIUM (cross-checked with official docs)
- [SwiftRocks — Timsort and Introsort: Swift's sorting algorithms](https://swiftrocks.com/introsort-timsort-swifts-sorting-algorithm.html) + [Kotlin stdlib `sortedBy` docs](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/sorted-by.html) — sort stability on both platforms — MEDIUM; NaN/equality/rounding semantics from JLS/Kotlin/Swift language specs — HIGH (spec-level), flagged inline
- [java-crypto/cross_platform_crypto — AES-GCM + PBKDF2 string encryption](https://github.com/java-crypto/cross_platform_crypto/blob/main/docs/aes_gcm_256_pbkdf2_string_encryption.md) + [Daniel Hugenroth — Password hashing on Android](https://www.danielhugenroth.com/posts/2021_06_password_hashing_on_android/) — KDF parameters, cross-platform format — MEDIUM
- [Google IssueTracker #37136466 — SAF ACTION_CREATE_DOCUMENT cannot overwrite](https://issuetracker.google.com/issues/37136466) — "(1)" filename behavior — MEDIUM (primary bug tracker)
- [OneUptime — Monorepo path filters in GitHub Actions](https://oneuptime.com/blog/post/2025-12-20-monorepo-path-filters-github-actions/view) + [Just Eat Takeaway — iOS Monorepo & CI Pipelines](https://medium.com/justeattakeaway-tech/ios-monorepo-ci-pipelines-99a3b69240a9) — CI cost/path-filter patterns — LOW-MEDIUM (practitioner reports)
- iOS project internals (`.planning/PROJECT.md`, `ANDROID-PORT-PLAN.md`, iOS 04-02 delete-day lesson, copy-audit substring gate memory) — HIGH (first-party)

---
*Pitfalls research for: Endo-App v2.0 Android port (health-data app added to an iOS-first monorepo)*
*Researched: 2026-07-08*
