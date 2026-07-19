package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Python 解析器测试
 */
class PythonParserAdapterTest {

    @TempDir
    Path tempDir;

    private Config config;
    private PythonParserAdapter parser;

    @BeforeEach
    void setUp() {
        config = new Config();
        config.setProjectRoot(tempDir);
        parser = new PythonParserAdapter(config);
    }

    @Test
    void isPythonFile() {
        assertTrue(PythonParserAdapter.isPythonFile("test.py"));
        assertTrue(PythonParserAdapter.isPythonFile("test.pyw"));
        assertFalse(PythonParserAdapter.isPythonFile("test.js"));
        assertFalse(PythonParserAdapter.isPythonFile("test.java"));
    }

    @Test
    void parseClassDeclaration() throws Exception {
        Path file = tempDir.resolve("user_service.py");
        Files.writeString(file, """
            class UserService:
                def __init__(self, name: str):
                    self.name = name

                def get_name(self) -> str:
                    return self.name
            """);

        var result = parser.parse("user_service.py", file);

        assertFalse(result.symbols().isEmpty());
        var classSymbol = result.symbols().stream()
            .filter(s -> s.name().equals("UserService"))
            .findFirst();
        assertTrue(classSymbol.isPresent());
        assertEquals(Symbol.SymbolKind.CLASS, classSymbol.get().kind());
    }

    @Test
    void parseFunctionDeclaration() throws Exception {
        Path file = tempDir.resolve("utils.py");
        Files.writeString(file, """
            def format_date(date) -> str:
                return date.isoformat()

            def add(a: int, b: int) -> int:
                return a + b
            """);

        var result = parser.parse("utils.py", file);

        assertFalse(result.symbols().isEmpty());
        assertTrue(result.symbols().stream().anyMatch(s -> s.name().equals("format_date")));
        assertTrue(result.symbols().stream().anyMatch(s -> s.name().equals("add")));
    }

    @Test
    void parseImportStatements() throws Exception {
        Path file = tempDir.resolve("app.py");
        Files.writeString(file, """
            import os
            from datetime import datetime
            from typing import List, Dict

            class App:
                pass
            """);

        var result = parser.parse("app.py", file);

        assertFalse(result.references().isEmpty());
        assertTrue(result.references().stream().anyMatch(r -> r.context().contains("os")));
        assertTrue(result.references().stream().anyMatch(r -> r.context().contains("datetime")));
    }

    @Test
    void parseDecorator() throws Exception {
        Path file = tempDir.resolve("views.py");
        Files.writeString(file, """
            @app.route('/api/users')
            def get_users():
                return []

            @staticmethod
            def helper():
                pass
            """);

        var result = parser.parse("views.py", file);

        assertFalse(result.symbols().isEmpty());
        assertFalse(result.annotations().isEmpty());
    }

    @Test
    void parseInheritance() throws Exception {
        Path file = tempDir.resolve("models.py");
        Files.writeString(file, """
            class BaseModel:
                pass

            class UserModel(BaseModel):
                def __init__(self, name: str):
                    self.name = name
            """);

        var result = parser.parse("models.py", file);

        assertFalse(result.symbols().isEmpty());
        var userModel = result.symbols().stream()
            .filter(s -> s.name().equals("UserModel"))
            .findFirst();
        assertTrue(userModel.isPresent());
        assertEquals("BaseModel", userModel.get().superClass());
    }
}
