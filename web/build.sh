#!/usr/bin/env bash
# Bundles web/apartment.js and three.js into one plain script for the launcher.
# The TV's WebView loads it from the APK, so nothing is fetched from the internet.
# Needs Node.js; installs three@0.160.0 (the version the original page used) and esbuild.
set -euo pipefail
cd "$(dirname "$0")"
[ -d node_modules/three ] || npm install --no-save --silent three@0.160.0 esbuild@0.24.0
npx esbuild apartment.js --bundle --minify --format=iife --target=es2017 \
  --outfile=../assets/apartment/apartment.js
