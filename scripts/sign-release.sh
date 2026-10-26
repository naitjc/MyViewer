#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ] && [ -x /opt/homebrew/opt/openjdk@21/bin/java ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
fi
MYVIEWER_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}"
MYVIEWER_SIGN_DIR="$PROJECT_DIR/.signing"
mkdir -p "$MYVIEWER_SIGN_DIR" "$PROJECT_DIR/dist"
chmod 700 "$MYVIEWER_SIGN_DIR"
if [ ! -f "$MYVIEWER_SIGN_DIR/password" ]; then
    python3 -c 'import secrets; print(secrets.token_urlsafe(32))' > "$MYVIEWER_SIGN_DIR/password"
    chmod 600 "$MYVIEWER_SIGN_DIR/password"
fi
if [ ! -f "$MYVIEWER_SIGN_DIR/myviewer.jks" ]; then
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$MYVIEWER_SIGN_DIR/myviewer.jks" -alias myviewer \
        -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=MyViewer Local" \
        -storepass:file "$MYVIEWER_SIGN_DIR/password" -keypass:file "$MYVIEWER_SIGN_DIR/password"
    chmod 600 "$MYVIEWER_SIGN_DIR/myviewer.jks"
fi
"$PROJECT_DIR/scripts/build.sh" :app:assembleRelease --no-daemon --console=plain
JAVA_HOME="$JAVA_HOME" "$MYVIEWER_SDK/build-tools/36.0.0/apksigner" sign \
    --ks "$MYVIEWER_SIGN_DIR/myviewer.jks" --ks-key-alias myviewer \
    --ks-pass "file:$MYVIEWER_SIGN_DIR/password" \
    --out "$PROJECT_DIR/dist/MyViewer-0.1.0.apk" "$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
JAVA_HOME="$JAVA_HOME" "$MYVIEWER_SDK/build-tools/36.0.0/apksigner" verify --verbose "$PROJECT_DIR/dist/MyViewer-0.1.0.apk"
printf '\n%s\n' "$PROJECT_DIR/dist/MyViewer-0.1.0.apk"

