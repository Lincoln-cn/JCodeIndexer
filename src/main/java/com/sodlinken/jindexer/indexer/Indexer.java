package com.sodlinken.jindexer.indexer;

import com.sodlinken.jindexer.chunker.Chunker;
import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.*;
import com.sodlinken.jindexer.parser.*;
import com.sodlinken.jindexer.storage.DatabaseManager;
import com.sodlinken.jindexer.storage.StorageService;
import com.sodlinken.jindexer.util.Sha1Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 增量索引器
 * - 文件级增量：SHA-1 比对
 * - 事务保护：单文件内原子提交
 * - Virtual Threads 并行解析
 * - 支持 Java / YAML / Properties / .env / POM / Gradle 文件
 */
public class Indexer {

    private static final Logger log = LoggerFactory.getLogger(Indexer.class);

    private final Config config;
    private final StorageService storage;
    private final DatabaseManager dbManager;
    private final JavaParserAdapter javaParser;
    private final ConfigFileParser configFileParser;
    private final PomParser pomParser;
    private final GradleParser gradleParser;
    private final Chunker chunker;

    private volatile boolean indexing = false;
    private final Object lock = new Object();

    public Indexer(Config config, StorageService storage, DatabaseManager dbManager) {
        this.config = config;
        this.storage = storage;
        this.dbManager = dbManager;
        this.javaParser = new JavaParserAdapter(config);
        this.configFileParser = new ConfigFileParser();
        this.pomParser = new PomParser();
        this.gradleParser = new GradleParser();
        this.chunker = new Chunker();
    }

    /**
     * 执行增量索引
     */
    public IndexResult index() throws Exception {
        return index(ProgressListener.NONE);
    }

