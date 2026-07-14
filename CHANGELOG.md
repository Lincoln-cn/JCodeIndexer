# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-07-13

### Added
- **Kotlin 支持**: 完整的 Kotlin 语言支持
  - KotlinParserAdapter 解析 Kotlin 文件
  - 支持 class, object, data class, sealed class
  - 支持函数、属性、继承、接口实现
  - Indexer 自动扫描 .kt/.kts 文件
  - MCP 工具完全支持 Kotlin 代码
- **测试**: 26 个 Kotlin 相关测试
- **API 文档**: 新增支持语言说明

### Testing
- 272 个测试全部通过

## [1.1.5] - 2026-07-13

### Added
- **Kotlin 集成测试**: 3 个端到端 Kotlin 项目测试
  - 完整 Kotlin 项目索引
  - Java/Kotlin 混合项目
  - 增量索引 Kotlin 文件
- **API 文档**: 新增支持语言说明

### Testing
- 272 个测试全部通过

## [1.1.4] - 2026-07-13

### Added
- **MCP 工具 Kotlin 支持**: 验证所有 MCP 工具对 Kotlin 代码的支持
  - find_symbol 支持 Kotlin 类/函数
  - find_references 支持 Kotlin 引用
  - find_implementations 支持 Kotlin 接口实现
  - 增量索引支持 Kotlin 文件
- **新增测试**: 9 个 Kotlin MCP 集成测试

### Testing
- 269 个测试全部通过

## [1.1.3] - 2026-07-13

### Added
- **Indexer Kotlin 集成**: Indexer 现在支持 Kotlin 文件索引
  - 自动扫描 .kt/.kts 文件
  - 使用 KotlinParserAdapter 解析
  - 支持增量索引和 diff 策略
- **新增测试**: 2 个 Indexer Kotlin 测试

### Testing
- 260 个测试全部通过

## [1.1.2] - 2026-07-13

### Added
- **Kotlin 解析器**: 新增 KotlinParserAdapter
  - 支持 class, object, data class, sealed class
  - 支持函数和属性提取
  - 支持继承和接口实现
  - 支持 import 引用
- **新增测试**: 12 个 Kotlin 解析测试

### Testing
- 258 个测试全部通过

## [1.1.1] - 2026-07-13

### Added
- **Kotlin 支持准备**: Symbol 新增 Kotlin 特有字段
  - isDataClass: data class 标记
  - isObject: object 单例标记
  - isSealed: sealed class 标记
  - isCompanion: companion object 标记
- **数据库迁移**: 自动添加 Kotlin 字段，兼容旧数据库

### Testing
- 246 个测试全部通过

## [1.1.0] - 2026-07-13

### Added
- **继承关系支持**: 完整的 Java 类继承和接口实现支持
  - Symbol 新增 superClass, interfaces 字段
  - JavaParserAdapter 自动提取 extends/implements
  - StorageService 提供继承关系查询
- **MCP 工具扩展**: 新增 3 个 Java 特有工具
  - `find_implementations`: 查找接口的所有实现类
  - `find_overrides`: 查找方法的所有重写
  - `find_usages`: 查找字段的所有使用位置
- **工具总数**: 10 → 13 个 MCP 工具
- **数据库迁移**: 自动添加新字段，兼容旧数据库
- **集成测试**: 4 个端到端继承关系测试
- **API 文档**: 新增工具文档

### Testing
- 246 个测试全部通过

## [1.0.5] - 2026-07-13

### Added
- **集成测试**: 4 个端到端继承关系测试
- **API 文档**: 新增 find_implementations, find_overrides, find_usages 文档

### Fixed
- insertSymbols 方法现在正确存储继承信息

### Testing
- 246 个测试全部通过

## [1.0.4] - 2026-07-13

### Added
- **MCP 工具扩展**: 新增 3 个 Java 特有工具
  - `find_implementations`: 查找接口的所有实现类
  - `find_overrides`: 查找方法的所有重写
  - `find_usages`: 查找字段的所有使用位置
- **工具总数**: 10 → 13 个 MCP 工具
- **新增测试**: 6 个 MCP 工具测试

## [1.0.3] - 2026-07-13

