// Requirement: cross-cutting claims/language audit (Pitfall 5, D-14) — new Phase-2 copy must pass
// ClaimsPolicy.forbiddenVerbs + forbiddenGenderedTokens and use no hard single date.
//
// State: PARTIALLY GREEN. The symptom/region/flow LABEL half is implemented in Plan 02-01 Task 2
// (the three Domain enums' plainLabels). The cycle-copy half is still RED until Plan 02-04 Task 2.
import Testing
import Foundation
@testable import EndoApp
import EndoInsights

@Suite("Phase-2 copy audit")
struct CopyAuditTests {

    /// Every plain label across the three Domain enums, lowercased for substring auditing.
    private var allDomainLabels: [String] {
        SymptomType.allCases.map(\.plainLabel)
            + BodyRegion.allCases.map(\.plainLabel)
            + FlowIntensity.allCases.map(\.plainLabel)
    }

    // GREEN (Plan 02-01 Task 2): no Domain plainLabel contains a forbidden medical-purpose verb.
    @Test func domainLabels_containNoForbiddenVerb() {
        for label in allDomainLabels {
            let lower = label.lowercased()
            for verb in ClaimsPolicy.forbiddenVerbs {
                #expect(!lower.contains(verb.lowercased()),
                        "Domain label \"\(label)\" must not contain forbidden verb \"\(verb)\".")
            }
        }
    }

    // GREEN (Plan 02-01 Task 2): no Domain plainLabel contains a gendered token.
    @Test func domainLabels_containNoGenderedToken() {
        for label in allDomainLabels {
            let lower = label.lowercased()
            for token in ClaimsPolicy.forbiddenGenderedTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Domain label \"\(label)\" must not contain gendered token \"\(token)\".")
            }
        }
    }

    // Sanity: the audited surface is non-empty (catches an accidentally-empty allCases).
    @Test func domainLabels_areNonEmpty() {
        #expect(!allDomainLabels.isEmpty)
        for label in allDomainLabels {
            #expect(!label.trimmingCharacters(in: .whitespaces).isEmpty,
                    "Every Domain plainLabel must be a non-empty string.")
        }
    }

    // MARK: - Cycle-summary copy (Plan 02-04 Task 2): the no-over-claim half (CYCL-02/03/04, Pitfall 5)

    /// Every fixed string CycleSummaryView can render, lowercased for substring auditing.
    private var allCycleCopy: [String] { CycleSummaryCopy.auditableStrings }

    // No cycle string contains a forbidden medical-purpose / causal verb ("predict"/"trigger"/"will"…).
    @Test func cycleCopy_containsNoForbiddenVerb() {
        for copy in allCycleCopy {
            let lower = copy.lowercased()
            for verb in ClaimsPolicy.forbiddenVerbs {
                #expect(!lower.contains(verb.lowercased()),
                        "Cycle copy \"\(copy)\" must not contain forbidden verb \"\(verb)\".")
            }
        }
    }

    // No cycle string contains a gendered token.
    @Test func cycleCopy_containsNoGenderedToken() {
        for copy in allCycleCopy {
            let lower = copy.lowercased()
            for token in ClaimsPolicy.forbiddenGenderedTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Cycle copy \"\(copy)\" must not contain gendered token \"\(token)\".")
            }
        }
    }

    // The confidence indicator is plain-language (never "predicted"); the surface is non-empty.
    @Test func cycleCopy_isPlainLanguage_andNonEmpty() {
        #expect(!allCycleCopy.isEmpty)
        for copy in allCycleCopy {
            #expect(!copy.trimmingCharacters(in: .whitespaces).isEmpty,
                    "Every cycle string must be non-empty.")
            #expect(!copy.lowercased().contains("predict"),
                    "Cycle copy must not use prediction framing: \"\(copy)\".")
        }
        // The suppressed line emits nothing cycle-derivable — it must read as an off-state, not a value.
        #expect(CycleSummaryCopy.suppressed.lowercased().contains("off"),
                "The suppressed line must read as an off-state.")
        // The sparse line is the honest "not enough data" framing, never a fabricated value.
        #expect(CycleSummaryCopy.notEnoughData.lowercased().contains("not enough data"))
    }

    // MARK: - Phase-3 factor + reminder copy (Plan 03-01): factor labels + gentle reminder copy

    /// Every Phase-3 factor plainLabel + the reminder title/body, lowercased for substring auditing.
    private var allPhase3Copy: [String] {
        var out: [String] = []
        out += FactorKind.allCases.map(\.plainLabel)
        out += FoodTag.allCases.map(\.plainLabel)
        out += ActivityType.allCases.map(\.plainLabel)
        out += ActivityIntensity.allCases.map(\.plainLabel)
        out += SleepQuality.allCases.map(\.plainLabel)
        out += ReminderCopy.auditableStrings
        out += CheckInCopy.auditableStrings
        return out
    }

    // GREEN (Plan 03-01): no Phase-3 copy contains a forbidden medical-purpose/causal verb.
    // CRITICAL: the food TAGS must never read as "triggers" — "trigger" is a forbidden verb (D-21).
    @Test func phase3Copy_containsNoForbiddenVerb() {
        for copy in allPhase3Copy {
            let lower = copy.lowercased()
            for verb in ClaimsPolicy.forbiddenVerbs {
                #expect(!lower.contains(verb.lowercased()),
                        "Phase-3 copy \"\(copy)\" must not contain forbidden verb \"\(verb)\".")
            }
        }
    }

    // GREEN (Plan 03-01): no Phase-3 copy contains a gendered token.
    @Test func phase3Copy_containsNoGenderedToken() {
        for copy in allPhase3Copy {
            let lower = copy.lowercased()
            for token in ClaimsPolicy.forbiddenGenderedTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Phase-3 copy \"\(copy)\" must not contain gendered token \"\(token)\".")
            }
        }
    }

    // GREEN (Plan 03-01): the audited surface is non-empty and every label is a non-empty string.
    @Test func phase3Copy_isNonEmpty() {
        #expect(!allPhase3Copy.isEmpty)
        for copy in allPhase3Copy {
            #expect(!copy.trimmingCharacters(in: .whitespaces).isEmpty,
                    "Every Phase-3 label must be a non-empty string.")
        }
    }

    // MARK: - Phase-8 insights chrome copy (Plan 08-03): InsightsCopy (the dashboard chrome)
    //
    // The card/chart render the engine's already-gated headline/detail VERBATIM; the UI adds only the
    // chrome copy in `InsightsCopy`, which MUST also pass the ClaimsPolicy choke-point AND a stricter
    // numeric-confidence scan (D-11 / T-08-09): confidence is one of three plain-language words —
    // NEVER a "%", "p =", "r =", or a bare digit-as-confidence.

    /// Every fixed string `InsightsCopy` can render, the surface CopyAuditTests scans.
    private var allInsightsCopy: [String] { InsightsCopy.auditableStrings }

    // No insights chrome string contains a forbidden medical-purpose / causal verb.
    @Test func insightsCopy_containsNoForbiddenVerb() {
        for copy in allInsightsCopy {
            let lower = copy.lowercased()
            for verb in ClaimsPolicy.forbiddenVerbs {
                #expect(!lower.contains(verb.lowercased()),
                        "Insights copy \"\(copy)\" must not contain forbidden verb \"\(verb)\".")
            }
        }
    }

    // No insights chrome string contains a gendered token (incl. the "higher"/"lower" → "her" trap).
    @Test func insightsCopy_containsNoGenderedToken() {
        for copy in allInsightsCopy {
            let lower = copy.lowercased()
            for token in ClaimsPolicy.forbiddenGenderedTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Insights copy \"\(copy)\" must not contain gendered token \"\(token)\".")
            }
        }
    }

    // CRITICAL (D-11): the confidence indicator — and ALL chrome copy — is plain-language only:
    // NO "%", NO "p =", NO "r =", and NO bare digit posing as confidence/precision.
    @Test func insightsCopy_hasNoNumericConfidence() {
        // The three confidence words specifically must be free of any digit or percent/coefficient token.
        let confidenceWords = InsightConfidence.allCases.map(InsightsCopy.confidenceWord)
        for word in confidenceWords {
            let hasDigit = word.contains { $0.isNumber }
            #expect(!hasDigit,
                    "Confidence word \"\(word)\" must contain NO digit (plain-language only, D-11).")
            let hasPercent = word.contains("%")
            #expect(!hasPercent, "Confidence word \"\(word)\" must not contain a percentage.")
        }

        // Across the whole chrome surface, the false-precision tokens must never appear. (Plain
        // counts like "Seen across 12 logged days." are evidence-count words, not confidence — they
        // legitimately contain a digit, so the bare-digit scan is scoped to the confidence words above.)
        let forbiddenPrecisionTokens = ["%", "p =", "p=", "r =", "r=", "p-value", "p value"]
        for copy in allInsightsCopy {
            let lower = copy.lowercased()
            for token in forbiddenPrecisionTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Insights copy \"\(copy)\" must not contain false-precision token \"\(token)\" (D-11).")
            }
        }
    }

    // The audited surface is non-empty and every string is a non-empty string.
    @Test func insightsCopy_isNonEmpty() {
        #expect(!allInsightsCopy.isEmpty)
        for copy in allInsightsCopy {
            #expect(!copy.trimmingCharacters(in: .whitespaces).isEmpty,
                    "Every InsightsCopy string must be non-empty.")
        }
        // The Correlations section uses the non-causal "Possible associations" framing (not "Correlations").
        #expect(InsightsCopy.sectionCorrelations.lowercased().contains("possible association"))
        // The sparse line is the honest "not enough data" framing, never a fabricated value.
        #expect(InsightsCopy.sparseTitle.lowercased().contains("not enough data"))
    }

    // MARK: - Phase-10 report copy (Plan 10-01): ReportCopy (the doctor-ready report surface)
    //
    // ReportCopy centralizes EVERY fixed string the report renders (Surface 1 on-screen builder/preview
    // AND Surface 2 PDF) so a causal/diagnostic/gendered/numeric-over-claim string FAILS THE BUILD before
    // it can reach a clinician-facing PDF (T-10-03). Clones the insightsCopy_* scans. The "self-tracked,
    // not diagnostic" disclaimer prose is NOT here — Plan 02 reuses Disclaimer.text (audited by
    // DisclaimerTests); the report footer's "Self-tracked, not diagnostic" framing IS scanned below.

    /// Every fixed string `ReportCopy` can render, the surface CopyAuditTests scans.
    private var allReportCopy: [String] { ReportCopy.auditableStrings }

    // No report string contains a forbidden medical-purpose / causal verb (incl. "will"/"trigger"/"detect").
    @Test func reportCopy_containsNoForbiddenVerb() {
        for copy in allReportCopy {
            let lower = copy.lowercased()
            for verb in ClaimsPolicy.forbiddenVerbs {
                #expect(!lower.contains(verb.lowercased()),
                        "Report copy \"\(copy)\" must not contain forbidden verb \"\(verb)\".")
            }
        }
    }

    // No report string contains a gendered token (incl. the "her" substring trap in there/other/higher).
    @Test func reportCopy_containsNoGenderedToken() {
        for copy in allReportCopy {
            let lower = copy.lowercased()
            for token in ClaimsPolicy.forbiddenGenderedTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Report copy \"\(copy)\" must not contain gendered token \"\(token)\".")
            }
        }
    }

    // The report carries NO numeric confidence/false-precision token (D-11 posture — words only).
    @Test func reportCopy_hasNoNumericConfidence() {
        let forbiddenPrecisionTokens = ["%", "p =", "p=", "r =", "r=", "p-value", "p value"]
        for copy in allReportCopy {
            let lower = copy.lowercased()
            for token in forbiddenPrecisionTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Report copy \"\(copy)\" must not contain false-precision token \"\(token)\".")
            }
        }
    }

    // The audited surface is non-empty and every string is a non-empty string.
    @Test func reportCopy_isNonEmpty() {
        #expect(!allReportCopy.isEmpty)
        for copy in allReportCopy {
            #expect(!copy.trimmingCharacters(in: .whitespaces).isEmpty,
                    "Every ReportCopy string must be non-empty.")
        }
        // The suppressed-cycle line reads as an off-state (mirror CycleSummaryCopy.suppressed), never a value.
        #expect(ReportCopy.cycleSuppressed.lowercased().contains("off"),
                "The suppressed-cycle line must read as an off-state.")
        // The per-section sparse line is the honest "not enough data" framing.
        #expect(ReportCopy.notEnoughDataInRange.lowercased().contains("not enough data"))
        // The footer carries the "self-tracked, not diagnostic" framing (RPRT-02 honesty).
        #expect(ReportCopy.footer.lowercased().contains("self-tracked"))
        #expect(ReportCopy.footer.lowercased().contains("not diagnostic"))
    }
}
