#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/tmp/flashnote-android-sdk}}"
BUILD_TOOLS="$ANDROID_SDK_DIR/build-tools/35.0.1"
ANDROID_JAR="$ANDROID_SDK_DIR/platforms/android-35/android.jar"
BUILD_DIR="$PROJECT_DIR/build"
OUTPUT_APK="$PROJECT_DIR/dist/FlashNotePush-Web-v1.5.3.apk"
SIGNED_APK="$BUILD_DIR/app-signed-$$.apk"
mkdir -p "$BUILD_DIR/compiled" "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$PROJECT_DIR/dist"
"$BUILD_TOOLS/aapt2" compile --dir "$PROJECT_DIR/res" -o "$BUILD_DIR/compiled/resources.zip"
"$BUILD_TOOLS/aapt2" link -o "$BUILD_DIR/resources.apk" -I "$ANDROID_JAR" --manifest "$PROJECT_DIR/AndroidManifest.xml" \
  --java "$BUILD_DIR/gen" --min-sdk-version 23 --target-sdk-version 35 --version-code 153 --version-name 1.5.3 "$BUILD_DIR/compiled/resources.zip"
java com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR" \
  -d "$BUILD_DIR/classes" $(find "$PROJECT_DIR/src" "$BUILD_DIR/gen" -name '*.java' | sort)
(cd "$BUILD_DIR/classes" && zip -q -r "$BUILD_DIR/classes.jar" .)
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 23 --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
cp "$BUILD_DIR/resources.apk" "$BUILD_DIR/app-with-dex-unaligned.apk"
(cd "$BUILD_DIR/dex" && zip -q -u "$BUILD_DIR/app-with-dex-unaligned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/app-with-dex-unaligned.apk" "$BUILD_DIR/app-aligned.apk"
KEYSTORE="${FLASHNOTE_KEYSTORE:?Set FLASHNOTE_KEYSTORE to your signing keystore path}"
KEY_ALIAS="${FLASHNOTE_KEY_ALIAS:?Set FLASHNOTE_KEY_ALIAS}"
KEYSTORE_PASS="${FLASHNOTE_KEYSTORE_PASS:?Set FLASHNOTE_KEYSTORE_PASS}"
KEY_PASS="${FLASHNOTE_KEY_PASS:-$KEYSTORE_PASS}"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASS" --key-pass "pass:$KEY_PASS" --out "$SIGNED_APK" "$BUILD_DIR/app-aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$SIGNED_APK"
cp "$SIGNED_APK" "$OUTPUT_APK"
