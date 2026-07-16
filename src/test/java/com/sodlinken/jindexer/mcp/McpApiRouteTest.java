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
 * MCP API 路由工具测试
 */
class McpApiRouteTest {

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
    void searchApiRoutes() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Controller.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserController", "com.example.UserController",
            "class UserController", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertApiRoutes(List.of(
            new ApiRoute(0, symbolId, "GET", "/api/users", "/api/users", "", "Controller.java", 10),
            new ApiRoute(0, symbolId, "POST", "/api/orders", "/api/orders", "", "Controller.java", 20)
        ));

        var results = storage.searchApiRoutes("users", null, 10);
        assertEquals(1, results.size());
        assertEquals("GET", results.getFirst().httpMethod());
    }

    @Test
    void searchApiRoutesByMethod() throws Exception {
        long symbolId = storage.insertSymbol(new Symbol(0, "Controller.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserController", "com.example.UserController",
            "class UserController", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertApiRoutes(List.of(
            new ApiRoute(0, symbolId, "GET", "/api/users", "/api/users", "", "Controller.java", 10),
            new ApiRoute(0, symbolId, "POST", "/api/orders", "/api/orders", "", "Controller.java", 20)
        ));

        var getResults = storage.searchApiRoutes("api", "GET", 10);
        assertEquals(1, getResults.size());

        var postResults = storage.searchApiRoutes("api", "POST", 10);
        assertEquals(1, postResults.size());
    }

    @Test
    void findTypeHierarchyUp() throws Exception {
        storage.insertSymbol(new Symbol(0, "Base.java", 1, 50,
            Symbol.SymbolKind.CLASS, "BaseService", "com.example.BaseService",
            "class BaseService", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertSymbol(new Symbol(0, "User.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserService", "com.example.UserService",
            "class UserService extends BaseService", "", "", 1, "",
            "BaseService", null,
            false, false, false, false, false, false));

        var hierarchy = storage.findTypeHierarchyUp("UserService", 10);
        assertTrue(hierarchy.size() >= 2);
    }

    @Test
    void findTypeHierarchyDown() throws Exception {
        storage.insertSymbol(new Symbol(0, "Base.java", 1, 50,
            Symbol.SymbolKind.CLASS, "BaseService", "com.example.BaseService",
            "class BaseService", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertSymbol(new Symbol(0, "User.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserService", "com.example.UserService",
            "class UserService extends BaseService", "", "", 1, "",
            "BaseService", null,
            false, false, false, false, false, false));

        var children = storage.findTypeHierarchyDown("BaseService", 10);
        assertFalse(children.isEmpty());
    }

    @Test
    void findBeanDependencies() throws Exception {
        long beanId = storage.insertSymbol(new Symbol(0, "OrderService.java", 1, 50,
            Symbol.SymbolKind.CLASS, "OrderService", "com.example.OrderService",
            "class OrderService", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        long depId = storage.insertSymbol(new Symbol(0, "UserService.java", 1, 30,
            Symbol.SymbolKind.CLASS, "UserService", "com.example.UserService",
            "class UserService", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertBeanDependencies(List.of(
            new BeanDependency(0, beanId, depId, "UserService", "CONSTRUCTOR", "userService", "OrderService.java", 10)
        ));

        var deps = storage.findBeanDependencies(beanId, 10);
        assertEquals(1, deps.size());
    }

    @Test
    void findBeanDependents() throws Exception {
        long userServiceId = storage.insertSymbol(new Symbol(0, "UserService.java", 1, 30,
            Symbol.SymbolKind.CLASS, "UserService", "com.example.UserService",
            "class UserService", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        long orderServiceId = storage.insertSymbol(new Symbol(0, "OrderService.java", 1, 50,
            Symbol.SymbolKind.CLASS, "OrderService", "com.example.OrderService",
            "class OrderService", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertBeanDependencies(List.of(
            new BeanDependency(0, orderServiceId, userServiceId, "UserService", "CONSTRUCTOR", "userService", "OrderService.java", 10)
        ));

        var dependents = storage.findBeanDependents(userServiceId, 10);
        assertEquals(1, dependents.size());
    }

    @Test
    void findTestMappings() throws Exception {
        long testId = storage.insertSymbol(new Symbol(0, "UserServiceTest.java", 1, 30,
            Symbol.SymbolKind.CLASS, "UserServiceTest", "com.example.UserServiceTest",
            "class UserServiceTest", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertTestMappings(List.of(
            new TestMapping(0, testId, null, "UserServiceTest", "UserService", "NAME_PATTERN", "UserServiceTest.java")
        ));

        var mappings = storage.findTestMappingsBySource("UserService", 10);
        assertEquals(1, mappings.size());
    }
}
