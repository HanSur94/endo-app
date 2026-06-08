// Requirement: ONBD-01 — explicit, withdrawable GDPR consent gate (D-02, D-04, D-22).
//
// `ConsentStore` is the injectable/testable seam (D-22) the gate reads. The concrete
// `UserDefaultsConsentStore` persists the consent flag in `UserDefaults` — deliberately OUTSIDE the
// SwiftData store so it never round-trips through CloudKit (RESEARCH Pattern 3 anti-pattern: a
// synced consent record would couple "may I process data" to the very store it gates). It is
// `@Observable` so SwiftUI re-routes when consent is granted/withdrawn, and accepts a custom
// `UserDefaults` suite for test isolation.
import Foundation
import Observation

/// The consent persistence seam (D-22). Mockable; the gate (`AppGate`) reads `state`.
///
/// `@MainActor`-isolated: consent state drives SwiftUI routing and is read/written on the main
/// actor, which also makes it safe to use as a SwiftUI Environment value under Swift 6.
@MainActor
protocol ConsentStore {
    /// The current consent state (ONBD-01).
    var state: ConsentState { get }
    /// Record explicit consent at the given date (D-02).
    func grant(at date: Date)
    /// Withdraw consent, returning the app to the gated state (D-04 — withdrawable in Settings).
    func withdraw()
}

/// `UserDefaults`-backed `ConsentStore`. The flag lives outside the synced SwiftData store
/// (RESEARCH Pattern 3) and is observable so SwiftUI re-routes on change.
///
/// `state` is a stored, observable property kept in sync with `UserDefaults`: UserDefaults is the
/// durable source of truth (survives launches), while the in-memory `state` drives SwiftUI
/// re-routing via `@Observable`.
@Observable
@MainActor
final class UserDefaultsConsentStore: ConsentStore {
    /// Key under which the consent grant date is persisted (a `Date`, or absent = not consented).
    private static let consentDateKey = "consent.grantedAt"

    @ObservationIgnored private let defaults: UserDefaults

    /// Observable, durable consent state. Initialized from `UserDefaults` and written back on change.
    private(set) var state: ConsentState

    /// - Parameter defaults: inject a custom suite (e.g. a clean test suite) for isolation.
    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let date = defaults.object(forKey: Self.consentDateKey) as? Date {
            self.state = .consented(date)
        } else {
            self.state = .notConsented
        }
    }

    func grant(at date: Date) {
        defaults.set(date, forKey: Self.consentDateKey)
        state = .consented(date)
    }

    func withdraw() {
        defaults.removeObject(forKey: Self.consentDateKey)
        state = .notConsented
    }
}
