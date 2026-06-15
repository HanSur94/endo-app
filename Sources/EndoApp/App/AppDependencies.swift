// Requirement: D-22 — composition root wiring the ModelContainer + services into the Environment.
//
// `AppDependencies` is the single place that constructs the SwiftData `ModelContainer` (via
// `makeModelContainer()`, behind the SyncMode flag) and the `ConsentStore`, and exposes them for
// injection into the SwiftUI Environment. Later phases add more services here (sync, insights,
// HealthKit import) without touching the views.
//
// The consent store is exposed through a custom `EnvironmentKey` (`\.consentStore`) so `AppGate`
// can read it; the model container is injected via SwiftUI's built-in `.modelContainer(_:)`.
import SwiftUI
import SwiftData

/// The app's composition root (D-22). Built once at launch by `EndoAppApp`.
@MainActor
final class AppDependencies {
    /// The SwiftData container (local-only `.none` in Phase 1, behind the SyncMode flag).
    let modelContainer: ModelContainer
    /// The consent gate's persistence seam (ONBD-01). Concrete UserDefaults-backed impl.
    let consentStore: UserDefaultsConsentStore
    /// The terminology/identity preference store (ONBD-03, D-07). UserDefaults-backed, outside the
    /// synced store.
    let terminologyStore: TerminologyStore
    /// The local-notification scheduling seam (CHCK-02, D-17). Concrete `UNUserNotificationCenter`
    /// wrapper; the Settings reminders section drives it, and `EndoAppApp` injects it at the App root
    /// (Plan 04) alongside the deep-link delegate.
    let notificationScheduler: any NotificationScheduler
    /// The notification deep-link router (D-16). The `UNUserNotificationCenterDelegate` flips its flag
    /// on a reminder tap; the consented-branch UI presents `DailyCheckInView`. Injected at the App root.
    let checkInRouter: CheckInRouter
    /// The insights integration coordinator (D-01/D-04/D-13). Reads snapshots+dismissed on the main actor,
    /// runs the pure `InsightsEngine` off-main, caches by fingerprint, and publishes `[Insight]` for the
    /// dashboard (08-03/08-04). Constructed AFTER the container resolves so it can capture a live
    /// `SwiftDataRepository`. Injected via `\.insightsCoordinator`.
    let insightsCoordinator: InsightsCoordinator
    /// The account-availability seam (DATA-02/03, D-03). Resolved once at launch to decide local-vs-cloud
    /// sync BEFORE the container is built (P-2); also injected into the Environment so SettingsView (04-02)
    /// can show the iCloud sync status. `Sendable` (NOT `@MainActor`) — read off-main at build time.
    let accountAvailability: any AccountAvailability
    /// The HealthKit import orchestrator (DATA-05, D-09/D-12). Composes the selected `HealthDataReading`
    /// (the real `SystemHealthDataReading` on a normal launch, or `FixtureHealthDataReading` under a
    /// `-uitest-health-*` arg) + the per-domain `HealthAnchorStore` + a persist closure wrapping the
    /// Repository imported CRUD + a recompute closure firing the Phase-8 `InsightsCoordinator.dataChanged()`
    /// (D-12). Consent UI is gated behind AppGate; the service is consent-guarded too (T-09-08). Injected
    /// via `\.healthImportService` so the connect/rationale/refresh UI (09-03) can drive it.
    let healthImportService: HealthImportService
    /// Test-only deterministic chart-scrub selection day (Phase 13, CHART-01..04 E2E seam). Parsed
    /// from `-uitest-select-day <yyyy-MM-dd>` ONLY when `-uitest-reset` is also present (the
    /// established pairing — the in-memory store makes the seam unreachable in production) AND only
    /// in DEBUG builds (`#if DEBUG` belt-and-suspenders; compiled out of release entirely). `nil` on
    /// every normal launch — injected via `\.uiTestScrubDay` so `InsightTrendChart` can set a
    /// deterministic selection without any gesture coordinates.
    let uiTestScrubDay: Date?

