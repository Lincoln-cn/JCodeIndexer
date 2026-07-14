# 贡献者指南

感谢您对 Java Code Indexer 的关注！

## 开发环境

### 前置要求

- JDK 21+
- Maven 3.8+
- Git

### 克隆项目

```bash
git clone https://github.com/Lincoln-cn/JCodeIndexer.git
cd java-code-indexer
```

### 构建项目

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 打包
mvn package -q -DskipTests
```

---

## 项目结构

```
src/main/java/com/sodlinken/jindexer/
├── cli/              # CLI 入口
├── mcp/              # MCP Server 实现
├── config/           # 配置加载
├── storage/          # SQLite 存储层
├── indexer/          # 增量索引引擎
├── parser/           # 文件解析器
│   ├── JavaParserAdapter.java
│   ├── PomParser.java
│   ├── GradleParser.java
│   └── ConfigFileParser.java
├── chunker/          # 代码切片器
├── search/           # 搜索提供者
├── model/            # 数据模型 (records)
└── util/             # 工具类

src/test/java/com/sodlinken/jindexer/
├── mcp/              # MCP 集成测试
├── storage/          # 存储层测试
├── parser/           # 解析器测试
├── chunker/          # 切片器测试
├── indexer/          # 索引器测试
├── search/           # 搜索测试
└── config/           # 配置测试
```

---

## 开发流程

### 1. 创建分支

```bash
git checkout -b feature/your-feature-name
```

### 2. 编写代码

- 遵循现有代码风格
- 为新功能编写测试
- 确保所有测试通过：`mvn test`

### 3. 提交代码

```bash
git add .
git commit -m "feat: add new feature description"
```

### 4. 推送分支

```bash
git push origin feature/your-feature-name
```

### 5. 创建 Pull Request

在 GitHub 上创建 PR，描述您的更改。

---

## 代码规范

### 命名规范

- 类名：PascalCase（如 `StorageService`）
- 方法名：camelCase（如 `insertSymbol`）
- 常量：UPPER_SNAKE_CASE（如 `MAX_CHUNK_TOKENS`）
- 包名：全小写（如 `com.sodlinken.jindexer`）

### 代码风格

- 使用 4 空格缩进
- 行长度限制 120 字符
- 使用 `var` 关键字简化局部变量声明
- 优先使用 records 作为数据载体

### 注释规范

- 公共 API 必须有 Javadoc
- 复杂逻辑需要行内注释
- 避免无意义的注释

---

## 测试规范

### 单元测试

- 测试类命名：`<被测试类名>Test.java`
- 测试方法命名：`<测试场景>` 或 `should_<行为>_when_<条件>`
- 使用 `@TempDir` 管理临时文件
- 每个测试方法只测试一个行为

### 集成测试

- 测试类命名：`<被测试类名>IntegrationTest.java`
- 使用真实数据库（临时目录）
- 测试完整的业务流程

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=StorageServiceTest

# 运行特定测试方法
mvn test -Dtest=StorageServiceTest#insertAndSearchSymbol
```

---

## 提交规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Type 类型

- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式调整（不影响功能）
- `refactor`: 重构（不影响功能）
- `test`: 测试相关
- `chore`: 构建/工具相关

### 示例

```
feat(storage): add bulk insert for symbols
fix(indexer): handle empty Java files gracefully
docs(api): update MCP tools documentation
test(parser): add GradleParser edge cases
```

---

## Issue 规范

### Bug 报告

请包含：
1. 问题描述
2. 复现步骤
3. 期望行为
4. 实际行为
5. 环境信息（Java 版本、操作系统）
6. 相关日志

### 功能请求

请包含：
1. 功能描述
2. 使用场景
3. 期望的 API/行为

---

## 发布流程

### 版本号

使用 [Semantic Versioning](https://semver.org/)：
- MAJOR: 不兼容的 API 变更
- MINOR: 向后兼容的功能新增
- PATCH: 向后兼容的 Bug 修复

### 发布步骤

1. 更新 `pom.xml` 版本号
2. 更新 `CHANGELOG.md`
3. 创建 Git tag：`git tag v1.0.0`
4. 推送 tag：`git push origin v1.0.0`
5. GitHub Actions 自动构建并发布 Release

---

## 许可证

本项目使用 Apache License 2.0。贡献代码即表示您同意将代码置于相同许可证下。
