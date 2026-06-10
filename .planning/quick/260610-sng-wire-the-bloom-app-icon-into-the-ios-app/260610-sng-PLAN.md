---
phase: quick-260610-sng
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - Sources/EndoApp/Resources/Assets.xcassets/Contents.json
  - Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json
  - Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
  - project.yml
autonomous: true
requirements: [QUICK-ICON]

must_haves:
  truths:
    - "An asset catalog exists under Sources/EndoApp/Resources/ alongside Info.plist, EndoApp.entitlements, PrivacyInfo.xcprivacy"
    - "The installed AppIcon-1024.png has NO alpha channel (App Store / iOS icon requirement)"
    - "The EndoApp target declares ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon in project.yml"
    - "xcodegen generate succeeds and the generated project references ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon"
  artifacts:
    - path: "Sources/EndoApp/Resources/Assets.xcassets/Contents.json"
      provides: "Top-level asset-catalog manifest"
      contains: "\"author\" : \"xcode\""
    - path: "Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json"
      provides: "Single-size universal iOS app icon declaration"
      contains: "\"idiom\" : \"universal\""
    - path: "Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
      provides: "The 1024x1024 Bloom icon, alpha-stripped"
    - path: "project.yml"
      provides: "EndoApp target setting wiring the AppIcon name"
      contains: "ASSETCATALOG_COMPILER_APPICON_NAME"
  key_links:
    - from: "project.yml (EndoApp settings)"
      to: "Assets.xcassets/AppIcon.appiconset"
      via: "ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon"
      pattern: "ASSETCATALOG_COMPILER_APPICON_NAME"
    - from: "AppIcon.appiconset/Contents.json"
      to: "AppIcon-1024.png"
      via: "filename entry in images array"
      pattern: "AppIcon-1024.png"
---

<objective>
Wire the finished "Bloom" app icon (white five-petal flower, golden center, rose→plum gradient) into the EndoApp iOS target.

Purpose: The app currently ships with no app icon — there is no asset catalog anywhere in the repo. This installs the existing master art as the app icon using the modern single-size (1024×1024) format and wires it into the XcodeGen target so it is actually compiled into the app.

Output: A committed `Sources/EndoApp/Resources/Assets.xcassets/` asset catalog containing an alpha-stripped 1024 icon, plus a `project.yml` change naming the icon. The generated `.xcodeproj` is gitignored, so regeneration is a local verification step, not a committed artifact.
</objective>

<execution_context>
@$HOME/.claude/gsd-core/workflows/execute-plan.md
@$HOME/.claude/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@project.yml
@Sources/EndoApp/Resources/Info.plist

# Verified facts (do NOT re-investigate):
# - XcodeGen project; project.yml is committed, *.xcodeproj is gitignored, regenerated via `xcodegen generate` (installed at /opt/homebrew/bin/xcodegen).
# - App target: EndoApp, sources Sources/EndoApp, GENERATE_INFOPLIST_FILE: NO. NO asset catalog exists yet.
# - Master art: design/exports/AppIcon-1024.png — 1024×1024, square corners. CONFIRMED hasAlpha: YES → must be flattened.
# - Deployment target iOS 18; single-size 1024 icon format is supported (Xcode downscales the rest).
# - Resources/ already holds Info.plist, EndoApp.entitlements, PrivacyInfo.xcprivacy — the catalog belongs alongside them.
# - .xcassets is NOT gitignored → the catalog WILL be committed.
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create the asset catalog with the alpha-stripped Bloom icon</name>
  <files>
    Sources/EndoApp/Resources/Assets.xcassets/Contents.json
    Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json
    Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
  </files>
  <action>
    Create the directories `Sources/EndoApp/Resources/Assets.xcassets/` and `Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/`.

    Write the top-level catalog manifest `Assets.xcassets/Contents.json` as the standard Xcode stub: an object with a single `info` key holding `{ "author": "xcode", "version": 1 }`.

    Write the appiconset manifest `AppIcon.appiconset/Contents.json` declaring the modern single-size icon: an `images` array with ONE entry whose `idiom` is `"universal"`, `platform` is `"ios"`, `size` is `"1024x1024"`, and `filename` is `"AppIcon-1024.png"`, plus the same standard `info` block (`author` xcode, `version` 1). Do NOT add legacy per-size entries (40/60/76/etc.) — iOS 18 + modern Xcode downscale from the single 1024.

    Copy `design/exports/AppIcon-1024.png` into the appiconset as `AppIcon-1024.png` WITH THE ALPHA CHANNEL REMOVED. The master is confirmed `hasAlpha: yes`; App Store / iOS icons must have no alpha or the icon is rejected/blacked-out. Use `sips` to flatten: the reliable approach is to set the format and remove the alpha — e.g. `sips -s format png --setProperty hasAlpha no design/exports/AppIcon-1024.png --out Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`. If that sips invocation does not strip alpha on this macOS version, fall back to compositing onto an opaque background (e.g. `sips` matte / a Core Image flatten) — the END STATE that matters is `sips -g hasAlpha` reporting `no` on the installed file. Keep dimensions at 1024×1024.
  </action>
  <verify>
    <automated>test -f Sources/EndoApp/Resources/Assets.xcassets/Contents.json && test -f Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json && test -f Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png && [ "$(sips -g hasAlpha Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png | awk '/hasAlpha/{print $2}')" = "no" ] && [ "$(sips -g pixelWidth Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png | awk '/pixelWidth/{print $2}')" = "1024" ] && python3 -c "import json,sys; d=json.load(open('Sources/EndoApp/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json')); imgs=d['images']; assert len(imgs)==1, imgs; e=imgs[0]; assert e['idiom']=='universal' and e['size']=='1024x1024' and e['filename']=='AppIcon-1024.png', e; print('OK')"</automated>
  </verify>
  <done>The asset catalog exists with both Contents.json files and the icon PNG; the installed PNG reports hasAlpha=no at 1024×1024; the appiconset manifest declares exactly one universal 1024×1024 entry filename'd AppIcon-1024.png.</done>
