# Phase 26: Shared l10n Foundation - Pattern Map

**Mapped:** 2026-10-31
**Files analyzed:** 27 (new/modified, from 26-RESEARCH.md §Recommended Project Structure + Pitfalls 4/5)
**Analogs found:** 22 / 27

All analog paths below are git-tracked source in this worktree (iOS sources here are identical to `main`).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `l10n/claims-policy.{de,fr,es}.json` | config/data | static data | `android/insights/src/test/resources/stats-goldens.json` (shared JSON fixture) | partial |
| `l10n/glossary.json` | config/data (test-only) | static data | same | partial |
| `l10n/README.md` | docs | — | none | none |
| `android/insights/build.gradle.kts` (MODIFY) | config | build | itself (lines 26-28 `tasks.test`) | exact |
| `android/app/build.gradle.kts` (MODIFY) | config | build | itself (lines 37-42 `testOptions.unitTests`) | exact |
| `.github/workflows/android.yml` (MODIFY) | config | CI | itself (lines 6-13 `paths:`) | exact |
| `android/insights/.../l10n/PolicyLanguage.kt` | model | — | `insights/Insight.kt` lines 29-46 (`@Serializable enum class X(val raw: String)`) | exact |
| `android/insights/.../l10n/L10nText.kt` | utility | transform | `insights/ForbiddenTerms.kt` lines 99-104 (`violation`) | role-match |
| `android/insights/.../l10n/LanguageClaimsPolicy.kt` | model + matcher | transform | `insights/Insight.kt` (@Serializable) + `ForbiddenTerms.violation` | role-match |
| `android/insights/.../l10n/LanguageRules.kt` | utility | transform | `ForbiddenTerms.kt` | role-match |
| `android/insights/.../l10n/Glossary.kt` | model | transform | `Insight.kt` | role-match |
| `android/insights/.../l10n/ForbiddenTermsLocalized.kt` | service (loader) | file-I/O (classpath) | `insights/SpecialFunctionsGoldenTests.kt` lines 29-33 (lazy classpath JSON load) | role-match |
| `android/insights/src/test/.../l10n/LocalizedPolicySelfTests.kt` | test | data-driven | `insights/HonestyGateTests.kt` / `SpecialFunctionsGoldenTests.kt` | role-match |
| `android/insights/src/test/.../l10n/LocalizedPolicySchemaTests.kt` | test | data-driven | `SpecialFunctionsGoldenTests.kt` (JsonObject walk) | role-match |
| `android/insights/src/test/.../l10n/BundledPolicyParityTests.kt` | test | file-I/O | `insights/ClaimsPolicyDriftTests.kt` (repo-file read) | role-match (do NOT copy `assumeTrue`) |
| `android/app/src/main/.../content/ClaimsPolicyLocalized.kt` | service (delegate) | request-response | `app/content/ClaimsPolicy.kt` lines 32-40 | exact |
| `android/app/src/test/.../l10n/L10nSourceRegistry.kt` | test support (registry) | — | `app/content/AuditableCopy.kt` (`CopyAuditRegistry`) | exact |
| `android/app/src/test/.../l10n/L10nAuditTest.kt` | test | batch scan | `app/content/CopyAuditTest.kt` | exact |
| `android/app/src/test/.../l10n/L10nTripwireTest.kt` | test | file-I/O walk | `ClaimsPolicyDriftTests.kt` + `Tests/EndoAppTests/ReportNoNetworkTests.swift` (anti-vacuity) | partial |
| `android/app/src/test/.../l10n/GlossaryConformanceTest.kt` | test | data-driven | `CopyAuditTest.kt` | role-match |
| `android/app/src/test/.../l10n/ClaimsPolicyLocalizedReuseTest.kt` | test | — | `app/content/ClaimsPolicyReuseTest.kt` | exact |
| iOS `project.yml` (MODIFY) | config | build | itself lines 27-31 (`EndoApp.sources`) | exact |
| iOS `Sources/EndoApp/Content/ClaimsPolicy+Localized.swift` | service (loader) | file-I/O (bundle) | `Sources/EndoApp/Content/ClaimsPolicy.swift` (header/`enum` style; extension only) | role-match |
| iOS `Sources/EndoApp/Content/L10n/*.swift` (L10nText, LanguageClaimsPolicy, Glossary) | utility/model | transform | Kotlin reference impl from this phase | none in Swift |
| iOS `Tests/EndoAppTests/LocalizedPolicySelfTests.swift`, `LocalizedPolicySchemaTests.swift`, `GlossaryConformanceTests.swift` | test | data-driven | `Tests/EndoAppTests/CopyAuditTests.swift` (Swift Testing style) | role-match |
| iOS `Tests/EndoAppTests/BundledPolicyParityTests.swift`, `L10nAuditTests.swift` (+tripwire) | test | file-I/O | `Tests/EndoAppTests/ReportNoNetworkTests.swift` lines 16-31 (`#filePath` root) | exact |

