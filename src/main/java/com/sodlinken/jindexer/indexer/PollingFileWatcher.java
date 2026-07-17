package com.sodlinken.jindexer.indexer;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.FileMeta;
import com.sodlinken.jindexer.storage.StorageService;
import com.sodlinken.jindexer.util.Sha1Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 轮询模式文件监听器：定时检查文件变化并触发增量索引
 */
public class PollingFileWatcher {

    private static final Logger log = LoggerFactory.getLogger(PollingFileWatcher.class);

    private final Config config;
    private final Indexer indexer;
    private final StorageService storage;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    private volatile long lastCheckTime = 0;

    public PollingFileWatcher(Config config, Indexer indexer, StorageService storage) {
        this.config = config;
        this.indexer = indexer;
        this.storage = storage;
    }

    /**
     * 启动文件监听
     */
    public void start() {
        if (!config.isWatchEnabled()) {
            log.info("文件监听已禁用");
            return;
        }

        if (running) {
            log.warn("文件监听已在运行");
            return;
        }

        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PollingFileWatcher");
            t.setDaemon(true);
            return t;
        });

        int interval = config.getWatchIntervalSeconds();
        scheduler.scheduleAtFixedRate(this::checkForChanges, interval, interval, TimeUnit.SECONDS);
        log.info("文件监听已启动，检查间隔: {}s", interval);
    }

    /**
     * 停止文件监听
     */
    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("文件监听已停止");
    }

    /**
     * 检查文件变化
     */
    private void checkForChanges() {
        if (!running) return;

        try {
            long startTime = System.currentTimeMillis();
            List<Path> changedFiles = detectChanges();

            if (!changedFiles.isEmpty()) {
                log.info("检测到 {} 个文件变化，开始增量索引", changedFiles.size());
                indexer.incrementalReindex(changedFiles);
                log.info("增量索引完成，耗时 {}ms", System.currentTimeMillis() - startTime);
            }

            lastCheckTime = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("文件监听异常", e);
        }
    }

    /**
     * 检测文件变化：通过 SHA-1 比对
     */
    private List<Path> detectChanges() throws IOException {
        List<Path> changedFiles = new ArrayList<>();
        Path projectRoot = config.getProjectRoot();

        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
                // 跳过排除的目录
                if (isExcluded(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isSourceFile(file)) {
                    try {
                        String relativePath = projectRoot.relativize(file).toString()
                            .replace('\\', '/');
                        String currentSha1 = Sha1Util.compute(file);

                        // 获取已存储的 SHA-1
                        Optional<FileMeta> existing = storage.findFileMeta(relativePath);
                        if (existing.isEmpty() || !existing.get().sha1().equals(currentSha1)) {
                            changedFiles.add(file);
                        }
                    } catch (Exception e) {
                        log.warn("检查文件变化失败: {}", file, e);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return changedFiles;
    }

    /**
     * 判断是否为源文件
     */
    private boolean isSourceFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".java") ||
               fileName.endsWith(".kt") ||
               fileName.endsWith(".kts") ||
               fileName.endsWith(".scala") ||
               fileName.endsWith(".sc") ||
               fileName.endsWith(".xml") ||
               fileName.endsWith(".yml") ||
               fileName.endsWith(".yaml") ||
               fileName.endsWith(".properties") ||
               fileName.endsWith(".env") ||
               fileName.endsWith(".gradle");
    }

    /**
     * 判断是否在排除目录中
     */
    private boolean isExcluded(Path path) {
        String pathStr = path.toString().replace('\\', '/');
        for (String exclude : config.getWatchExclude()) {
            // 简单的 glob 匹配
            String pattern = exclude.replace("**", ".*").replace("*", "[^/]*");
            if (pathStr.matches(".*" + pattern + ".*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取监听状态
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 获取最后检查时间
     */
    public long getLastCheckTime() {
        return lastCheckTime;
    }
}
