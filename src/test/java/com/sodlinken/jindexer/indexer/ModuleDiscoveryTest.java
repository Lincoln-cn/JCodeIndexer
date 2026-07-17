package com.sodlinken.jindexer.indexer;

import com.sodlinken.jindexer.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleDiscoveryTest {

    @TempDir
    Path tempDir;

    private Config config;

    @BeforeEach
    void setUp() {
        config = new Config();
        config.setProjectRoot(tempDir);
    }

    // ==================== Maven Tests ====================

    @Test
    void discoverSingleModuleProject() throws Exception {
        // 单模块项目（无 <modules>）
        Files.writeString(tempDir.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>root</artifactId>
                <version>1.0.0</version>
            </project>
            """);

        List<Path> modules = ModuleDiscovery.discover(config);
        assertEquals(1, modules.size());
        assertEquals(tempDir, modules.get(0));
    }

    @Test
    void discoverMavenMultiModule() throws Exception {
        // 创建父 pom.xml
        Files.writeString(tempDir.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>parent</artifactId>
                <version>1.0.0</version>
                <modules>
                    <module>module-a</module>
                    <module>module-b</module>
                </modules>
            </project>
            """);

        // 创建子模块目录和 pom.xml
        Files.createDirectories(tempDir.resolve("module-a"));
        Files.writeString(tempDir.resolve("module-a/pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <artifactId>module-a</artifactId>
            </project>
            """);

        Files.createDirectories(tempDir.resolve("module-b"));
        Files.writeString(tempDir.resolve("module-b/pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <artifactId>module-b</artifactId>
            </project>
            """);

        List<Path> modules = ModuleDiscovery.discover(config);
        assertEquals(3, modules.size()); // root + module-a + module-b
        assertTrue(modules.contains(tempDir));
        assertTrue(modules.contains(tempDir.resolve("module-a")));
        assertTrue(modules.contains(tempDir.resolve("module-b")));
    }

    @Test
    void discoverNestedMavenModules() throws Exception {
        // 父模块 -> 子模块 -> 孙模块
        Files.writeString(tempDir.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modules><module>parent-mod</module></modules>
            </project>
            """);

        Path parentMod = tempDir.resolve("parent-mod");
        Files.createDirectories(parentMod);
        Files.writeString(parentMod.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modules><module>child-mod</module></modules>
            </project>
            """);

        Path childMod = parentMod.resolve("child-mod");
        Files.createDirectories(childMod);
        Files.writeString(childMod.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project><artifactId>child</artifactId></project>
            """);

        List<Path> modules = ModuleDiscovery.discover(config);
        assertEquals(3, modules.size()); // root + parent-mod + child-mod
    }

    @Test
    void mavenModuleDirectoryNotExist() throws Exception {
        // pom.xml 引用不存在的模块目录
        Files.writeString(tempDir.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modules><module>nonexistent</module></modules>
            </project>
            """);

        List<Path> modules = ModuleDiscovery.discover(config);
        assertEquals(1, modules.size()); // 只有根目录
    }

    // ==================== Gradle Tests ====================

    @Test
    void discoverGradleMultiModule() throws Exception {
        // 创建 settings.gradle
        Files.writeString(tempDir.resolve("settings.gradle"), """
            rootProject.name = 'my-project'
            include ':module-a'
            include ':module-b'
            """);

        // 创建子模块目录
        Files.createDirectories(tempDir.resolve("module-a"));
        Files.createDirectories(tempDir.resolve("module-b"));

        List<Path> modules = ModuleDiscovery.discover(config);
        assertEquals(3, modules.size()); // root + module-a + module-b
    }

    @Test
    void discoverGradleKts() throws Exception {
        Files.writeString(tempDir.resolve("settings.gradle.kts"), """
            rootProject.name = "my-project"
            include(":module-x")
            include(":module-y")
            """);

        Files.createDirectories(tempDir.resolve("module-x"));
        Files.createDirectories(tempDir.resolve("module-y"));

        List<Path> modules = ModuleDiscovery.discover(config);
        assertEquals(3, modules.size());
    }

    @Test
    void parseGradleIncludes() {
        String content = """
            rootProject.name = 'app'
            include ':module-a'
            include ':module-b', ':module-c'
            """;

        List<String> includes = ModuleDiscovery.parseGradleIncludes(content);
        assertEquals(3, includes.size());
        assertTrue(includes.contains(":module-a"));
        assertTrue(includes.contains(":module-b"));
        assertTrue(includes.contains(":module-c"));
    }

    // ==================== No Build Tool ====================

    @Test
    void noBuildToolReturnsRootOnly() {
        List<Path> modules = ModuleDiscovery.discover(config);
        assertEquals(1, modules.size());
        assertEquals(tempDir, modules.get(0));
    }
}
