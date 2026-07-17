package com.sodlinken.jindexer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

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

        log.info("配置加载完成: projectRoot={}, dataDir={}",
            config.getProjectRoot(), config.getDataDir());
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
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
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
                config.setIndexingThreads(toInt(indexing.get("threads"), 4));
            }
            if (indexing.containsKey("extract_javadoc")) {
                config.setExtractJavadoc(toBool(indexing.get("extract_javadoc"), false));
            }
            if (indexing.containsKey("follow_symlinks")) {
                config.setFollowSymlinks(toBool(indexing.get("follow_symlinks"), false));
            }
            if (indexing.containsKey("max_file_size_kb")) {
                config.setMaxFileSizeKB(toInt(indexing.get("max_file_size_kb"), 512));
            }
        }

        if (map.containsKey("data_dir")) {
            String dataDirStr = (String) map.get("data_dir");
            try {
                Path dataDirPath = Path.of(dataDirStr);
                if (!dataDirPath.isAbsolute()) {
                    dataDirPath = config.getProjectRoot().resolve(dataDirPath);
                }
                config.setDataDir(dataDirPath);
            } catch (Exception e) {
                log.warn("无效的数据目录路径: {}", dataDirStr);
            }
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

        // 文件监听配置
        if (map.containsKey("watch")) {
            Map<String, Object> watchMap = (Map<String, Object>) map.get("watch");
            if (watchMap.containsKey("enabled")) {
                config.setWatchEnabled(toBool(watchMap.get("enabled"), true));
            }
            if (watchMap.containsKey("mode")) {
                config.setWatchMode((String) watchMap.get("mode"));
            }
            if (watchMap.containsKey("interval")) {
                config.setWatchIntervalSeconds(toInt(watchMap.get("interval"), 5));
            }
            if (watchMap.containsKey("debounce_ms")) {
                config.setWatchDebounceMs(toInt(watchMap.get("debounce_ms"), 500));
            }
            if (watchMap.containsKey("exclude")) {
                @SuppressWarnings("unchecked")
                List<String> excludeList = (List<String>) watchMap.get("exclude");
                config.setWatchExclude(excludeList.toArray(new String[0]));
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

        // 自动发现多模块
        if (map.containsKey("auto_discover")) {
            config.setAutoDiscover(toBool(map.get("auto_discover"), false));
        }
    }

    private static void applyEnvironmentOverrides(Config config) {
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

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultValue;
    }

    private static boolean toBool(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }
}
