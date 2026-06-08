// Requirement: ONBD-03 — inclusive, non-assumptive language + optional customization (D-06/07/08).
//
// `TerminologyPreference` (RESEARCH Pattern 4) is the Codable value type that captures how the user
// wants the app to refer to them and their bleeding: a neutral default ("bleeding"), an optional
// pronoun string, and an optional display name. It is persisted in `UserDefaults` — deliberately
// OUTSIDE the synced SwiftData store (these are local UI preferences, not health records). A custom
// store can be injected for test isolation.
import Foundation

/// The user's terminology / identity preferences (ONBD-03, RESEARCH Pattern 4). Neutral defaults
/// (D-08); customization is optional and never a forced onboarding wall (D-07).
struct TerminologyPreference: Codable, Equatable {
    /// How the app refers to bleeding. Neutral default "bleeding" (D-08); user may switch to
    /// "period" / "menstruation" in Settings (D-07).
    enum BleedingTerm: String, CaseIterable, Codable {
        case bleeding
        case period
        case menstruation

        /// The resolved user-facing noun for this term.
        var noun: String { rawValue }
    }

    /// The chosen bleeding term. Defaults to `.bleeding` (D-08 neutral default).
    var bleedingTerm: BleedingTerm = .bleeding
    /// Optional pronoun string (e.g. "they/them"). `nil` = unset; the app assumes nothing (D-06).
    var pronouns: String? = nil
    /// Optional display name the user wants the app to use. `nil` = unset.
    var displayName: String? = nil

    init(bleedingTerm: BleedingTerm = .bleeding, pronouns: String? = nil, displayName: String? = nil) {
        self.bleedingTerm = bleedingTerm
        self.pronouns = pronouns
        self.displayName = displayName
    }
}

/// Persists `TerminologyPreference` in `UserDefaults` (outside the synced store). Observable so
/// SwiftUI updates when the user customizes terminology in Settings (D-07). Injectable suite for
/// tests.
@Observable
@MainActor
final class TerminologyStore {
    private static let key = "terminology.preference"

    @ObservationIgnored private let defaults: UserDefaults

    /// The current preference. Writing it persists to `UserDefaults` and notifies observers.
    var preference: TerminologyPreference {
        didSet { persist() }
    }

    /// Free-text fields (display name) are bounded to a sane length to avoid pathological input
    /// (threat T-01-05); they are plain local strings with no injection surface.
    static let maxDisplayNameLength = 60

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: Self.key),
           let decoded = try? JSONDecoder().decode(TerminologyPreference.self, from: data) {
            self.preference = decoded
        } else {
            self.preference = TerminologyPreference()
        }
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(preference) {
            defaults.set(data, forKey: Self.key)
        }
    }
}
