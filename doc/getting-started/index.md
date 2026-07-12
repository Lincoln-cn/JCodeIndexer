# 快速入门

## 安装

### 构建（需要 JDK 21）

```bash
# 构建
mvn package -q -DskipTests

# 运行 MCP server
java -jar target/java-code-indexer-*-shaded.jar --project-root /path/to/project
```

## 配置

创建 `.jindexer/config.yaml`（可选）：

```yaml
project_root: /path/to/project
data_dir: .jindexer
threads: 4
```

## 初始化索引

```bash
java -jar target/java-code-indexer-*-shaded.jar \
  --project-root /path/to/project \
  --init
```

## 运行索引

```bash
java -jar target/java-code-indexer-*-shaded.jar \
  --project-root /path/to/project \
  --index
```

## 在 AI 助手中使用

### Qwen Code / Claude Desktop / Cursor

在 MCP 配置中添加 server：

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

## 多项目模式

```yaml
# .jindexer/config.yaml
projects:
  - name: backend
    root: /path/to/backend
  - name: frontend
    root: /path/to/frontend
```
