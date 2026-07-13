package com.sodlinken.jindexer.storage;

import com.sodlinken.jindexer.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试详细统计信息
 */
class StorageServiceStatsTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private StorageService storage;

    @BeforeEach
    void setUp() throws Exception {
        db = new DatabaseManager(tempDir.resolve("index.db"));
        db.initialize();
        storage = new StorageService(db);

        // 插入测试数据
        storage.insertSymbol(new Symbol(0, "Foo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo", "", "", "", 1, ""));
        storage.insertSymbol(new Symbol(0, "Foo.java", 5, 15,
            Symbol.SymbolKind.METHOD, "bar", "com.Foo.bar", "", "", "", 1, ""));
        storage.insertSymbol(new Symbol(0, "Bar.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Bar", "com.Bar", "", "", "", 1, ""));

        storage.insertChunks(List.of(
            new Chunk(0, "Foo.java", Chunk.ChunkType.METHOD, 5, 15, "bar", "code", "", "Foo", ""),
            new Chunk(0, "Bar.java", Chunk.ChunkType.CLASS, 1, 10, "Bar", "code", "", "Bar", "")
        ));

        storage.insertDependencies(List.of(
            new Dependency(0, "pom.xml", 10, "g1", "d1", "1.0", "compile", Dependency.DepType.POM, null),
            new Dependency(0, "pom.xml", 15, "g2", "d2", "2.0", "compile", Dependency.DepType.POM, null)
        ));
    }

    @AfterEach
    void tearDown() {
        storage.close();
        db.close();
    }

    @Test
    void detailedStatsBasic() throws Exception {
        Map<String, Object> stats = storage.getDetailedStats();

        assertEquals(3, stats.get("symbols"));
        assertEquals(0, stats.get("references"));
        assertEquals(0, stats.get("calls"));
        assertEquals(2, stats.get("chunks"));
        assertEquals(2, stats.get("dependencies"));
    }

    @Test
    void detailedStatsByKind() throws Exception {
        Map<String, Object> stats = storage.getDetailedStats();

        @SuppressWarnings("unchecked")
        Map<String, Integer> symbolsByKind = (Map<String, Integer>) stats.get("symbols_by_kind");
        assertNotNull(symbolsByKind);
        assertEquals(2, symbolsByKind.get("CLASS"));
        assertEquals(1, symbolsByKind.get("METHOD"));
    }

    @Test
    void detailedStatsByType() throws Exception {
        Map<String, Object> stats = storage.getDetailedStats();

        @SuppressWarnings("unchecked")
        Map<String, Integer> chunksByType = (Map<String, Integer>) stats.get("chunks_by_type");
        assertNotNull(chunksByType);
        assertEquals(1, chunksByType.get("METHOD"));
        assertEquals(1, chunksByType.get("CLASS"));
    }

    @Test
    void detailedStatsDependencies() throws Exception {
        Map<String, Object> stats = storage.getDetailedStats();

        @SuppressWarnings("unchecked")
        Map<String, Integer> depsByType = (Map<String, Integer>) stats.get("dependencies_by_type");
        assertNotNull(depsByType);
        assertEquals(2, depsByType.get("POM"));
    }

    @Test
    void detailedStatsTopFiles() throws Exception {
        Map<String, Object> stats = storage.getDetailedStats();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topFiles = (List<Map<String, Object>>) stats.get("top_files");
        assertNotNull(topFiles);
        assertFalse(topFiles.isEmpty());
    }
}