    init() {
        // Test-only, launch-arg-guarded deterministic reset (used by EndoAppUITests). When the
        // `-uitest-reset` argument is present we (a) clear the persisted consent flag so every UI
        // run starts at the consent gate, and (b) build the SwiftData store in-memory so no entries
        // leak between runs. This branch is unreachable in normal app launches, so it cannot change
        // production behavior.
        let uiTestReset = ProcessInfo.processInfo.arguments.contains("-uitest-reset")
        if uiTestReset {
            UserDefaults.standard.removeObject(forKey: "consent.grantedAt")
            // Also clear the device-local "has decided on Health connect" flag (09-03) so every UI run
            // starts with the FRESH passive connect affordance (`health.connect.button`), not the
            // previously-decided deep-link state — otherwise the flag leaks across same-simulator runs.
            UserDefaults.standard.removeObject(forKey: "health.connect.decided")
        }
        // Test-only, launch-arg-guarded insights seed (used by the dashboard E2E). When present we insert
        // a deterministic threshold-crossing fixture (≥14 consecutive days of one symptom severity, well
        // past `HonestyGate.minSample(.trend) == 3`) so the engine reliably emits ≥1 `.trend` card. PAIRED
        // with `-uitest-reset` (T-08-06): the seed only ever touches the in-memory store, so it can NEVER
        // write to the user's real local/CloudKit store. Unreachable in normal launches.
        let seedInsights = ProcessInfo.processInfo.arguments.contains("-uitest-seed-insights")

        // Test-only, launch-arg-guarded REPORT seed (used by ReportFlowUITests, 10-03). When present we
        // insert a deterministic, populated date range — a couple of weeks of symptoms PLUS at least one
        // daily factor (sleep) + a bleeding day — so the report builder produces a non-empty PDF over the
        // "Last 30 days" preset. PAIRED with `-uitest-reset` (T-10-13): the seed only ever touches the
        // in-memory store, so it can NEVER write to the user's real local/CloudKit store. Unreachable in
        // normal launches.
        let seedReport = ProcessInfo.processInfo.arguments.contains("-uitest-seed-report")

        // Test-only, launch-arg-guarded RECOMMENDATION seed (used by the dashboard recommendation E2E,
        // Plan 11-01/11-03). When present we insert a deterministic STRONG dairy→pelvicPain correlation
        // (~22 consecutive days) that the engine will gate → a `.recommendation` once Plan 03 wires the
        // section. PAIRED with `-uitest-reset` (mirrors `-uitest-seed-insights`): the seed only ever
        // touches the in-memory store, so it can NEVER write to the user's real local/CloudKit store.
        let seedReco = ProcessInfo.processInfo.arguments.contains("-uitest-seed-recommendations")

        // Test-only, launch-arg-guarded FLARE seed (used by the dashboard flare E2E, Plan 12-01/12-03).
        // When present we insert a deterministic ≥21-day symptom history whose RECENT days carry a HIGHER
        // severity than the prior days — a clear recent rising-severity spine over substantial personal
        // history (clears `HonestyGate.minSample(.flare) == 21`) so the FlareEngine (Plan 02) gates → ONE
        // `.flare` warning and (once Plan 03 wires the calm section) the flare section renders FIRST. PAIRED
        // with `-uitest-reset` (mirrors `-uitest-seed-insights`): the seed only ever touches the in-memory
        // store, so it can NEVER write to the user's real local/CloudKit store. Unreachable in normal launches.
        let seedFlare = ProcessInfo.processInfo.arguments.contains("-uitest-seed-flare")

        // Test-only, launch-arg-guarded DEMO seed (manual exploration on the Simulator). When present we
        // insert ~4 months (120 days) of deterministic, realistic data — cyclical bleeding every ~29 days,
        // cycle-modulated symptoms, food/sleep/activity/stress/mood factors with two honest associations
        // (dairy→pelvicPain, poor-sleep→next-day pain), occasional unlogged gap days, and a mild RECENT
        // severity rise — so every dashboard section (flare → trends → cycle phase → associations →
        // recommendations), the report, and the cycle summary all light up at once. PAIRED with
        // `-uitest-reset` (mirrors `-uitest-seed-insights`): the seed only ever touches the in-memory
        // store, so it can NEVER write to the user's real local/CloudKit store. Unreachable in normal launches.
        let seedDemo = ProcessInfo.processInfo.arguments.contains("-uitest-seed-demo")

        // Test-only, launch-arg-guarded SCRUB seed (used by ChartScrubUITests, Plan 13-03). When present
        // we insert a dense severity spine with ONE in-window gap day and ONE logged severity-0 day so the
        // chart-scrub E2E can assert all three selection states (logged / gap / zero) deterministically.
        // PAIRED with `-uitest-reset` (mirrors `-uitest-seed-insights`): the seed only ever touches the
        // in-memory store, so it can NEVER write to the user's real local/CloudKit store.
        let seedScrub = ProcessInfo.processInfo.arguments.contains("-uitest-seed-scrub")

        // Test-only, launch-arg-guarded EXPLORE seed (used by ExploreUITests, Plan 15-06). When present we
        // insert a deterministic MULTI-series dataset so the Explore-tab E2E can assert per-scale lanes, the
        // multi-series scrub tooltip (a real-unit readout line AND a per-series "Not logged" gap line), event
        // ticks, and the empty/sparse states — all via a11y ids + the `-uitest-select-day` seam, with NO
        // scrub-gesture coordinates. Two quantitative series on DIFFERENT scales (pelvicPain severity 0…10 +
        // sleep hours) + one event series (a food tag) + an in-window GAP day + a logged-0 day, ALL inside the
        // last-7 window (the Phase-13 "all anomalies in last-7" rule) so a single `-uitest-select-day` resolves
        // deterministically in every range preset. PAIRED with `-uitest-reset` (mirrors `-uitest-seed-scrub`):
        // the seed only ever touches the in-memory store, so it can NEVER write to the user's real
        // local/CloudKit store. Unreachable in normal launches.
        let seedExplore = ProcessInfo.processInfo.arguments.contains("-uitest-seed-explore")

        // The suppressed VARIANT of the Explore seed (used by ExploreUITests' suppression-hides-bleeding case):
        // the SAME multi-series dataset PLUS a "suppressed" `TreatmentState`, so the picker offers NO
        // `bleeding.intensity`/`bleeding.event` chip (T-15-11, the UI half of the Phase-14 accessor gate),
        // provable by the chip's ABSENCE. PAIRED with `-uitest-reset` (in-memory only). Unreachable in normal
        // launches.
        let seedExploreSuppressed = ProcessInfo.processInfo.arguments.contains("-uitest-seed-explore-suppressed")

        // Test-only, launch-arg-guarded deterministic SCRUB-SELECTION day (Phase 13 E2E seam, T-13-02).
        // Parsed ONLY when `-uitest-reset` is present (in-memory store — the same unreachable-in-production
        // pairing as every seed above) and ONLY in DEBUG builds (belt-and-suspenders compile-out). The
        // parse is defensive (ASVS V5, T-13-02b): bounds-checked arg access + a strict en_US_POSIX
        // `yyyy-MM-dd` parse in the CURRENT time zone (must match the chart's `Calendar.current`),
        // normalized to start-of-day — garbage input yields nil, NEVER a crash.
        var scrubDay: Date? = nil
        #if DEBUG
        if uiTestReset,
           let argIndex = ProcessInfo.processInfo.arguments.firstIndex(of: "-uitest-select-day"),
           ProcessInfo.processInfo.arguments.indices.contains(argIndex + 1) {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = .current
            formatter.dateFormat = "yyyy-MM-dd"
            scrubDay = formatter.date(from: ProcessInfo.processInfo.arguments[argIndex + 1])
                .map { Calendar.current.startOfDay(for: $0) }
        }
        #endif
        self.uiTestScrubDay = scrubDay

        // Test-only, launch-arg-guarded HealthKit fixture selection (used by HealthConnectUITests, 09-03).
        // Mirrors `-uitest-reset`/`-uitest-seed-insights`: a `-uitest-health-*` arg swaps the REAL
        // `SystemHealthDataReading` for the deterministic `FixtureHealthDataReading` so the whole import →
        // merge → recompute flow is E2E-provable on the Simulator with NO real device, NO HealthKit grant,
        // and NO `HKHealthStore` (T-09-06). PAIRED with `-uitest-reset` (the in-memory store) below so the
        // fixture reader can never touch a real store. Unreachable on a normal launch.
        let healthArguments = ProcessInfo.processInfo.arguments

        // Resolve account availability FIRST, then derive `SyncMode` BEFORE building the container
        // (RESEARCH Pattern 2 / Pitfall P-2 — the local-only path must be a genuine `.none` store).
        // `uiTestReset` forces deterministic local-only so UI tests never touch a real iCloud account.
        // The resolved `SyncMode`/account state lives ONLY here in code, NEVER in the synced store
        // (D-15 / threat T-04-04).
        let availability: any AccountAvailability =
            uiTestReset ? StubAccountAvailability(isICloudAvailable: false) : SystemAccountAvailability()
        self.accountAvailability = availability
        let syncMode = SyncMode.resolve(availability)

        do {
            self.modelContainer = try makeModelContainer(syncMode: syncMode, inMemory: uiTestReset)
        } catch {
            // The expected path on a healthy or migratable store is success. A failure here means the
            // on-disk store could not be opened/migrated at all (e.g. a corrupt or pre-versioning
            // store left by an interrupted run, or an irrecoverable migration error — SwiftData
            // error 134504 "unknown model version"). We must NEVER hard-crash here: an
            // `assertionFailure` traps in DEBUG builds and would brick the app for that user — the
            // very crash this fallback is meant to prevent. Instead, recover: first discard the
            // unreadable on-disk store and recreate a FRESH on-disk store (so logging still
            // persists), and only as a last resort fall back to in-memory.
            //
            // NOTE: only the error TYPE is logged (never `error` verbatim) — SwiftDataError describes
            // the store/migration, not record contents, so no health values are surfaced.
            print("⚠️ Endo-App: \(syncMode) ModelContainer failed to open (\(type(of: error))); recovering.")
            // Destructive store deletion is SAFE only on the local-only path: there is no synced copy,
            // so a fresh on-disk store loses nothing the user could recover elsewhere. On the `.cloudKit`
            // path the failing store may be MID-SYNC, so we must NOT silently wipe it (data not yet
            // pushed to iCloud would be lost). `discardUnreadableStore` returns whether it actually
            // removed the store; we only retry on-disk when a clean store was established, otherwise we
            // skip straight to (and SURFACE) the in-memory fallback so we never loop on the same corrupt
            // store nor pretend the cloud store recovered.
            let cleared = Self.discardUnreadableStore(syncMode: syncMode, inMemory: uiTestReset)
            var recovered: ModelContainer?
            if cleared {
                do {
                    // Retry on-disk against the now-clean store so future data still persists across launches.
                    recovered = try makeModelContainer(syncMode: syncMode, inMemory: uiTestReset)
                } catch {
                    print("⚠️ Endo-App: on-disk recovery failed (\(type(of: error))); using in-memory store.")
                }
            } else {
                // `.cloudKit` failure with no safe wipe — surface it loudly. The in-memory fallback keeps
                // the app usable this launch WITHOUT touching the possibly-mid-sync on-disk store; on the
                // next launch a healed iCloud/store state can open normally.
                print("⚠️ Endo-App: \(syncMode) store not safely recoverable on disk (left intact); using in-memory store this launch.")
            }
            if let recovered {
                self.modelContainer = recovered
            } else {
                do {
                    self.modelContainer = try makeModelContainer(syncMode: syncMode, inMemory: true)
                } catch {
                    // An in-memory container should never fail; if it does the schema itself is invalid.
                    fatalError("Failed to build even an in-memory ModelContainer: \(type(of: error))")
                }
            }
        }
        self.consentStore = UserDefaultsConsentStore()
        self.terminologyStore = TerminologyStore()
        self.notificationScheduler = SystemNotificationScheduler()
        self.checkInRouter = CheckInRouter()

        // Build the insights coordinator over a live `SwiftDataRepository` (D-13). A dedicated
        // `ModelContext(modelContainer)` backs the coordinator's main-actor reads/writes; the seams wrap
        // the 08-01 CRUD (daySnapshots / dismissedSignalKeys / loadCachedInsights / replaceCachedInsights).
        let insightsRepo = SwiftDataRepository(context: ModelContext(self.modelContainer))

        // T-08-06 safety: the seed fixture is inserted ONLY when BOTH `-uitest-seed-insights` AND the
        // in-memory `-uitest-reset` store are active, so a threshold fixture can never leak to a real store.
        if seedInsights && uiTestReset {
            Self.seedThresholdCrossingInsights(into: insightsRepo)
        }

        // T-10-13 safety: the report seed is inserted ONLY when BOTH `-uitest-seed-report` AND the
        // in-memory `-uitest-reset` store are active, so a populated range can never leak to a real store.
        if seedReport && uiTestReset {
            Self.seedReportRange(into: insightsRepo)
        }

        // Plan 11-01 safety: the recommendation seed is inserted ONLY when BOTH `-uitest-seed-recommendations`
        // AND the in-memory `-uitest-reset` store are active, so the correlation fixture can never leak to a
        // real store.
        if seedReco && uiTestReset {
            Self.seedRecommendationCorrelation(into: insightsRepo)
        }

        // Plan 12-01 safety: the flare seed is inserted ONLY when BOTH `-uitest-seed-flare` AND the
        // in-memory `-uitest-reset` store are active, so the rising-severity fixture can never leak to a
        // real store.
        if seedFlare && uiTestReset {
            Self.seedFlareWarning(into: insightsRepo)
        }

        // Demo-seed safety (mirrors T-08-06): inserted ONLY when BOTH `-uitest-seed-demo` AND the
        // in-memory `-uitest-reset` store are active, so the multi-month fixture can never leak to a
        // real store.
        if seedDemo && uiTestReset {
            Self.seedDemoMonths(into: insightsRepo)
        }

        // Plan 13-03 safety: the scrub seed is inserted ONLY when BOTH `-uitest-seed-scrub` AND the
        // in-memory `-uitest-reset` store are active, so the gap/zero fixture can never leak to a
        // real store.
        if seedScrub && uiTestReset {
            Self.seedScrubFixture(into: insightsRepo)
        }

        // Plan 15-06 safety: the Explore seed is inserted ONLY when BOTH `-uitest-seed-explore` (or its
        // `-uitest-seed-explore-suppressed` variant) AND the in-memory `-uitest-reset` store are active, so
        // the multi-series fixture can never leak to a real store. The suppressed variant additionally flips
        // the latest `TreatmentState` to "suppressed" so the picker hides the bleeding chips (T-15-11).
        if (seedExplore || seedExploreSuppressed) && uiTestReset {
            Self.seedExploreFixture(into: insightsRepo, suppressed: seedExploreSuppressed)
        }

        self.insightsCoordinator = InsightsCoordinator(
            loadSnapshots: { try insightsRepo.daySnapshots() },
            loadDismissed: { try insightsRepo.dismissedSignalKeys() },
            persist: { insights, computedAt, fingerprint in
                // Best-effort persist: a cache write failure must never crash the app; the next recompute
                // simply rewrites it. No health content is logged on failure (T-08-05).
                try? insightsRepo.replaceCachedInsights(insights, computedAt: computedAt,
                                                        fingerprint: fingerprint)
            },
            loadCache: { (try? insightsRepo.loadCachedInsights()) ?? [] },
            now: { Date() },
            // Route the dismiss WRITE through the coordinator's OWN repo/context (D-09/D-10) so the
            // immediately-following recompute reads the new dismissal from the same context and excludes
            // the card. A separate view-owned context would not be visible to the coordinator's reads.
            persistDismissal: { signalKey in try insightsRepo.dismissInsight(signalKey: signalKey) })

        // MARK: - HealthKit import service (DATA-05, D-09/D-12)

        // The HealthKit boundary: REAL reader on a normal launch; the deterministic fixture under a
        // `-uitest-health-*` arg, PAIRED with the in-memory `-uitest-reset` store (T-09-06) so the fixture
        // can never touch a real store. The `-denied`/`-empty` args both map to `.empty` (a denied read and
        // a granted-but-empty read are indistinguishable for reads — Pitfall 1 / SC3).
        let healthReader: HealthDataReading
        if uiTestReset && healthArguments.contains("-uitest-health-seed") {
            healthReader = FixtureHealthDataReading(Self.healthSeedFixture())
        } else if uiTestReset && (healthArguments.contains("-uitest-health-denied")
                                  || healthArguments.contains("-uitest-health-empty")) {
            healthReader = FixtureHealthDataReading(.empty)
        } else {
            healthReader = SystemHealthDataReading()
        }

        // The persist closure wraps the Repository imported CRUD. The service's `persist` is `@Sendable`
        // and runs SYNCHRONOUSLY on the `HealthImportService` actor's executor (NOT the main actor), so we
        // must NOT `MainActor.assumeIsolated` here — that would trap. Instead the DEDICATED `ModelContext`
        // is confined to this box and only ever touched through the single-threaded import service, so it
        // is effectively actor-isolated. The box is `@unchecked Sendable` to cross into the `@Sendable`
        // closure (mirrors the 09-02 test seam). A dedicated context keeps the import write isolated from
        // the cache writes.
        let healthRepoBox = RepoBox(SwiftDataRepository(context: ModelContext(self.modelContainer)))
        let coordinator = self.insightsCoordinator

        self.healthImportService = HealthImportService(
            reader: healthReader,
            anchorStore: HealthAnchorStore(),
            persist: { values, deletedUUIDs in
                // Runs on the import-service actor's executor (single-threaded). The box's context is only
                // ever touched here, so the upsert+delete are a confined, race-free unit.
                try healthRepoBox.repo.replaceOrInsertImported(values: values, deletedUUIDs: deletedUUIDs)
            },
            recompute: {
                // D-12: fire the EXISTING Phase-8 recompute seam. The coordinator is `@MainActor`-isolated
                // and `dataChanged()` itself debounces off-main, so we hop to the main actor with a
                // fire-and-forget Task (the recompute is async by contract — no synchronous wait needed).
                Task { @MainActor in coordinator.dataChanged() }
            })
    }