    /**
     * 执行增量索引（带进度回调）
     */
    public IndexResult index(ProgressListener progress) throws Exception {
        synchronized (lock) {
            if (indexing) {
                throw new IllegalStateException("索引正在进行中，请稍后重试");
            }
            indexing = true;
        }

        long startTime = System.currentTimeMillis();
        Path projectRoot = config.getProjectRoot();

        log.info("开始增量索引: {}", projectRoot);

        try {
            // 1. 扫描所有支持的文件
            progress.onPhaseStart("扫描文件", 0);

            List<Path> javaFiles = new ArrayList<>();
            List<Path> configFileFiles = new ArrayList<>();
            List<Path> pomFiles = new ArrayList<>();
            List<Path> gradleFiles = new ArrayList<>();

            scanFiles(projectRoot, javaFiles, configFileFiles, pomFiles, gradleFiles);

            int totalFiles = javaFiles.size() + configFileFiles.size() + pomFiles.size() + gradleFiles.size();
            progress.onPhaseEnd("扫描文件");
            log.info("扫描到 {} 个文件 (Java={}, Config={}, POM={}, Gradle={})",
                totalFiles, javaFiles.size(), configFileFiles.size(), pomFiles.size(), gradleFiles.size());

            // 2. 对比 SHA-1，找出需要更新的文件
            progress.onPhaseStart("比对哈希", totalFiles);

            List<Path> toUpdate = new ArrayList<>();
            List<String> toDelete = new ArrayList<>();
            Map<String, String> sha1Cache = new HashMap<>(); // 缓存 SHA-1 避免重复计算

            Set<String> currentFiles = new HashSet<>();
            List<Path> allFiles = new ArrayList<>();
            allFiles.addAll(javaFiles);
            allFiles.addAll(configFileFiles);
            allFiles.addAll(pomFiles);
            allFiles.addAll(gradleFiles);

            int fileIndex = 0;
            for (Path file : allFiles) {
                // 统一使用正斜杠，确保跨平台兼容
                String relativePath = projectRoot.relativize(file).toString().replace("\\", "/");
                currentFiles.add(relativePath);

                try {
                    String sha1 = Sha1Util.compute(file);
                    sha1Cache.put(relativePath, sha1); // 缓存 SHA-1
                    Optional<FileMeta> existing = storage.findFileMeta(relativePath);

                    if (existing.isEmpty() || !existing.get().sha1().equals(sha1)) {
                        toUpdate.add(file);
                    }
                } catch (NoSuchAlgorithmException e) {
                    log.warn("SHA-1 计算失败: {}", relativePath, e);
                }

                fileIndex++;
                progress.onProgress("比对哈希", fileIndex, totalFiles);
            }
            progress.onPhaseEnd("比对哈希");

            // 3. 删除已不存在的文件
            dbManager.executeInTransactionVoid(conn -> {
                var stmt = conn.prepareStatement("SELECT file_path FROM file_meta");
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    String indexedPath = rs.getString("file_path");
                    if (!currentFiles.contains(indexedPath)) {
                        toDelete.add(indexedPath);
                    }
                }
                return null;
            });

            // 4. 删除过期文件的数据
            for (String deletedFile : toDelete) {
                storage.deleteAllByFile(deletedFile);
                log.debug("删除过期文件: {}", deletedFile);
            }

            // 5. 并行解析和索引变更文件
            progress.onPhaseStart("索引文件", toUpdate.size());

            List<String> errors = Collections.synchronizedList(new ArrayList<String>());
            AtomicInteger indexed = new AtomicInteger(0);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<?>> futures = new ArrayList<>();
                Map<String, String> finalSha1Cache = sha1Cache;

                for (Path file : toUpdate) {
                    futures.add(executor.submit(() -> {
                        try {
                            indexFile(projectRoot, file, finalSha1Cache);
                        } catch (Exception e) {
                            String relPath = projectRoot.relativize(file).toString();
                            errors.add(relPath + ": " + e.getMessage());
                            log.warn("索引文件失败: {}", relPath, e);
                        } finally {
                            progress.onProgress("索引文件", indexed.incrementAndGet(), toUpdate.size());
                        }
                    }));
                }

                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        log.warn("等待索引任务完成失败", e);
                    }
                }
            }
            progress.onPhaseEnd("索引文件");

            // 6. 重新统计
            int[] stats = storage.getProjectStats();
            long durationMs = System.currentTimeMillis() - startTime;

            IndexResult result = new IndexResult(
                totalFiles, toUpdate.size(), toDelete.size(),
                stats[0], stats[1], stats[2], stats[3],
                stats[5], stats[6],
                durationMs, errors
            );

            log.info("索引完成: 更新={} 删除={} 耗时={}ms 错误={}",
                toUpdate.size(), toDelete.size(), durationMs, errors.size());

            return result;
        } finally {
            indexing = false;
        }
    }

    /**
     * 索引单个文件（事务保护）
     * 使用 diff 策略：仅更新变化的符号/块/配置/依赖，减少数据库写入
     */
    private void indexFile(Path projectRoot, Path filePath, Map<String, String> sha1Cache) throws Exception {
        // 统一使用正斜杠，确保跨平台兼容
        String relativePath = projectRoot.relativize(filePath).toString().replace("\\", "/");
        String fileName = filePath.getFileName().toString().toLowerCase();

        dbManager.executeInTransactionVoid(conn -> {
            if (fileName.endsWith(".java")) {
                indexJavaFile(relativePath, filePath);
            } else if (PomParser.isPomFile(fileName)) {
                indexPomFile(relativePath, filePath);
            } else if (GradleParser.isGradleFile(fileName)) {
                indexGradleFile(relativePath, filePath);
            } else if (ConfigFileParser.isConfigFile(fileName)) {
                indexConfigFile(relativePath, filePath);
            }

            // 更新文件元信息（使用缓存的 SHA-1）
            try {
                String sha1 = sha1Cache.getOrDefault(relativePath, Sha1Util.compute(filePath));
                long size = Files.size(filePath);
                long lastModified = Files.getLastModifiedTime(filePath).toMillis();
                FileMeta meta = new FileMeta(
                    relativePath, size, lastModified, sha1,
                    0, System.currentTimeMillis()
                );
                storage.upsertFileMeta(meta);
            } catch (Exception e) {
                throw new RuntimeException("更新文件元信息失败", e);
            }

            return null;
        });

        log.debug("索引完成: {}", relativePath);
    }

    /**
     * Java 文件 diff 索引：
     * 1. 引用/调用 → 总是全量删除重插（依赖 symbol ID）
     * 2. 符号 → 按 qualifiedName+kind 做 diff，仅增删变化项
     * 3. 代码块 → 按 type+className+name 做 diff
     */
    private void indexJavaFile(String relativePath, Path filePath) throws Exception {
        ParseResult parsed = javaParser.parse(relativePath, filePath);

        // --- 符号 diff（先插入符号，引用需要 symbol_id） ---
        List<Symbol> oldSymbols = storage.findSymbolsByFile(relativePath);
        List<Symbol> newSymbols = parsed.symbols();

        // 构建旧符号索引：key = qualifiedName + "|" + kind
        Map<String, Symbol> oldSymbolMap = new LinkedHashMap<>();
        for (Symbol s : oldSymbols) {
            oldSymbolMap.put(s.qualifiedName() + "|" + s.kind(), s);
        }

        // 构建新符号索引
        Map<String, Symbol> newSymbolMap = new LinkedHashMap<>();
        for (Symbol s : newSymbols) {
            newSymbolMap.put(s.qualifiedName() + "|" + s.kind(), s);
        }

        // 找出需要删除的符号 ID
        List<Long> symbolIdsToDelete = new ArrayList<>();
        for (Map.Entry<String, Symbol> entry : oldSymbolMap.entrySet()) {
            if (!newSymbolMap.containsKey(entry.getKey())) {
                symbolIdsToDelete.add(entry.getValue().id());
            }
        }
        if (!symbolIdsToDelete.isEmpty()) {
            storage.deleteSymbolsByIds(symbolIdsToDelete);
        }

        // 找出需要插入的符号（不在旧集合中的）
        List<Symbol> symbolsToInsert = new ArrayList<>();
        for (Map.Entry<String, Symbol> entry : newSymbolMap.entrySet()) {
            if (!oldSymbolMap.containsKey(entry.getKey())) {
                symbolsToInsert.add(entry.getValue());
            }
        }
        if (!symbolsToInsert.isEmpty()) {
            storage.insertSymbols(symbolsToInsert);
        }

        // --- 引用和调用：先删旧的，再解析 symbol_id 后插入 ---
        storage.deleteReferencesByFile(relativePath);
        storage.deleteCallsByFile(relativePath);

        // 解析引用的 symbol_id（先收集所有已知符号的 qualifiedName -> id 映射）
        List<Symbol> allSymbols = storage.listAllSymbols(10000);
        Map<String, Long> qualifiedNameToId = new LinkedHashMap<>();
        for (Symbol s : allSymbols) {
            qualifiedNameToId.put(s.qualifiedName(), s.id());
        }

        // 解析引用中的类型名，查找对应的 symbol_id
        for (Reference ref : parsed.references()) {
            String refName = extractReferenceName(ref.context());
            if (refName != null) {
                Long symbolId = qualifiedNameToId.get(refName);
                if (symbolId != null && symbolId > 0) {
                    Reference resolvedRef = new Reference(0, symbolId, ref.fromFile(), ref.fromLine(), ref.context());
                    storage.insertReference(resolvedRef);
                }
            }
        }

        for (Call call : parsed.calls()) {
            storage.insertCall(call);
        }

        // --- 代码块 diff ---
        String packageName = "";
        for (Symbol s : newSymbols) {
            if (s.kind() == Symbol.SymbolKind.CLASS) {
                int lastDot = s.qualifiedName().lastIndexOf('.');
                packageName = lastDot > 0 ? s.qualifiedName().substring(0, lastDot) : "";
                break;
            }
        }
        List<Chunk> newChunks = chunker.chunkFile(relativePath, filePath, packageName);
        List<Chunk> oldChunks = storage.findChunksByFile(relativePath);

        Map<String, Chunk> oldChunkMap = new LinkedHashMap<>();
        for (Chunk c : oldChunks) {
            oldChunkMap.put(c.type() + "|" + c.className() + "|" + c.name(), c);
        }

        Map<String, Chunk> newChunkMap = new LinkedHashMap<>();
        for (Chunk c : newChunks) {
            newChunkMap.put(c.type() + "|" + c.className() + "|" + c.name(), c);
        }

        List<Long> chunkIdsToDelete = new ArrayList<>();
        for (Map.Entry<String, Chunk> entry : oldChunkMap.entrySet()) {
            if (!newChunkMap.containsKey(entry.getKey())) {
                chunkIdsToDelete.add(entry.getValue().id());
            }
        }
        if (!chunkIdsToDelete.isEmpty()) {
            storage.deleteChunksByIds(chunkIdsToDelete);
        }

        List<Chunk> chunksToInsert = new ArrayList<>();
        for (Map.Entry<String, Chunk> entry : newChunkMap.entrySet()) {
            if (!oldChunkMap.containsKey(entry.getKey())) {
                chunksToInsert.add(entry.getValue());
            }
        }
        if (!chunksToInsert.isEmpty()) {
            storage.insertChunks(chunksToInsert);
        }

        log.debug("Java diff 完成: {} (符号: {}增{}删, 块: {}增{}删)",
            relativePath, symbolsToInsert.size(), symbolIdsToDelete.size(),
            chunksToInsert.size(), chunkIdsToDelete.size());
    }

    /**
     * 配置文件 diff 索引：按 filePath+key 做 diff
     */
    private void indexConfigFile(String relativePath, Path filePath) throws Exception {
        List<ConfigEntry> newEntries = configFileParser.parse(relativePath, filePath);
        List<ConfigEntry> oldEntries = storage.findConfigEntriesByFile(relativePath);

        Map<String, ConfigEntry> oldMap = new LinkedHashMap<>();
        for (ConfigEntry e : oldEntries) {
            oldMap.put(e.key(), e);
        }

        Map<String, ConfigEntry> newMap = new LinkedHashMap<>();
        for (ConfigEntry e : newEntries) {
            newMap.put(e.key(), e);
        }

        // 找出需要删除的 key
        List<String> keysToDelete = new ArrayList<>();
        for (String key : oldMap.keySet()) {
            if (!newMap.containsKey(key)) {
                keysToDelete.add(key);
            }
        }

        // 找出需要插入/更新的 entry
        List<ConfigEntry> entriesToUpsert = new ArrayList<>();
        for (Map.Entry<String, ConfigEntry> entry : newMap.entrySet()) {
            ConfigEntry old = oldMap.get(entry.getKey());
            if (old == null || !old.value().equals(entry.getValue().value())) {
                entriesToUpsert.add(entry.getValue());
            }
        }

        if (!keysToDelete.isEmpty()) {
            storage.deleteConfigEntriesByKeys(relativePath, keysToDelete);
        }
        if (!entriesToUpsert.isEmpty()) {
            storage.insertConfigEntries(entriesToUpsert);
        }

        log.debug("配置文件 diff 完成: {} ({}增{}删)", relativePath, entriesToUpsert.size(), keysToDelete.size());
    }

    /**
     * POM 文件 diff 索引：按 groupId+artifactId 做 diff
     */
    private void indexPomFile(String relativePath, Path filePath) throws Exception {
        indexDependencyFile(relativePath, filePath, pomParser.parse(relativePath, filePath), "POM");
    }

    /**
     * Gradle 文件 diff 索引：按 groupId+artifactId 做 diff
     */
    private void indexGradleFile(String relativePath, Path filePath) throws Exception {
        indexDependencyFile(relativePath, filePath, gradleParser.parse(relativePath, filePath), "Gradle");
    }

    /**
     * 依赖文件通用 diff 索引
     */
    private void indexDependencyFile(String relativePath, Path filePath,
                                      List<Dependency> newDeps, String type) throws Exception {
        List<Dependency> oldDeps = storage.findDependenciesByFile(relativePath);

        Map<String, Dependency> oldMap = new LinkedHashMap<>();
        for (Dependency d : oldDeps) {
            oldMap.put(d.groupId() + ":" + d.artifactId(), d);
        }

        Map<String, Dependency> newMap = new LinkedHashMap<>();
        for (Dependency d : newDeps) {
            newMap.put(d.groupId() + ":" + d.artifactId(), d);
        }

        List<String> keysToDelete = new ArrayList<>();
        for (String key : oldMap.keySet()) {
            if (!newMap.containsKey(key)) {
                keysToDelete.add(key);
            }
        }

        List<Dependency> depsToInsert = new ArrayList<>();
        for (Map.Entry<String, Dependency> entry : newMap.entrySet()) {
            if (!oldMap.containsKey(entry.getKey())) {
                depsToInsert.add(entry.getValue());
            }
        }

        if (!keysToDelete.isEmpty()) {
            storage.deleteDependenciesByKeys(relativePath, keysToDelete);
        }
        if (!depsToInsert.isEmpty()) {
            storage.insertDependencies(depsToInsert);
        }

        log.debug("{} diff 完成: {} ({}增{}删)", type, relativePath, depsToInsert.size(), keysToDelete.size());
    }

    /**
     * 扫描项目目录，按文件类型分类
     */
    private void scanFiles(Path root,
                           List<Path> javaFiles,
                           List<Path> configFileFiles,
                           List<Path> pomFiles,
                           List<Path> gradleFiles) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
                for (String pattern : config.getExcludePatterns()) {
                    String excludeDir = pattern.replace("**/", "").replace("/**", "");
                    if (dirName.equals(excludeDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.size() > config.getMaxFileSizeKB() * 1024L) {
                    return FileVisitResult.CONTINUE;
                }

                String fileName = file.getFileName().toString().toLowerCase();

                if (fileName.endsWith(".java")) {
                    javaFiles.add(file);
                } else if (ConfigFileParser.isConfigFile(fileName)) {
                    configFileFiles.add(file);
                } else if (PomParser.isPomFile(fileName)) {
                    pomFiles.add(file);
                } else if (GradleParser.isGradleFile(fileName)) {
                    gradleFiles.add(file);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    public boolean isIndexing() {
        return indexing;
    }

    /**
     * 从引用上下文中提取被引用的类型/类名
     */
    private String extractReferenceName(String context) {
        if (context == null) return null;
        // "import com.foo.Bar" -> "com.foo.Bar"
        if (context.startsWith("import ")) {
            return context.substring(7).trim();
        }
        // "type Bar" -> "Bar"
        if (context.startsWith("type ")) {
            return context.substring(5).trim();
        }
        return context.trim();
    }
}
