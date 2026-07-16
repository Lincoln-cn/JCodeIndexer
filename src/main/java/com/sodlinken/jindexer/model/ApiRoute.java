package com.sodlinken.jindexer.model;

/**
 * API 路由映射：URL → Controller 方法
 */
public record ApiRoute(
    long id,
    long symbolId,
    String httpMethod,    // GET / POST / PUT / DELETE / PATCH
    String path,          // 完整路径: /api/users/{id}
    String basePath,      // 类级别路径: /api/users
    String methodPath,    // 方法级别路径: /{id}
    String filePath,      // 文件相对路径
    int startLine         // 起始行号
) {}