### Added
- **继承关系查询**: StorageService 新增 3 个查询方法
  - `findImplementations`: 查找接口的所有实现类
  - `findOverrides`: 查找方法的所有重写
  - `findFieldUsages`: 查找字段的所有使用位置
- **新增测试**: 8 个继承查询测试

## [1.0.2] - 2026-07-13

### Added
- **继承信息提取**: JavaParserAdapter 自动提取 extends/implements 信息
  - 支持类继承 (extends)
  - 支持接口实现 (implements)
  - 支持 Record 实现接口
- **新增测试**: 5 个继承提取测试

## [1.0.1] - 2026-07-13

### Added
- **继承关系支持**: Symbol 新增 superClass 和 interfaces 字段
  - superClass: 父类限定名
  - interfaces: 实现的接口列表 (JSON 数组)
- **数据库迁移**: 自动添加新字段，兼容旧数据库

### Changed
- StorageService 优化 SQL 查询，使用公共列常量

## [1.0.0] - 2026-07-13

### Added
- **Gradle 解析器测试**: 13 个测试覆盖 Groovy/Kotlin DSL、版本变量、scope 映射
- **配置文件解析器测试**: 19 个测试覆盖 YAML/Properties/ENV 格式
- **数据库管理器测试**: 10 个测试覆盖事务、初始化、连接管理
- **StorageService 边界测试**: 29 个测试覆盖空值、重复、批量、特殊字符
- **Chunker 边界测试**: 21 个测试覆盖空文件、嵌套类、Record、Lambda
- **Indexer 单元测试**: 21 个测试覆盖增量索引、文件变更、错误处理
- **MCP 工具集成测试**: 27 个测试覆盖所有工具边界情况
- **性能基准测试**: 11 个测试覆盖索引速度、搜索延迟、内存占用

### Changed
- **文档完善**: 新增配置详解、故障排查、贡献者指南
- **API 参考更新**: 补充 search_all_projects、health 工具文档
- **架构设计更新**: 反映最新工具数量和模块结构

### Testing
- 223 个单元测试全部通过 (+210%)
- 覆盖所有核心模块边界情况

## [0.7.2] - 2026-07-13

### Added
- **索引导出**: 新增 `--export <file>` 命令，将索引数据导出为 JSON 文件
  - 导出所有符号、引用、调用、代码块、配置、依赖
  - 包含版本号、导出时间、项目路径
  - 支持 pretty-print 格式化输出

### Testing
- 72 个单元测试全部通过

## [0.7.1] - 2026-07-13

### Added
- **多项目搜索**: 新增 `search_all_projects` 工具（仅多项目模式可用）
  - 跨所有已索引项目搜索
  - 返回每个项目的结果，包含 project 标识
  - 支持 limit 参数控制每个项目的返回数量

## [0.7.0] - 2026-07-13

### Added
- **搜索结果高亮**: search_code 返回匹配关键词的位置信息
  - 符号名高亮 (highlights 字段)
  - 代码块内容高亮 (highlights 字段)
  - 支持大小写不敏感匹配

## [0.6.3] - 2026-07-13

### Changed
- **索引统计增强**: --status 和 health 工具显示更详细信息
  - Top 5 文件（按符号数）
  - 代码总行数
  - 平均每文件符号数
  - 最近索引时间

## [0.6.2] - 2026-07-13

### Changed
- **搜索排序优化**: FTS5 符号搜索优先精确匹配 > 类型优先级 > rank
- **搜索排序优化**: FTS5 代码块搜索按类型排序（CLASS > METHOD > FILE_HEADER > ANNOTATION）
- **索引统计增强**: --status 和 health 工具显示详细分类统计
  - 符号按类型统计（CLASS/METHOD/FIELD）
  - 代码块按类型统计（CLASS/METHOD/FILE_HEADER/ANNOTATION）
  - 文件按类型统计（java/yaml/properties/xml/gradle/env）
  - 依赖按类型统计（POM/GRADLE）

## [0.6.1] - 2026-07-13

### Added
- **health 工具**: MCP 服务器健康检查，返回状态、版本、运行时间、项目统计
- MCP 工具总数从 8 增加到 9

## [0.6.0] - 2026-07-13

