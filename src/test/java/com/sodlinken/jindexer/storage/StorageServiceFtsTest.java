package com.sodlinken.jindexer.storage;

import com.sodlinken.jindexer.model.Chunk;
import com.sodlinken.jindexer.model.Symbol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 FTS5 全文搜索功能
 */
class StorageServiceFtsTest {

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
        storage.insertSymbol(new Symbol(0, "Foo.java", 10, 50,
            Symbol.SymbolKind.CLASS, "Foo", "com.example.Foo",
            "class Foo", "", "", 1, "A foo class"));
        storage.insertSymbol(new Symbol(0, "Foo.java", 20, 30,
            Symbol.SymbolKind.METHOD, "bar", "com.example.Foo.bar",
            "void bar()", "void", "Foo", 1, ""));
        storage.insertSymbol(new Symbol(0, "Bar.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Bar", "com.example.Bar",
            "class Bar", "", "", 1, ""));

        storage.insertChunks(List.of(
            new Chunk(0, "Foo.java", Chunk.ChunkType.METHOD, 10, 20,
                "handleError", "try { return config.get(\"key\"); } catch (Exception e) {}",
                "com.example", "Foo", "")
        ));
    }

    @AfterEach
    void tearDown() {
        storage.close();
        db.close();
    }

    @Test
    void ftsSearchSymbols() throws SQLException {
        List<Symbol> results = storage.searchSymbolsFts("Foo", 10);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(s -> s.name().equals("Foo")));
    }

    @Test
    void ftsSearchSymbolsPrefix() throws SQLException {
        List<Symbol> results = storage.searchSymbolsFts("F*", 10);
        assertFalse(results.isEmpty());
    }

    @Test
    void ftsSearchChunks() throws SQLException {
        List<Chunk> results = storage.searchChunksFts("config", 10);
        assertFalse(results.isEmpty());
    }

    @Test
    void ftsSearchNoResults() throws SQLException {
        List<Symbol> results = storage.searchSymbolsFts("NonExistent", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void ftsSearchBooleanAnd() throws SQLException {
        List<Symbol> results = storage.searchSymbolsFts("Foo AND bar", 10);
        assertFalse(results.isEmpty());
    }

    @Test
    void listAllSymbols() throws SQLException {
        List<Symbol> results = storage.listAllSymbols(10);
        assertEquals(3, results.size());
    }

    @Test
    void listAllChunks() throws SQLException {
        List<Chunk> results = storage.listAllChunks(10);
        assertEquals(1, results.size());
    }
}
