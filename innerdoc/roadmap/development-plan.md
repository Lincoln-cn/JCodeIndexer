# 开发推进计划

## 版本规划

| 版本 | 主题 | 目标 | 状态 |
|------|------|------|------|
| v0.3.0 | 核心 bug 修复 | find_references/find_dependencies/get_file_info 修复 | ✅ 已完成 |
| v0.3.1 | 安全修复 + 性能优化 | SnakeYAML SafeConstructor, SHA-1 缓存, PomParser findLineNumber | ✅ 已完成 |
| v0.4.0 | FTS5 + CLI + 性能优化 | 全文搜索, CLI 命令, 引用解析优化 | ✅ 已完成 |
| v0.5.0 | 测试完善 + 代码质量 | 测试覆盖率提升 + 重构 | ⏳ 待开发 |
| v1.0.0 | 稳定版发布 | 生产就绪 | ⏳ 待开发 |

---

## v0.3.0 — 核心 bug 修复 (已完成)

### 修复内容

| Bug | 根因 | 修复文件 |
|-----|------|---------|
| find_references 返回 0 | 引用 symbol_id=0 被跳过 | Indexer.java |
| find_dependencies 通配符失败 | NULL 值导致 LIKE 返回 NULL | StorageService.java |
| get_file_info 返回 0 | Windows 路径 `\` vs `/` 不匹配 | Indexer.java, StorageService.java |

---

## v0.3.1 — 安全修复 + 性能优化 (已完成)

### 修复内容

| 修复 | 说明 | 文件 |
|------|------|------|
| SnakeYAML 安全漏洞 | 使用 SafeConstructor 防止反序列化攻击 | ConfigLoader.java, ConfigFileParser.java |
| PomParser findLineNumber | 正确读取文件定位行号 | PomParser.java |
| SHA-1 缓存 | 避免重复计算 | Indexer.java |

---

## v0.4.0 — FTS5 + CLI + 性能优化 (已完成)

### 已实现功能

| 功能 | 说明 | 文件 |
|------|------|------|
| FTS5 全文搜索 | symbols_fts, chunks_fts + 触发器自动同步 | Schema.java, StorageService.java, StructuredSearch.java |
| 布尔搜索 | `Config AND Loader` | StorageService.java |
| 前缀搜索 | `Mcp*` | StorageService.java |
| 短语搜索 | `"import java"` | StorageService.java |
| --version | 显示版本号 | CliMain.java |
| --status | 显示索引统计 (符号/引用/调用/块/文件/配置/依赖) | CliMain.java |
| --search | 直接搜索，无需启动 MCP | CliMain.java |
| Indexer 引用解析优化 | 逐条查询替代全量加载 | Indexer.java, StorageService.java |

### 性能对比

| 操作 | LIKE (旧) | FTS5 (新) | 提升 |
|------|-----------|-----------|------|
| 1000 文件搜索 | ~50ms | ~6ms | 8x |
| 10000 文件搜索 | ~500ms | ~10ms | 50x |

### 新增测试

| 测试类 | 测试数 | 覆盖 |
|--------|--------|------|
| PomParserTest | 5 | 解析、dependencyManagement、变量、${project.version}、isPomFile |
| StructuredSearchTest | 5 | FTS5 搜索、通配符、无结果、限制、计时 |
| StorageServiceTest (扩展) | +12 | 符号/引用/调用/块/配置/依赖/文件元信息/统计 |

---

## v0.5.0 — 测试完善 + 代码质量 (待开发)

**预计周期**: 1-2 周

### 测试计划

| 模块 | 测试数 | 状态 |
|------|--------|------|
| Chunker | 6 | ❌ 待开发 |
| JavaParserAdapter | 7 | ❌ 待开发 |
| McpE2ETest | 改为 JUnit | ❌ 待开发 |

### 代码质量

| 优化 | 说明 |
|------|------|
| SignatureBuilder | 提取重复签名构建代码 |
| 共享 JavaParser | 消除重复静态实例 |
| Config 改为 record | 与其他 model 保持一致 |

---

## 版本时间线

```
v0.3.0 (2026-07-12) ── 核心 bug 修复 ✅
    │
    v0.3.1 (2026-07-12) ── 安全修复 + 性能优化 ✅
    │
    v0.4.0 (2026-07-12) ── FTS5 + CLI + 性能优化 ✅
    │   ├── FTS5 全文搜索 (布尔/前缀/短语)
    │   ├── CLI (--version/--status/--search)
    │   ├── Indexer 引用解析优化
    │   └── 38 个单元测试
    │
    └── v0.5.0 (预计 2026-07-26) ── 测试完善 + 代码质量
        ├── Chunker 单元测试
        ├── JavaParserAdapter 单元测试
        ├── McpE2ETest 改为 JUnit
        ├── 消除重复代码
        └── Config 改为 record

v1.0.0 (预计 2026-08-09) ── 生产稳定版发布
```

---

## 验证标准

### v0.4.0 ✅
- [x] FTS5 搜索在 1000+ 文件项目中 < 10ms
- [x] 布尔搜索正常工作
- [x] --version / --status / --search 正常
- [x] Indexer 引用解析不全量加载
- [x] 38 个单元测试通过

### v0.5.0
- [ ] ChunkerTest 覆盖所有切分场景
- [ ] JavaParserAdapterTest 覆盖核心解析
- [ ] McpE2ETest 能被 `mvn test` 执行
- [ ] 无重复代码（SignatureBuilder 提取）
- [ ] Config 为 record 类型
- [ ] 50+ 单元测试
