package com.sodlinken.jindexer.search;

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

class StructuredSearchTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private StorageService storage;
    private StructuredSearch search;

    @BeforeEach
    void setUp() throws Exception {
        db = new DatabaseManager(tempDir.resolve("index.db"));
        db.initialize();
        storage = new StorageService(db);
        search = new StructuredSearch(storage);

        // 插入测试数据
        storage.insertSymbol(new Symbol(0, "src/Foo.java", 10, 50,
            Symbol.SymbolKind.CLASS, "Foo", "com.example.Foo",
            "class Foo", "", "", 1, "A foo class"));
        storage.insertSymbol(new Symbol(0, "src/Foo.java", 20, 30,
            Symbol.SymbolKind.METHOD, "bar", "com.example.Foo.bar",
            "void bar()", "void", "Foo", 1, ""));
    }

    @AfterEach
    void tearDown() {
        storage.close();
        db.close();
    }

    @Test
    void searchByKeyword() {
        SearchResult result = search.search("Foo", 10);
        assertTrue(result.totalHits() > 0);
        assertFalse(result.symbols().isEmpty());
        assertEquals("Foo", result.symbols().get(0).name());
    }

    @Test
    void searchWildcard() {
        SearchResult result = search.search("*", 10);
        assertTrue(result.totalHits() > 0);
        assertEquals(2, result.symbols().size());
    }

    @Test
    void searchNoResults() {
        SearchResult result = search.search("NonExistent", 10);
        assertEquals(0, result.totalHits());
        assertTrue(result.symbols().isEmpty());
    }

    @Test
    void searchRespectsLimit() {
        SearchResult result = search.search("*", 1);
        assertEquals(1, result.symbols().size());
    }

    @Test
    void searchTiming() {
        SearchResult result = search.search("Foo", 10);
        assertTrue(result.queryTimeMs() >= 0);
    }
}
