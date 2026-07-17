# Java Code Indexer

> JVM AST-level code indexer (Java/Kotlin/Scala) with SQLite storage, exposed as an [MCP](https://modelcontextprotocol.io/) server for AI coding assistants.

[中文](README.zh-CN.md) | English

---

## Why

AI coding assistants exploring unfamiliar JVM projects today:

| Task | Without Java Code Indexer | With Java Code Indexer |
|------|--------------------------|----------------------|
| Find where `UserService` is defined | `grep` across 100+ files (~15k tokens) | `find_symbol("UserService")` (~200 tokens) |
| Understand all callers of `saveOrder()` | Read files one by one (~8k tokens) | `get_call_graph("saveOrder", "callers")` (~500 tokens) |
| Find a config value in YAML/properties | `find` + `cat` multiple config files (~5k tokens) | `search_config("spring.datasource.url")` (~200 tokens) |
| Locate all dependencies | Open pom.xml + transitive (~3k tokens) | `find_dependencies("spring-boot-starter")` (~300 tokens) |
| Find all `@RestController` classes | Search through entire codebase (~8k tokens) | `find_by_annotation("RestController")` (~200 tokens) |
| Understand interface implementations | Read multiple files (~5k tokens) | `find_implementations("UserService")` (~300 tokens) |
| Find which controller handles `/api/users/{id}` | Search through all controllers (~5k tokens) | `find_route("GET", "/api/users/123")` (~200 tokens) |
| Understand Bean injection relationships | Read multiple `@Autowired` fields (~3k tokens) | `get_bean_dependencies("OrderService")` (~300 tokens) |
| Find related test classes | Search test directories (~2k tokens) | `find_related_tests("UserService")` (~200 tokens) |

---

## Features

### Multi-Language Support

| Language | Status | Parser |
|----------|--------|--------|
| Java | ✅ Full support | JavaParser AST |
| Kotlin | ✅ Full support | KotlinParserAdapter (regex) |
| Scala | ✅ Full support | ScalaParserAdapter (regex) |

### Annotation Recognition

Supports 30+ annotations across major frameworks:

| Framework | Annotations |
|-----------|-------------|
| Spring Boot | `@RestController`, `@Service`, `@Repository`, `@Component` |
| Spring MVC | `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@DeleteMapping` |
| JPA | `@Entity`, `@Table`, `@Column`, `@Id`, `@GeneratedValue` |
| Lombok | `@Data`, `@Builder`, `@Getter`, `@Setter`, `@NoArgsConstructor` |
| Validation | `@NotNull`, `@Size`, `@Min`, `@Max`, `@Email` |
| MyBatis | `@Mapper`, `@Select`, `@Insert`, `@Update`, `@Delete` |
| Swagger | `@Api`, `@ApiOperation`, `@ApiParam` |
| Security | `@EnableWebSecurity`, `@PreAuthorize`, `@Secured` |
| Cache | `@EnableCaching`, `@Cacheable`, `@CacheEvict`, `@CachePut` |
| Async | `@EnableAsync`, `@Async` |
| Scheduling | `@EnableScheduling`, `@Scheduled` |

### Spring Ecosystem Support

| Feature | Description |
|---------|-------------|
| API Route Mapping | Automatically extract `@RequestMapping`/`@GetMapping` etc., build URL → Controller method mapping |
| Type Hierarchy | Query complete class inheritance chains (parent/child relationships) |
| Bean Dependencies | Extract `@Autowired`/`@Inject` injection relationships |
| Test Coverage Mapping | Auto-associate test classes with source classes (e.g., `UserServiceTest` → `UserService`) |

---

## Installation

Requires **Java 21+** (JRE or JDK). Docker and Native Image options bundle the runtime.

**Option 1: Download from GitHub Releases (recommended)**

```bash
# Fat JAR (all platforms)
curl -LO https://github.com/sodlinken/java-code-indexer/releases/latest/download/java-code-indexer-VERSION.jar

# Native Image (Linux)
curl -LO https://github.com/sodlinken/java-code-indexer/releases/latest/download/java-code-indexer-VERSION-linux-amd64.tar.gz

# Native Image (macOS)
curl -LO https://github.com/sodlinken/java-code-indexer/releases/latest/download/java-code-indexer-VERSION-darwin-arm64.tar.gz

# Native Image (Windows)
curl -LO https://github.com/sodlinken/java-code-indexer/releases/latest/download/java-code-indexer-VERSION-windows-amd64.zip
```

**Option 2: Build from source** (requires Java 21+ and Maven 3.8+)

```bash
git clone https://github.com/sodlinken/java-code-indexer.git
cd java-code-indexer
mvn package -q -DskipTests
# output: target/java-code-indexer-*-shaded.jar
```

**Option 3: Docker**

```bash
docker pull sodlinken/jcodeindexer:latest
```

---

## Quickstart

```bash
# 1. Index your JVM project (Java/Kotlin/Scala)
java -jar java-code-indexer-VERSION.jar --project-root /path/to/your/project --index

# 2. Start the MCP server (stdio, for Claude Code / Qwen Code / Cursor)
java -jar java-code-indexer-VERSION.jar --project-root /path/to/your/project
```

That's it. Your AI assistant can now query your codebase structure instead of reading files.

---

## CLI Reference

```
java -jar java-code-indexer-VERSION.jar [options]

Options:
  --project-root <path>   JVM project root directory (default: current dir)
  --data-dir <path>       Index data directory (default: .jindexer)
  --init                  Initialize database schema only
  --index                 Run indexer (extract symbols/references/calls)
  --status                Show index statistics
  --search <query>        Search directly (no MCP server needed)
  --export <file>         Export index data to JSON file
  --version               Show version number
  --help, -h              Show help

No flags → start MCP server over stdio.
```

---

## MCP Tools (26 tools)

When running as an MCP server, Java Code Indexer exposes these tools:

| Tool | Description |
|------|-------------|
| `find_symbol` | Find symbols (class/method/field) by name, `*` wildcard supported |
| `find_references` | Find all reference locations for a symbol |
| `get_call_graph` | Method call graph — callers, callees, or both |
| `search_code` | Search symbol names and code content (FTS5 full-text) |
| `get_file_info` | File details: symbols, code chunks, call relationships |
| `search_config` | Search config files (YAML / Properties / .env) |
| `find_dependencies` | Search project dependencies (Maven / Gradle) |
| `health` | Server health check with status and statistics |
| `list_projects` | List indexed projects (multi-project mode only) |
| `search_all_projects` | Search across all projects (multi-project mode) |
| `find_implementations` | Find all classes implementing an interface |
| `find_overrides` | Find all method overrides in subclasses |
| `find_usages` | Find all usages of a field/variable |
| `find_annotations` | Find all annotations on a symbol |
| `find_by_annotation` | Find symbols with a specific annotation |
| `find_api_routes` | Find API route mappings (URL → Controller method) |
| `find_route` | Find Controller method by HTTP method + URL path |
| `get_type_hierarchy` | Get complete class inheritance hierarchy |
| `get_bean_dependencies` | Find Bean's dependencies (what it depends on) |
| `get_bean_dependents` | Find Beans that depend on this Bean |
| `find_related_tests` | Find test classes related to source code |
| `reindex` | Trigger re-indexing of the project |
| `index_status` | Get current indexing status and statistics |
| `search_symbols` | Advanced symbol search with kind/annotation filters |
| `get_code_metrics` | Get code metrics (LOC, method/field count) for a class |
| `list_modules` | List auto-discovered sub-modules (auto_discover mode) |

---

## Configuration

Create `.jindexer/config.yaml` in your project root (optional):

```yaml
# === Project Paths ===
# Project root (CLI --project-root overrides this)
project_root: /path/to/project

# Data directory (stores index.db)
data_dir: .jindexer

# === Indexing ===
# Number of indexing threads
indexing_threads: 4

# Max file size to index (KB)
max_file_size_kb: 512

# Extract Javadoc comments
extract_javadoc: false

# Follow symbolic links
follow_symlinks: false

# Include patterns (files to index)
include_patterns:
  - "**/*.java"
  - "**/*.yml"
  - "**/*.yaml"
  - "**/*.properties"
  - "**/*.env"
  - "**/pom.xml"
  - "**/build.gradle"
  - "**/build.gradle.kts"

# Exclude patterns (files to skip)
exclude_patterns:
  - "**/target/**"
  - "**/build/**"
  - "**/.git/**"
  - "**/node_modules/**"

# === Storage ===
# Database filename
db_name: index.db

# === Logging ===
# Log level: DEBUG, INFO, WARN, ERROR
log_level: INFO

# Enable verbose logging
verbose: false

# === File Watcher ===
# Enable automatic re-indexing on file changes
watch_enabled: true

# Watch mode: event (NIO WatchService) or polling (SHA-1 check)
watch_mode: event

# Watch interval in seconds (polling mode)
watch_interval_seconds: 5

# Debounce interval in ms (event mode)
watch_debounce_ms: 500

# Directories to exclude from watching
watch_exclude:
  - "**/target/**"
  - "**/build/**"
  - "**/.git/**"
  - "**/node_modules/**"

# === Multi-project Mode ===
# Define multiple projects to index together
projects:
  - name: backend
    root: /path/to/backend
  - name: frontend
    root: /path/to/frontend

# === Auto Discover ===
# Auto-discover Maven/Gradle sub-modules
auto_discover: true
```

### Configuration Priority

CLI flags → environment variables → config file → defaults.

### All Configuration Options

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `project_root` | string | - | Project root directory |
| `data_dir` | string | `.jindexer` | Data directory for index database |
| `indexing_threads` | int | `4` | Number of indexing threads |
| `max_file_size_kb` | int | `512` | Max file size to index (KB) |
| `extract_javadoc` | bool | `false` | Extract Javadoc comments |
| `follow_symlinks` | bool | `false` | Follow symbolic links |
| `include_patterns` | list | `["**/*.java", ...]` | File patterns to index |
| `exclude_patterns` | list | `["**/target/**", ...]` | File patterns to skip |
| `db_name` | string | `index.db` | Database filename |
| `log_level` | string | `INFO` | Log level (DEBUG/INFO/WARN/ERROR) |
| `verbose` | bool | `false` | Enable verbose logging |
| `watch_enabled` | bool | `true` | Enable file watcher |
| `watch_mode` | string | `event` | Watch mode: `event` (NIO WatchService) or `polling` (SHA-1 check) |
| `watch_interval_seconds` | int | `5` | File watcher interval (polling mode) |
| `watch_debounce_ms` | int | `500` | Debounce interval in ms (event mode) |
| `watch_exclude` | list | `["**/target/**", ...]` | Directories to exclude from watching |
| `auto_discover` | bool | `false` | Auto-discover Maven/Gradle sub-modules |
| `projects` | list | `[]` | Multi-project mode configuration |

---

## AI Assistant Integration

### Claude Code / Cursor / Qwen Code

Add to your MCP configuration:

```json
{
  "mcpServers": {
    "java-code-indexer": {
      "command": "java",
      "args": ["-jar", "/path/to/java-code-indexer-VERSION.jar", "--project-root", "/path/to/project"]
    }
  }
}
```

### Docker Integration

```json
{
  "mcpServers": {
    "java-code-indexer": {
      "command": "docker",
      "args": ["run", "--rm", "-i", "-v", "/path/to/project:/project", "sodlinken/jcodeindexer:latest", "--project-root", "/project"]
    }
  }
}
```

---

## How It Works

```
┌──────────────┐     MCP (stdio)     ┌──────────────────────┐
│  AI Assistant │ ◄─────────────────► │  Java Code Indexer   │
│  (Qwen/Claude)│    JSON-RPC         │                      │
└──────────────┘                     │  ┌────────────────┐  │
                                     │  │ Parsers:       │  │
                                     │  │  JavaParser    │  │
                                     │  │  KotlinParser  │  │
                                     │  │  ScalaParser   │  │
                                     │  └───────┬────────┘  │
                                     │          ▼            │
                                     │  ┌────────────────┐  │
                                     │  │  SQLite (WAL)  │  │
                                     │  │  symbols       │  │
                                     │  │  references    │  │
                                     │  │  call_graphs   │  │
                                     │  │  annotations   │  │
                                     │  │  configs       │  │
                                     │  │  dependencies  │  │
                                     │  └────────────────┘  │
                                     └──────────────────────┘
```

### Indexing Strategy

1. Walk JVM source files (Java/Kotlin/Scala) via filesystem traversal
2. SHA-1 content hash skips unchanged files (incremental)
3. Parsers extract symbols, references, call relationships, and annotations
4. POM/Gradle parsers extract dependency information
5. Config parsers extract YAML/Properties/.env entries
6. All data upserted into embedded SQLite (WAL mode)
7. FTS5 full-text search indexes auto-synced via triggers

### Database Schema

Thirteen core tables:

- **`symbols`** — classes, methods, fields with location and signatures
- **`references`** — symbol usage locations across the codebase
- **`calls`** — method call relationships (caller → callee)
- **`annotations`** — symbol annotations with attributes
- **`chunks`** — code slices at class/method granularity
- **`file_meta`** — SHA-1 hashes for incremental indexing
- **`config_entries`** — YAML/Properties/.env key-value pairs
- **`dependencies`** — Maven/Gradle dependency declarations
- **`api_routes`** — Spring Boot API route mappings (URL → Controller)
- **`bean_dependencies`** — Spring Bean injection relationships
- **`test_mappings`** — Test class to source class associations
- **`index_metadata`** — Index metadata (key-value store)
- **`code_metrics`** — Code metrics (LOC, method/field counts, complexity)

Plus FTS5 full-text search tables (`symbols_fts`, `chunks_fts`) with auto-sync triggers.

---

## Architecture

```
src/main/java/com/sodlinken/jindexer/
├── cli/          # CLI entry point
├── mcp/          # MCP server (JSON-RPC over stdio)
├── config/       # YAML config loader
├── storage/      # SQLite schema & StorageService (including FTS5)
├── indexer/      # Incremental indexing engine
├── parser/       # Java, Kotlin, Scala, POM, Gradle, Config parsers
├── chunker/      # Code chunking (class/method slices)
├── search/       # Structured search (FTS5 full-text)
├── model/        # Data models (Symbol, Call, Chunk, Annotation, etc.)
└── util/         # SHA-1 hashing utility
```

---

## Distribution

### GitHub Release

Release workflow is automated via GitHub Actions:

| Artifact | Description |
|----------|-------------|
| `java-code-indexer-VERSION.jar` | Fat JAR, requires Java 21+ |
| `java-code-indexer-VERSION-linux-amd64.tar.gz` | Linux amd64 Native Image |
| `java-code-indexer-VERSION-darwin-arm64.tar.gz` | macOS arm64 Native Image |
| `java-code-indexer-VERSION-windows-amd64.zip` | Windows amd64 Native Image |
| `docker-image-VERSION.tar.gz` | Docker offline image |
| `checksums-VERSION.sha256` | SHA-256 checksums |

---

## Development

```bash
# Clone and build
git clone https://github.com/Lincoln-cn/JCodeIndexer.git
cd JCodeIndexer
mvn package -q -DskipTests

# Run tests
mvn test

# Index this project itself
java -jar target/java-code-indexer-*-shaded.jar --project-root . --index

# Start MCP server
java -jar target/java-code-indexer-*-shaded.jar --project-root .
```

### Release Workflow

GitHub Actions automated release process:

1. Push `v*` tag or manual trigger
2. Build Fat JAR
3. Build Native Image (Linux amd64, macOS arm64, Windows amd64)
4. Build Docker image
5. Create GitHub Release and upload artifacts

```bash
# Create release tag (replace VERSION with actual version)
git tag vVERSION
git push origin vVERSION
```

---

## Testing

```bash
# Run all tests (384+ tests)
mvn test

# Run specific test class
mvn test -Dtest=JavaParserAdapterTest

# Run performance benchmarks
mvn test -Dtest=PerformanceBenchmarkTest
```

---

## What It Is NOT

- Not a code execution sandbox
- Not a test runner or linter
- Not a replacement for LSP/IDE features
- Not AI-generated summaries (symbol extraction is deterministic)

## License

[Apache License 2.0](LICENSE)
