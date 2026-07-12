# Java Code Indexer

> 基于 Java AST 级别的代码索引器，使用 SQLite 存储，通过 [MCP](https://modelcontextprotocol.io/) 协议为 AI 编程助手提供服务。

[中文](README.zh-CN.md) | [English](README.md)

---

## 为什么需要它

AI 编程助手探索一个不熟悉的 Java 项目时：

| 任务 | 没有 Java Code Indexer | 有了 Java Code Indexer |
|------|----------------------|----------------------|
| 找到 `UserService` 定义位置 | `grep` 100+ 文件（~15k tokens） | `find_symbol("UserService")`（~200 tokens） |
| 查找 `saveOrder()` 的所有调用者 | 逐个文件阅读（~8k tokens） | `get_call_graph("saveOrder", "callers")`（~500 tokens） |
| 查找 YAML/properties 中的配置值 | `find` + `cat` 多个配置文件（~5k tokens） | `search_config("spring.datasource.url")`（~200 tokens） |
| 定位项目所有依赖 | 打开 pom.xml + 传递依赖（~3k tokens） | `find_dependencies("spring-boot-starter")`（~300 tokens） |

---

## 安装

需要 **Java 21+**（JRE 或 JDK）。Docker 和 Native Image 方式已内置运行时。

**方式一：下载 JAR（推荐）**

```bash
# 从 GitHub Releases 下载（将 VERSION 替换为实际版本号，如 1.0.0）
curl -LO https://github.com/sodlinken/java-code-indexer/releases/latest/download/java-code-indexer-VERSION.jar
```

**方式二：从源码构建**（需要 Java 21+ 和 Maven 3.8+）

```bash
git clone https://github.com/sodlinken/java-code-indexer.git
cd java-code-indexer
mvn package -q -DskipTests
# 产物：target/java-code-indexer-*-shaded.jar
```

**方式三：Native Image（GraalVM）**

```bash
native-image -jar target/java-code-indexer-*-shaded.jar \
  -o jindexer --no-fallback
```

**方式四：Docker**

```bash
docker pull sodlinken/jcodeindexer:latest
```

---

## 快速开始

```bash
# 1. 索引你的 Java 项目
java -jar java-code-indexer-VERSION.jar --project-root /path/to/your/java-project --index

# 2. 启动 MCP 服务（stdio，适用于 Claude Code / Qwen Code / Cursor）
java -jar java-code-indexer-VERSION.jar --project-root /path/to/your/java-project
```

完成。你的 AI 助手现在可以直接查询代码库结构，而不需要逐个读取文件。

---

## CLI 参考

```
java -jar java-code-indexer-VERSION.jar [options]

Options:
  --project-root <path>   Java 项目根目录（默认：当前目录）
  --data-dir <path>       索引数据目录（默认：.jindexer）
  --init                  仅初始化数据库 schema
  --index                 运行索引器（提取符号/引用/调用关系）
  --status                显示索引统计信息
  --search <query>        直接搜索（无需启动 MCP 服务）
  --version               显示版本号
  --help, -h              显示帮助

不带参数 → 启动 MCP 服务（stdio）。
```

---

## MCP 工具

以 MCP 服务模式运行时，Java Code Indexer 提供以下工具：

| 工具 | 说明 |
|------|------|
| `find_symbol` | 按名称查找符号（类/方法/字段），支持 `*` 通配符 |
| `find_references` | 查找符号的所有引用位置 |
| `get_call_graph` | 方法调用图 — 调用者、被调用者、或双向 |
| `search_code` | 搜索符号名和代码内容 |
| `get_file_info` | 文件详情：符号、代码块、调用关系 |
| `search_config` | 搜索配置文件（YAML / Properties / .env） |
| `find_dependencies` | 搜索项目依赖（Maven / Gradle） |
| `list_projects` | 列出已索引项目（仅多项目模式） |

---

## 配置

在项目根目录创建 `.jindexer/config.yaml`（可选）：

```yaml
project_root: /path/to/project
data_dir: .jindexer
threads: 4
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `INDEXER_THREADS` | 索引线程数 | `4` |
| `INDEXER_LOG_LEVEL` | 日志级别 | `INFO` |

优先级：CLI 参数 → 环境变量 → 配置文件 → 默认值。

---

## AI 助手集成

### Qwen Code / Claude Desktop / Cursor

添加到你的 MCP 配置中：

```json
{
  "mcpServers": {
    "java-code-indexer": {
      "command": "/path/to/jdk-21/bin/java",
      "args": ["-jar", "/path/to/java-code-indexer-VERSION.jar", "--project-root", "/path/to/project"]
    }
  }
}
```

> **注意：** `command` 必须指向 Java 21+ 运行时。如果 `JAVA_HOME` 已指向 JDK 21，可使用 `"${JAVA_HOME}/bin/java"`。

---

## 工作原理

```
┌──────────────┐     MCP (stdio)     ┌──────────────────────┐
│  AI 编程助手  │ ◄─────────────────► │  Java Code Indexer   │
│  (Qwen/Claude)│    JSON-RPC         │                      │
└──────────────┘                     │  ┌────────────────┐  │
                                     │  │ JavaParser AST │  │
                                     │  └───────┬────────┘  │
                                     │          ▼            │
                                     │  ┌────────────────┐  │
                                     │  │  SQLite (WAL)  │  │
                                     │  │  symbols       │  │
                                     │  │  references    │  │
                                     │  │  call_graphs   │  │
                                     │  │  configs       │  │
                                     │  │  dependencies  │  │
                                     │  └────────────────┘  │
                                     └──────────────────────┘
```

### 索引策略

1. 文件系统遍历 Java 源文件
2. SHA-1 内容哈希跳过未修改文件（增量索引）
3. JavaParser AST 提取符号、引用和调用关系
4. POM/Gradle 解析器提取依赖信息
5. 配置解析器提取 YAML/Properties/.env 条目
6. 所有数据 upsert 到嵌入式 SQLite（WAL 模式）
7. FTS5 全文搜索索引通过触发器自动同步

### 数据库 Schema

七张核心表：

- **`symbols`** — 类、方法、字段，含位置和签名
- **`references`** — 符号在整个代码库中的使用位置
- **`calls`** — 方法调用关系（调用者 → 被调用者）
- **`chunks`** — 类/方法粒度的代码切片
- **`file_meta`** — 用于增量索引的 SHA-1 哈希
- **`config_entries`** — YAML/Properties/.env 配置条目
- **`dependencies`** — Maven/Gradle 依赖声明

另外还有 FTS5 全文搜索表（`symbols_fts`、`chunks_fts`），通过触发器自动同步。

---

## 项目结构

```
src/main/java/com/sodlinken/jindexer/
├── cli/          # CLI 入口
├── mcp/          # MCP 服务 (JSON-RPC over stdio)
├── config/       # YAML 配置加载器
├── storage/      # SQLite schema 和 StorageService（含 FTS5）
├── indexer/      # 增量索引引擎
├── parser/       # Java、POM、Gradle、Config 解析器
├── chunker/      # 代码切片（类/方法级别）
├── search/       # 结构化搜索（FTS5 全文搜索）
├── model/        # 数据模型 (Symbol, Call, Chunk 等)
└── util/         # SHA-1 哈希工具
```

---

## 发布方式

### GitHub Release

发布流程通过 GitHub Actions 自动化，产物包括：

| 产物 | 说明 |
|------|------|
| `java-code-indexer-VERSION.jar` | Fat JAR，需 Java 21+ |
| `java-code-indexer-VERSION-linux-amd64.tar.gz` | Linux amd64 Native Image |
| `java-code-indexer-VERSION-darwin-arm64.tar.gz` | macOS arm64 Native Image |
| `docker-image-VERSION.tar.gz` | Docker 离线镜像 |
| `checksums-VERSION.sha256` | SHA-256 校验和 |

### 本地构建

```bash
# 构建 Fat JAR + Native Image
./script/release.sh

# 指定版本号
./script/release.sh -v 1.0.0

# 跳过 Native Image
./script/release.sh -v 1.0.0 --skip-native
```

### Fat JAR

构建产物位于 `release/<version>/java-code-indexer-<version>.jar`，需 Java 21+。

```bash
java -jar release/1.0.0/java-code-indexer-1.0.0.jar --project-root /path/to/project
```

### Native Image (GraalVM)

构建产物位于 `release/<version>/jindexer`，无需 JVM，启动即时。

```bash
./release/1.0.0/jindexer --project-root /path/to/project --index
```

### Docker

```bash
# 本地构建并运行
docker build -t java-code-indexer .
docker run --rm -v /path/to/project:/project java-code-indexer --project-root /project --index

# 推送到私有 registry
DOCKER_REGISTRY=your-registry.com ./script/release.sh --push-docker
```

---

## 开发

```bash
# 克隆并构建
git clone https://github.com/sodlinken/java-code-indexer.git
cd java-code-indexer
mvn package -q -DskipTests

# 索引本项目
java -jar target/java-code-indexer-*-shaded.jar \
  --project-root . --index

# 启动 MCP 服务
java -jar target/java-code-indexer-*-shaded.jar --project-root .
```

### 发布流程

GitHub Actions 自动化发布流程：

1. 推送 `v*` 标签或手动触发
2. 构建 Fat JAR
3. 构建 Native Image（Linux amd64, macOS arm64）
4. 构建 Docker 镜像
5. 创建 GitHub Release 并上传产物

```bash
# 创建发布标签
git tag v1.0.0
git push origin v1.0.0
```

## 贡献

参见 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## 它不是什么

- 不是代码执行沙箱
- 不是测试运行器或 linter
- 不是 LSP/IDE 功能的替代品
- 不是 AI 生成的摘要（符号提取是确定性的）

## 许可证

[Apache License 2.0](LICENSE)
