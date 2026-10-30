#!/usr/bin/env bash
# Makes the Apartment launcher's videos: bundles web/apartment.js with three.js into a page,
# then renders one full turn of the flat for day and for night (web/render_video.js) into
# ../assets/apartment. Rendering is done in software and takes about an hour per video.
# Needs Node.js, Playwright's Chromium and ffmpeg; installs three@0.160.0 (the version the
# original page used), esbuild and playwright.
set -euo pipefail
cd "$(dirname "$0")"
[ -d node_modules/three ] || npm install --no-save --silent three@0.160.0 esbuild@0.24.0 playwright
page=$(mktemp -d)
cp render.html "$page/index.html"
npx esbuild apartment.js --bundle --minify --format=iife --target=es2017 --outfile="$page/apartment.js"
for variant in day night; do
  node render_video.js "$page" "$variant" "../assets/apartment/orbit_$variant.mp4"
done
rm -rf "$page"
