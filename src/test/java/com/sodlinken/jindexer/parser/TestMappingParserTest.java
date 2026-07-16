package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.TestMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestMappingParserTest {

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
    void suffixTest() throws IOException {
        Path file = tempDir.resolve("UserServiceTest.java");
        Files.writeString(file, """
            package com.example;
            
            import org.junit.jupiter.api.Test;
            
            class UserServiceTest {
                @Test
                void shouldCreateUser() {}
            }
            """);

        ParseResult result = adapter.parse("UserServiceTest.java", file);
        assertFalse(result.testMappings().isEmpty());
        
        TestMapping mapping = result.testMappings().getFirst();
        assertEquals("UserServiceTest", mapping.testClassName());
        assertEquals("UserService", mapping.sourceClassName());
        assertEquals("NAME_PATTERN", mapping.mappingType());
    }

    @Test
    void suffixTests() throws IOException {
        Path file = tempDir.resolve("OrderServiceTests.java");
        Files.writeString(file, """
            package com.example;
            
            class OrderServiceTests {}
            """);

        ParseResult result = adapter.parse("OrderServiceTests.java", file);
        assertFalse(result.testMappings().isEmpty());
        
        TestMapping mapping = result.testMappings().getFirst();
        assertEquals("OrderServiceTests", mapping.testClassName());
        assertEquals("OrderService", mapping.sourceClassName());
    }

    @Test
    void suffixSpec() throws IOException {
        Path file = tempDir.resolve("PaymentServiceSpec.java");
        Files.writeString(file, """
            package com.example;
            
            class PaymentServiceSpec {}
            """);

        ParseResult result = adapter.parse("PaymentServiceSpec.java", file);
        assertFalse(result.testMappings().isEmpty());
        
        TestMapping mapping = result.testMappings().getFirst();
        assertEquals("PaymentServiceSpec", mapping.testClassName());
        assertEquals("PaymentService", mapping.sourceClassName());
    }

    @Test
    void prefixTest() throws IOException {
        Path file = tempDir.resolve("TestNotificationService.java");
        Files.writeString(file, """
            package com.example;
            
            class TestNotificationService {}
            """);

        ParseResult result = adapter.parse("TestNotificationService.java", file);
        assertFalse(result.testMappings().isEmpty());
        
        TestMapping mapping = result.testMappings().getFirst();
        assertEquals("TestNotificationService", mapping.testClassName());
        assertEquals("NotificationService", mapping.sourceClassName());
    }

    @Test
    void nonTestClass() throws IOException {
        Path file = tempDir.resolve("UserService.java");
        Files.writeString(file, """
            package com.example;
            
            class UserService {}
            """);

        ParseResult result = adapter.parse("UserService.java", file);
        assertTrue(result.testMappings().isEmpty());
    }

    @Test
    void multipleTestClasses() throws IOException {
        Path file = tempDir.resolve("ControllerTest.java");
        Files.writeString(file, """
            package com.example;
            
            class UserControllerTest {}
            """);

        ParseResult result = adapter.parse("ControllerTest.java", file);
        assertEquals(1, result.testMappings().size());
        assertEquals("UserController", result.testMappings().getFirst().sourceClassName());
    }

    @Test
    void shortNameNoMatch() throws IOException {
        // "ATest" → "A" (4 chars removed, but test name is only 5 chars)
        // This should still match since length > 4 check passes
        Path file = tempDir.resolve("XTest.java");
        Files.writeString(file, """
            package com.example;
            
            class XTest {}
            """);

        ParseResult result = adapter.parse("XTest.java", file);
        // "XTest" ends with "Test" and length > 4, so it should match → source = "X"
        assertFalse(result.testMappings().isEmpty());
        assertEquals("X", result.testMappings().getFirst().sourceClassName());
    }

    @Test
    void filePath() throws IOException {
        Path file = tempDir.resolve("MyServiceTest.java");
        Files.writeString(file, """
            package com.example;
            
            class MyServiceTest {}
            """);

        ParseResult result = adapter.parse("MyServiceTest.java", file);
        assertFalse(result.testMappings().isEmpty());
        assertEquals("MyServiceTest.java", result.testMappings().getFirst().filePath());
    }
}
