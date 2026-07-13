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
}
