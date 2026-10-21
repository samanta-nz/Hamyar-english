#!/usr/bin/env bash
# Populate library/src/main/assets/espeak-ng-data/ with the espeak-ng 1.52.0 phoneme
# data (needed at runtime for G2P). The data is produced by espeak-ng's own compiler,
# so we take it from a host build/install rather than cross-compiling it.
set -euo pipefail

ASSETS_DIR="$(cd "$(dirname "$0")/.." && pwd)/library/src/main/assets/espeak-ng-data"

echo "Target: $ASSETS_DIR"

# Option A — from a host espeak-ng install (Debian/Ubuntu):
if [ -d /usr/lib/x86_64-linux-gnu/espeak-ng-data ]; then
  SRC=/usr/lib/x86_64-linux-gnu/espeak-ng-data
elif [ -d /usr/share/espeak-ng-data ]; then
  SRC=/usr/share/espeak-ng-data
else
  echo "espeak-ng-data not found. Install espeak-ng 1.52.0 first:"
  echo "  sudo apt-get install -y espeak-ng-data   # or build the submodule with 'make'"
  exit 1
fi

echo "Copying from $SRC"
rm -f "$ASSETS_DIR/README.md" 2>/dev/null || true
cp -r "$SRC"/. "$ASSETS_DIR"/
echo "Done. $(du -sh "$ASSETS_DIR" | cut -f1) staged."
