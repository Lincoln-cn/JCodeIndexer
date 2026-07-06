# Java Code Indexer

> MCP-based Java code indexing server for AI coding assistants.

[中文](README.zh-CN.md) | English

## Overview

Java Code Indexer analyzes Java source code at the AST level, stores symbols, call graphs, dependencies, and configuration entries in an embedded SQLite database, and exposes them as 9 MCP tools via JSON-RPC over stdio. AI coding assistants (Qwen Code, Claude, Cursor, etc.) can use these tools to navigate and understand your Java project.

**Key features:**

- **9 MCP tools** — symbol lookup, reference search, call graph, code search, config search, dependency search, and more
- **Incremental indexing** — SHA-1 change detection, only re-parses modified files
- **Multi-project support** — independent SQLite database per project
- **Java 21 virtual threads** — parallel file parsing for fast indexing
- **Multiple parsers** — Java (JavaParser), Maven POM, Gradle, YAML/Properties/.env

## Quick Start

### Build from Source

Requires **Java 21** and **Maven 3.8+**.

```bash
git clone https://github.com/sodlinken/java-code-indexer.git
cd java-code-indexer
mvn package -q -DskipTests
```

The fat JAR is built to `target/java-code-indexer-0.1.0-SNAPSHOT-shaded.jar`.

### Index a Project

```bash
java -jar target/java-code-indexer-*-shaded.jar \
  --project-root /path/to/your/java-project \
  --index
```

### Start MCP Server

```bash
java -jar target/java-code-indexer-*-shaded.jar \
  --project-root /path/to/your/java-project
```

## MCP Configuration

Add the server to your AI coding assistant's MCP config.

### Qwen Code

```json
{
  "mcpServers": {
    "java-code-indexer": {
      "command": "java",
      "args": ["-jar", "/path/to/java-code-indexer-*-shaded.jar", "--project-root", "/path/to/project"]
    }
  }
}
```

### Claude Desktop / Cursor

```json
{
  "mcpServers": {
    "java-code-indexer": {
      "command": "java",
      "args": ["-jar", "/path/to/java-code-indexer-*-shaded.jar", "--project-root", "/path/to/project"]
    }
  }
}
```

## CLI Usage

```
java -jar jindexer.jar [options]

Options:
  --project-root <path>   Java project root directory (default: current dir)
  --data-dir <path>       Index data directory
  --init                  Initialize database schema only
  --index                 Run indexer (extract symbols/references/calls)
  --help, -h              Show help

No flags → start MCP server.
```

## Configuration

Create `.jindexer/config.yaml` in your project root:

```yaml
# Single project
project_root: /path/to/project
data_dir: .jindexer
threads: 4
embedding:
  enabled: false
```

Or configure multiple projects:

```yaml
# Multi-project
data_dir: .jindexer
projects:
  - name: my-app
    root: /path/to/my-app
  - name: my-lib
    root: /path/to/my-lib
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

## MCP Tools

| Tool | Description |
|------|-------------|
| `find_symbol` | Find symbols by name (class/method/field), supports `*` wildcard |
| `find_references` | Find all references to a symbol |
| `get_call_graph` | Get method call graph (callers/callees/both) |
| `search_code` | Search symbol names and code content |
| `get_file_info` | Get file details (symbols, chunks, calls) |
| `search_config` | Search configuration files (YAML/Properties/ENV) |
| `find_dependencies` | Search project dependencies (Maven/Gradle) |
| `semantic_search` | Semantic search (requires Embedding config, not yet active) |
| `list_projects` | List indexed projects (multi-project mode only) |

## Project Structure

```
src/main/java/com/sodlinken/jindexer/
├── cli/          # CLI entry point (CliMain)
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

## Tech Stack

- **Java 21** — virtual threads, pattern matching, switch expressions
- **JavaParser 3.26** — AST parsing for Java source code
- **SQLite** — embedded database with WAL mode
- **MCP Protocol** — JSON-RPC over stdio (2024-11-05)
- **Gson** — JSON serialization
- **OkHttp** — HTTP client for Embedding API
- **SnakeYAML** — YAML config parsing
- **SLF4J + Logback** — logging (output to stderr)

## Documentation

Full documentation is available at the project's MkDocs site (if deployed), or browse `doc/` directly:

- [Getting Started](doc/getting-started/index.md)
- [Architecture Design](doc/design/index.md)
- [API Reference](doc/api/index.md)

## Distribution

### Fat JAR (default)

```bash
mvn package -q -DskipTests
# output: target/java-code-indexer-0.1.0-SNAPSHOT-shaded.jar
```

### Native Image (GraalVM)

Requires GraalVM JDK 21 with `native-image` installed.

```bash
mvn package -q -DskipTests -Pnative
# or build manually:
native-image -jar target/java-code-indexer-*-shaded.jar \
  -o jindexer --no-fallback -H:+ReportExceptionStackTraces
```

Produces a single native binary (~30-50 MB) with instant startup, no JVM required at runtime.

### Docker Image

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/java-code-indexer-*-shaded.jar /app/jindexer.jar
ENTRYPOINT ["java", "-jar", "/app/jindexer.jar"]
```

```bash
docker build -t java-code-indexer .
docker run --rm -v /path/to/project:/project java-code-indexer --project-root /project --index
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE)
