# OpenEndo

**A free, privacy-first endometriosis symptom tracker — native iOS and native Android.**
No account, no backend, no analytics. Your data stays on your device and in your own private iCloud.

[![ios](https://github.com/HanSur94/endo-app/actions/workflows/ios.yml/badge.svg)](https://github.com/HanSur94/endo-app/actions/workflows/ios.yml)
[![android](https://github.com/HanSur94/endo-app/actions/workflows/android.yml/badge.svg)](https://github.com/HanSur94/endo-app/actions/workflows/android.yml)
[![license: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

<img src="design/exports/AppIcon-1024.png" width="120" alt="OpenEndo app icon">

## What it is

Logging symptoms for years should tell you something. OpenEndo turns daily self-tracking into
charts you can actually read: trends, cycle-phase patterns, factor-vs-factor comparisons, and
descriptive insights that never pretend to diagnose anything.

Built by one person, free for everyone, open source. It is a **wellness self-tracking tool, not a
medical device** — it does not diagnose, treat, or replace medical advice.

## What it does

- **Fast daily check-in** — pain areas on a body map, severity, bleeding, fatigue, mood, GI/"endo belly",
  food tags, activity and sleep. Prefilled with yesterday's meds/sleep/activity, never with symptoms.
- **Cycle tracking** that tolerates irregular or hormonally suppressed cycles.
- **Apple Health / Health Connect import** (read-only, optional) so you don't log twice.
- **Insights** — trend charts with scrub-to-read, cycle-phase mapping, correlations, flare
  early warning, and a "possible association" card you can open into the days behind it.
- **Doctor-ready PDF** — a shareable summary with per-source labels and prominent
  "self-tracked, not diagnostic" framing. No silent upload; it goes through the system share sheet.
- **Private iCloud sync** across *your* devices. There is no account because there is no server.

Hard rules the code enforces (and its tests audit): absence is never rendered as zero, no dual-axis
charts, no fitted lines or r-values, no alarmist or diagnostic copy.

## Privacy

Everything is stored on-device. Sync, if you enable it, goes only to your private CloudKit database —
a backend that only you can read. There is no tracking SDK, no advertising, no third-party sharing.

Full policy: [docs/privacy.md](docs/privacy.md) — the same text the app shows in-app
(`Sources/EndoApp/Content/PrivacyPolicyText.swift`, kept in sync by `scripts/sync-privacy.sh`).

## Build it

**iOS** (Xcode 26+, iOS 18+):

```bash
brew install xcodegen        # the .xcodeproj is generated, never committed
xcodegen generate
open EndoApp.xcodeproj
```

Tests, headless:

```bash
xcodegen generate
xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 17'
```

HealthKit and CloudKit stay inert on the Simulator and without a paid Apple Developer team.

**Android** (JDK 21):

```bash
cd android
./gradlew :app:assembleDebug     # APK at app/build/outputs/apk/debug/
./gradlew test                   # JVM unit tests (insights engine + app)
```

Health Connect is read-only, optional, and needs a physical device (Android 14+) or the Health
Connect app on older versions.

## Repository layout

| Path | What |
|---|---|
| `Sources/EndoApp` | iOS app (SwiftUI, SwiftData, HealthKit, CloudKit) |
| `EndoInsights` | Foundation-only insights engine as a local Swift package — zero dependencies |
| `android/` | Native Android app (Kotlin, Compose, Room, Health Connect) |
| `Tests/` | iOS unit + UI tests, including the copy-audit suite |
| `design/exports` | App icon sources |
| `.planning/` | The design and decision record — requirements, phases, milestone audits |

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). The one non-negotiable: user-facing copy must pass the
claims/forbidden-verb audit tests. This is a health app for a condition people are routinely
gaslit about; the wording rules are the product, not a style preference.

## Status

- iOS: v1.1 feature-complete (interactive charts + Explore).
- Android: native port, heading for its first store release.
- 1.0 on the stores will be English; German/French/Spanish follow with the multilingual milestone.

## License

[MIT](LICENSE) © 2026 Hannes Suhr. Not affiliated with any other app of a similar name.
