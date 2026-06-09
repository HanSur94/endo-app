// EndoInsights — the flare early-warning producer (Phase 12, Plan 02, INST-06 / D-01..D-08 / SC1 / V7).
//
// THE hardest, riskiest capability of the project — built dead LAST, conservatively, biased toward
// SILENCE. A heuristic, ML-free, non-diagnostic flare early-warning derived from the user's OWN history:
// it COMPOSES three already-gated signals (a recent rising-severity spine, currently-active known
// triggers, proximity to a historically higher-symptom cycle phase) into a BOUNDED (≤ .medium),
// uncertainty-first confidence — and WITHHOLDS entirely when the evidence is insufficient.
//
// Foundation ONLY — NO SwiftData/SwiftUI/UIKit (SC1; BoundaryLint is the proof). Pure: an INJECTED
// `today` + `calendar`, NO `Date.now`/`Date()`/`UUID()`/RNG/`print` (V7/D-16). Mirrors
// `RecommendationsEngine` EXACTLY: an `enum` namespace, one `static func flareWarning(from:asOf:calendar:)`
// entry, `Equatable` `[Insight]` output — same snapshots + same injected `today` → an equal `[Insight]`.
//
// COMPOSE, NEVER RE-DERIVE (D-01/D-02): the engine consumes `TrendsEngine` (rising severity),
// `CorrelationEngine.scoredCandidates` (active triggers), and `CyclePhaseMapper.phaseWindows` (cycle-phase
// proximity) — it runs NO raw statistics of its own. The rising-severity spine is REQUIRED: no recent rise
// → NO warning (silence is honest). Active triggers and cycle-phase proximity can only RAISE the
// confidence one notch each on top of an existing rising spine — they can NEVER start a warning alone.
//
// BIAS TO WITHHOLD (D-03/D-05/Pitfall 2): a suppressed / flat / insufficient user yields ZERO flare
// warning (or, on the A3 cusp — a real recent rise but sub-21-day evidence — ONE honest "not enough data
// yet" notice). The confidence is CAPPED at .medium (NEVER .high), floored at .low; the warning is a
// words-only card (no number, no chart points). A flare warning is never alarmist/diagnostic/certain.
//
// STABLE SIGNALKEY (D-07): the warning's signalKey is the value/window-INDEPENDENT "flare.warning" with
// the `.allTime` window — so the Phase-6 dismissal + Phase-8 cache survive recompute. `Insight.derived`
// yields the stable id from those inputs. The insufficiency notice shares the SAME signalKey (one flare
// signal); the typed `isInsufficiencyNotice` marker (NOT a signalKey suffix — WR-01) carries it past the
// gate floor.
//
// APP↔PACKAGE COPY MIRROR (mirrors the `RecommendationCopy`↔`RecommendationsCopy` precedent): the package
// CANNOT import the app (SC1), so the curated, calm, uncertainty-first copy lives HERE in a
// package-internal `FlareCopyText` enum, BYTE-IDENTICAL to the app's audited `FlareCopy`
// (`Sources/EndoApp/Features/Insights/FlareCopy.swift`). The app surface is audited by `CopyAuditTests`;
// this mirror is proven clean by the gate's zero-forbidden-terms scan (alarmist ∪ causal ∪ imperative).
import Foundation

// MARK: - engine (Task 2)
//
// The `enum FlareEngine` producer body lands here in Task 2 (above the FlareCopyText / FlareDriftGuard
// blocks below). Task 1 ships ONLY the copy mirror + the cross-boundary drift guard so the app-side
// FlareCopy + FlareCopyDriftTests + the CopyAuditTests flare block can compile and go GREEN first.

// MARK: - Package-internal curated copy mirror (BYTE-IDENTICAL to the app's audited FlareCopy).

