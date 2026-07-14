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
 * MCP 工具对 Scala 代码的支持测试
 */
class McpScalaTest {

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
    void findSymbolInScala() throws Exception {
        createScalaFile("UserService.scala", """
            package com.example

            class UserService {
                def save() {}
            }
            """);

        indexer.index();

        List<Symbol> results = storage.searchSymbolsByName("UserService", 10);
        assertFalse(results.isEmpty());
        assertEquals("UserService", results.getFirst().name());
    }

    @Test
    void findMethodInScala() throws Exception {
        createScalaFile("UserService.scala", """
            package com.example

            class UserService {
                def save() {}
                def find(id: Long) {}
            }
            """);

        indexer.index();

        List<Symbol> results = storage.searchSymbolsByName("save", 10);
        assertFalse(results.isEmpty());
        assertEquals("save", results.getFirst().name());
    }

    @Test
    void findCaseClassInScala() throws Exception {
        createScalaFile("User.scala", """
            package com.example

            case class User(name: String, age: Int)
            """);

        indexer.index();

        List<Symbol> results = storage.searchSymbolsByName("User", 10);
        assertFalse(results.isEmpty());
    }

    @Test
    void findTraitInScala() throws Exception {
        createScalaFile("Repository.scala", """
            package com.example

            trait Repository[T] {
                def findById(id: Long): Option[T]
            }
            """);

        indexer.index();

        List<Symbol> results = storage.searchSymbolsByName("Repository", 10);
        assertFalse(results.isEmpty());
    }

    @Test
    void mixedJavaKotlinScalaProject() throws Exception {
        // Java file
        createJavaFile("JavaService.java", """
            package com.example;

            public class JavaService {
                public void process() {}
            }
            """);

        // Kotlin file
        createKotlinFile("KotlinService.kt", """
            package com.example

            class KotlinService {
                fun process() {}
            }
            """);

        // Scala file
        createScalaFile("ScalaService.scala", """
            package com.example

            class ScalaService {
                def process() {}
            }
            """);

        indexer.index();

        // All three should be indexed
        List<Symbol> javaSymbols = storage.findSymbolsByFile("JavaService.java");
        List<Symbol> kotlinSymbols = storage.findSymbolsByFile("KotlinService.kt");
        List<Symbol> scalaSymbols = storage.findSymbolsByFile("ScalaService.scala");

        assertFalse(javaSymbols.isEmpty());
        assertFalse(kotlinSymbols.isEmpty());
        assertFalse(scalaSymbols.isEmpty());
    }

    private Path createScalaFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    private Path createJavaFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    private Path createKotlinFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}
