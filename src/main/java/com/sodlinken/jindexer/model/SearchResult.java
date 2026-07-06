package com.sodlinken.jindexer.model;

import java.util.List;

/**
 * 搜索结果
 */
public record SearchResult(
    List<Symbol> symbols,   // 符号搜索命中
    List<Chunk> chunks,     // 代码块搜索命中
    List<Reference> references, // 引用搜索命中
    long totalHits,         // 总命中数
    long queryTimeMs        // 查询耗时
) {
    public static SearchResult empty(long queryTimeMs) {
        return new SearchResult(List.of(), List.of(), List.of(), 0, queryTimeMs);
    }
}
