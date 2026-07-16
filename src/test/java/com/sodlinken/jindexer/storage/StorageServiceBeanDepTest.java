package com.sodlinken.jindexer.storage;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.BeanDependency;
import com.sodlinken.jindexer.model.Symbol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceBeanDepTest {

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
    void insertAndQueryDependencies() throws Exception {
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

        List<BeanDependency> deps = storage.findBeanDependencies(beanId, 10);
        assertEquals(1, deps.size());
        assertEquals("UserService", deps.getFirst().dependsOnType());
        assertEquals("CONSTRUCTOR", deps.getFirst().injectionType());
    }

    @Test
    void queryDependents() throws Exception {
        long userServiceId = storage.insertSymbol(new Symbol(0, "UserService.java", 1, 30,
            Symbol.SymbolKind.CLASS, "UserService", "com.example.UserService",
            "class UserService", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        long orderServiceId = storage.insertSymbol(new Symbol(0, "OrderService.java", 1, 50,
            Symbol.SymbolKind.CLASS, "OrderService", "com.example.OrderService",
            "class OrderService", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        long paymentServiceId = storage.insertSymbol(new Symbol(0, "PaymentService.java", 1, 40,
            Symbol.SymbolKind.CLASS, "PaymentService", "com.example.PaymentService",
            "class PaymentService", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertBeanDependencies(List.of(
            new BeanDependency(0, orderServiceId, userServiceId, "UserService", "CONSTRUCTOR", "userService", "OrderService.java", 10),
            new BeanDependency(0, paymentServiceId, userServiceId, "UserService", "FIELD", "userService", "PaymentService.java", 15)
        ));

        List<BeanDependency> dependents = storage.findBeanDependents(userServiceId, 10);
        assertEquals(2, dependents.size());
    }

    @Test
    void deleteByFile() throws Exception {
        long beanId = storage.insertSymbol(new Symbol(0, "Service.java", 1, 50,
            Symbol.SymbolKind.CLASS, "Service", "com.example.Service",
            "class Service", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertBeanDependencies(List.of(
            new BeanDependency(0, beanId, null, "Dependency", "FIELD", "dep", "Service.java", 10)
        ));

        storage.deleteBeanDependenciesByFile("Service.java");

        List<BeanDependency> deps = storage.findBeanDependencies(beanId, 10);
        assertTrue(deps.isEmpty());
    }

    @Test
    void multipleInjectionTypes() throws Exception {
        long beanId = storage.insertSymbol(new Symbol(0, "Complex.java", 1, 50,
            Symbol.SymbolKind.CLASS, "Complex", "com.example.Complex",
            "class Complex", "", "", 1, "", null, null,
            false, false, false, false, false, false));

        storage.insertBeanDependencies(List.of(
            new BeanDependency(0, beanId, null, "DepA", "FIELD", "depA", "Complex.java", 10),
            new BeanDependency(0, beanId, null, "DepB", "CONSTRUCTOR", "depB", "Complex.java", 15),
            new BeanDependency(0, beanId, null, "DepC", "SETTER", "setDepC", "Complex.java", 20)
        ));

        List<BeanDependency> deps = storage.findBeanDependencies(beanId, 10);
        assertEquals(3, deps.size());
        
        assertTrue(deps.stream().anyMatch(d -> "FIELD".equals(d.injectionType())));
        assertTrue(deps.stream().anyMatch(d -> "CONSTRUCTOR".equals(d.injectionType())));
        assertTrue(deps.stream().anyMatch(d -> "SETTER".equals(d.injectionType())));
    }

    @Test
    void emptyDependencies() throws Exception {
        List<BeanDependency> deps = storage.findBeanDependencies(999, 10);
        assertTrue(deps.isEmpty());
    }
}
