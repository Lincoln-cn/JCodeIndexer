package com.sodlinken.jindexer.model;

/**
 * Bean 定义来源信息
 * 记录 @Bean 方法或 @Component 类的 Bean 定义位置
 */
public record BeanSource(
    long id,
    long symbolId,
    String returnType,
    String beanName,
    String sourceType,
    String filePath,
    int startLine
) {}
