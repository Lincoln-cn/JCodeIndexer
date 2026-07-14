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
 * MCP 工具对 Kotlin 代码的支持测试
 */
class McpKotlinTest {

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
    void findSymbolInKotlin() throws Exception {
        createKotlinFile("UserService.kt", """
            package com.example

            class UserService {
                fun save() {}
            }
            """);

        indexer.index();

        List<Symbol> results = storage.searchSymbolsByName("UserService", 10);
        assertFalse(results.isEmpty());
        assertEquals("UserService", results.getFirst().name());
    }

    @Test
    void findMethodInKotlin() throws Exception {
        createKotlinFile("UserService.kt", """
            package com.example

            class UserService {
                fun save() {}
                fun find(id: Long) {}
            }
            """);

        indexer.index();

        List<Symbol> results = storage.searchSymbolsByName("save", 10);
        assertFalse(results.isEmpty());
        assertEquals("save", results.getFirst().name());
    }

    @Test
    void findDataClassInKotlin() throws Exception {
        createKotlinFile("User.kt", """
            package com.example

            data class User(
                val name: String,
                val age: Int
            )
            """);

        indexer.index();

        List<Symbol> results = storage.searchSymbolsByName("User", 10);
        assertFalse(results.isEmpty());

        Symbol userClass = results.stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(userClass);
        assertTrue(userClass.isDataClass());
    }

    @Test
    void findObjectInKotlin() throws Exception {
        createKotlinFile("Singleton.kt", """
            package com.example

            object Singleton {
                fun getInstance() = this
            }
            """);

        indexer.index();

        List<Symbol> results = storage.searchSymbolsByName("Singleton", 10);
        assertFalse(results.isEmpty());

        Symbol singletonClass = results.stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(singletonClass);
        assertTrue(singletonClass.isObject());
    }

    @Test
    void searchCodeInKotlin() throws Exception {
        createKotlinFile("Utils.kt", """
            package com.example

            class Utils {
                fun process(data: String): String {
                    return data.uppercase()
                }
            }
            """);

        indexer.index();

        // 验证代码块被创建
        var chunks = storage.findChunksByFile("Utils.kt");
        assertNotNull(chunks);
    }

    @Test
    void findImplementationsInKotlin() throws Exception {
        createKotlinFile("Repository.kt", """
            package com.example

            interface Repository {
                fun findById(id: Long)
            }
            """);

        createKotlinFile("UserRepository.kt", """
            package com.example

            class UserRepository : Repository {
                override fun findById(id: Long) {}
            }
            """);

        indexer.index();

        List<Symbol> implementations = storage.findImplementations("Repository", 10);
        assertFalse(implementations.isEmpty());
        assertTrue(implementations.stream().anyMatch(s -> s.name().equals("UserRepository")));
    }

    @Test
    void findReferencesInKotlin() throws Exception {
        createKotlinFile("Config.kt", """
            package com.example

            class Config {
                val name: String = "default"
            }
            """);

        createKotlinFile("App.kt", """
            package com.example

            class App {
                val config = Config()
            }
            """);

        indexer.index();

        List<Symbol> configSymbols = storage.searchSymbolsByName("Config", 10);
        assertFalse(configSymbols.isEmpty());

        long configId = configSymbols.getFirst().id();
        var refs = storage.findReferencesBySymbol(configId);
        assertNotNull(refs);
    }

    @Test
    void mixedJavaKotlinProject() throws Exception {
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

        indexer.index();

        // Both should be indexed
        List<Symbol> javaSymbols = storage.findSymbolsByFile("JavaService.java");
        List<Symbol> kotlinSymbols = storage.findSymbolsByFile("KotlinService.kt");

        assertFalse(javaSymbols.isEmpty());
        assertFalse(kotlinSymbols.isEmpty());
    }

    @Test
    void incrementalIndexKotlin() throws Exception {
        createKotlinFile("Service.kt", """
            package com.example

            class Service {
                fun method1() {}
            }
            """);

        indexer.index();

        // Add new method
        modifyKotlinFile("Service.kt", """
            package com.example

            class Service {
                fun method1() {}
                fun method2() {}
            }
            """);

        indexer.index();

        // Should have both methods
        List<Symbol> symbols = storage.findSymbolsByFile("Service.kt");
        long methodCount = symbols.stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.METHOD)
            .count();
        assertEquals(2, methodCount);
    }

    private Path createKotlinFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    private Path createJavaFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    private void modifyKotlinFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
    }
}
