# 故障排查

## 常见问题

### 1. 启动失败：Java 版本不兼容

**错误信息：**
```
UnsupportedClassVersionError: com/sodlinken/jindexer/cli/CliMain has been compiled by a more recent version of the Java Runtime
```

**解决方案：**
- 需要 Java 21 或更高版本
- 检查版本：`java -version`
- 升级 JDK：https://adoptium.net/

---

### 2. 索引失败：数据库锁定

**错误信息：**
```
SQLITE_BUSY: The database file is locked
```

**解决方案：**
- 确保没有其他进程在使用数据库
- 重启 MCP Server
- 删除 `.jindexer/index.db` 后重新索引

---

### 3. 索引失败：权限不足

**错误信息：**
```
java.nio.file.AccessDeniedException
```

**解决方案：**
- 检查项目目录的读取权限
- 检查 `.jindexer/` 目录的写入权限
- 使用 `chmod` 或以管理员身份运行

---

### 4. MCP 工具调用超时

**症状：**
AI 助手调用工具后长时间无响应

**解决方案：**
- 检查索引是否完成：`java -jar java-code-indexer.jar --status`
- 减少 `max_file_size_kb` 配置
- 增加 `indexing_threads` 配置

---

### 5. 搜索结果为空

**可能原因：**
1. 未执行索引
2. 索引未完成
3. 搜索关键词不匹配

**解决方案：**
```bash
# 检查索引状态
java -jar java-code-indexer.jar --status

# 重新索引
java -jar java-code-indexer.jar --index

# 直接搜索测试
java -jar java-code-indexer.jar --search "关键词"
```

---

### 6. 配置文件不生效

**检查清单：**
1. 配置文件路径是否正确：`<project-root>/.jindexer/config.yaml`
2. YAML 语法是否正确
3. 环境变量是否覆盖了配置
4. CLI 参数是否覆盖了配置

**调试方法：**
```bash
# 查看当前配置
java -jar java-code-indexer.jar --status

# 使用环境变量覆盖
export JINDEXER_PROJECT_ROOT=/path/to/project
```

---

### 7. 多项目模式问题

**症状：**
切换项目后数据混乱

**解决方案：**
- 确保每个项目有独立的 `.jindexer/` 目录
- 使用 `project` 参数指定目标项目
- 检查配置文件中的 `projects` 列表

---

### 8. 内存不足

**错误信息：**
```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案：**
```bash
# 增加 JVM 堆内存
java -Xmx2g -jar java-code-indexer.jar --index

# 或减少索引线程数
# config.yaml
indexing_threads: 2
```

---

## 调试模式

### 启用详细日志

```bash
# 设置日志级别
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug \
     -jar java-code-indexer.jar --index
```

### 检查数据库内容

```bash
# 使用 sqlite3 命令行工具
sqlite3 .jindexer/index.db

# 查看表结构
.tables
.schema symbols

# 查看数据
SELECT COUNT(*) FROM symbols;
SELECT * FROM symbols LIMIT 5;
```

---

## 性能问题

### 索引速度慢

**优化建议：**
1. 增加 `indexing_threads`（建议 CPU 核心数的 1-2 倍）
2. 减少 `max_file_size_kb`
3. 配置 `exclude_dirs` 排除不需要的目录
4. 确保磁盘 I/O 性能

### 搜索速度慢

**优化建议：**
1. 确保数据库已建立索引（默认已创建）
2. 减少 `limit` 参数
3. 使用更精确的搜索关键词

---

## 日志分析

### 日志位置

日志输出到 stderr，可通过重定向保存：

```bash
java -jar java-code-indexer.jar --index 2> jindexer.log
```

### 关键日志

- `开始增量索引` — 索引开始
- `扫描到 N 个文件` — 文件扫描完成
- `索引完成` — 索引结束
- `MCP Server 已启动` — MCP 服务就绪

---

## 获取帮助

如果以上方法都无法解决问题：

1. 查看 GitHub Issues：https://github.com/Lincoln-cn/JCodeIndexer/issues
2. 提交 Issue 时请包含：
   - 错误信息
   - Java 版本
   - 操作系统
   - 配置文件内容
   - 日志输出
