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
echo "=== Fixing sqlite-jdbc multi-release JAR ==="
TMP_EXTRACT=$(mktemp -d)
echo "Temp directory: $TMP_EXTRACT"

# Use jar command to extract (works better on Windows)
echo "Extracting with jar command..."
cd "$TMP_EXTRACT"
jar xf "$WORK_JAR" META-INF/versions/9/org/sqlite/nativeimage/ 2>/dev/null || true
cd - > /dev/null

echo "Extracted files:"
find "$TMP_EXTRACT" -type f 2>/dev/null || echo "No files extracted"

if [[ -d "$TMP_EXTRACT/META-INF/versions/9/org/sqlite/nativeimage" ]]; then
  echo "Found nativeimage directory!"

  # List the extracted files
  echo "Nativeimage files:"
  ls -la "$TMP_EXTRACT/META-INF/versions/9/org/sqlite/nativeimage/"

  # Remove sqlite directory from versions/9 in JAR
  echo "Removing versions/9/org/sqlite from JAR..."
  zip -qd "$WORK_JAR" "META-INF/versions/9/org/sqlite/*" 2>/dev/null || true

  # Add Feature class to JAR root
  echo "Adding nativeimage to JAR root..."
  cd "$TMP_EXTRACT/META-INF/versions/9"
  zip -qr "$WORK_JAR" org/sqlite/nativeimage/
  cd - > /dev/null

  echo "=== sqlite-jdbc fix completed ==="
  echo "Verifying fix:"
  zip -l "$WORK_JAR" | grep -i "nativeimage" || echo "No nativeimage files found"
else
  echo "WARNING: nativeimage directory not found in extracted files"
  echo "This may indicate the JAR does not contain the multi-release sqlite Feature"
fi
rm -rf "$TMP_EXTRACT"

# Convert Windows path for GraalVM
GRAALVM_HOME_FIXED=$(echo "$GRAALVM_HOME" | sed 's|\\|/|g')
export PATH="$GRAALVM_HOME_FIXED/bin:$PATH"

echo ""
echo "=== Build Configuration ==="
echo "GRAALVM_HOME: $GRAALVM_HOME_FIXED"
echo "Java version: $(java -version 2>&1 | head -1)"

# Determine native-image command
if [[ "$OS" == "windows" ]]; then
  NATIVE_IMAGE="native-image.cmd"
else
  NATIVE_IMAGE="native-image"
fi

echo "Using native-image: $NATIVE_IMAGE"
echo ""

# Build native image
"$NATIVE_IMAGE" -jar "$WORK_JAR" \
  -o "jindexer" \
  --no-fallback \
  -H:+ReportExceptionStackTraces \
  -H:+UnlockExperimentalVMOptions \
  -H:ConfigurationFileDirectories=src/main/resources/META-INF/native-image/com.sodlinken/jcodeindexer

echo "=== Native Image build completed ==="
