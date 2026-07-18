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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多项目模式测试
 * 验证多项目模式下工具是否正确支持 project 参数切换项目上下文
 */
class MultiProjectModeTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db1;
    private DatabaseManager db2;
    private StorageService storage1;
    private StorageService storage2;

    @BeforeEach
    void setUp() throws Exception {
        // 创建两个独立的数据库模拟两个项目
        Path dataDir1 = tempDir.resolve("project1/.jindexer");
        Path dataDir2 = tempDir.resolve("project2/.jindexer");

        Config config1 = new Config();
        config1.setProjectRoot(tempDir.resolve("project1"));
        config1.setDataDir(dataDir1);

        Config config2 = new Config();
        config2.setProjectRoot(tempDir.resolve("project2"));
        config2.setDataDir(dataDir2);

        db1 = new DatabaseManager(config1.getDbPath());
        db1.initialize();
        storage1 = new StorageService(db1);

        db2 = new DatabaseManager(config2.getDbPath());
        db2.initialize();
        storage2 = new StorageService(db2);
    }

    @AfterEach
    void tearDown() {
        db1.close();
        db2.close();
    }

    /**
     * 测试多项目模式下不同项目的符号隔离
     */
    @Test
    void differentProjectsShouldHaveIsolatedSymbols() throws Exception {
        // 1. 在项目 1 中插入符号
        storage1.insertSymbol(new Symbol(0, "Service1.java", 1, 50,
            Symbol.SymbolKind.CLASS, "Service1", "com.example.Service1",
            "class Service1", "", "", 1, null, null, null,
            false, false, false, false, false, false));

        // 2. 在项目 2 中插入符号
        storage2.insertSymbol(new Symbol(0, "Service2.java", 1, 50,
            Symbol.SymbolKind.CLASS, "Service2", "com.example.Service2",
            "class Service2", "", "", 1, null, null, null,
            false, false, false, false, false, false));

        // 3. 验证项目 1 中只能找到 Service1
        var symbols1 = storage1.searchSymbolsByName("Service", 10);
        assertEquals(1, symbols1.size());
        assertEquals("Service1", symbols1.getFirst().name());

        // 4. 验证项目 2 中只能找到 Service2
        var symbols2 = storage2.searchSymbolsByName("Service", 10);
        assertEquals(1, symbols2.size());
        assertEquals("Service2", symbols2.getFirst().name());
    }

    /**
     * 测试多项目模式下不同项目的调用关系隔离
     */
    @Test
    void differentProjectsShouldHaveIsolatedCallGraphs() throws Exception {
        // 1. 在项目 1 中插入调用关系
        storage1.insertSymbol(new Symbol(0, "Caller.java", 1, 20,
            Symbol.SymbolKind.METHOD, "callMethod", "com.example.Caller.callMethod",
            "void callMethod()", "void", "Caller", 1, null, null, null,
            false, false, false, false, false, false));
        storage1.insertSymbol(new Symbol(0, "Callee.java", 1, 20,
            Symbol.SymbolKind.METHOD, "helperMethod", "com.example.Callee.helperMethod",
            "void helperMethod()", "void", "Callee", 1, null, null, null,
            false, false, false, false, false, false));
        storage1.insertCall(new Call(0, "com.example.Caller.callMethod", "Caller.java", 5,
            "com.example.Callee.helperMethod", "Callee.java"));

        // 2. 在项目 2 中插入不同的调用关系
        storage2.insertSymbol(new Symbol(0, "Caller2.java", 1, 20,
            Symbol.SymbolKind.METHOD, "anotherCall", "com.example.Caller2.anotherCall",
            "void anotherCall()", "void", "Caller2", 1, null, null, null,
            false, false, false, false, false, false));
        storage2.insertSymbol(new Symbol(0, "Target.java", 1, 20,
            Symbol.SymbolKind.METHOD, "targetMethod", "com.example.Target.targetMethod",
            "void targetMethod()", "void", "Target", 1, null, null, null,
            false, false, false, false, false, false));
        storage2.insertCall(new Call(0, "com.example.Caller2.anotherCall", "Caller2.java", 5,
            "com.example.Target.targetMethod", "Target.java"));

        // 3. 验证项目 1 的调用关系
        var calls1 = storage1.findCallsByMethod("com.example.Caller.callMethod");
        assertEquals(1, calls1.size());
        assertEquals("com.example.Callee.helperMethod", calls1.getFirst().calleeMethod());

        // 4. 验证项目 2 的调用关系
        var calls2 = storage2.findCallsByMethod("com.example.Caller2.anotherCall");
        assertEquals(1, calls2.size());
        assertEquals("com.example.Target.targetMethod", calls2.getFirst().calleeMethod());

        // 5. 验证项目 1 中找不到项目 2 的调用关系
        var calls1ForCaller2 = storage1.findCallsByMethod("com.example.Caller2.anotherCall");
        assertTrue(calls1ForCaller2.isEmpty());
    }

    /**
     * 测试多项目模式下不同项目的代码度量隔离
     */
    @Test
    void differentProjectsShouldHaveIsolatedMetrics() throws Exception {
        // 1. 在项目 1 中插入代码度量
        storage1.upsertCodeMetrics(new CodeMetrics(
            0, null, "Service1.java", "Service1", "com.example",
            100, 10, 5, 20, System.currentTimeMillis()));

        // 2. 在项目 2 中插入不同的代码度量
        storage2.upsertCodeMetrics(new CodeMetrics(
            0, null, "Service2.java", "Service2", "com.example",
            200, 20, 10, 50, System.currentTimeMillis()));

        // 3. 验证项目 1 的代码度量
        var metrics1 = storage1.findCodeMetricsByFile("Service1.java", "Service1");
        assertTrue(metrics1.isPresent());
        assertEquals(100, metrics1.get().linesOfCode());
        assertEquals(20, metrics1.get().complexityEstimate());

        // 4. 验证项目 2 的代码度量
        var metrics2 = storage2.findCodeMetricsByFile("Service2.java", "Service2");
        assertTrue(metrics2.isPresent());
        assertEquals(200, metrics2.get().linesOfCode());
        assertEquals(50, metrics2.get().complexityEstimate());

        // 5. 验证项目 1 中找不到项目 2 的代码度量
        var metrics1ForService2 = storage1.findCodeMetricsByFile("Service2.java", "Service2");
        assertTrue(metrics1ForService2.isEmpty());
    }

    /**
     * 测试多项目模式下不同项目的配置隔离
     */
    @Test
    void differentProjectsShouldHaveIsolatedConfigs() throws Exception {
        // 1. 在项目 1 中插入配置条目
        storage1.insertConfigEntries(java.util.List.of(
            new ConfigEntry(0, "application.yml", 1, "server.port", "8080",
                ConfigEntry.ConfigType.YAML, "server.port: 8080"),
            new ConfigEntry(0, "application.yml", 2, "spring.datasource.url", "jdbc:mysql://localhost/db1",
                ConfigEntry.ConfigType.YAML, "spring.datasource.url: jdbc:mysql://localhost/db1")
        ));

        // 2. 在项目 2 中插入不同的配置条目
        storage2.insertConfigEntries(java.util.List.of(
            new ConfigEntry(0, "application.yml", 1, "server.port", "9090",
                ConfigEntry.ConfigType.YAML, "server.port: 9090"),
            new ConfigEntry(0, "application.yml", 2, "spring.datasource.url", "jdbc:mysql://localhost/db2",
                ConfigEntry.ConfigType.YAML, "spring.datasource.url: jdbc:mysql://localhost/db2")
        ));

        // 3. 验证项目 1 的配置
        var config1 = storage1.searchConfigEntries("server.port", "YAML", 10);
        assertEquals(1, config1.size());
        assertEquals("8080", config1.getFirst().value());

        // 4. 验证项目 2 的配置
        var config2 = storage2.searchConfigEntries("server.port", "YAML", 10);
        assertEquals(1, config2.size());
        assertEquals("9090", config2.getFirst().value());
    }
}
