package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.BeanDependency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeanDependencyParserTest {

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
    void fieldInjection() throws IOException {
        Path file = tempDir.resolve("UserService.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Service;
            
            @Service
            public class UserService {
                @Autowired
                private UserRepository userRepository;
                
                @Autowired
                private OrderService orderService;
            }
            """);

        ParseResult result = adapter.parse("UserService.java", file);
        assertFalse(result.beanDependencies().isEmpty());
        assertEquals(2, result.beanDependencies().size());
        
        BeanDependency dep1 = result.beanDependencies().getFirst();
        assertEquals("UserRepository", dep1.dependsOnType());
        assertEquals("FIELD", dep1.injectionType());
        assertEquals("userRepository", dep1.fieldName());
        
        BeanDependency dep2 = result.beanDependencies().get(1);
        assertEquals("OrderService", dep2.dependsOnType());
        assertEquals("FIELD", dep2.injectionType());
    }

    @Test
    void constructorInjection() throws IOException {
        Path file = tempDir.resolve("OrderService.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Service;
            
            @Service
            public class OrderService {
                private final UserService userService;
                private final PaymentService paymentService;
                
                @Autowired
                public OrderService(UserService userService, PaymentService paymentService) {
                    this.userService = userService;
                    this.paymentService = paymentService;
                }
            }
            """);

        ParseResult result = adapter.parse("OrderService.java", file);
        assertFalse(result.beanDependencies().isEmpty());
        
        List<BeanDependency> ctorDeps = result.beanDependencies().stream()
            .filter(d -> "CONSTRUCTOR".equals(d.injectionType()))
            .toList();
        assertEquals(2, ctorDeps.size());
        
        assertTrue(ctorDeps.stream().anyMatch(d -> "UserService".equals(d.dependsOnType())));
        assertTrue(ctorDeps.stream().anyMatch(d -> "PaymentService".equals(d.dependsOnType())));
    }

    @Test
    void setterInjection() throws IOException {
        Path file = tempDir.resolve("NotificationService.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Service;
            
            @Service
            public class NotificationService {
                private EmailService emailService;
                
                @Autowired
                public void setEmailService(EmailService emailService) {
                    this.emailService = emailService;
                }
            }
            """);

        ParseResult result = adapter.parse("NotificationService.java", file);
        assertFalse(result.beanDependencies().isEmpty());
        
        BeanDependency dep = result.beanDependencies().getFirst();
        assertEquals("EmailService", dep.dependsOnType());
        assertEquals("SETTER", dep.injectionType());
        assertEquals("setEmailService", dep.fieldName());
    }

    @Test
    void mixedInjection() throws IOException {
        Path file = tempDir.resolve("ComplexService.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Service;
            
            @Service
            public class ComplexService {
                @Autowired
                private DependencyA dependencyA;
                
                private final DependencyB dependencyB;
                
                @Autowired
                public ComplexService(DependencyB dependencyB) {
                    this.dependencyB = dependencyB;
                }
            }
            """);

        ParseResult result = adapter.parse("ComplexService.java", file);
        
        List<BeanDependency> fieldDeps = result.beanDependencies().stream()
            .filter(d -> "FIELD".equals(d.injectionType()))
            .toList();
        List<BeanDependency> ctorDeps = result.beanDependencies().stream()
            .filter(d -> "CONSTRUCTOR".equals(d.injectionType()))
            .toList();
        
        assertEquals(1, fieldDeps.size());
        assertEquals(1, ctorDeps.size());
        assertEquals("DependencyA", fieldDeps.getFirst().dependsOnType());
        assertEquals("DependencyB", ctorDeps.getFirst().dependsOnType());
    }

    @Test
    void noAutowiredAnnotation() throws IOException {
        Path file = tempDir.resolve("PlainService.java");
        Files.writeString(file, """
            package com.example;
            
            public class PlainService {
                private SomeDependency dependency;
            }
            """);

        ParseResult result = adapter.parse("PlainService.java", file);
        assertTrue(result.beanDependencies().isEmpty());
    }

    @Test
    void injectAnnotation() throws IOException {
        Path file = tempDir.resolve("InjectService.java");
        Files.writeString(file, """
            package com.example;
            
            import javax.inject.Inject;
            import javax.inject.Named;
            
            public class InjectService {
                @Inject
                private DependencyA dependencyA;
            }
            """);

        ParseResult result = adapter.parse("InjectService.java", file);
        assertFalse(result.beanDependencies().isEmpty());
        
        BeanDependency dep = result.beanDependencies().getFirst();
        assertEquals("DependencyA", dep.dependsOnType());
        assertEquals("FIELD", dep.injectionType());
    }

    @Test
    void multiParameterConstructor() throws IOException {
        Path file = tempDir.resolve("MultiCtorService.java");
        Files.writeString(file, """
            package com.example;
            
            public class MultiCtorService {
                private final DependencyA a;
                private final DependencyB b;
                private final DependencyC c;
                
                public MultiCtorService(DependencyA a, DependencyB b, DependencyC c) {
                    this.a = a;
                    this.b = b;
                    this.c = c;
                }
            }
            """);

        ParseResult result = adapter.parse("MultiCtorService.java", file);
        
        // 多参数构造函数（无 @Autowired）也应该被识别
        List<BeanDependency> ctorDeps = result.beanDependencies().stream()
            .filter(d -> "CONSTRUCTOR".equals(d.injectionType()))
            .toList();
        assertEquals(3, ctorDeps.size());
    }

    @Test
    void filePath() throws IOException {
        Path file = tempDir.resolve("TestController.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.web.bind.annotation.RestController;
            
            @RestController
            public class TestController {
                @Autowired
                private TestService testService;
            }
            """);

        ParseResult result = adapter.parse("TestController.java", file);
        assertFalse(result.beanDependencies().isEmpty());
        
        BeanDependency dep = result.beanDependencies().getFirst();
        assertEquals("TestController.java", dep.filePath());
        assertTrue(dep.startLine() > 0);
    }
}
