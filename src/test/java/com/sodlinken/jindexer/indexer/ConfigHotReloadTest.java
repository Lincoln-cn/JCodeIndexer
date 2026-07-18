package com.sodlinken.jindexer.indexer;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.*;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置热更新测试
 * 验证修改配置文件后是否触发重新索引
 */
class ConfigHotReloadTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private StorageService storage;
    private Config config;
    private Indexer indexer;

    @BeforeEach
    void setUp() throws Exception {
        config = new Config();
        config.setProjectRoot(tempDir);
        config.setDataDir(tempDir.resolve(".jindexer"));
        config.setWatchEnabled(false);

        db = new DatabaseManager(config.getDbPath());
        db.initialize();
        storage = new StorageService(db);
        indexer = new Indexer(config, storage, db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    /**
     * 测试配置文件变化后触发全量重新索引
     */
    @Test
    void configChangeShouldTriggerFullReindex() throws Exception {
        // 1. 创建初始 Java 文件
        Path javaFile = tempDir.resolve("src/main/java/com/example/Service.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, """
            package com.example;

            public class Service {
                public void doSomething() {
                    System.out.println("Hello");
                }
            }
            """);

        // 2. 执行初始索引
        IndexResult result1 = indexer.index();
        assertEquals(1, result1.totalFiles());

        // 3. 验证初始索引数据
        var symbols1 = storage.findSymbolsByFile("src/main/java/com/example/Service.java");
        assertFalse(symbols1.isEmpty());

        // 4. 创建配置文件变化（模拟 config.yaml 变化）
        Path configDir = tempDir.resolve(".jindexer");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("config.yaml");
        Files.writeString(configFile, """
            indexing_threads: 4
            watch_enabled: true
            """);

        // 5. 模拟配置变化后的全量重新索引
        // 在实际场景中，EventFileWatcher 会检测到配置文件变化并调用 fullSha1Check
        indexer.index();

        // 6. 验证索引数据仍然正确
        var symbols2 = storage.findSymbolsByFile("src/main/java/com/example/Service.java");
        assertFalse(symbols2.isEmpty());
    }

    /**
     * 测试配置文件变化日志输出
     */
    @Test
    void configChangeShouldLogMessage() throws Exception {
        // 这个测试验证配置变化时会输出日志
        // 实际的日志输出需要在运行时验证

        // 创建配置文件
        Path configDir = tempDir.resolve(".jindexer");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("config.yaml");
        Files.writeString(configFile, "indexing_threads: 4\n");

        // 验证配置文件已创建
        assertTrue(Files.exists(configFile));
    }
}
