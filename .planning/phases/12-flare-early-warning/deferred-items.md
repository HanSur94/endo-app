# Phase 12 — Deferred Items

- [Plan 12-02, out-of-scope flake] `InsightsCoordinatorTests.dataChanged_coalescesRapidCallsIntoOneRecompute()` intermittently fails under full-suite parallel load (async-debounce timing), but PASSES in isolation and when its suite runs alone. Pre-existing Phase-8 coordinator test; unrelated to the flare files (which never touch InsightsCoordinator). Not fixed here — out of scope per the executor scope boundary.
