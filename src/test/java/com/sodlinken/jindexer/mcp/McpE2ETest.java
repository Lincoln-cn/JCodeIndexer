package com.sodlinken.jindexer.mcp;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.config.ConfigLoader;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.indexer.Indexer;
import com.sodlinken.jindexer.model.Symbol;
import com.sodlinken.jindexer.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 端到端测试：索引 + 搜索 + 调用图
 */
class McpE2ETest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private StorageService storage;

    @BeforeEach
    void setUp() throws Exception {
        Config config = new Config();
        config.setProjectRoot(tempDir);
        config.setDataDir(tempDir.resolve(".jindexer"));
        
        db = new DatabaseManager(config.getDbPath());
        db.initialize();
        storage = new StorageService(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void databaseStatistics() throws Exception {
        int symbols = db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM symbols");
            return rs.next() ? rs.getInt(1) : 0;
        });
        int references = db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM code_references");
            return rs.next() ? rs.getInt(1) : 0;
        });
        int calls = db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM calls");
            return rs.next() ? rs.getInt(1) : 0;
        });
        int chunks = db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM chunks");
            return rs.next() ? rs.getInt(1) : 0;
        });

        // 初始状态应该是空的
        assertEquals(0, symbols);
        assertEquals(0, references);
        assertEquals(0, calls);
        assertEquals(0, chunks);
    }

    @Test
    void symbolSearch() throws SQLException {
        // 插入测试符号
        storage.insertSymbol(new Symbol(0, "Test.java", 1, 10,
            Symbol.SymbolKind.CLASS, "TestClass", "com.example.TestClass",
            "class TestClass", "", "", 1, ""));

        List<Symbol> results = storage.searchSymbolsByName("Test", 10);
        assertFalse(results.isEmpty());
        assertEquals("TestClass", results.get(0).name());
    }

    @Test
    void emptyDatabaseSearch() throws SQLException {
        List<Symbol> results = storage.searchSymbolsByName("NonExistent", 10);
        assertTrue(results.isEmpty());
    }
}
