#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ] && [ -x /opt/homebrew/opt/openjdk@21/bin/java ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
fi
export GRADLE_USER_HOME="${MYVIEWER_GRADLE_HOME:-$PROJECT_DIR/.tooling/gradle-home}"
if [ -d "$HOME/.gradle/caches/modules-2" ]; then
    export GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches"
fi
if [ ! -f "$PROJECT_DIR/local.properties" ] && [ -d /opt/homebrew/share/android-commandlinetools/platforms/android-36 ]; then
    printf 'sdk.dir=/opt/homebrew/share/android-commandlinetools\n' > "$PROJECT_DIR/local.properties"
fi
MYVIEWER_GRADLE=$(find "$HOME/.gradle/wrapper/dists/gradle-8.14.3-all" -path '*/bin/gradle' -type f 2>/dev/null | head -n 1)
if [ -z "$MYVIEWER_GRADLE" ] && [ -x "$PROJECT_DIR/gradlew" ]; then
    exec "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" "$@"
fi
if [ -z "$MYVIEWER_GRADLE" ]; then
    printf '%s\n' '请安装 Gradle 8.14.3，或生成 Gradle Wrapper 后重试。' >&2
    exit 1
fi
exec "$MYVIEWER_GRADLE" -p "$PROJECT_DIR" "$@"