## Pattern Assignments

### `android/insights/build.gradle.kts` (MODIFY)
**Analog:** itself, lines 26-28:
```kotlin
tasks.test {
    useJUnitPlatform()
}
```
Extend in place (keep `useJUnitPlatform()`), add the `Sync` task + `sourceSets.main { resources.srcDir(syncL10nPolicy) }` + `inputs.dir(l10n)` + `systemProperty("endo.repoRoot", ...)` per RESEARCH Pattern 5. Keep the file's header comment style (lines 1-4: `// :insights — ...`). No new dependency: `implementation(libs.kotlinx.serialization.json)` already at line 17.

### `android/app/build.gradle.kts` (MODIFY)
**Analog:** itself, lines 37-42:
```kotlin
testOptions {
    unitTests {
        // Robolectric must load the merged manifest + resources ...
        isIncludeAndroidResources = true
    }
}
```
Add inside `unitTests`: `all { it.systemProperty("endo.repoRoot", rootDir.parentFile.absolutePath); it.inputs.dir(rootDir.parentFile.resolve("l10n")).withPathSensitivity(PathSensitivity.RELATIVE) }`. Keep a one-line rationale comment like the existing one (Pitfall 4).

### `.github/workflows/android.yml` (MODIFY)
**Analog:** lines 6-13 — add `- 'l10n/**'` to both `push.paths` and `pull_request.paths`.

### `PolicyLanguage.kt` (model)
**Analog:** `android/insights/src/main/kotlin/com/endoapp/insights/Insight.kt` lines 29-36:
```kotlin
@Serializable
enum class InsightKind(val raw: String) {
    TREND("trend"),
    ...
}
```
Use `enum class PolicyLanguage(val tag: String) { DE("de"), FR("fr"), ES("es") }`. Package `com.endoapp.insights.l10n`.

### `L10nText.kt`, `LanguageRules.kt`, `LanguageClaimsPolicy.kt`, `Glossary.kt` (matcher + models)
**Analog:** `ForbiddenTerms.kt`. Copy the file-header convention (lines 1-20: platform-mirror note, "Pure-JVM — no android./androidx. (BoundaryLint is the proof)", contract paragraph) and the `violation` return shape (first hit or null), lines 99-104:
```kotlin
fun violation(text: String): String? {
    val lowered = text.lowercase()
    return causal.firstOrNull { lowered.contains(it) }
        ?: imperative.firstOrNull { lowered.contains(it) }
        ?: alarmist.firstOrNull { lowered.contains(it) }
}
```
Differences required by RESEARCH Pattern 1: return a `Violation(category, kind, term)` not `String`; use `lowercase(Locale.ROOT)` after NFC (the English `lowercase()` is locale-free in Kotlin, but be explicit); fixed category→kind→list evaluation order. `BoundaryLintTests.kt` will automatically scan the new `l10n/` subpackage, so no android imports allowed. Models: `@Serializable data class` with all-required fields (strict decode = no `ignoreUnknownKeys`), as in `Insight.kt`.

### `ForbiddenTermsLocalized.kt` (loader, classpath I/O)
**Analog:** `android/insights/src/test/kotlin/com/endoapp/insights/SpecialFunctionsGoldenTests.kt` lines 29-33:
```kotlin
private val goldens by lazy {
    val stream = javaClass.classLoader.getResourceAsStream("stats-goldens.json")
        ?: error("stats-goldens.json not found on the test classpath")
    Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
}
```
Copy the `by lazy` + `?: error(...)` fail-closed shape; resource path `/l10n/claims-policy.<tag>.json`; decode with `Json.decodeFromString<LanguageClaimsPolicy>` (default strict `Json`, as `InsightCodableTests.kt` line 22 `private val json = Json`). Exposed as `fun ForbiddenTerms.forLanguage(lang: PolicyLanguage): LanguageClaimsPolicy` (extension in a new file, never edit `ForbiddenTerms.kt`). Cache: a `private val cache by lazy { PolicyLanguage.entries.associateWith { load(it) } }` returning the same instance (needed for the assertSame reuse test).

