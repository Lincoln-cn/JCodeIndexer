package com.sodlinken.jindexer.mcp;

import com.sodlinken.jindexer.config.Config;
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

/**
 * MCP 工具边界测试
 */
class McpToolEdgeTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private StorageService storage;

    @BeforeEach
    void setUp() throws Exception {
        Config config = new Config();
        config.setProjectRoot(tempDir);
        config.setDataDir(tempDir.resolve(".jindexer"));

        db = new DatabaseManager(config.getDbPath());
        db.initialize();
        storage = new StorageService(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    // ==================== find_symbol Tests ====================

    @Test
    void findSymbolByName() throws Exception {
        storage.insertSymbol(new Symbol(0, "Foo.java", 1, 50,
            Symbol.SymbolKind.CLASS, "Foo", "com.example.Foo",
            "class Foo", "", "", 1, ""));

        List<Symbol> results = storage.searchSymbolsByName("Foo", 10);
        assertFalse(results.isEmpty());
        assertEquals("Foo", results.get(0).name());
    }

    @Test
    void findSymbolByNamePartial() throws Exception {
        storage.insertSymbol(new Symbol(0, "a.java", 1, 10,
            Symbol.SymbolKind.CLASS, "UserService", "com.app.UserService",
            "", "", "", 1, ""));
        storage.insertSymbol(new Symbol(0, "b.java", 1, 10,
            Symbol.SymbolKind.CLASS, "ConfigService", "com.app.ConfigService",
            "", "", "", 1, ""));

        List<Symbol> results = storage.searchSymbolsByName("Service", 10);
        assertEquals(2, results.size());
    }

    @Test
    void findSymbolByQualifiedName() throws Exception {
        storage.insertSymbol(new Symbol(0, "Foo.java", 1, 50,
            Symbol.SymbolKind.CLASS, "Foo", "com.example.Foo",
            "class Foo", "", "", 1, ""));

        List<Symbol> results = storage.searchSymbolsByName("com.example.Foo", 10);
        assertFalse(results.isEmpty());
    }

    @Test
    void findSymbolCaseInsensitive() throws Exception {
        storage.insertSymbol(new Symbol(0, "Foo.java", 1, 50,
            Symbol.SymbolKind.CLASS, "FooBar", "com.FooBar",
            "", "", "", 1, ""));

        List<Symbol> results = storage.searchSymbolsByName("foobar", 10);
        assertFalse(results.isEmpty());
    }

    @Test
    void findSymbolMaxResults() throws Exception {
        for (int i = 0; i < 100; i++) {
            storage.insertSymbol(new Symbol(0, "File.java", 1, 10,
                Symbol.SymbolKind.CLASS, "Item" + i, "com.Item" + i,
                "", "", "", 1, ""));
        }

        List<Symbol> results = storage.searchSymbolsByName("Item", 5);
        assertEquals(5, results.size());
    }

    // ==================== find_references Tests ====================

    @Test
    void findReferencesBySymbolId() throws Exception {
        long symId = storage.insertSymbol(new Symbol(0, "A.java", 1, 10,
            Symbol.SymbolKind.CLASS, "A", "com.A", "", "", "", 1, ""));

        storage.insertReference(new Reference(0, symId, "B.java", 5, "import com.A;"));
        storage.insertReference(new Reference(0, symId, "C.java", 10, "import com.A;"));

        var refs = storage.findReferencesBySymbol(symId);
        assertEquals(2, refs.size());
    }

    @Test
    void findReferencesBySymbolName() throws Exception {
        storage.insertSymbol(new Symbol(0, "A.java", 1, 10,
            Symbol.SymbolKind.CLASS, "MyClass", "com.MyClass", "", "", "", 1, ""));

        long symId = storage.searchSymbolsByName("MyClass", 1).get(0).id();
        storage.insertReference(new Reference(0, symId, "B.java", 5, "ref"));
        storage.insertReference(new Reference(0, symId, "C.java", 10, "ref"));

        var refs = storage.findReferencesBySymbolName("MyClass", 10);
        assertEquals(2, refs.size());
    }

    @Test
    void findReferencesNoMatch() throws Exception {
        var refs = storage.findReferencesBySymbol(999999);
        assertTrue(refs.isEmpty());
    }

    // ==================== get_call_graph Tests ====================

    @Test
    void findCallers() throws Exception {
        storage.insertCall(new Call(0, "com.A.method", "A.java", 10, "com.B.method", null));
        storage.insertCall(new Call(0, "com.C.method", "C.java", 20, "com.B.method", null));

        var callers = storage.findCallers("com.B.method");
        assertEquals(2, callers.size());
    }

    @Test
    void findCallees() throws Exception {
        storage.insertCall(new Call(0, "com.A.method", "A.java", 10, "com.B.bar", null));
        storage.insertCall(new Call(0, "com.A.method", "A.java", 15, "com.C.baz", null));

        var calls = storage.findCallsByMethod("com.A.method");
        assertEquals(2, calls.size());
    }

    @Test
    void findCallGraphEmpty() throws Exception {
        var callers = storage.findCallers("nonexistent.method");
        assertTrue(callers.isEmpty());
    }

    // ==================== search_code Tests ====================

    @Test
    void searchCodeByContent() throws Exception {
        storage.insertChunks(List.of(
            new Chunk(0, "Foo.java", Chunk.ChunkType.METHOD, 10, 20,
                "handleError", "try { process(); } catch (Exception e) { log(e); }", "", "Foo", ""),
            new Chunk(0, "Bar.java", Chunk.ChunkType.METHOD, 5, 15,
                "save", "public void save() {}", "", "Bar", "")
        ));

        var chunks = storage.searchChunksByContent("Exception", 10);
        assertFalse(chunks.isEmpty());
        assertEquals("handleError", chunks.get(0).name());
    }

    @Test
    void searchCodeByClassName() throws Exception {
        storage.insertChunks(List.of(
            new Chunk(0, "Foo.java", Chunk.ChunkType.CLASS, 1, 50,
                "Foo", "class Foo {}", "com.example", "Foo", ""),
            new Chunk(0, "Bar.java", Chunk.ChunkType.CLASS, 1, 30,
                "Bar", "class Bar {}", "com.example", "Bar", "")
        ));

        var chunks = storage.findChunksByFile("Foo.java");
        assertFalse(chunks.isEmpty());
        assertEquals("Foo", chunks.get(0).className());
    }

    @Test
    void searchCodeEmpty() throws Exception {
        var chunks = storage.searchChunksByContent("NonExistentContent", 10);
        assertTrue(chunks.isEmpty());
    }

    // ==================== get_file_info Tests ====================

    @Test
    void getFileInfo() throws Exception {
        FileMeta meta = new FileMeta("Foo.java", 1024, System.currentTimeMillis(),
            "abc123", 5, System.currentTimeMillis());
        storage.upsertFileMeta(meta);

        var result = storage.findFileMeta("Foo.java");
        assertTrue(result.isPresent());
        assertEquals(1024, result.get().size());
        assertEquals("abc123", result.get().sha1());
    }

    @Test
    void getFileInfoNonExistent() throws Exception {
        var result = storage.findFileMeta("nonexistent.java");
        assertTrue(result.isEmpty());
    }

    @Test
    void getFileInfoUpdate() throws Exception {
        FileMeta meta1 = new FileMeta("Foo.java", 100, System.currentTimeMillis(),
            "old_sha", 1, System.currentTimeMillis());
        FileMeta meta2 = new FileMeta("Foo.java", 200, System.currentTimeMillis(),
            "new_sha", 2, System.currentTimeMillis());

        storage.upsertFileMeta(meta1);
        storage.upsertFileMeta(meta2);

        var result = storage.findFileMeta("Foo.java");
        assertTrue(result.isPresent());
        assertEquals("new_sha", result.get().sha1());
    }

    // ==================== search_config Tests ====================

    @Test
    void searchConfigEntries() throws Exception {
        storage.insertConfigEntries(List.of(
            new ConfigEntry(0, "app.yml", 1, "server.port", "8080",
                ConfigEntry.ConfigType.YAML, "server:\n  port: 8080"),
            new ConfigEntry(0, "app.yml", 5, "server.host", "localhost",
                ConfigEntry.ConfigType.YAML, "server:\n  host: localhost")
        ));

        var entries = storage.searchConfigEntries("port", null, 10);
        assertEquals(1, entries.size());
        assertEquals("server.port", entries.get(0).key());
    }

    @Test
    void searchConfigEntriesByType() throws Exception {
        storage.insertConfigEntries(List.of(
            new ConfigEntry(0, "app.yml", 1, "key1", "val1",
                ConfigEntry.ConfigType.YAML, "content"),
            new ConfigEntry(0, "app.properties", 1, "key2", "val2",
                ConfigEntry.ConfigType.PROPERTIES, "content")
        ));

        var yaml = storage.searchConfigEntries("key", "YAML", 10);
        assertEquals(1, yaml.size());

        var props = storage.searchConfigEntries("key", "PROPERTIES", 10);
        assertEquals(1, props.size());
    }

    @Test
    void searchConfigEntriesEmpty() throws Exception {
        var entries = storage.searchConfigEntries("nonexistent", null, 10);
        assertTrue(entries.isEmpty());
    }

    // ==================== find_dependencies Tests ====================

    @Test
    void searchDependencies() throws Exception {
        storage.insertDependencies(List.of(
            new Dependency(0, "pom.xml", 10, "org.junit.jupiter", "junit-jupiter",
                "5.10.3", "test", Dependency.DepType.POM, null),
            new Dependency(0, "pom.xml", 15, "com.google.code.gson", "gson",
                "2.11.0", "compile", Dependency.DepType.POM, null)
        ));

        var deps = storage.searchDependencies("junit", null, 10);
        assertEquals(1, deps.size());
        assertEquals("junit-jupiter", deps.get(0).artifactId());
    }

    @Test
    void searchDependenciesByType() throws Exception {
        storage.insertDependencies(List.of(
            new Dependency(0, "pom.xml", 10, "g1", "d1", "1.0", "compile",
                Dependency.DepType.POM, null),
            new Dependency(0, "build.gradle", 10, "g2", "d2", "2.0", "implementation",
                Dependency.DepType.GRADLE, null)
        ));

        var pom = storage.searchDependencies("*", "POM", 10);
        assertEquals(1, pom.size());

        var gradle = storage.searchDependencies("*", "GRADLE", 10);
        assertEquals(1, gradle.size());
    }

    @Test
    void searchDependenciesWildcard() throws Exception {
        storage.insertDependencies(List.of(
            new Dependency(0, "pom.xml", 10, "g1", "d1", "1.0", "compile",
                Dependency.DepType.POM, null),
            new Dependency(0, "pom.xml", 15, "g2", "d2", "2.0", "test",
                Dependency.DepType.POM, null)
        ));

        var all = storage.searchDependencies("*", null, 10);
        assertEquals(2, all.size());
    }

    // ==================== list_projects Tests ====================

    @Test
    void getProjectStatsEmpty() throws Exception {
        int[] stats = storage.getProjectStats();
        assertEquals(0, stats[0]); // symbols
    }

    @Test
    void getProjectStatsWithData() throws Exception {
        storage.insertSymbol(new Symbol(0, "a.java", 1, 10,
            Symbol.SymbolKind.CLASS, "A", "com.A", "", "", "", 1, ""));
        storage.insertChunks(List.of(
            new Chunk(0, "a.java", Chunk.ChunkType.CLASS, 1, 10, "A", "code", "", "", "")
        ));

        int[] stats = storage.getProjectStats();
        assertEquals(1, stats[0]); // symbols
        assertEquals(1, stats[3]); // chunks
    }

    // ==================== Health Check Tests ====================

    @Test
    void healthCheck() throws Exception {
        // Insert some data
        storage.insertSymbol(new Symbol(0, "a.java", 1, 10,
            Symbol.SymbolKind.CLASS, "A", "com.A", "", "", "", 1, ""));

        // Database should be accessible
        var conn = db.getConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
    }

    @Test
    void healthCheckAfterClose() throws Exception {
        db.close();
        assertThrows(IllegalStateException.class, () -> db.getConnection());
    }
}
