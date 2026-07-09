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
echo "Checking sqlite-jdbc multi-release JAR..."
TMP_EXTRACT=$(mktemp -d)
echo "Extracting to: $TMP_EXTRACT"

# List sqlite files in JAR
echo "SQLite files in JAR:"
unzip -l "$WORK_JAR" 2>/dev/null | grep -i "nativeimage" || echo "No nativeimage files found"

# Try to extract the nativeimage directory specifically
unzip -qo "$WORK_JAR" "META-INF/versions/9/org/sqlite/nativeimage/*" -d "$TMP_EXTRACT" 2>/dev/null || true

echo "Extracted files:"
find "$TMP_EXTRACT" -type f 2>/dev/null || echo "No files extracted"

if [[ -d "$TMP_EXTRACT/META-INF/versions/9/org/sqlite/nativeimage" ]]; then
  echo "Found nativeimage directory, fixing sqlite-jdbc multi-release JAR..."
  # Remove sqlite directory from versions/9
  zip -qd "$WORK_JAR" "META-INF/versions/9/org/sqlite/*" 2>/dev/null || true
  # Add Feature class to JAR root
  (cd "$TMP_EXTRACT/META-INF/versions/9" && zip -qr "$WORK_JAR" org/sqlite/nativeimage/)
  echo "sqlite-jdbc fix completed"
  # Verify the fix
  echo "Verifying fix - nativeimage files in JAR:"
  zip -l "$WORK_JAR" | grep -i "nativeimage" || echo "No nativeimage files found after fix"
else
  echo "WARNING: nativeimage directory not found, skipping fix"
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
