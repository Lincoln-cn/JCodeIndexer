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
 * 注解扩展测试
 */
class McpAnnotationExtendedTest {

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
    void springBootProject() throws Exception {
        createJavaFile("Application.java", """
            package com.example;

            @SpringBootApplication
            public class Application {
                public static void main(String[] args) {
                    SpringApplication.run(Application.class, args);
                }
            }
            """);

        createJavaFile("UserController.java", """
            package com.example;

            @RestController
            @RequestMapping("/api/users")
            public class UserController {
                @GetMapping("/{id}")
                public User getUser(@PathVariable Long id) {
                    return null;
                }
            }
            """);

        createJavaFile("UserService.java", """
            package com.example;

            @Service
            @Transactional
            public class UserService {
                public User findById(Long id) {
                    return null;
                }
            }
            """);

        createJavaFile("UserRepository.java", """
            package com.example;

            @Repository
            public interface UserRepository {
                User findById(Long id);
            }
            """);

        indexer.index();

        // 验证注解被索引
        var restControllers = storage.findByAnnotation("RestController", 10);
        var services = storage.findByAnnotation("Service", 10);
        var repositories = storage.findByAnnotation("Repository", 10);

        assertNotNull(restControllers);
        assertNotNull(services);
        assertNotNull(repositories);
    }

    @Test
    void jpaProject() throws Exception {
        createJavaFile("User.java", """
            package com.example;

            @Entity
            @Table(name = "users")
            public class User {
                @Id
                @GeneratedValue(strategy = GenerationType.IDENTITY)
                private Long id;

                @Column(name = "user_name")
                private String name;
            }
            """);

        createJavaFile("UserRepository.java", """
            package com.example;

            @Repository
            public interface UserRepository {
                User findById(Long id);
            }
            """);

        indexer.index();

        var entities = storage.findByAnnotation("Entity", 10);
        assertNotNull(entities);
    }

    @Test
    void mixedAnnotationsProject() throws Exception {
        createJavaFile("Controller.java", """
            package com.example;

            @RestController
            @RequestMapping("/api")
            public class Controller {
                @GetMapping("/users")
                @CrossOrigin
                public void listUsers() {}
            }
            """);

        createJavaFile("Service.java", """
            package com.example;

            @Service
            @Lazy
            public class Service {
                @Cacheable("data")
                public void process() {}
            }
            """);

        indexer.index();

        // 验证多种注解都被索引
        var restControllers = storage.findByAnnotation("RestController", 10);
        var services = storage.findByAnnotation("Service", 10);
        var lazy = storage.findByAnnotation("Lazy", 10);

        assertNotNull(restControllers);
        assertNotNull(services);
        assertNotNull(lazy);
    }

    private Path createJavaFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}