### `ClaimsPolicyLocalized.kt` (`:app` delegate)
**Analog:** `android/app/src/main/kotlin/com/endoapp/app/content/ClaimsPolicy.kt` lines 22, 36-37:
```kotlin
import com.endoapp.insights.ForbiddenTerms
...
/** Medical-purpose / causal verbs + D-08 causal connectives — REUSED, never forked. */
val causal: List<String> get() = ForbiddenTerms.causal
```
New file: `fun ClaimsPolicy.forLanguage(lang: PolicyLanguage): LanguageClaimsPolicy = ForbiddenTerms.forLanguage(lang)` with the same "REUSED, never forked" header comment (lines 9-14 wording).

### `ClaimsPolicyLocalizedReuseTest.kt`
**Analog:** `android/app/src/test/kotlin/com/endoapp/app/content/ClaimsPolicyReuseTest.kt` lines 1-28 (JUnit 4 imports `org.junit.Assert.assertSame`, `org.junit.Test`):
```kotlin
@Test
fun causalIsReusedFromInsights_notForked() {
    assertSame(
        "ClaimsPolicy.causal must BE the :insights ForbiddenTerms.causal instance (no divergent fork).",
        ForbiddenTerms.causal,
        ClaimsPolicy.causal,
    )
}
```
One such test per `PolicyLanguage` (or a loop). Note `:app` tests are JUnit 4; `:insights` tests are JUnit Jupiter.

### `L10nSourceRegistry.kt` (test registry)
**Analog:** `android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt` lines 19-40 (`interface AuditableCopy { val auditableStrings }` + `object CopyAuditRegistry { val sources: List<AuditableCopy> = listOf(...) }` and the "registration IS the enforcement" doc comment). Mirror as `interface L10nExtractor { fun claims(path): Boolean; fun extract(file): List<L10nString> }` + `object L10nSourceRegistry { val extractors = listOf(...) }`, plus explicit exempt patterns (README, policy rules/selfTest) with reasons.

### `L10nAuditTest.kt` / `GlossaryConformanceTest.kt`
**Analog:** `android/app/src/test/kotlin/com/endoapp/app/content/CopyAuditTest.kt`.
- Catalog-scan loop, lines 51-61:
```kotlin
@Test
fun registeredCatalog_containsNoForbiddenToken() {
    for (copy in CopyAuditRegistry.allAuditableStrings) {
        val hit = firstForbiddenToken(copy)
        assertNull("Registered copy \"$copy\" must contain NO ... (found \"$hit\").", hit)
    }
}
```
- Red-capable control, lines 95-111 (`scanCatchesAForbiddenString`: feed deliberately-bad strings through the SAME helper, `assertNotNull`). Use one `selfTest.mustFail` sentence per language.
- Header doc (lines 9-28) explaining NON-VACUITY — copy that framing.

### `L10nTripwireTest.kt`
**Analogs:** repo-file access from `ClaimsPolicyDriftTests.kt` lines 33-42 — but **replace** the `user.dir` walk-up with `System.getProperty("endo.repoRoot") ?: error(...)` (Pitfall 6), and **do not** copy `assumeTrue` (line 68; Anti-Pattern "skip-instead-of-fail"). Anti-vacuity from `ReportNoNetworkTests.swift` lines 28-31 (`#expect(!files.isEmpty, ...)`) → `assertTrue(found.isNotEmpty())` so a moved `l10n/` cannot pass. Walk with `File.walkTopDown().onEnter { it.name !in excluded && !(it != root && File(it, ".git").exists()) }`.

### `LocalizedPolicySelfTests.kt`, `LocalizedPolicySchemaTests.kt`, `BundledPolicyParityTests.kt` (`:insights`, JUnit Jupiter)
**Analog:** `SpecialFunctionsGoldenTests.kt` lines 13-19 imports + lazy JSON (above); assertion message style lines 37-42 (`assertTrue(cond, "explanatory message")` — Jupiter puts message last). Schema key-set test walks `Json.parseToJsonElement(...).jsonObject.keys`. Parity test: classpath bytes (`getResourceAsStream("l10n/claims-policy.de.json")!!.readBytes()`) `contentEquals` `File(repoRoot, "l10n/claims-policy.de.json").readBytes()`. Use `@ParameterizedTest @EnumSource(PolicyLanguage::class)` (params is in the Jupiter aggregator).

### iOS `project.yml` (MODIFY)
**Analog:** lines 27-31:
```yaml
  EndoApp:
    type: application
    platform: iOS
    sources:
      - Sources/EndoApp
```
Append `- path: l10n` / `includes: ["claims-policy.*.json"]` / `buildPhase: resources`. Run `xcodegen generate` afterwards.

