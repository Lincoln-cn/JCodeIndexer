# Java Code Indexer

> Java AST-level code indexer with SQLite storage, exposed as an [MCP](https://modelcontextprotocol.io/) server for AI coding assistants.

[中文](README.zh-CN.md) | English

---

## Why

AI coding assistants exploring an unfamiliar Java project today:

| Task | Without Java Code Indexer | With Java Code Indexer |
|------|--------------------------|----------------------|
| Find where `UserService` is defined | `grep` across 100+ files (~15k tokens) | `find_symbol("UserService")` (~200 tokens) |
| Understand all callers of `saveOrder()` | Read files one by one (~8k tokens) | `get_call_graph("saveOrder", "callers")` (~500 tokens) |
| Find a config value in YAML/properties | `find` + `cat` multiple config files (~5k tokens) | `search_config("spring.datasource.url")` (~200 tokens) |
| Locate all dependencies | Open pom.xml + transitive (~3k tokens) | `find_dependencies("spring-boot-starter")` (~300 tokens) |

---

## Installation

Requires **Java 21+** (JRE or JDK). Docker and Native Image options bundle the runtime.

**Option 1: Download JAR (recommended)**

```bash
# Download from GitHub Releases
curl -LO https://github.com/sodlinken/java-code-indexer/releases/latest/download/java-code-indexer-latest.jar
```

**Option 2: Build from source** (requires Java 21+ and Maven 3.8+)

```bash
git clone https://github.com/sodlinken/java-code-indexer.git
cd java-code-indexer
mvn package -q -DskipTests
# output: target/java-code-indexer-*-shaded.jar
```

**Option 3: Native Image (GraalVM)**

```bash
native-image -jar target/java-code-indexer-*-shaded.jar \
  -o jindexer --no-fallback
```

**Option 4: Docker**

```bash
docker pull ghcr.io/sodlinken/java-code-indexer:latest
```

---

## Quickstart

```bash
# 1. Index your Java project
java -jar jindexer.jar --project-root /path/to/your/java-project --index

# 2. Start the MCP server (stdio, for Claude Code / Qwen Code / Cursor)
java -jar jindexer.jar --project-root /path/to/your/java-project
```

That's it. Your AI assistant can now query your codebase structure instead of reading files.

---

## CLI Reference

```
java -jar jindexer.jar [options]

Options:
  --project-root <path>   Java project root directory (default: current dir)
  --data-dir <path>       Index data directory (default: .jindexer)
  --init                  Initialize database schema only
  --index                 Run indexer (extract symbols/references/calls)
  --help, -h              Show help

No flags → start MCP server over stdio.
```

---

## MCP Tools

When running as an MCP server, Java Code Indexer exposes these tools:

| Tool | Description |
|------|-------------|
| `find_symbol` | Find symbols (class/method/field) by name, `*` wildcard supported |
| `find_references` | Find all reference locations for a symbol |
| `get_call_graph` | Method call graph — callers, callees, or both |
| `search_code` | Search symbol names and code content |
| `get_file_info` | File details: symbols, code chunks, call relationships |
| `search_config` | Search config files (YAML / Properties / .env) |
| `find_dependencies` | Search project dependencies (Maven / Gradle) |
| `semantic_search` | Semantic search (requires Embedding config, not yet active) |
| `list_projects` | List indexed projects (multi-project mode only) |

---

## Configuration

Create `.jindexer/config.yaml` in your project root (optional):

```yaml
project_root: /path/to/project
data_dir: .jindexer
threads: 4
embedding:
  enabled: false
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `INDEXER_EMBEDDING_ENABLED` | Enable embedding API | `false` |
| `INDEXER_EMBEDDING_BASE_URL` | Embedding API endpoint | — |
| `INDEXER_EMBEDDING_MODEL` | Embedding model name | — |
| `INDEXER_THREADS` | Indexing thread count | `4` |
| `INDEXER_LOG_LEVEL` | Log level | `INFO` |

Priority: CLI flags → environment variables → config file → defaults.

---

## AI Assistant Integration

### Qwen Code / Claude Desktop / Cursor

Add to your MCP configuration:

```json
{
  "mcpServers": {
    "java-code-indexer": {
      "command": "/path/to/jdk-21/bin/java",
      "args": ["-jar", "/path/to/jindexer.jar", "--project-root", "/path/to/project"]
    }
  }
}
```

> **Note:** `command` must point to a Java 21+ runtime. If `JAVA_HOME` is set to JDK 21, you can use `"${JAVA_HOME}/bin/java"` instead.

---

## How It Works

```
┌──────────────┐     MCP (stdio)     ┌──────────────────────┐
│  AI Assistant │ ◄─────────────────► │  Java Code Indexer   │
│  (Qwen/Claude)│    JSON-RPC         │                      │
└──────────────┘                     │  ┌────────────────┐  │
                                     │  │ JavaParser AST │  │
                                     │  └───────┬────────┘  │
                                     │          ▼            │
                                     │  ┌────────────────┐  │
                                     │  │  SQLite (WAL)  │  │
                                     │  │  symbols       │  │
                                     │  │  references    │  │
                                     │  │  call_graphs   │  │
                                     │  │  configs       │  │
                                     │  │  dependencies  │  │
                                     │  └────────────────┘  │
                                     └──────────────────────┘
```

### Indexing Strategy

1. Walk Java source files via filesystem traversal
2. SHA-1 content hash skips unchanged files (incremental)
3. JavaParser AST extracts symbols, references, and call relationships
4. POM/Gradle parsers extract dependency information
5. Config parsers extract YAML/Properties/.env entries
6. All data upserted into embedded SQLite (WAL mode)

### Database Schema

Five core tables:

- **`symbols`** — classes, methods, fields with location and signatures
- **`references`** — symbol usage locations across the codebase
- **`calls`** — method call relationships (caller → callee)
- **`chunks`** — code slices at class/method granularity
- **`file_hashes`** — SHA-1 hashes for incremental indexing

---

## Architecture

```
src/main/java/com/sodlinken/jindexer/
├── cli/          # CLI entry point
├── mcp/          # MCP server (JSON-RPC over stdio)
├── config/       # YAML config loader
├── storage/      # SQLite schema & StorageService
├── indexer/      # Incremental indexing engine
├── parser/       # Java, POM, Gradle, Config parsers
├── chunker/      # Code chunking (class/method slices)
├── search/       # Structured search provider
├── model/        # Data models (Symbol, Call, Chunk, etc.)
└── util/         # SHA-1 hashing utility
```

---

## Distribution

产物输出到 `release/<version>/` 目录。

### 一键构建

```bash
# 构建 Fat JAR + Native Image
./script/release.sh

# 指定版本号
./script/release.sh -v 1.0.0

# 跳过 Native Image
./script/release.sh -v 1.0.0 --skip-native
```

### Fat JAR

构建产物位于 `release/<version>/java-code-indexer-<version>.jar`，需 Java 21+。

```bash
java -jar release/1.0.0/java-code-indexer-1.0.0.jar --project-root /path/to/project
```

### Native Image (GraalVM)

构建产物位于 `release/<version>/jindexer`，无需 JVM，启动即时。

```bash
./release/1.0.0/jindexer --project-root /path/to/project --index
```

### Docker

```bash
# 本地构建并运行
docker build -t java-code-indexer .
docker run --rm -v /path/to/project:/project java-code-indexer --project-root /project --index

# 推送到私有 registry
DOCKER_REGISTRY=your-registry.com ./script/release.sh --push-docker
```

---

## Development

```bash
# Clone and build
git clone https://github.com/sodlinken/java-code-indexer.git
cd java-code-indexer
mvn package -q -DskipTests

# Index this project itself
java -jar target/java-code-indexer-*-shaded.jar \
  --project-root . --index

# Start MCP server
java -jar target/java-code-indexer-*-shaded.jar --project-root .
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## What It Is NOT

- Not a code execution sandbox
- Not a test runner or linter
- Not a replacement for LSP/IDE features
- Not AI-generated summaries (symbol extraction is deterministic)

## License

[Apache License 2.0](LICENSE)
