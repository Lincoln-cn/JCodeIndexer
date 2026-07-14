package com.sodlinken.jindexer.benchmark;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.indexer.Indexer;
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
 * 性能基准测试：索引速度、搜索延迟、内存占用
 */
class PerformanceBenchmarkTest {

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

    // ==================== Indexing Performance Tests ====================

    @Test
    void benchmarkIndexingSingleFile() throws Exception {
        createJavaFile("Single.java", generateJavaClass("Single", 50));

        long start = System.currentTimeMillis();
        IndexResult result = indexer.index();
        long duration = System.currentTimeMillis() - start;

        System.out.printf("[Benchmark] 单文件索引: %d ms%n", duration);
        assertTrue(duration < 1000, "单文件索引应小于 1 秒");
        assertEquals(1, result.totalFiles());
    }

    @Test
    void benchmarkIndexing10Files() throws Exception {
        for (int i = 0; i < 10; i++) {
            createJavaFile("Class" + i + ".java", generateJavaClass("Class" + i, 30));
        }

        long start = System.currentTimeMillis();
        IndexResult result = indexer.index();
        long duration = System.currentTimeMillis() - start;

        System.out.printf("[Benchmark] 10 文件索引: %d ms (平均 %.1f ms/文件)%n",
            duration, duration / 10.0);
        assertTrue(duration < 5000, "10 文件索引应小于 5 秒");
        assertEquals(10, result.totalFiles());
    }

    @Test
    void benchmarkIndexing50Files() throws Exception {
        for (int i = 0; i < 50; i++) {
            createJavaFile("Class" + i + ".java", generateJavaClass("Class" + i, 40));
        }

        long start = System.currentTimeMillis();
        IndexResult result = indexer.index();
        long duration = System.currentTimeMillis() - start;

        System.out.printf("[Benchmark] 50 文件索引: %d ms (平均 %.1f ms/文件)%n",
            duration, duration / 50.0);
        assertTrue(duration < 15000, "50 文件索引应小于 15 秒");
        assertEquals(50, result.totalFiles());
    }

    @Test
    void benchmarkIncrementalIndexing() throws Exception {
        // 初始索引
        for (int i = 0; i < 20; i++) {
            createJavaFile("Class" + i + ".java", generateJavaClass("Class" + i, 30));
        }
        indexer.index();

        // 新增 5 个文件
        for (int i = 20; i < 25; i++) {
            createJavaFile("Class" + i + ".java", generateJavaClass("Class" + i, 30));
        }

        long start = System.currentTimeMillis();
        IndexResult result = indexer.index();
        long duration = System.currentTimeMillis() - start;

        System.out.printf("[Benchmark] 增量索引 (5 新文件): %d ms%n", duration);
        assertTrue(duration < 3000, "增量索引应小于 3 秒");
        assertEquals(5, result.updatedFiles());
    }

    @Test
    void benchmarkLargeClassIndexing() throws Exception {
        // 创建包含大量方法的大类
        StringBuilder sb = new StringBuilder();
        sb.append("package com.example;\n\n");
        sb.append("public class LargeClass {\n");
        for (int i = 0; i < 100; i++) {
            sb.append("    public void method").append(i).append("() {\n");
            sb.append("        System.out.println(\"Method ").append(i).append("\");\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n");

        createJavaFile("LargeClass.java", sb.toString());

        long start = System.currentTimeMillis();
        IndexResult result = indexer.index();
        long duration = System.currentTimeMillis() - start;

        System.out.printf("[Benchmark] 大类索引 (100 方法): %d ms%n", duration);
        assertTrue(duration < 5000, "大类索引应小于 5 秒");
    }

    // ==================== Search Performance Tests ====================

    @Test
    void benchmarkSearchByName() throws Exception {
        // 插入测试数据
        for (int i = 0; i < 1000; i++) {
            storage.insertSymbol(new Symbol(0, "File" + (i % 100) + ".java",
                1, 10, Symbol.SymbolKind.CLASS,
                "Symbol" + i, "com.example.Symbol" + i,
                "", "", "", 1, ""));
        }

        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            storage.searchSymbolsByName("Symbol500", 10);
        }
        long duration = System.currentTimeMillis() - start;

        System.out.printf("[Benchmark] 符号搜索 (100 次): %d ms (平均 %.2f ms/次)%n",
            duration, duration / 100.0);
        assertTrue(duration < 1000, "100 次搜索应小于 1 秒");
    }

