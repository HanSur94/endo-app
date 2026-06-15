// Requirement: ONBD-02 — "not medical advice / not a medical device" framing (single source of truth).
//
// `Disclaimer.text` is the ONE place this framing lives. Onboarding surfaces it before the consent
// action (D-05), and later phases reuse `DisclaimerBanner` alongside insight surfaces. Keeping it in
// a single constant makes the claim auditable and impossible to drift across screens.
import SwiftUI

/// The app's "not medical advice / not a medical device" framing (ONBD-02, D-05).
/// The single source of truth — every surface renders THIS text, never a hand-typed copy.
enum Disclaimer {
    /// Plain-language, warm-but-honest framing. MUST contain both "not medical advice" and
    /// "not a medical device" (the auditable claim, enforced by `DisclaimerTests`).
    static let text: String = """
    Endo-App is an informational self-tracking tool. It is not medical advice and \
    not a medical device. It does not diagnose, treat, cure, or prevent any condition. \
    The patterns it surfaces come from your own data and are meant to help you understand \
    your body and have better conversations with your clinician — not to replace professional care.
    """
}

/// A reusable banner that renders `Disclaimer.text` (D-05). Surfaced in onboarding now and reserved
/// for insight surfaces in later phases so the framing always accompanies anything that looks like
/// guidance.
struct DisclaimerBanner: View {
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "info.circle")
                .foregroundStyle(Color.plum)
                .accessibilityHidden(true)
            Text(Disclaimer.text)
                .font(.footnote)
                .foregroundStyle(Color.inkSecondary)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.petal, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.petalLine, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
    }
}
