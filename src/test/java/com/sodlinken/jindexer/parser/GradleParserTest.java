package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.model.Dependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GradleParserTest {

    private final GradleParser parser = new GradleParser();

    @Test
    void parseShortFormatDependencies(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            dependencies {
                implementation 'org.springframework:spring-core:6.1.0'
                implementation 'com.google.code.gson:gson:2.11.0'
                testImplementation 'junit:junit:4.13.2'
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle", buildFile);
        assertEquals(3, deps.size());

        Dependency spring = deps.get(0);
        assertEquals("org.springframework", spring.groupId());
        assertEquals("spring-core", spring.artifactId());
        assertEquals("6.1.0", spring.version());
        assertEquals("compile", spring.scope());
        assertEquals(Dependency.DepType.GRADLE, spring.depType());
    }

    @Test
    void parseShortFormatWithParentheses(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            dependencies {
                implementation("org.springframework:spring-core:6.1.0")
                testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle", buildFile);
        assertEquals(2, deps.size());
        assertEquals("spring-core", deps.get(0).artifactId());
    }

    @Test
    void parseMapFormatDependencies(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            dependencies {
                implementation {
                    group = 'org.apache.commons'
                    name = 'commons-lang3'
                    version = '3.14.0'
                }
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle", buildFile);
        assertEquals(1, deps.size());

        Dependency commons = deps.get(0);
        assertEquals("org.apache.commons", commons.groupId());
        assertEquals("commons-lang3", commons.artifactId());
    }

    @Test
    void parseScopeMapping(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            dependencies {
                implementation 'g:a:1.0'
                api 'g:b:1.0'
                compileOnly 'g:c:1.0'
                runtimeOnly 'g:d:1.0'
                testImplementation 'g:e:1.0'
                kapt 'g:f:1.0'
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle", buildFile);
        assertEquals(6, deps.size());

        // implementation -> compile
        assertEquals("compile", deps.get(0).scope());
        // api -> compile
        assertEquals("compile", deps.get(1).scope());
        // compileOnly -> provided
        assertEquals("provided", deps.get(2).scope());
        // runtimeOnly -> compile
        assertEquals("compile", deps.get(3).scope());
        // testImplementation -> test
        assertEquals("test", deps.get(4).scope());
        // kapt -> provided
        assertEquals("provided", deps.get(5).scope());
    }

    @Test
    void resolveVersionVariables(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            ext {
                springVersion = '6.1.0'
            }

            dependencies {
                implementation "org.springframework:spring-core:${springVersion}"
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle", buildFile);
        assertEquals(1, deps.size());
        assertEquals("6.1.0", deps.get(0).version());
    }

    @Test
    void resolveSimpleVersionVariable(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            ext {
                gsonVersion = '2.11.0'
            }

            dependencies {
                implementation 'com.google.code.gson:gson:gsonVersion'
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle", buildFile);
        assertEquals(1, deps.size());
        assertEquals("2.11.0", deps.get(0).version());
    }

    @Test
    void parseDependencyWithClassifier(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            dependencies {
                implementation 'com.example:lib:1.0:sources'
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle", buildFile);
        assertEquals(1, deps.size());
        assertEquals("1.0", deps.get(0).version());
        assertEquals("sources", deps.get(0).classifier());
    }

    @Test
    void parseKotlinDsl(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle.kts");
        Files.writeString(buildFile, """
            dependencies {
                implementation("org.springframework:spring-core:6.1.0")
                testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle.kts", buildFile);
        assertEquals(2, deps.size());
    }

    @Test
    void parseEmptyDependenciesBlock(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            dependencies {
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle", buildFile);
        assertTrue(deps.isEmpty());
    }

    @Test
    void parseNoDependenciesBlock(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            plugins {
                id 'java'
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle", buildFile);
        assertTrue(deps.isEmpty());
    }

    @Test
    void findLineNumber(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            plugins {
                id 'java'
            }

            dependencies {
                implementation 'org.springframework:spring-core:6.1.0'
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle", buildFile);
        assertEquals(1, deps.size());
        assertEquals(6, deps.get(0).line());
    }

    @Test
    void isGradleFile() {
        assertTrue(GradleParser.isGradleFile("build.gradle"));
        assertTrue(GradleParser.isGradleFile("build.gradle.kts"));
        assertTrue(GradleParser.isGradleFile("BUILD.GRADLE"));
        assertFalse(GradleParser.isGradleFile("pom.xml"));
        assertFalse(GradleParser.isGradleFile("settings.gradle"));
    }

    @Test
    void parseMultipleScopesAndFormats(@TempDir Path tempDir) throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            dependencies {
                implementation 'g:a:1.0'
                implementation("g:b:2.0")
                testImplementation 'g:c:3.0'
            }
            """);

        List<Dependency> deps = parser.parse("build.gradle", buildFile);
        assertEquals(3, deps.size());
    }
}
