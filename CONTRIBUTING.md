# Contributing to OpenEndo

Bug reports, wording fixes, accessibility findings and platform-specific fixes are all welcome.
Please open an issue first for anything larger than a small fix — this is a one-person project and
an uncoordinated big PR is likely to be rewritten.

## The one hard rule

User-facing copy is audited by tests. Strings may not:

- claim a diagnosis, treatment, cure, or prediction presented as fact,
- use alarmist language ("danger", "warning: you will…"),
- assume gender in default copy.

The audits live in `Tests/EndoAppTests` (`ClaimsPolicy`, `Terminology`, copy-audit suites) and are
run in CI. If you add copy, add it to the auditable string lists. PRs that break the audit do not merge.

## Build and test

```bash
# iOS
brew install xcodegen
xcodegen generate
xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 17'

# Android
cd android && ./gradlew test
```

## Commits

`type(scope): summary`, types used here: `feat`, `fix`, `test`, `style`, `docs`, `refactor`, `chore`.
One logical change per commit. No generated files: `*.xcodeproj`, `build/`, `DerivedData`.

## Data handling

Never commit real health data, screenshots of real records, keystores, or credentials. Test
fixtures must be synthetic.

## Security

See [SECURITY.md](SECURITY.md) — do not open a public issue for anything touching health data.
