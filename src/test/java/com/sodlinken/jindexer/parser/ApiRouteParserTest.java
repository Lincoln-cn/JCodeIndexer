package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.ApiRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiRouteParserTest {

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
    void getMapping() throws IOException {
        Path file = tempDir.resolve("UserController.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;
            
            @RestController
            public class UserController {
                @GetMapping("/api/users")
                public List<User> getUsers() {
                    return List.of();
                }
            }
            """);

        ParseResult result = adapter.parse("UserController.java", file);
        assertFalse(result.apiRoutes().isEmpty());
        
        ApiRoute route = result.apiRoutes().getFirst();
        assertEquals("GET", route.httpMethod());
        assertEquals("/api/users", route.path());
        assertEquals("UserController.java", route.filePath());
    }

    @Test
    void postMapping() throws IOException {
        Path file = tempDir.resolve("OrderController.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestBody;
            import org.springframework.web.bind.annotation.RestController;
            
            @RestController
            public class OrderController {
                @PostMapping("/api/orders")
                public Order createOrder(@RequestBody Order order) {
                    return order;
                }
            }
            """);

        ParseResult result = adapter.parse("OrderController.java", file);
        assertFalse(result.apiRoutes().isEmpty());
        
        ApiRoute route = result.apiRoutes().getFirst();
        assertEquals("POST", route.httpMethod());
        assertEquals("/api/orders", route.path());
    }

    @Test
    void requestMappingWithMethod() throws IOException {
        Path file = tempDir.resolve("UserController.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestMethod;
            import org.springframework.web.bind.annotation.RestController;
            
            @RestController
            public class UserController {
                @RequestMapping(value = "/api/users", method = RequestMethod.GET)
                public List<User> getUsers() {
                    return List.of();
                }
            }
            """);

        ParseResult result = adapter.parse("UserController.java", file);
        assertFalse(result.apiRoutes().isEmpty());
        
        ApiRoute route = result.apiRoutes().getFirst();
        assertEquals("GET", route.httpMethod());
        assertEquals("/api/users", route.path());
    }

    @Test
    void pathConcatenation() throws IOException {
        Path file = tempDir.resolve("UserController.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;
            
            @RestController
            @RequestMapping("/api/users")
            public class UserController {
                @GetMapping("/{id}")
                public User getUser(@PathVariable Long id) {
                    return null;
                }
                
                @GetMapping("")
                public List<User> getAll() {
                    return List.of();
                }
            }
            """);

        ParseResult result = adapter.parse("UserController.java", file);
        assertEquals(2, result.apiRoutes().size());
        
        // 查找 /{id} 路由
        ApiRoute idRoute = result.apiRoutes().stream()
            .filter(r -> r.path().contains("{id}"))
            .findFirst().orElse(null);
        assertNotNull(idRoute);
        assertEquals("GET", idRoute.httpMethod());
        assertEquals("/api/users/{id}", idRoute.path());
        assertEquals("/api/users", idRoute.basePath());
        assertEquals("/{id}", idRoute.methodPath());
        
        // 查找 "" 路由
        ApiRoute allRoute = result.apiRoutes().stream()
            .filter(r -> r.path().equals("/api/users"))
            .findFirst().orElse(null);
        assertNotNull(allRoute);
    }

    @Test
    void multipleHttpMethods() throws IOException {
        Path file = tempDir.resolve("CrudController.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api/items")
            public class CrudController {
                @GetMapping
                public List<Item> list() { return List.of(); }
                
                @PostMapping
                public Item create(@RequestBody Item item) { return item; }
                
                @PutMapping("/{id}")
                public Item update(@PathVariable Long id, @RequestBody Item item) { return item; }
                
                @DeleteMapping("/{id}")
                public void delete(@PathVariable Long id) {}
            }
            """);

        ParseResult result = adapter.parse("CrudController.java", file);
        assertEquals(4, result.apiRoutes().size());
        
        List<String> methods = result.apiRoutes().stream()
            .map(ApiRoute::httpMethod)
            .toList();
        assertTrue(methods.contains("GET"));
        assertTrue(methods.contains("POST"));
        assertTrue(methods.contains("PUT"));
        assertTrue(methods.contains("DELETE"));
    }

    @Test
    void noAnnotations() throws IOException {
        Path file = tempDir.resolve("UserService.java");
        Files.writeString(file, """
            package com.example;
            
            public class UserService {
                public void doSomething() {}
            }
            """);

        ParseResult result = adapter.parse("UserService.java", file);
        assertTrue(result.apiRoutes().isEmpty());
    }

    @Test
    void mixedAnnotations() throws IOException {
        Path file = tempDir.resolve("MixedController.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api")
            public class MixedController {
                @GetMapping("/users")
                public List<User> getUsers() { return List.of(); }
                
                @PostMapping("/orders")
                public Order createOrder(@RequestBody Order order) { return order; }
                
                @DeleteMapping("/users/{id}")
                public void deleteUser(@PathVariable Long id) {}
            }
            """);

        ParseResult result = adapter.parse("MixedController.java", file);
        assertEquals(3, result.apiRoutes().size());
        
        // 验证路径拼接
        assertTrue(result.apiRoutes().stream().anyMatch(r -> r.path().equals("/api/users")));
        assertTrue(result.apiRoutes().stream().anyMatch(r -> r.path().equals("/api/orders")));
        assertTrue(result.apiRoutes().stream().anyMatch(r -> r.path().equals("/api/users/{id}")));
    }

    @Test
    void patchMapping() throws IOException {
        Path file = tempDir.resolve("PatchController.java");
        Files.writeString(file, """
            package com.example;
            
            import org.springframework.web.bind.annotation.PatchMapping;
            import org.springframework.web.bind.annotation.RestController;
            
            @RestController
            public class PatchController {
                @PatchMapping("/api/users/{id}")
                public User patchUser(@PathVariable Long id) {
                    return null;
                }
            }
            """);

        ParseResult result = adapter.parse("PatchController.java", file);
        assertEquals(1, result.apiRoutes().size());
        assertEquals("PATCH", result.apiRoutes().getFirst().httpMethod());
    }
}
