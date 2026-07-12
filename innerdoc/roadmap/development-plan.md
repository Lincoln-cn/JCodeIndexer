# 开发推进计划

## 版本规划

| 版本 | 主题 | 目标 | 状态 |
|------|------|------|------|
| v0.3.0 | 核心 bug 修复 | find_references/find_dependencies/get_file_info 修复 | ✅ 已完成 |
| v0.3.1 | 安全修复 + 性能优化 | SnakeYAML SafeConstructor, SHA-1 缓存, PomParser findLineNumber | ✅ 已完成 |
| v0.4.0 | FTS5 + CLI + 性能优化 | 全文搜索, CLI 命令, 引用解析优化 | ✅ 已完成 |
| v0.5.0 | 测试完善 + 代码质量 | 测试覆盖率提升 + 重构 | ⏳ 待开发 |
| v0.6.0 | 健壮性 + 错误处理 | 优雅降级、配置校验、索引恢复 | ⏳ 待开发 |
| v0.7.0 | 开发者体验 | 监控模式、进度改进、日志优化 | ⏳ 待开发 |
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
| --status | 显示索引统计 | CliMain.java |
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
| SignatureBuilder | 提取重复签名构建代码 (~50行) |
| 共享 JavaParser | 消除重复静态实例 |
| Config 改为 record | 与其他 model 保持一致 |

---

## v0.6.0 — 健壮性 + 错误处理 (待开发)

**预计周期**: 1 周

**目标**: 让项目在异常情况下也能优雅运行

### 错误处理改进

| 改进项 | 说明 | 文件 |
|--------|------|------|
| Config 校验 | 启动时校验必填字段、类型、路径存在性 | Config.java, CliMain.java |
| 索引损坏恢复 | 数据库损坏时自动重建 | DatabaseManager.java, Indexer.java |
| 更好的错误消息 | 中英文统一、包含文件路径和行号 | 全局 |
| 异常分类 | 区分用户错误 vs 系统错误，提供修复建议 | CliMain.java |

### 健壮性

| 改进项 | 说明 |
|--------|------|
| 并发索引保护 | 防止多个进程同时索引同一项目 |
| 增量索引可靠性 | SHA-1 碰撞时的处理策略 |
| 大文件处理 | 超大文件的内存保护 |
| 符号名冲突 | 同名符号的优先级规则 |

### 验证标准

- [ ] 配置文件缺失/格式错误时给出清晰提示
- [ ] 数据库损坏时能自动重建索引
- [ ] 两个进程同时索引不会导致数据库损坏
- [ ] 50+ 单元测试通过

---

## v0.7.0 — 开发者体验 (待开发)

**预计周期**: 1 周

**目标**: 让开发者更容易使用和调试

### 新功能

| 功能 | 说明 | 价值 |
|------|------|------|
| 监控模式 | 文件变更时自动重新索引 | 开发时实时更新 |
| 健康检查 | MCP 服务器提供 `health` 工具 | 监控集成 |
| 索引进度改进 | 更详细的进度信息和 ETA | 用户体验 |

### 监控模式设计

```bash
# 启动监控模式
java -jar jindexer.jar --project-root . --watch

# 功能：
# - 监听文件系统变更
# - 300ms 防抖
# - 自动增量索引
# - 通知 MCP 客户端更新
```

### 验证标准

- [ ] `--watch` 模式能监听文件变更
- [ ] 文件保存后 1s 内自动重新索引
- [ ] `health` 工具返回服务器状态

---

## v1.0.0 — 稳定版发布 (待开发)

**预计周期**: 2 周

**目标**: 生产就绪

### 发布清单

| 类别 | 检查项 | 状态 |
|------|--------|------|
| 测试 | 50+ 单元测试通过 | ⏳ |
| 测试 | E2E 测试覆盖所有工具 | ⏳ |
| 文档 | API 文档完整 | ✅ |
| 文档 | 入门文档完整 | ✅ |
| 文档 | CHANGELOG 完整 | ✅ |
| 性能 | 搜索 < 10ms (1000+ 文件) | ✅ |
| 性能 | 索引 40 文件 < 5s | ✅ |
| 健壮性 | 错误处理完善 | ⏳ |
| 健壮性 | 配置校验 | ⏳ |
| 兼容性 | Windows/Linux/macOS | ✅ |
| 兼容性 | Java 21+ | ✅ |

---

## 版本时间线

```
v0.3.0 (2026-07-12) ── 核心 bug 修复 ✅
    │
    v0.3.1 (2026-07-12) ── 安全修复 + 性能优化 ✅
    │
    v0.4.0 (2026-07-12) ── FTS5 + CLI + 性能优化 ✅
    │
    v0.5.0 (预计 2026-07-19) ── 测试完善 + 代码质量
    │   ├── Chunker 单元测试
    │   ├── JavaParserAdapter 单元测试
    │   ├── McpE2ETest 改为 JUnit
    │   ├── 消除重复代码
    │   └── Config 改为 record
    │
    v0.6.0 (预计 2026-07-26) ── 健壮性 + 错误处理
    │   ├── Config 校验
    │   ├── 索引损坏恢复
    │   ├── 更好的错误消息
    │   └── 并发索引保护
    │
    v0.7.0 (预计 2026-08-02) ── 开发者体验
    │   ├── 监控模式
    │   ├── 健康检查
    │   └── 索引进度改进
    │
    v1.0.0 (预计 2026-08-09) ── 生产稳定版发布
        ├── 50+ 单元测试
        ├── 文档完整
        ├── 性能基准测试
        └── 错误处理完善
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

### v0.6.0
- [ ] 配置文件缺失/格式错误时给出清晰提示
- [ ] 数据库损坏时能自动重建索引
- [ ] 两个进程同时索引不会导致数据库损坏
- [ ] 60+ 单元测试通过

### v0.7.0
- [ ] `--watch` 模式能监听文件变更
- [ ] 文件保存后 1s 内自动重新索引
- [ ] `health` 工具返回服务器状态
- [ ] 70+ 单元测试通过

### v1.0.0
- [ ] 所有测试通过 (70+)
- [ ] 文档完整
- [ ] 性能基准测试通过
- [ ] 错误处理完善
- [ ] 生产环境验证通过
