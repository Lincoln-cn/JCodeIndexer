package com.sodlinken.jindexer.model;

import java.util.List;

/**
 * 文件详细信息（get_file_info 返回）
 */
public record FileInfo(
    String filePath,
    long size,
    String sha1,
    int symbolCount,
    int chunkCount,
    int callCount,
    List<Symbol> symbols,       // 文件内的所有符号
    List<Chunk> chunks,         // 文件内的所有代码块
    List<Call> calls            // 文件内的所有调用关系
) {}
