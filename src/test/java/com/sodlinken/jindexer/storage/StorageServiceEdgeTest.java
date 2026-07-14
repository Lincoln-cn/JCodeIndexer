package com.sodlinken.jindexer.storage;

import com.sodlinken.jindexer.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceEdgeTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private StorageService storage;

    @BeforeEach
    void setUp() throws Exception {
        db = new DatabaseManager(tempDir.resolve("index.db"));
        db.initialize();
        storage = new StorageService(db);
    }

    @AfterEach
    void tearDown() {
        storage.close();
        db.close();
    }

    // ==================== Empty / Null Input Tests ====================

    @Test
    void searchSymbolsByNameEmptyQuery() throws Exception {
        List<Symbol> results = storage.searchSymbolsByName("", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchSymbolsByNameNoMatch() throws Exception {
        List<Symbol> results = storage.searchSymbolsByName("NonExistentSymbol123", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void findSymbolsByFileNonExistent() throws Exception {
        List<Symbol> results = storage.findSymbolsByFile("nonexistent.java");
        assertTrue(results.isEmpty());
    }

    @Test
    void findReferencesBySymbolNonExistent() throws Exception {
        var refs = storage.findReferencesBySymbol(999999);
        assertTrue(refs.isEmpty());
    }

    @Test
    void findReferencesBySymbolNameNonExistent() throws Exception {
        var refs = storage.findReferencesBySymbolName("NonExistent", 10);
        assertTrue(refs.isEmpty());
    }

    @Test
    void findCallersNonExistent() throws Exception {
        var callers = storage.findCallers("com.nonexistent.method");
        assertTrue(callers.isEmpty());
    }

    @Test
    void findCallsByMethodNonExistent() throws Exception {
        var calls = storage.findCallsByMethod("com.nonexistent.method");
        assertTrue(calls.isEmpty());
    }

    @Test
    void findChunksByFileNonExistent() throws Exception {
        var chunks = storage.findChunksByFile("nonexistent.java");
        assertTrue(chunks.isEmpty());
    }

    @Test
    void searchChunksByContentNoMatch() throws Exception {
        var chunks = storage.searchChunksByContent("NonExistentContent123", 10);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void searchConfigEntriesNoMatch() throws Exception {
        var entries = storage.searchConfigEntries("nonexistent", null, 10);
        assertTrue(entries.isEmpty());
    }

    @Test
    void searchDependenciesNoMatch() throws Exception {
        var deps = storage.searchDependencies("nonexistent", null, 10);
        assertTrue(deps.isEmpty());
    }

    @Test
    void findFileMetaNonExistent() throws Exception {
        var result = storage.findFileMeta("nonexistent.java");
        assertTrue(result.isEmpty());
    }

    // ==================== Duplicate Insert Tests ====================

    @Test
    void insertDuplicateSymbols() throws Exception {
        Symbol sym1 = new Symbol(0, "Foo.java", 10, 50,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo",
            "class Foo", "", "", 1, "");
        Symbol sym2 = new Symbol(0, "Foo.java", 10, 50,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo",
            "class Foo", "", "", 1, "");

        long id1 = storage.insertSymbol(sym1);
        long id2 = storage.insertSymbol(sym2);

        assertTrue(id1 > 0);
        assertTrue(id2 > 0);
        assertNotEquals(id1, id2); // Should create separate entries
    }

    @Test
    void insertDuplicateReferences() throws Exception {
        long symId = storage.insertSymbol(new Symbol(0, "A.java", 1, 10,
            Symbol.SymbolKind.CLASS, "A", "com.A", "", "", "", 1, ""));

        storage.insertReference(new Reference(0, symId, "B.java", 5, "ref1"));
        storage.insertReference(new Reference(0, symId, "B.java", 5, "ref1")); // Duplicate

        var refs = storage.findReferencesBySymbol(symId);
        assertEquals(2, refs.size()); // Both stored
    }

    @Test
    void insertDuplicateCalls() throws Exception {
        storage.insertCall(new Call(0, "com.A.method", "A.java", 10, "com.B.method", null));
        storage.insertCall(new Call(0, "com.A.method", "A.java", 10, "com.B.method", null)); // Duplicate

        var callers = storage.findCallers("com.B.method");
        assertEquals(2, callers.size()); // Both stored
    }

    // ==================== Batch Insert Tests ====================

    @Test
    void insertMultipleChunks() throws Exception {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            chunks.add(new Chunk(0, "File" + i + ".java", Chunk.ChunkType.METHOD,
                i * 10, i * 10 + 5, "method" + i, "code" + i, "", "Class", "void m()"));
        }

        storage.insertChunks(chunks);

        var allChunks = storage.findChunksByFile("File50.java");
        assertEquals(1, allChunks.size());
        assertEquals("method50", allChunks.get(0).name());
    }

    @Test
    void insertMultipleConfigEntries() throws Exception {
        List<ConfigEntry> entries = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            entries.add(new ConfigEntry(0, "config.yml", i + 1,
                "key" + i, "value" + i, ConfigEntry.ConfigType.YAML, "content"));
        }

        storage.insertConfigEntries(entries);

        var results = storage.searchConfigEntries("key25", null, 10);
        assertEquals(1, results.size());
        assertEquals("value25", results.get(0).value());
    }

    @Test
    void insertMultipleDependencies() throws Exception {
        List<Dependency> deps = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            deps.add(new Dependency(0, "pom.xml", i + 1,
                "com.example", "lib" + i, "1.0", "compile", Dependency.DepType.POM, null));
        }

        storage.insertDependencies(deps);

        var results = storage.searchDependencies("lib15", null, 10);
        assertEquals(1, results.size());
        assertEquals("lib15", results.get(0).artifactId());
    }

    // ==================== Special Character Tests ====================

    @Test
    void searchSymbolsWithSpecialChars() throws Exception {
        storage.insertSymbol(new Symbol(0, "Foo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "My-Class_2.0", "com.app.My-Class_2.0",
            "", "", "", 1, ""));

        var results = storage.searchSymbolsByName("My-Class", 10);
        assertEquals(1, results.size());
    }

    @Test
    void insertSymbolWithEmptySignature() throws Exception {
        Symbol sym = new Symbol(0, "Foo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo",
            "", "", "", 1, "");

        long id = storage.insertSymbol(sym);
        assertTrue(id > 0);

        var results = storage.searchSymbolsByName("Foo", 10);
        assertEquals(1, results.size());
    }

    @Test
    void insertChunkWithLongContent() throws Exception {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContent.append("line of code content ");
        }

        Chunk chunk = new Chunk(0, "BigFile.java", Chunk.ChunkType.CLASS,
            1, 1000, "BigClass", longContent.toString(), "", "", "");

        storage.insertChunks(List.of(chunk));

        var results = storage.findChunksByFile("BigFile.java");
        assertEquals(1, results.size());
        assertTrue(results.get(0).content().length() > 1000);
    }

    // ==================== FileMeta Tests ====================

    @Test
    void upsertFileMetaUpdate() throws Exception {
        FileMeta meta1 = new FileMeta("Foo.java", 100, System.currentTimeMillis(), "abc", 5, System.currentTimeMillis());
        FileMeta meta2 = new FileMeta("Foo.java", 200, System.currentTimeMillis(), "def", 10, System.currentTimeMillis());

        storage.upsertFileMeta(meta1);
        storage.upsertFileMeta(meta2); // Should update

        var result = storage.findFileMeta("Foo.java");
        assertTrue(result.isPresent());
        assertEquals(200, result.get().size());
        assertEquals("def", result.get().sha1());
    }

    @Test
    void upsertMultipleFileMeta() throws Exception {
        for (int i = 0; i < 20; i++) {
            storage.upsertFileMeta(new FileMeta("File" + i + ".java",
                i * 100, System.currentTimeMillis(), "sha" + i, i, System.currentTimeMillis()));
        }

        var result = storage.findFileMeta("File10.java");
        assertTrue(result.isPresent());
        assertEquals(1000, result.get().size());
    }

    // ==================== Project Stats Tests ====================

    @Test
    void getProjectStatsEmpty() throws Exception {
        int[] stats = storage.getProjectStats();
        assertEquals(0, stats[0]); // symbols
    }

    @Test
    void getProjectStatsWithData() throws Exception {
        storage.insertSymbol(new Symbol(0, "a.java", 1, 10,
            Symbol.SymbolKind.CLASS, "A", "com.A", "", "", "", 1, ""));
        storage.insertSymbol(new Symbol(0, "a.java", 5, 15,
            Symbol.SymbolKind.METHOD, "m", "com.A.m", "", "", "", 1, ""));
        storage.insertChunks(List.of(
            new Chunk(0, "a.java", Chunk.ChunkType.CLASS, 1, 10, "A", "code", "", "", ""),
            new Chunk(0, "a.java", Chunk.ChunkType.METHOD, 5, 15, "m", "code", "", "", "")
        ));

        int[] stats = storage.getProjectStats();
        assertEquals(2, stats[0]); // symbols
        assertEquals(2, stats[3]); // chunks
    }

    // ==================== FTS Search Tests ====================

    @Test
    void searchChunksByContentEmpty() throws Exception {
        var chunks = storage.searchChunksByContent("", 10);
        // Empty query might return all or none depending on implementation
        assertNotNull(chunks);
    }

    @Test
    void searchConfigEntriesByTypeNonExistent() throws Exception {
        storage.insertConfigEntries(List.of(
            new ConfigEntry(0, "app.yml", 1, "key", "val", ConfigEntry.ConfigType.YAML, "content")
        ));

        var results = storage.searchConfigEntries("key", "NONEXISTENT", 10);
        assertTrue(results.isEmpty());
    }

    // ==================== Dependency Search Tests ====================

    @Test
    void searchDependenciesByScope() throws Exception {
        storage.insertDependencies(List.of(
            new Dependency(0, "pom.xml", 10, "g", "d1", "1.0", "compile", Dependency.DepType.POM, null),
            new Dependency(0, "pom.xml", 15, "g", "d2", "1.0", "test", Dependency.DepType.POM, null)
        ));

        // Search by artifact should work regardless of scope
        var compile = storage.searchDependencies("d1", null, 10);
        assertEquals(1, compile.size());

        var test = storage.searchDependencies("d2", null, 10);
        assertEquals(1, test.size());
    }

    @Test
    void searchDependenciesWildcardAll() throws Exception {
        storage.insertDependencies(List.of(
            new Dependency(0, "pom.xml", 10, "g1", "d1", "1.0", "compile", Dependency.DepType.POM, null),
            new Dependency(0, "pom.xml", 15, "g2", "d2", "2.0", "test", Dependency.DepType.GRADLE, null)
        ));

        var all = storage.searchDependencies("*", null, 10);
        assertEquals(2, all.size());
    }
}
