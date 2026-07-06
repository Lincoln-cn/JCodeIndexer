# Java Code Indexer

A Java AST-level code indexer with SQLite storage, exposed as an [MCP](https://modelcontextprotocol.io/) server for AI coding assistants.

## Why

AI coding assistants exploring an unfamiliar Java project today face significant challenges. Without an indexer, tasks like finding where `UserService` is defined require grepping across 100+ files (~15k tokens), understanding all callers of `saveOrder()` requires reading files one by one (~8k tokens), and locating configuration values involves multiple find and cat commands (~5k tokens). With Java Code Indexer, these tasks become efficient: `find_symbol("UserService")` uses only ~200 tokens, `get_call_graph("saveOrder", "callers")` uses ~500 tokens, and `search_config("spring.datasource.url")` uses ~200 tokens.

## Features

- **Symbol Extraction**: Parse Java source files to extract classes, methods, fields, and their locations
- **Reference Tracking**: Find all reference locations for any symbol
- **Call Graph Analysis**: Get method callers, callees, or both directions
- **Code Search**: Search symbol names and code content
- **File Information**: Get file details including symbols, code chunks, and call relationships
- **Config Search**: Search YAML, Properties, and .env configuration files
- **Dependency Discovery**: Search Maven/Gradle dependencies
- **Multi-Project Support**: Index and query multiple projects

## Installation

### Option 1: Download JAR (Recommended)

```bash
curl -LO https://github.com/sodlinken/java-code-indexer/releases/latest/download/java-code-indexer-latest.jar
```

### Option 2: Build from Source

```bash
git clone https://github.com/sodlinken/java-code-indexer.git
cd java-code-indexer
mvn package -q -DskipTests
```

### Option 3: Native Image (GraalVM)

```bash
native-image -jar target/java-code-indexer-*-shaded.jar -o jindexer --no-fallback
```

### Option 4: Docker

```bash
docker pull ghcr.io/sodlinken/java-code-indexer:latest
```

## Quick Start

```bash
# 1. Index your Java project
java -jar jindexer.jar --project-root /path/to/your/java-project --index

# 2. Start the MCP server (stdio mode)
java -jar jindexer.jar --project-root /path/to/your/java-project
```

## CLI Reference

```
java -jar jindexer.jar [options]

Options:
  --project-root <path>   Java project root directory (default: current dir)
  --data-dir <path>       Index data directory (default: .jindexer)
  --init                  Initialize database schema only
  --index                 Run indexer (extract symbols/references/calls)
  --help, -h              Show help
```

## MCP Tools

| Tool | Description |
|------|-------------|
| `find_symbol` | Find symbols (class/method/field) by name, `*` wildcard supported |
| `find_references` | Find all reference locations for a symbol |
| `get_call_graph` | Method call graph — callers, callees, or both |
| `search_code` | Search symbol names and code content |
| `get_file_info` | File details: symbols, code chunks, call relationships |
| `search_config` | Search config files (YAML / Properties / .env) |
| `find_dependencies` | Search project dependencies (Maven / Gradle) |
| `list_projects` | List indexed projects (multi-project mode only) |

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
| `INDEXER_THREADS` | Indexing thread count | `4` |
| `INDEXER_LOG_LEVEL` | Log level | `INFO` |
| `INDEXER_EMBEDDING_ENABLED` | Enable embedding API | `false` |

Priority: CLI flags → environment variables → config file → defaults.

## AI Assistant Integration

### Claude Desktop / Qwen Code / Cursor

Add to your MCP configuration:

```json
{
  "mcpServers": {
    "java-code-indexer": {
      "command": "java",
      "args": ["-jar", "/path/to/jindexer.jar", "--project-root", "/path/to/project"]
    }
  }
}
```

## How It Works

Java Code Indexer walks Java source files using filesystem traversal, uses SHA-1 content hash to skip unchanged files for incremental indexing, extracts symbols, references, and call relationships via JavaParser AST, parses POM/Gradle files for dependency information, extracts YAML/Properties/.env entries for config data, and stores everything in embedded SQLite with WAL mode.

### Database Schema

Five core tables store the indexed data: **symbols** for classes, methods, fields with location and signatures; **references** for symbol usage locations across the codebase; **calls** for method call relationships; **chunks** for code slices at class/method granularity; and **file_hashes** for SHA-1 hashes enabling incremental indexing.

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

## Development

```bash
# Clone and build
git clone https://github.com/sodlinken/java-code-indexer.git
cd java-code-indexer
mvn package -q -DskipTests

# Index this project itself
java -jar target/java-code-indexer-*-shaded.jar --project-root . --index

# Start MCP server
java -jar target/java-code-indexer-*-shaded.jar --project-root .
```

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

[Apache License 2.0](LICENSE)