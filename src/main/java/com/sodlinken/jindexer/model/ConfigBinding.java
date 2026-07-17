package com.sodlinken.jindexer.model;

/**
 * 配置绑定信息
 * 记录 @ConfigurationProperties 绑定的类和字段
 */
public record ConfigBinding(
    long id,
    long symbolId,
    String configKey,
    String fieldName,
    String bindingType,
    String filePath,
    int startLine
) {}
