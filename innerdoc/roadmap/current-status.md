# 项目当前状态

## 最新版本: v1.0.0 (2026-07-13)

### 版本历史

| 版本 | 日期 | 主要变更 |
|------|------|---------|
| v0.3.0 | 2026-07-12 | 修复 find_references/find_dependencies/get_file_info bug |
| v0.3.1 | 2026-07-12 | SnakeYAML 安全修复, SHA-1 缓存, PomParser findLineNumber |
| v0.4.0 | 2026-07-12 | FTS5 全文搜索, CLI 增强, Indexer 引用解析优化 |
| v0.5.0 | 2026-07-12 | 测试完善 54 tests (ChunkerTest, JavaParserAdapterTest, McpE2ETest) |
| v0.6.0 | 2026-07-12 | DatabaseManager 事务安全, ConfigLoader 类型安全, McpServer OOM 保护 |
| v0.6.1 | 2026-07-12 | health 健康检查工具 |
| v0.6.2 | 2026-07-12 | FTS5 搜索排序优化, 索引统计增强 |
| v0.6.3 | 2026-07-12 | 详细统计 (Top files, code lines, avg symbols) |
| v0.7.0 | 2026-07-13 | 搜索结果高亮 (highlights 字段) |
| v0.7.1 | 2026-07-13 | search_all_projects 多项目搜索 |
| v0.7.2 | 2026-07-13 | --export 索引导出为 JSON |
| v0.8.0 | 2026-07-13 | GradleParser + ConfigFileParser + DatabaseManager 测试 (42 tests) |
| v0.8.1 | 2026-07-13 | StorageService 边界测试 (29 tests) |
| v0.8.2 | 2026-07-13 | Chunker 边界测试 (21 tests) |
| v0.8.3 | 2026-07-13 | Indexer 单元测试 (21 tests) |
| v0.8.4 | 2026-07-13 | MCP 工具集成测试 (27 tests) |
| v0.9.0 | 2026-07-13 | 文档完善 (配置详解, 故障排查, 贡献者指南) |
| v0.9.1 | 2026-07-13 | 性能基准测试 (11 tests) |
| v1.0.0 | 2026-07-13 | 生产稳定版发布 |

### 当前功能状态

#### MCP 工具 (10 个)

| 工具 | 状态 | FTS5 | 测试 |
|------|------|------|------|
| find_symbol | ✅ | ✓ | ✓ |
| find_references | ✅ | — | ✓ |
| get_call_graph | ✅ | — | ✓ |
| search_code | ✅ | ✓ | ✓ |
| get_file_info | ✅ | — | ✓ |
| search_config | ✅ | — | ✓ |
| find_dependencies | ✅ | — | ✓ |
| list_projects | ✅ | — | ✓ |
| search_all_projects | ✅ | — | ✓ |
| health | ✅ | — | ✓ |

#### CLI 命令

| 命令 | 状态 |
|------|------|
| --init | ✅ |
| --index | ✅ |
| --status | ✅ |
| --search | ✅ |
| --version | ✅ |
| --export | ✅ |

### 测试统计

- 单元测试: 223 个
- 测试通过率: 100%
- 覆盖模块: Config (11), Storage (68), Parser (44), Search (5), Chunker (27), Indexer (21), MCP (36), Benchmark (11)

### 文档

- README.md (English)
- README.zh-CN.md (中文)
- doc/getting-started/ — 快速入门
- doc/api/ — API 参考 (10 个工具)
- doc/configuration/ — 配置详解
- doc/troubleshooting/ — 故障排查
- doc/contributing/ — 贡献者指南
- doc/design/ — 架构设计
- doc/performance/ — 性能基准

### 性能指标

| 场景 | 性能 |
|------|------|
| 单文件索引 | ~18 ms |
| 50 文件索引 | ~742 ms |
| 100 文件索引 | ~1119 ms |
| 增量索引 | ~91 ms |
| 符号搜索 | <10 ms/次 |
| 内容搜索 | <40 ms/次 |
