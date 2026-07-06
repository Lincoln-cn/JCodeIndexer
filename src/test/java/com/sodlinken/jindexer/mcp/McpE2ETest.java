package com.sodlinken.jindexer.mcp;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.config.ConfigLoader;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.indexer.Indexer;
import com.sodlinken.jindexer.storage.StorageService;

import java.nio.file.Path;

/**
 * 直接在进程内测试 MCP 服务器功能
 */
public class McpE2ETest {

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of("/home/ubuntu/jairouter/mcp/java-code-indexer");
        Path dataDir = projectRoot.resolve(".jindexer");
        Config config = ConfigLoader.load(projectRoot, dataDir);
        DatabaseManager db = new DatabaseManager(config.getDbPath());
        db.initialize();
        StorageService storage = new StorageService(db);
        Indexer indexer = new Indexer(config, storage, db);

        System.out.println("=== 1. 执行索引 ===");
        indexer.index();
        System.out.println("索引完成");

        System.out.println("\n=== 2. 数据库统计 ===");
        System.out.println("symbols: " + db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM symbols");
            return rs.next() ? rs.getInt(1) : 0;
        }));
        System.out.println("references: " + db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM code_references");
            return rs.next() ? rs.getInt(1) : 0;
        }));
        System.out.println("calls: " + db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM calls");
            return rs.next() ? rs.getInt(1) : 0;
        }));
        System.out.println("chunks: " + db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM chunks");
            return rs.next() ? rs.getInt(1) : 0;
        }));
        System.out.println("files: " + db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM file_meta");
            return rs.next() ? rs.getInt(1) : 0;
        }));

        System.out.println("\n=== 3. 按 kind 分组的 symbols ===");
        db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT kind, COUNT(*) as cnt FROM symbols GROUP BY kind ORDER BY cnt DESC");
            while (rs.next()) {
                System.out.println("  " + rs.getString(1) + ": " + rs.getInt(2));
            }
            return null;
        });

        System.out.println("\n=== 4. symbol_count 为 0 的文件 ===");
        db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT file_path, symbol_count FROM file_meta WHERE symbol_count = 0");
            while (rs.next()) {
                System.out.println("  " + rs.getString(1) + " (symbols: " + rs.getInt(2) + ")");
            }
            return null;
        });

        System.out.println("\n=== 5. 各文件 symbol 数量 ===");
        db.executeInTransaction(conn -> {
            var rs = conn.createStatement().executeQuery("SELECT file_path, symbol_count FROM file_meta ORDER BY symbol_count DESC");
            while (rs.next()) {
                System.out.println("  " + rs.getString(1) + " (symbols: " + rs.getInt(2) + ")");
            }
            return null;
        });

        System.out.println("\n=== 6. 搜索符号 - McpServer ===");
        var results = storage.searchSymbolsByName("McpServer", 5);
        for (var r : results) {
            System.out.println("  " + r.name() + " (" + r.kind() + ") - " + r.filePath() + ":" + r.startLine());
        }

        System.out.println("\n=== 7. 搜索符号 - Schema ===");
        results = storage.searchSymbolsByName("Schema", 5);
        for (var r : results) {
            System.out.println("  " + r.name() + " (" + r.kind() + ") - " + r.filePath() + ":" + r.startLine());
        }

        System.out.println("\n=== 8. 搜索代码块 ===");
        var chunks = storage.searchChunksByContent("initialize", 5);
        for (var c : chunks) {
            System.out.println("  " + c.name() + " (" + c.type() + ") - " + c.filePath() + ":" + c.startLine());
        }

        System.out.println("\n=== 9. 查找调用者 ===");
        var callers = storage.findCallers("com.sodlinken.jindexer.mcp.McpServer.handleToolCall");
        System.out.println("  调用者数量: " + callers.size());

        System.out.println("\n=== E2E 测试完成 ===");
    }
}
