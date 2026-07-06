package com.sodlinken.jindexer.mcp;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * MCP Server 入口点
 *
 * Usage:
 *   java -jar jindexer-mcp.jar [options]
 *
 * Options:
 *   --project-root <path>  Java 项目根目录
 *   --data-dir <path>      索引数据目录（默认 <project-root>/.jindexer）
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // 解析命令行参数
        Path projectRoot = null;
        Path dataDir = null;

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

            McpServer server = new McpServer(config);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("收到 shutdown 信号，正在关闭...");
                server.shutdown();
            }));

            server.start();
        } catch (Exception e) {
            log.error("启动失败", e);
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("java-code-indexer MCP Server");
        System.out.println();
        System.out.println("Usage: java -jar jindexer-mcp.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --project-root <path>  Java 项目根目录（默认当前目录）");
        System.out.println("  --data-dir <path>      索引数据目录");
        System.out.println("  --help, -h             显示帮助");
    }
}
