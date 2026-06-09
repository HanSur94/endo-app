// Requirement: DATA-05 / D-06 (Phase 9, Plan 09-03) — the in-app rationale screen shown BEFORE Apple's
// system authorization sheet.
//
// PERMISSION SEQUENCING (D-06, the SC2 anchor): Apple's system sheet must NEVER appear cold. This screen
// states, in plain language, that the import is READ-ONLY, stays on-device / in private iCloud, and is
// OPTIONAL — and only its primary "Continue" button proceeds to `requestAuthorization` (which triggers
// the OS sheet). "Not now" dismisses without requesting anything (no nag, D-08).
//
// READ-STATUS HONESTY (Pitfall 1, SC3): "Continue" calls `requestAuthorization` THEN `importNew` for every
// domain regardless of the (hidden) grant status — a denied/empty read simply contributes nothing. We do
// NOT branch on `authorizationStatus(for:)` to decide whether to import.
//
// COPY: every visible string comes from `HealthConnectCopy` (ClaimsPolicy-audited, T-09-15). Stable a11y
// ids (`health.rationale.*`) so the E2E asserts the rationale-before-sheet ordering structurally.
import SwiftUI

/// The rationale screen (D-06). Presented as a sheet by `HealthConnectSection` on the connect tap; the
/// system sheet is reached ONLY through "Continue".
struct HealthRationaleView: View {
    @Environment(\.healthImportService) private var healthImportService
    @Environment(\.dismiss) private var dismiss

    /// Invoked from the "Continue" path ONLY (WR-03), so the host can mark the connect affordance
    /// "decided" exclusively when the user actually proceeds to Apple's system sheet — NOT on "Not now".
    /// Defaults to a no-op so previews / callers that don't track decided-state need no wiring.
    var onProceed: () -> Void = {}

    /// Disables the buttons while the authorize + import round-trip is in flight (no double-taps).
    @State private var isRequesting = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(HealthConnectCopy.rationaleHeadline)
                        .font(.title2.weight(.semibold))

                    Text(HealthConnectCopy.rationaleWhatItReads)
                        .font(.body)

                    VStack(alignment: .leading, spacing: 12) {
                        rationaleRow(symbol: "lock.shield", text: HealthConnectCopy.rationaleReadOnly)
                        rationaleRow(symbol: "iphone", text: HealthConnectCopy.rationalePrivate)
                        rationaleRow(symbol: "checkmark.circle", text: HealthConnectCopy.rationaleOptional)
                    }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
            }
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 8) {
                    Button {
                        proceed()
                    } label: {
                        Text(HealthConnectCopy.continueButton)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isRequesting)
                    .accessibilityIdentifier("health.rationale.continue")

                    Button {
                        dismiss()
                    } label: {
                        Text(HealthConnectCopy.notNowButton)
                            .frame(maxWidth: .infinity)
                    }
                    .disabled(isRequesting)
                    .accessibilityIdentifier("health.rationale.cancel")
                }
                .padding()
                .background(.bar)
            }
            .navigationTitle(HealthConnectCopy.sectionTitle)
            .navigationBarTitleDisplayMode(.inline)
        }
        // Stable root id so the E2E can assert the rationale appears BEFORE any system sheet.
        .accessibilityIdentifier("health.rationale.screen")
    }

    @ViewBuilder
    private func rationaleRow(symbol: String, text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Image(systemName: symbol)
                .frame(width: 20)
            Text(text)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// "Continue" (D-06): trigger the system sheet via `requestAuthorization`, THEN import the first-grant
    /// 365-day backfill for every domain (D-13). The import is attempted regardless of the hidden grant
    /// status (Pitfall 1); a denied/empty read contributes nothing. Dismiss the rationale when done.
    private func proceed() {
        isRequesting = true
        // WR-03: the user committed to connecting (Apple's system sheet is about to appear), so mark the
        // affordance "decided" now — only this path flips it, never "Not now".
        onProceed()
        let service = healthImportService
        Task {
            for domain in HealthDomain.allCases {
                // requestAuthorization shows Apple's system sheet (a no-op in the fixture). A throw is
                // swallowed — we still attempt the pull, which contributes nothing on denial (SC3).
                try? await service.requestAuthorization(for: domain)
                await service.importNew(for: domain)
            }
            await MainActor.run {
                isRequesting = false
                dismiss()
            }
        }
    }
}
