// Requirement: DATA-05 / D-09 (Phase 9) — per-domain `HKQueryAnchor` persistence kept OUTSIDE
// CloudKit (RESEARCH OQ2 → RESOLVED: `UserDefaults` via `NSKeyedArchiver`, keyed per `HealthDomain`).
//
// WHY OUTSIDE CLOUDKIT (RESEARCH A3 / OQ2): the anchor is per-DEVICE runtime cursor state, not user
// health data. Mirroring it into the synced `ImportedSample` store would let one device's incremental
// cursor leak onto another and silently skip samples that device hasn't yet seen. So — exactly like the
// `SyncMode` flag (D-15) — this device/runtime state lives in `UserDefaults`, never in the synced store.
//
// CORRUPTION SAFETY (threat T-09-09): `HKQueryAnchor` conforms to `NSSecureCoding`. A corrupt or
// undecodable archive returns `nil` — the caller discards it and re-backfills (the bounded 365-day
// first-grant pull, D-13). NEVER force-unwrap, NEVER crash on a bad blob (asserted by the round-trip
// test). We do NOT hand-roll anchor serialization (RESEARCH Don't-Hand-Roll): `NSKeyedArchiver` /
// `NSKeyedUnarchiver` with `requiringSecureCoding: true` is the only blessed path.
import Foundation
import HealthKit

/// A minimal injectable key-value store so `HealthAnchorStore` is unit-testable without touching the
/// real `UserDefaults` (which is process-global and would leak state across tests). `UserDefaults`
/// already satisfies this shape, so production wiring passes `.standard` directly.
protocol AnchorKeyValueStore: AnyObject {
    func data(forKey defaultName: String) -> Data?
    func set(_ value: Any?, forKey defaultName: String)
    func removeObject(forKey defaultName: String)
}

extension UserDefaults: AnchorKeyValueStore {}

/// An in-memory `AnchorKeyValueStore` for tests — deterministic, isolated, never touches disk.
final class InMemoryAnchorStore: AnchorKeyValueStore {
    private var storage: [String: Data] = [:]
    func data(forKey defaultName: String) -> Data? { storage[defaultName] }
    func set(_ value: Any?, forKey defaultName: String) {
        if let data = value as? Data { storage[defaultName] = data }
        else if value == nil { storage[defaultName] = nil }
    }
    func removeObject(forKey defaultName: String) { storage[defaultName] = nil }
}

/// Per-`HealthDomain` `HKQueryAnchor` persistence outside CloudKit (D-09 / OQ2). Archive/unarchive via
/// `NSKeyedArchiver` (secure coding); a corrupt blob degrades to `nil` (discard-and-re-backfill, T-09-09).
struct HealthAnchorStore {
    private let kv: AnchorKeyValueStore

    /// - Parameter kv: the backing store. Defaults to `UserDefaults.standard` for production; tests
    ///   inject `InMemoryAnchorStore` for isolation.
    init(kv: AnchorKeyValueStore = UserDefaults.standard) {
        self.kv = kv
    }

    /// The `UserDefaults` key for a domain's persisted anchor. Namespaced + per-domain so the four
    /// domains advance their cursors independently (D-09).
    private func key(for domain: HealthDomain) -> String {
        "health.anchor.\(domain.rawValue)"
    }

    /// The persisted anchor for `domain`, or `nil` when none is stored OR the stored blob is corrupt
    /// (discard-and-re-backfill, T-09-09 — never crashes on a bad archive).
    func anchor(for domain: HealthDomain) -> HKQueryAnchor? {
        guard let data = kv.data(forKey: key(for: domain)) else { return nil }
        // A corrupt/undecodable blob throws here → we swallow it and return nil (re-backfill). Never crash.
        return try? NSKeyedUnarchiver.unarchivedObject(ofClass: HKQueryAnchor.self, from: data)
    }

    /// Persist (or clear, when `anchor == nil`) the cursor for `domain`. Archived with secure coding —
    /// no hand-rolled serialization (RESEARCH Don't-Hand-Roll).
    func setAnchor(_ anchor: HKQueryAnchor?, for domain: HealthDomain) {
        guard let anchor else {
            kv.removeObject(forKey: key(for: domain))
            return
        }
        // If archiving ever fails we simply do not persist (the next pull re-backfills). Never crash.
        if let data = try? NSKeyedArchiver.archivedData(withRootObject: anchor, requiringSecureCoding: true) {
            kv.set(data, forKey: key(for: domain))
        }
    }
}
