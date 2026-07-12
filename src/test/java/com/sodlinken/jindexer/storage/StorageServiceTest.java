package com.sodlinken.jindexer.storage;

import com.sodlinken.jindexer.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceTest {

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

    // ==================== Symbol Tests ====================

    @Test
    void insertAndSearchSymbol() throws SQLException {
        Symbol sym = new Symbol(0, "src/Foo.java", 10, 50,
            Symbol.SymbolKind.CLASS, "Foo", "com.example.Foo",
            "class Foo", "", "", 1, "A foo class");

        long id = storage.insertSymbol(sym);
        assertTrue(id > 0);

        List<Symbol> results = storage.searchSymbolsByName("Foo", 10);
        assertEquals(1, results.size());
        assertEquals("Foo", results.get(0).name());
        assertEquals("com.example.Foo", results.get(0).qualifiedName());
        assertEquals("src/Foo.java", results.get(0).filePath());
    }

    @Test
    void searchByPartialName() throws SQLException {
        storage.insertSymbol(new Symbol(0, "a.java", 1, 10,
            Symbol.SymbolKind.CLASS, "MyService", "com.app.MyService",
            "", "", "", 1, ""));
        storage.insertSymbol(new Symbol(0, "b.java", 1, 10,
            Symbol.SymbolKind.CLASS, "UserService", "com.app.UserService",
            "", "", "", 1, ""));
        storage.insertSymbol(new Symbol(0, "c.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Config", "com.app.Config",
            "", "", "", 1, ""));

        List<Symbol> results = storage.searchSymbolsByName("Service", 10);
        assertEquals(2, results.size());
    }

    @Test
    void insertMethodSymbol() throws SQLException {
        storage.insertSymbol(new Symbol(0, "Foo.java", 1, 20,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo",
            "", "", "", 1, ""));
        storage.insertSymbol(new Symbol(0, "Foo.java", 5, 15,
            Symbol.SymbolKind.METHOD, "bar", "com.Foo.bar",
            "void bar()", "void", "Foo", 1, ""));

        List<Symbol> results = storage.searchSymbolsByName("bar", 10);
        assertEquals(1, results.size());
        assertEquals(Symbol.SymbolKind.METHOD, results.get(0).kind());
        assertEquals("Foo", results.get(0).parentClass());
    }

    @Test
    void findSymbolsByFile() throws SQLException {
        storage.insertSymbol(new Symbol(0, "Foo.java", 1, 20,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo", "", "", "", 1, ""));
        storage.insertSymbol(new Symbol(0, "Foo.java", 5, 15,
            Symbol.SymbolKind.METHOD, "bar", "com.Foo.bar", "", "", "", 1, ""));
        storage.insertSymbol(new Symbol(0, "Bar.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Bar", "com.Bar", "", "", "", 1, ""));

        List<Symbol> results = storage.findSymbolsByFile("Foo.java");
        assertEquals(2, results.size());
    }

    // ==================== Reference Tests ====================

    @Test
    void insertReference() throws SQLException {
        long symId = storage.insertSymbol(new Symbol(0, "A.java", 1, 10,
            Symbol.SymbolKind.CLASS, "A", "com.A", "", "", "", 1, ""));

        Reference ref = new Reference(0, symId, "B.java", 20, "import com.A;");
        storage.insertReference(ref);

        var refs = storage.findReferencesBySymbol(symId);
        assertEquals(1, refs.size());
        assertEquals("B.java", refs.get(0).fromFile());
        assertEquals(20, refs.get(0).fromLine());
    }

    @Test
    void findReferencesBySymbolName() throws SQLException {
        long symId = storage.insertSymbol(new Symbol(0, "A.java", 1, 10,
            Symbol.SymbolKind.CLASS, "ConfigLoader", "com.ConfigLoader", "", "", "", 1, ""));

        storage.insertReference(new Reference(0, symId, "B.java", 5, "import com.ConfigLoader;"));
        storage.insertReference(new Reference(0, symId, "C.java", 10, "import com.ConfigLoader;"));

        var refs = storage.findReferencesBySymbolName("ConfigLoader", 10);
        assertEquals(2, refs.size());
    }

    // ==================== Call Tests ====================

    @Test
    void insertCallRelation() throws SQLException {
        Call call = new Call(0, "com.A.method", "A.java", 10, "com.B.method", null);
        storage.insertCall(call);

        var callers = storage.findCallers("com.B.method");
        assertEquals(1, callers.size());
        assertEquals("com.A.method", callers.get(0).callerMethod());
    }

    @Test
    void findCallsByMethod() throws SQLException {
        storage.insertCall(new Call(0, "com.A.foo", "A.java", 10, "com.B.bar", null));
        storage.insertCall(new Call(0, "com.C.baz", "C.java", 20, "com.B.bar", null));

        var calls = storage.findCallsByMethod("com.B.bar");
        assertEquals(2, calls.size());
    }

    // ==================== Chunk Tests ====================

    @Test
    void insertAndSearchChunks() throws SQLException {
        Chunk chunk = new Chunk(0, "Foo.java", Chunk.ChunkType.METHOD, 10, 30,
            "bar", "public void bar() {}", "com.example", "Foo", "void bar()");

        storage.insertChunks(List.of(chunk));

        var chunks = storage.findChunksByFile("Foo.java");
        assertEquals(1, chunks.size());
        assertEquals("bar", chunks.get(0).name());
        assertEquals(Chunk.ChunkType.METHOD, chunks.get(0).type());
    }

    @Test
    void searchChunksByContent() throws SQLException {
        storage.insertChunks(List.of(
            new Chunk(0, "Foo.java", Chunk.ChunkType.METHOD, 10, 20,
                "handleError", "try { ... } catch (Exception e) {}", "", "Foo", ""),
            new Chunk(0, "Bar.java", Chunk.ChunkType.METHOD, 5, 15,
                "save", "public void save() {}", "", "Bar", "")
        ));

        var chunks = storage.searchChunksByContent("Exception", 10);
        assertEquals(1, chunks.size());
        assertEquals("handleError", chunks.get(0).name());
    }

    // ==================== Config Tests ====================

    @Test
    void insertAndSearchConfigEntries() throws SQLException {
        storage.insertConfigEntries(List.of(
            new ConfigEntry(0, "app.yml", 1, "server.port", "8080", ConfigEntry.ConfigType.YAML, "server:\n  port: 8080"),
            new ConfigEntry(0, "app.yml", 5, "server.host", "localhost", ConfigEntry.ConfigType.YAML, "server:\n  host: localhost")
        ));

        var entries = storage.searchConfigEntries("port", null, 10);
        assertEquals(1, entries.size());
        assertEquals("server.port", entries.get(0).key());
        assertEquals("8080", entries.get(0).value());
    }

    @Test
    void searchConfigEntriesByType() throws SQLException {
        storage.insertConfigEntries(List.of(
            new ConfigEntry(0, "app.yml", 1, "key1", "val1", ConfigEntry.ConfigType.YAML, "some content"),
            new ConfigEntry(0, "app.properties", 1, "key2", "val2", ConfigEntry.ConfigType.PROPERTIES, "some content")
        ));

        var yamlEntries = storage.searchConfigEntries("key", "YAML", 10);
        assertEquals(1, yamlEntries.size());
        assertEquals("key1", yamlEntries.get(0).key());
    }

    // ==================== Dependency Tests ====================

    @Test
    void insertAndSearchDependencies() throws SQLException {
        storage.insertDependencies(List.of(
            new Dependency(0, "pom.xml", 10, "org.junit.jupiter", "junit-jupiter", "5.10.3", "test", Dependency.DepType.POM, null),
            new Dependency(0, "pom.xml", 15, "com.google.code.gson", "gson", "2.11.0", "compile", Dependency.DepType.POM, null)
        ));

        var deps = storage.searchDependencies("junit", null, 10);
        assertEquals(1, deps.size());
        assertEquals("junit-jupiter", deps.get(0).artifactId());
    }

    @Test
    void searchDependenciesWildcard() throws SQLException {
        storage.insertDependencies(List.of(
            new Dependency(0, "pom.xml", 10, "org.junit.jupiter", "junit-jupiter", "5.10.3", "test", Dependency.DepType.POM, null),
            new Dependency(0, "pom.xml", 15, "com.google.code.gson", "gson", "2.11.0", "compile", Dependency.DepType.POM, null)
        ));

        var deps = storage.searchDependencies("*", null, 10);
        assertEquals(2, deps.size());
    }

    @Test
    void searchDependenciesByType() throws SQLException {
        storage.insertDependencies(List.of(
            new Dependency(0, "pom.xml", 10, "g1", "d1", "1.0", "compile", Dependency.DepType.POM, null),
            new Dependency(0, "build.gradle", 10, "g2", "d2", "2.0", "implementation", Dependency.DepType.GRADLE, null)
        ));

        var pomDeps = storage.searchDependencies("*", "POM", 10);
        assertEquals(1, pomDeps.size());
        assertEquals("d1", pomDeps.get(0).artifactId());
    }

    // ==================== FileMeta Tests ====================

    @Test
    void upsertFileMeta() throws SQLException {
        FileMeta meta = new FileMeta("Foo.java", 1024, System.currentTimeMillis(), "abc123", 5, System.currentTimeMillis());
        storage.upsertFileMeta(meta);

        var result = storage.findFileMeta("Foo.java");
        assertTrue(result.isPresent());
        assertEquals("abc123", result.get().sha1());
        assertEquals(1024, result.get().size());
    }

    // ==================== Project Stats ====================

    @Test
    void getProjectStats() throws SQLException {
        storage.insertSymbol(new Symbol(0, "a.java", 1, 10,
            Symbol.SymbolKind.CLASS, "A", "com.A", "", "", "", 1, ""));
        storage.insertChunks(List.of(
            new Chunk(0, "a.java", Chunk.ChunkType.CLASS, 1, 10, "A", "code", "", "", "")
        ));

        int[] stats = storage.getProjectStats();
        assertEquals(1, stats[0]); // symbols
        assertEquals(1, stats[3]); // chunks (index 3)
    }
}
