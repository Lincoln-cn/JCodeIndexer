package com.sodlinken.jindexer.cli;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.config.ConfigLoader;
import com.sodlinken.jindexer.indexer.Indexer;
import com.sodlinken.jindexer.model.IndexResult;
import com.sodlinken.jindexer.model.SearchResult;
import com.sodlinken.jindexer.search.StructuredSearch;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * CLI 入口点：处理 init, index, status, search 命令
 *
 * Usage:
 *   java -jar jindexer.jar --project-root <path> --init
 *   java -jar jindexer.jar --project-root <path> --index
 *   java -jar jindexer.jar --project-root <path> --status
 *   java -jar jindexer.jar --project-root <path> --search <query>
 *   java -jar jindexer.jar --project-root <path>           (启动 MCP 服务)
 */
public class CliMain {

    private static final Logger log = LoggerFactory.getLogger(CliMain.class);

    public static void main(String[] args) {
        // 快速处理 --version 和 --help（不需要项目路径）
        for (String arg : args) {
            if ("--version".equals(arg)) {
                printVersion();
                return;
            }
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsage();
                return;
            }
        }

        Path projectRoot = null;
        Path dataDir = null;
        boolean initMode = false;
        boolean indexMode = false;
        boolean statusMode = false;
        String searchQuery = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--project-root" -> {
                    if (i + 1 < args.length) {
                        projectRoot = Path.of(args[++i]);
                    }
                }
                case "--data-dir" -> {
                    if (i + 1 < args.length) {
                        dataDir = Path.of(args[++i]);
                    }
                }
                case "--init" -> initMode = true;
                case "--index" -> indexMode = true;
                case "--status" -> statusMode = true;
                case "--search" -> {
                    if (i + 1 < args.length) {
                        searchQuery = args[++i];
                    }
                }
            }
        }

        if (projectRoot == null) {
            projectRoot = Path.of(System.getProperty("user.dir"));
        }

        try {
            Path effectiveDataDir = dataDir != null ? dataDir : projectRoot.resolve(".jindexer");
            Config config = ConfigLoader.load(projectRoot, effectiveDataDir);

            if (dataDir != null) {
                config.setDataDir(dataDir);
            }

            DatabaseManager dbManager = new DatabaseManager(config.getDbPath());

            if (initMode) {
                runInit(dbManager);
            } else if (indexMode) {
                runIndex(config, dbManager);
            } else if (statusMode) {
                runStatus(config, dbManager);
            } else if (searchQuery != null) {
                runSearch(config, dbManager, searchQuery);
            } else {
                // 启动 MCP 服务
                startMcpServer(config);
            }
        } catch (Exception e) {
            log.error("执行失败: {}", e.getMessage());
            System.exit(1);
        }
    }

    private static void runInit(DatabaseManager dbManager) throws Exception {
        System.out.println("Initializing database schema...");
        dbManager.initialize();
        System.out.println("Schema initialized.");
        dbManager.close();
    }

    private static void runIndex(Config config, DatabaseManager dbManager) throws Exception {
        dbManager.initialize();
        StorageService storage = new StorageService(dbManager);
        Indexer indexer = new Indexer(config, storage, dbManager);

        System.out.println("Indexing project: " + config.getProjectRoot());

        CliProgressListener progress = new CliProgressListener(System.err);
        IndexResult result = indexer.index(progress);

        if (!result.errors().isEmpty()) {
            System.out.println("Errors (" + result.errors().size() + "):");
            result.errors().forEach(e -> System.out.println("  - " + e));
            System.exit(1);
        }

        dbManager.close();
    }

    private static void runStatus(Config config, DatabaseManager dbManager) throws Exception {
        dbManager.initialize();
        StorageService storage = new StorageService(dbManager);

        int[] stats = storage.getProjectStats();
        System.out.println("=== Index Status ===");
        System.out.println("Project: " + config.getProjectRoot());
        System.out.println("Data Dir: " + config.getDataDir());
        System.out.println("Symbols: " + stats[0]);
        System.out.println("References: " + stats[1]);
        System.out.println("Calls: " + stats[2]);
        System.out.println("Chunks: " + stats[3]);
        System.out.println("Files: " + stats[4]);
        System.out.println("Config Entries: " + stats[5]);
        System.out.println("Dependencies: " + stats[6]);

        dbManager.close();
    }

    private static void runSearch(Config config, DatabaseManager dbManager, String query) throws Exception {
        dbManager.initialize();
        StorageService storage = new StorageService(dbManager);
        StructuredSearch search = new StructuredSearch(storage);

        System.out.println("=== Search: " + query + " ===");
        SearchResult result = search.search(query, 20);

        System.out.println("Symbols found: " + result.symbols().size());
        for (var s : result.symbols()) {
            System.out.println("  [" + s.kind() + "] " + s.name() + " @ " + s.filePath() + ":" + s.startLine());
        }

        System.out.println("Chunks found: " + result.chunks().size());
        for (var c : result.chunks()) {
            System.out.println("  [" + c.type() + "] " + (c.name() != null ? c.name() : "") + " @ " + c.filePath() + ":" + c.startLine());
        }

        System.out.println("Total: " + result.totalHits() + " hits in " + result.queryTimeMs() + "ms");

        dbManager.close();
    }

    private static void startMcpServer(Config config) throws Exception {
        com.sodlinken.jindexer.mcp.McpServer server = new com.sodlinken.jindexer.mcp.McpServer(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("收到 shutdown 信号，正在关闭...");
            server.shutdown();
        }));

        server.start();
    }

    private static void printVersion() {
        System.out.println("java-code-indexer v0.6.0");
    }

    private static void printUsage() {
        System.out.println("java-code-indexer v0.6.0");
        System.out.println();
        System.out.println("Usage: java -jar jindexer.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --project-root <path>  Java 项目根目录（默认当前目录）");
        System.out.println("  --data-dir <path>      索引数据目录");
        System.out.println("  --init                 初始化数据库 schema");
        System.out.println("  --index                索引项目（提取符号/引用/调用关系）");
        System.out.println("  --status               显示索引统计信息");
        System.out.println("  --search <query>       直接搜索（无需启动 MCP 服务）");
        System.out.println("  --version              显示版本号");
        System.out.println("  --help, -h             显示帮助");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # 初始化数据库");
        System.out.println("  java -jar jindexer.jar --project-root /path/to/project --init");
        System.out.println();
        System.out.println("  # 索引项目");
        System.out.println("  java -jar jindexer.jar --project-root /path/to/project --index");
        System.out.println();
        System.out.println("  # 查看索引状态");
        System.out.println("  java -jar jindexer.jar --project-root /path/to/project --status");
        System.out.println();
        System.out.println("  # 搜索");
        System.out.println("  java -jar jindexer.jar --project-root /path/to/project --search McpServer");
        System.out.println();
        System.out.println("  # 启动 MCP 服务（供 Claude Code 等工具调用）");
        System.out.println("  java -jar jindexer.jar --project-root /path/to/project");
    }
}
