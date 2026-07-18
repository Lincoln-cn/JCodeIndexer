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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP complexity_report 工具测试
 * 验证复杂度值是否正确存储和返回
 */
class McpComplexityReportTest {

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
     * 测试复杂度值是否正确存储
     */
    @Test
    void complexityMetricsShouldBeStoredCorrectly() throws Exception {
        // 1. 插入类符号
        long classSymbolId = storage.insertSymbol(new Symbol(
            0, "src/main/java/com/example/Service.java", 1, 100,
            Symbol.SymbolKind.CLASS, "Service", "com.example.Service",
            "class Service", "", "", 1, null, null, null,
            false, false, false, false, false, false
        ));

        // 2. 插入代码度量（复杂度为 15）
        CodeMetrics metrics = new CodeMetrics(
            0, classSymbolId, "src/main/java/com/example/Service.java",
            "Service", "com.example", 100, 10, 5,
            15, System.currentTimeMillis()
        );
        storage.upsertCodeMetrics(metrics);

        // 3. 验证度量数据已存储
        Optional<CodeMetrics> stored = storage.findCodeMetricsByFile(
            "src/main/java/com/example/Service.java", "Service"
        );
        assertTrue(stored.isPresent());
        assertEquals(15, stored.get().complexityEstimate());
        assertEquals(100, stored.get().linesOfCode());
        assertEquals(10, stored.get().methodCount());
        assertEquals(5, stored.get().fieldCount());
    }

    /**
     * 测试复杂度值更新
     */
    @Test
    void complexityMetricsShouldBeUpdated() throws Exception {
        // 1. 插入类符号
        long classSymbolId = storage.insertSymbol(new Symbol(
            0, "TestClass.java", 1, 50,
            Symbol.SymbolKind.CLASS, "TestClass", "com.example.TestClass",
            "class TestClass", "", "", 1, null, null, null,
            false, false, false, false, false, false
        ));

        // 2. 插入初始度量（复杂度为 5）
        CodeMetrics metrics1 = new CodeMetrics(
            0, classSymbolId, "TestClass.java",
            "TestClass", "com.example", 50, 5, 3,
            5, System.currentTimeMillis()
        );
        storage.upsertCodeMetrics(metrics1);

        // 3. 更新度量（复杂度变为 10）
        CodeMetrics metrics2 = new CodeMetrics(
            0, classSymbolId, "TestClass.java",
            "TestClass", "com.example", 100, 10, 5,
            10, System.currentTimeMillis()
        );
        storage.upsertCodeMetrics(metrics2);

        // 4. 验证更新后的值
        Optional<CodeMetrics> stored = storage.findCodeMetricsByFile("TestClass.java", "TestClass");
        assertTrue(stored.isPresent());
        assertEquals(10, stored.get().complexityEstimate());
        assertEquals(100, stored.get().linesOfCode());
    }

    /**
     * 测试复杂度值为 0 的情况（简单方法）
     */
    @Test
    void simpleClassShouldHaveZeroComplexity() throws Exception {
        // 1. 插入类符号
        long classSymbolId = storage.insertSymbol(new Symbol(
            0, "Simple.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Simple", "com.example.Simple",
            "class Simple", "", "", 1, null, null, null,
            false, false, false, false, false, false
        ));

        // 2. 插入度量（复杂度为 0）
        CodeMetrics metrics = new CodeMetrics(
            0, classSymbolId, "Simple.java",
            "Simple", "com.example", 10, 1, 0,
            0, System.currentTimeMillis()
        );
        storage.upsertCodeMetrics(metrics);

        // 3. 验证复杂度为 0
        Optional<CodeMetrics> stored = storage.findCodeMetricsByFile("Simple.java", "Simple");
        assertTrue(stored.isPresent());
        assertEquals(0, stored.get().complexityEstimate());
    }

    /**
     * 测试复杂度值为高值的情况（复杂类）
     */
    @Test
    void complexClassShouldHaveHighComplexity() throws Exception {
        // 1. 插入类符号
        long classSymbolId = storage.insertSymbol(new Symbol(
            0, "Complex.java", 1, 500,
            Symbol.SymbolKind.CLASS, "Complex", "com.example.Complex",
            "class Complex", "", "", 1, null, null, null,
            false, false, false, false, false, false
        ));

        // 2. 插入度量（复杂度为 100）
        CodeMetrics metrics = new CodeMetrics(
            0, classSymbolId, "Complex.java",
            "Complex", "com.example", 500, 50, 20,
            100, System.currentTimeMillis()
        );
        storage.upsertCodeMetrics(metrics);

        // 3. 验证复杂度为 100
        Optional<CodeMetrics> stored = storage.findCodeMetricsByFile("Complex.java", "Complex");
        assertTrue(stored.isPresent());
        assertEquals(100, stored.get().complexityEstimate());
    }

    /**
     * 测试按包名查询复杂度
     */
    @Test
    void findMetricsByPackageName() throws Exception {
        // 1. 插入多个类的度量
        storage.upsertCodeMetrics(new CodeMetrics(
            0, null, "A.java", "A", "com.example", 100, 10, 5,
            20, System.currentTimeMillis()
        ));
        storage.upsertCodeMetrics(new CodeMetrics(
            0, null, "B.java", "B", "com.example", 200, 20, 10,
            50, System.currentTimeMillis()
        ));

        // 2. 按包名查询
        var results = storage.findCodeMetricsByPackageName("com.example", 10);
        assertEquals(2, results.size());
        // 应该按 lines_of_code 降序排列
        assertTrue(results.get(0).linesOfCode() >= results.get(1).linesOfCode());
    }
}
