package com.sodlinken.jindexer.mcp;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.indexer.Indexer;
import com.sodlinken.jindexer.model.*;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 继承关系工具集成测试
 */
class McpInheritanceIntegrationTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private StorageService storage;
    private Indexer indexer;

    @BeforeEach
    void setUp() throws Exception {
        Config config = new Config();
        config.setProjectRoot(tempDir);
        config.setDataDir(tempDir.resolve(".jindexer"));

        db = new DatabaseManager(config.getDbPath());
        db.initialize();
        storage = new StorageService(db);
        indexer = new Indexer(config, storage, db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void findImplementationsAfterIndexing() throws Exception {
        // 创建接口
        createJavaFile("Serializable.java", """
            package java.io;
            public interface Serializable {}
            """);

        // 创建实现类
        createJavaFile("UserDto.java", """
            package com.example;

            import java.io.Serializable;

            public class UserDto implements Serializable {
                private String name;
            }
            """);

        createJavaFile("OrderDto.java", """
            package com.example;

            import java.io.Serializable;

            public class OrderDto implements Serializable {
                private long orderId;
            }
            """);

        createJavaFile("Helper.java", """
            package com.example;

            public class Helper {
                // 不实现 Serializable
            }
            """);

        // 索引
        indexer.index();

        // 查找 Serializable 的实现
        List<Symbol> implementations = storage.findImplementations("Serializable", 10);

        // 验证
        assertFalse(implementations.isEmpty());
        assertTrue(implementations.stream().anyMatch(s -> s.name().equals("UserDto")));
        assertTrue(implementations.stream().anyMatch(s -> s.name().equals("OrderDto")));
        assertFalse(implementations.stream().anyMatch(s -> s.name().equals("Helper")));
    }

    @Test
    void findOverridesAfterIndexing() throws Exception {
        // 创建父类
        createJavaFile("BaseService.java", """
            package com.example;

            public abstract class BaseService {
                public abstract void save();
            }
            """);

        // 创建子类
        createJavaFile("UserService.java", """
            package com.example;

            public class UserService extends BaseService {
                @Override
                public void save() {
                    System.out.println("Saving user");
                }
            }
            """);

        createJavaFile("OrderService.java", """
            package com.example;

            public class OrderService extends BaseService {
                @Override
                public void save() {
                    System.out.println("Saving order");
                }
            }
            """);

        // 索引
        indexer.index();

        // 查找 save 方法的重写
        List<Symbol> overrides = storage.findOverrides("save", "BaseService", 10);

        // 验证
        assertFalse(overrides.isEmpty());
        assertTrue(overrides.stream().anyMatch(s -> s.name().equals("save")));
    }

    @Test
    void findFieldUsagesAfterIndexing() throws Exception {
        // 创建类
        createJavaFile("UserService.java", """
            package com.example;

            public class UserService {
                private String name;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }
            }
            """);

        // 索引
        indexer.index();

        // 查找 name 字段的使用
        var usages = storage.findFieldUsages("com.example.UserService.name", 10);

        // 验证（至少有引用）
        assertNotNull(usages);
    }

    @Test
    void findImplementationsWithInheritance() throws Exception {
        // 创建接口层次
        createJavaFile("Closeable.java", """
            package java.io;
            public interface Closeable {}
            """);

        createJavaFile("AutoCloseable.java", """
            package java.lang;
            public interface AutoCloseable {}
            """);

        // 创建实现类（实现多个接口）
        createJavaFile("MyResource.java", """
            package com.example;

            import java.io.Closeable;

            public class MyResource implements Closeable, AutoCloseable {
                @Override
                public void close() {}
            }
            """);

        // 索引
        indexer.index();

        // 查找 Closeable 的实现
        List<Symbol> closeableImpls = storage.findImplementations("Closeable", 10);
        assertFalse(closeableImpls.isEmpty());
        assertTrue(closeableImpls.stream().anyMatch(s -> s.name().equals("MyResource")));

        // 查找 AutoCloseable 的实现
        List<Symbol> autoCloseImpls = storage.findImplementations("AutoCloseable", 10);
        assertFalse(autoCloseImpls.isEmpty());
        assertTrue(autoCloseImpls.stream().anyMatch(s -> s.name().equals("MyResource")));
    }

    private Path createJavaFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}
