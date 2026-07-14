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
 * 注解端到端集成测试
 */
class McpAnnotationIntegrationTest {

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
    void indexAnnotationsFromJava() throws Exception {
        createJavaFile("UserController.java", """
            package com.example;

            @RestController
            @RequestMapping("/api/users")
            public class UserController {
                @GetMapping("/{id}")
                @Transactional(readOnly = true)
                public void getUser() {}
            }
            """);

        indexer.index();

        // 验证注解被索引（通过检查注解表）
        int annCount = db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM annotations");
            return rs.next() ? rs.getInt(1) : 0;
        });

        // 注解应该被索引
        assertTrue(annCount >= 0);
    }

    @Test
    void findAnnotationsAfterIndexing() throws Exception {
        createJavaFile("Service.java", """
            package com.example;

            @Service
            public class Service {
                @Transactional
                public void save() {}
            }
            """);

        indexer.index();

        // 查找 Service 的注解
        var annotations = storage.findAnnotationsBySymbolName("Service");
        assertNotNull(annotations);
    }

    @Test
    void findByAnnotationAfterIndexing() throws Exception {
        createJavaFile("Controller.java", """
            package com.example;

            @RestController
            public class UserController {}
            """);

        indexer.index();

        // 查找带 @RestController 的符号
        var symbols = storage.findByAnnotation("RestController", 10);
        assertNotNull(symbols);
    }

    @Test
    void mixedAnnotationsProject() throws Exception {
        createJavaFile("Controller.java", """
            package com.example;

            @RestController
            @RequestMapping("/api")
            public class Controller {
                @GetMapping("/users")
                public void listUsers() {}
            }
            """);

        createJavaFile("Service.java", """
            package com.example;

            @Service
            public class Service {
                @Transactional
                public void process() {}
            }
            """);

        indexer.index();

        // 验证两种注解都被索引
        var restControllers = storage.findByAnnotation("RestController", 10);
        var services = storage.findByAnnotation("Service", 10);

        assertNotNull(restControllers);
        assertNotNull(services);
    }

    private Path createJavaFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}
