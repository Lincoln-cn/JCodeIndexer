package com.sodlinken.jindexer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionTest {

    @Test
    void getVersion() {
        String version = Version.getVersion();
        assertNotNull(version);
        assertFalse(version.isBlank());
        // 版本号应该是 x.y.z 格式
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"),
            "Version should match x.y.z format: " + version);
    }

    @Test
    void getAppName() {
        String name = Version.getAppName();
        assertNotNull(name);
        assertEquals("java-code-indexer", name);
    }

    @Test
    void getFullVersion() {
        String full = Version.getFullVersion();
        assertNotNull(full);
        assertTrue(full.startsWith("java-code-indexer v"));
        assertTrue(full.contains(Version.getVersion()));
    }
}
