// Requirement: DATA-05 / T-09-15 (Phase 9, Plan 09-03) — the ClaimsPolicy choke-point for every
// HealthKit connect/rationale/badge/refresh string AND the NSHealthShareUsageDescription literal.
//
// Extends the established `CopyAuditTests` forbidden-verb + gendered-token scan (Pitfall 5) to cover the
// `HealthConnectCopy` surface. This is a PURE string scan — no Simulator UI. It is the test that keeps the
// permission/provenance copy free of medical-purpose/causal verbs (diagnose/detect/treat/.../trigger/
// causes/will) and gendered tokens (she/her/women/woman/lady/ladies — INCLUDING the "her" SUBSTRING trap,
// e.g. "here"/"there"/"gather"/"higher", the documented Phase-3 trap).
import Testing
import Foundation
@testable import EndoApp

@Suite("Phase-9 HealthKit connect copy audit")
struct HealthConnectCopyAuditTests {

    /// Every fixed string `HealthConnectCopy` can render (rationale + section + badge + the
    /// NSHealthShareUsageDescription literal), the surface this suite scans.
    private var allHealthCopy: [String] { HealthConnectCopy.auditableStrings }

    // No HealthKit copy contains a forbidden medical-purpose / causal verb (MDR/DiGA line, T-09-15).
    @Test func healthCopy_containsNoForbiddenVerb() {
        for copy in allHealthCopy {
            let lower = copy.lowercased()
            for verb in ClaimsPolicy.forbiddenVerbs {
                #expect(!lower.contains(verb.lowercased()),
                        "HealthKit copy \"\(copy)\" must not contain forbidden verb \"\(verb)\".")
            }
        }
    }

    // No HealthKit copy contains a gendered token (incl. the "her" SUBSTRING trap, T-09-15).
    @Test func healthCopy_containsNoGenderedToken() {
        for copy in allHealthCopy {
            let lower = copy.lowercased()
            for token in ClaimsPolicy.forbiddenGenderedTokens {
                #expect(!lower.contains(token.lowercased()),
                        "HealthKit copy \"\(copy)\" must not contain gendered token \"\(token)\".")
            }
        }
    }

    // The audited surface is non-empty and every string is a non-empty string.
    @Test func healthCopy_isNonEmpty() {
        #expect(!allHealthCopy.isEmpty)
        for copy in allHealthCopy {
            #expect(!copy.trimmingCharacters(in: .whitespaces).isEmpty,
                    "Every HealthConnectCopy string must be non-empty.")
        }
    }

    // The rationale must STATE read-only + private + optional (D-06) and the badge must read provenance.
    @Test func healthCopy_statesReadOnlyPrivateOptional_andProvenance() {
        #expect(HealthConnectCopy.rationaleReadOnly.lowercased().contains("only reads"),
                "The rationale must state the import is read-only.")
        #expect(HealthConnectCopy.rationalePrivate.lowercased().contains("private icloud"),
                "The rationale must state data stays in the user's private iCloud.")
        #expect(HealthConnectCopy.rationaleOptional.lowercased().contains("optional"),
                "The rationale must state the import is optional.")
        #expect(HealthConnectCopy.sourceBadge.lowercased().contains("apple health"),
                "The provenance badge must read 'from Apple Health' (D-03).")
    }

    // The Info.plist NSHealthShareUsageDescription literal IS the HealthConnectCopy source of truth — the
    // audit therefore covers the system-sheet copy too (Task 1 placed the SAME string).
    @Test func healthShareUsageDescription_isAuditedSourceOfTruth() {
        #expect(HealthConnectCopy.auditableStrings.contains(HealthConnectCopy.healthShareUsageDescription),
                "The NSHealthShareUsageDescription literal must be in the audited surface.")
        let lower = HealthConnectCopy.healthShareUsageDescription.lowercased()
        #expect(lower.contains("read"), "The usage string must state it reads (read-only).")
        #expect(lower.contains("optional"), "The usage string must state the import is optional.")
    }
}
