# Java Code Indexer

> 面向 AI 编程助手的 MCP Java 代码索引服务器。

[中文](README.zh-CN.md) | [English](README.md)

## 概述

Java Code Indexer 在 AST 级别分析 Java 源码，将符号、调用关系、依赖和配置信息存储到嵌入式 SQLite 数据库，并通过 JSON-RPC over stdio 暴露为 9 个 MCP 工具。AI 编程助手（Qwen Code、Claude、Cursor 等）可使用这些工具来导航和理解你的 Java 项目。

**核心特性：**

- **9 个 MCP 工具** — 符号查找、引用搜索、调用图、代码搜索、配置搜索、依赖搜索等
- **增量索引** — SHA-1 变更检测，仅重新解析修改过的文件
- **多项目支持** — 每个项目独立的 SQLite 数据库
- **Java 21 虚拟线程** — 并行文件解析，快速索引
- **多格式解析** — Java (JavaParser)、Maven POM、Gradle、YAML/Properties/.env

## 快速开始

### 从源码构建

需要 **Java 21** 和 **Maven 3.8+**。

```bash
git clone https://github.com/sodlinken/java-code-indexer.git
cd java-code-indexer
mvn package -q -DskipTests
```

构建产物为 `target/java-code-indexer-0.1.0-SNAPSHOT-shaded.jar`。

### 索引项目

```bash
java -jar target/java-code-indexer-*-shaded.jar \
  --project-root /path/to/your/java-project \
  --index
```

### 启动 MCP 服务

```bash
java -jar target/java-code-indexer-*-shaded.jar \
  --project-root /path/to/your/java-project
```

## MCP 配置

将服务器添加到你的 AI 编程助手 MCP 配置中。

### Qwen Code

```json
{
  "mcpServers": {
    "java-code-indexer": {
      "command": "java",
      "args": ["-jar", "/path/to/java-code-indexer-*-shaded.jar", "--project-root", "/path/to/project"]
    }
  }
}
```

### Claude Desktop / Cursor

```json
{
  "mcpServers": {
    "java-code-indexer": {
      "command": "java",
      "args": ["-jar", "/path/to/java-code-indexer-*-shaded.jar", "--project-root", "/path/to/project"]
    }
  }
}
```

## CLI 用法

```
java -jar jindexer.jar [options]

Options:
  --project-root <path>   Java 项目根目录（默认：当前目录）
  --data-dir <path>       索引数据目录
  --init                  仅初始化数据库 schema
  --index                 运行索引器（提取符号/引用/调用关系）
  --help, -h              显示帮助

不带参数 → 启动 MCP 服务。
```

## 配置

在项目根目录创建 `.jindexer/config.yaml`：

```yaml
# 单项目模式
project_root: /path/to/project
data_dir: .jindexer
threads: 4
embedding:
  enabled: false
```

或配置多项目：

```yaml
# 多项目模式
data_dir: .jindexer
projects:
  - name: my-app
    root: /path/to/my-app
  - name: my-lib
    root: /path/to/my-lib
embedding:
  enabled: false
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `INDEXER_EMBEDDING_ENABLED` | 启用 Embedding API | `false` |
| `INDEXER_EMBEDDING_BASE_URL` | Embedding API 端点 | — |
| `INDEXER_EMBEDDING_MODEL` | Embedding 模型名称 | — |
| `INDEXER_THREADS` | 索引线程数 | `4` |
| `INDEXER_LOG_LEVEL` | 日志级别 | `INFO` |

## MCP 工具

| 工具 | 说明 |
|------|------|
| `find_symbol` | 按名称查找符号（类/方法/字段），支持 `*` 通配符 |
| `find_references` | 查找符号的所有引用位置 |
| `get_call_graph` | 获取方法调用图（调用者/被调用者/双向） |
| `search_code` | 搜索符号名和代码内容 |
| `get_file_info` | 获取文件详情（符号、代码块、调用关系） |
| `search_config` | 搜索配置文件（YAML/Properties/ENV） |
| `find_dependencies` | 搜索项目依赖（Maven/Gradle） |
| `semantic_search` | 语义搜索（需配置 Embedding，暂未启用） |
| `list_projects` | 列出已索引项目（仅多项目模式） |

## 项目结构

```
src/main/java/com/sodlinken/jindexer/
├── cli/          # CLI 入口 (CliMain)
├── mcp/          # MCP 服务器 (JSON-RPC over stdio)
├── config/       # YAML 配置加载器
├── storage/      # SQLite schema 和 StorageService
├── indexer/      # 增量索引引擎
├── parser/       # Java、POM、Gradle、Config 解析器
├── chunker/      # 代码切片（类/方法级别）
├── search/       # 结构化搜索提供者
├── model/        # 数据模型 (Symbol, Call, Chunk 等)
└── util/         # SHA-1 哈希工具
```

## 技术栈

- **Java 21** — 虚拟线程、模式匹配、switch 表达式
- **JavaParser 3.26** — Java 源码 AST 解析
- **SQLite** — 嵌入式数据库，WAL 模式
- **MCP 协议** — JSON-RPC over stdio (2024-11-05)
- **Gson** — JSON 序列化
- **OkHttp** — Embedding API HTTP 客户端
- **SnakeYAML** — YAML 配置解析
- **SLF4J + Logback** — 日志（输出到 stderr）

## 文档

完整文档请参考 MkDocs 站点，或直接浏览 `doc/` 目录：

- [快速入门](doc/getting-started/index.md)
- [架构设计](doc/design/index.md)
- [API 参考](doc/api/index.md)

## 发布方案

### Fat JAR（默认）

```bash
mvn package -q -DskipTests
# 产物：target/java-code-indexer-0.1.0-SNAPSHOT-shaded.jar
```

### Native Image（GraalVM）

需要安装 GraalVM JDK 21 及 `native-image`。

```bash
mvn package -q -DskipTests -Pnative
# 或手动构建：
native-image -jar target/java-code-indexer-*-shaded.jar \
  -o jindexer --no-fallback -H:+ReportExceptionStackTraces
```

生成单一原生二进制文件（约 30-50 MB），即时启动，运行时无需 JVM。

### Docker 镜像

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/java-code-indexer-*-shaded.jar /app/jindexer.jar
ENTRYPOINT ["java", "-jar", "/app/jindexer.jar"]
```

```bash
docker build -t java-code-indexer .
docker run --rm -v /path/to/project:/project java-code-indexer --project-root /project --index
```

## 贡献

参见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可证

[Apache License 2.0](LICENSE)
