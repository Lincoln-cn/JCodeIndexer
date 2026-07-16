package com.sodlinken.jindexer.storage;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.Symbol;
import com.sodlinken.jindexer.model.TestMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceTestMappingTest {

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

    @Test
    void insertAndQueryBySource() throws Exception {
        long testSymbolId = storage.insertSymbol(new Symbol(0, "UserServiceTest.java", 1, 30,
            Symbol.SymbolKind.CLASS, "UserServiceTest", "com.example.UserServiceTest",
            "class UserServiceTest", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertTestMappings(List.of(
            new TestMapping(0, testSymbolId, null, "UserServiceTest", "UserService", "NAME_PATTERN", "UserServiceTest.java")
        ));

        List<TestMapping> mappings = storage.findTestMappingsBySource("UserService", 10);
        assertEquals(1, mappings.size());
        assertEquals("UserServiceTest", mappings.getFirst().testClassName());
        assertEquals("UserService", mappings.getFirst().sourceClassName());
    }

    @Test
    void multipleTestClasses() throws Exception {
        long test1Id = storage.insertSymbol(new Symbol(0, "UserServiceTest.java", 1, 30,
            Symbol.SymbolKind.CLASS, "UserServiceTest", "com.example.UserServiceTest",
            "class UserServiceTest", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        long test2Id = storage.insertSymbol(new Symbol(0, "UserServiceIntegrationTest.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserServiceIntegrationTest", "com.example.UserServiceIntegrationTest",
            "class UserServiceIntegrationTest", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertTestMappings(List.of(
            new TestMapping(0, test1Id, null, "UserServiceTest", "UserService", "NAME_PATTERN", "UserServiceTest.java"),
            new TestMapping(0, test2Id, null, "UserServiceIntegrationTest", "UserService", "NAME_PATTERN", "UserServiceIntegrationTest.java")
        ));

        List<TestMapping> mappings = storage.findTestMappingsBySource("UserService", 10);
        assertEquals(2, mappings.size());
    }

    @Test
    void deleteByFile() throws Exception {
        long testId = storage.insertSymbol(new Symbol(0, "Test.java", 1, 30,
            Symbol.SymbolKind.CLASS, "MyTest", "com.example.MyTest",
            "class MyTest", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertTestMappings(List.of(
            new TestMapping(0, testId, null, "MyTest", "My", "NAME_PATTERN", "Test.java")
        ));

        storage.deleteTestMappingsByFile("Test.java");

        List<TestMapping> mappings = storage.findTestMappingsBySource("My", 10);
        assertTrue(mappings.isEmpty());
    }

    @Test
    void limitResults() throws Exception {
        for (int i = 0; i < 10; i++) {
            long testId = storage.insertSymbol(new Symbol(0, "Test" + i + ".java", 1, 10,
                Symbol.SymbolKind.CLASS, "Test" + i, "com.example.Test" + i,
                "class Test" + i, "", "", 1, "", null, null,
                false, false, false, false, false, false));

            storage.insertTestMappings(List.of(
                new TestMapping(0, testId, null, "Test" + i, "MyClass", "NAME_PATTERN", "Test" + i + ".java")
            ));
        }

        List<TestMapping> mappings = storage.findTestMappingsBySource("MyClass", 5);
        assertEquals(5, mappings.size());
    }

    @Test
    void emptyResults() throws Exception {
        List<TestMapping> mappings = storage.findTestMappingsBySource("NonExistent", 10);
        assertTrue(mappings.isEmpty());
    }
}
