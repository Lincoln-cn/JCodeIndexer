package com.sodlinken.jindexer.model;

/**
 * Bean 依赖关系：@Autowired / @Inject 注入
 */
public record BeanDependency(
    long id,
    long beanSymbolId,           // 依赖方 Bean 的 symbol ID
    Long dependsOnSymbolId,      // 被依赖方 Bean 的 symbol ID（可能为 null）
    String dependsOnType,        // 依赖的类型名
    String injectionType,        // FIELD / CONSTRUCTOR / SETTER / PARAMETER
    String fieldName,            // 字段名（FIELD 注入时）
    String filePath,             // 文件相对路径
    int startLine                // 起始行号
) {}
