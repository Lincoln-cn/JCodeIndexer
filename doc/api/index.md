# API 参考

## 支持的语言

| 语言 | 状态 | 说明 |
|------|------|------|
| Java | ✅ 完全支持 | 使用 JavaParser 解析 |
| Kotlin | ✅ 完全支持 | 使用 KotlinParserAdapter 解析 |
| Scala | ✅ 完全支持 | 使用 ScalaParserAdapter 解析 |

## MCP 工具列表

| 工具 | 说明 |
|------|------|
| `find_symbol` | 按名称查找符号（类/方法/字段），支持 `*` 通配符 |
| `find_references` | 查找符号的所有引用位置 |
| `get_call_graph` | 获取方法的调用图（调用者/被调用者） |
| `search_code` | 搜索符号名和代码内容（FTS5 全文搜索） |
| `get_file_info` | 获取文件的符号/代码块/调用信息 |
| `search_config` | 搜索配置文件（YAML / Properties / .env） |
| `find_dependencies` | 搜索项目依赖（Maven / Gradle） |
| `health` | 服务器健康检查和统计信息 |
| `list_projects` | 列出已索引项目（多项目模式） |
| `search_all_projects` | 跨所有项目搜索（多项目模式） |
| `find_implementations` | 查找接口的所有实现类 |
| `find_overrides` | 查找方法的所有重写 |
| `find_usages` | 查找字段的所有使用位置 |
| `find_annotations` | 查找符号的所有注解 |
| `find_by_annotation` | 查找带特定注解的符号 |
| `find_api_routes` | 查找 API 路由映射（URL → Controller） |
| `find_route` | 根据 HTTP 方法 + URL 路径查找 Controller 方法 |
| `get_type_hierarchy` | 获取类的完整继承层次结构 |
| `get_bean_dependencies` | 查找 Bean 的依赖 |
| `get_bean_dependents` | 查找依赖该 Bean 的其他 Bean |
| `find_related_tests` | 查找与源代码相关的测试类 |
| `reindex` | 触发项目重新索引 |
| `index_status` | 获取索引状态和统计信息 |
| `search_symbols` | 高级符号搜索，支持类型/注解过滤 |
| `get_code_metrics` | 获取代码度量（LOC、方法/字段数量） |

---

## 工具详情

### find_symbol

按限定名或名称查找符号。

**参数：**
- `query` (string, 必填) — 搜索关键词，支持 `*` 通配符返回全部
- `limit` (integer, 可选) — 最大返回数，默认 20
- `project` (string, 可选) — 目标项目名称（多项目模式）

**返回：**
```json
{
  "project": "default",
  "symbols": [
    {
      "id": 1,
      "name": "McpServer",
      "qualified_name": "com.sodlinken.jindexer.mcp.McpServer",
      "kind": "CLASS",
      "file": "src/main/java/.../McpServer.java",
      "line": 22,
      "signature": ""
    }
  ],
  "total": 1
}
```

---

### find_references

查找某个符号的所有引用位置。支持两种模式：按 `symbol_id` 精确查找，或按 `symbol_name` 模糊查找。

**参数：**
- `symbol_id` (integer) — 精确查找，按符号 ID
- `symbol_name` (string) — 模糊查找，按符号名称（支持 `*` 通配符）
- `limit` (integer, 可选) — 最大返回数，默认 50
- `project` (string, 可选) — 目标项目名称

**返回：**
```json
{
  "references": [
    {
      "id": 1,
      "symbol_id": 42,
      "from_file": "src/main/java/.../Main.java",
      "from_line": 15,
      "context": "import com.sodlinken.jindexer.mcp.McpServer;"
    }
  ],
  "total": 1
}
```

---

### get_call_graph

获取方法的调用图（调用者/被调用者）。

**参数：**
- `method_name` (string, 必填) — 方法全限定名（如 `com.example.MyClass.myMethod`）
- `direction` (string, 可选) — 方向：`callers` | `callees` | `both`，默认 `both`
- `project` (string, 可选) — 目标项目名称

**返回：**
```json
{
  "method": "com.example.MyClass.myMethod",
  "callers": [
    {
      "method": "com.example.Caller.call",
      "file": "src/main/java/.../Caller.java",
      "line": 25
    }
  ],
  "callees": [
    {
      "method": "com.example.Dao.save",
      "file": "src/main/java/.../Dao.java",
      "line": 10
    }
  ]
}
```

---

### search_code

结构化搜索：符号名 + 代码内容。使用 `*` 通配符可返回所有结果。

**参数：**
- `query` (string, 必填) — 搜索关键词
- `limit` (integer, 可选) — 最大返回数，默认 20
- `project` (string, 可选) — 目标项目名称

**返回：**
```json
{
  "symbols": [...],
  "chunks": [
    {
      "file": "src/main/java/.../McpServer.java",
      "name": "handleMessage",
      "type": "METHOD",
      "line": 152
    }
  ],
  "total_hits": 5,
  "query_time_ms": 12
}
```

---

### get_file_info

获取指定文件的符号、代码块和调用信息。

**参数：**
- `file_path` (string, 必填) — 文件相对路径
- `project` (string, 可选) — 目标项目名称

