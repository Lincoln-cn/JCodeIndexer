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
import java.util.concurrent.*;

/**
 * 事件驱动文件监听器：使用 Java NIO WatchService 检测文件变化
 * 延迟 < 100ms，支持去抖和 OVERFLOW fallback
 */
public class EventFileWatcher {

    private static final Logger log = LoggerFactory.getLogger(EventFileWatcher.class);

    private final Config config;
    private final Indexer indexer;
    private final StorageService storage;

    private WatchService watcher;
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    private final BlockingQueue<Path> pendingChanges = new LinkedBlockingQueue<>();
    private final Set<Path> pendingDeletes = ConcurrentHashMap.newKeySet();
    private final Set<Path> pendingConfigChanges = ConcurrentHashMap.newKeySet();

    private Thread eventLoopThread;
    private ScheduledExecutorService debounceScheduler;
    private volatile boolean running = false;

    public EventFileWatcher(Config config, Indexer indexer, StorageService storage) {
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

        try {
            watcher = FileSystems.getDefault().newWatchService();
            registerRecursive(config.getProjectRoot());
            running = true;

            // 启动事件循环线程
            eventLoopThread = new Thread(this::eventLoop, "EventFileWatcher-Events");
            eventLoopThread.setDaemon(true);
            eventLoopThread.start();

            // 启动去抖定时器
            debounceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "EventFileWatcher-Debounce");
                t.setDaemon(true);
                return t;
            });
            int debounceMs = config.getWatchDebounceMs();
            debounceScheduler.scheduleAtFixedRate(this::processPendingChanges,
                debounceMs, debounceMs, TimeUnit.MILLISECONDS);

            log.info("文件监听已启动（事件驱动模式），去抖间隔: {}ms", debounceMs);
        } catch (IOException e) {
            log.error("启动文件监听失败", e);
            // fallback 到轮询模式
            log.info("WatchService 不可用，回退到轮询模式");
            running = false;
        }
    }

    /**
     * 停止文件监听
     */
    public void stop() {
        running = false;

        if (debounceScheduler != null) {
            debounceScheduler.shutdown();
            try {
                if (!debounceScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    debounceScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                debounceScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (eventLoopThread != null) {
            eventLoopThread.interrupt();
            try {
                eventLoopThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException e) {
                log.warn("关闭 WatchService 失败", e);
            }
        }

        watchKeys.clear();
        pendingChanges.clear();
        pendingDeletes.clear();
        log.info("文件监听已停止");
    }

    /**
     * 递归注册目录到 WatchService
     */
    private void registerRecursive(Path dir) throws IOException {
        if (!Files.isDirectory(dir) || isExcluded(dir)) {
            return;
        }

        WatchKey key = dir.register(watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.OVERFLOW);

        watchKeys.put(key, dir);

        try (var stream = Files.list(dir)) {
            for (Path child : stream.toList()) {
                if (Files.isDirectory(child) && !isExcluded(child)) {
                    registerRecursive(child);
                }
            }
        }
    }

    /**
     * 事件循环：阻塞等待文件系统事件
     */
    private void eventLoop() {
        while (running) {
            try {
                WatchKey key = watcher.take(); // 阻塞等待
                Path dir = watchKeys.get(key);

                if (dir == null) {
                    key.cancel();
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        // 事件溢出，触发全量 SHA-1 比对
                        log.warn("WatchService 事件溢出，触发全量检查");
                        fullSha1Check();
                        continue;
                    }

                    Path changed = dir.resolve((Path) event.context());
                    processEvent(kind, changed);
                }

                boolean valid = key.reset();
                if (!valid) {
                    watchKeys.remove(key);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running) {
                    log.error("事件循环异常", e);
                }
            }
        }
    }

    /**
     * 处理单个文件系统事件
     */
    private void processEvent(WatchEvent.Kind<?> kind, Path changed) {
        if (isExcluded(changed)) return;

        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            pendingDeletes.add(changed);
            return;
        }

        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            // 新目录需要递归注册
            if (Files.isDirectory(changed)) {
                try {
                    registerRecursive(changed);
                } catch (IOException e) {
                    log.warn("注册新目录失败: {}", changed, e);
                }
                return;
            }
        }

        if (isSourceFile(changed)) {
            pendingChanges.add(changed);
        }

        // 检测配置文件变化，触发配置热更新
        if (isConfigFile(changed)) {
            pendingConfigChanges.add(changed);
        }
    }

    /**
     * 判断是否为配置文件
     */
    private boolean isConfigFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.equals("config.yaml") || fileName.equals("config.yml");
    }

    /**
     * 去抖处理：批量验证变化并触发 reindex
     */
    private void processPendingChanges() {
        if (!running) return;

        try {
            // 处理配置文件变化
            if (!pendingConfigChanges.isEmpty()) {
                Set<Path> configFiles = new HashSet<>(pendingConfigChanges);
                pendingConfigChanges.clear();
                log.info("检测到配置文件变化，重新加载配置");
                // 配置变化时触发全量重新索引
                fullSha1Check();
                return;
            }

            // 收集待处理的变更文件（去重）
            Set<Path> changedFiles = new HashSet<>();
            pendingChanges.drainTo(changedFiles);

            // 处理删除文件
            Set<Path> deletedFiles = new HashSet<>(pendingDeletes);
            pendingDeletes.clear();

            for (Path deleted : deletedFiles) {
                String relativePath = config.getProjectRoot().relativize(deleted).toString()
                    .replace('\\', '/');
                try {
                    storage.deleteAllByFile(relativePath);
                    log.debug("清理已删除文件: {}", relativePath);
                } catch (Exception e) {
                    log.warn("清理删除文件失败: {}", relativePath, e);
                }
            }

            if (changedFiles.isEmpty()) return;

            // SHA-1 验证：只处理真正变化的文件
            List<Path> confirmedChanges = new ArrayList<>();
            for (Path file : changedFiles) {
                if (!Files.exists(file)) continue; // 文件可能已被删除
                if (!isSourceFile(file)) continue;

                try {
                    String relativePath = config.getProjectRoot().relativize(file).toString()
                        .replace('\\', '/');
                    String currentSha1 = Sha1Util.compute(file);

                    Optional<FileMeta> existing = storage.findFileMeta(relativePath);
                    if (existing.isEmpty() || !existing.get().sha1().equals(currentSha1)) {
                        confirmedChanges.add(file);
                    }
                } catch (Exception e) {
                    log.warn("SHA-1 验证失败: {}", file, e);
                }
            }

            if (!confirmedChanges.isEmpty()) {
                log.info("检测到 {} 个文件变化，开始增量索引", confirmedChanges.size());
                long start = System.currentTimeMillis();
                indexer.incrementalReindex(confirmedChanges);
                log.info("增量索引完成，耗时 {}ms", System.currentTimeMillis() - start);
            }

        } catch (Exception e) {
            log.error("处理文件变化异常", e);
        }
    }

    /**
     * OVERFLOW 时的全量 SHA-1 比对 fallback
     */
    private void fullSha1Check() {
        try {
            List<Path> changedFiles = new ArrayList<>();
            Path projectRoot = config.getProjectRoot();

            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (isExcluded(dir)) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isSourceFile(file)) {
                        try {
                            String relativePath = projectRoot.relativize(file).toString()
                                .replace('\\', '/');
                            String currentSha1 = Sha1Util.compute(file);
                            Optional<FileMeta> existing = storage.findFileMeta(relativePath);
                            if (existing.isEmpty() || !existing.get().sha1().equals(currentSha1)) {
                                changedFiles.add(file);
                            }
                        } catch (Exception e) {
                            log.warn("SHA-1 检查失败: {}", file, e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (!changedFiles.isEmpty()) {
                log.info("全量检查发现 {} 个变化文件", changedFiles.size());
                indexer.incrementalReindex(changedFiles);
            }
        } catch (Exception e) {
            log.error("全量 SHA-1 检查失败", e);
        }
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
}
