# Security Policy

## Reporting a vulnerability

Use GitHub's **private vulnerability reporting** (Security → Report a vulnerability) rather than a
public issue, or email the maintainer listed in [docs/privacy.md](docs/privacy.md).

Include what you did, what happened, and the app version + platform. A proof of concept helps.

## What counts as high severity here

This is a health-data app with no server. The serious classes are:

- health data leaving the device or the user's private iCloud without consent,
- data readable by another app or an unauthenticated local process,
- export/share paths that upload instead of handing the file to the system share sheet,
- consent state that can be bypassed or silently reset.

## Disclosure

Coordinated disclosure: report privately, get an acknowledgement within a week. A fix ships in the
next release and the report is credited unless you prefer otherwise.
