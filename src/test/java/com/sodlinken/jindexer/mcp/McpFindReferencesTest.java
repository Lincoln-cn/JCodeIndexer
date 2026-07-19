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
 * find_references 工具测试
 */
class McpFindReferencesTest {

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
     * 测试通过 symbol_id 查找引用
     */
    @Test
    void findReferencesBySymbolId() throws Exception {
        // 1. 插入符号
        long symbolId = storage.insertSymbol(new Symbol(
            0, "UserService.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserService", "com.example.UserService",
            "class UserService", "", "", 1, null, null, null,
            false, false, false, false, false, false));

        // 2. 插入引用
        storage.insertReference(new Reference(0, symbolId, "Controller.java", 10, "import com.example.UserService"));
        storage.insertReference(new Reference(0, symbolId, "Controller.java", 15, "type UserService"));

        // 3. 查找引用
        var references = storage.findReferencesBySymbol(symbolId);
        assertEquals(2, references.size());
    }

    /**
     * 测试通过 symbol_name 查找引用
     */
    @Test
    void findReferencesBySymbolName() throws Exception {
        // 1. 插入符号
        long symbolId = storage.insertSymbol(new Symbol(
            0, "UserService.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserService", "com.example.UserService",
            "class UserService", "", "", 1, null, null, null,
            false, false, false, false, false, false));

        // 2. 插入引用
        storage.insertReference(new Reference(0, symbolId, "Controller.java", 10, "import com.example.UserService"));

        // 3. 通过名称查找引用
        var references = storage.findReferencesBySymbolName("UserService", 10);
        assertEquals(1, references.size());
    }

    /**
     * 测试通过完全限定名查找引用
     */
    @Test
    void findReferencesByQualifiedName() throws Exception {
        // 1. 插入符号
        long symbolId = storage.insertSymbol(new Symbol(
            0, "UserService.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserService", "com.example.UserService",
            "class UserService", "", "", 1, null, null, null,
            false, false, false, false, false, false));

        // 2. 插入引用
        storage.insertReference(new Reference(0, symbolId, "Controller.java", 10, "import com.example.UserService"));

        // 3. 通过完全限定名查找引用
        var references = storage.findReferencesBySymbolName("com.example.UserService", 10);
        assertEquals(1, references.size());
    }

    /**
     * 测试查找不存在的符号的引用
     */
    @Test
    void findReferencesForNonexistentSymbol() throws Exception {
        var references = storage.findReferencesBySymbolName("NonexistentClass", 10);
        assertTrue(references.isEmpty());
    }
}