### iOS `ClaimsPolicy+Localized.swift` and `Content/L10n/*.swift`
**Analog:** `Sources/EndoApp/Content/ClaimsPolicy.swift` lines 1-15 — header comment (`// Requirement: ...` then rationale), `import Foundation`, caseless `enum` namespace with `static let`. New file only: `extension ClaimsPolicy { static func forLanguage(_ lang: PolicyLanguage) -> LanguageClaimsPolicy }`. Never add `static let …: [String] = [` to `ClaimsPolicy.swift` (drift regex, `ClaimsPolicyDriftTests.kt` lines 52-53). Cache as immutable `private static let policies` (Swift 6); models `struct …: Codable, Sendable` with non-optional `let`s. Port the logic 1:1 from the Kotlin reference.

### iOS tests
**Analog (root resolution + anti-vacuity):** `Tests/EndoAppTests/ReportNoNetworkTests.swift` lines 13-31:
```swift
import Testing
import Foundation

@Suite("RPRT-03 report module no-network invariant")
struct ReportNoNetworkTests {
    @Test func reportModuleImportsNoNetworking() throws {
        let projectRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // EndoAppTests/
            .deletingLastPathComponent()   // Tests/
            .deletingLastPathComponent()   // <project root>
        ...
        #expect(!files.isEmpty, "... found no ... at \(reportSources.path)")
```
**Analog (app-symbol tests):** `Tests/EndoAppTests/CopyAuditTests.swift` lines 6-9, 11-12 (`import Testing`, `import Foundation`, `@testable import EndoApp`, `@Suite("...") struct …`, `#expect(cond, "message")`). Schema test uses `JSONSerialization.jsonObject` key sets (JSONDecoder ignores unknown keys — Pitfall 3).

## Shared Patterns

### Fail-closed loading
**Source:** `SpecialFunctionsGoldenTests.kt` lines 29-33 (`?: error(...)`). Apply to all loaders and tests: missing file/resource throws; never `assumeTrue`/skip (contrast `ClaimsPolicyDriftTests.kt` line 68).

### Non-vacuity controls
**Source:** `CopyAuditTest.kt` lines 95-111; `ReportNoNetworkTests.swift` lines 28-31. Every scan test gets a red-capable control fed through the same helper and a non-empty-set assertion.

### Reuse, never fork
**Source:** `ClaimsPolicy.kt` lines 9-14, 36-37 + `ClaimsPolicyReuseTest.kt`. `:app` delegates to `:insights`; prove with `assertSame`.

### Registration is enforcement
**Source:** `AuditableCopy.kt` lines 1-8, 19-40. The l10n registry and tripwire follow the same wording/structure.

### File header convention
**Source:** `ForbiddenTerms.kt` lines 1-20, `ClaimsPolicy.swift` lines 1-9. Every new file opens with a `//` block naming module, mirror counterpart, requirement IDs (SAFE-01..04), and the contract.

### SAFE-04 frozen files (do not edit)
`ForbiddenTerms.kt`, `HonestyGate.kt`, `ClaimsPolicy.kt`, `AuditableCopy.kt`, `CopyAuditTest.kt`, `ClaimsPolicyReuseTest.kt`, `ClaimsPolicyDriftTests.kt`, `HonestyGateTests.kt`, `EnumRawValueParityTests.kt`; iOS `ClaimsPolicy.swift`, `EndoInsights/**`, `CopyAuditTests.swift`, `FlareCopyDriftTests.swift`, `RecommendationsCopyDriftTests.swift`.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `l10n/README.md` | docs | — | No repo-root shared-data dir exists yet |
| `l10n/*.json` schema | data | — | Only analog is a test-resource golden JSON; schema comes from RESEARCH Patterns 2-3 |
| Tokenizer / NFC folding (both platforms) | utility | transform | No existing Unicode-aware matcher; follow RESEARCH Pattern 1 exactly |
| Gradle `Sync` resource task | config | build | No generated-resource tasks in repo; use RESEARCH Pattern 5 |
| Swift `Content/L10n/*` matcher | utility | transform | Port of the Kotlin reference built in this phase |

## Metadata
**Analog search scope:** `android/insights/src/{main,test}`, `android/app/src/{main,test}/.../content`, `android/*.gradle.kts`, `.github/workflows`, `Sources/EndoApp/Content`, `Tests/EndoAppTests`, `project.yml`
**Files scanned:** ~15 read
**Pattern extraction date:** 2026-10-31
