package com.sodlinken.jindexer.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.Symbol;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 协议缺口与返回格式统一测试
 * 覆盖：progress capability 声明、ping 空结果、notifications/initialized 处理、
 * list_modules 无条件注册、单项目模式项目名、complexity_report 每方法复杂度、total 字段统一
 */
class McpProtocolGapTest {

    private static final Method HANDLE_MESSAGE;

    static {
        try {
            HANDLE_MESSAGE = McpServer.class.getDeclaredMethod("handleMessage", String.class);
            HANDLE_MESSAGE.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @TempDir
    Path tempDir;

    private ByteArrayOutputStream outBuffer;
    private PrintStream originalOut;
    private McpServer server;

    @BeforeEach
    void setUp() throws Exception {
        originalOut = System.out;
        outBuffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBuffer, true, StandardCharsets.UTF_8));

        Config config = new Config();
        config.setProjectRoot(tempDir);
        config.setDataDir(tempDir.resolve(".jindexer"));
        config.setWatchEnabled(false);

        server = new McpServer(config);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.shutdown();
        }
        System.setOut(originalOut);
    }

    /** 调用 handleMessage，返回写入 stdout 的完整输出并清空缓冲 */
    private String invoke(String json) throws Exception {
        outBuffer.reset();
        HANDLE_MESSAGE.invoke(server, json);
        return outBuffer.toString(StandardCharsets.UTF_8);
    }

    private JsonObject send(String json) throws Exception {
        String out = invoke(json);
        assertFalse(out.isBlank(), "应有响应输出");
        return JsonParser.parseString(out).getAsJsonObject();
    }

    /** 解析 tools/call 响应中的 result.content[0].text */
    private JsonObject toolResult(String json) throws Exception {
        JsonObject response = send(json);
        JsonObject result = response.getAsJsonObject("result");
        JsonArray content = result.getAsJsonArray("content");
        JsonObject text = content.get(0).getAsJsonObject();
        return JsonParser.parseString(text.get("text").getAsString()).getAsJsonObject();
    }

    // ==================== 协议缺口 ====================

    @Test
    void initializeDeclaresProgressCapability() throws Exception {
        JsonObject response = send("""
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}
            """);
        JsonObject result = response.getAsJsonObject("result");
        assertEquals("2024-11-05", result.get("protocolVersion").getAsString());
        JsonObject capabilities = result.getAsJsonObject("capabilities");
        assertTrue(capabilities.has("progress"), "initialize 应声明 progress capability");
        assertTrue(capabilities.has("tools"));
        assertTrue(capabilities.has("resources"));
    }

    @Test
    void pingReturnsEmptyResult() throws Exception {
        JsonObject response = send("""
            {"jsonrpc":"2.0","id":2,"method":"ping"}
            """);
        assertEquals(2, response.get("id").getAsInt());
        JsonObject result = response.getAsJsonObject("result");
        assertEquals(0, result.size(), "ping 应返回空 result");
    }

    @Test
    void initializedNotificationProducesNoResponse() throws Exception {
        String out = invoke("""
            {"jsonrpc":"2.0","method":"notifications/initialized"}
            """);
        assertTrue(out.isBlank(), "通知不应产生响应");
    }

    // ==================== 工具注册与项目名 ====================

    @Test
    void listModulesRegisteredInSingleProjectMode() throws Exception {
        JsonObject response = send("""
            {"jsonrpc":"2.0","id":3,"method":"tools/list"}
            """);
        JsonArray tools = response.getAsJsonObject("result").getAsJsonArray("tools");
        boolean hasListModules = false;
        for (var tool : tools) {
            if ("list_modules".equals(tool.getAsJsonObject().get("name").getAsString())) {
                hasListModules = true;
                break;
            }
        }
        assertTrue(hasListModules, "单项目模式下也应注册 list_modules");
    }

    @Test
    void singleProjectUsesRootDirectoryName() throws Exception {
        JsonObject result = toolResult("""
            {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"list_projects"}}
            """);
        String expected = tempDir.getFileName().toString();
        assertEquals(expected, result.get("default_project").getAsString());
        assertNotEquals("default", result.get("default_project").getAsString());
    }

    // ==================== complexity_report 每方法复杂度 ====================

    @Test
    void complexityReportComputesPerMethodComplexity() throws Exception {
        // 预建数据库并写入符号（与 ProjectContext 使用同一路径）
        Config config = new Config();
        config.setProjectRoot(tempDir);
        config.setDataDir(tempDir.resolve(".jindexer"));
        DatabaseManager db = new DatabaseManager(config.getDbPath());
        db.initialize();
        StorageService storage = new StorageService(db);

        Files.writeString(tempDir.resolve("TestClass.java"), """
            package com.example;

            public class TestClass {
                public void simple() {
                    System.out.println("hi");
                }

                public void complex(int x) {
                    if (x > 0) {
                        if (x > 10) {
                            for (int i = 0; i < x; i++) {
                                System.out.println(i);
                            }
                        }
                    }
                }
            }
            """);

        storage.insertSymbol(new Symbol(0, "TestClass.java", 3, 16,
            Symbol.SymbolKind.CLASS, "TestClass", "com.example.TestClass",
            "class TestClass", "", "", 1, ""));
        storage.insertSymbol(new Symbol(0, "TestClass.java", 4, 6,
            Symbol.SymbolKind.METHOD, "simple", "com.example.TestClass.simple",
            "void simple()", "void", "TestClass", 1, ""));
        storage.insertSymbol(new Symbol(0, "TestClass.java", 8, 15,
            Symbol.SymbolKind.METHOD, "complex", "com.example.TestClass.complex",
            "void complex(int x)", "void", "TestClass", 1, ""));
        db.close();

        JsonObject result = toolResult("""
            {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"complexity_report","arguments":{"class_name":"TestClass"}}}
            """);

        assertEquals(2, result.get("total").getAsInt());
        JsonArray methods = result.getAsJsonArray("methods");
        assertEquals(2, methods.size());

        JsonObject simple = methods.get(0).getAsJsonObject();
        assertEquals("simple", simple.get("name").getAsString());
        assertEquals(1, simple.get("cyclomatic_complexity").getAsInt());
        assertEquals(0, simple.get("cognitive_complexity").getAsInt());

        JsonObject complex = methods.get(1).getAsJsonObject();
        assertEquals("complex", complex.get("name").getAsString());
        assertEquals(4, complex.get("cyclomatic_complexity").getAsInt());
        assertEquals(3, complex.get("cognitive_complexity").getAsInt());
    }

    // ==================== total 字段统一 ====================

    @Test
    void findRouteReturnsTotal() throws Exception {
        JsonObject result = toolResult("""
            {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"find_route","arguments":{"http_method":"GET","path":"/api/nonexistent"}}}
            """);
        assertFalse(result.get("matched").getAsBoolean());
        assertTrue(result.has("total"), "find_route 应返回 total 字段");
        assertEquals(0, result.get("total").getAsInt());
    }

    @Test
    void getTypeHierarchyReturnsTotal() throws Exception {
        JsonObject result = toolResult("""
            {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"get_type_hierarchy","arguments":{"class_name":"NoSuchClass"}}}
            """);
        assertTrue(result.has("total"), "get_type_hierarchy 应返回 total 字段");
        assertEquals(0, result.get("total").getAsInt());
    }

    @Test
    void indexStatusReturnsTotal() throws Exception {
        JsonObject result = toolResult("""
            {"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"index_status"}}
            """);
        assertTrue(result.has("total"), "index_status 应返回 total 字段");
        assertTrue(result.has("symbols"));
        assertTrue(result.has("files"));
    }
}