</task>

<task type="auto">
  <name>Task 2: Wire the icon into project.yml and verify regeneration</name>
  <files>project.yml</files>
  <action>
    Edit `project.yml`: in the `EndoApp` target's existing `settings:` block (the one already holding PRODUCT_BUNDLE_IDENTIFIER, INFOPLIST_FILE, GENERATE_INFOPLIST_FILE, CODE_SIGN_ENTITLEMENTS, CODE_SIGN_STYLE), add the line `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon`. Match the existing two-space-under-settings indentation. Do NOT touch the test targets, the package block, or the top-level settings. The `Sources/EndoApp` source entry already globs the Resources directory, so the new `.xcassets` is picked up automatically — no `sources:` change is needed.

    After editing, regenerate the project with `xcodegen generate` (binary at /opt/homebrew/bin/xcodegen) to confirm the spec is valid and the catalog is wired. The resulting `EndoApp.xcodeproj` is gitignored and is a LOCAL verification artifact only — do not commit it.
  </action>
  <verify>
    <automated>grep -q 'ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon' project.yml && xcodegen generate 2>&1 | tail -3 && grep -rq 'ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon' EndoApp.xcodeproj/project.pbxproj && echo WIRED_OK</automated>
  </verify>
  <done>project.yml sets ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon under the EndoApp target; `xcodegen generate` succeeds; the generated project.pbxproj contains `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon`; output ends with WIRED_OK.</done>
</task>

</tasks>

<verification>
End-to-end checks for the phase:
1. `Sources/EndoApp/Resources/Assets.xcassets/` exists alongside Info.plist with both Contents.json files and the icon PNG.
2. `sips -g hasAlpha .../AppIcon-1024.png` reports `no` (alpha stripped) at 1024×1024.
3. `project.yml` contains `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon` in the EndoApp target settings.
4. `xcodegen generate` succeeds and the generated `EndoApp.xcodeproj/project.pbxproj` references `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon`.

Note: a full `xcodebuild` icon-asset compile is a stronger check but is slow and not required — the regeneration + pbxproj grep above is the lightest reliable proof the icon is wired. The executor MAY run a build if it is fast on this machine, but it is optional.
</verification>

<success_criteria>
- A committed asset catalog under `Sources/EndoApp/Resources/Assets.xcassets/` containing the alpha-stripped 1024 Bloom icon in modern single-size format.
- `project.yml` names the icon via `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon`.
- `xcodegen generate` succeeds with the catalog picked up and the setting present in the generated project.
- The generated `.xcodeproj` is NOT committed (gitignored).
</success_criteria>

<output>
Create `.planning/quick/260610-sng-wire-the-bloom-app-icon-into-the-ios-app/260610-sng-SUMMARY.md` when done.
</output>
