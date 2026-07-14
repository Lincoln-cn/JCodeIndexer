package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.Annotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 注解识别综合测试
 */
class AnnotationTest {

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
    void validationAnnotations() throws IOException {
        Path file = tempDir.resolve("Dto.java");
        Files.writeString(file, """
            package com.example;

            @Valid
            public class Dto {
            }
            """);

        ParseResult result = adapter.parse("Dto.java", file);

        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("Valid")));
    }

    @Test
    void springAnnotations() throws IOException {
        Path file = tempDir.resolve("Config.java");
        Files.writeString(file, """
            package com.example;

            @Configuration
            @ComponentScan("com.example")
            public class Config {
            }
            """);

        ParseResult result = adapter.parse("Config.java", file);

        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("Configuration")));
        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("ComponentScan")));
    }

    @Test
    void mybatisAnnotations() throws IOException {
        Path file = tempDir.resolve("Mapper.java");
        Files.writeString(file, """
            package com.example;

            @Mapper
            public interface UserMapper {
            }
            """);

        ParseResult result = adapter.parse("Mapper.java", file);

        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("Mapper")));
    }

    @Test
    void swaggerAnnotations() throws IOException {
        Path file = tempDir.resolve("ApiController.java");
        Files.writeString(file, """
            package com.example;

            @Api(tags = "User API")
            @RestController
            public class ApiController {
            }
            """);

        ParseResult result = adapter.parse("ApiController.java", file);

        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("Api")));
        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("RestController")));
    }

    @Test
    void springSecurityAnnotations() throws IOException {
        Path file = tempDir.resolve("SecurityConfig.java");
        Files.writeString(file, """
            package com.example;

            @Configuration
            @EnableWebSecurity
            public class SecurityConfig {
            }
            """);

        ParseResult result = adapter.parse("SecurityConfig.java", file);

        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("Configuration")));
        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("EnableWebSecurity")));
    }

    @Test
    void cacheAnnotations() throws IOException {
        Path file = tempDir.resolve("CacheService.java");
        Files.writeString(file, """
            package com.example;

            @Service
            @EnableCaching
            public class CacheService {
            }
            """);

        ParseResult result = adapter.parse("CacheService.java", file);

        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("Service")));
        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("EnableCaching")));
    }

    @Test
    void asyncAnnotations() throws IOException {
        Path file = tempDir.resolve("AsyncService.java");
        Files.writeString(file, """
            package com.example;

            @Service
            @EnableAsync
            public class AsyncService {
            }
            """);

        ParseResult result = adapter.parse("AsyncService.java", file);

        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("Service")));
        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("EnableAsync")));
    }

    @Test
    void scheduledAnnotations() throws IOException {
        Path file = tempDir.resolve("Scheduler.java");
        Files.writeString(file, """
            package com.example;

            @Component
            @EnableScheduling
            public class Scheduler {
            }
            """);

        ParseResult result = adapter.parse("Scheduler.java", file);

        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("Component")));
        assertTrue(result.annotations().stream().anyMatch(a -> a.name().equals("EnableScheduling")));
    }
}
