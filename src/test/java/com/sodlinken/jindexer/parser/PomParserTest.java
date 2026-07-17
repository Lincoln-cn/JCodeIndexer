package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.model.Dependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PomParserTest {

    private final PomParser parser = new PomParser();

    @Test
    void parseSimplePom(@TempDir Path tempDir) throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>5.10.3</version>
                    </dependency>
                    <dependency>
                        <groupId>com.google.code.gson</groupId>
                        <artifactId>gson</artifactId>
                        <version>2.11.0</version>
                        <scope>compile</scope>
                    </dependency>
                </dependencies>
            </project>
            """);

        List<Dependency> deps = parser.parse("pom.xml", pom);
        assertEquals(2, deps.size());

        Dependency junit = deps.get(0);
        assertEquals("org.junit.jupiter", junit.groupId());
        assertEquals("junit-jupiter", junit.artifactId());
        assertEquals("5.10.3", junit.version());
        assertEquals("compile", junit.scope());
        assertEquals(Dependency.DepType.POM, junit.depType());
        assertEquals(10, junit.line()); // line number should be found (1-based index)

        Dependency gson = deps.get(1);
        assertEquals("com.google.code.gson", gson.groupId());
        assertEquals("gson", gson.artifactId());
    }

    @Test
    void skipDependencyManagement(@TempDir Path tempDir) throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-dependencies</artifactId>
                            <version>3.2.0</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>5.10.3</version>
                    </dependency>
                </dependencies>
            </project>
            """);

        List<Dependency> deps = parser.parse("pom.xml", pom);
        assertEquals(1, deps.size());
        assertEquals("junit-jupiter", deps.get(0).artifactId());
    }

    @Test
    void resolvePropertyVariables(@TempDir Path tempDir) throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
                <properties>
                    <gson.version>2.11.0</gson.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>com.google.code.gson</groupId>
                        <artifactId>gson</artifactId>
                        <version>${gson.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """);

        List<Dependency> deps = parser.parse("pom.xml", pom);
        assertEquals(1, deps.size());
        assertEquals("2.11.0", deps.get(0).version());
    }

    @Test
    void resolveProjectVersion(@TempDir Path tempDir) throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>my-lib</artifactId>
                        <version>${project.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """);

        List<Dependency> deps = parser.parse("pom.xml", pom);
        assertEquals(1, deps.size());
        assertEquals("1.0.0", deps.get(0).version());
    }

    @Test
    void isPomFile() {
        assertTrue(PomParser.isPomFile("pom.xml"));
        assertTrue(PomParser.isPomFile("POM.XML"));
        assertFalse(PomParser.isPomFile("build.gradle"));
        assertFalse(PomParser.isPomFile("pom.xml.bak"));
    }

    // ==================== parseModules Tests ====================

    @Test
    void parseModules(@TempDir Path tempDir) throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <modules>
                    <module>module-a</module>
                    <module>module-b</module>
                    <module>module-c</module>
                </modules>
            </project>
            """);

        List<String> modules = parser.parseModules(pom);
        assertEquals(3, modules.size());
        assertEquals("module-a", modules.get(0));
        assertEquals("module-b", modules.get(1));
        assertEquals("module-c", modules.get(2));
    }

    @Test
    void parseModulesEmpty(@TempDir Path tempDir) throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
            </project>
            """);

        List<String> modules = parser.parseModules(pom);
        assertTrue(modules.isEmpty());
    }

    @Test
    void parseModulesFileNotExist(@TempDir Path tempDir) throws Exception {
        List<String> modules = parser.parseModules(tempDir.resolve("nonexistent.xml"));
        assertTrue(modules.isEmpty());
    }
}
