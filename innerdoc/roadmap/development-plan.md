# 开发推进计划

## 版本规划

| 版本 | 主题 | 目标 |
|------|------|------|
| v0.3.1 | 安全修复 + 性能优化 | 当前版本（已完成） |
| v0.4.0 | 搜索性能 + CLI 增强 | FTS5 全文搜索 + CLI 命令 |
| v0.5.0 | 测试完善 + 代码质量 | 测试覆盖率提升 + 重构 |
| v1.0.0 | 稳定版发布 | 生产就绪 |

---

## v0.4.0 — 搜索性能 + CLI 增强

**预计周期**: 1-2 周

### 1. FTS5 全文搜索 (P0)

**目标**: 替换 `LIKE '%query%'` 全表扫描，搜索性能提升 10-100x

**实现方案**:
- SQLite FTS5 虚拟表（symbols_fts, chunks_fts）
- 索引时同步写入 FTS 表
- 搜索时使用 FTS5 MATCH 查询

**修改文件**:
- `Schema.java` — 添加 FTS5 表定义
- `StorageService.java` — 添加 FTS 搜索方法
- `Indexer.java` — 索引时同步写入 FTS 表
- `StructuredSearch.java` — 使用 FTS 搜索

**工作量**: 2-3 天

### 2. Indexer 引用解析性能优化 (P0)

**目标**: 避免 `listAllSymbols(10000)` 全量加载

**实现方案**:
- 按文件范围查询符号，而非全表扫描
- 或使用符号名索引直接查找

**修改文件**:
- `Indexer.java` — 优化 `indexJavaFile` 中的符号查找
- `StorageService.java` — 添加按文件范围查询符号方法

**工作量**: 1 天

### 3. CLI 增强 (P1)

**新增命令**:
- `--version` — 显示版本号
- `--status` — 显示索引统计信息
- `--rebuild` — 强制全量重建索引（跳过 SHA-1 检查）
- `--search <query>` — 直接搜索，无需启动 MCP server

**修改文件**:
- `CliMain.java` — 添加新命令处理

**工作量**: 1-2 天

### 4. McpServer 错误处理 (P1)

**问题**: `send()` 方法吞掉 IOException，客户端不知道响应丢失

**修改文件**:
- `McpServer.java` — send() 方法添加错误日志和通知

**工作量**: 0.5 天

### 5. DatabaseManager 线程安全 (P0)

**问题**: `executeInTransaction()` 使用 `synchronized`，序列化所有写操作

**实现方案**:
- 使用 SQLite WAL 模式 + 读写分离
- 或使用连接池 + 事务隔离

**修改文件**:
- `DatabaseManager.java` — 优化并发写入

**工作量**: 1-2 天

---

## v0.5.0 — 测试完善 + 代码质量

**预计周期**: 1-2 周

### 1. Chunker 单元测试 (P2)

**目标**: 覆盖代码切片逻辑

**测试用例**:
- 类切分（小类整体、大类只取头部）
- 方法切分（正常方法、超长方法截断）
- 文件头切分（package + imports）
- Record 类型支持

**新建文件**:
- `src/test/java/.../chunker/ChunkerTest.java`

**工作量**: 1 天

### 2. JavaParserAdapter 单元测试 (P2)

**目标**: 覆盖核心解析器

**测试用例**:
- 类/方法/字段符号提取
- import 引用提取
- 调用关系提取
- 边界情况（空文件、语法错误）

**新建文件**:
- `src/test/java/.../parser/JavaParserAdapterTest.java`

**工作量**: 2 天

### 3. McpE2ETest 改为 JUnit (P2)

**问题**: 当前是 `public static void main()`，不会被 `mvn test` 执行

**修改文件**:
- `src/test/java/.../mcp/McpE2ETest.java` — 改为 `@Test` 注解

**工作量**: 0.5 天

### 4. 消除重复代码 (P3)

**问题**: JavaParserAdapter 和 Chunker 有 ~50 行重复签名构建代码

**实现方案**:
- 提取 `SignatureBuilder` 工具类
- 共享静态 JavaParser 实例

**修改文件**:
- 新建 `src/main/java/.../util/SignatureBuilder.java`
- `JavaParserAdapter.java` — 使用 SignatureBuilder
- `Chunker.java` — 使用 SignatureBuilder

**工作量**: 1 天

### 5. Config 改为 record (P3)

**目标**: 与其他 model 保持一致风格

**修改文件**:
- `Config.java` — 改为 record 类型
- `ConfigLoader.java` — 适配 record 不可变性

**工作量**: 1 天

---

## 版本时间线

```
v0.3.1 (2026-07-12) ── 当前版本
    │
    ├── v0.4.0 (预计 2026-07-26)
    │   ├── FTS5 全文搜索
    │   ├── Indexer 性能优化
    │   ├── CLI 增强
    │   ├── McpServer 错误处理
    │   └── DatabaseManager 线程安全
    │
    └── v0.5.0 (预计 2026-08-09)
        ├── Chunker 单元测试
        ├── JavaParserAdapter 单元测试
        ├── McpE2ETest 改为 JUnit
        ├── 消除重复代码
        └── Config 改为 record

v1.0.0 (预计 2026-08-23)
    └── 生产稳定版发布
```

---

## 验证标准

### v0.4.0
- [ ] FTS5 搜索在 1000+ 文件项目中 < 10ms
- [ ] `--version` 输出正确版本号
- [ ] `--status` 显示正确的索引统计
- [ ] `--search` 能直接搜索并返回结果
- [ ] 所有现有测试通过
- [ ] 38+ 单元测试

### v0.5.0
- [ ] ChunkerTest 覆盖所有切分场景
- [ ] JavaParserAdapterTest 覆盖核心解析
- [ ] McpE2ETest 能被 `mvn test` 执行
- [ ] 无重复代码（SignatureBuilder 提取）
- [ ] Config 为 record 类型
- [ ] 50+ 单元测试

### v1.0.0
- [ ] 所有 v0.4.0 + v0.5.0 功能完成
- [ ] 文档完整（API、设计、入门）
- [ ] 生产环境验证通过
- [ ] 性能基准测试通过
