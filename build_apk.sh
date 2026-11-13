#!/bin/bash
# Builds a signed APK without Gradle (only aapt2/javac/d8/apksigner).
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_SDK_ROOT:-/tmp/android-sdk}"
BT="${BUILD_TOOLS:-/tmp/bt34/android-14}"
PLATFORM="${PLATFORM_DIR:-/tmp/pf33/android-13}"
JAVA_HOME="${JAVA_HOME:-/tmp/jdk17home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

AJ="$PLATFORM/android.jar"
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" versions/v1.70

echo "[1/7] aapt2 compile"
"$BT/aapt2" compile --dir app/res -o "$OUT/res.zip"

echo "[2/7] aapt2 link"
"$BT/aapt2" link \
  -o "$OUT/app-unsigned.apk" \
  -I "$AJ" \
  --manifest app/AndroidManifest.xml \
  "$OUT/res.zip" \
  -A app/assets \
  --java "$OUT/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 33 \
  --version-code 70 \
  --version-name 1.70 \
  --no-version-vectors

echo "[3/7] javac"
find app/src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 8 -encoding UTF-8 -classpath "$AJ" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/7] d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 26 --lib "$AJ" --output "$OUT/dex" @"$OUT/classes.txt"
(cd "$OUT/dex" && zip -q -j ../app-unsigned.apk classes.dex)

echo "[5/7] zipalign"
"$BT/zipalign" -f -p 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

echo "[6/7] sign"
KS=keystore/debug.keystore
if [ ! -f "$KS" ]; then
  mkdir -p keystore
  keytool -genkeypair -v -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Krasnoyarsk Bus Watch, OU=Watch, O=Local, L=Krasnoyarsk, C=RU" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out versions/v1.70/KrskBusWatch-1.70.apk "$OUT/app-aligned.apk"

echo "[7/7] verify"
"$BT/apksigner" verify --print-certs versions/v1.70/KrskBusWatch-1.70.apk | head -8
ls -lh versions/v1.70/KrskBusWatch-1.70.apk
