package com.sodlinken.jindexer.mcp;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.*;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP v1.7.0 工具测试
 */
class McpV17Test {

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
    void indexMetadata() throws Exception {
        storage.upsertIndexMetadata("last_indexed_at", "2026-07-16T10:00:00Z");

        var value = storage.getIndexMetadata("last_indexed_at");
        assertTrue(value.isPresent());
        assertEquals("2026-07-16T10:00:00Z", value.get());
    }

    @Test
    void indexMetadataUpdate() throws Exception {
        storage.upsertIndexMetadata("last_indexed_at", "2026-07-16T10:00:00Z");
        storage.upsertIndexMetadata("last_indexed_at", "2026-07-16T11:00:00Z");

        var value = storage.getIndexMetadata("last_indexed_at");
        assertTrue(value.isPresent());
        assertEquals("2026-07-16T11:00:00Z", value.get());
    }

    @Test
    void indexMetadataNotFound() throws Exception {
        var value = storage.getIndexMetadata("nonexistent");
        assertFalse(value.isPresent());
    }

    @Test
    void codeMetrics() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "TestClass.java", 1, 50,
            Symbol.SymbolKind.CLASS, "TestClass", "com.example.TestClass",
            "class TestClass", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        CodeMetrics metrics = new CodeMetrics(0, symbolId, "TestClass.java",
            "TestClass", "com.example", 50, 5, 3, 10, System.currentTimeMillis());

        storage.upsertCodeMetrics(metrics);

        var result = storage.findCodeMetricsByFile("TestClass.java", "TestClass");
        assertTrue(result.isPresent());
        assertEquals(50, result.get().linesOfCode());
        assertEquals(5, result.get().methodCount());
        assertEquals(3, result.get().fieldCount());
    }

    @Test
    void codeMetricsUpdate() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "TestClass.java", 1, 50,
            Symbol.SymbolKind.CLASS, "TestClass", "com.example.TestClass",
            "class TestClass", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        CodeMetrics metrics1 = new CodeMetrics(0, symbolId, "TestClass.java",
            "TestClass", "com.example", 50, 5, 3, 10, System.currentTimeMillis());
        storage.upsertCodeMetrics(metrics1);

        CodeMetrics metrics2 = new CodeMetrics(0, symbolId, "TestClass.java",
            "TestClass", "com.example", 100, 10, 5, 20, System.currentTimeMillis());
        storage.upsertCodeMetrics(metrics2);

        var result = storage.findCodeMetricsByFile("TestClass.java", "TestClass");
        assertTrue(result.isPresent());
        assertEquals(100, result.get().linesOfCode());
        assertEquals(10, result.get().methodCount());
    }

    @Test
    void codeMetricsByPackage() throws Exception {
        storage.insertSymbol(new Symbol(0, "A.java", 1, 50,
            Symbol.SymbolKind.CLASS, "A", "com.example.A",
            "class A", "", "", 1, "", null, null,
            false, false, false, false, false, false));
        storage.insertSymbol(new Symbol(0, "B.java", 1, 30,
            Symbol.SymbolKind.CLASS, "B", "com.example.B",
            "class B", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.upsertCodeMetrics(new CodeMetrics(0, null, "A.java",
            "A", "com.example", 50, 5, 3, 10, System.currentTimeMillis()));
        storage.upsertCodeMetrics(new CodeMetrics(0, null, "B.java",
            "B", "com.example", 30, 3, 2, 5, System.currentTimeMillis()));

        var results = storage.findCodeMetricsByPackageName("com.example", 10);
        assertEquals(2, results.size());
        // 应该按 lines_of_code 降序排列
        assertTrue(results.get(0).linesOfCode() >= results.get(1).linesOfCode());
    }
}
