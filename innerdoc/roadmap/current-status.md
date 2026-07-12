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

| 工具 | 状态 | FTS5 |
|------|------|------|
| find_symbol | ✅ 正常 | ✓ |
| find_references | ✅ 正常 | — |
| get_call_graph | ✅ 正常 | — |
| search_code | ✅ 正常 | ✓ |
| get_file_info | ✅ 正常 | — |
| search_config | ✅ 正常 | — |
| find_dependencies | ✅ 正常 | — |
| list_projects | ✅ 正常 | — |

#### CLI 命令

| 命令 | 状态 |
|------|------|
| --init | ✅ 正常 |
| --index | ✅ 正常 |
| --status | ✅ 正常 (v0.4.0 新增) |
| --search | ✅ 正常 (v0.4.0 新增) |
| --version | ✅ 正常 (v0.4.0 新增) |

#### 核心模块

| 模块 | 状态 | 测试 |
|------|------|------|
| Config/ConfigLoader | ✅ 正常 | ✅ 11 tests |
| StorageService | ✅ 正常 | ✅ 17 tests |
| PomParser | ✅ 正常 | ✅ 5 tests |
| StructuredSearch (FTS5) | ✅ 正常 | ✅ 5 tests |
| McpServer | ✅ 正常 | ⚠️ 仅 E2E |
| Indexer | ✅ 正常 | ❌ 无测试 |
| Chunker | ✅ 正常 | ❌ 无测试 |
| JavaParserAdapter | ✅ 正常 | ❌ 无测试 |

### 已修复的问题

| 问题 | 修复版本 |
|------|---------|
| find_references 返回 0 | v0.3.0 |
| find_dependencies 通配符失败 | v0.3.0 |
| get_file_info 路径不匹配 | v0.3.0 |
| SnakeYAML 安全漏洞 | v0.3.1 |
| PomParser findLineNumber | v0.3.1 |
| SHA-1 重复计算 | v0.3.1 |
| FTS5 布尔搜索 | v0.4.0 |
| Indexer 全量加载符号 | v0.4.0 |

### 测试统计

- 单元测试: 38 个
- E2E 测试: 1 个（非 JUnit）
- 测试通过率: 100%
