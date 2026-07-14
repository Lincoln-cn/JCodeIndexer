package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KotlinParserAdapter 测试
 */
class KotlinParserAdapterTest {

    @TempDir
    Path tempDir;

    private KotlinParserAdapter adapter;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.setProjectRoot(tempDir);
        adapter = new KotlinParserAdapter(config);
    }

    @Test
    void classExtraction() throws IOException {
        Path file = tempDir.resolve("MyClass.kt");
        Files.writeString(file, """
            package com.example

            class MyClass {
                fun greet() {}
            }
            """);

        ParseResult result = adapter.parse("MyClass.kt", file);

        Symbol classSymbol = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(classSymbol);
        assertEquals("MyClass", classSymbol.name());
        assertEquals("com.example.MyClass", classSymbol.qualifiedName());
    }

    @Test
    void dataClassExtraction() throws IOException {
        Path file = tempDir.resolve("User.kt");
        Files.writeString(file, """
            package com.example

            data class User(
                val name: String,
                val age: Int
            )
            """);

        ParseResult result = adapter.parse("User.kt", file);

        Symbol classSymbol = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(classSymbol);
        assertTrue(classSymbol.isDataClass());
        assertEquals("User", classSymbol.name());
    }

    @Test
    void objectExtraction() throws IOException {
        Path file = tempDir.resolve("Singleton.kt");
        Files.writeString(file, """
            package com.example

            object Singleton {
                fun getInstance() = this
            }
            """);

        ParseResult result = adapter.parse("Singleton.kt", file);

        Symbol classSymbol = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(classSymbol);
        assertTrue(classSymbol.isObject());
        assertEquals("Singleton", classSymbol.name());
    }

    @Test
    void sealedClassExtraction() throws IOException {
        Path file = tempDir.resolve("Result.kt");
        Files.writeString(file, """
            package com.example

            sealed class Result {
                data class Success(val data: Any) : Result()
                data class Error(val message: String) : Result()
            }
            """);

        ParseResult result = adapter.parse("Result.kt", file);

        Symbol sealedClass = result.symbols().stream()
            .filter(s -> s.isSealed())
            .findFirst()
            .orElse(null);

        assertNotNull(sealedClass);
        assertEquals("Result", sealedClass.name());
    }

    @Test
    void functionExtraction() throws IOException {
        Path file = tempDir.resolve("Utils.kt");
        Files.writeString(file, """
            package com.example

            class Utils {
                fun calculate(a: Int, b: Int): Int {
                    return a + b
                }
            }
            """);

        ParseResult result = adapter.parse("Utils.kt", file);

        Symbol method = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.METHOD)
            .findFirst()
            .orElse(null);

        assertNotNull(method);
        assertEquals("calculate", method.name());
        assertEquals("Int", method.returnType());
    }

    @Test
    void propertyExtraction() throws IOException {
        Path file = tempDir.resolve("Config.kt");
        Files.writeString(file, """
            package com.example

            class Config {
                val name: String = "default"
                var count: Int = 0
            }
            """);

        ParseResult result = adapter.parse("Config.kt", file);

        List<Symbol> fields = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.FIELD)
            .toList();

        assertTrue(fields.size() >= 2);
        assertTrue(fields.stream().anyMatch(s -> s.name().equals("name")));
        assertTrue(fields.stream().anyMatch(s -> s.name().equals("count")));
    }

    @Test
    void companionObjectExtraction() throws IOException {
        Path file = tempDir.resolve("Factory.kt");
        Files.writeString(file, """
            package com.example

            class Factory {
                companion object {
                    fun create(): Factory = Factory()
                }
            }
            """);

        ParseResult result = adapter.parse("Factory.kt", file);

        // 验证类被解析
        Symbol factoryClass = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(factoryClass);
        assertEquals("Factory", factoryClass.name());
    }

    @Test
    void importExtraction() throws IOException {
        Path file = tempDir.resolve("WithImports.kt");
        Files.writeString(file, """
            package com.example

            import java.util.List
            import kotlin.collections.MutableMap

            class WithImports {
            }
            """);

        ParseResult result = adapter.parse("WithImports.kt", file);

        assertFalse(result.references().isEmpty());
        assertTrue(result.references().size() >= 2);
    }

    @Test
    void isKotlinFile() {
        assertTrue(KotlinParserAdapter.isKotlinFile("test.kt"));
        assertTrue(KotlinParserAdapter.isKotlinFile("test.kts"));
        assertTrue(KotlinParserAdapter.isKotlinFile("TEST.KT"));
        assertFalse(KotlinParserAdapter.isKotlinFile("test.java"));
        assertFalse(KotlinParserAdapter.isKotlinFile("test.py"));
    }

    @Test
    void emptyFile() throws IOException {
        Path file = tempDir.resolve("Empty.kt");
        Files.writeString(file, "");

        ParseResult result = adapter.parse("Empty.kt", file);
        assertNotNull(result);
        assertTrue(result.symbols().isEmpty());
    }

    @Test
    void inheritanceExtraction() throws IOException {
        Path file = tempDir.resolve("Child.kt");
        Files.writeString(file, """
            package com.example

            open class Parent
            class Child : Parent()
            """);

        ParseResult result = adapter.parse("Child.kt", file);

        Symbol childClass = result.symbols().stream()
            .filter(s -> s.name().equals("Child"))
            .findFirst()
            .orElse(null);

        assertNotNull(childClass);
        assertNotNull(childClass.interfaces());
        assertTrue(childClass.interfaces().contains("Parent"));
    }

    @Test
    void interfaceImplementationExtraction() throws IOException {
        Path file = tempDir.resolve("ServiceImpl.kt");
        Files.writeString(file, """
            package com.example

            interface Serializable
            class MyDto : Serializable
            """);

        ParseResult result = adapter.parse("ServiceImpl.kt", file);

        Symbol myDto = result.symbols().stream()
            .filter(s -> s.name().equals("MyDto"))
            .findFirst()
            .orElse(null);

        assertNotNull(myDto);
        assertNotNull(myDto.interfaces());
        assertTrue(myDto.interfaces().contains("Serializable"));
    }
}
