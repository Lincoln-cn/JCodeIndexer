# Java Code Indexer

Java 项目的 MCP 代码索引工具，基于 JavaParser + SQLite，为 AI 编程助手提供结构化代码搜索能力。

## 功能概览

- **符号索引**：自动提取类、方法、字段定义
- **调用关系**：方法级调用图构建
- **代码块提取**：按类/方法切分代码，支持内容搜索
- **多项目支持**：独立数据库，互不干扰
- **MCP 协议**：9 个工具，支持 Qwen Code / Claude 等 AI 助手

## 快速开始

查看 [快速入门](getting-started/index.md) 了解安装和配置方法。

## 文档结构

| 目录 | 内容 |
|------|------|
| [快速入门](getting-started/index.md) | 安装、配置、基本使用 |
| [设计文档](design/index.md) | 架构设计、Schema、MCP 工具 |
| [API 参考](api/index.md) | MCP 工具接口文档 |
