package com.sodlinken.jindexer.model;

/**
 * 符号引用关系（变量、import、类型引用等）
 */
public record Reference(
    long id,
    long symbolId,          // 被引用的符号 ID
    String fromFile,        // 引用来源文件
    int fromLine,           // 引用所在行号
    String context          // 引用上下文代码片段
) {}
