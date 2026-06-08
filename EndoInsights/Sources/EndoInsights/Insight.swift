// EndoInsights — the generic Insight result contract (Phase 5, D-02 / D-17).
//
// Foundation only — NO SwiftData/SwiftUI/UIKit (SC1). The OUTPUT of `TrendsEngine` and the stable
// value Phases 6/7/8/11/12 all consume. The enums reserve future cases NOW so the downstream
// contract is visible without a later breaking change.
//
// DETERMINISM CONTRACT (Pitfall 4 — load-bearing for Plan 02's Equatable fixtures): `Insight.id` is
// DERIVED deterministically from `kind` + `window` + a signal key, NEVER `UUID()`. The SAME inputs
// always produce the SAME id, so a whole `Insight` (and an `[Insight]`) stays exactly `Equatable`
// for fixture assertions. The engine MUST construct Insights via `Insight.derived(...)`; there is
// deliberately NO `UUID()`-defaulting initializer it could reach for by accident.
import Foundation

/// The kind of insight. `.trend` ships in Phase 5; the rest are RESERVED now (D-17) so the
/// Phase 6–12 contract is visible without a later contract change.
public enum InsightKind: String, Sendable, Equatable, CaseIterable {
    /// Phase 5 — descriptive trends (INST-01).
    case trend
    /// Reserved — Phase 6 cycle-phase mapping (INST-02).
    case cyclePhase
    /// Reserved — Phase 7 correlations / trigger discovery (INST-03).
    case correlation
    /// Reserved — Phase 11 personalized recommendations (INST-05).
    case recommendation
    /// Reserved — Phase 12 flare early-warning (INST-06).
    case flare
}

/// The rolling window a trend is computed over (D-06). Anchored on absolute calendar dates ending at
/// the injected `today` — NOT cycle phase (that is Phase 6).
public enum TrendWindow: String, Sendable, Equatable, CaseIterable {
    case last7
    case last30
    case last90
    case allTime
}

/// Descriptive confidence in an insight (D-02). `Int`-backed + `Comparable` so callers can order it
/// (`none < low < medium < high`). A direct lift of the app's `Confidence` enum for cross-codebase
/// consistency. Phase 5 populates it descriptively; the formal gating/withholding layer is Phase 6.
public enum InsightConfidence: Int, Sendable, Equatable, Comparable, CaseIterable {
    case none = 0
    case low = 1
    case medium = 2
    case high = 3

    public static func < (lhs: Self, rhs: Self) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}

/// A single supporting data point for an insight (for the future Phase 8 chart).
public struct InsightPoint: Sendable, Equatable {
    public let date: Date
    public let value: Double

    public init(date: Date, value: Double) {
        self.date = date
        self.value = value
    }
}

/// A generic, `Sendable`, `Equatable` insight value (D-02). `headline`/`detail` are plain,
/// descriptive, non-causal copy (the `ClaimsPolicy` posture; Phase 8 renders them).
public struct Insight: Sendable, Equatable, Identifiable {
    /// Deterministically derived from `kind` + `window` + a signal key (Pitfall 4) — never `UUID()`.
    public let id: UUID
    public let kind: InsightKind
    public let headline: String
    public let detail: String
    public let window: TrendWindow
    public let confidence: InsightConfidence
    public let points: [InsightPoint]

    /// Memberwise init. Callers in the engine MUST prefer `derived(...)` so `id` stays deterministic;
    /// this init takes an explicit `id` and never defaults it to a random `UUID()`.
    public init(
        id: UUID,
        kind: InsightKind,
        headline: String,
        detail: String,
        window: TrendWindow,
        confidence: InsightConfidence,
        points: [InsightPoint]
    ) {
        self.id = id
        self.kind = kind
        self.headline = headline
        self.detail = detail
        self.window = window
        self.confidence = confidence
        self.points = points
    }

    /// THE engine-facing factory. Derives `id` deterministically from `kind` + `window` + `signalKey`
    /// (e.g. a symptom/factor identifier), so the SAME logical insight always has the SAME id and the
    /// value stays fully `Equatable` for exact-array fixture assertions (Pitfall 4).
    ///
    /// - Parameter signalKey: a stable string identifying the signal this insight is about
    ///   (e.g. `"symptom.pelvicPain.severity"` or `"factor.sleep.poor.frequency"`).
    public static func derived(
        kind: InsightKind,
        window: TrendWindow,
        signalKey: String,
        headline: String,
        detail: String,
        confidence: InsightConfidence,
        points: [InsightPoint]
    ) -> Insight {
        Insight(
            id: DeterministicUUID.make(kind: kind, window: window, signalKey: signalKey),
            kind: kind,
            headline: headline,
            detail: detail,
            window: window,
            confidence: confidence,
            points: points
        )
    }
}

/// Stable, dependency-free UUID derivation for `Insight.id` (Pitfall 4).
///
/// Pure Foundation — no CryptoKit/SwiftData/SwiftUI. A v5-style namespaced derivation: the inputs are
/// folded into a deterministic 128-bit digest via FNV-1a over two 64-bit lanes, then stamped with the
/// RFC-4122 version (5) and variant bits so the result is a well-formed UUID. The SAME inputs ALWAYS
/// yield the SAME UUID; different inputs yield different UUIDs in practice. This is an identity
/// derivation (not a security primitive), so a non-cryptographic hash is appropriate.
enum DeterministicUUID {
    /// A fixed namespace string so EndoInsights ids never collide with unrelated derivations.
    private static let namespace = "com.endoapp.endoinsights.insight.v1"

    static func make(kind: InsightKind, window: TrendWindow, signalKey: String) -> UUID {
        let key = "\(namespace)|\(kind.rawValue)|\(window.rawValue)|\(signalKey)"
        let bytes = Array(key.utf8)

        // Two FNV-1a 64-bit lanes with distinct offset bases → 16 deterministic bytes.
        var lane0: UInt64 = 0xcbf2_9ce4_8422_2325            // FNV-1a 64-bit offset basis
        var lane1: UInt64 = 0x9e37_79b9_7f4a_7c15            // a distinct, well-mixed basis
        let prime: UInt64 = 0x0000_0100_0000_01b3            // FNV 64-bit prime
        for byte in bytes {
            lane0 = (lane0 ^ UInt64(byte)) &* prime
            lane1 = (lane1 &+ UInt64(byte)) &* prime
        }

        var b = [UInt8](repeating: 0, count: 16)
        for i in 0..<8 { b[i] = UInt8(truncatingIfNeeded: lane0 >> (UInt64(i) * 8)) }
        for i in 0..<8 { b[8 + i] = UInt8(truncatingIfNeeded: lane1 >> (UInt64(i) * 8)) }

        // Stamp RFC-4122 version 5 (name-based) + variant bits so it is a well-formed UUID.
        b[6] = (b[6] & 0x0F) | 0x50
        b[8] = (b[8] & 0x3F) | 0x80

        return UUID(uuid: (b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7],
                           b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]))
    }
}
