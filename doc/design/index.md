# 设计文档

完整的架构设计文档请参考 `innerdoc/design/java-code-indexer-design-v3.0-release.md`。

## 架构概览

```
AI 助手 (Qwen Code / Claude)
    │
    ▼ MCP Protocol (JSON-RPC over stdio)
┌─────────────────────────────┐
│         McpServer           │
├─────────────────────────────┤
│      ToolDispatcher         │
│  ┌─────┬─────┬───────────┐  │
│  │find │find_|get_        │  │
│  │sym  │ref  │call_graph │  │
│  │...  │...  │...        │  │
│  └─────┴─────┴───────────┘  │
├─────────────────────────────┤
│     StructuredSearch        │
│   (SQL Query Builder)       │
├─────────────────────────────┤
│      StorageService         │
│   (JDBC + SQLite WAL)       │
├─────────────────────────────┤
│     IndexerEngine           │
│  JavaParser + SymbolVisitor │
│  Parallel (8 threads)      │
│  Incremental (SHA1 diff)   │
└─────────────────────────────┘
```

## 数据库 Schema

9 张表：`symbols`, `code_references`, `calls`, `chunks`, `file_meta`, `embedding_cache`, `embedding_failures`, `config_entries`, `dependencies`

详见设计文档 §7.2。
