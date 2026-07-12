package com.sodlinken.jindexer.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @Test
    void loadWithDefaults(@TempDir Path tempDir) {
        Config config = ConfigLoader.load(tempDir, tempDir.resolve(".jindexer"));
        assertEquals(tempDir, config.getProjectRoot());
        assertEquals(tempDir.resolve(".jindexer"), config.getDataDir());
        assertEquals("index.db", config.getDbName());
        assertFalse(config.isMultiProject());
    }

    @Test
    void loadFromFile(@TempDir Path tempDir) throws IOException {
        Path jindexer = tempDir.resolve(".jindexer");
        Files.createDirectories(jindexer);
        Files.writeString(jindexer.resolve("config.yaml"),
            """
            indexing:
              threads: 8
              extract_javadoc: true
            storage:
              db_name: custom.db
            """);

        Config config = ConfigLoader.load(tempDir, jindexer);
        assertEquals(8, config.getIndexingThreads());
        assertTrue(config.isExtractJavadoc());
        assertEquals("custom.db", config.getDbName());
    }

    @Test
    void loadMultiProjectConfig(@TempDir Path tempDir) throws IOException {
        Path jindexer = tempDir.resolve(".jindexer");
        Files.createDirectories(jindexer);
        Files.writeString(jindexer.resolve("config.yaml"),
            """
            projects:
              - name: project-a
                root: /tmp/project-a
              - name: project-b
                root: /tmp/project-b
            data_dir: .jindexer
            db_name: index.db
            """);

        Config config = ConfigLoader.load(tempDir, jindexer);
        assertTrue(config.isMultiProject());
        assertEquals(2, config.getProjects().size());
        assertEquals("project-a", config.getProjects().get(0).name());
        assertEquals(Path.of("/tmp/project-a"), config.getProjects().get(0).root());
        assertEquals("project-b", config.getProjects().get(1).name());
    }

    @Test
    void dataDirFromYamlIsResolved(@TempDir Path tempDir) throws IOException {
        Path jindexer = tempDir.resolve(".jindexer");
        Files.createDirectories(jindexer);
        Files.writeString(jindexer.resolve("config.yaml"),
            """
            data_dir: custom-data
            """);

        Config config = ConfigLoader.load(tempDir, jindexer);
        assertEquals(tempDir.resolve("custom-data"), config.getDataDir());
    }

    @Test
    void noConfigFileUsesDefaults(@TempDir Path tempDir) {
        Config config = ConfigLoader.load(tempDir, tempDir.resolve(".jindexer"));
        assertEquals("index.db", config.getDbName());
        assertTrue(config.getProjects().isEmpty());
    }

    @Test
    void rootFallbackConfig(@TempDir Path tempDir) throws IOException {
        // config.yaml at project root instead of .jindexer/
        Files.writeString(tempDir.resolve("config.yaml"),
            """
            storage:
              db_name: root-config.db
            """);

        Config config = ConfigLoader.load(tempDir, tempDir.resolve(".jindexer"));
        assertEquals("root-config.db", config.getDbName());
    }
}
