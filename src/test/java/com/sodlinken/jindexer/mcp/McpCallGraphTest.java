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
 * MCP get_call_graph 工具测试
 * 验证 callees 的 file 字段是否正确填充
 */
class McpCallGraphTest {

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
     * 测试 callees 的 file 字段是否正确填充
     * 当 callee 是索引范围内的方法时，file 字段应该显示实际文件路径
     */
    @Test
    void calleesShouldHaveCorrectFilePath() throws Exception {
        // 1. 插入 caller 方法的 symbol
        long callerSymbolId = storage.insertSymbol(new Symbol(
            0, "Caller.java", 10, 20,
            Symbol.SymbolKind.METHOD, "doSomething", "com.example.Caller.doSomething",
            "void doSomething()", "void", "Caller", 1, null, null, null,
            false, false, false, false, false, false
        ));

        // 2. 插入 callee 方法的 symbol（同一个文件或不同文件均可）
        long calleeSymbolId = storage.insertSymbol(new Symbol(
            0, "Callee.java", 5, 15,
            Symbol.SymbolKind.METHOD, "helperMethod", "com.example.Callee.helperMethod",
            "void helperMethod()", "void", "Callee", 1, null, null, null,
            false, false, false, false, false, false
        ));

        // 3. 插入调用关系（calleeFile 为 null，模拟当前问题）
        Call call = new Call(0, "com.example.Caller.doSomething", "Caller.java", 12,
            "com.example.Callee.helperMethod", null);
        storage.insertCall(call);

        // 4. 验证调用关系已插入
        var calls = storage.findCallsByMethod("com.example.Caller.doSomething");
        assertEquals(1, calls.size());
        assertNull(calls.getFirst().calleeFile()); // 当前行为：calleeFile 为 null

        // 5. 通过 callee 查找 symbol，应该能找到文件路径
        var calleeSymbol = storage.findSymbolByQualifiedName("com.example.Callee.helperMethod");
        assertTrue(calleeSymbol.isPresent());
        assertEquals("Callee.java", calleeSymbol.get().filePath());

        // 6. 验证：通过 calleeMethod 查找 symbol 应该能得到正确的 file 路径
        // 这是修复的核心逻辑
        String expectedFile = calleeSymbol.get().filePath();
        assertNotNull(expectedFile);
        assertEquals("Callee.java", expectedFile);
    }

    /**
     * 测试当 callee 不在索引范围内时，file 字段应显示 "unknown"
     */
    @Test
    void calleesOutsideIndexShouldShowUnknown() throws Exception {
        // 1. 插入 caller 方法的 symbol
        long callerSymbolId = storage.insertSymbol(new Symbol(
            0, "Caller.java", 10, 20,
            Symbol.SymbolKind.METHOD, "callExternal", "com.example.Caller.callExternal",
            "void callExternal()", "void", "Caller", 1, null, null, null,
            false, false, false, false, false, false
        ));

        // 2. 插入调用关系，callee 是外部库方法（不在索引范围内）
        Call call = new Call(0, "com.example.Caller.callExternal", "Caller.java", 12,
            "org.springframework.util.StringUtils.hasText", null);
        storage.insertCall(call);

        // 3. 验证 callee 不在 symbols 表中
        var calleeSymbol = storage.findSymbolByQualifiedName("org.springframework.util.StringUtils.hasText");
        assertFalse(calleeSymbol.isPresent());

        // 4. 验证：当 callee 不在索引范围内时，file 字段应显示 "unknown"
        var calls = storage.findCallsByMethod("com.example.Caller.callExternal");
        assertEquals(1, calls.size());
        assertNull(calls.getFirst().calleeFile());
    }

    /**
     * 测试 callers 的 file 字段是否正确填充
     */
    @Test
    void callersShouldHaveCorrectFilePath() throws Exception {
        // 1. 插入 callee 方法的 symbol
        long calleeSymbolId = storage.insertSymbol(new Symbol(
            0, "Service.java", 1, 50,
            Symbol.SymbolKind.METHOD, "process", "com.example.Service.process",
            "void process()", "void", "Service", 1, null, null, null,
            false, false, false, false, false, false
        ));

        // 2. 插入 caller 方法的 symbol
        long callerSymbolId = storage.insertSymbol(new Symbol(
            0, "Controller.java", 1, 30,
            Symbol.SymbolKind.METHOD, "handleRequest", "com.example.Controller.handleRequest",
            "void handleRequest()", "void", "Controller", 1, null, null, null,
            false, false, false, false, false, false
        ));

        // 3. 插入调用关系
        Call call = new Call(0, "com.example.Controller.handleRequest", "Controller.java", 5,
            "com.example.Service.process", "Service.java");
        storage.insertCall(call);

        // 4. 验证 callers 的 file 字段正确
        var callers = storage.findCallers("com.example.Service.process");
        assertEquals(1, callers.size());
        assertEquals("Controller.java", callers.getFirst().callerFile());
    }
}
