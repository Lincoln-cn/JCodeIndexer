# 配置详解

## 配置文件位置

```
<project-root>/.jindexer/config.yaml
```

## 完整配置示例

```yaml
# 项目根目录（CLI --project-root 覆盖）
project_root: /path/to/project

# 数据目录（存放 SQLite 数据库）
data_dir: .jindexer

# 数据库文件名
db_name: index.db

# 索引线程数
indexing_threads: 4

# 最大文件大小 (KB)，超过此大小的文件跳过
max_file_size_kb: 512

# 是否提取 Javadoc
extract_javadoc: true

# 是否跟随符号链接
follow_symlinks: false

# 排除目录（glob 模式）
exclude_dirs:
  - "**/target/**"
  - "**/build/**"
  - "**/node_modules/**"
  - "**/.git/**"

# 排除文件模式
exclude_files:
  - "**/*.test.java"
  - "**/*.spec.java"

# 多项目模式
projects:
  - name: backend
    root: /path/to/backend
  - name: frontend
    root: /path/to/frontend
```

---

## 配置项说明

### 基础配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `project_root` | string | `.` | 项目根目录路径 |
| `data_dir` | string | `.jindexer` | 数据存储目录 |
| `db_name` | string | `index.db` | SQLite 数据库文件名 |

### 索引配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `indexing_threads` | int | `4` | 并行索引线程数 |
| `max_file_size_kb` | int | `512` | 最大文件大小 (KB) |
| `extract_javadoc` | bool | `true` | 是否提取 Javadoc 注释 |
| `follow_symlinks` | bool | `false` | 是否跟随符号链接 |

### 排除规则

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `exclude_dirs` | list | `[]` | 排除的目录模式 |
| `exclude_files` | list | `[]` | 排除的文件模式 |

支持的 glob 模式：
- `**` — 匹配任意层级目录
- `*` — 匹配单层目录或文件名
- `?` — 匹配单个字符

### 多项目配置

| 配置项 | 类型 | 说明 |
|--------|------|------|
| `projects` | list | 项目列表 |
| `projects[].name` | string | 项目名称 |
| `projects[].root` | string | 项目根目录 |

---

## 环境变量覆盖

所有配置项都支持环境变量覆盖，格式为 `JINDEXER_<配置项大写>`：

```bash
# 覆盖项目根目录
export JINDEXER_PROJECT_ROOT=/path/to/project

# 覆盖数据目录
export JINDEXER_DATA_DIR=/data/index

# 覆盖索引线程数
export JINDEXER_INDEXING_THREADS=8

# 启动 MCP Server
java -jar java-code-indexer.jar
```

---

## CLI 参数覆盖

CLI 参数优先级最高，会覆盖配置文件和环境变量：

```bash
# 覆盖项目根目录
java -jar java-code-indexer.jar --project-root /path/to/project --index

# 覆盖数据目录
java -jar java-code-indexer.jar --data-dir /data/index --index
```

---

## 配置优先级

```
CLI 参数 > 环境变量 > 配置文件 > 默认值
```

---

## 多项目模式

### 配置方式

```yaml
projects:
  - name: backend
    root: /path/to/backend
  - name: frontend
    root: /path/to/frontend
```

### MCP 工具调用

```json
{
  "tool": "find_symbol",
  "arguments": {
    "query": "UserService",
    "project": "backend"
  }
}
```

不指定 `project` 参数时，使用默认项目（配置文件中的第一个项目）。

### 数据隔离

每个项目使用独立的 SQLite 数据库，存储在各自项目的 `.jindexer/` 目录下。

---

## 常见配置场景

### 大型项目

```yaml
indexing_threads: 8
max_file_size_kb: 1024
exclude_dirs:
  - "**/target/**"
  - "**/build/**"
  - "**/generated/**"
```

### 微服务项目

```yaml
projects:
  - name: user-service
    root: /path/to/user-service
  - name: order-service
    root: /path/to/order-service
  - name: gateway
    root: /path/to/gateway
```

### CI/CD 环境

```bash
# 使用环境变量配置
export JINDEXER_PROJECT_ROOT=$GITHUB_WORKSPACE
export JINDEXER_DATA_DIR=$GITHUB_WORKSPACE/.jindexer
export JINDEXER_INDEXING_THREADS=2

java -jar java-code-indexer.jar --index
```
