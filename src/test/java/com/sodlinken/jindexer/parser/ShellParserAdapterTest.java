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
 * Shell 解析器测试
 */
class ShellParserAdapterTest {

    @TempDir
    Path tempDir;

    private Config config;
    private ShellParserAdapter parser;

    @BeforeEach
    void setUp() {
        config = new Config();
        config.setProjectRoot(tempDir);
        parser = new ShellParserAdapter(config);
    }

    @Test
    void isShellFile() {
        assertTrue(ShellParserAdapter.isShellFile("test.sh"));
        assertTrue(ShellParserAdapter.isShellFile("test.bash"));
        assertTrue(ShellParserAdapter.isShellFile("test.zsh"));
        assertTrue(ShellParserAdapter.isShellFile("test.ksh"));
        assertTrue(ShellParserAdapter.isShellFile("test.fish"));
        assertFalse(ShellParserAdapter.isShellFile("test.java"));
        assertFalse(ShellParserAdapter.isShellFile("test.py"));
    }

    @Test
    void parseFunctionDefinition() throws Exception {
        Path file = tempDir.resolve("deploy.sh");
        Files.writeString(file, """
            #!/bin/bash

            deploy() {
                echo "Deploying..."
                mvn package
            }

            rollback() {
                echo "Rolling back..."
            }
            """);

        var result = parser.parse("deploy.sh", file);

        assertFalse(result.symbols().isEmpty());
        assertTrue(result.symbols().stream().anyMatch(s -> s.name().equals("deploy")));
        assertTrue(result.symbols().stream().anyMatch(s -> s.name().equals("rollback")));
    }

    @Test
    void parseVariableAssignment() throws Exception {
        Path file = tempDir.resolve("config.sh");
        Files.writeString(file, """
            #!/bin/bash

            APP_NAME="myapp"
            VERSION="1.0.0"
            export DEBUG=true
            """);

        var result = parser.parse("config.sh", file);

        assertFalse(result.symbols().isEmpty());
        assertTrue(result.symbols().stream().anyMatch(s -> s.name().equals("APP_NAME")));
        assertTrue(result.symbols().stream().anyMatch(s -> s.name().equals("VERSION")));
        assertTrue(result.symbols().stream().anyMatch(s -> s.name().equals("DEBUG")));
    }

    @Test
    void parseSourceCommand() throws Exception {
        Path file = tempDir.resolve("main.sh");
        Files.writeString(file, """
            #!/bin/bash

            source utils.sh
            . config.sh

            echo "Hello"
            """);

        var result = parser.parse("main.sh", file);

        assertFalse(result.references().isEmpty());
        assertTrue(result.references().stream().anyMatch(r -> r.context().contains("utils.sh")));
        assertTrue(result.references().stream().anyMatch(r -> r.context().contains("config.sh")));
    }

    @Test
    void parseShebang() throws Exception {
        Path file = tempDir.resolve("script.sh");
        Files.writeString(file, """
            #!/bin/bash

            echo "Hello"
            """);

        var result = parser.parse("script.sh", file);

        assertFalse(result.symbols().isEmpty());
        var shebang = result.symbols().stream()
            .filter(s -> s.name().equals("#!interpreter"))
            .findFirst();
        assertTrue(shebang.isPresent());
    }
}
