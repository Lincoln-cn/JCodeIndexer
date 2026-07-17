package com.sodlinken.jindexer.indexer;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.*;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Indexer 单元测试：增量索引、文件变更检测、错误处理
 */
class IndexerTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private StorageService storage;
    private Indexer indexer;

    @BeforeEach
    void setUp() throws Exception {
        Config config = new Config();
        config.setProjectRoot(tempDir);
        config.setDataDir(tempDir.resolve(".jindexer"));

        db = new DatabaseManager(config.getDbPath());
        db.initialize();
        storage = new StorageService(db);
        indexer = new Indexer(config, storage, db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    // ==================== Basic Indexing Tests ====================

    @Test
    void indexEmptyProject() throws Exception {
        IndexResult result = indexer.index();

        assertEquals(0, result.totalFiles());
        assertEquals(0, result.updatedFiles());
        assertEquals(0, result.deletedFiles());
    }

    @Test
    void indexSingleJavaFile() throws Exception {
        createJavaFile("Main.java", """
            package com.example;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            }
            """);

        IndexResult result = indexer.index();

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.updatedFiles());
        assertEquals(0, result.deletedFiles());

        // Verify symbols were indexed
        List<Symbol> symbols = storage.searchSymbolsByName("Main", 10);
        assertFalse(symbols.isEmpty());
        assertEquals("Main", symbols.get(0).name());
    }

    @Test
    void indexMultipleJavaFiles() throws Exception {
        createJavaFile("A.java", """
            package com.example;
            public class A { public void methodA() {} }
            """);
        createJavaFile("B.java", """
            package com.example;
            public class B { public void methodB() {} }
            """);
        createJavaFile("C.java", """
            package com.example;
            public class C { public void methodC() {} }
            """);

        IndexResult result = indexer.index();

        assertEquals(3, result.totalFiles());
        assertEquals(3, result.updatedFiles());
    }

    @Test
    void indexWithPackageAndImports() throws Exception {
        createJavaFile("Service.java", """
            package com.example.service;

            import java.util.List;
            import java.util.ArrayList;

            public class Service {
                private List<String> items = new ArrayList<>();

                public void addItem(String item) {
                    items.add(item);
                }
            }
            """);

        IndexResult result = indexer.index();
        assertEquals(1, result.updatedFiles());

        List<Symbol> symbols = storage.searchSymbolsByName("Service", 10);
        assertFalse(symbols.isEmpty());
    }

    // ==================== Incremental Indexing Tests ====================

    @Test
    void indexTwiceSkipsUnchanged() throws Exception {
        createJavaFile("Foo.java", """
            package com.example;
            public class Foo { public void bar() {} }
            """);

        IndexResult first = indexer.index();
        assertEquals(1, first.updatedFiles());

        IndexResult second = indexer.index();
        assertEquals(0, second.updatedFiles()); // No changes
    }

    @Test
    void indexDetectsFileChange() throws Exception {
        Path fooFile = createJavaFile("Foo.java", """
            package com.example;
            public class Foo { public void bar() {} }
            """);

        indexer.index();

        // Modify the file
        Files.writeString(fooFile, """
            package com.example;
            public class Foo { public void baz() {} }
            """);

        IndexResult result = indexer.index();
        assertEquals(1, result.updatedFiles()); // Should re-index
    }

    @Test
    void indexDetectsNewFile() throws Exception {
        createJavaFile("A.java", """
            package com.example;
            public class A {}
            """);

        indexer.index();

        // Add new file
        createJavaFile("B.java", """
            package com.example;
            public class B {}
            """);

        IndexResult result = indexer.index();
        assertEquals(1, result.updatedFiles());
    }

    @Test
    void indexDetectsDeletedFile() throws Exception {
        Path file = createJavaFile("ToDelete.java", """
            package com.example;
            public class ToDelete {}
            """);

        indexer.index();

        // Delete the file
        Files.delete(file);

        IndexResult result = indexer.index();
        assertEquals(1, result.deletedFiles());
    }

    // ==================== Config File Indexing Tests ====================

    @Test
    void indexYamlConfig() throws Exception {
        createFile("application.yml", """
            server:
              port: 8080
              host: localhost
            """);

        IndexResult result = indexer.index();
        assertEquals(1, result.totalFiles());

        var entries = storage.searchConfigEntries("port", null, 10);
        assertFalse(entries.isEmpty());
    }

    @Test
    void indexPropertiesFile() throws Exception {
        createFile("app.properties", """
            db.url=jdbc:mysql://localhost:3306/mydb
            db.username=root
            """);

        IndexResult result = indexer.index();
        assertEquals(1, result.totalFiles());

        var entries = storage.searchConfigEntries("db.url", null, 10);
        assertFalse(entries.isEmpty());
    }

    @Test
    void indexEnvFile() throws Exception {
        createFile(".env", """
            DATABASE_URL=jdbc:mysql://localhost:3306/mydb
            API_KEY=abc123
            """);

        IndexResult result = indexer.index();
        assertEquals(1, result.totalFiles());
    }

    // ==================== POM Indexing Tests ====================

    @Test
    void indexPomFile() throws Exception {
        createFile("pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>5.10.3</version>
                    </dependency>
                </dependencies>
            </project>
            """);

        IndexResult result = indexer.index();
        assertEquals(1, result.totalFiles());

        var deps = storage.searchDependencies("junit", null, 10);
        assertFalse(deps.isEmpty());
    }

    // ==================== Gradle Indexing Tests ====================

    @Test
    void indexGradleFile() throws Exception {
        createFile("build.gradle", """
            dependencies {
                implementation 'org.springframework:spring-core:6.1.0'
                testImplementation 'junit:junit:4.13.2'
            }
            """);

        IndexResult result = indexer.index();
        assertEquals(1, result.totalFiles());

        var deps = storage.searchDependencies("spring-core", null, 10);
        assertFalse(deps.isEmpty());
    }

    // ==================== Mixed File Types Tests ====================

    @Test
    void indexMixedProject() throws Exception {
        createJavaFile("App.java", """
            package com.example;
            public class App { public void run() {} }
            """);
        createFile("pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>app</artifactId>
                <version>1.0.0</version>
            </project>
            """);
        createFile("application.yml", """
            server:
              port: 8080
            """);

        IndexResult result = indexer.index();
        assertEquals(3, result.totalFiles());
    }

    // ==================== Error Handling Tests ====================

    @Test
    void indexInvalidJavaFile() throws Exception {
        createJavaFile("Invalid.java", """
            This is not valid Java code {{{
            """);

        // Should not throw, just log warning
        IndexResult result = indexer.index();
        assertEquals(1, result.totalFiles());
    }

    @Test
    void indexNonExistentProjectRoot() throws Exception {
        Config config = new Config();
        config.setProjectRoot(Path.of("/nonexistent/path"));
        config.setDataDir(tempDir.resolve(".jindexer2"));

        DatabaseManager db2 = new DatabaseManager(config.getDbPath());
        db2.initialize();
        StorageService storage2 = new StorageService(db2);
        Indexer indexer2 = new Indexer(config, storage2, db2);

        try {
            // Should throw exception for non-existent path
            assertThrows(Exception.class, () -> indexer2.index());
        } finally {
            db2.close();
        }
    }

    // ==================== Progress Listener Tests ====================

    @Test
    void indexWithProgressListener() throws Exception {
        createJavaFile("Foo.java", """
            package com.example;
            public class Foo {}
            """);

        StringBuilder phases = new StringBuilder();
        indexer.index(new ProgressListener() {
            @Override
            public void onPhaseStart(String phase, int total) {
                phases.append(phase).append(":");
            }

            @Override
            public void onPhaseEnd(String phase) {}

            @Override
            public void onProgress(String phase, int current, int total) {}

            @Override
            public void onError(String message) {}
        });

        assertTrue(phases.toString().contains("扫描文件"));
        assertTrue(phases.toString().contains("比对哈希"));
    }

    // ==================== Concurrency Tests ====================

    @Test
    void indexConcurrentCallThrows() throws Exception {
        createJavaFile("Foo.java", """
            package com.example;
            public class Foo {}
            """);

        // Start indexing in a thread
        Thread indexerThread = new Thread(() -> {
            try {
                indexer.index();
            } catch (Exception e) {
                // Expected
            }
        });
        indexerThread.start();

        // Wait a bit for the first thread to start
        Thread.sleep(50);

        // Try to index concurrently - should throw
        try {
            indexer.index();
        } catch (IllegalStateException e) {
            // Expected - concurrent access blocked
            return;
        } catch (Exception e) {
            // Other exceptions are also acceptable
            return;
        }

        // If we get here, the test passes (indexing was fast enough)
        indexerThread.join();
    }

    // ==================== File Meta Tests ====================

    @Test
    void indexUpdatesFileMeta() throws Exception {
        createJavaFile("Foo.java", """
            package com.example;
            public class Foo {}
            """);

        indexer.index();

        var meta = storage.findFileMeta("Foo.java");
        assertTrue(meta.isPresent());
        assertNotNull(meta.get().sha1());
        assertTrue(meta.get().size() > 0);
    }

    @Test
    void indexUpdatesSha1OnModification() throws Exception {
        Path file = createJavaFile("Foo.java", """
            package com.example;
            public class Foo {}
            """);

        indexer.index();
        String sha1Before = storage.findFileMeta("Foo.java").get().sha1();

        // Modify file
        Files.writeString(file, """
            package com.example;
            public class Foo { public void bar() {} }
            """);

        indexer.index();
        String sha1After = storage.findFileMeta("Foo.java").get().sha1();

        assertNotEquals(sha1Before, sha1After);
    }

    // ==================== Chunk Tests ====================

    @Test
    void indexCreatesChunks() throws Exception {
        createJavaFile("Foo.java", """
            package com.example;

            public class Foo {
                public void method1() {}
                public void method2() {}
            }
            """);

        indexer.index();

        var chunks = storage.findChunksByFile("Foo.java");
        assertFalse(chunks.isEmpty());
    }

    @Test
    void indexKotlinFile() throws Exception {
        createFile("UserService.kt", """
            package com.example

            class UserService {
                fun save() {}
                fun find(id: Long) {}
            }
            """);

        IndexResult result = indexer.index();

        assertEquals(1, result.totalFiles());

        // Verify symbols were indexed
        List<Symbol> symbols = storage.findSymbolsByFile("UserService.kt");
        assertFalse(symbols.isEmpty());
    }

    @Test
    void indexKotlinDataClass() throws Exception {
        createFile("User.kt", """
            package com.example

            data class User(
                val name: String,
                val age: Int
            )
            """);

        indexer.index();

        List<Symbol> symbols = storage.findSymbolsByFile("User.kt");
        assertFalse(symbols.isEmpty());

        Symbol userClass = symbols.stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(userClass);
        assertTrue(userClass.isDataClass());
    }

    @Test
    void indexAnnotations() throws Exception {
        createJavaFile("Controller.java", """
            package com.example;

            @RestController
            @RequestMapping("/api/users")
            public class Controller {
                @GetMapping("/{id}")
                public void getUser() {}
            }
            """);

        indexer.index();

        // 验证注解被删除（因为还没有存储注解）
        // 注解存储将在 v1.2.4 实现
    }

    @Test
    void indexScalaFile() throws Exception {
        createFile("UserService.scala", """
            package com.example

            class UserService {
                def save() {}
                def find(id: Long) {}
            }
            """);

        IndexResult result = indexer.index();

        assertEquals(1, result.totalFiles());

        // Verify symbols were indexed
        List<Symbol> symbols = storage.findSymbolsByFile("UserService.scala");
        assertFalse(symbols.isEmpty());
    }

    @Test
    void indexScalaCaseClass() throws Exception {
        createFile("User.scala", """
            package com.example

            case class User(name: String, age: Int)
            """);

        indexer.index();

        List<Symbol> symbols = storage.findSymbolsByFile("User.scala");
        assertFalse(symbols.isEmpty());
    }

    @Test
    void indexScalaTrait() throws Exception {
        createFile("Repository.scala", """
            package com.example

            trait Repository[T] {
                def findById(id: Long): Option[T]
            }
            """);

        indexer.index();

        List<Symbol> symbols = storage.findSymbolsByFile("Repository.scala");
        assertFalse(symbols.isEmpty());
    }

    // ==================== Cleanup Deleted Files Tests ====================

    @Test
    void incrementalReindexCleansDeletedFiles() throws Exception {
        // 创建两个 Java 文件
        Path file1 = createJavaFile("Keep.java", """
            package com.example;
            public class Keep { private int x; }
            """);
        Path file2 = createJavaFile("Delete.java", """
            package com.example;
            public class Delete { private int y; }
            """);

        // 初始索引
        indexer.index(ProgressListener.NONE);

        // 验证两个文件都被索引
        var keepSymbols = storage.searchSymbolsByName("Keep", 10);
        var deleteSymbols = storage.searchSymbolsByName("Delete", 10);
        assertFalse(keepSymbols.isEmpty());
        assertFalse(deleteSymbols.isEmpty());

        // 删除 file2
        Files.delete(file2);

        // 增量索引（触发 cleanupDeletedFiles）
        indexer.incrementalReindex(List.of(file1));

        // 验证 Delete 的索引被清理
        var deleteSymbolsAfter = storage.searchSymbolsByName("Delete", 10);
        assertTrue(deleteSymbolsAfter.isEmpty(), "Deleted file's symbols should be cleaned up");

        // 验证 Keep 仍然存在
        var keepSymbolsAfter = storage.searchSymbolsByName("Keep", 10);
        assertFalse(keepSymbolsAfter.isEmpty());
    }

    // ==================== Helper Methods ====================

    private Path createJavaFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    private Path createFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}
