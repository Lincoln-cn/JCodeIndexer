package com.sodlinken.jindexer.model;

import java.util.Map;

/**
 * 注解实体，存储符号的注解信息
 */
public record Annotation(
    long id,
    long symbolId,              // 关联的符号 ID
    String name,                // 注解名（如 "RestController"）
    Map<String, String> attributes  // 注解属性（如 {"path": "/api/users"}）
) {}
