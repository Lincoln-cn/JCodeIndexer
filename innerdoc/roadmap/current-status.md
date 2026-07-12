# 项目当前状态

## 最新版本: v0.4.0 (2026-07-12)

### 版本历史

| 版本 | 日期 | 主要变更 |
|------|------|---------|
| v0.3.0 | 2026-07-12 | 修复 find_references/find_dependencies/get_file_info bug |
| v0.3.1 | 2026-07-12 | SnakeYAML 安全修复, SHA-1 缓存, PomParser findLineNumber |
| v0.4.0 | 2026-07-12 | FTS5 全文搜索, CLI 增强, Indexer 引用解析优化 |

### 当前功能状态

#### MCP 工具 (8 个)

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

#### CLI 命令

| 命令 | 状态 |
|------|------|
| --init | ✅ |
| --index | ✅ |
| --status | ✅ (v0.4.0) |
| --search | ✅ (v0.4.0) |
| --version | ✅ (v0.4.0) |

### 测试统计

- 单元测试: 38 个
- 测试通过率: 100%
- 覆盖模块: Config (11), Storage (17), Parser (5), Search (5)

### 下一步

- v0.5.0: 测试完善 + 代码质量 (Chunker/JavaParserAdapter 测试, SignatureBuilder, Config record)
- v0.6.0: 健壮性 + 错误处理 (配置校验, 索引恢复, 并发保护)
- v0.7.0: 开发者体验 (监控模式, 健康检查)
- v1.0.0: 生产稳定版发布
