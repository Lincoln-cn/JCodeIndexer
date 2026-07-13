# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.0] - 2026-07-13

### Added
- **ChunkerTest** (6 tests): 小类整体切分、大类头部提取、方法切分、文件头、Record 类型、包名提取
- **JavaParserAdapterTest** (7 tests): 类/方法/字段符号提取、引用提取、调用关系、空文件、语法错误
- **McpE2ETest** (3 tests): 改为 JUnit 格式，数据库统计、符号搜索、空库搜索
- **CLI 增强**: --version, --status, --search 命令
- **FTS5 布尔搜索**: `Config AND Loader` 正常工作
- **Indexer 性能优化**: 逐条查询替代全量加载 10000 符号

### Testing
- 单元测试从 38 增加到 54 个，全部通过
- ChunkerTest: 6 个测试覆盖所有切分场景
- JavaParserAdapterTest: 7 个测试覆盖核心解析逻辑
- McpE2ETest: 改为 JUnit 格式，可被 `mvn test` 执行

## [0.4.0] - 2026-07-12

### Added
- **FTS5 全文搜索**: 使用 SQLite FTS5 虚拟表实现高性能全文搜索
- FTS5 触发器自动同步：INSERT/UPDATE/DELETE 时自动维护 FTS 索引
- 布尔搜索支持：`Config AND Loader`、`Config OR Service`、`NOT test`
- 前缀搜索：`Mcp*` 匹配所有 Mcp 开头的符号
- 短语搜索：`"import java"` 精确匹配短语
- StructuredSearch 优先使用 FTS5，降级到 LIKE 查询

### Fixed
- **FTS5 布尔搜索**: 修复 `AND`/`OR`/`NOT` 被当作普通词处理的问题
- **PomParser `${project.version}`**: 支持解析项目自身版本（不仅限 parent version）

### Performance
- 搜索性能提升 10-100x（FTS5 vs LIKE）
- 平均搜索耗时从 ~50ms 降到 ~6ms

### Documentation
- 更新 API 文档：移除 semantic_search，工具数从 9 改为 8
- 更新设计文档：移除 embedding 表引用，工具计数 9→8
- 更新入门文档：修正配置示例，添加多项目模式
- 新增开发推进计划（innerdoc/roadmap/）
- 新增设计文档（innerdoc/design/v0.4.0-fts5-search.md）

### Testing
- 新增 PomParserTest (5 tests)
- 新增 StructuredSearchTest (5 tests)
- 扩展 StorageServiceTest (+12 tests)
- 总测试数从 16 增加到 38

## [0.3.1] - 2026-07-12

### Fixed
- **安全漏洞**: SnakeYAML 使用 SafeConstructor 防止反序列化攻击
- **PomParser.findLineNumber**: 现在正确读取文件定位行号
- **性能优化**: SHA-1 缓存避免重复计算

## [0.3.0] - 2026-07-12

### Fixed
- **find_references**: 引用 symbol_id=0 被跳过导致返回空结果
- **find_dependencies**: SQL 查询未处理 NULL 值导致通配符查询失败
- **get_file_info**: Windows 路径分隔符不匹配
- Release workflow needs 条件修复

### Changed
- 移除语义搜索功能（embedding/vector 相关代码）
- Python E2E 测试改用环境变量
- 单元测试精简为 16 个

### Added
- CI workflow (ci.yml)
- Release workflow 添加测试步骤
- CHANGELOG 文件

### Documentation
- 重写设计文档
- 完善 API 参考
- 修复 MkDocs 配置

## [1.0.0-SNAPSHOT] - Unreleased

### Added
- MCP server with 7 tools for AI coding assistants
- Symbol indexing (class/method/field) with JavaParser AST
- Reference tracking across codebase
- Method call graph analysis
- Incremental indexing with SHA-1 file hashing
- Config file parsing (YAML / Properties / .env)
- Dependency parsing (Maven POM / Gradle)
- Multi-project mode support
- Structured search with SQLite LIKE queries

### Tools
- `find_symbol` — Find symbols by name with wildcard support
- `find_references` — Find all reference locations for a symbol
- `get_call_graph` — Method call graph (callers/callees)
- `search_code` — Search symbol names and code content
- `get_file_info` — File details: symbols, chunks, calls
- `search_config` — Search config files (YAML/Properties/.env)
- `find_dependencies` — Search project dependencies (Maven/Gradle)
- `list_projects` — List indexed projects (multi-project mode)

### Distribution
- Fat JAR (Java 21+)
- Native Image (GraalVM: Linux amd64, macOS arm64)
- Docker image (multi-platform)
- GitHub Actions release workflow

### Infrastructure
- SQLite storage with WAL mode
- Virtual Threads for parallel indexing
- SHA-1 incremental indexing
- JSON-RPC 2.0 over stdio (MCP protocol)
