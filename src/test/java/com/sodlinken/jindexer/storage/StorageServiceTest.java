package com.sodlinken.jindexer.storage;

import com.sodlinken.jindexer.model.Call;
import com.sodlinken.jindexer.model.Reference;
import com.sodlinken.jindexer.model.Symbol;
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
    void insertCallRelation() throws SQLException {
        Call call = new Call(0, "com.A.method", "A.java", 10, "com.B.method", null);
        storage.insertCall(call);

        var callers = storage.findCallers("com.B.method");
        assertEquals(1, callers.size());
        assertEquals("com.A.method", callers.get(0).callerMethod());
    }
}
