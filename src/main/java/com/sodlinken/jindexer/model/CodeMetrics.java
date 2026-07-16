package com.sodlinken.jindexer.model;

/**
 * 代码度量：类/文件级别的代码统计
 */
public record CodeMetrics(
    long id,
    Long symbolId,           // 关联的符号 ID（可为 null）
    String filePath,         // 文件相对路径
    String className,        // 类名
    String packageName,      // 包名
    int linesOfCode,         // 代码行数
    int methodCount,         // 方法数
    int fieldCount,          // 字段数
    int complexityEstimate,  // 复杂度估算
    long updatedAt           // 更新时间戳
) {}
