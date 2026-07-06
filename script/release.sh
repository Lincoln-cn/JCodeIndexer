#!/usr/bin/env bash
#
# release.sh — Java Code Indexer 发布脚本
#
# 用法:
#   ./script/release.sh [OPTIONS]
#
# 选项:
#   --version, -v <ver>    指定版本号（默认从 pom.xml 读取）
#   --native               构建 GraalVM Native Image（需 GraalVM JDK 21）
#   --docker               构建 Docker 镜像
#   --output, -o <dir>     输出目录（默认 dist/）
#   --skip-tests           跳过测试
#   --help, -h             显示帮助
#
# 环境变量:
#   VERSION               覆盖版本号（优先级高于 --version）
#   DOCKER_REGISTRY       Docker 镜像仓库前缀（默认：ghcr.io/sodlinken）
#   DOCKER_IMAGE_NAME     Docker 镜像名（默认：java-code-indexer）

set -euo pipefail

# ─── 颜色输出 ──────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ─── 默认值 ──────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/dist"
BUILD_NATIVE=false
BUILD_DOCKER=false
SKIP_TESTS=false
VERSION="${VERSION:-}"

DOCKER_REGISTRY="${DOCKER_REGISTRY:-ghcr.io/sodlinken}"
DOCKER_IMAGE_NAME="${DOCKER_IMAGE_NAME:-java-code-indexer}"

# ─── 帮助 ──────────────────────────────────────────────
usage() {
    cat <<'EOF'
Java Code Indexer 发布脚本

用法: ./script/release.sh [OPTIONS]

选项:
  --version, -v <ver>    指定版本号（默认从 pom.xml 读取）
  --native               构建 GraalVM Native Image
  --docker               构建 Docker 镜像
  --output, -o <dir>     输出目录（默认 dist/）
  --skip-tests           跳过测试
  --help, -h             显示帮助

示例:
  # 基本发布（仅 Fat JAR）
  ./script/release.sh

  # 指定版本号
  ./script/release.sh -v 0.1.0

  # 包含 Native Image
  ./script/release.sh --native

  # 完整发布（JAR + Native + Docker）
  ./script/release.sh --native --docker

  # CI 环境使用
  VERSION=0.1.0 ./script/release.sh --native --docker
EOF
    exit 0
}

# ─── 参数解析 ──────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        -v|--version)
            VERSION="$2"
            shift 2
            ;;
        --native)
            BUILD_NATIVE=true
            shift
            ;;
        --docker)
            BUILD_DOCKER=true
            shift
            ;;
        -o|--output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        -h|--help)
            usage
            ;;
        *)
            error "未知参数: $1"
            usage
            ;;
    esac
done

