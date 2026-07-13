package com.sodlinken.jindexer.mcp;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试搜索结果高亮逻辑
 */
class McpHighlightTest {

    @Test
    void highlightBasicMatch() throws Exception {
        McpServer server = createTestServer();
        Method method = McpServer.class.getDeclaredMethod("computeHighlights", String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) method.invoke(server, "import com.example.Foo;", "Foo");

        assertFalse(highlights.isEmpty());
        assertEquals(19, highlights.get(0).get("start"));
        assertEquals(22, highlights.get(0).get("end"));
    }

    @Test
    void highlightMultipleMatches() throws Exception {
        McpServer server = createTestServer();
        Method method = McpServer.class.getDeclaredMethod("computeHighlights", String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) method.invoke(server, "Foo.bar(Foo)", "Foo");

        assertEquals(2, highlights.size());
    }

    @Test
    void highlightCaseInsensitive() throws Exception {
        McpServer server = createTestServer();
        Method method = McpServer.class.getDeclaredMethod("computeHighlights", String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) method.invoke(server, "import com.example.FOO;", "foo");

        assertFalse(highlights.isEmpty());
    }

    @Test
    void highlightNullText() throws Exception {
        McpServer server = createTestServer();
        Method method = McpServer.class.getDeclaredMethod("computeHighlights", String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) method.invoke(server, (String) null, "Foo");

        assertTrue(highlights.isEmpty());
    }

    @Test
    void highlightWildcardQuery() throws Exception {
        McpServer server = createTestServer();
        Method method = McpServer.class.getDeclaredMethod("computeHighlights", String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) method.invoke(server, "import Foo;", "*");

        assertTrue(highlights.isEmpty());
    }

    @Test
    void highlightNoMatch() throws Exception {
        McpServer server = createTestServer();
        Method method = McpServer.class.getDeclaredMethod("computeHighlights", String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) method.invoke(server, "import Foo;", "Bar");

        assertTrue(highlights.isEmpty());
    }

    private McpServer createTestServer() {
        com.sodlinken.jindexer.config.Config config = new com.sodlinken.jindexer.config.Config();
        config.setProjectRoot(Path.of("."));
        config.setDataDir(Path.of("."));
        return new McpServer(config);
    }
}