    /// A box that lets a non-`Sendable` `SwiftDataRepository` cross into the import service's `@Sendable`
    /// persist closure. Safe because the boxed `ModelContext` is touched ONLY by the single-threaded
    /// import service actor — no concurrent access. Mirrors the 09-02 test seam shape.
    private final class RepoBox: @unchecked Sendable {
        let repo: SwiftDataRepository
        init(_ repo: SwiftDataRepository) { self.repo = repo }
    }

    /// The deterministic `-uitest-health-seed` fixture (T-09-06): a handful of imported values across the
    /// four domains, dated within the backfill window, so the E2E can prove an imported value renders the
    /// "from Apple Health" badge and a Refresh pulls + recomputes. PAIRED with the in-memory store; it
    /// never touches a real `HKHealthStore`. `.none` delta ⇒ a re-pull replays the same seed (dedupe).
    private static func healthSeedFixture() -> FixtureHealthDataReading.Fixture {
        let day = Calendar.current.startOfDay(for: Date())
        let values: [ImportedSampleValue] = [
            ImportedSampleValue(hkUUID: UUID(), day: day, domain: .sleep,
                                payload: .sleep(hours: 7.5, quality: .good)),
            ImportedSampleValue(hkUUID: UUID(), day: day, domain: .workouts,
                                payload: .workout(type: .cardio, minutes: 35)),
            ImportedSampleValue(hkUUID: UUID(), day: day, domain: .nutrition,
                                payload: .nutrition(ateThatDay: true)),
        ]
        return .seed(values: values, deleted: [], delta: .none)
    }

