package com.sodlinken.jindexer.model;

/**
 * 配置文件条目
 */
public record ConfigEntry(
    long id,
    String filePath,
    int line,
    String key,
    String value,
    ConfigType configType,
    String content
) {
    public enum ConfigType {
        YAML, PROPERTIES, ENV
    }
}
