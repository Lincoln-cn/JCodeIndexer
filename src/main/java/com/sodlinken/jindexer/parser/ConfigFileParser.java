package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.model.ConfigEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * YAML / Properties 配置文件解析器
 * - YAML：snakeyaml 解析 + 扁平化嵌套 key
 * - Properties：逐行解析 key=value
 */
public class ConfigFileParser {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileParser.class);
    private static final Set<String> YAML_EXTENSIONS = Set.of(".yml", ".yaml");
    private static final Set<String> PROPERTIES_EXTENSIONS = Set.of(".properties", ".env");

    /**
     * 解析配置文件，返回 ConfigEntry 列表
     */
    public List<ConfigEntry> parse(String relativePath, Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();
        String content = Files.readString(filePath, StandardCharsets.UTF_8);

        if (YAML_EXTENSIONS.stream().anyMatch(fileName::endsWith)) {
            return parseYaml(relativePath, content);
        } else if (fileName.endsWith(".properties")) {
            return parseProperties(relativePath, content);
        } else if (fileName.endsWith(".env")) {
            return parseEnv(relativePath, content);
        }

        return List.of();
    }

    /**
     * 判断是否为可识别的配置文件
     */
    public static boolean isConfigFile(String fileName) {
        String lower = fileName.toLowerCase();
        return YAML_EXTENSIONS.stream().anyMatch(lower::endsWith)
            || lower.endsWith(".properties")
            || lower.endsWith(".env");
    }

    private List<ConfigEntry> parseYaml(String relativePath, String content) {
        List<ConfigEntry> entries = new ArrayList<>();

        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object data = yaml.load(content);
            if (data instanceof Map<?, ?> map) {
                flattenYamlMap(map, "", relativePath, content, entries);
            }
        } catch (Exception e) {
            log.warn("YAML 解析失败: {}", e.getMessage());
        }

        return entries;
    }

    @SuppressWarnings("unchecked")
    private void flattenYamlMap(Map<?, ?> map, String prefix, String relativePath, String content, List<ConfigEntry> entries) {
        for (var entry : map.entrySet()) {
            String key = entry.getKey().toString();
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;

            if (entry.getValue() instanceof Map<?, ?> nested) {
                flattenYamlMap(nested, fullKey, relativePath, content, entries);
            } else if (entry.getValue() instanceof List<?> list) {
                String value = listToString(list);
                int line = findLineNumber(content, key);
                entries.add(new ConfigEntry(0, relativePath, line, fullKey, value, ConfigEntry.ConfigType.YAML, content));
            } else {
                String value = entry.getValue() != null ? entry.getValue().toString() : null;
                int line = findLineNumber(content, key);
                entries.add(new ConfigEntry(0, relativePath, line, fullKey, value, ConfigEntry.ConfigType.YAML, content));
            }
        }
    }

    private List<ConfigEntry> parseProperties(String relativePath, String content) {
        List<ConfigEntry> entries = new ArrayList<>();
        String[] lines = content.split("\\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;

            int eqIdx = line.indexOf('=');
            if (eqIdx < 0) eqIdx = line.indexOf(':');
            if (eqIdx < 0) continue;

            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();

            // 处理 ${} 占位符引用
            entries.add(new ConfigEntry(0, relativePath, i + 1, key, value, ConfigEntry.ConfigType.PROPERTIES, content));
        }

        return entries;
    }

    private List<ConfigEntry> parseEnv(String relativePath, String content) {
        List<ConfigEntry> entries = new ArrayList<>();
        String[] lines = content.split("\\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            int eqIdx = line.indexOf('=');
            if (eqIdx < 0) continue;

            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();

            // 去除引号
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }

            entries.add(new ConfigEntry(0, relativePath, i + 1, key, value, ConfigEntry.ConfigType.ENV, content));
        }

        return entries;
    }

    private int findLineNumber(String content, String key) {
        String[] lines = content.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith(key + ":") || trimmed.startsWith(key + " :")) {
                return i + 1;
            }
        }
        return 1;
    }

    private String listToString(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}