    /// Test-only fixture seam (T-08-06): insert a deterministic, threshold-crossing symptom history so the
    /// engine reliably emits ≥1 `.trend` card for the dashboard E2E. Logs 21 consecutive days of one
    /// symptom (`pelvicPain`) — well past `HonestyGate.minSample(.trend) == 3` (RESEARCH Open Question 1
    /// recommends ~14–30 days). Best-effort; a seed failure must not crash a UI-test launch.
    private static func seedThresholdCrossingInsights(into repo: SwiftDataRepository) {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        // 21 consecutive days ending today, ascending — a steady mid-high severity so a trend is emitted.
        for offset in stride(from: 21, through: 1, by: -1) {
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }
            try? repo.upsertSymptom(type: .pelvicPain, severity: 6, on: day)
        }
    }

    /// Test-only fixture seam (Plan 13-03): insert a deterministic scrub fixture so the chart-scrub E2E
    /// (ChartScrubUITests) can assert all THREE selection states. Clones `seedThresholdCrossingInsights`'s
    /// dense pelvicPain spine (21 days ending yesterday — well past `HonestyGate.minSample(.trend) == 3`)
    /// with two deliberate anomalies, BOTH inside the last-7 window so every window-card resolves the
    /// same state (RESEARCH Pitfall 6 — an out-of-window day is logged for the 30-day card but a gap for
    /// the 7-day card, making assertions ambiguous):
    ///   • offset 3 — SKIPPED entirely (the in-window GAP day → "Not logged")
    ///   • offset 2 — severity 0 (the logged-ZERO day → "Severity 0 of 10"; absence ≠ 0)
    ///   • offset 1 — severity 6 (the logged assertion day → "Severity 6 of 10")
    /// PAIRED with the in-memory `-uitest-reset` store; it can never touch a real store. Best-effort;
    /// a seed failure must not crash a UI-test launch. Does NOT modify `seedThresholdCrossingInsights`
    /// (four existing E2Es depend on its exact shape).
    private static func seedScrubFixture(into repo: SwiftDataRepository) {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        for offset in stride(from: 21, through: 1, by: -1) {
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }
            if offset == 3 { continue }            // the in-window gap day (never logged)
            let severity = offset == 2 ? 0 : 6     // the logged-zero day vs the dense spine
            try? repo.upsertSymptom(type: .pelvicPain, severity: severity, on: day)
        }
    }

    /// Test-only fixture seam (Plan 15-06): insert a deterministic MULTI-series dataset so the Explore-tab
    /// E2E (ExploreUITests) can assert per-scale lanes (EXPL-03), the single multi-series scrub tooltip
    /// (EXPL-05 — a real-unit readout line AND a per-series "Not logged" gap line on the SAME day), event
    /// ticks (EXPL-04), and the empty/sparse states (EXPL-07) via a11y ids + the `-uitest-select-day` seam,
    /// with NO scrub-gesture coordinates. Cloned from `seedScrubFixture`'s posture; does NOT modify it.
    ///
    /// Everything lands inside the LAST-7 window (the Phase-13 "all anomalies in last-7" rule) so a single
    /// `-uitest-select-day` resolves the SAME state in EVERY range preset (RESEARCH Pitfall 6). The seeded
    /// scrub day is **offset 1** (today-1), chosen so the multi-series tooltip exercises ALL three line kinds
    /// at once:
    ///   • `pelvicPain` severity (a 0…10 quantitative lane) — LOGGED severity 6 on offset 1 (a readout line),
    ///     with an in-window GAP day (offset 3, never logged → "Not logged") and a logged-ZERO day (offset 2,
    ///     severity 0 → a real point, distinct from a gap).
    ///   • `sleep.hours` (a SECOND quantitative lane on a DIFFERENT scale) — logged on the other in-window
    ///     days but a deliberate GAP on offset 1, so the scrub day shows sleep's per-series "Not logged" line
    ///     alongside pelvicPain's readout (the gap-honest multi-series proof).
    ///   • `food.dairy` (a presence/EVENT lane) — present on offset 1 (and others), so the scrub day also
    ///     carries an event "Logged" line and the event lane renders ticks, never a 0/1 line.
    ///
    /// When `suppressed` is `true` the latest `TreatmentState` is flipped to "suppressed" so the picker hides
    /// BOTH bleeding chips (T-15-11) — provable by the chip's ABSENCE. PAIRED with the in-memory
    /// `-uitest-reset` store; it can never touch a real store. Best-effort; a seed failure must not crash a
    /// UI-test launch.
    private static func seedExploreFixture(into repo: SwiftDataRepository, suppressed: Bool) {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        // A dense 6-day spine ending yesterday (offsets 6…1), all inside the last-7 window. The two
        // deliberate pelvicPain anomalies (the GAP day + the logged-0 day) sit at offsets 3 and 2; offset 1
        // is the seeded scrub/assertion day.
        for offset in stride(from: 6, through: 1, by: -1) {
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }

            // ── Series 1: pelvicPain severity (0…10). offset 3 is the GAP (skipped); offset 2 is the logged
            //    ZERO; every other in-window day is the dense severity-6 spine.
            if offset != 3 {
                let severity = offset == 2 ? 0 : 6
                try? repo.upsertSymptom(type: .pelvicPain, severity: severity, on: day)
            }

            // ── Series 2: sleep hours (a DIFFERENT scale). Logged every in-window day EXCEPT offset 1, so
            //    the seeded scrub day (offset 1) is a sleep GAP → its tooltip line reads "Not logged" while
            //    pelvicPain reads a real value (the multi-series gap-honest proof). Distinct hours so the
            //    lane is a real shape, not a flat line.
            if offset != 1 {
                try? repo.setSleep(hours: offset % 2 == 0 ? 7.5 : 6.0, quality: .okay, on: day)
            }

            // ── Event series: a food tag (dairy). Present on the scrub day (offset 1) and a couple of others
            //    so the event lane renders occurrence ticks and the scrub-day tooltip shows an event line.
            if offset == 1 || offset == 4 || offset == 6 {
                try? repo.setFood(tags: [.dairy], freeText: nil, on: day)
            }
        }

        // The suppressed variant: append a "suppressed" TreatmentState so the latest snapshot's `suppressed`
        // flag drives the picker filter to hide the bleeding chips (T-15-11). Without this the default seed
        // is NOT suppressed, so the bleeding chips ARE offered (the normal chip-render path).
        if suppressed {
            try? repo.setSuppressed(true)
        }
    }

    /// Test-only fixture seam (T-10-13): insert a deterministic, POPULATED date range so the report
    /// builder produces a non-empty PDF over the "Last 30 days" preset (ReportFlowUITests, 10-03). Logs
    /// ~21 consecutive days of one symptom (`pelvicPain`) PLUS a daily factor (sleep) and a bleeding day,
    /// all within the last 30 days. PAIRED with the in-memory `-uitest-reset` store; it can never touch a
    /// real store. Best-effort; a seed failure must not crash a UI-test launch.
    private static func seedReportRange(into repo: SwiftDataRepository) {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        // 21 consecutive days ending yesterday: symptoms + a sleep factor (so the report's symptoms +
        // daily-factor sections are non-empty and the legend surfaces a self-reported source label).
        for offset in stride(from: 21, through: 1, by: -1) {
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }
            try? repo.upsertSymptom(type: .pelvicPain, severity: 6, on: day)
            try? repo.setSleep(hours: 7, quality: .okay, on: day)
        }
        // A couple of bleeding days near the start of the window so the cycle section has flow to summarize.
        for offset in [20, 19] {
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }
            try? repo.setBleeding(intensity: .medium, clots: false, on: day)
        }
    }

    /// Test-only fixture seam (Plan 11-01): insert a deterministic, STRONG dairy→pelvicPain correlation so
    /// the engine gates ONE `.correlation` → and (once Plan 03 wires the section) ONE `.recommendation` for
    /// the dashboard recommendation E2E. Logs ~22 consecutive days: on ~half the days dairy + pelvicPain
    /// severity 7, on the other ~half a DIFFERENT food (caffeine) + pelvicPain severity 2 — so the non-dairy
    /// days are honestly `.absent` for dairy (a day-with-any-log, A7), not unobserved. This mirrors the
    /// `strongAssociationFixture` shape (n=22 ≥ minSample(.correlation)==10, large |δ| → gated). PAIRED with
    /// the in-memory `-uitest-reset` store; it can never touch a real store. Best-effort.
    private static func seedRecommendationCorrelation(into repo: SwiftDataRepository) {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        // 22 consecutive days ending today: alternate dairy/high-severity vs caffeine/low-severity by parity.
        for offset in 0..<22 {
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }
            if offset % 2 == 0 {
                try? repo.setFood(tags: [.dairy], freeText: nil, on: day)
                try? repo.upsertSymptom(type: .pelvicPain, severity: 7, on: day)
            } else {
                try? repo.setFood(tags: [.caffeine], freeText: nil, on: day)
                try? repo.upsertSymptom(type: .pelvicPain, severity: 2, on: day)
            }
        }
    }

    /// Test-only fixture seam (Plan 12-01): insert a deterministic ≥21-day symptom history with a RECENT
    /// rising-severity spine so the FlareEngine (Plan 02) gates ONE `.flare` warning — and (once Plan 03
    /// wires the calm section) the flare section renders FIRST — for the dashboard flare E2E. Logs 30
    /// consecutive days of one symptom (`pelvicPain`): the prior 20 days (offsets 30…11) at a calmer
    /// severity 3, the recent 10 days (offsets 10…1) at a higher severity 7 — a clear recent rise over a
    /// substantial history (n=30 ≥ minSample(.flare)==21). PAIRED with the in-memory `-uitest-reset` store;
    /// it can never touch a real store. Best-effort; a seed failure must not crash a UI-test launch.
    private static func seedFlareWarning(into repo: SwiftDataRepository) {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        // 30 consecutive days ending yesterday. Prior 20 days (30…11) calmer (severity 3); recent 10 days
        // (10…1) higher (severity 7) so a recent rising spine exists over ≥21 days of evidence.
        for offset in stride(from: 30, through: 1, by: -1) {
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }
            let severity = offset <= 10 ? 7 : 3
            try? repo.upsertSymptom(type: .pelvicPain, severity: severity, on: day)
        }
    }

    /// Test-only DEMO fixture seam: ~4 months (120 days, ending yesterday) of deterministic, realistic
    /// data so EVERY surface lights up at once for manual Simulator exploration — 4 bleeding cycles
    /// (~29-day rhythm, 5-day runs), cycle-modulated pelvic pain + dysmenorrhea + fatigue, two honest
    /// associations (dairy→pelvicPain, poor-sleep→next-day pain), food/activity/sleep/stress/mood
    /// factors, occasional fully-unlogged gap days (absence ≠ 0 stays visible in charts), and a mild
    /// RECENT rise so the calm flare card appears. Deterministic (offset arithmetic only, no RNG).
    /// PAIRED with the in-memory `-uitest-reset` store; it can never touch a real store. Best-effort.
    private static func seedDemoMonths(into repo: SwiftDataRepository) {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())

        for offset in stride(from: 120, through: 1, by: -1) {
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }

            // Honest gaps: roughly every 11th historic day is fully unlogged (skip recent week).
            if offset % 11 == 0 && offset > 7 { continue }

            // ── Cycle: a ~29-day rhythm with a 5-day bleed at the start of each cycle.
            let cycleDay = offset % 29   // 0…28; days 0–4 of the rhythm = menses
            let menses = cycleDay < 5
            if menses {
                let intensity: FlowIntensity = (cycleDay < 2) ? .heavy : (cycleDay < 4 ? .medium : .light)
                try? repo.setBleeding(intensity: intensity, clots: cycleDay < 2, on: day)
            }

            // ── Factors first (associations key off these).
            let dairyDay = offset % 3 == 0
            var tags: [FoodTag] = dairyDay ? [.dairy] : [.caffeine]
            if offset % 13 == 0 { tags.append(.highSugar) }
            try? repo.setFood(tags: tags, freeText: nil, on: day)

            let poorSleepNight = offset % 7 < 2   // ~2 nights/week poor sleep
            try? repo.setSleep(hours: poorSleepNight ? 5.5 : 7.5,
                               quality: poorSleepNight ? .poor : (offset % 5 == 0 ? .good : .okay),
                               on: day)

            if offset % 4 != 1 {   // activity most days, honest skips
                let yoga = offset % 5 == 0
                try? repo.setActivity(type: yoga ? .yogaStretching : .walking,
                                      minutes: yoga ? 25 : 35,
                                      intensity: yoga ? .low : .medium, on: day)
            }
            try? repo.setStress(poorSleepNight ? 4 : 2, on: day)
            try? repo.setMood(menses ? 2 : 4, on: day)

            // ── Symptoms: baseline modulated by cycle phase + the two seeded associations + a recent rise.
            // Prior-day poor sleep bumps today's pain (offset+1's night was poor when (offset+1) % 7 < 2).
            let afterPoorSleep = (offset + 1) % 7 < 2
            var pain = 2
            if menses { pain += 3 }
            if dairyDay { pain += 2 }
            if afterPoorSleep { pain += 1 }
            if offset <= 7 { pain += 2 }          // the mild recent rise (calm flare hint)
            try? repo.upsertSymptom(type: .pelvicPain, severity: min(pain, 9), on: day)
            if menses {
                try? repo.upsertSymptom(type: .dysmenorrhea, severity: min(pain + 1, 10), on: day)
                try? repo.setPainPoint(region: .lowerAbdomenPelvis, severity: min(pain, 9), on: day)
            }
            try? repo.upsertSymptom(type: .fatigue, severity: menses ? 6 : (poorSleepNight ? 5 : 3), on: day)
            if offset % 6 == 0 {
                try? repo.upsertSymptom(type: .giEndoBelly, severity: dairyDay ? 6 : 3, on: day)
            }

            if cycleDay == 0 {
                try? repo.setNote("Period started today.", on: day)
            }
        }
    }

    /// Recovery helper (D-22 resilience): delete an unreadable SwiftData store (and its WAL/SHM
    /// sidecars) so a fresh on-disk store can be created after an unrecoverable open/migration failure.
    ///
    /// PHASE 4 SAFETY (WR-02): CloudKit private-database sync is now live, so this can run on a
    /// `.cloudKit`-mode failure where the on-disk store may be MID-SYNC. Wiping such a store could
    /// drop data not yet pushed to iCloud, so the destructive removal is gated to `.localOnly` ONLY —
    /// the path with no synced copy, where a fresh store loses nothing recoverable elsewhere. On the
    /// `.cloudKit` path we leave the store intact and return `false` so the caller surfaces the failure
    /// and uses an in-memory store this launch instead of silently looping on the corrupt store.
    ///
    /// The store filename is DISCOVERED from a `ModelConfiguration` (matching whatever URL SwiftData
    /// actually uses) rather than hardcoding `default.store`, which would silently miss a relocated or
    /// CloudKit-mirrored store. Best-effort — file-removal failures are ignored.
    ///
    /// - Returns: `true` when a store was (or could be) cleared so an on-disk retry is worthwhile;
    ///   `false` when removal was deliberately skipped (`.cloudKit`) and the caller must fall back.
    static func discardUnreadableStore(syncMode: SyncMode, inMemory: Bool) -> Bool {
        // Never delete anything for an in-memory (UI-test) store — there is no on-disk file to clear.
        guard !inMemory else { return false }
        // Only `.localOnly` may be safely wiped; a `.cloudKit` store may be mid-sync (data not yet
        // uploaded), so leave it intact and signal the caller to fall back to in-memory.
        guard syncMode == .localOnly else { return false }

        let fm = FileManager.default
        // Discover the actual on-disk store URL SwiftData would use for this schema/config, instead of
        // assuming `default.store`. The local-only configuration mirrors `makeModelContainer`'s `.none`
        // path so the resolved URL matches the store that just failed to open.
        let schema = Schema(versionedSchema: SchemaV3.self)
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false,
                                               cloudKitDatabase: .none)
        let storeURL = configuration.url
        // Remove the store plus its SQLite WAL/SHM sidecars (same basename, suffixed).
        for suffix in ["", "-shm", "-wal"] {
            let sidecar = storeURL.deletingLastPathComponent()
                .appending(path: storeURL.lastPathComponent + suffix)
            try? fm.removeItem(at: sidecar)
        }
        return true
    }
}