    @Test
    void benchmarkSearchByContent() throws Exception {
        // 插入测试数据
        for (int i = 0; i < 500; i++) {
            storage.insertChunks(List.of(
                new Chunk(0, "File" + i + ".java", Chunk.ChunkType.METHOD,
                    1, 20, "method" + i, "public void method" + i + "() { /* content */ }",
                    "", "Class" + i, "")
            ));
        }

        long start = System.currentTimeMillis();
        for (int i = 0; i < 50; i++) {
            storage.searchChunksByContent("content", 10);
        }
        long duration = System.currentTimeMillis() - start;

        System.out.printf("[Benchmark] 内容搜索 (50 次): %d ms (平均 %.2f ms/次)%n",
            duration, duration / 50.0);
        assertTrue(duration < 2000, "50 次搜索应小于 2 秒");
    }

    @Test
    void benchmarkWildcardSearch() throws Exception {
        // 插入测试数据
        for (int i = 0; i < 500; i++) {
            storage.insertSymbol(new Symbol(0, "File.java",
                1, 10, Symbol.SymbolKind.CLASS,
                "Item" + i, "com.example.Item" + i,
                "", "", "", 1, ""));
        }

        long start = System.currentTimeMillis();
        storage.searchSymbolsByName("Item", 100);
        long duration = System.currentTimeMillis() - start;

        System.out.printf("[Benchmark] 通配符搜索 (返回 100 条): %d ms%n", duration);
        assertTrue(duration < 500, "通配符搜索应小于 500 ms");
    }

    // ==================== Memory Usage Tests ====================

    @Test
    void benchmarkMemoryUsage() throws Exception {
        Runtime runtime = Runtime.getRuntime();

        // 清理内存
        runtime.gc();
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();

        // 创建测试数据
        for (int i = 0; i < 100; i++) {
            createJavaFile("Class" + i + ".java", generateJavaClass("Class" + i, 20));
        }
        indexer.index();

        // 清理并测量
        runtime.gc();
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = afterMemory - beforeMemory;

        System.out.printf("[Benchmark] 100 文件索引内存占用: %.2f MB%n",
            memoryUsed / 1024.0 / 1024.0);
        assertTrue(memoryUsed < 100 * 1024 * 1024, "内存占用应小于 100 MB");
    }

    @Test
    void benchmarkSearchMemoryStability() throws Exception {
        Runtime runtime = Runtime.getRuntime();

        // 插入大量数据
        for (int i = 0; i < 1000; i++) {
            storage.insertSymbol(new Symbol(0, "File.java",
                1, 10, Symbol.SymbolKind.CLASS,
                "Symbol" + i, "com.Symbol" + i,
                "", "", "", 1, ""));
        }

        runtime.gc();
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();

        // 执行大量搜索
        for (int i = 0; i < 1000; i++) {
            storage.searchSymbolsByName("Symbol", 10);
        }

        runtime.gc();
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryDelta = afterMemory - beforeMemory;

        System.out.printf("[Benchmark] 1000 次搜索内存变化: %.2f MB%n",
            memoryDelta / 1024.0 / 1024.0);
        // 内存变化不应过大（允许一定波动）
        assertTrue(Math.abs(memoryDelta) < 50 * 1024 * 1024,
            "搜索不应导致内存大幅增长");
    }

    // ==================== Concurrent Performance Tests ====================

    @Test
    void benchmarkConcurrentSearch() throws Exception {
        // 插入测试数据
        for (int i = 0; i < 500; i++) {
            storage.insertSymbol(new Symbol(0, "File.java",
                1, 10, Symbol.SymbolKind.CLASS,
                "Item" + i, "com.Item" + i,
                "", "", "", 1, ""));
        }

        int threadCount = 4;
        int searchesPerThread = 100;
        long[] threadDurations = new long[threadCount];
        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            threads[t] = new Thread(() -> {
                long start = System.currentTimeMillis();
                try {
                    for (int i = 0; i < searchesPerThread; i++) {
                        storage.searchSymbolsByName("Item" + (i % 100), 10);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                threadDurations[threadIndex] = System.currentTimeMillis() - start;
            });
        }

        long totalStart = System.currentTimeMillis();
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long totalDuration = System.currentTimeMillis() - totalStart;

        System.out.printf("[Benchmark] 并发搜索 (%d 线程 x %d 次): 总耗时 %d ms%n",
            threadCount, searchesPerThread, totalDuration);
        assertTrue(totalDuration < 5000, "并发搜索应小于 5 秒");
    }

    // ==================== Helper Methods ====================

    private Path createJavaFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    private String generateJavaClass(String className, int methodCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.example;\n\n");
        sb.append("public class ").append(className).append(" {\n");
        for (int i = 0; i < methodCount; i++) {
            sb.append("    public void method").append(i).append("() {\n");
            sb.append("        System.out.println(\"").append(className).append(".method").append(i).append("\");\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
