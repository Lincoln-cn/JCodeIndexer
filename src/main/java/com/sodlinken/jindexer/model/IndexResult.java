package com.sodlinken.jindexer.model;

import java.util.List;

/**
 * 索引结果
 */
public record IndexResult(
    int totalFiles,         // 扫描文件总数
    int updatedFiles,       // 新增/修改文件数
    int deletedFiles,       // 已删除文件数
    int symbolCount,        // 提取的符号总数
    int referenceCount,     // 提取的引用总数
    int callCount,          // 提取的调用关系总数
    int chunkCount,         // 生成的代码块总数
    int configCount,        // 提取的配置项总数
    int dependencyCount,    // 提取的依赖总数
    long durationMs,        // 耗时
    List<String> errors     // 解析失败的文件列表
) {}
