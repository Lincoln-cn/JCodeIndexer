package com.sodlinken.jindexer.storage;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.Symbol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypeHierarchyTest {

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
    void findParentClass() throws Exception {
        // BaseService
        storage.insertSymbol(new Symbol(0, "Base.java", 1, 50,
            Symbol.SymbolKind.CLASS, "BaseService", "com.example.BaseService",
            "class BaseService", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        // UserService extends BaseService
        storage.insertSymbol(new Symbol(0, "User.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserService", "com.example.UserService",
            "class UserService extends BaseService", "", "", 1, "",
            "BaseService", null,
            false, false, false, false, false, false));

        List<Symbol> hierarchy = storage.findTypeHierarchyUp("com.example.UserService", 10);
        
        // 应该找到 UserService 和 BaseService
        assertTrue(hierarchy.size() >= 2);
        assertTrue(hierarchy.stream().anyMatch(s -> "UserService".equals(s.name())));
        assertTrue(hierarchy.stream().anyMatch(s -> "BaseService".equals(s.name())));
    }

    @Test
    void findImplementedInterface() throws Exception {
        // Serializable
        storage.insertSymbol(new Symbol(0, "Serial.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Serializable", "java.io.Serializable",
            "interface Serializable", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        // UserDto implements Serializable
        storage.insertSymbol(new Symbol(0, "Dto.java", 1, 20,
            Symbol.SymbolKind.CLASS, "UserDto", "com.example.UserDto",
            "class UserDto implements Serializable", "", "", 1, "",
            null, List.of("Serializable"),
            false, false, false, false, false, false));

        List<Symbol> hierarchy = storage.findTypeHierarchyUp("com.example.UserDto", 10);
        
        assertTrue(hierarchy.size() >= 2);
        assertTrue(hierarchy.stream().anyMatch(s -> "UserDto".equals(s.name())));
        assertTrue(hierarchy.stream().anyMatch(s -> "Serializable".equals(s.name())));
    }

    @Test
    void findChildren() throws Exception {
        // BaseService
        storage.insertSymbol(new Symbol(0, "Base.java", 1, 50,
            Symbol.SymbolKind.CLASS, "BaseService", "com.example.BaseService",
            "class BaseService", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        // UserService extends BaseService
        storage.insertSymbol(new Symbol(0, "User.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserService", "com.example.UserService",
            "class UserService extends BaseService", "", "", 1, "",
            "BaseService", null,
            false, false, false, false, false, false));

        // OrderService extends BaseService
        storage.insertSymbol(new Symbol(0, "Order.java", 1, 50,
            Symbol.SymbolKind.CLASS, "OrderService", "com.example.OrderService",
            "class OrderService extends BaseService", "", "", 1, "",
            "BaseService", null,
            false, false, false, false, false, false));

        List<Symbol> children = storage.findTypeHierarchyDown("BaseService", 10);
        
        assertTrue(children.size() >= 2);
        assertTrue(children.stream().anyMatch(s -> "UserService".equals(s.name())));
        assertTrue(children.stream().anyMatch(s -> "OrderService".equals(s.name())));
    }

    @Test
    void findInterfaceImplementations() throws Exception {
        // Repository interface
        storage.insertSymbol(new Symbol(0, "Repo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Repository", "com.example.Repository",
            "interface Repository", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        // UserRepository implements Repository
        storage.insertSymbol(new Symbol(0, "UserRepo.java", 1, 30,
            Symbol.SymbolKind.CLASS, "UserRepository", "com.example.UserRepository",
            "class UserRepository implements Repository", "", "", 1, "",
            null, List.of("Repository"),
            false, false, false, false, false, false));

        List<Symbol> children = storage.findTypeHierarchyDown("Repository", 10);
        
        assertFalse(children.isEmpty());
        assertTrue(children.stream().anyMatch(s -> "UserRepository".equals(s.name())));
    }

    @Test
    void multiLevelHierarchy() throws Exception {
        // Object
        storage.insertSymbol(new Symbol(0, "Obj.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Object", "java.lang.Object",
            "class Object", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        // BaseService extends Object
        storage.insertSymbol(new Symbol(0, "Base.java", 1, 50,
            Symbol.SymbolKind.CLASS, "BaseService", "com.example.BaseService",
            "class BaseService", "", "", 1, "",
            "java.lang.Object", null,
            false, false, false, false, false, false));

        // UserService extends BaseService
        storage.insertSymbol(new Symbol(0, "User.java", 1, 50,
            Symbol.SymbolKind.CLASS, "UserService", "com.example.UserService",
            "class UserService extends BaseService", "", "", 1, "",
            "BaseService", null,
            false, false, false, false, false, false));

        List<Symbol> hierarchy = storage.findTypeHierarchyUp("com.example.UserService", 10);
        
        // 应该找到 UserService → BaseService → Object
        assertTrue(hierarchy.size() >= 2);
    }

    @Test
    void limitResults() throws Exception {
        // Base
        storage.insertSymbol(new Symbol(0, "Base.java", 1, 50,
            Symbol.SymbolKind.CLASS, "Base", "com.example.Base",
            "class Base", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        // A extends Base
        storage.insertSymbol(new Symbol(0, "A.java", 1, 50,
            Symbol.SymbolKind.CLASS, "A", "com.example.A",
            "class A extends Base", "", "", 1, "",
            "Base", null,
            false, false, false, false, false, false));

        // B extends Base
        storage.insertSymbol(new Symbol(0, "B.java", 1, 50,
            Symbol.SymbolKind.CLASS, "B", "com.example.B",
            "class B extends Base", "", "", 1, "",
            "Base", null,
            false, false, false, false, false, false));

        // C extends Base
        storage.insertSymbol(new Symbol(0, "C.java", 1, 50,
            Symbol.SymbolKind.CLASS, "C", "com.example.C",
            "class C extends Base", "", "", 1, "",
            "Base", null,
            false, false, false, false, false, false));

        List<Symbol> children = storage.findTypeHierarchyDown("Base", 2);
        
        assertEquals(2, children.size());
    }

    @Test
    void noHierarchy() throws Exception {
        // Standalone class
        storage.insertSymbol(new Symbol(0, "Standalone.java", 1, 50,
            Symbol.SymbolKind.CLASS, "Standalone", "com.example.Standalone",
            "class Standalone", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        List<Symbol> parents = storage.findTypeHierarchyUp("com.example.Standalone", 10);
        
        // 只有自己
        assertEquals(1, parents.size());
        assertEquals("Standalone", parents.getFirst().name());
    }
}