/// PACKAGE-LOCAL MIRROR of the app's `FlareCopy` (`Sources/EndoApp/Features/Insights/FlareCopy.swift`):
/// the package CANNOT import the app target (SC1), so the SAME curated, calm, uncertainty-first,
/// NON-alarmist strings are duplicated here as data and MUST stay byte-identical with the app surface.
/// The app surface is audited by `CopyAuditTests`; THIS mirror is proven clean by the HonestyGate
/// zero-forbidden-terms scan (alarmist + causal + imperative, D-04) and the `FlareCopyDriftTests` byte
/// identity guard.
///
/// SUBSTRING TRAP (Pitfall 2): every word is CURATED to avoid the forbidden substrings ENTIRELY. The two
/// LOAD-BEARING traps Phase 12 dodges (verified BY HAND against the alarmist ∪ causal ∪ imperative ∪
/// gendered ∪ numeric union):
///   • "more difficult days" — no "her" (avoids the "higher" gendered trap), no "predict", no alarmist
///     word, no imperative ("difficult" has no "cut"/"use"; "more" is clean).
///   • "keep an eye on" / "discuss with your clinician" — no imperative substring (discuss has no
///     "cut"/"use"), no causal, no alarmist.
///   • "a pattern in your own data" — matches `ClaimsPolicy.allowedFraming`; no forbidden substring.
///   • "Based on a recent rising pattern and a factor you have logged recently." — NO "predict", NO causal
///     ("rising"/"pattern"/"factor" are clean), NO imperative; "recently" has no "certain", "factor" has
///     no alarmist substring.
///   • The detail deliberately OMITS "not a prediction" (that would trip "predict") — the
///     not-medical-advice framing is carried by the pinned DisclaimerBanner + "discuss with your clinician".
///   • "warning" appears ONLY in `sectionFlare` ("Flare early-warning", D-07) — the one sanctioned use;
///     it is deliberately EXCLUDED from the alarmist list so the header is legal.
enum FlareCopyText {

    /// The "Flare early-warning" section title (the ONLY sanctioned use of "warning", D-07).
    static let sectionFlare = "Flare early-warning"

    /// The calm, uncertainty-first headline ("more difficult days", NOT "higher-symptom days").
    static let headline = "Your recent pattern looks similar to times before more difficult days."

    /// The honest "your own data, keep an eye on it, discuss with your clinician" detail line.
    static let detail = "This is a pattern in your own data — something to keep an eye on and discuss with your clinician."

    /// The optional "what's contributing" line, appended ONLY when a secondary signal fired (from the TYPED
    /// engine signals, never parsed copy).
    static let contributing = "Based on a recent rising pattern and a factor you have logged recently."

    /// The insufficiency-notice title (reuses the sparse-title wording for parity).
    static let insufficientTitle = "Not enough data yet."

    /// The insufficiency-notice subtitle — the honest "keep logging" framing.
    static let insufficientSubtitle = "Keep logging and this fills in over time."
}

// MARK: - Cross-boundary drift-guard surface (WR-02 / WR-03) — the ONLY public window onto the mirrors.

/// PUBLIC, TEST-SUPPORT-ONLY window onto the package-internal mirrors the APP duplicates and must keep in
/// sync (the app↔package "drift guard" precedent established by `RecommendationsDriftGuard`). The app test
/// target sees ONLY the package's PUBLIC API (it imports `EndoInsights` as a release product, NOT
/// `@testable`), so the package-internal `FlareCopyText` strings and `ForbiddenTerms.alarmist` list are
/// invisible to it. This shim forwards those mirrored surfaces — and NOTHING else — so an app-side test
/// (`FlareCopyDriftTests`) can assert the two halves are byte-identical / set-identical and a future edit
/// to ONE side without the other fails a test. The forwarded values are pure data with no behavior;
/// exposing them does NOT widen the engine's real API surface or the Foundation-only boundary
/// (BoundaryLintTests stays green — this imports nothing).
public enum FlareDriftGuard {

    /// The package-internal `FlareCopyText.headline` mirror, for byte-identity comparison against the app's
    /// audited `FlareCopy.headline` (WR-02).
    public static var packageHeadline: String { FlareCopyText.headline }

    /// The package-internal `FlareCopyText.detail` mirror (WR-02).
    public static var packageDetail: String { FlareCopyText.detail }

    /// The package-internal `FlareCopyText.contributing` mirror (WR-02).
    public static var packageContributing: String { FlareCopyText.contributing }

    /// The package-internal `FlareCopyText.sectionFlare` mirror (WR-02).
    public static var packageSectionFlare: String { FlareCopyText.sectionFlare }

    /// The package-internal `FlareCopyText.insufficientTitle` mirror (WR-02).
    public static var packageInsufficientTitle: String { FlareCopyText.insufficientTitle }

    /// The package-internal `FlareCopyText.insufficientSubtitle` mirror (WR-02).
    public static var packageInsufficientSubtitle: String { FlareCopyText.insufficientSubtitle }

    /// The package-internal alarmist forbidden list (`ForbiddenTerms.alarmist`), for set-equality parity
    /// against the app's `ClaimsPolicy.forbiddenAlarmist` (WR-03). The two lists are documented to stay
    /// BYTE-SET-IDENTICAL; `FlareCopyDriftTests` asserts `Set(app) == Set(package)`.
    public static var packageForbiddenAlarmist: [String] { ForbiddenTerms.alarmist }
}
