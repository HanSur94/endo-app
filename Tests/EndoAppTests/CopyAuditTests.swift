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

    // Phase-13 (CHART-02): the two scrub-tooltip strings are LOCKED verbatim (UI-SPEC Copywriting
    // Contract) — this pin stops a later reword from dodging the substring curation above. Both are
    // registered in `InsightsCopy.auditableStrings`, so the forbidden-verb / gendered-token /
    // numeric-confidence scans cover them automatically; the pins below lock the exact wording:
    // the gap reads "Not logged" (never "0", never "No data"), the logged value reads
    // "Severity {n} of 10" (a severity-0 day reads "Severity 0 of 10" — absence != 0).
    @Test func insightsCopy_scrubTooltipStrings_areLockedVerbatim() {
        #expect(InsightsCopy.scrubNotLogged == "Not logged",
                "The gap-day tooltip wording is LOCKED (UI-SPEC Copywriting Contract).")
        #expect(InsightsCopy.scrubSeverity(6) == "Severity 6 of 10",
                "The logged-day tooltip value wording is LOCKED (UI-SPEC Copywriting Contract).")
        #expect(InsightsCopy.scrubSeverity(0) == "Severity 0 of 10",
                "A logged severity-0 day reads a REAL zero, provably distinct from the gap wording.")
        // Both strings are registered in the audited surface (RESEARCH Pitfall 5 — an unregistered
        // string silently escapes the scans).
        #expect(InsightsCopy.auditableStrings.contains(InsightsCopy.scrubNotLogged))
        #expect(InsightsCopy.auditableStrings.contains(InsightsCopy.scrubSeverity(6)))
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

    // MARK: - Phase-11 recommendation copy (Plan 11-01): the forbidden-IMPERATIVE scan (D-04)
    //
    // Recommendations are DISCUSSION PROMPTS, never instructions. A NEW imperative scan layers onto the
    // existing forbidden-verb / gendered-token / numeric-confidence scans: no recommendation string may
    // contain any `ClaimsPolicy.forbiddenImperatives` substring ("take"/"stop"/"avoid"/"start"/"should"/
    // "must"/"use"/"try"/"cut"). Pitfall 2: this is a CASE-INSENSITIVE SUBSTRING scan, so curated copy
    // must avoid the forbidden substrings ENTIRELY (no "use" ⊂ "because", "her" ⊂ "higher",
    // "start" ⊂ "started"). The POSITIVE-CONTROL tests below prove the scan WORKS regardless of Plan 02.

    /// The case-insensitive substring imperative scan (the SCAN LOGIC under test). Returns the first
    /// forbidden imperative `text` contains, or `nil` if clean.
    private func firstImperative(in text: String) -> String? {
        let lower = text.lowercased()
        return ClaimsPolicy.forbiddenImperatives.first { lower.contains($0.lowercased()) }
    }

    /// The real `RecommendationsCopy` auditable surface (Plan 11-02 re-pointed this from the Plan-01 inline
    /// representative list to `RecommendationsCopy.auditableStrings`). The SAME causal/imperative/gendered/
    /// numeric scans below now guard the production recommendation-copy surface the engine renders.
    private var allRecommendationsCopy: [String] { RecommendationsCopy.auditableStrings }

    // GREEN now (Plan 11-01): no representative recommendation string contains a forbidden imperative.
    // RED→GREEN in Plan 02 once this points at `RecommendationsCopy.auditableStrings`.
    @Test func recommendationsCopy_containsNoImperative() {
        for copy in allRecommendationsCopy {
            #expect(firstImperative(in: copy) == nil,
                    "Recommendation copy \"\(copy)\" must contain NO imperative substring.")
        }
    }

    // GREEN now: no recommendation string contains a forbidden medical-purpose / causal verb.
    @Test func recommendationsCopy_containsNoForbiddenVerb() {
        for copy in allRecommendationsCopy {
            let lower = copy.lowercased()
            for verb in ClaimsPolicy.forbiddenVerbs {
                #expect(!lower.contains(verb.lowercased()),
                        "Recommendation copy \"\(copy)\" must not contain forbidden verb \"\(verb)\".")
            }
        }
    }

    // GREEN now: no recommendation string contains a gendered token (incl. the "her" ⊂ "higher" trap —
    // the curated copy uses "more intense", never "higher").
    @Test func recommendationsCopy_containsNoGenderedToken() {
        for copy in allRecommendationsCopy {
            let lower = copy.lowercased()
            for token in ClaimsPolicy.forbiddenGenderedTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Recommendation copy \"\(copy)\" must not contain gendered token \"\(token)\".")
            }
        }
    }

    // GREEN now: no recommendation string carries a numeric-confidence / false-precision token (words only).
    @Test func recommendationsCopy_hasNoNumericConfidence() {
        let forbiddenPrecisionTokens = ["%", "p =", "p=", "r =", "r=", "p-value", "p value"]
        for copy in allRecommendationsCopy {
            let lower = copy.lowercased()
            for token in forbiddenPrecisionTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Recommendation copy \"\(copy)\" must not contain false-precision token \"\(token)\".")
            }
        }
    }

    // MARK: Positive controls — prove the imperative scan WORKS (independent of Plan 02).

    // NEGATIVE control: deliberate imperatives MUST be flagged by the scan.
    @Test func imperativeScan_flagsDeliberateImperatives() {
        // "you should avoid dairy" → contains "should" AND "avoid"; "try cutting gluten" → "try" (+ "cut").
        let deliberateImperatives = [
            "you should avoid dairy",
            "try cutting gluten",
        ]
        for sentence in deliberateImperatives {
            #expect(firstImperative(in: sentence) != nil,
                    "A deliberate imperative \"\(sentence)\" MUST be flagged by the imperative scan.")
        }
    }

    // POSITIVE control: the curated discussion-prompt words MUST pass clean (no substring-trap false hit).
    @Test func imperativeScan_passesCuratedDiscussionWords() {
        // Each verified BY HAND against the substring trap:
        //   "explore"  — no "start"/"stop"/"try"/"use"/"cut"/"take"/"must"/"should"/"avoid"
        //   "discuss"  — no "cut"/"use" (d-i-s-c-u-s-s has no "cut"; "us" is not "use")
        //   "tend to feel better" — no imperative substring
        //   "a pattern in your own data" — no imperative substring
        let curatedWords = [
            "explore",
            "discuss",
            "tend to feel better",
            "a pattern in your own data",
        ]
        for word in curatedWords {
            #expect(firstImperative(in: word) == nil,
                    "Curated discussion word \"\(word)\" must pass the imperative scan clean (substring trap).")
        }
    }

    // MARK: - Phase-12 flare ALARMIST/DIAGNOSTIC scan (Plan 12-01): the new alarmist guard (D-04)
    //
    // A flare early-warning is a CALM, uncertainty-first discussion prompt — never alarmist ("a flare
    // attack is imminent"), diagnostic ("we diagnose a flare"), a prediction ("we predict a flare"), or a
    // certainty ("you will flare"). A NEW alarmist scan layers onto the existing forbidden-verb / gendered /
    // numeric / imperative scans: no flare-related string may contain any `ClaimsPolicy.forbiddenAlarmist`
    // substring ("attack"/"alarm"/"alert"/"emergency"/"urgent"/"danger"/"diagnos"/"predict"/"imminent"/
    // "certain"/"definitely"/"episode"/"will flare"/"guarantee"). `"warning"` is DELIBERATELY EXCLUDED —
    // the section HEADER is literally "Flare early-warning" (D-07). The controls below prove the SCAN WORKS
    // independent of Plan 02 (which ships the real `FlareCopy.auditableStrings` surface to scan).

    /// The case-insensitive substring alarmist scan (the SCAN LOGIC under test). Returns the first
    /// forbidden alarmist term `text` contains, or `nil` if clean.
    private func firstAlarmist(in text: String) -> String? {
        let lower = text.lowercased()
        return ClaimsPolicy.forbiddenAlarmist.first { lower.contains($0.lowercased()) }
    }

    // POSITIVE control (negative test): deliberate alarmist/diagnostic/certain sentences MUST be flagged.
    @Test func alarmistScan_flagsDeliberateAlarmistCopy() {
        // Each contains at least one alarmist/diagnostic/certainty stem:
        //   "a flare attack is imminent"  → "attack" AND "imminent"
        //   "we predict a flare"          → "predict"
        //   "you will flare"              → "will flare"
        //   "this is an emergency"        → "emergency"
        let deliberateAlarmist = [
            "a flare attack is imminent",
            "we predict a flare",
            "you will flare",
            "this is an emergency",
        ]
        for sentence in deliberateAlarmist {
            #expect(firstAlarmist(in: sentence) != nil,
                    "A deliberate alarmist sentence \"\(sentence)\" MUST be flagged by the alarmist scan.")
        }
    }

    // NEGATIVE control (positive test): the curated CALM flare words MUST pass clean (no substring-trap hit).
    @Test func alarmistScan_passesCuratedCalmWords() {
        // The SAME curated calm forms Plan 02 ships — each verified BY HAND against the substring trap:
        //   "your recent pattern looks similar to times before more difficult days"
        //       — "more difficult" (NOT "higher" → the "her" gendered trap); no "predict"/"attack"/"certain"
        //   "something to keep an eye on and discuss with your clinician"
        //       — no alarmist substring (note: deliberately NOT "alert"/"warning")
        //   "based on a recent rising pattern and a factor you have logged recently"
        //       — no "predict"/"diagnos"/"certain"; "rising pattern", not "prediction"
        // (The naive UI-SPEC "not a prediction" wording contains "predict" → it WOULD be flagged, so the
        //  curated copy AVOIDS the word entirely; this control uses the final clean forms only.)
        let curatedCalmWords = [
            "pattern",
            "keep an eye on",
            "discuss",
            "similar to",
            "something to",
            "your recent pattern looks similar to times before more difficult days",
            "something to keep an eye on and discuss with your clinician",
            "based on a recent rising pattern and a factor you have logged recently",
        ]
        for word in curatedCalmWords {
            #expect(firstAlarmist(in: word) == nil,
                    "Curated calm flare word \"\(word)\" must pass the alarmist scan clean (substring trap).")
        }
    }

    // MARK: - Phase-12 FlareCopy scan (Plan 12-02): the real flare-copy surface vs the FULL scan union
    //
    // Plan 01 proved the alarmist SCAN works (positive/negative controls). Plan 02 ships the real
    // `FlareCopy.auditableStrings` surface and points the FULL scan union at it: a flare string may contain
    // NO causal verb, NO gendered token (incl. the "her" ⊂ "higher" trap), NO numeric-confidence /
    // false-precision token, NO imperative substring, AND NO alarmist/diagnostic/certainty term. The
    // curated copy ("more difficult days", not "higher"; OMITS "not a prediction") dodges every trap.

    /// The real `FlareCopy` auditable surface (section header + headline + detail + contributing +
    /// insufficient title/subtitle), the surface the full scan union below guards.
    private var allFlareCopy: [String] { FlareCopy.auditableStrings }

    // No flare string contains a forbidden medical-purpose / causal verb.
    @Test func flareCopy_containsNoForbiddenVerb() {
        for copy in allFlareCopy {
            let lower = copy.lowercased()
            for verb in ClaimsPolicy.forbiddenVerbs {
                #expect(!lower.contains(verb.lowercased()),
                        "Flare copy \"\(copy)\" must not contain forbidden verb \"\(verb)\".")
            }
        }
    }

    // No flare string contains a gendered token (incl. the "her" ⊂ "higher" trap — the curated copy uses
    // "more difficult days", never "higher-symptom days").
    @Test func flareCopy_containsNoGenderedToken() {
        for copy in allFlareCopy {
            let lower = copy.lowercased()
            for token in ClaimsPolicy.forbiddenGenderedTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Flare copy \"\(copy)\" must not contain gendered token \"\(token)\".")
            }
        }
    }

    // No flare string contains an imperative substring (a flare card is a discussion prompt, not an order).
    @Test func flareCopy_containsNoImperative() {
        for copy in allFlareCopy {
            #expect(firstImperative(in: copy) == nil,
                    "Flare copy \"\(copy)\" must contain NO imperative substring.")
        }
    }

    // No flare string contains an alarmist / diagnostic / certainty term ("warning" is allowed in the
    // section HEADER only — it is excluded from the alarmist list precisely so the header is legal).
    @Test func flareCopy_containsNoAlarmistTerm() {
        for copy in allFlareCopy {
            #expect(firstAlarmist(in: copy) == nil,
                    "Flare copy \"\(copy)\" must contain NO alarmist/diagnostic term.")
        }
    }

    // No flare string carries a numeric-confidence / false-precision token (words-only card, D-11).
    @Test func flareCopy_hasNoNumericConfidence() {
        let forbiddenPrecisionTokens = ["%", "p =", "p=", "r =", "r=", "p-value", "p value"]
        for copy in allFlareCopy {
            let lower = copy.lowercased()
            for token in forbiddenPrecisionTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Flare copy \"\(copy)\" must not contain false-precision token \"\(token)\".")
            }
        }
    }

    // The audited surface is non-empty and every string is a non-empty string.
    @Test func flareCopy_isNonEmpty() {
        #expect(!allFlareCopy.isEmpty)
        for copy in allFlareCopy {
            #expect(!copy.trimmingCharacters(in: .whitespaces).isEmpty,
                    "Every FlareCopy string must be non-empty.")
        }
        // The section header is the one sanctioned use of "early-warning" (D-07).
        #expect(FlareCopy.sectionFlare.lowercased().contains("early-warning"))
        // The insufficiency notice carries the honest "not enough data" framing (mirror the sparse title).
        #expect(FlareCopy.insufficientTitle.lowercased().contains("not enough data"))
    }

    // MARK: - Phase-15 Explore chrome copy (Plan 15-03): ExploreCopy (the Explore tab chrome)
    //
    // Explore BYPASSES the InsightsEngine/HonestyGate — it shows RAW descriptive data with no confidence
    // pills, no associations, no causal framing. So every NEW chrome string must be DESCRIPTIVE-ONLY and
    // escape NONE of the scans. This block REGISTERS `ExploreCopy.auditableStrings` in the SAME change
    // that introduces the enum (UI-SPEC honesty constraint 5 / RESEARCH Pitfall 3 — an unregistered copy
    // surface silently escapes the ClaimsPolicy substring scans, BEFORE any 15-04/15-05 view renders a
    // string). Clones the `insightsCopy_*` suite PLUS the imperative/alarmist scans (Explore is
    // descriptive-only), and pins the "Not logged" gap line verbatim like the dashboard's scrub posture.

    /// Every fixed string `ExploreCopy` can render, the surface CopyAuditTests scans.
    private var allExploreCopy: [String] { ExploreCopy.auditableStrings }

    // No Explore chrome string contains a forbidden medical-purpose / causal verb (incl. "will"/"trigger").
    @Test func exploreCopy_containsNoForbiddenVerb() {
        for copy in allExploreCopy {
            let lower = copy.lowercased()
            for verb in ClaimsPolicy.forbiddenVerbs {
                #expect(!lower.contains(verb.lowercased()),
                        "Explore copy \"\(copy)\" must not contain forbidden verb \"\(verb)\".")
            }
        }
    }

    // No Explore chrome string contains a gendered token (incl. the "her" ⊂ "another"/"other"/"there"
    // trap — the cap helper uses "a new one", never "another").
    @Test func exploreCopy_containsNoGenderedToken() {
        for copy in allExploreCopy {
            let lower = copy.lowercased()
            for token in ClaimsPolicy.forbiddenGenderedTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Explore copy \"\(copy)\" must not contain gendered token \"\(token)\".")
            }
        }
    }

    // No Explore string contains an imperative substring (Explore chrome is descriptive, not an order).
    @Test func exploreCopy_containsNoImperative() {
        for copy in allExploreCopy {
            #expect(firstImperative(in: copy) == nil,
                    "Explore copy \"\(copy)\" must contain NO imperative substring.")
        }
    }

    // No Explore string contains an alarmist / diagnostic / certainty term (Explore is calm, descriptive).
    @Test func exploreCopy_containsNoAlarmistTerm() {
        for copy in allExploreCopy {
            #expect(firstAlarmist(in: copy) == nil,
                    "Explore copy \"\(copy)\" must contain NO alarmist/diagnostic term.")
        }
    }

    // No Explore string carries a numeric-confidence / false-precision token (words-only chrome, D-11).
    // Explore has NO confidence chrome at all — it bypasses the gate; this asserts the posture stays so.
    @Test func exploreCopy_hasNoNumericConfidence() {
        let forbiddenPrecisionTokens = ["%", "p =", "p=", "r =", "r=", "p-value", "p value"]
        for copy in allExploreCopy {
            let lower = copy.lowercased()
            for token in forbiddenPrecisionTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Explore copy \"\(copy)\" must not contain false-precision token \"\(token)\" (D-11).")
            }
        }
    }

    // The audited surface is non-empty and every string is a non-empty string.
    @Test func exploreCopy_isNonEmpty() {
        #expect(!allExploreCopy.isEmpty)
        for copy in allExploreCopy {
            #expect(!copy.trimmingCharacters(in: .whitespaces).isEmpty,
                    "Every ExploreCopy string must be non-empty.")
        }
        // The empty-state heading invites the user to pick a series (EXPL-07).
        #expect(ExploreCopy.emptyHeading.lowercased().contains("pick a series"))
        // The sparse line is the honest "not enough data" framing, never a fabricated value (EXPL-07).
        #expect(ExploreCopy.sparseHeading.lowercased().contains("not enough data"))
    }

    // EXPL-06: `windowLabel` maps every TrendWindow to the dashboard's audited "last N days"/"all time"
    // wording, and those labels are registered in the audited surface.
    @Test func exploreCopy_windowLabel_mapsEveryTrendWindow() {
        #expect(ExploreCopy.windowLabel(.last7) == "last 7 days")
        #expect(ExploreCopy.windowLabel(.last30) == "last 30 days")
        #expect(ExploreCopy.windowLabel(.last90) == "last 90 days")
        #expect(ExploreCopy.windowLabel(.allTime) == "all time")
        for window in TrendWindow.allCases {
            #expect(ExploreCopy.auditableStrings.contains(ExploreCopy.windowLabel(window)),
                    "windowLabel(\(window)) must be registered in ExploreCopy.auditableStrings.")
        }
    }

    // The tooltip gap line is LOCKED verbatim to "Not logged" (reusing the dashboard posture) — pinned
    // like `insightsCopy_scrubTooltipStrings_areLockedVerbatim` so a later reword can't dodge the scans.
    // The gap reads "Not logged" (never "0", never "No data"); both gap + event "Logged" are registered.
    @Test func exploreCopy_gapLine_isLockedVerbatim() {
        #expect(ExploreCopy.notLogged == "Not logged",
                "The gap-day tooltip wording is LOCKED (UI-SPEC Copywriting Contract).")
        #expect(ExploreCopy.notLogged == InsightsCopy.scrubNotLogged,
                "Explore reuses the LOCKED dashboard gap posture verbatim.")
        #expect(ExploreCopy.eventTagged == "Logged",
                "The event-lane presence value is LOCKED to \"Logged\".")
        // Both strings are registered in the audited surface (RESEARCH Pitfall 3 — an unregistered
        // string silently escapes the scans).
        #expect(ExploreCopy.auditableStrings.contains("Not logged"))
        #expect(ExploreCopy.auditableStrings.contains(ExploreCopy.eventTagged))
        // The cap-reached helper avoids the "another" ⊃ "her" substring trap (the "a new one" fix).
        #expect(!ExploreCopy.capReached.lowercased().contains("another"),
                "The cap helper must use \"a new one\", never \"another\" (\"another\" ⊃ \"her\").")
    }
}
