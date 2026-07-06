package com.sodlinken.jindexer.model;

/**
 * 方法调用关系
 */
public record Call(
    long id,
    String callerMethod,    // 调用者限定名
    String callerFile,      // 调用者所在文件
    int callerLine,         // 调用发生行号
    String calleeMethod,    // 被调用者限定名
    String calleeFile       // 被调用者所在文件（索引范围内时填充，否则为 null）
) {}
