package com.sodlinken.jindexer.model;

/**
 * 文件元信息，用于增量检测
 */
public record FileMeta(
    String filePath,        // 相对于项目根目录的路径
    long size,              // 文件大小（字节）
    long lastModified,      // 最后修改时间戳
    String sha1,            // 文件内容 SHA-1
    int symbolCount,        // 关联的符号数
    long lastIndexedAt      // 上次索引时间戳
) {}
