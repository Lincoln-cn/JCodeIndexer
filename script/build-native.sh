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

# Remove sqlite-jdbc native-image configuration that causes issues
# The SqliteJdbcFeature is in a multi-release JAR that GraalVM can't access
echo "=== Removing problematic sqlite-jdbc native-image config ==="
TMP_EXTRACT=$(mktemp -d)
echo "Temp directory: $TMP_EXTRACT"

# Extract the JAR
cd "$TMP_EXTRACT"
jar xf "$WORK_JAR"
cd - > /dev/null

# Remove the sqlite native-image config
if [[ -d "$TMP_EXTRACT/META-INF/native-image/org.xerial/sqlite-jdbc" ]]; then
  echo "Removing sqlite-jdbc native-image configuration..."
  rm -rf "$TMP_EXTRACT/META-INF/native-image/org.xerial/sqlite-jdbc"
fi

# Remove multi-release JAR entries that cause issues
if [[ -d "$TMP_EXTRACT/META-INF/versions/9/org/sqlite/nativeimage" ]]; then
  echo "Removing multi-release nativeimage entries..."
  rm -rf "$TMP_EXTRACT/META-INF/versions/9/org/sqlite/nativeimage"
fi

# Rebuild the JAR
echo "Rebuilding JAR..."
cd "$TMP_EXTRACT"
jar cf "$WORK_JAR" .
cd - > /dev/null

# Verify the JAR
echo "Verifying JAR..."
jar tf "$WORK_JAR" | grep -i "nativeimage" || echo "No nativeimage entries found (good)"

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
