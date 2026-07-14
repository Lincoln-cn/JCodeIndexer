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
 * StorageService 注解 CRUD 测试
 */
class StorageServiceAnnotationTest {

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
        db.close();
    }

    @Test
    void insertAndFindAnnotation() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Foo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo", "class Foo", null, null, 1, null,
            null, null, false, false, false, false));

        long annId = storage.insertAnnotation(new Annotation(0, symbolId, "RestController", Map.of()));
        assertTrue(annId > 0);

        var annotations = storage.findAnnotationsBySymbol(symbolId);
        assertEquals(1, annotations.size());
        assertEquals("RestController", annotations.getFirst().name());
    }

    @Test
    void insertAnnotationWithAttributes() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Foo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo", "class Foo", null, null, 1, null,
            null, null, false, false, false, false));

        storage.insertAnnotation(new Annotation(0, symbolId, "RequestMapping", Map.of("path", "/api/foo")));

        var annotations = storage.findAnnotationsBySymbol(symbolId);
        assertEquals(1, annotations.size());
        assertEquals("RequestMapping", annotations.getFirst().name());
        assertNotNull(annotations.getFirst().attributes());
        assertEquals("/api/foo", annotations.getFirst().attributes().get("path"));
    }

    @Test
    void insertMultipleAnnotations() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Foo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo", "class Foo", null, null, 1, null,
            null, null, false, false, false, false));

        storage.insertAnnotations(List.of(
            new Annotation(0, symbolId, "RestController", Map.of()),
            new Annotation(0, symbolId, "RequestMapping", Map.of("path", "/api/foo"))
        ));

        var annotations = storage.findAnnotationsBySymbol(symbolId);
        assertEquals(2, annotations.size());
    }

    @Test
    void findAnnotationsBySymbolName() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Foo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo", "class Foo", null, null, 1, null,
            null, null, false, false, false, false));

        storage.insertAnnotation(new Annotation(0, symbolId, "RestController", Map.of()));

        var annotations = storage.findAnnotationsBySymbolName("Foo");
        assertEquals(1, annotations.size());
    }

    @Test
    void findByAnnotation() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Foo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo", "class Foo", null, null, 1, null,
            null, null, false, false, false, false));

        storage.insertAnnotation(new Annotation(0, symbolId, "RestController", Map.of()));

        var symbols = storage.findByAnnotation("RestController", 10);
        assertEquals(1, symbols.size());
        assertEquals("Foo", symbols.getFirst().name());
    }

    @Test
    void findByAnnotationNoMatch() throws Exception {
        var symbols = storage.findByAnnotation("NonExistent", 10);
        assertTrue(symbols.isEmpty());
    }

    @Test
    void deleteAnnotationsBySymbol() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Foo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo", "class Foo", null, null, 1, null,
            null, null, false, false, false, false));

        storage.insertAnnotation(new Annotation(0, symbolId, "RestController", Map.of()));

        storage.deleteAnnotationsBySymbol(symbolId);

        var annotations = storage.findAnnotationsBySymbol(symbolId);
        assertTrue(annotations.isEmpty());
    }

    @Test
    void deleteAnnotationsByFile() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Foo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Foo", "com.Foo", "class Foo", null, null, 1, null,
            null, null, false, false, false, false));

        storage.insertAnnotation(new Annotation(0, symbolId, "RestController", Map.of()));

        storage.deleteAnnotationsByFile("Foo.java");

        var annotations = storage.findAnnotationsBySymbol(symbolId);
        assertTrue(annotations.isEmpty());
    }

    @Test
    void findAnnotationsNoMatch() throws Exception {
        var annotations = storage.findAnnotationsBySymbol(999999);
        assertTrue(annotations.isEmpty());
    }
}
