---
status: partial
phase: 03-lifestyle-logging-fast-daily-check-in-reminders
source: [03-VERIFICATION.md, 03-VALIDATION.md]
started: 2026-06-08
updated: 2026-06-08
---

## Current Test

[awaiting human testing — OS-level notification behaviors XCUITest cannot assert]

## Tests

### 1. Local reminder fires + deep-links into check-in
expected: With a reminder enabled ~1–2 min out, backgrounding the app, the banner fires at the scheduled time; tapping it opens the Daily check-in sheet (inside the consented branch).
result: [pending]

### 2. Just-in-time permission prompt
expected: On first enabling a reminder, the iOS system notification-permission prompt appears (not at launch/onboarding).
result: [pending]

### 3. Denied permission degrades gracefully
expected: If notification permission is denied, the app stays fully usable; the reminders settings reflect the denied state and offer a path to Settings.app. No nagging.
result: [pending]

### 4. On-device quiet-hours + auto-suppress
expected: No reminder fires inside the quiet-hours window. After completing today's check-in, today's reminder does NOT show, but the repeating reminder STILL fires the next day (CR-01 fix — suppress today only, never permanently cancel).
result: [pending]

### 5. Mood 1–5 valence reads as "overall mood for the day" (UX clarity)
expected: On the simulator, the day-mood control reads clearly as the day's overall mood, distinct from the Phase-2 acute "Low mood" symptom (D-08, flagged for UAT).
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
