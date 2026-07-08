#!/usr/bin/env bash
#
# release.sh — Java Code Indexer 发布脚本
#
# 用法:
#   ./script/release.sh [OPTIONS]
#
# 选项:
#   --version, -v <ver>    指定版本号（默认从 pom.xml 读取）
#   --skip-native          跳过 GraalVM Native Image 构建
#   --push-docker          构建并推送 Docker 镜像到 registry
#   --output, -o <dir>     输出目录（默认 release/<version>/）
#   --skip-tests           跳过测试
#   --help, -h             显示帮助
#
# 环境变量:
#   VERSION               覆盖版本号（优先级高于 --version）
#   DOCKER_REGISTRY       Docker 镜像仓库前缀（默认：空，即 Docker Hub）
#   DOCKER_IMAGE_NAME     Docker 镜像名（默认：sodlinken/jcodeindexer）
#   DOCKER_TAG            Docker 标签（默认：latest）

set -euo pipefail

# ─── 颜色输出 ──────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

info()    { echo -e "${CYAN}[INFO]${NC}  $*" >&2; }
success() { echo -e "${GREEN}[OK]${NC}    $*" >&2; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*" >&2; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ─── 默认值 ──────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR=""  # 延迟到版本号确定后设置
BUILD_NATIVE=true
PUSH_DOCKER=false
SKIP_TESTS=false
VERSION="${VERSION:-}"

DOCKER_REGISTRY="${DOCKER_REGISTRY:-}"
DOCKER_IMAGE_NAME="${DOCKER_IMAGE_NAME:-sodlinken/jcodeindexer}"
DOCKER_TAG="${DOCKER_TAG:-latest}"

# ─── 帮助 ──────────────────────────────────────────────
usage() {
    cat <<'EOF'
Java Code Indexer 发布脚本

用法: ./script/release.sh [OPTIONS]

选项:
  --version, -v <ver>    指定版本号（默认从 pom.xml 读取）
  --skip-native          跳过 GraalVM Native Image 构建
  --push-docker          构建并推送 Docker 镜像到 registry
  --output, -o <dir>     输出目录（默认 release/<version>/）
  --skip-tests           跳过测试
  --help, -h             显示帮助

示例:
  # 基本发布（Fat JAR + Native Image）
  ./script/release.sh

  # 指定版本号，跳过 Native Image
  ./script/release.sh -v 1.0.0 --skip-native

  # 推送到 Docker Hub（默认 sodlinken/jcodeindexer）
  ./script/release.sh --push-docker

  # 推送到私有 registry
  DOCKER_REGISTRY=myregistry.com DOCKER_IMAGE_NAME=myapp ./script/release.sh --push-docker

  # CI 环境使用
  VERSION=1.0.0 ./script/release.sh --push-docker
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
        --skip-native)
            BUILD_NATIVE=false
            shift
            ;;
        --push-docker)
            PUSH_DOCKER=true
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

    if [[ "$PUSH_DOCKER" == true ]] && ! command -v docker &>/dev/null; then
        error "docker 未安装，无法推送 Docker 镜像"
        exit 1
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

    # 设置输出目录
    if [[ -z "$OUTPUT_DIR" ]]; then
        OUTPUT_DIR="$PROJECT_ROOT/release/$VERSION"
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

    # 查找构建产物：shaded jar 替换了原始 jar，原始 jar 以 original- 前缀保存
    local jar_file
    jar_file=$(find target -name "*.jar" -not -name "*.original*" -not -name "original-*" | head -1)

    if [[ -z "$jar_file" ]]; then
        error "Fat JAR 构建失败"
        exit 1
    fi

    success "Fat JAR: $jar_file"
    echo "$jar_file"
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

    # 修复 multi-release JAR 问题：sqlite-jdbc 把 GraalVM Feature 放在 META-INF/versions/9/
    # native-image 无法识别，需要将 Feature 类从 versions/9 提取到根路径
    local work_jar="$OUTPUT_DIR/jindexer-build.jar"
    cp "$jar_file" "$work_jar"

    local tmp_extract
    tmp_extract=$(mktemp -d)
    unzip -qo "$work_jar" "META-INF/versions/9/org/sqlite/*" -d "$tmp_extract" 2>/dev/null || true

    if [[ -d "$tmp_extract/META-INF/versions/9/org/sqlite/nativeimage" ]]; then
        info "修复 sqlite-jdbc multi-release JAR：提取 Feature 类到根路径"
        # 从 versions/9 下移除 sqlite 目录
        zip -qd "$work_jar" "META-INF/versions/9/org/sqlite/*" 2>/dev/null || true
        # 将 Feature 类放到 JAR 根路径（org/sqlite/nativeimage/）
        (cd "$tmp_extract/META-INF/versions/9" && zip -qr "$work_jar" org/sqlite/nativeimage/)
    fi
    rm -rf "$tmp_extract"

    native-image -jar "$work_jar" \
        -o "$OUTPUT_DIR/jindexer" \
        --no-fallback \
        -H:+ReportExceptionStackTraces \
        -H:+UnlockExperimentalVMOptions \
        -H:ConfigurationFileDirectories="$PROJECT_ROOT/src/main/resources/META-INF/native-image"

    rm -f "$work_jar"

    local suffix=""
    local os_type
    os_type=$(uname -s | tr '[:upper:]' '[:lower:]')
    [[ "$os_type" == "mingw"* || "$os_type" == "msys"* || "$os_type" == "cygwin"* ]] && suffix=".exe"

    success "Native Image: $OUTPUT_DIR/jindexer$suffix"
}

