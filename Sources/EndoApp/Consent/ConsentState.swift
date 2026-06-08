// Requirement: ONBD-01 — explicit, withdrawable GDPR consent before any health-data processing.
//
// `ConsentState` is the value type the whole app routes on (RESEARCH Pattern 3). It is a plain,
// Equatable value with no storage of its own — persistence lives in `ConsentStore`. The two cases
// model the only states the gate cares about: consent NOT given, or consent given at a known date.
import Foundation

/// The app-level consent state (ONBD-01). `consented` carries the grant date so the privacy /
/// withdrawal copy can explain "you consented on <date>".
enum ConsentState: Equatable {
    case notConsented
    case consented(Date)
}
