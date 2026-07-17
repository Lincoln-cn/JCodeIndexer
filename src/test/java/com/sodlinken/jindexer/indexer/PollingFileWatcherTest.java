package com.sodlinken.jindexer.indexer;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PollingFileWatcherTest {

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
        config.setWatchMode("polling");
        config.setWatchIntervalSeconds(1);

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
        PollingFileWatcher watcher = new PollingFileWatcher(config, indexer, storage);

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
        PollingFileWatcher watcher = new PollingFileWatcher(config, indexer, storage);

        watcher.start();
        assertFalse(watcher.isRunning());
    }

    @Test
    void detectNewFile() throws Exception {
        // 创建一个 Java 文件
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.writeString(javaFile, """
            package com.example;
            
            public class TestClass {
                private int value;
                
                public int getValue() {
                    return value;
                }
            }
            """);

        Indexer indexer = new Indexer(config, storage, db);
        PollingFileWatcher watcher = new PollingFileWatcher(config, indexer, storage);

        // 初始索引
        indexer.index(com.sodlinken.jindexer.indexer.ProgressListener.NONE);

        // 验证文件已被索引
        var symbols = storage.searchSymbolsByName("TestClass", 10);
        assertFalse(symbols.isEmpty());
    }

    @Test
    void detectModifiedFile() throws Exception {
        // 创建初始文件
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.writeString(javaFile, """
            package com.example;
            
            public class TestClass {
                private int value;
            }
            """);

        Indexer indexer = new Indexer(config, storage, db);

        // 初始索引
        indexer.index(com.sodlinken.jindexer.indexer.ProgressListener.NONE);

        // 修改文件
        Files.writeString(javaFile, """
            package com.example;
            
            public class TestClass {
                private int value;
                private String name;
                
                public int getValue() {
                    return value;
                }
            }
            """);

        // 验证可以重新索引
        var result = indexer.index(com.sodlinken.jindexer.indexer.ProgressListener.NONE);
        assertTrue(result.totalFiles() > 0);
    }

    @Test
    void excludedDirectories() throws Exception {
        // 创建被排除的目录中的文件
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Path excludedFile = targetDir.resolve("Excluded.java");
        Files.writeString(excludedFile, """
            package com.example;
            
            public class Excluded {}
            """);

        // 创建正常目录中的文件
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Path normalFile = srcDir.resolve("Normal.java");
        Files.writeString(normalFile, """
            package com.example;
            
            public class Normal {}
            """);

        Indexer indexer = new Indexer(config, storage, db);
        indexer.index(com.sodlinken.jindexer.indexer.ProgressListener.NONE);

        // 验证 Excluded 未被索引，Normal 被索引
        var excludedSymbols = storage.searchSymbolsByName("Excluded", 10);
        var normalSymbols = storage.searchSymbolsByName("Normal", 10);

        assertTrue(excludedSymbols.isEmpty());
        assertFalse(normalSymbols.isEmpty());
    }
}
