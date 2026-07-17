package com.sodlinken.jindexer.mcp;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * search_symbols 通配符测试
 */
class McpWildcardTest {

    @TempDir
    Path tempDir;

    private McpServer server;

    @BeforeEach
    void setUp() throws Exception {
        Config config = new Config();
        config.setProjectRoot(tempDir);
        config.setDataDir(tempDir.resolve(".jindexer"));

        server = new McpServer(config);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.shutdown();
    }

    @Test
    void searchSymbolsWithWildcard() throws Exception {
        // 创建测试文件
        java.nio.file.Files.writeString(tempDir.resolve("UserService.java"),
            "package com.example; public class UserService { private String name; }");
        java.nio.file.Files.writeString(tempDir.resolve("OrderService.java"),
            "package com.example; public class OrderService { private int count; }");
        java.nio.file.Files.writeString(tempDir.resolve("UserController.java"),
            "package com.example; public class UserController { }");

        // 初始化存储并索引
        Config config = new Config();
        config.setProjectRoot(tempDir);
        config.setDataDir(tempDir.resolve(".jindexer"));
        DatabaseManager db = new DatabaseManager(config.getDbPath());
        db.initialize();
        StorageService storage = new StorageService(db);

        // 索引文件
        var indexer = new com.sodlinken.jindexer.indexer.Indexer(config, storage, db);
        indexer.index(com.sodlinken.jindexer.indexer.ProgressListener.NONE);

        // 搜索 "*Service" - 应该匹配 UserService 和 OrderService
        var results = storage.searchSymbolsByName("*Service", 10);
        assertFalse(results.isEmpty(), "Wildcard search '*Service' should find results");

        // 验证找到的是 Service 类
        boolean foundUserService = results.stream().anyMatch(s -> s.name().equals("UserService"));
        boolean foundOrderService = results.stream().anyMatch(s -> s.name().equals("OrderService"));
        assertTrue(foundUserService, "Should find UserService");
        assertTrue(foundOrderService, "Should find OrderService");

        db.close();
    }
}
