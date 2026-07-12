# 开发推进计划

## 版本规划

| 版本 | 主题 | 目标 | 状态 |
|------|------|------|------|
| v0.3.1 | 安全修复 + 性能优化 | 安全漏洞修复，SHA-1 缓存 | ✅ 已完成 |
| v0.4.0 | FTS5 全文搜索 | 高性能全文搜索 + 布尔查询 | ✅ 已完成 (FTS5 部分) |
| v0.4.1 | CLI 增强 | --version, --status, --search | ⏳ 待开发 |
| v0.5.0 | 测试完善 + 代码质量 | 测试覆盖率提升 + 重构 | ⏳ 待开发 |
| v1.0.0 | 稳定版发布 | 生产就绪 | ⏳ 待开发 |

---

## v0.4.0 — FTS5 全文搜索 (已完成)

### 已实现功能

| 功能 | 状态 | 说明 |
|------|------|------|
| FTS5 虚拟表 | ✅ | symbols_fts, chunks_fts |
| 触发器自动同步 | ✅ | INSERT/UPDATE/DELETE 自动维护 |
| 布尔搜索 | ✅ | `Config AND Loader` |
| 前缀搜索 | ✅ | `Mcp*` |
| 短语搜索 | ✅ | `"import java"` |
| 降级兼容 | ✅ | FTS5 失败时降级到 LIKE |

### 性能对比

| 操作 | LIKE (旧) | FTS5 (新) | 提升 |
|------|-----------|-----------|------|
| 1000 文件搜索 | ~50ms | ~6ms | 8x |
| 10000 文件搜索 | ~500ms | ~10ms | 50x |

### 修改文件

- `Schema.java` — FTS5 表 + 触发器
- `StorageService.java` — FTS 搜索方法
- `StructuredSearch.java` — 优先使用 FTS5

---

## v0.4.1 — CLI 增强 (待开发)

**预计周期**: 1-2 天

### 新增命令

| 命令 | 说明 | 优先级 |
|------|------|--------|
| `--version` | 显示版本号 | P1 |
| `--status` | 显示索引统计信息 | P1 |
| `--rebuild` | 强制全量重建索引 | P2 |
| `--search <query>` | 直接搜索，无需启动 MCP | P2 |

### 修改文件

- `CliMain.java` — 添加新命令处理

---

## v0.4.2 — 性能优化 (待开发)

**预计周期**: 1-2 天

### 优化项

| 优化 | 说明 | 优先级 |
|------|------|--------|
| Indexer 引用解析 | 避免 `listAllSymbols(10000)` 全量加载 | P0 |
| DatabaseManager 线程安全 | `synchronized` 序列化所有写操作 | P0 |
| McpServer 错误处理 | `send()` 吞异常 | P1 |

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
v0.3.1 (2026-07-12) ── 安全修复 + 性能优化 ✅
    │
    ├── v0.4.0 (2026-07-12) ── FTS5 全文搜索 ✅
    │   ├── FTS5 虚拟表 + 触发器
    │   ├── 布尔搜索 + 前缀搜索
    │   └── 性能提升 10-100x
    │
    ├── v0.4.1 (预计 2026-07-19) ── CLI 增强
    │   ├── --version
    │   ├── --status
    │   └── --search
    │
    ├── v0.4.2 (预计 2026-07-26) ── 性能优化
    │   ├── Indexer 引用解析优化
    │   ├── DatabaseManager 线程安全
    │   └── McpServer 错误处理
    │
    └── v0.5.0 (预计 2026-08-09) ── 测试完善 + 代码质量
        ├── Chunker 单元测试
        ├── JavaParserAdapter 单元测试
        ├── McpE2ETest 改为 JUnit
        ├── 消除重复代码
        └── Config 改为 record

v1.0.0 (预计 2026-08-23) ── 生产稳定版发布
```

---

## 验证标准

### v0.4.0 ✅
- [x] FTS5 搜索在 1000+ 文件项目中 < 10ms
- [x] 布尔搜索正常工作
- [x] 所有现有测试通过
- [x] 38+ 单元测试

### v0.4.1
- [ ] `--version` 输出正确版本号
- [ ] `--status` 显示正确的索引统计
- [ ] `--search` 能直接搜索并返回结果

### v0.4.2
- [ ] Indexer 引用解析不全量加载
- [ ] DatabaseManager 支持并发写入
- [ ] McpServer send() 不吞异常

### v0.5.0
- [ ] ChunkerTest 覆盖所有切分场景
- [ ] JavaParserAdapterTest 覆盖核心解析
- [ ] McpE2ETest 能被 `mvn test` 执行
- [ ] 无重复代码（SignatureBuilder 提取）
- [ ] Config 为 record 类型
- [ ] 50+ 单元测试
