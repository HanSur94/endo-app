#!/usr/bin/env bash
# Publish the landing page to the NAS container, always from origin/main — never from whatever the
# working tree happens to be on. Serving dir is /volume1/docker/openendo-site (python http.server,
# uid 1000:10 because of the UGOS ACL, Cache-Control: no-store).
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
NAS="Hannes@192.168.178.40"
KEY="${NAS_KEY:-$HOME/.ssh/id_nas}"
DEST="/volume1/docker/openendo-site"
URL="http://192.168.178.40:8491/"

git -C "$REPO" fetch -q origin
git -C "$REPO" archive --format=tar origin/main:docs | ssh -q -i "$KEY" "$NAS" "tar -xf - -C $DEST"

want="$(git -C "$REPO" show origin/main:docs/index.html | shasum -a 256 | cut -c1-16)"
got="$(curl -s --max-time 10 "$URL" | shasum -a 256 | cut -c1-16)"
echo "origin/main: $want"
echo "nas:         $got"
[ "$want" = "$got" ] || { echo "MISMATCH — the NAS is not serving origin/main" >&2; exit 1; }
echo "ok — $URL serves origin/main"