// MARK: - Environment injection

/// Environment key carrying the app's `ConsentStore` (D-22 injection seam).
private struct ConsentStoreKey: EnvironmentKey {
    // Default is a fresh UserDefaults-backed store so previews/tests have a sane value. The store is
    // `@MainActor`-isolated; SwiftUI only ever reads environment values on the main actor, so
    // constructing the default under `assumeIsolated` is safe and satisfies the nonisolated
    // `EnvironmentKey.defaultValue` requirement under Swift 6.
    static var defaultValue: any ConsentStore {
        MainActor.assumeIsolated { UserDefaultsConsentStore() }
    }
}

extension EnvironmentValues {
    /// The injected consent store the gate routes on (ONBD-01).
    var consentStore: any ConsentStore {
        get { self[ConsentStoreKey.self] }
        set { self[ConsentStoreKey.self] = newValue }
    }
}

/// Environment key carrying the app's `TerminologyStore` (ONBD-03 / D-07 injection seam).
private struct TerminologyStoreKey: EnvironmentKey {
    // Same main-actor-safe default pattern as the consent store.
    static var defaultValue: TerminologyStore {
        MainActor.assumeIsolated { TerminologyStore() }
    }
}

extension EnvironmentValues {
    /// The injected terminology store Settings edits and copy reads through (ONBD-03, D-07).
    var terminologyStore: TerminologyStore {
        get { self[TerminologyStoreKey.self] }
        set { self[TerminologyStoreKey.self] = newValue }
    }
}

