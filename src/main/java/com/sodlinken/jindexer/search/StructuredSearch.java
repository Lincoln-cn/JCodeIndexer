package com.sodlinken.jindexer.search;

import com.sodlinken.jindexer.model.SearchResult;
import com.sodlinken.jindexer.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * 结构化搜索：基于 SQLite FTS5 的全文搜索
 * 优先使用 FTS5，降级到 LIKE 查询
 */
public class StructuredSearch implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(StructuredSearch.class);
    private final StorageService storage;

    public StructuredSearch(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public SearchResult search(String query, int limit) {
        long startTime = System.currentTimeMillis();

        try {
            boolean isWildcard = "*".equals(query);

            // 搜索符号
            var symbols = isWildcard ? listAllSymbols(limit) : searchSymbols(query, limit);
            // 搜索代码块
            var chunks = isWildcard ? listAllChunks(limit) : searchChunks(query, limit);

            long queryTime = System.currentTimeMillis() - startTime;
            long totalHits = symbols.size() + chunks.size();

            return new SearchResult(symbols, chunks, List.of(), totalHits, queryTime);
        } catch (Exception e) {
            log.error("搜索失败", e);
            return SearchResult.empty(System.currentTimeMillis() - startTime);
        }
    }

    private List<com.sodlinken.jindexer.model.Symbol> listAllSymbols(int limit) {
        try {
            return storage.listAllSymbols(limit);
        } catch (SQLException e) {
            log.warn("列出所有符号失败", e);
            return List.of();
        }
    }

    private List<com.sodlinken.jindexer.model.Chunk> listAllChunks(int limit) {
        try {
            return storage.listAllChunks(limit);
        } catch (SQLException e) {
            log.warn("列出所有代码块失败", e);
            return List.of();
        }
    }

    private List<com.sodlinken.jindexer.model.Symbol> searchSymbols(String query, int limit) {
        try {
            // 优先使用 FTS5 搜索
            return storage.searchSymbolsFts(query, limit);
        } catch (SQLException e) {
            log.debug("FTS5 搜索失败，降级到 LIKE 搜索: {}", e.getMessage());
            try {
                return storage.searchSymbolsByName(query, limit);
            } catch (SQLException ex) {
                log.warn("符号搜索失败", ex);
                return List.of();
            }
        }
    }

    private List<com.sodlinken.jindexer.model.Chunk> searchChunks(String query, int limit) {
        try {
            // 优先使用 FTS5 搜索
            return storage.searchChunksFts(query, limit);
        } catch (SQLException e) {
            log.debug("FTS5 搜索失败，降级到 LIKE 搜索: {}", e.getMessage());
            try {
                return storage.searchChunksByContent(query, limit);
            } catch (SQLException ex) {
                log.warn("代码块搜索失败", ex);
                return List.of();
            }
        }
    }
}
