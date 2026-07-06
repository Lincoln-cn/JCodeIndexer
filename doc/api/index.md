# API 参考

## MCP 工具列表

| 工具 | 说明 |
|------|------|
| `find_symbol` | 按限定名或名称查找符号（类/方法/字段） |
| `find_references` | 查找某个符号的所有引用位置 |
| `get_call_graph` | 获取方法的调用图（调用者/被调用者） |
| `get_file_info` | 获取指定文件的符号/代码块/调用信息 |
| `find_dependencies` | 查找项目依赖（Maven POM / Gradle） |
| `search_code` | 结构化搜索：符号名 + 代码内容 |
| `search_config` | 搜索配置文件中的 key-value |
| `semantic_search` | 语义搜索（需启用 Embedding） |
| `init_index` | 初始化/重建索引 |

## 工具详情

### find_symbol

按限定名或名称查找符号。

**参数：**
- `query` (string, 必填) — 搜索关键词，支持 `*` 通配符
- `kind` (string, 可选) — 符号类型过滤：`CLASS`, `METHOD`, `FIELD`
- `limit` (integer, 可选) — 最大返回数，默认 20

### find_references

查找某个符号的所有引用位置。

**参数：**
- `symbol_id` (integer) — 精确查找，按 symbol_id
- `symbol_name` (string) — 模糊查找，按 symbol_name
- `limit` (integer, 可选) — 最大返回数，默认 20

### search_code

结构化搜索：符号名 + 代码内容。

**参数：**
- `query` (string, 必填) — 搜索关键词，使用 `*` 返回全部结果
- `limit` (integer, 可选) — 最大返回数，默认 20