/// Environment key carrying the app's `NotificationScheduler` (CHCK-02 / D-17 injection seam).
private struct NotificationSchedulerKey: EnvironmentKey {
    // Same main-actor-safe default pattern as the consent store: the scheduler is `@MainActor`-isolated
    // and SwiftUI reads environment values on the main actor, so constructing the default under
    // `assumeIsolated` is safe and satisfies the nonisolated `EnvironmentKey.defaultValue` requirement
    // under Swift 6.
    static var defaultValue: any NotificationScheduler {
        MainActor.assumeIsolated { SystemNotificationScheduler() }
    }
}

extension EnvironmentValues {
    /// The injected notification scheduler the reminders settings section drives (CHCK-02, D-17).
    var notificationScheduler: any NotificationScheduler {
        get { self[NotificationSchedulerKey.self] }
        set { self[NotificationSchedulerKey.self] = newValue }
    }
}

/// Environment key carrying the app's `AccountAvailability` seam (DATA-02/03, D-03 injection seam).
private struct AccountAvailabilityKey: EnvironmentKey {
    // Unlike the consent/terminology/scheduler keys, `AccountAvailability` is `Sendable` (NOT
    // `@MainActor`), so the default does NOT need `MainActor.assumeIsolated` — it constructs directly.
    static let defaultValue: any AccountAvailability = SystemAccountAvailability()
}

