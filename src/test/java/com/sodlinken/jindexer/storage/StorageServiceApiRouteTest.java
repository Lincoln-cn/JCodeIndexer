package com.sodlinken.jindexer.storage;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.ApiRoute;
import com.sodlinken.jindexer.model.Symbol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceApiRouteTest {

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
    void insertAndSearch() throws Exception {
        // 先插入一个 symbol 作为关联
        long symbolId = storage.insertSymbol(new Symbol(0, "Controller.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserController", "com.example.UserController",
            "class UserController", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertApiRoutes(List.of(
            new ApiRoute(0, symbolId, "GET", "/api/users", "/api/users", "", "Controller.java", 10),
            new ApiRoute(0, symbolId, "POST", "/api/users", "/api/users", "", "Controller.java", 20)
        ));

        List<ApiRoute> results = storage.searchApiRoutes("users", null, 10);
        assertEquals(2, results.size());
    }

    @Test
    void searchByMethod() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Controller.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserController", "com.example.UserController",
            "class UserController", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertApiRoutes(List.of(
            new ApiRoute(0, symbolId, "GET", "/api/users", "/api/users", "", "Controller.java", 10),
            new ApiRoute(0, symbolId, "POST", "/api/orders", "/api/orders", "", "Controller.java", 20)
        ));

        List<ApiRoute> getResults = storage.searchApiRoutes("api", "GET", 10);
        assertEquals(1, getResults.size());
        assertEquals("GET", getResults.getFirst().httpMethod());

        List<ApiRoute> postResults = storage.searchApiRoutes("api", "POST", 10);
        assertEquals(1, postResults.size());
        assertEquals("POST", postResults.getFirst().httpMethod());
    }

    @Test
    void deleteByFile() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Controller.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserController", "com.example.UserController",
            "class UserController", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertApiRoutes(List.of(
            new ApiRoute(0, symbolId, "GET", "/api/users", "/api/users", "", "Controller.java", 10)
        ));

        storage.deleteApiRoutesByFile("Controller.java");

        List<ApiRoute> results = storage.searchApiRoutes("users", null, 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void pathConcatenation() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Controller.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserController", "com.example.UserController",
            "class UserController", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertApiRoutes(List.of(
            new ApiRoute(0, symbolId, "GET", "/api/users/{id}", "/api/users", "/{id}", "Controller.java", 10)
        ));

        List<ApiRoute> results = storage.searchApiRoutes("users/{id}", null, 10);
        assertEquals(1, results.size());
        assertEquals("/api/users/{id}", results.getFirst().path());
        assertEquals("/api/users", results.getFirst().basePath());
        assertEquals("/{id}", results.getFirst().methodPath());
    }

    @Test
    void limitResults() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Controller.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserController", "com.example.UserController",
            "class UserController", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        for (int i = 0; i < 10; i++) {
            storage.insertApiRoutes(List.of(
                new ApiRoute(0, symbolId, "GET", "/api/resource" + i, "/api/resource" + i, "", "Controller.java", 10)
            ));
        }

        List<ApiRoute> results = storage.searchApiRoutes("resource", null, 5);
        assertEquals(5, results.size());
    }

    @Test
    void emptySearch() throws Exception {
        List<ApiRoute> results = storage.searchApiRoutes("nonexistent", null, 10);
        assertTrue(results.isEmpty());
    }
}
