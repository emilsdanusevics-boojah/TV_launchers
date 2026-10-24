#!/usr/bin/env bash
# Builds and signs MinimalTVLauncher.apk without Gradle.
# Needs (Debian/Ubuntu): aapt dalvik-exchange apksigner zipalign android-sdk-platform-23 openjdk
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
DX=${DX:-/usr/lib/android-sdk/build-tools/debian/dx}
OUT=build

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$ANDROID_JAR"

javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java')

"$DX" --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/classes"

aapt package -f -M AndroidManifest.xml -S res -A assets -I "$ANDROID_JAR" -F "$OUT/unsigned.apk"
(cd "$OUT" && aapt add unsigned.apk classes.dex >/dev/null)

zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f release.keystore ]; then
  keytool -genkeypair -keystore release.keystore -storepass android -keypass android \
    -alias launcher -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Minimal TV Launcher" >/dev/null 2>&1
fi
apksigner sign --ks release.keystore --ks-pass pass:android --ks-key-alias launcher \
  --out MinimalTVLauncher.apk "$OUT/aligned.apk"
apksigner verify MinimalTVLauncher.apk
echo "Built $(pwd)/MinimalTVLauncher.apk"