### Fixed
- **DatabaseManager 事务安全**: 非 SQL 异常时正确回滚事务，DDL 失败时恢复 autoCommit
- **DatabaseManager 连接状态**: 检查连接是否已关闭，损坏时自动重建
- **DatabaseManager 完整性检查**: 初始化时执行 PRAGMA integrity_check
- **ConfigLoader 类型安全**: YAML 值类型错误时优雅降级（不再抛出 ClassCastException）
- **ConfigLoader 路径验证**: 非法路径字符时给出警告而非崩溃
- **McpServer OOM 保护**: Content-Length 超过 10MB 时拒绝并跳过
- **CliMain 错误处理**: 索引错误时返回非零退出码

### Changed
- 版本号更新为 0.6.0
- DatabaseManager.close() 现在将 connection 设为 null

## [0.5.0] - 2026-07-13

### Added
- **ChunkerTest** (6 tests): 小类整体切分、大类头部提取、方法切分、文件头、Record 类型、包名提取
- **JavaParserAdapterTest** (7 tests): 类/方法/字段符号提取、引用提取、调用关系、空文件、语法错误
- **McpE2ETest** (3 tests): 改为 JUnit 格式，数据库统计、符号搜索、空库搜索
- **CLI 增强**: --version, --status, --search 命令
- **FTS5 布尔搜索**: `Config AND Loader` 正常工作
- **Indexer 性能优化**: 逐条查询替代全量加载 10000 符号

### Testing
- 单元测试从 38 增加到 54 个，全部通过
- ChunkerTest: 6 个测试覆盖所有切分场景
- JavaParserAdapterTest: 7 个测试覆盖核心解析逻辑
- McpE2ETest: 改为 JUnit 格式，可被 `mvn test` 执行

## [0.4.0] - 2026-07-12

### Added
- **FTS5 全文搜索**: 使用 SQLite FTS5 虚拟表实现高性能全文搜索
- FTS5 触发器自动同步：INSERT/UPDATE/DELETE 时自动维护 FTS 索引
- 布尔搜索支持：`Config AND Loader`、`Config OR Service`、`NOT test`
- 前缀搜索：`Mcp*` 匹配所有 Mcp 开头的符号
- 短语搜索：`"import java"` 精确匹配短语
- StructuredSearch 优先使用 FTS5，降级到 LIKE 查询

### Fixed
- **FTS5 布尔搜索**: 修复 `AND`/`OR`/`NOT` 被当作普通词处理的问题
- **PomParser `${project.version}`**: 支持解析项目自身版本（不仅限 parent version）

### Performance
- 搜索性能提升 10-100x（FTS5 vs LIKE）
- 平均搜索耗时从 ~50ms 降到 ~6ms

### Documentation
- 更新 API 文档：移除 semantic_search，工具数从 9 改为 8
- 更新设计文档：移除 embedding 表引用，工具计数 9→8
- 更新入门文档：修正配置示例，添加多项目模式
- 新增开发推进计划（innerdoc/roadmap/）
- 新增设计文档（innerdoc/design/v0.4.0-fts5-search.md）

### Testing
- 新增 PomParserTest (5 tests)
- 新增 StructuredSearchTest (5 tests)
- 扩展 StorageServiceTest (+12 tests)
- 总测试数从 16 增加到 38

## [0.3.1] - 2026-07-12

### Fixed
- **安全漏洞**: SnakeYAML 使用 SafeConstructor 防止反序列化攻击
- **PomParser.findLineNumber**: 现在正确读取文件定位行号
- **性能优化**: SHA-1 缓存避免重复计算

## [0.3.0] - 2026-07-12

### Fixed
- **find_references**: 引用 symbol_id=0 被跳过导致返回空结果
- **find_dependencies**: SQL 查询未处理 NULL 值导致通配符查询失败
- **get_file_info**: Windows 路径分隔符不匹配
- Release workflow needs 条件修复

### Changed
- 移除语义搜索功能（embedding/vector 相关代码）
- Python E2E 测试改用环境变量
- 单元测试精简为 16 个

### Added
- CI workflow (ci.yml)
- Release workflow 添加测试步骤
- CHANGELOG 文件

### Documentation
- 重写设计文档
- 完善 API 参考
- 修复 MkDocs 配置
