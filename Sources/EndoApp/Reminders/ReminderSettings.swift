// Requirement: CHCK-02 — opt-in, fatigue-aware reminder CONFIG (D-18/19/20).
//
// `ReminderSettings` is the UserDefaults-backed device preference holding whether reminders are
// enabled, the daily reminder times, and the optional quiet-hours window. It mirrors the
// `TerminologyStore` pattern (RESEARCH Pattern 4 / Assumption A2): an `@Observable @MainActor` store
// persisted in `UserDefaults` — deliberately OUTSIDE the synced persistence store. These are LOCAL
// device prefs (which clock this phone nudges at), NOT health records, so they must NEVER be written
// to the CloudKit-bound store (A2 / threat T-03-12).
//
// DEFAULT OFF (D-18): `enabled` starts `false`. No reminder is ever scheduled until the user opts in
// via the Settings master toggle and grants just-in-time permission.
//
// Times/quiet-hours are stored as minutes-since-midnight `Int`s (hour*60 + minute) so they round-trip
// cleanly through `UserDefaults` without any `DateComponents`/`Date` encoding ambiguity.
import Foundation

/// The user's reminder configuration (CHCK-02). Persisted in `UserDefaults` (outside the synced
/// store, A2). Observable so the Settings reminders section updates as the user edits it. Default OFF
/// (D-18); a sensible single evening time is *suggested* (not pre-applied) on first enable.
@Observable
@MainActor
final class ReminderSettings {

    // MARK: - UserDefaults keys (device-pref namespace, never in the synced store)

    private enum Key {
        static let enabled = "reminders.enabled"
        static let times = "reminders.times"           // [Int] minutes-of-day
        static let quietStart = "reminders.quietStart" // Int minutes-of-day, or absent
        static let quietEnd = "reminders.quietEnd"      // Int minutes-of-day, or absent
    }

    /// The single sensible evening default time SUGGESTED when the user first enables reminders
    /// (D-18). 20:00 local. Suggested, never silently pre-applied while reminders are off.
    static let defaultEveningTime = DateComponents(hour: 20, minute: 0)

    @ObservationIgnored private let defaults: UserDefaults

    /// Whether reminders are enabled. Defaults to `false` (D-18 opt-in). Writing persists.
    var enabled: Bool {
        didSet { defaults.set(enabled, forKey: Key.enabled) }
    }

    /// The daily reminder times (hour/minute). Writing persists the minutes-of-day representation.
    var times: [DateComponents] {
        didSet { defaults.set(times.map(Self.minutesOfDay), forKey: Key.times) }
    }

    /// The optional quiet-hours window (no reminder fires inside it). `nil` = no quiet hours. Writing
    /// persists both bounds (or clears them when set to `nil`).
    var quietHours: QuietHours? {
        didSet {
            if let quietHours {
                defaults.set(Self.minutesOfDay(quietHours.start), forKey: Key.quietStart)
                defaults.set(Self.minutesOfDay(quietHours.end), forKey: Key.quietEnd)
            } else {
                defaults.removeObject(forKey: Key.quietStart)
                defaults.removeObject(forKey: Key.quietEnd)
            }
        }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        // Default OFF — `bool(forKey:)` returns false for an absent key, which is exactly D-18.
        self.enabled = defaults.bool(forKey: Key.enabled)
        if let raw = defaults.array(forKey: Key.times) as? [Int] {
            self.times = raw.map(Self.dateComponents(fromMinutes:))
        } else {
            self.times = []
        }
        // Quiet hours present only when BOTH bounds were stored.
        if defaults.object(forKey: Key.quietStart) != nil,
           defaults.object(forKey: Key.quietEnd) != nil {
            let start = Self.dateComponents(fromMinutes: defaults.integer(forKey: Key.quietStart))
            let end = Self.dateComponents(fromMinutes: defaults.integer(forKey: Key.quietEnd))
            self.quietHours = QuietHours(start: start, end: end)
        } else {
            self.quietHours = nil
        }
    }

    // MARK: - Minutes-of-day <-> DateComponents (UserDefaults-friendly Int representation)

    /// Minutes since midnight for an hour/minute `DateComponents`. Missing components count as 0.
    static func minutesOfDay(_ components: DateComponents) -> Int {
        (components.hour ?? 0) * 60 + (components.minute ?? 0)
    }

    /// An hour/minute `DateComponents` from minutes-since-midnight (clamped into a valid 0..<1440 day).
    static func dateComponents(fromMinutes minutes: Int) -> DateComponents {
        let clamped = min(max(minutes, 0), 24 * 60 - 1)
        return DateComponents(hour: clamped / 60, minute: clamped % 60)
    }
}
