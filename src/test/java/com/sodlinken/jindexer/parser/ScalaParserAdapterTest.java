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
 * ScalaParserAdapter 测试
 */
class ScalaParserAdapterTest {

    @TempDir
    Path tempDir;

    private ScalaParserAdapter adapter;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.setProjectRoot(tempDir);
        adapter = new ScalaParserAdapter(config);
    }

    @Test
    void classExtraction() throws IOException {
        Path file = tempDir.resolve("MyClass.scala");
        Files.writeString(file, """
            package com.example

            class MyClass {
                def greet() {}
            }
            """);

        ParseResult result = adapter.parse("MyClass.scala", file);

        Symbol classSymbol = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(classSymbol);
        assertEquals("MyClass", classSymbol.name());
        assertEquals("com.example.MyClass", classSymbol.qualifiedName());
    }

    @Test
    void caseClassExtraction() throws IOException {
        Path file = tempDir.resolve("User.scala");
        Files.writeString(file, """
            package com.example

            case class User(name: String, age: Int)
            """);

        ParseResult result = adapter.parse("User.scala", file);

        Symbol classSymbol = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(classSymbol);
        assertTrue(classSymbol.isCaseClass());
        assertEquals("User", classSymbol.name());
    }

    @Test
    void objectExtraction() throws IOException {
        Path file = tempDir.resolve("Singleton.scala");
        Files.writeString(file, """
            package com.example

            object Singleton {
                def getInstance = this
            }
            """);

        ParseResult result = adapter.parse("Singleton.scala", file);

        Symbol objectSymbol = result.symbols().stream()
            .filter(s -> s.isObject())
            .findFirst()
            .orElse(null);

        assertNotNull(objectSymbol);
        assertEquals("Singleton", objectSymbol.name());
    }

    @Test
    void traitExtraction() throws IOException {
        Path file = tempDir.resolve("Repository.scala");
        Files.writeString(file, """
            package com.example

            trait Repository[T] {
                def findById(id: Long): Option[T]
            }
            """);

        ParseResult result = adapter.parse("Repository.scala", file);

        Symbol traitSymbol = result.symbols().stream()
            .filter(s -> s.isTrait())
            .findFirst()
            .orElse(null);

        assertNotNull(traitSymbol);
        assertEquals("Repository", traitSymbol.name());
    }

    @Test
    void functionExtraction() throws IOException {
        Path file = tempDir.resolve("Utils.scala");
        Files.writeString(file, """
            package com.example

            class Utils {
                def calculate(a: Int, b: Int): Int = a + b
            }
            """);

        ParseResult result = adapter.parse("Utils.scala", file);

        Symbol method = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.METHOD)
            .findFirst()
            .orElse(null);

        assertNotNull(method);
        assertEquals("calculate", method.name());
    }

    @Test
    void valueExtraction() throws IOException {
        Path file = tempDir.resolve("Config.scala");
        Files.writeString(file, """
            package com.example

            class Config {
                val name: String = "default"
                var count: Int = 0
            }
            """);

        ParseResult result = adapter.parse("Config.scala", file);

        List<Symbol> fields = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.FIELD)
            .toList();

        assertTrue(fields.size() >= 2);
        assertTrue(fields.stream().anyMatch(s -> s.name().equals("name")));
        assertTrue(fields.stream().anyMatch(s -> s.name().equals("count")));
    }

    @Test
    void importExtraction() throws IOException {
        Path file = tempDir.resolve("WithImports.scala");
        Files.writeString(file, """
            package com.example

            import java.util.List
            import scala.collection.mutable.Map

            class WithImports {
            }
            """);

        ParseResult result = adapter.parse("WithImports.scala", file);

        assertFalse(result.references().isEmpty());
        assertTrue(result.references().size() >= 2);
    }

    @Test
    void isScalaFile() {
        assertTrue(ScalaParserAdapter.isScalaFile("test.scala"));
        assertTrue(ScalaParserAdapter.isScalaFile("test.sc"));
        assertTrue(ScalaParserAdapter.isScalaFile("TEST.SCALA"));
        assertFalse(ScalaParserAdapter.isScalaFile("test.java"));
        assertFalse(ScalaParserAdapter.isScalaFile("test.kt"));
    }

    @Test
    void emptyFile() throws IOException {
        Path file = tempDir.resolve("Empty.scala");
        Files.writeString(file, "");

        ParseResult result = adapter.parse("Empty.scala", file);
        assertNotNull(result);
        assertTrue(result.symbols().isEmpty());
    }

    @Test
    void inheritanceExtraction() throws IOException {
        Path file = tempDir.resolve("Child.scala");
        Files.writeString(file, """
            package com.example

            class Parent
            class Child extends Parent
            """);

        ParseResult result = adapter.parse("Child.scala", file);

        Symbol childClass = result.symbols().stream()
            .filter(s -> s.name().equals("Child"))
            .findFirst()
            .orElse(null);

        assertNotNull(childClass);
        assertNotNull(childClass.interfaces());
        assertTrue(childClass.interfaces().contains("Parent"));
    }

    @Test
    void traitImplementationExtraction() throws IOException {
        Path file = tempDir.resolve("ServiceImpl.scala");
        Files.writeString(file, """
            package com.example

            trait Serializable
            class MyDto extends Serializable
            """);

        ParseResult result = adapter.parse("ServiceImpl.scala", file);

        Symbol myDto = result.symbols().stream()
            .filter(s -> s.name().equals("MyDto"))
            .findFirst()
            .orElse(null);

        assertNotNull(myDto);
        assertNotNull(myDto.interfaces());
        assertTrue(myDto.interfaces().contains("Serializable"));
    }

    @Test
    void annotationExtraction() throws IOException {
        Path file = tempDir.resolve("Annotated.scala");
        Files.writeString(file, """
            package com.example

            @deprecated
            class OldClass {
                def oldMethod() {}
            }
            """);

        ParseResult result = adapter.parse("Annotated.scala", file);

        assertFalse(result.annotations().isEmpty());
        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("deprecated")));
    }
}
