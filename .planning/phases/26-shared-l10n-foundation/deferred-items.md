# Phase 26 — Deferred Items (out of scope, logged by executors)

| Found in | Item | Why deferred |
|----------|------|--------------|
| 26-01 | `> Configure project :app`: "Using a Project object as a dependency notation has been deprecated. This will fail with an error in Gradle 10." (`android/app/build.gradle.kts`, project-object dependency notation) | Existed before this phase; unrelated to l10n. Fix with `project(":insights")` string notation in a separate quick task before any Gradle 10 upgrade. |
