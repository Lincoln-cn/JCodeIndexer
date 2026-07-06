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

创建 `config.yaml`：

```yaml
projects:
  - name: my-project
    path: /path/to/project
    language: JAVA
    data_dir: ~/.java-code-indexer-data/my-project

embedding:
  enabled: false
```

## 初始化索引

```bash
java -jar target/java-code-indexer-*-shaded.jar \
  --project-root /path/to/project \
  --index
```

## 在 Qwen Code 中使用

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
