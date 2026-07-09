#!/bin/bash
set -e

# Build Native Image for Java Code Indexer
# Usage: ./build-native.sh <os> <arch> <version>

OS="${1:?Usage: $0 <os> <arch> <version>}"
ARCH="${2:?Usage: $0 <os> <arch> <version>}"
VERSION="${3:?Usage: $0 <os> <arch> <version>}"

echo "=== Building Native Image for ${OS}-${ARCH} v${VERSION} ==="

# Find JAR file
JAR=$(find target -maxdepth 1 -name "*.jar" -not -name "original-*" | head -n 1)
if [[ -z "$JAR" ]]; then
  echo "ERROR: No JAR found in target directory"
  ls -la target/
  exit 1
fi
echo "Found JAR: $JAR"

# Create work JAR with absolute path
WORK_JAR="$(pwd)/target/jindexer-build.jar"
cp "$JAR" "$WORK_JAR"
echo "Work JAR: $WORK_JAR"

# Fix sqlite-jdbc multi-release JAR
# sqlite-jdbc puts GraalVM Feature in META-INF/versions/9/
# native-image cannot recognize it, need to extract to root path
TMP_EXTRACT=$(mktemp -d)
unzip -qo "$WORK_JAR" "META-INF/versions/9/org/sqlite/*" -d "$TMP_EXTRACT" 2>/dev/null || true

if [[ -d "$TMP_EXTRACT/META-INF/versions/9/org/sqlite/nativeimage" ]]; then
  echo "Fixing sqlite-jdbc multi-release JAR..."
  # Remove sqlite directory from versions/9
  zip -qd "$WORK_JAR" "META-INF/versions/9/org/sqlite/*" 2>/dev/null || true
  # Add Feature class to JAR root
  (cd "$TMP_EXTRACT/META-INF/versions/9" && zip -qr "$WORK_JAR" org/sqlite/nativeimage/)
fi
rm -rf "$TMP_EXTRACT"

# Convert Windows path for GraalVM
GRAALVM_HOME_FIXED=$(echo "$GRAALVM_HOME" | sed 's|\\|/|g')
export PATH="$GRAALVM_HOME_FIXED/bin:$PATH"

echo "GRAALVM_HOME: $GRAALVM_HOME_FIXED"
echo "Java version: $(java -version 2>&1 | head -1)"

# Determine native-image command
if [[ "$OS" == "windows" ]]; then
  NATIVE_IMAGE="native-image.cmd"
else
  NATIVE_IMAGE="native-image"
fi

echo "Using native-image: $NATIVE_IMAGE"

# Build native image
"$NATIVE_IMAGE" -jar "$WORK_JAR" \
  -o "jindexer" \
  --no-fallback \
  -H:+ReportExceptionStackTraces \
  -H:+UnlockExperimentalVMOptions \
  -H:ConfigurationFileDirectories=src/main/resources/META-INF/native-image/com.sodlinken/jcodeindexer

echo "=== Native Image build completed ==="
