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

class JavaParserAdapterTest {

    @TempDir
    Path tempDir;

    private JavaParserAdapter adapter;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.setProjectRoot(tempDir);
        adapter = new JavaParserAdapter(config);
    }

    @Test
    void classSymbolExtraction() throws IOException {
        Path file = tempDir.resolve("MyClass.java");
        Files.writeString(file, """
            package com.example;
            
            public class MyClass {
                private int x;
            }
            """);

        ParseResult result = adapter.parse("MyClass.java", file);
        
        assertFalse(result.symbols().isEmpty());
        Symbol classSymbol = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);
        
        assertNotNull(classSymbol);
        assertEquals("MyClass", classSymbol.name());
        assertEquals("com.example.MyClass", classSymbol.qualifiedName());
    }

    @Test
    void methodSymbolExtraction() throws IOException {
        Path file = tempDir.resolve("Methods.java");
        Files.writeString(file, """
            package com.example;
            
            public class Methods {
                public void doSomething() {
                }
                
                private int calculate(int a, int b) {
                    return a + b;
                }
            }
            """);

        ParseResult result = adapter.parse("Methods.java", file);
        
        List<Symbol> methods = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.METHOD)
            .toList();
        
        assertTrue(methods.size() >= 2);
        assertTrue(methods.stream().anyMatch(s -> s.name().equals("doSomething")));
        assertTrue(methods.stream().anyMatch(s -> s.name().equals("calculate")));
    }

    @Test
    void fieldSymbolExtraction() throws IOException {
        Path file = tempDir.resolve("Fields.java");
        Files.writeString(file, """
            package com.example;
            
            public class Fields {
                private String name;
                public int count;
                protected boolean active;
            }
            """);

        ParseResult result = adapter.parse("Fields.java", file);
        
        List<Symbol> fields = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.FIELD)
            .toList();
        
        assertTrue(fields.size() >= 3);
        assertTrue(fields.stream().anyMatch(s -> s.name().equals("name")));
        assertTrue(fields.stream().anyMatch(s -> s.name().equals("count")));
    }

    @Test
    void importReferenceExtraction() throws IOException {
        Path file = tempDir.resolve("Imports.java");
        Files.writeString(file, """
            package com.example;
            
            import java.util.List;
            import java.util.Map;
            
            public class Imports {
                private List<String> items;
            }
            """);

        ParseResult result = adapter.parse("Imports.java", file);
        
        // 应该有引用
        assertFalse(result.references().isEmpty());
    }

    @Test
    void callRelationExtraction() throws IOException {
        Path file = tempDir.resolve("Calls.java");
        Files.writeString(file, """
            package com.example;
            
            public class Calls {
                public void methodA() {
                    methodB();
                }
                
                public void methodB() {
                }
            }
            """);

        ParseResult result = adapter.parse("Calls.java", file);
        
        assertFalse(result.calls().isEmpty());
    }

    @Test
    void emptyFile() throws IOException {
        Path file = tempDir.resolve("Empty.java");
        Files.writeString(file, "");

        ParseResult result = adapter.parse("Empty.java", file);
        
        // 空文件应该返回空结果
        assertNotNull(result);
    }

    @Test
    void syntaxErrorFile() throws IOException {
        Path file = tempDir.resolve("BadSyntax.java");
        Files.writeString(file, """
            package com.example;

            public class BadSyntax {
                this is not valid java
            }
            """);

        // 解析器应该能处理语法错误，不会抛出异常
        ParseResult result = adapter.parse("BadSyntax.java", file);
        assertNotNull(result);
    }

    @Test
    void extendsExtraction() throws IOException {
        Path file = tempDir.resolve("Child.java");
        Files.writeString(file, """
            package com.example;

            public class Child extends Parent {
                @Override
                public void greet() {}
            }
            """);

        ParseResult result = adapter.parse("Child.java", file);

        Symbol classSymbol = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(classSymbol);
        assertEquals("Parent", classSymbol.superClass());
    }

    @Test
    void implementsExtraction() throws IOException {
        Path file = tempDir.resolve("ServiceImpl.java");
        Files.writeString(file, """
            package com.example;

            public class ServiceImpl implements Serializable, Cloneable {
            }
            """);

        ParseResult result = adapter.parse("ServiceImpl.java", file);

        Symbol classSymbol = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(classSymbol);
        assertNotNull(classSymbol.interfaces());
        assertEquals(2, classSymbol.interfaces().size());
        assertTrue(classSymbol.interfaces().contains("Serializable"));
        assertTrue(classSymbol.interfaces().contains("Cloneable"));
    }

    @Test
    void extendsAndImplementsExtraction() throws IOException {
        Path file = tempDir.resolve("FullChild.java");
        Files.writeString(file, """
            package com.example;

            public class FullChild extends BaseService implements Serializable, AutoCloseable {
            }
            """);

        ParseResult result = adapter.parse("FullChild.java", file);

        Symbol classSymbol = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(classSymbol);
        assertEquals("BaseService", classSymbol.superClass());
        assertEquals(2, classSymbol.interfaces().size());
    }

    @Test
    void noExtendsNoImplements() throws IOException {
        Path file = tempDir.resolve("Simple.java");
        Files.writeString(file, """
            package com.example;

            public class Simple {
            }
            """);

        ParseResult result = adapter.parse("Simple.java", file);

        Symbol classSymbol = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(classSymbol);
        assertNull(classSymbol.superClass());
        assertNull(classSymbol.interfaces());
    }

    @Test
    void recordImplementsExtraction() throws IOException {
        Path file = tempDir.resolve("MyRecord.java");
        Files.writeString(file, """
            package com.example;

            public record MyRecord(int x, int y) implements Comparable<MyRecord> {
            }
            """);

        ParseResult result = adapter.parse("MyRecord.java", file);

        Symbol recordSymbol = result.symbols().stream()
            .filter(s -> s.kind() == Symbol.SymbolKind.CLASS)
            .findFirst()
            .orElse(null);

        assertNotNull(recordSymbol);
        assertNotNull(recordSymbol.interfaces());
        assertEquals(1, recordSymbol.interfaces().size());
        assertEquals("Comparable", recordSymbol.interfaces().getFirst());
    }

    @Test
    void markerAnnotationExtraction() throws IOException {
        Path file = tempDir.resolve("Annotated.java");
        Files.writeString(file, """
            package com.example;

            @Deprecated
            public class Annotated {
                @Override
                public String toString() { return ""; }
            }
            """);

        ParseResult result = adapter.parse("Annotated.java", file);

        assertFalse(result.annotations().isEmpty());
        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("Deprecated")));
        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("Override")));
    }

    @Test
    void singleMemberAnnotationExtraction() throws IOException {
        Path file = tempDir.resolve("Controller.java");
        Files.writeString(file, """
            package com.example;

            @RestController
            @RequestMapping("/api/users")
            public class Controller {
            }
            """);

        ParseResult result = adapter.parse("Controller.java", file);

        var mapping = result.annotations().stream()
            .filter(a -> a.name().equals("RequestMapping"))
            .findFirst()
            .orElse(null);

        assertNotNull(mapping);
        assertNotNull(mapping.attributes());
        assertTrue(mapping.attributes().containsKey("value"));
    }

    @Test
    void normalAnnotationExtraction() throws IOException {
        Path file = tempDir.resolve("Entity.java");
        Files.writeString(file, """
            package com.example;

            @Table(name = "users")
            public class Entity {
            }
            """);

        ParseResult result = adapter.parse("Entity.java", file);

        var table = result.annotations().stream()
            .filter(a -> a.name().equals("Table"))
            .findFirst()
            .orElse(null);

        assertNotNull(table);
        assertNotNull(table.attributes());
        assertTrue(table.attributes().containsKey("name"));
    }

    @Test
    void methodAnnotationExtraction() throws IOException {
        Path file = tempDir.resolve("Service.java");
        Files.writeString(file, """
            package com.example;

            public class Service {
                @Transactional
                @GetMapping("/{id}")
                public void getUser() {}
            }
            """);

        ParseResult result = adapter.parse("Service.java", file);

        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("Transactional")));
        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("GetMapping")));
    }
}