extension EnvironmentValues {
    /// The injected account-availability seam SettingsView reads for the iCloud sync status (04-02).
    var accountAvailability: any AccountAvailability {
        get { self[AccountAvailabilityKey.self] }
        set { self[AccountAvailabilityKey.self] = newValue }
    }
}

/// Environment key carrying the app's `HealthImportService` (DATA-05 injection seam, 09-03). Mirrors the
/// `insightsCoordinator` key shape: the connect/rationale/refresh UI reads it to drive authorization +
/// import. The DEFAULT is a benign, consent-disabled service over the `.empty` fixture so previews/tests
/// that do not inject the real service NEVER touch a real `HKHealthStore` (T-09-06) and NEVER persist.
private struct HealthImportServiceKey: EnvironmentKey {
    static let defaultValue = HealthImportService(
        reader: FixtureHealthDataReading(.empty),
        anchorStore: HealthAnchorStore(kv: InMemoryAnchorStore()),
        persist: { _, _ in },
        recompute: {},
        consentGuard: { false })
}

extension EnvironmentValues {
    /// The injected HealthKit import service the connect/rationale/refresh UI drives (DATA-05, 09-03).
    var healthImportService: HealthImportService {
        get { self[HealthImportServiceKey.self] }
        set { self[HealthImportServiceKey.self] = newValue }
    }
}

/// Environment key carrying the test-only deterministic chart-scrub day (Phase 13 E2E seam, T-13-02).
/// The default is `nil` — production-inert by construction: only a DEBUG launch that pairs
/// `-uitest-select-day` with `-uitest-reset` ever injects a value. `Date?` is `Sendable`, so no
/// `MainActor.assumeIsolated` dance is needed (unlike the consent/terminology keys above).
private struct UITestScrubDayKey: EnvironmentKey {
    static let defaultValue: Date? = nil
}

extension EnvironmentValues {
    /// The deterministic chart-scrub selection day for UI tests (Plan 13-03). `nil` on every normal
    /// launch; `InsightTrendChart` reads it in `.onAppear` behind its `allowsSelection` +
    /// `xDomain.contains` guards.
    var uiTestScrubDay: Date? {
        get { self[UITestScrubDayKey.self] }
        set { self[UITestScrubDayKey.self] = newValue }
    }
}
