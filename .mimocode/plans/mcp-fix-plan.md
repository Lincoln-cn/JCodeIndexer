# MCP 问题修复计划

## 一、MCP 工具注册问题

### 问题描述
v2.0.0 新增的工具（`find_circular_deps`、`list_modules`）未在 MCP 工具列表中注册。

### 原因分析
McpServer 构造函数在 `handleToolsList()` 中注册工具时，部分工具的注册逻辑位于条件分支内（如 `multiProject`、`autoDiscover`），导致在单项目模式下这些工具不被注册。

### 修复方案
在 `handleToolsList()` 中，确保所有工具都在无条件分支中注册，或将条件分支的工具描述改为"仅在特定模式下可用"。

### 影响文件
- `src/main/java/com/sodlinken/jindexer/mcp/McpServer.java`

---

## 二、FileWatcher Indexer 实例不共享

### 问题描述
`McpServer.initFileWatchers()` 为每个项目创建独立的 `Indexer` 实例，与 MCP 工具调用 `callReindex()` 时创建的 `Indexer` 实例不同。

### 原因分析
```java
// initFileWatchers() 中
Indexer indexer = new Indexer(ctx.config(), ctx.storage(), ctx.dbManager());

// callReindex() 中
Indexer indexer = new Indexer(ctx.config(), storage, ctx.dbManager());
```

两个 Indexer 实例有独立的 `indexing` 锁标志，可能导致并发写入问题。

### 修复方案
在 `ProjectContext` 中持有共享的 `Indexer` 实例，Watcher 和 MCP 工具共用。

### 影响文件
- `src/main/java/com/sodlinken/jindexer/mcp/ProjectContext.java`
- `src/main/java/com/sodlinken/jindexer/mcp/McpServer.java`

---

## 三、配置热更新路径问题

### 问题描述
`.jindexer/config.yaml` 变化时，EventFileWatcher 的 `processEvent()` 会在 `isExcluded` 检查时直接返回，导致配置热更新不生效。

### 原因分析
`.jindexer` 目录在 `watchExclude` 列表中，WatchService 不会监听该目录下的文件变化。

### 修复方案
在 `processEvent()` 中，先检查是否为配置文件（`.jindexer/config.yaml`），如果是则直接加入 `pendingConfigChanges`，跳过 `isExcluded` 检查。

### 影响文件
- `src/main/java/com/sodlinken/jindexer/indexer/EventFileWatcher.java`

---

## 四、get_call_graph caller 显示 "unknown"

### 问题描述
`get_call_graph` 工具返回的 `callers` 列表中 `file` 字段显示 "unknown"。

### 原因分析
调用图查询 SQL 中，caller 信息的 `file_path` 字段可能为空或未正确关联。

### 修复方案
检查 `StorageService.getCallGraph()` 的 SQL 查询，确保 caller 的 `file_path` 正确关联。

### 影响文件
- `src/main/java/com/sodlinken/jindexer/storage/StorageService.java`

---

## 五、get_type_hierarchy parents 返回自身

### 问题描述
`get_type_hierarchy` 工具返回的 `parents` 列表包含自身类名，而非父类。

### 原因分析
类型层次查询逻辑可能将当前类也加入了 parents 列表。

### 修复方案
检查 `StorageService.findTypeHierarchy()` 方法，确保 parents 只包含直接父类和接口。

### 影响文件
- `src/main/java/com/sodlinken/jindexer/storage/StorageService.java`

---

## 六、search_symbols 通配符不工作

### 问题描述
`search_symbols` 工具使用 `*Service` 查询时返回空结果。

### 原因分析
通配符查询可能未正确转换为 SQL LIKE 模式。

### 修复方案
在 `callSearchSymbols()` 中，将 `*` 转换为 SQL 的 `%` 通配符。

### 影响文件
- `src/main/java/com/sodlinken/jindexer/mcp/McpServer.java`

---

## 七、index_status last_indexed_at 返回 "unknown"

### 问题描述
`index_status` 工具返回的 `last_indexed_at` 字段值为 "unknown"。

### 原因分析
索引完成时未正确更新 `index_metadata` 表中的 `last_indexed_at` 键值。

### 修复方案
在 `Indexer.index()` 方法完成时，调用 `storage.setIndexMetadata("last_indexed_at", ...)` 更新时间戳。

### 影响文件
- `src/main/java/com/sodlinken/jindexer/indexer/Indexer.java`

---

## 八、complexity_report 未计算复杂度值

### 问题描述
`complexity_report` 工具只返回方法列表，未计算实际的圈复杂度和认知复杂度值。

### 原因分析
`callComplexityReport()` 方法只查询符号列表，未调用 `ComplexityAnalyzer` 计算复杂度。

### 修复方案
在 `callComplexityReport()` 中，读取源文件并使用 `ComplexityAnalyzer` 计算每个方法的复杂度。

### 影响文件
- `src/main/java/com/sodlinken/jindexer/mcp/McpServer.java`

---

## 九、返回格式不统一

### 问题描述
- 部分工具返回 `total` 字段，部分不返回
- `line` 字段语义不一致（定义行号 vs 引用行号）
- 单项目模式下仍返回 `project: "default"`

### 修复方案
1. 所有工具统一返回 `total` 字段
2. `line` 字段统一为符号定义行号
3. 单项目模式下 `project` 字段返回项目名称而非 "default"

### 影响文件
- `src/main/java/com/sodlinken/jindexer/mcp/McpServer.java`

---

## 优先级排序

| 优先级 | 问题 | 影响 |
|--------|------|------|
| P0 | 工具未注册 | 功能不可用 |
| P0 | 配置热更新失效 | 核心功能不工作 |
| P1 | Indexer 实例不共享 | 并发安全隐患 |
| P1 | get_call_graph caller unknown | 数据不准确 |
| P1 | search_symbols 通配符不工作 | 查询功能受限 |
| P2 | get_type_hierarchy parents 返回自身 | 数据不准确 |
| P2 | index_status last_indexed_at unknown | 信息不完整 |
| P2 | complexity_report 未计算复杂度 | 功能不完整 |
| P3 | 返回格式不统一 | 用户体验差 |
