# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2026-07-12

### Fixed
- **find_references**: 引用 symbol_id=0 被跳过导致返回空结果。修复为先插入符号再解析引用的 qualifiedName→symbolId 映射
- **find_dependencies**: SQL 查询未处理 NULL 值，当 group_id/version 为 NULL 时 LIKE 比较返回 NULL。添加 `? = '*'` 条件处理通配符查询
- **get_file_info**: Windows 路径分隔符 `\` 与查询的 `/` 不匹配。统一使用正斜杠
- Release workflow `needs` condition blocking Fat JAR release when Native/Docker builds are skipped

### Changed
- 移除语义搜索功能（embedding/vector 相关代码），聚焦核心结构化搜索
- Python E2E 测试改用环境变量，不再硬编码路径
- 单元测试从 23 个精简为 16 个（移除 embedding 相关测试）

### Added
- CI workflow for pull requests and pushes (`ci.yml`)
- Test step in release workflow before build
- CHANGELOG 文件
- `find_references` 支持按 symbol_name 模糊查找
- `find_dependencies` 支持通配符 `*` 查询所有依赖

### Documentation
- 重写设计文档（不再引用不存在的 `innerdoc/` 路径）
- 完善 API 参考（7 个工具完整参数文档）
- 修复 MkDocs 配置（site_url, repo_url, edit_uri）
- 更新 README 移除 embedding 相关配置

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
