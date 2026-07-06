package com.sodlinken.jindexer.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void defaultValues() {
        Config config = new Config();
        assertEquals(4, config.getIndexingThreads());
        assertFalse(config.isExtractJavadoc());
        assertFalse(config.isFollowSymlinks());
        assertEquals(512, config.getMaxFileSizeKB());
        assertEquals("index.db", config.getDbName());
        assertFalse(config.isEmbeddingEnabled());
        assertFalse(config.isVerbose());
        assertNotNull(config.getProjects());
        assertTrue(config.getProjects().isEmpty());
        assertFalse(config.isMultiProject());
    }

    @Test
    void dbPathResolution(@TempDir Path tempDir) {
        Config config = new Config();
        config.setProjectRoot(tempDir);
        config.setDataDir(tempDir.resolve(".jindexer"));
        config.setDbName("index.db");

        assertEquals(tempDir.resolve(".jindexer/index.db"), config.getDbPath());
    }

    @Test
    void multiProjectDetection() {
        Config config = new Config();
        assertFalse(config.isMultiProject());

        config.setProjects(List.of(new Config.Project("a", Path.of("/a"))));
        assertTrue(config.isMultiProject());
    }

    @Test
    void projectDataDirResolution(@TempDir Path tempDir) {
        Config config = new Config();
        config.setDataDir(tempDir.resolve(".jindexer"));

        Path dataDir = config.getProjectDataDir("myproject");
        assertEquals(tempDir.resolve(".jindexer/myproject"), dataDir);
    }

    @Test
    void projectDbPathResolution(@TempDir Path tempDir) {
        Config config = new Config();
        config.setDataDir(tempDir.resolve(".jindexer"));
        config.setDbName("index.db");

        Path dbPath = config.getProjectDbPath("myproject");
        assertEquals(tempDir.resolve(".jindexer/myproject/index.db"), dbPath);
    }
}
