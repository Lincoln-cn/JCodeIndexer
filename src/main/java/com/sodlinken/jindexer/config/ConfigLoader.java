package com.sodlinken.jindexer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 配置加载器，优先级：环境变量 > CLI 参数 > config.yaml > 默认值
 */
public class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    /**
     * 加载配置：先读 config.yaml，再用环境变量覆盖
     */
    public static Config load(Path projectRoot, Path dataDir) {
        Config config = new Config();
        config.setProjectRoot(projectRoot);
        config.setDataDir(dataDir);

        // 尝试读取 config.yaml
        loadFromFile(config, projectRoot);

        // 环境变量覆盖（优先级最高）
        applyEnvironmentOverrides(config);

        log.info("配置加载完成: projectRoot={}, dataDir={}, embedding={}",
            config.getProjectRoot(), config.getDataDir(), config.isEmbeddingEnabled());
        return config;
    }

    private static void loadFromFile(Config config, Path projectRoot) {
        Path configPath = projectRoot.resolve(".jindexer/config.yaml");
        if (!Files.exists(configPath)) {
            // 尝试项目根目录下的 config.yaml
            configPath = projectRoot.resolve("config.yaml");
        }
        if (!Files.exists(configPath)) {
            log.info("未找到配置文件，使用默认配置");
            return;
        }

        log.info("读取配置文件: {}", configPath);
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(configPath)) {
            Map<String, Object> map = yaml.load(is);
            if (map == null) return;

            applyMapValues(config, map);
        } catch (IOException e) {
            log.warn("读取配置文件失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyMapValues(Config config, Map<String, Object> map) {
        if (map.containsKey("indexing")) {
            Map<String, Object> indexing = (Map<String, Object>) map.get("indexing");
            if (indexing.containsKey("threads")) {
                config.setIndexingThreads(((Number) indexing.get("threads")).intValue());
            }
            if (indexing.containsKey("extract_javadoc")) {
                config.setExtractJavadoc((Boolean) indexing.get("extract_javadoc"));
            }
            if (indexing.containsKey("follow_symlinks")) {
                config.setFollowSymlinks((Boolean) indexing.get("follow_symlinks"));
            }
            if (indexing.containsKey("max_file_size_kb")) {
                config.setMaxFileSizeKB(((Number) indexing.get("max_file_size_kb")).intValue());
            }
        }

        if (map.containsKey("embedding")) {
            Map<String, Object> embedding = (Map<String, Object>) map.get("embedding");
            if (embedding.containsKey("enabled")) {
                config.setEmbeddingEnabled((Boolean) embedding.get("enabled"));
            }
            if (embedding.containsKey("api_url")) {
                config.setEmbeddingApiUrl((String) embedding.get("api_url"));
            }
            if (embedding.containsKey("api_key")) {
                config.setEmbeddingApiKey((String) embedding.get("api_key"));
            }
            if (embedding.containsKey("model")) {
                config.setEmbeddingModel((String) embedding.get("model"));
            }
            if (embedding.containsKey("batch_size")) {
                config.setEmbeddingBatchSize(((Number) embedding.get("batch_size")).intValue());
            }
        }

        if (map.containsKey("data_dir")) {
            String dataDirStr = (String) map.get("data_dir");
            Path dataDirPath = Path.of(dataDirStr);
            if (!dataDirPath.isAbsolute()) {
                dataDirPath = config.getProjectRoot().resolve(dataDirPath);
            }
            config.setDataDir(dataDirPath);
        }

        if (map.containsKey("storage")) {
            Map<String, Object> storage = (Map<String, Object>) map.get("storage");
            if (storage.containsKey("db_name")) {
                config.setDbName((String) storage.get("db_name"));
            }
        }

        if (map.containsKey("log")) {
            Map<String, Object> logMap = (Map<String, Object>) map.get("log");
            if (logMap.containsKey("level")) {
                config.setLogLevel((String) logMap.get("level"));
            }
            if (logMap.containsKey("verbose")) {
                config.setVerbose((Boolean) logMap.get("verbose"));
            }
        }

        // 多项目配置
        if (map.containsKey("projects")) {
            List<Config.Project> projects = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> projectList = (List<Map<String, Object>>) map.get("projects");
            for (Map<String, Object> proj : projectList) {
                String name = (String) proj.get("name");
                String root = (String) proj.get("root");
                if (name != null && root != null) {
                    projects.add(new Config.Project(name, Path.of(root)));
                }
            }
            config.setProjects(projects);
        }
    }

    private static void applyEnvironmentOverrides(Config config) {
        // INDEXER_EMBEDDING_ENABLED
        String embeddingEnabled = System.getenv("INDEXER_EMBEDDING_ENABLED");
        if (embeddingEnabled != null) {
            config.setEmbeddingEnabled(Boolean.parseBoolean(embeddingEnabled));
        }

        // INDEXER_EMBEDDING_API_URL
        String embeddingApiUrl = System.getenv("INDEXER_EMBEDDING_API_URL");
        if (embeddingApiUrl != null && !embeddingApiUrl.isBlank()) {
            config.setEmbeddingApiUrl(embeddingApiUrl);
        }

        // INDEXER_EMBEDDING_API_KEY
        String embeddingApiKey = System.getenv("INDEXER_EMBEDDING_API_KEY");
        if (embeddingApiKey != null && !embeddingApiKey.isBlank()) {
            config.setEmbeddingApiKey(embeddingApiKey);
        }

        // INDEXER_EMBEDDING_MODEL
        String embeddingModel = System.getenv("INDEXER_EMBEDDING_MODEL");
        if (embeddingModel != null && !embeddingModel.isBlank()) {
            config.setEmbeddingModel(embeddingModel);
        }

        // INDEXER_THREADS
        String threads = System.getenv("INDEXER_THREADS");
        if (threads != null) {
            try {
                config.setIndexingThreads(Integer.parseInt(threads));
            } catch (NumberFormatException e) {
                log.warn("无效的 INDEXER_THREADS 值: {}", threads);
            }
        }

        // INDEXER_LOG_LEVEL
        String logLevel = System.getenv("INDEXER_LOG_LEVEL");
        if (logLevel != null && !logLevel.isBlank()) {
            config.setLogLevel(logLevel);
        }
    }
}
