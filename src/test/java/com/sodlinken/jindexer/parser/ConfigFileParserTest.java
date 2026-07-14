package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.model.ConfigEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigFileParserTest {

    private final ConfigFileParser parser = new ConfigFileParser();

    @Test
    void parseYamlFile(@TempDir Path tempDir) throws Exception {
        Path yamlFile = tempDir.resolve("application.yml");
        Files.writeString(yamlFile, """
            server:
              port: 8080
              host: localhost
            spring:
              datasource:
                url: jdbc:mysql://localhost:3306/mydb
                username: root
            """);

        List<ConfigEntry> entries = parser.parse("application.yml", yamlFile);
        assertEquals(4, entries.size());

        ConfigEntry port = entries.stream()
            .filter(e -> e.key().equals("server.port"))
            .findFirst().orElse(null);
        assertNotNull(port);
        assertEquals("8080", port.value());
        assertEquals(ConfigEntry.ConfigType.YAML, port.configType());
    }

    @Test
    void parseNestedYamlMap(@TempDir Path tempDir) throws Exception {
        Path yamlFile = tempDir.resolve("config.yml");
        Files.writeString(yamlFile, """
            level1:
              level2:
                level3:
                  key: deep-value
            """);

        List<ConfigEntry> entries = parser.parse("config.yml", yamlFile);
        assertEquals(1, entries.size());
        assertEquals("level1.level2.level3.key", entries.get(0).key());
        assertEquals("deep-value", entries.get(0).value());
    }

    @Test
    void parseYamlWithList(@TempDir Path tempDir) throws Exception {
        Path yamlFile = tempDir.resolve("config.yml");
        Files.writeString(yamlFile, """
            servers:
              - server1
              - server2
              - server3
            """);

        List<ConfigEntry> entries = parser.parse("config.yml", yamlFile);
        assertEquals(1, entries.size());
        assertEquals("servers", entries.get(0).key());
        assertTrue(entries.get(0).value().contains("server1"));
    }

    @Test
    void parseYamlWithComments(@TempDir Path tempDir) throws Exception {
        Path yamlFile = tempDir.resolve("config.yml");
        Files.writeString(yamlFile, """
            # This is a comment
            server:
              # Another comment
              port: 8080
            """);

        List<ConfigEntry> entries = parser.parse("config.yml", yamlFile);
        assertEquals(1, entries.size());
        assertEquals("server.port", entries.get(0).key());
    }

    @Test
    void parseYamlWithNullValue(@TempDir Path tempDir) throws Exception {
        Path yamlFile = tempDir.resolve("config.yml");
        Files.writeString(yamlFile, """
            server:
              port: 8080
              name:
            """);

        List<ConfigEntry> entries = parser.parse("config.yml", yamlFile);
        assertEquals(2, entries.size());

        ConfigEntry name = entries.stream()
            .filter(e -> e.key().equals("server.name"))
            .findFirst().orElse(null);
        assertNotNull(name);
        assertNull(name.value());
    }

    @Test
    void parsePropertiesFile(@TempDir Path tempDir) throws Exception {
        Path propsFile = tempDir.resolve("app.properties");
        Files.writeString(propsFile, """
            # Database configuration
            db.url=jdbc:mysql://localhost:3306/mydb
            db.username=root
            db.password=secret
            """);

        List<ConfigEntry> entries = parser.parse("app.properties", propsFile);
        assertEquals(3, entries.size());

        ConfigEntry url = entries.stream()
            .filter(e -> e.key().equals("db.url"))
            .findFirst().orElse(null);
        assertNotNull(url);
        assertEquals("jdbc:mysql://localhost:3306/mydb", url.value());
        assertEquals(ConfigEntry.ConfigType.PROPERTIES, url.configType());
    }

    @Test
    void parsePropertiesWithColonSeparator(@TempDir Path tempDir) throws Exception {
        Path propsFile = tempDir.resolve("app.properties");
        Files.writeString(propsFile, """
            db.url: jdbc:mysql://localhost:3306/mydb
            db.username: root
            """);

        List<ConfigEntry> entries = parser.parse("app.properties", propsFile);
        assertEquals(2, entries.size());
    }

    @Test
    void parsePropertiesWithPlaceholders(@TempDir Path tempDir) throws Exception {
        Path propsFile = tempDir.resolve("app.properties");
        Files.writeString(propsFile, """
            app.name=MyApp
            app.description=The ${app.name} application
            """);

        List<ConfigEntry> entries = parser.parse("app.properties", propsFile);
        assertEquals(2, entries.size());

        ConfigEntry desc = entries.stream()
            .filter(e -> e.key().equals("app.description"))
            .findFirst().orElse(null);
        assertNotNull(desc);
        assertEquals("The ${app.name} application", desc.value());
    }

    @Test
    void parseEnvFile(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
            # Environment variables
            DATABASE_URL=jdbc:mysql://localhost:3306/mydb
            API_KEY=abc123
            """);

        List<ConfigEntry> entries = parser.parse(".env", envFile);
        assertEquals(2, entries.size());

        ConfigEntry dbUrl = entries.stream()
            .filter(e -> e.key().equals("DATABASE_URL"))
            .findFirst().orElse(null);
        assertNotNull(dbUrl);
        assertEquals("jdbc:mysql://localhost:3306/mydb", dbUrl.value());
        assertEquals(ConfigEntry.ConfigType.ENV, dbUrl.configType());
    }

    @Test
    void parseEnvFileWithQuotes(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
            DB_HOST="localhost"
            DB_NAME='mydb'
            """);

        List<ConfigEntry> entries = parser.parse(".env", envFile);
        assertEquals(2, entries.size());

        ConfigEntry host = entries.stream()
            .filter(e -> e.key().equals("DB_HOST"))
            .findFirst().orElse(null);
        assertNotNull(host);
        assertEquals("localhost", host.value());
    }

    @Test
    void parseEnvFileWithEmptyValues(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
            EMPTY_VAR=
            ANOTHER_VAR=value
            """);

        List<ConfigEntry> entries = parser.parse(".env", envFile);
        assertEquals(2, entries.size());
    }

    @Test
    void parseYamlAliasFile(@TempDir Path tempDir) throws Exception {
        Path yamlFile = tempDir.resolve("config.yaml");
        Files.writeString(yamlFile, """
            database:
              port: 3306
            """);

        List<ConfigEntry> entries = parser.parse("config.yaml", yamlFile);
        assertEquals(1, entries.size());
        assertEquals("database.port", entries.get(0).key());
    }

    @Test
    void findLineNumberYaml(@TempDir Path tempDir) throws Exception {
        Path yamlFile = tempDir.resolve("config.yml");
        Files.writeString(yamlFile, """
            # Comment
            server:
              port: 8080
            """);

        List<ConfigEntry> entries = parser.parse("config.yml", yamlFile);
        assertEquals(1, entries.size());
        // Line 2 is "# Comment", line 3 is "server:", so line number is 3
        assertEquals(3, entries.get(0).line());
    }

    @Test
    void findLineNumberProperties(@TempDir Path tempDir) throws Exception {
        Path propsFile = tempDir.resolve("app.properties");
        Files.writeString(propsFile, """
            # Comment
            db.url=jdbc:mysql://localhost
            """);

        List<ConfigEntry> entries = parser.parse("app.properties", propsFile);
        assertEquals(1, entries.size());
        assertEquals(2, entries.get(0).line());
    }

    @Test
    void parseUnsupportedFileType(@TempDir Path tempDir) throws Exception {
        Path xmlFile = tempDir.resolve("config.xml");
        Files.writeString(xmlFile, "<config><key>value</key></config>");

        List<ConfigEntry> entries = parser.parse("config.xml", xmlFile);
        assertTrue(entries.isEmpty());
    }

    @Test
    void isConfigFile() {
        assertTrue(ConfigFileParser.isConfigFile("config.yml"));
        assertTrue(ConfigFileParser.isConfigFile("config.yaml"));
        assertTrue(ConfigFileParser.isConfigFile("app.properties"));
        assertTrue(ConfigFileParser.isConfigFile(".env"));
        assertTrue(ConfigFileParser.isConfigFile("CONFIG.YML"));
        assertFalse(ConfigFileParser.isConfigFile("pom.xml"));
        assertFalse(ConfigFileParser.isConfigFile("build.gradle"));
        assertFalse(ConfigFileParser.isConfigFile("README.md"));
    }

    @Test
    void parseYamlWithMultipleKeys(@TempDir Path tempDir) throws Exception {
        Path yamlFile = tempDir.resolve("config.yml");
        Files.writeString(yamlFile, """
            app:
              name: MyApp
              version: 1.0.0
              debug: true
            """);

        List<ConfigEntry> entries = parser.parse("config.yml", yamlFile);
        assertEquals(3, entries.size());
    }

    @Test
    void parseEmptyPropertiesFile(@TempDir Path tempDir) throws Exception {
        Path propsFile = tempDir.resolve("empty.properties");
        Files.writeString(propsFile, "");

        List<ConfigEntry> entries = parser.parse("empty.properties", propsFile);
        assertTrue(entries.isEmpty());
    }

    @Test
    void parseEmptyEnvFile(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "# Just a comment\n");

        List<ConfigEntry> entries = parser.parse(".env", envFile);
        assertTrue(entries.isEmpty());
    }
}
