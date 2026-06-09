// Requirement: DATA-05 / D-09 / D-12 (Phase 9) — the app-side import orchestrator. It composes the
// injected `HealthDataReading` seam (which already returns ONLY Sendable values — no `HKSample` escapes,
// Pitfall 2), the per-domain `HealthAnchorStore`, a persist closure (the Repository CRUD from Task 2),
// and the Phase-8 `InsightsCoordinator.dataChanged()` recompute seam (D-12 — REUSE it, no new machinery).
//
// SWIFT 6 ISOLATION (RESEARCH Pattern 1): an `actor`. Because the seam returns Sendable values, the
// service itself NEVER touches a non-`Sendable` `HKSample`/`HKHealthStore` — it only sequences seam
// calls. Every injected dependency is `Sendable` so they can be held across the actor's isolation.
//
// CONSENT DEFENCE-IN-DEPTH (threat T-09-08): a `consentGuard` closure gates the whole import. Even
// though the UI is gated behind the `.consented` AppGate branch (09-03) and the Repository CRUD is
// `assertConsented()`-gated, the service refuses to pull or persist when consent is false — never process
// HK data pre-consent.
//
// PRIVACY LOGGING (threat T-09-07): the service NEVER logs sample contents. On a pull error only the
// error TYPE is surfaced (and even that is swallowed gracefully — a failed pull contributes nothing,
// never crashes). Mirrors the InsightsCoordinator no-content-logging posture (T-08-05).
//
// READ-STATUS HONESTY (Pitfall 1, SC3): an empty/denied pull persists nothing and leaves every snapshot
// field nil downstream — it is NOT a "no symptoms" signal.
import Foundation
import SwiftData
import HealthKit   // ONLY for the opaque HKQueryAnchor cursor type the seam vends.

/// The isolated import orchestrator (D-09/D-12). Authorize → anchored pull → persist (dedupe/deletions)
/// → store the new anchor → fire the Phase-8 recompute seam ONCE. All behind injected, Sendable seams so
/// tests need no real `HKHealthStore`, store, or clock.
actor HealthImportService {

    /// The injected HealthKit boundary (real conformer in 09-03; `FixtureHealthDataReading` in tests).
    private let reader: HealthDataReading
    /// Per-domain anchor persistence outside CloudKit (D-09).
    private let anchorStore: HealthAnchorStore
    /// Persist the pulled values + honored deletions. Wraps the Repository CRUD (Task 2). `throws` so a
    /// store failure surfaces; the service treats a throw as "contributed nothing" (graceful).
    private let persist: @Sendable ([ImportedSampleValue], [UUID]) throws -> Void
    /// The Phase-8 recompute trigger — `InsightsCoordinator.dataChanged()` (D-12). Called ONCE after a
    /// successful persist that actually wrote/changed rows. NO new recompute machinery.
    private let recompute: @Sendable () -> Void
    /// Defence-in-depth consent gate (T-09-08). `false` ⇒ the service does nothing (no pull, no persist).
    private let consentGuard: @Sendable () -> Bool

    /// - Parameters mirror the seams above. `consentGuard` defaults to `true` so a caller already inside
    ///   the consented branch needs no extra wiring (mirrors `SwiftDataRepository`).
    init(
        reader: HealthDataReading,
        anchorStore: HealthAnchorStore,
        persist: @escaping @Sendable ([ImportedSampleValue], [UUID]) throws -> Void,
        recompute: @escaping @Sendable () -> Void,
        consentGuard: @escaping @Sendable () -> Bool = { true }
    ) {
        self.reader = reader
        self.anchorStore = anchorStore
        self.persist = persist
        self.recompute = recompute
        self.consentGuard = consentGuard
    }

    /// Request READ access for `domain` (D-05/D-06). Called ONLY after the in-app rationale "Continue"
    /// (09-03). Consent-gated: pre-consent it is a no-op (T-09-08). A successful return does NOT imply a
    /// grant (read status is hidden, Pitfall 1) — the caller proceeds to `importNew` regardless.
    func requestAuthorization(for domain: HealthDomain) async throws {
        guard consentGuard() else { return }
        try await reader.requestAuthorization(for: domain)
    }

    /// Request READ access for the UNION of every domain in ONE call (WR-05) — at most one system sheet,
    /// the Apple-recommended pattern, instead of up to four sequential per-domain prompts. Called ONLY
    /// after the in-app rationale "Continue" (09-03). Consent-gated; a successful return does NOT imply a
    /// grant (Pitfall 1) — the caller then loops `importNew(for:)` per domain.
    func requestAuthorization() async throws {
        guard consentGuard() else { return }
        try await reader.requestAuthorization()
    }

    /// Import new/changed samples for `domain` (D-09 incremental). Loads the persisted anchor (nil ⇒ the
    /// D-13 first-grant 365-day backfill, decided inside the real reader), pulls, persists the values +
    /// deletions (dedupe handled in the Repository — Pitfall 5), stores the new anchor, then fires the
    /// Phase-8 recompute ONCE (D-12). Consent-gated; never crashes on a pull error (T-09-09/T-09-07).
    func importNew(for domain: HealthDomain) async {
        // T-09-08: refuse to process HK data pre-consent.
        guard consentGuard() else { return }

        let anchor = anchorStore.anchor(for: domain)

        let pulled: (values: [ImportedSampleValue], deleted: [UUID], newAnchor: HKQueryAnchor?)
        do {
            pulled = try await reader.pull(domain, since: anchor)
        } catch {
            // T-09-07: surface the error TYPE only — NEVER sample contents — and contribute nothing.
            // A failed pull (e.g. a corrupt anchor) degrades to "no change"; we discard the anchor so the
            // next pull re-backfills (T-09-09). Never crash.
            anchorStore.setAnchor(nil, for: domain)
            return
        }

        do {
            // Persist BEFORE advancing the anchor — if the persist throws, we have NOT moved the cursor
            // past samples we failed to store, so the next pull re-delivers them (no silent data loss).
            try persist(pulled.values, pulled.deleted)
        } catch {
            // T-09-07: no content in the log; contribute nothing on a store failure.
            return
        }

        // Advance the cursor only after a successful persist (D-09).
        anchorStore.setAnchor(pulled.newAnchor, for: domain)

        // D-12: fire the EXISTING Phase-8 recompute seam ONCE per successful import. Even an empty pull
        // calls through harmlessly (the coordinator's fingerprint short-circuits an unchanged recompute),
        // so the contract "after a successful import, dataChanged() fires once" holds uniformly.
        recompute()
    }
}
