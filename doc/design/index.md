# 架构设计

## 架构概览

```
AI 助手 (Qwen Code / Claude / Cursor)
    │
    ▼ MCP Protocol (JSON-RPC over stdio)
┌─────────────────────────────────────────┐
│              McpServer                  │
│  ┌──────────────────────────────────┐   │
│  │  ToolDispatcher (26 tools)       │   │
│  │  find_symbol | find_references   │   │
│  │  get_call_graph | search_code    │   │
│  │  get_file_info | search_config   │   │
│  │  find_dependencies | reindex     │   │
│  │  list_projects | index_status    │   │
│  │  search_all_projects | health    │   │
│  │  search_symbols | get_code_metrics│  │
│  └──────────────────────────────────┘   │
├─────────────────────────────────────────┤
│  SearchProvider                         │
│  └─ StructuredSearch (FTS5)             │
├─────────────────────────────────────────┤
│  StorageService (JDBC + SQLite WAL)     │
├─────────────────────────────────────────┤
│  Indexer (Incremental, SHA-1 diff)      │
│  ├─ JavaParser → symbols/refs/calls     │
│  ├─ Chunker → code blocks               │
│  ├─ PomParser / GradleParser → deps     │
│  └─ ConfigFileParser → config entries   │
└─────────────────────────────────────────┘
```

## 模块说明

| 模块 | 路径 | 职责 |
|------|------|------|
| `cli/` | CLI 入口 | 参数解析，启动 MCP server 或执行索引 |
| `mcp/` | MCP server | JSON-RPC over stdio，注册 26 个工具 |
| `config/` | 配置加载 | YAML 配置 + 环境变量覆盖 |
| `storage/` | 存储层 | SQLite schema + CRUD + FTS5 |
| `indexer/` | 索引引擎 | 增量索引，SHA-1 哈希跳过 |
| `parser/` | 解析器 | Java/POM/Gradle/Config 文件解析 |
| `chunker/` | 代码切片 | 按类/方法粒度切分代码块 |
| `search/` | 搜索提供者 | FTS5 全文搜索 |
| `model/` | 数据模型 | Symbol, Call, Chunk 等 record |
| `util/` | 工具类 | SHA-1 哈希等 |

## 数据库 Schema

SQLite 数据库（WAL 模式），13 张核心表：

### symbols
存储类、方法、字段的定义信息。

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | INTEGER PK | 自增主键 |
| `file_path` | TEXT | 文件相对路径 |
| `start_line` / `end_line` | INTEGER | 起止行号 |
| `kind` | TEXT | CLASS / METHOD / FIELD |
| `name` | TEXT | 短名称 |
| `qualified_name` | TEXT | 全限定名 |
| `signature` | TEXT | 方法签名 |
| `return_type` | TEXT | 返回类型 |
| `parent_class` | TEXT | 所属类 |
| `modifiers` | INTEGER | 修饰符位掩码 |
| `javadoc` | TEXT | Javadoc 内容 |

### code_references
符号的使用位置。

| 列 | 类型 | 说明 |
|----|------|------|
| `symbol_id` | INTEGER FK | 引用的符号 ID |
| `from_file` | TEXT | 引用所在文件 |
| `from_line` | INTEGER | 引用行号 |
| `context` | TEXT | 上下文代码片段 |

### calls
方法调用关系。

| 列 | 类型 | 说明 |
|----|------|------|
| `caller_method` | TEXT | 调用者方法全限定名 |
| `caller_file` / `caller_line` | TEXT/INT | 调用者位置 |
| `callee_method` | TEXT | 被调用者方法全限定名 |
| `callee_file` | TEXT | 被调用者文件（可能为空） |

### chunks
代码块（类/方法级别的代码切片）。

| 列 | 类型 | 说明 |
|----|------|------|
| `file_path` | TEXT | 文件相对路径 |
| `type` | TEXT | CLASS / METHOD / ANNOTATION / FILE_HEADER |
| `start_line` / `end_line` | INTEGER | 起止行号 |
| `name` | TEXT | 块名称（方法名/类名） |
| `content` | TEXT | 源代码内容 |
| `package_name` / `class_name` / `signature` | TEXT | 元数据 |

### file_meta
文件元信息（用于增量索引）。

| 列 | 类型 | 说明 |
|----|------|------|
| `file_path` | TEXT PK | 文件相对路径 |
| `sha1` | TEXT | 内容 SHA-1 哈希 |
| `last_indexed_at` | INTEGER | 上次索引时间戳 |

### config_entries
配置文件中的 key-value 对（YAML / Properties / .env）。

### dependencies
项目依赖（Maven POM / Gradle）。

## 索引策略

### 增量索引流程

1. **扫描文件**：遍历项目目录，按类型分类（Java / Config / POM / Gradle）
2. **SHA-1 比对**：对比 `file_meta.sha1`，仅处理变更文件
3. **删除过期**：移除已不存在的文件数据
4. **并行解析**：Virtual Threads 并行解析变更文件
5. **Diff 更新**：按 qualifiedName+kind 做符号 diff，仅增删变化项

### Diff 策略

- **符号**：按 `qualifiedName + kind` 做 diff
- **代码块**：按 `type + className + name` 做 diff
- **引用/调用**：全量删除重插（依赖 symbol ID）
- **配置**：按 `filePath + key` 做 diff
- **依赖**：按 `groupId + artifactId` 做 diff

## 多项目模式

支持同时索引多个项目：

```yaml
projects:
  - name: backend
    root: /path/to/backend
  - name: frontend
    root: /path/to/frontend
```

每个项目使用独立的 SQLite 数据库，MCP 工具通过 `project` 参数路由到对应项目。

## MCP 协议

- 传输：stdio（JSON-RPC 2.0）
- 帧格式：`Content-Length: N\r\n\r\n{json}` 或裸 JSON 行
- 工具注册：`tools/list` 返回 26 个工具定义
- 工具调用：`tools/call` 路由到对应实现
