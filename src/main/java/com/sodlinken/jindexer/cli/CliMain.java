package com.sodlinken.jindexer.cli;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.config.ConfigLoader;
import com.sodlinken.jindexer.indexer.Indexer;
import com.sodlinken.jindexer.model.IndexResult;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * CLI 入口点：处理 init 和 index 命令
 *
 * Usage:
 *   java -jar jindexer.jar --project-root <path> --init
 *   java -jar jindexer.jar --project-root <path> --index
 *   java -jar jindexer.jar --project-root <path>         (启动 MCP 服务)
 */
public class CliMain {

    private static final Logger log = LoggerFactory.getLogger(CliMain.class);

    public static void main(String[] args) {
        Path projectRoot = null;
        Path dataDir = null;
        boolean initMode = false;
        boolean indexMode = false;

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
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
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
            } else {
                // 启动 MCP 服务
                startMcpServer(config);
            }
        } catch (Exception e) {
            log.error("执行失败", e);
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
            System.out.println("Errors:");
            result.errors().forEach(e -> System.out.println("  - " + e));
        }

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

    private static void printUsage() {
        System.out.println("java-code-indexer");
        System.out.println();
        System.out.println("Usage: java -jar jindexer.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --project-root <path>  Java 项目根目录（默认当前目录）");
        System.out.println("  --data-dir <path>      索引数据目录");
        System.out.println("  --init                 初始化数据库 schema");
        System.out.println("  --index                索引项目（提取符号/引用/调用关系）");
        System.out.println("  --help, -h             显示帮助");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # 初始化数据库");
        System.out.println("  java -jar jindexer.jar --project-root /path/to/project --init");
        System.out.println();
        System.out.println("  # 索引项目");
        System.out.println("  java -jar jindexer.jar --project-root /path/to/project --index");
        System.out.println();
        System.out.println("  # 启动 MCP 服务（供 Claude Code 等工具调用）");
        System.out.println("  java -jar jindexer.jar --project-root /path/to/project");
    }
}
