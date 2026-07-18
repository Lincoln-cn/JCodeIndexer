package com.sodlinken.jindexer.mcp;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.indexer.Indexer;
import com.sodlinken.jindexer.search.SearchProvider;
import com.sodlinken.jindexer.search.StructuredSearch;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单个项目的运行时上下文：数据库 + 存储 + 搜索 + 索引器
 */
public record ProjectContext(
    Config config,
    DatabaseManager dbManager,
    StorageService storage,
    SearchProvider searchProvider,
    Indexer indexer
) implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ProjectContext.class);

    /**
     * 创建项目上下文（自动初始化数据库）
     */
    public static ProjectContext create(Config config) {
        try {
            DatabaseManager dbManager = new DatabaseManager(config.getDbPath());
            dbManager.initialize();
            StorageService storage = new StorageService(dbManager);
            SearchProvider searchProvider = new StructuredSearch(storage);
            Indexer indexer = new Indexer(config, storage, dbManager);
            return new ProjectContext(config, dbManager, storage, searchProvider, indexer);
        } catch (Exception e) {
            throw new RuntimeException("创建项目上下文失败: " + config.getProjectRoot(), e);
        }
    }

    @Override
    public void close() {
        dbManager.close();
    }
}
