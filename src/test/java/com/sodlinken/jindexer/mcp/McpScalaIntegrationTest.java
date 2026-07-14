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
 * Scala 支持端到端集成测试
 */
class McpScalaIntegrationTest {

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
    void fullScalaProjectIndexing() throws Exception {
        createScalaFile("src/main/scala/com/example/User.scala", """
            package com.example

            case class User(id: Long, name: String, email: String)
            """);

        createScalaFile("src/main/scala/com/example/UserRepository.scala", """
            package com.example

            trait UserRepository[T] {
                def findById(id: Long): Option[T]
                def findAll(): List[T]
                def save(item: T): Unit
            }
            """);

        createScalaFile("src/main/scala/com/example/UserRepositoryImpl.scala", """
            package com.example

            class UserRepositoryImpl extends UserRepository[User] {
                private val users = scala.collection.mutable.Map[Long, User]()

                override def findById(id: Long): Option[User] = users.get(id)
                override def findAll(): List[User] = users.values.toList
                override def save(user: User): Unit = users(user.id) = user
            }
            """);

        createScalaFile("src/main/scala/com/example/App.scala", """
            package com.example

            object App extends App {
                val repository = new UserRepositoryImpl()
                val user = User(1L, "John", "john@example.com")
                repository.save(user)
                println(s"Created user: $$user")
            }
            """);

        // 索引
        IndexResult result = indexer.index();

        // 验证索引结果
        assertEquals(4, result.totalFiles());

        // 验证符号被索引
        int symbolCount = storage.getProjectStats()[0];
        assertTrue(symbolCount > 0, "Should have indexed symbols");
    }

    @Test
    void mixedJvmProject() throws Exception {
        // Java
        createJavaFile("src/main/java/com/example/JavaHelper.java", """
            package com.example;

            public class JavaHelper {
                public static String formatName(String name) {
                    return name.toUpperCase();
                }
            }
            """);

        // Kotlin
        createKotlinFile("src/main/kotlin/com/example/KotlinService.kt", """
            package com.example

            class KotlinService {
                fun processName(name: String): String {
                    return JavaHelper.formatName(name)
                }
            }
            """);

        // Scala
        createScalaFile("src/main/scala/com/example/ScalaUtil.scala", """
            package com.example

            object ScalaUtil {
                def format(name: String): String = name.toLowerCase
            }
            """);

        // 索引
        IndexResult result = indexer.index();

        // 验证三种语言都被索引
        assertEquals(3, result.totalFiles());
    }

    private Path createScalaFile(String fileName, String content) throws Exception {
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

    private Path createKotlinFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}
