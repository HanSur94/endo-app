// Requirement: D-02/D-14 (Phase 8) — durable, CloudKit-safe persistence of a GATED insight so the
// dashboard loads instantly on open and syncs across devices (Phase 4).
//
// PRIVACY BOUNDARY (security threat T-08-01): the `payload` blob encodes ONLY the engine's gated
// `EndoInsights.Insight` (a non-causal, gate-audited headline/detail + a non-identifying category
// `signalKey` + numeric `points`) — NEVER a `DaySnapshot`, NEVER raw note prose. The encoder takes an
// `Insight`, not the snapshot, so the cache carries the same category-level data the engine already
// emits and no MORE (GDPR Art.9-adjacent). The flat `signalKey`/`kindRaw`/`windowRaw` columns are the
// same non-identifying category labels DismissedInsight already stores. When CloudKit is active
// (Phase 4) only these fields cross into the user's PRIVATE iCloud DB.
//
// CloudKit-safe @Model (mirrors DismissedInsight / FactorReading conventions exactly, D-14):
//   - Every stored property is optional OR defaulted (no non-optional, default-less fields).
//   - NO `@Attribute(.unique)` — identity is the app-generated `id` UUID (idempotent across devices).
//   - No relationships — strictly additive, avoids CloudKit relationship traps.
//
// A1 nesting decision: nested under `extension SchemaV1 { @Model … }` exactly like the existing
// models. SwiftData keys entity identity on the class/entity NAME ("CachedInsight"), not the enum
// namespace, so listing this same class additively under SchemaV5.models is a lightweight-compatible
// change. SchemaMigrationTests (V4→V5) is the gate that proves it.
//
// REPLACE-ALL semantics (D-04/D-14): the Repository deletes every existing row and re-inserts on each
// recompute — there is no per-insight upsert/merge here, so a stale or dismissed insight cannot linger.
import Foundation
import SwiftData

extension SchemaV1 {

    /// One durable, CloudKit-safe cache row per gated `Insight` (D-02/D-14). Flat queryable identity
    /// columns (`signalKey`/`kindRaw`/`windowRaw`) + a `Codable`-encoded `payload` blob carrying the
    /// WHOLE `Insight` (so `points`/`confidence`/`sampleSize`/`isInsufficiencyNotice` round-trip in one
    /// `Data` column) + cache metadata (`computedAt`/`fingerprint`).
    @Model
    final class CachedInsight {
        /// App-generated stable identifier. NOT `.unique` (CloudKit-safe).
        var id: UUID = UUID()
        /// The STABLE identity/dismissal key (D-09) — an engine-generated, value-independent category
        /// label (e.g. `symptom.pelvicPain.severity`). NOT user free-text, NO health prose. Optional
        /// per the CloudKit all-optional/defaulted rule.
        var signalKey: String? = nil
        /// `InsightKind.rawValue` — flat column so section routing needs no blob decode. Optional (CloudKit).
        var kindRaw: String? = nil
        /// `TrendWindow.rawValue` — flat column. Optional (CloudKit).
        var windowRaw: String? = nil
        /// The whole `Insight`, `Codable`-encoded (Phase 8 D-18). Carries ONLY the gated insight value —
        /// never a `DaySnapshot`, never note prose (T-08-01). Optional/defaulted (CloudKit).
        var payload: Data? = nil
        /// When this cache row was computed (D-02/D-04). Defaulted (CloudKit rule).
        var computedAt: Date = Date()
        /// The input fingerprint this cache was computed from (D-04). Optional (CloudKit).
        var fingerprint: String? = nil

        init(id: UUID = UUID(), signalKey: String? = nil, kindRaw: String? = nil,
             windowRaw: String? = nil, payload: Data? = nil,
             computedAt: Date = .now, fingerprint: String? = nil) {
            self.id = id
            self.signalKey = signalKey
            self.kindRaw = kindRaw
            self.windowRaw = windowRaw
            self.payload = payload
            self.computedAt = computedAt
            self.fingerprint = fingerprint
        }
    }
}