**返回：**
```json
{
  "file": "src/main/java/.../McpServer.java",
  "symbol_count": 15,
  "chunk_count": 12,
  "symbols": [
    {
      "name": "McpServer",
      "kind": "CLASS",
      "line": 22,
      "signature": ""
    }
  ],
  "chunks": [
    {
      "type": "METHOD",
      "name": "start",
      "line": 77
    }
  ]
}
```

---

### search_config

搜索配置文件中的 key-value（YAML / Properties / .env）。

**参数：**
- `query` (string, 必填) — 搜索关键词（匹配 key / value / content）
- `config_type` (string, 可选) — 配置类型过滤：`YAML` | `PROPERTIES` | `ENV`
- `limit` (integer, 可选) — 最大返回数，默认 20
- `project` (string, 可选) — 目标项目名称

**返回：**
```json
{
  "config_entries": [
    {
      "id": 1,
      "file": "application.yml",
      "line": 5,
      "key": "spring.datasource.url",
      "value": "jdbc:mysql://localhost:3306/db",
      "type": "YAML",
      "content": "spring:\n  datasource:\n    url: jdbc:mysql://..."
    }
  ],
  "total": 1
}
```

---

### find_dependencies

查找项目依赖（Maven POM / Gradle）。省略 `query` 或传 `*` 可返回所有依赖。

**参数：**
- `query` (string, 可选) — 搜索关键词（匹配 artifactId / groupId / version），省略返回所有
- `dep_type` (string, 可选) — 依赖类型过滤：`POM` | `GRADLE`
- `limit` (integer, 可选) — 最大返回数，默认 20
- `project` (string, 可选) — 目标项目名称

**返回：**
```json
{
  "dependencies": [
    {
      "id": 1,
      "file": "pom.xml",
      "line": 10,
      "group_id": "org.xerial",
      "artifact_id": "sqlite-jdbc",
      "version": "3.49.1.0",
      "scope": "compile",
      "type": "POM",
      "classifier": ""
    }
  ],
  "total": 1
}
```

---

### list_projects

列出所有已索引的项目及其状态。仅在多项目模式下可用。

**参数：** 无

**返回：**
```json
{
  "projects": [
    {
      "name": "backend",
      "root": "/path/to/backend",
      "data_dir": "/path/to/backend/.jindexer"
    }
  ],
  "total": 1,
  "default_project": "backend"
}
```

---

### search_all_projects

跨所有项目搜索符号。仅在多项目模式下可用。

**参数：**
- `query` (string, 必填) — 搜索关键词
- `limit` (integer, 可选) — 每个项目最大返回数，默认 10

**返回：**
```json
{
  "results": {
    "backend": [
      {
        "id": 1,
        "name": "UserService",
        "qualified_name": "com.example.UserService",
        "kind": "CLASS",
        "file": "src/main/java/.../UserService.java"
      }
    ],
    "frontend": []
  },
  "total_hits": 1,
  "projects_searched": 2
}
```

---

### health

健康检查，返回系统状态和索引统计信息。

**参数：** 无

**返回：**
```json
{
  "status": "healthy",
  "uptime_seconds": 3600,
  "projects": {
    "default": {
      "symbols": 1234,
      "chunks": 5678,
      "files_indexed": 100,
      "db_size_mb": 12.5
    }
  }
}
```

---

### find_implementations

查找接口的所有实现类（支持直接和间接实现）。

**参数：**
- `interface_name` (string, 必填) — 接口名称或限定名
- `limit` (integer, 可选) — 最大返回数，默认 20
- `project` (string, 可选) — 目标项目名称

**返回：**
```json
{
  "project": "default",
  "interface": "Serializable",
  "implementations": [
    {
      "id": 1,
      "name": "UserDto",
      "qualified_name": "com.example.UserDto",
      "file": "src/main/java/.../UserDto.java",
      "line": 5,
      "super_class": "",
      "interfaces": ["java.io.Serializable"]
    }
  ],
  "total": 1
}
```

---

### find_overrides

查找方法的所有重写实现（包括子类重写）。

**参数：**
- `method_name` (string, 必填) — 方法名
- `class_name` (string, 必填) — 父类限定名
- `limit` (integer, 可选) — 最大返回数，默认 20
- `project` (string, 可选) — 目标项目名称

**返回：**
```json
{
  "project": "default",
  "method": "save",
  "parent_class": "BaseService",
  "overrides": [
    {
      "id": 1,
      "name": "save",
      "qualified_name": "com.example.UserService.save",
      "file": "src/main/java/.../UserService.java",
      "line": 10,
      "signature": "void save()",
      "return_type": "void"
    }
  ],
  "total": 1
}
```

---

### find_usages

查找字段/变量的所有使用位置。

**参数：**
- `field_name` (string, 必填) — 字段限定名
- `limit` (integer, 可选) — 最大返回数，默认 50
- `project` (string, 可选) — 目标项目名称

**返回：**
```json
{
  "project": "default",
  "field": "com.example.UserService.name",
  "usages": [
    {
      "id": 1,
      "file": "src/main/java/.../Controller.java",
      "line": 20,
      "context": "userService.getName()"
    }
  ],
  "total": 1
}
```
