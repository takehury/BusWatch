#!/bin/bash
# Builds the Wear OS 3/4/5 variant of Krasnoyarsk Bus Watch.
set -euo pipefail
cd "$(dirname "$0")"

BT="${BUILD_TOOLS:-/tmp/bt34/android-14}"
PLATFORM="${PLATFORM_DIR:-/tmp/pf33/android-13}"
JAVA_HOME="${JAVA_HOME:-/tmp/jdk17home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

AJ="$PLATFORM/android.jar"
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" dist

echo "[1/6] aapt2 compile"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"

echo "[2/6] aapt2 link (targetSdk 34, watch required)"
"$BT/aapt2" link \
  -o "$OUT/app-unsigned.apk" \
  -I "$AJ" \
  --manifest AndroidManifest.xml \
  "$OUT/res.zip" \
  -A assets \
  --java "$OUT/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --version-code 131 \
  --version-name 1.68-wear \
  --no-version-vectors

echo "[3/6] javac"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 8 -encoding UTF-8 -classpath "$AJ" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/6] d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 26 --lib "$AJ" --output "$OUT/dex" @"$OUT/classes.txt"
(cd "$OUT/dex" && zip -q -j ../app-unsigned.apk classes.dex)

echo "[5/6] zipalign"
"$BT/zipalign" -f -p 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

echo "[6/6] sign"
KS=keystore/debug.keystore
[ -f "$KS" ] || KS=../../keystore/debug.keystore
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out dist/KrskBusWatch-wear-1.68.apk "$OUT/app-aligned.apk"
"$BT/apksigner" verify --print-certs dist/KrskBusWatch-wear-1.68.apk | head -4
ls -lh dist/KrskBusWatch-wear-1.68.apk
