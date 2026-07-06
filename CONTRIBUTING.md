# Contributing to Java Code Indexer

[English](CONTRIBUTING.md) | [中文](#中文)

---

## Getting Started

### Prerequisites

- **Java 21** (JDK)
- **Maven 3.8+**
- **Git**

### Build & Run

```bash
git clone https://github.com/sodlinken/java-code-indexer.git
cd java-code-indexer
mvn package -q -DskipTests
```

### Run Tests

```bash
mvn test
```

### Project Layout

```
src/main/java/com/sodlinken/jindexer/
├── cli/          # CLI entry point
├── mcp/          # MCP server (JSON-RPC over stdio)
├── config/       # YAML config loader
├── storage/      # SQLite schema & StorageService
├── indexer/      # Incremental indexing engine
├── parser/       # Java, POM, Gradle, Config parsers
├── chunker/      # Code chunking
├── search/       # Structured search provider
├── model/        # Data models
└── util/         # Utilities
```

## Development Workflow

1. **Fork** the repository on GitHub
2. **Create a branch** from `main` for your change
3. **Make your changes** following the code style below
4. **Add or update tests** for any new functionality
5. **Run `mvn test`** and ensure all tests pass
6. **Submit a Pull Request** with a clear description

## Code Style

- Follow standard Java conventions (Google Java Style as reference)
- Use meaningful variable/method names
- Add Javadoc for public APIs
- Keep methods focused and short
- Use Java 21 features where appropriate (virtual threads, pattern matching, switch expressions)

## Commit Messages

- Use imperative mood ("Add feature" not "Added feature")
- Keep the subject line under 72 characters
- Reference issue numbers when applicable (e.g., `Fix #42`)

## Reporting Issues

- Use GitHub Issues for bug reports and feature requests
- Include steps to reproduce for bugs
- Specify your Java version and OS

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).

---

# 中文

## 快速开始

### 环境要求

- **Java 21** (JDK)
- **Maven 3.8+**
- **Git**

### 构建与运行

```bash
git clone https://github.com/sodlinken/java-code-indexer.git
cd java-code-indexer
mvn package -q -DskipTests
```

### 运行测试

```bash
mvn test
```

## 开发流程

1. 在 GitHub 上 **Fork** 仓库
2. 基于 `main` 分支 **创建特性分支**
3. 按照下方代码规范 **编写代码**
4. 为新功能 **添加或更新测试**
5. 运行 `mvn test` 确保所有测试通过
6. 提交 **Pull Request**，附上清晰的描述

## 代码规范

- 遵循标准 Java 编码规范（可参考 Google Java Style）
- 使用有意义的变量/方法名
- 为公开 API 添加 Javadoc
- 保持方法短小、职责单一
- 适当使用 Java 21 特性（虚拟线程、模式匹配、switch 表达式）

## 提交规范

- 使用祈使语气（"Add feature" 而非 "Added feature"）
- 标题行不超过 72 个字符
- 关联 Issue 编号（如 `Fix #42`）

## 报告问题

- 使用 GitHub Issues 提交 Bug 报告或功能请求
- Bug 需包含复现步骤
- 请注明 Java 版本和操作系统

## 许可证

贡献即表示您同意您的贡献将按照 [Apache License 2.0](LICENSE) 进行许可。
