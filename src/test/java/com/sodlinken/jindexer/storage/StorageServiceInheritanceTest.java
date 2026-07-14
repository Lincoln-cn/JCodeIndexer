package com.sodlinken.jindexer.storage;

import com.sodlinken.jindexer.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StorageService 继承关系查询测试
 */
class StorageServiceInheritanceTest {

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
    void findImplementationsByInterface() throws Exception {
        // 插入接口实现类
        storage.insertSymbol(new Symbol(0, "SerializableImpl.java", 1, 10,
            Symbol.SymbolKind.CLASS, "UserDto", "com.app.UserDto",
            "class UserDto", null, null, 1, null,
            null, List.of("java.io.Serializable")));

        storage.insertSymbol(new Symbol(0, "SerializableImpl2.java", 1, 10,
            Symbol.SymbolKind.CLASS, "OrderDto", "com.app.OrderDto",
            "class OrderDto", null, null, 1, null,
            null, List.of("java.io.Serializable")));

        storage.insertSymbol(new Symbol(0, "NonSerializable.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Helper", "com.app.Helper",
            "class Helper", null, null, 1, null,
            null, null));

        List<Symbol> results = storage.findImplementations("Serializable", 10);
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(s -> s.name().equals("UserDto")));
        assertTrue(results.stream().anyMatch(s -> s.name().equals("OrderDto")));
    }

    @Test
    void findImplementationsBySuperClass() throws Exception {
        // 插入继承关系
        storage.insertSymbol(new Symbol(0, "BaseService.java", 1, 10,
            Symbol.SymbolKind.CLASS, "BaseService", "com.app.BaseService",
            "class BaseService", null, null, 1, null,
            null, null));

        storage.insertSymbol(new Symbol(0, "UserService.java", 1, 10,
            Symbol.SymbolKind.CLASS, "UserService", "com.app.UserService",
            "class UserService", null, null, 1, null,
            "BaseService", null));

        storage.insertSymbol(new Symbol(0, "OrderService.java", 1, 10,
            Symbol.SymbolKind.CLASS, "OrderService", "com.app.OrderService",
            "class OrderService", null, null, 1, null,
            "BaseService", null));

        List<Symbol> results = storage.findImplementations("BaseService", 10);
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(s -> s.name().equals("UserService")));
        assertTrue(results.stream().anyMatch(s -> s.name().equals("OrderService")));
    }

    @Test
    void findImplementationsNoMatch() throws Exception {
        storage.insertSymbol(new Symbol(0, "Foo.java", 1, 10,
            Symbol.SymbolKind.CLASS, "Foo", "com.app.Foo",
            "class Foo", null, null, 1, null,
            null, null));

        List<Symbol> results = storage.findImplementations("NonExistent", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void findOverridesMethod() throws Exception {
        // 插入父类
        storage.insertSymbol(new Symbol(0, "BaseService.java", 1, 10,
            Symbol.SymbolKind.CLASS, "BaseService", "com.app.BaseService",
            "class BaseService", null, null, 1, null,
            null, null));

        // 插入父类方法
        storage.insertSymbol(new Symbol(0, "BaseService.java", 5, 8,
            Symbol.SymbolKind.METHOD, "save", "com.app.BaseService.save",
            "void save()", "void", "BaseService", 1, null,
            null, null));

        // 插入子类（继承 BaseService）
        storage.insertSymbol(new Symbol(0, "UserService.java", 1, 20,
            Symbol.SymbolKind.CLASS, "UserService", "com.app.UserService",
            "class UserService", null, null, 1, null,
            "BaseService", null));

        // 插入子类重写方法
        storage.insertSymbol(new Symbol(0, "UserService.java", 10, 15,
            Symbol.SymbolKind.METHOD, "save", "com.app.UserService.save",
            "void save()", "void", "UserService", 1, null,
            null, null));

        List<Symbol> results = storage.findOverrides("save", "BaseService", 10);
        assertEquals(1, results.size());
        assertEquals("com.app.UserService.save", results.getFirst().qualifiedName());
    }

    @Test
    void findOverridesNoMatch() throws Exception {
        List<Symbol> results = storage.findOverrides("nonexistent", "NonExistent", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void findFieldUsagesByQualifiedName() throws Exception {
        // 插入字段
        storage.insertSymbol(new Symbol(0, "UserService.java", 1, 10,
            Symbol.SymbolKind.CLASS, "UserService", "com.app.UserService",
            "class UserService", null, null, 1, null, null, null));

        long fieldId = storage.insertSymbol(new Symbol(0, "UserService.java", 5, 5,
            Symbol.SymbolKind.FIELD, "name", "com.app.UserService.name",
            "String name", "String", "UserService", 1, null, null, null));

        // 插入引用
        storage.insertReference(new Reference(0, fieldId, "Controller.java", 20, "userService.name"));
        storage.insertReference(new Reference(0, fieldId, "Test.java", 5, "user.name"));

        var usages = storage.findFieldUsages("com.app.UserService.name", 10);
        assertEquals(2, usages.size());
    }

    @Test
    void findFieldUsagesNoMatch() throws Exception {
        var usages = storage.findFieldUsages("nonexistent.field", 10);
        assertTrue(usages.isEmpty());
    }

    @Test
    void findImplementationsWithLimit() throws Exception {
        // 插入多个实现类
        for (int i = 0; i < 10; i++) {
            storage.insertSymbol(new Symbol(0, "Impl" + i + ".java", 1, 10,
                Symbol.SymbolKind.CLASS, "Impl" + i, "com.app.Impl" + i,
                "class Impl" + i, null, null, 1, null,
                null, List.of("java.io.Serializable")));
        }

        List<Symbol> results = storage.findImplementations("Serializable", 5);
        assertEquals(5, results.size());
    }
}
