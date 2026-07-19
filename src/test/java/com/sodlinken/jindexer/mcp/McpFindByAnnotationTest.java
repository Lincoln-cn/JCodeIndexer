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
 * find_by_annotation 工具测试
 */
class McpFindByAnnotationTest {

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

    /**
     * 测试通过注解名查找符号
     */
    @Test
    void findByAnnotation() throws Exception {
        // 1. 插入符号
        long symbolId = storage.insertSymbol(new Symbol(
            0, "TestController.java", 1, 50,
            Symbol.SymbolKind.CLASS, "TestController", "com.example.TestController",
            "class TestController", "", "", 1, null, null, null,
            false, false, false, false, false, false));

        // 2. 插入注解
        storage.insertAnnotation(new Annotation(0, symbolId, "RestController", null));

        // 3. 通过注解名查找符号
        var symbols = storage.findByAnnotation("RestController", 10);
        assertEquals(1, symbols.size());
        assertEquals("TestController", symbols.getFirst().name());
    }

    /**
     * 测试查找不存在的注解
     */
    @Test
    void findByAnnotationNotFound() throws Exception {
        var symbols = storage.findByAnnotation("NonexistentAnnotation", 10);
        assertTrue(symbols.isEmpty());
    }
}