# ─── Docker 镜像 ──────────────────────────────────────
build_and_push_docker() {
    if [[ "$PUSH_DOCKER" != true ]]; then
        return
    fi

    info "构建并推送 Docker 镜像..."

    local image_base="${DOCKER_IMAGE_NAME}"
    [[ -n "$DOCKER_REGISTRY" ]] && image_base="${DOCKER_REGISTRY}/${image_base}"

    local full_tag="${image_base}:${DOCKER_TAG}"
    local latest_tag="${image_base}:latest"
    local version_tag="${image_base}:${VERSION}"

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
    jar_file=$(find "$OUTPUT_DIR" -name "*.jar" -not -name "Dockerfile.release" | head -1)
    if [[ -z "$jar_file" ]]; then
        error "未找到 JAR 文件，无法构建 Docker 镜像"
        return 1
    fi
    cp "$jar_file" "$OUTPUT_DIR/jindexer.jar"

    # 构建镜像
    docker build -t "$full_tag" -t "$latest_tag" -t "$version_tag" \
        -f "$tmp_dockerfile" "$OUTPUT_DIR"

    # 清理临时文件
    rm -f "$tmp_dockerfile" "$OUTPUT_DIR/jindexer.jar"

    # 推送镜像
    info "推送 $full_tag ..."
    docker push "$full_tag"

    if [[ "$DOCKER_TAG" != "latest" ]]; then
        info "推送 $latest_tag ..."
        docker push "$latest_tag"
    fi

    info "推送 $version_tag ..."
    docker push "$version_tag"

    success "Docker 镜像已推送: $full_tag, $latest_tag, $version_tag"
}

# ─── SHA-256 校验 ──────────────────────────────────────
generate_checksums() {
    info "生成 SHA-256 校验和..."

    cd "$OUTPUT_DIR"
    local checksum_file="checksums.sha256"

    : > "$checksum_file"

    for f in *.jar jindexer*; do
        [[ -f "$f" ]] || continue
        [[ "$f" == "checksums.sha256" ]] && continue
        sha256sum "$f" >> "$checksum_file"
    done

    if [[ -s "$checksum_file" ]]; then
        success "校验和: $OUTPUT_DIR/$checksum_file"
        cat "$checksum_file" | sed 's/^/  /'
    fi
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
    ls -lh "$OUTPUT_DIR" | grep -v "^total" | sed 's/^/  /'
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

    # 如果输出目录是默认值（版本号确定后），使用它
    if [[ -z "$OUTPUT_DIR" ]]; then
        OUTPUT_DIR="$PROJECT_ROOT/release/$VERSION"
    fi

    mkdir -p "$OUTPUT_DIR"

    echo ""
    info "版本: $VERSION"
    info "输出目录: $OUTPUT_DIR"
    info "Native Image: $BUILD_NATIVE"
    info "Docker Push: $PUSH_DOCKER"
    if [[ "$PUSH_DOCKER" == true ]]; then
        local image_info="$DOCKER_IMAGE_NAME"
        [[ -n "$DOCKER_REGISTRY" ]] && image_info="$DOCKER_REGISTRY/$image_info"
        info "Docker Image: $image_info"
    fi
    echo ""

    # 构建 Fat JAR
    local jar_file
    jar_file=$(build_fat_jar)

    # 复制 JAR 到输出目录
    local jar_name
    jar_name=$(basename "$jar_file")
    cp "$jar_file" "$OUTPUT_DIR/$jar_name"
    success "JAR 已复制到 $OUTPUT_DIR/$jar_name"

    echo ""

    # 构建 Native Image
    build_native_image "$jar_file"

    echo ""

    # 构建并推送 Docker 镜像
    build_and_push_docker

    echo ""

    # 生成校验和
    generate_checksums

    # 汇总
    print_summary
}

main "$@"
