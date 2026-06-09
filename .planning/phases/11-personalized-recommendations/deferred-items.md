# Phase 11 Deferred Items

## [11-02] Flaky/crashing pre-existing test — InsightsCoordinatorTests.dataChanged_coalescesRapidCallsIntoOneRecompute
- **Found during:** 11-02 full-scheme regression (EndoAppTests target).
- **Symptom:** The test intermittently crashes/times out ("Restarting after unexpected exit, crash, or test timeout"), failing the whole EndoAppTests run even though all 56 unit tests otherwise pass. It also hangs the isolated InsightsCoordinatorTests run before printing a result.
- **Why out of scope:** A debounce/timing coalescing test in InsightsCoordinator — does NOT touch RecommendationsEngine / RecommendationsCopy / ForbiddenTerms (this plan's files). Pre-existing; not introduced or affected by 11-02.
- **Action:** Deferred. A future plan should stabilize the coalescing test's timing assertion (e.g. inject a deterministic clock / await the debounce window instead of racing wall-clock).
