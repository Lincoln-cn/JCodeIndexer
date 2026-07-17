package com.sodlinken.jindexer.indexer;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EventFileWatcherTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private StorageService storage;
    private Config config;

    @BeforeEach
    void setUp() throws Exception {
        config = new Config();
        config.setProjectRoot(tempDir);
        config.setDataDir(tempDir.resolve(".jindexer"));
        config.setWatchEnabled(true);
        config.setWatchMode("event");
        config.setWatchDebounceMs(200);

        db = new DatabaseManager(config.getDbPath());
        db.initialize();
        storage = new StorageService(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void startAndStop() throws Exception {
        Indexer indexer = new Indexer(config, storage, db);
        EventFileWatcher watcher = new EventFileWatcher(config, indexer, storage);

        assertFalse(watcher.isRunning());

        watcher.start();
        assertTrue(watcher.isRunning());

        watcher.stop();
        assertFalse(watcher.isRunning());
    }

    @Test
    void disabledWatcher() throws Exception {
        config.setWatchEnabled(false);
        Indexer indexer = new Indexer(config, storage, db);
        EventFileWatcher watcher = new EventFileWatcher(config, indexer, storage);

        watcher.start();
        assertFalse(watcher.isRunning());
    }

    @Test
    void startAndStopMultipleTimes() throws Exception {
        Indexer indexer = new Indexer(config, storage, db);
        EventFileWatcher watcher = new EventFileWatcher(config, indexer, storage);

        // 多次启动停止不应抛异常
        for (int i = 0; i < 3; i++) {
            watcher.start();
            assertTrue(watcher.isRunning());
            watcher.stop();
            assertFalse(watcher.isRunning());
        }
    }

    @Test
    void detectNewFile() throws Exception {
        // 先创建一个 Java 文件
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.writeString(javaFile, """
            package com.example;

            public class TestClass {
                private int value;
            }
            """);

        Indexer indexer = new Indexer(config, storage, db);
        EventFileWatcher watcher = new EventFileWatcher(config, indexer, storage);

        // 初始索引
        indexer.index(ProgressListener.NONE);

        // 启动监听
        watcher.start();
        assertTrue(watcher.isRunning());

        // 创建新文件
        Path newFile = tempDir.resolve("NewClass.java");
        Files.writeString(newFile, """
            package com.example;

            public class NewClass {
                private String name;
            }
            """);

        // 等待去抖处理
        Thread.sleep(500);

        // 停止监听
        watcher.stop();

        // 验证新文件被索引
        var symbols = storage.searchSymbolsByName("NewClass", 10);
        assertFalse(symbols.isEmpty(), "NewClass should be indexed after file creation");
    }

    @Test
    void excludedDirectories() throws Exception {
        // 创建被排除的目录中的文件
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("Excluded.java"), """
            package com.example;
            public class Excluded {}
            """);

        // 创建正常目录中的文件
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Normal.java"), """
            package com.example;
            public class Normal {}
            """);

        Indexer indexer = new Indexer(config, storage, db);
        indexer.index(ProgressListener.NONE);

        // 验证 Excluded 未被索引，Normal 被索引
        var excludedSymbols = storage.searchSymbolsByName("Excluded", 10);
        var normalSymbols = storage.searchSymbolsByName("Normal", 10);

        assertTrue(excludedSymbols.isEmpty());
        assertFalse(normalSymbols.isEmpty());
    }
}
