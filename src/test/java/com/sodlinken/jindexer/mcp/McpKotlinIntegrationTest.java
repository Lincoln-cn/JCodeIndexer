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
 * Kotlin 支持端到端集成测试
 */
class McpKotlinIntegrationTest {

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
    void fullKotlinProjectIndexing() throws Exception {
        // 创建一个完整的 Kotlin 项目结构
        createKotlinFile("src/main/kotlin/com/example/User.kt", """
            package com.example

            data class User(
                val id: Long,
                val name: String,
                val email: String
            )
            """);

        createKotlinFile("src/main/kotlin/com/example/UserRepository.kt", """
            package com.example

            interface UserRepository {
                fun findById(id: Long): User?
                fun findAll(): List<User>
                fun save(user: User)
            }
            """);

        createKotlinFile("src/main/kotlin/com/example/UserRepositoryImpl.kt", """
            package com.example

            class UserRepositoryImpl : UserRepository {
                private val users = mutableMapOf<Long, User>()

                override fun findById(id: Long): User? = users[id]

                override fun findAll(): List<User> = users.values.toList()

                override fun save(user: User) {
                    users[user.id] = user
                }
            }
            """);

        createKotlinFile("src/main/kotlin/com/example/UserService.kt", """
            package com.example

            class UserService(private val repository: UserRepository) {
                fun getUser(id: Long): User? = repository.findById(id)

                fun createUser(name: String, email: String): User {
                    val user = User(System.currentTimeMillis(), name, email)
                    repository.save(user)
                    return user
                }

                fun listUsers(): List<User> = repository.findAll()
            }
            """);

        createKotlinFile("src/main/kotlin/com/example/App.kt", """
            package com.example

            fun main() {
                val repository = UserRepositoryImpl()
                val service = UserService(repository)

                val user = service.createUser("John", "john@example.com")
                println("Created user: ${"$"}user")

                val found = service.getUser(user.id)
                println("Found user: ${"$"}found")
            }
            """);

        // 索引
        IndexResult result = indexer.index();

        // 验证索引结果
        assertEquals(5, result.totalFiles());

        // 验证符号被索引
        int symbolCount = storage.getProjectStats()[0];
        assertTrue(symbolCount > 0, "Should have indexed symbols");
    }

    @Test
    void kotlinWithJavaInterop() throws Exception {
        // 创建 Java 文件
        createJavaFile("src/main/java/com/example/JavaHelper.java", """
            package com.example;

            public class JavaHelper {
                public static String formatName(String name) {
                    return name.toUpperCase();
                }
            }
            """);

        // 创建 Kotlin 文件引用 Java
        createKotlinFile("src/main/kotlin/com/example/KotlinService.kt", """
            package com.example

            class KotlinService {
                fun processName(name: String): String {
                    return JavaHelper.formatName(name)
                }
            }
            """);

        // 索引
        IndexResult result = indexer.index();

        // 验证两种语言都被索引
        List<Symbol> javaSymbols = storage.findSymbolsByFile("src/main/java/com/example/JavaHelper.java");
        List<Symbol> kotlinSymbols = storage.findSymbolsByFile("src/main/kotlin/com/example/KotlinService.kt");

        assertFalse(javaSymbols.isEmpty());
        assertFalse(kotlinSymbols.isEmpty());
    }

    @Test
    void incrementalKotlinIndexing() throws Exception {
        // 初始索引
        createKotlinFile("Service.kt", """
            package com.example

            class Service {
                fun method1() {}
            }
            """);

        indexer.index();

        // 添加新文件
        createKotlinFile("NewService.kt", """
            package com.example

            class NewService {
                fun newMethod() {}
            }
            """);

        IndexResult result = indexer.index();

        // 验证新文件被索引
        List<Symbol> newSymbols = storage.findSymbolsByFile("NewService.kt");
        assertFalse(newSymbols.isEmpty());
    }

    private Path createKotlinFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private Path createJavaFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}
