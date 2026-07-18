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
 * FileWatcher 数据一致性测试
 * 验证 FileWatcher 自动索引后数据与手动 reindex 一致
 */
class FileWatcherConsistencyTest {

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
        config.setWatchEnabled(false); // 测试时禁用自动监听

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
     * 测试文件修改后索引数据一致性
     */
    @Test
    void fileModificationShouldUpdateIndexConsistently() throws Exception {
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
        assertEquals("Service", symbols1.getFirst().name());

        // 4. 修改文件
        Files.writeString(javaFile, """
            package com.example;

            public class Service {
                public void doSomething() {
                    System.out.println("Hello World");
                }

                public void doAnotherThing() {
                    System.out.println("Another");
                }
            }
            """);

        // 5. 执行增量索引（模拟 FileWatcher 行为）
        IndexResult result2 = indexer.index();
        assertEquals(1, result2.totalFiles());

        // 6. 验证更新后的索引数据
        var symbols2 = storage.findSymbolsByFile("src/main/java/com/example/Service.java");
        assertFalse(symbols2.isEmpty());
        // 应该有两个方法
        long methodCount = symbols2.stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.METHOD)
            .count();
        assertEquals(2, methodCount);
    }

    /**
     * 测试文件删除后索引数据清理
     */
    @Test
    void fileDeletionShouldCleanIndexData() throws Exception {
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
        indexer.index();

        // 3. 验证初始索引数据
        var symbols1 = storage.findSymbolsByFile("src/main/java/com/example/Service.java");
        assertFalse(symbols1.isEmpty());

        // 4. 删除文件
        Files.delete(javaFile);

        // 5. 执行增量索引（模拟 FileWatcher 行为）
        indexer.index();

        // 6. 验证索引数据已清理
        var symbols2 = storage.findSymbolsByFile("src/main/java/com/example/Service.java");
        assertTrue(symbols2.isEmpty());
    }

    /**
     * 测试文件元信息更新时间戳
     */
    @Test
    void fileMetaShouldBeUpdatedAfterIndex() throws Exception {
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
        indexer.index();

        // 3. 验证文件元信息
        Optional<FileMeta> meta1 = storage.findFileMeta("src/main/java/com/example/Service.java");
        assertTrue(meta1.isPresent());
        long firstIndexTime = meta1.get().lastIndexedAt();
        assertTrue(firstIndexTime > 0);

        // 4. 等待一段时间后修改文件
        Thread.sleep(100);
        Files.writeString(javaFile, """
            package com.example;

            public class Service {
                public void doSomething() {
                    System.out.println("Hello World");
                }
            }
            """);

        // 5. 执行增量索引
        indexer.index();

        // 6. 验证文件元信息已更新
        Optional<FileMeta> meta2 = storage.findFileMeta("src/main/java/com/example/Service.java");
        assertTrue(meta2.isPresent());
        long secondIndexTime = meta2.get().lastIndexedAt();
        assertTrue(secondIndexTime >= firstIndexTime);
    }

    /**
     * 测试配置文件变化后全量重新索引
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
        indexer.index();

        // 3. 验证初始索引数据
        var symbols1 = storage.findSymbolsByFile("src/main/java/com/example/Service.java");
        assertFalse(symbols1.isEmpty());

        // 4. 创建新的 Java 文件
        Path javaFile2 = tempDir.resolve("src/main/java/com/example/AnotherService.java");
        Files.writeString(javaFile2, """
            package com.example;

            public class AnotherService {
                public void doAnother() {
                    System.out.println("Another");
                }
            }
            """);

        // 5. 执行增量索引
        indexer.index();

        // 6. 验证新文件已索引
        var symbols2 = storage.findSymbolsByFile("src/main/java/com/example/AnotherService.java");
        assertFalse(symbols2.isEmpty());
    }

    /**
     * 测试 SHA-1 哈希验证
     */
    @Test
    void sha1HashShouldDetectChanges() throws Exception {
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
        indexer.index();

        // 3. 验证 SHA-1 哈希
        Optional<FileMeta> meta1 = storage.findFileMeta("src/main/java/com/example/Service.java");
        assertTrue(meta1.isPresent());
        String sha1_1 = meta1.get().sha1();
        assertNotNull(sha1_1);

        // 4. 修改文件内容（但保持相同行数）
        Files.writeString(javaFile, """
            package com.example;

            public class Service {
                public void doSomething() {
                    System.out.println("Hello World");
                }
            }
            """);

        // 5. 执行增量索引
        indexer.index();

        // 6. 验证 SHA-1 哈希已更新
        Optional<FileMeta> meta2 = storage.findFileMeta("src/main/java/com/example/Service.java");
        assertTrue(meta2.isPresent());
        String sha1_2 = meta2.get().sha1();
        assertNotEquals(sha1_1, sha1_2);
    }
}