# ─── 依赖检查 ──────────────────────────────────────────────
check_deps() {
    local missing=()

    if ! command -v java &>/dev/null; then
        missing+=("java")
    fi

    if ! command -v mvn &>/dev/null; then
        missing+=("mvn")
    fi

    if [[ "$BUILD_NATIVE" == true ]] && ! command -v native-image &>/dev/null; then
        warn "native-image 未安装，跳过 Native Image 构建"
        BUILD_NATIVE=false
    fi

    if [[ "$BUILD_DOCKER" == true ]] && ! command -v docker &>/dev/null; then
        warn "docker 未安装，跳过 Docker 镜像构建"
        BUILD_DOCKER=false
    fi

    if [[ ${#missing[@]} -gt 0 ]]; then
        error "缺少必要依赖: ${missing[*]}"
        exit 1
    fi
}

# ─── 版本号 ──────────────────────────────────────────────
resolve_version() {
    if [[ -n "$VERSION" ]]; then
        info "使用指定版本: $VERSION"
        return
    fi

    # 从 pom.xml 提取版本号
    if [[ -f "$PROJECT_ROOT/pom.xml" ]]; then
        VERSION=$(grep -oP '<version>\K[^<]+' "$PROJECT_ROOT/pom.xml" | head -1)
        info "从 pom.xml 读取版本: $VERSION"
    fi

    # 如果是 SNAPSHOT，附加 git commit hash
    if [[ "$VERSION" == *"-SNAPSHOT" ]]; then
        local git_hash
        git_hash=$(git -C "$PROJECT_ROOT" rev-parse --short HEAD 2>/dev/null || echo "unknown")
        VERSION="${VERSION%-SNAPSHOT}-${git_hash}"
        info "SNAPSHOT 版本，附加 commit hash: $VERSION"
    fi
}

# ─── 构建 ──────────────────────────────────────────────
build_fat_jar() {
    info "构建 Fat JAR..."
    cd "$PROJECT_ROOT"

    local mvn_args=("package" "-q")
    if [[ "$SKIP_TESTS" == true ]]; then
        mvn_args+=("-DskipTests")
    fi

    mvn "${mvn_args[@]}"

    # 查找构建产物
    local jar_file
    jar_file=$(find target -name "*-shaded.jar" -not -name "*.original" | head -1)

    if [[ -z "$jar_file" ]]; then
        error "Fat JAR 构建失败"
        exit 1
    fi

    success "Fat JAR: $jar_file"
    echo "$jar_file"
}

# ─── 创建归档 ──────────────────────────────────────────────
create_archive() {
    local jar_file="$1"
    local jar_name
    jar_name=$(basename "$jar_file")

    mkdir -p "$OUTPUT_DIR"

    local base_name="java-code-indexer-${VERSION}"

    # --- Fat JAR (通用) ---
    local jar_dest="$OUTPUT_DIR/${base_name}.jar"
    cp "$jar_file" "$jar_dest"
    info "Fat JAR: $jar_dest"

    # --- Linux 归档 (tar.gz) ---
    local linux_dir="$OUTPUT_DIR/${base_name}-linux-amd64"
    mkdir -p "$linux_dir"
    cp "$jar_file" "$linux_dir/jindexer.jar"
    cp "$PROJECT_ROOT/LICENSE" "$linux_dir/" 2>/dev/null || true
    cat > "$linux_dir/README.md" <<README_EOF
# Java Code Indexer ${VERSION}

## 使用方法

\`\`\`bash
java -jar jindexer.jar --project-root /path/to/project
java -jar jindexer.jar --project-root /path/to/project --index
\`\`\`

详见: https://github.com/sodlinken/java-code-indexer
README_EOF

    cd "$OUTPUT_DIR"
    tar -czf "${base_name}-linux-amd64.tar.gz" -C "$OUTPUT_DIR" "${base_name}-linux-amd64"
    rm -rf "$linux_dir"
    success "Linux 归档: $OUTPUT_DIR/${base_name}-linux-amd64.tar.gz"

    # --- macOS 归档 (tar.gz) ---
    local macos_dir="$OUTPUT_DIR/${base_name}-darwin-amd64"
    mkdir -p "$macos_dir"
    cp "$jar_file" "$macos_dir/jindexer.jar"
    cp "$PROJECT_ROOT/LICENSE" "$macos_dir/" 2>/dev/null || true
    cp "$linux_dir/README.md" "$macos_dir/README.md" 2>/dev/null || true
    # 复制 README（如果还在）
    if [[ ! -f "$macos_dir/README.md" ]]; then
        echo "# Java Code Indexer ${VERSION}" > "$macos_dir/README.md"
    fi

    tar -czf "${base_name}-darwin-amd64.tar.gz" -C "$OUTPUT_DIR" "${base_name}-darwin-amd64"
    rm -rf "$macos_dir"
    success "macOS 归档: $OUTPUT_DIR/${base_name}-darwin-amd64.tar.gz"

    # --- Windows 归档 (zip) ---
    local windows_dir="$OUTPUT_DIR/${base_name}-windows-amd64"
    mkdir -p "$windows_dir"
    cp "$jar_file" "$windows_dir/jindexer.jar"
    cp "$PROJECT_ROOT/LICENSE" "$windows_dir/" 2>/dev/null || true
    echo "# Java Code Indexer ${VERSION}" > "$windows_dir/README.md"

    if command -v zip &>/dev/null; then
        cd "$OUTPUT_DIR"
        zip -qr "${base_name}-windows-amd64.zip" "${base_name}-windows-amd64"
        rm -rf "$windows_dir"
        success "Windows 归档: $OUTPUT_DIR/${base_name}-windows-amd64.zip"
    else
        warn "zip 未安装，跳过 Windows 归档"
        rm -rf "$windows_dir"
    fi
}

# ─── Native Image ──────────────────────────────────────
build_native_image() {
    local jar_file="$1"

    if [[ "$BUILD_NATIVE" != true ]]; then
        return
    fi

    if ! command -v native-image &>/dev/null; then
        warn "native-image 未安装，跳过"
        return
    fi

    info "构建 Native Image..."

    local os_type arch_type
    os_type=$(uname -s | tr '[:upper:]' '[:lower:]')
    arch_type=$(uname -m)

    case "$arch_type" in
        x86_64)  arch_type="amd64" ;;
        aarch64) arch_type="arm64" ;;
    esac

    local binary_name="jindexer"
    native-image -jar "$jar_file" \
        -o "$OUTPUT_DIR/$binary_name" \
        --no-fallback \
        -H:+ReportExceptionStackTraces \
        -H:+UnlockExperimentalVMOptions \
        -H:ConfigurationFileDirectories="$PROJECT_ROOT/src/main/resources/META-INF/native-image"

    local suffix=""
    [[ "$os_type" == "mingw"* || "$os_type" == "msys"* || "$os_type" == "cygwin"* ]] && suffix=".exe"

    # 创建归档
    local base_name="java-code-indexer-${VERSION}"
    local native_archive="${OUTPUT_DIR}/${base_name}-${os_type}-${arch_type}-native"

    if [[ "$os_type" == "linux" ]]; then
        mkdir -p "$OUTPUT_DIR/${base_name}-linux-${arch_type}"
        mv "$OUTPUT_DIR/$binary_name$suffix" "$OUTPUT_DIR/${base_name}-linux-${arch_type}/"
        tar -czf "${native_archive}.tar.gz" -C "$OUTPUT_DIR" "${base_name}-linux-${arch_type}"
        rm -rf "$OUTPUT_DIR/${base_name}-linux-${arch_type}"
        success "Native Image: ${native_archive}.tar.gz"
    else
        mkdir -p "$OUTPUT_DIR/${base_name}-${os_type}-${arch_type}"
        mv "$OUTPUT_DIR/$binary_name$suffix" "$OUTPUT_DIR/${base_name}-${os_type}-${arch_type}/"
        tar -czf "${native_archive}.tar.gz" -C "$OUTPUT_DIR" "${base_name}-${os_type}-${arch_type}"
        rm -rf "$OUTPUT_DIR/${base_name}-${os_type}-${arch_type}"
        success "Native Image: ${native_archive}.tar.gz"
    fi
}

# ─── Docker 镜像 ──────────────────────────────────────
build_docker_image() {
    if [[ "$BUILD_DOCKER" != true ]]; then
        return
    fi

    info "构建 Docker 镜像..."

    local image_tag="${DOCKER_REGISTRY}/${DOCKER_IMAGE_NAME}:${VERSION}"
    local latest_tag="${DOCKER_REGISTRY}/${DOCKER_IMAGE_NAME}:latest"

    # 生成临时 Dockerfile
    local tmp_dockerfile="$OUTPUT_DIR/Dockerfile.release"
    cat > "$tmp_dockerfile" <<'DOCKERFILE_EOF'
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="Java Code Indexer"
LABEL org.opencontainers.image.description="MCP-based Java code indexing server for AI coding assistants"
LABEL org.opencontainers.image.source="https://github.com/sodlinken/java-code-indexer"

RUN addgroup -S jindexer && adduser -S jindexer -G jindexer

COPY jindexer.jar /app/jindexer.jar

USER jindexer

ENTRYPOINT ["java", "-jar", "/app/jindexer.jar"]
DOCKERFILE_EOF

    # 复制 JAR 到构建上下文
    local jar_file
    jar_file=$(find target -name "*-shaded.jar" -not -name "*.original" | head -1)
    cp "$jar_file" "$OUTPUT_DIR/jindexer.jar"

    docker build -t "$image_tag" -t "$latest_tag" \
        -f "$tmp_dockerfile" "$OUTPUT_DIR"

    # 清理临时文件
    rm -f "$tmp_dockerfile" "$OUTPUT_DIR/jindexer.jar"

    # 导出镜像为 tar
    local docker_archive="$OUTPUT_DIR/${DOCKER_IMAGE_NAME}-${VERSION}-docker.tar.gz"
    docker save "$image_tag" | gzip > "$docker_archive"
    success "Docker 镜像: $docker_archive"
    info "Docker 标签: $image_tag, $latest_tag"
}

# ─── SHA-256 校验 ──────────────────────────────────────
generate_checksums() {
    info "生成 SHA-256 校验和..."

    cd "$OUTPUT_DIR"
    local checksum_file="checksums-${VERSION}.sha256"

    : > "$checksum_file"

    for f in *.tar.gz *.zip *.jar; do
        [[ -f "$f" ]] || continue
        sha256sum "$f" >> "$checksum_file"
    done

    success "校验和: $OUTPUT_DIR/$checksum_file"
    cat "$checksum_file" | sed 's/^/  /'
}

# ─── 汇总 ──────────────────────────────────────────────
print_summary() {
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo -e "  ${GREEN}发布构建完成${NC}  版本: ${CYAN}${VERSION}${NC}"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    echo "  产物目录: $OUTPUT_DIR"
    echo ""
    ls -lh "$OUTPUT_DIR" | grep -v "^total" | grep -v "Dockerfile" | sed 's/^/  /'
    echo ""
    echo "═══════════════════════════════════════════════════════════"
}

# ─── 主流程 ──────────────────────────────────────────────
main() {
    echo ""
    info "Java Code Indexer 发布脚本"
    echo ""

    check_deps
    resolve_version

    echo ""
    info "版本: $VERSION"
    info "输出目录: $OUTPUT_DIR"
    info "Native Image: $BUILD_NATIVE"
    info "Docker 镜像: $BUILD_DOCKER"
    echo ""

    # 构建 Fat JAR
    local jar_file
    jar_file=$(build_fat_jar)

    echo ""

    # 创建归档
    create_archive "$jar_file"

    echo ""

    # 构建 Native Image
    build_native_image "$jar_file"

    echo ""

    # 构建 Docker 镜像
    build_docker_image

    echo ""

    # 生成校验和
    generate_checksums

    # 汇总
    print_summary
}

main "$@"
