---
phase: quick-260610-sng
plan: 01
subsystem: ios-app-shell
tags: [app-icon, asset-catalog, xcodegen, branding]
requires:
  - design/exports/AppIcon-1024.png (Bloom master art)
  - project.yml (EndoApp XcodeGen target)
provides:
  - Sources/EndoApp/Resources/Assets.xcassets (compiled app-icon asset catalog)
  - ASSETCATALOG_COMPILER_APPICON_NAME wiring on the EndoApp target
affects:
  - EndoApp.xcodeproj (regenerated; gitignored)
tech-stack:
  added: []
  patterns:
    - "Modern single-size (1024x1024) app-icon format — Xcode downscales the rest"
    - "CoreGraphics opaque-context flatten for alpha stripping when sips can't"
key-files:
  created:
    - Sources/EndoApp/Resources/Assets.xcassets/Contents.json
    - Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json
    - Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
  modified:
    - project.yml
decisions:
  - "Flattened alpha via a Quartz/CoreGraphics opaque context, not sips, because sips --setProperty hasAlpha no does not strip alpha on this macOS toolchain"
metrics:
  duration: ~4m
  completed: 2026-06-10
requirements: [QUICK-ICON]
---

# Phase quick-260610-sng Plan 01: Wire the Bloom App Icon Summary

Installed the finished "Bloom" app icon as an alpha-stripped 1024x1024 single-size asset catalog and wired it into the EndoApp XcodeGen target via `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon`.

## What Was Built

- A new `Sources/EndoApp/Resources/Assets.xcassets/` catalog sitting alongside `Info.plist`, `EndoApp.entitlements`, and `PrivacyInfo.xcprivacy`.
  - Top-level `Contents.json`: standard Xcode stub (`author: xcode, version: 1`).
  - `AppIcon.appiconset/Contents.json`: exactly one `universal` / `ios` / `1024x1024` entry filenamed `AppIcon-1024.png` (modern single-size format; no legacy per-size entries).
  - `AppIcon.appiconset/AppIcon-1024.png`: the Bloom master art, flattened to `hasAlpha: no` at 1024x1024.
- `project.yml`: added `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon` to the `EndoApp` target `settings:` block (alongside the existing bundle id / Info.plist / entitlements / signing settings). No source-glob change needed — `Sources/EndoApp` already picks up the new catalog.

## Tasks

| Task | Name | Commit | Files |
| ---- | ---- | ------ | ----- |
| 1 | Create asset catalog with alpha-stripped Bloom icon | `0b34fb6` | Assets.xcassets/Contents.json, AppIcon.appiconset/Contents.json, AppIcon-1024.png |
| 2 | Wire icon into project.yml + verify regeneration | `16986f9` | project.yml |

## Verification Gates

- **Task 1 (automated):** PASSED — all three files exist; installed PNG reports `hasAlpha=no` at `pixelWidth=1024`; appiconset manifest has exactly one `universal` / `1024x1024` / `AppIcon-1024.png` entry. Gate printed `OK`.
- **Task 2 (automated):** PASSED — `project.yml` contains `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon`; `xcodegen generate` succeeded; generated `project.pbxproj` contains `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon`. Gate printed `WIRED_OK`.
- **End-to-end:** PASSED — catalog sits alongside Info.plist; PNG `hasAlpha: no` at 1024x1024; setting present in `project.yml`; setting appears twice in the generated `project.pbxproj` (Debug + Release configs).
- **Gitignore check:** PASSED — `EndoApp.xcodeproj/project.pbxproj` is gitignored (regeneration is a local-only verification artifact, not committed).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] sips could not strip the alpha channel**
- **Found during:** Task 1
- **Issue:** The plan's primary command `sips -s format png --setProperty hasAlpha no ...` did not strip alpha on this macOS toolchain — the output PNG still reported `hasAlpha: yes` (and one `--out` invocation produced an invalid/empty file). The `--padToHeightWidth ... --padColor` matte fallback also left `hasAlpha: yes` (no padding occurs at the source's native 1024x1024).
- **Fix:** Applied the plan-sanctioned fallback ("composite onto an opaque background ... the END STATE that matters is `sips -g hasAlpha` reporting `no`"). Used a Quartz/CoreGraphics opaque RGB context (`kCGImageAlphaNoneSkipLast`), filled white, drew the source image, and re-encoded as PNG. Result: `hasAlpha: no` at 1024x1024.
- **Files modified:** Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
- **Commit:** `0b34fb6`

Note: the Bloom art fills the full square (rose→plum gradient background), so flattening removes the alpha channel with no visible compositing artifact.

## Notes

- `design/exports/AppIcon-1024.png` (the master art) remains untracked in the working tree — it is the source for this task and outside the committed artifact set; left unchanged (`hasAlpha: yes`).
- A full `xcodebuild` icon-asset compile was not run (optional per the plan); regeneration + pbxproj grep is the lightweight reliable proof the icon is wired.

## Self-Check: PASSED

- FOUND: Sources/EndoApp/Resources/Assets.xcassets/Contents.json
- FOUND: Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json
- FOUND: Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png (hasAlpha=no, 1024x1024)
- FOUND: project.yml contains ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon
- FOUND commit: 0b34fb6 (Task 1)
- FOUND commit: 16986f9 (Task 2)
