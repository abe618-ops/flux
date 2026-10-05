#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$(mktemp -d /tmp/wechat-ai-build.XXXXXX)"
trap 'rm -rf "$BUILD_DIR"' EXIT
TOOL_DIR="${AI_ARCHIVE_BUILD_TOOLS:-${ANDROID_HOME:-}/build-tools/35.0.0}"
ANDROID_JAR="${AI_ARCHIVE_ANDROID_JAR:-${ANDROID_HOME:-}/platforms/android-35/android.jar}"
if [[ -z "${ANDROID_HOME:-}" && ( -z "${AI_ARCHIVE_BUILD_TOOLS:-}" || -z "${AI_ARCHIVE_ANDROID_JAR:-}" ) ]]; then
  echo "Set ANDROID_HOME, or set both AI_ARCHIVE_BUILD_TOOLS and AI_ARCHIVE_ANDROID_JAR." >&2
  exit 1
fi
KEYSTORE="$PROJECT_DIR/signing/wechat-ai-archive-v2.keystore"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$PROJECT_DIR/signing"
for required in "$TOOL_DIR/aapt2" "$TOOL_DIR/lib/d8.jar" "$TOOL_DIR/apksigner" "$ANDROID_JAR"; do [[ -f "$required" ]] || { echo "Missing Android build tool: $required" >&2; exit 1; }; done
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR" -d "$BUILD_DIR/classes" "$PROJECT_DIR"/src/com/local/wechataiarchive/*.java
(cd "$BUILD_DIR/classes" && zip -q -r "$BUILD_DIR/classes.jar" .)
java -cp "$TOOL_DIR/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 23 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
"$TOOL_DIR/aapt2" compile --dir "$PROJECT_DIR/res" -o "$BUILD_DIR/resources.zip"
"$TOOL_DIR/aapt2" link -o "$BUILD_DIR/unsigned.apk" -I "$ANDROID_JAR" --manifest "$PROJECT_DIR/AndroidManifest.xml" --min-sdk-version 23 --target-sdk-version 35 --version-code 20000 --version-name 2.0.0 --auto-add-overlay -A "$PROJECT_DIR/assets" "$BUILD_DIR/resources.zip"
zip -q -0 -j "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/dex/classes.dex"
"$TOOL_DIR/zipalign" -f -p 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
if [[ ! -f "$KEYSTORE" ]]; then keytool -genkeypair -noprompt -keystore "$KEYSTORE" -storepass android -keypass android -alias wechatai -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Wechat AI Archive v2,O=Local Tools,C=CN" >/dev/null 2>&1; fi
"$TOOL_DIR/apksigner" sign --ks "$KEYSTORE" --ks-key-alias wechatai --ks-pass pass:android --key-pass pass:android --out "$PROJECT_DIR/Wechat-AI-Archive-v2.0.apk" "$BUILD_DIR/aligned.apk"
"$TOOL_DIR/apksigner" verify --verbose --print-certs "$PROJECT_DIR/Wechat-AI-Archive-v2.0.apk"
"$TOOL_DIR/zipalign" -c -p 4 "$PROJECT_DIR/Wechat-AI-Archive-v2.0.apk"
echo "Built: $PROJECT_DIR/Wechat-AI-Archive-v2.0.apk"
