#!/usr/bin/env bash
# Builds :payload and :qr's npm library output, packs each into a real tarball (not a
# `file:`-directory dependency — that symlinks, and Node resolves a symlinked package's
# own requires relative to its real path, missing this app's node_modules entirely), and
# installs those tarballs here. Re-run after any change to :payload or :qr.
set -euo pipefail
cd "$(dirname "$0")"

REPO_ROOT="$(cd ../.. && pwd)"
PACKAGES_DIR="$(pwd)/.local-packages"
mkdir -p "$PACKAGES_DIR"

echo "==> Building payload/qr npm library output"
(cd "$REPO_ROOT" && ./gradlew :payload:jsNodeProductionLibraryDistribution :qr:jsNodeProductionLibraryDistribution)

echo "==> Packing tarballs"
rm -f "$PACKAGES_DIR"/*.tgz
(cd "$REPO_ROOT/payload/build/dist/js/productionLibrary" && npm pack --pack-destination "$PACKAGES_DIR" --silent)
(cd "$REPO_ROOT/qr/build/dist/js/productionLibrary" && npm pack --pack-destination "$PACKAGES_DIR" --silent)

echo "==> Installing"
npm install \
  "$PACKAGES_DIR"/khalid64927-qr-studio-sg-payload-*.tgz \
  "$PACKAGES_DIR"/khalid64927-qr-studio-sg-qr-*.tgz \
  @js-joda/core@3.2.0

echo "==> Done. Run 'npm run dev' or 'npm run build && npm run start'."
