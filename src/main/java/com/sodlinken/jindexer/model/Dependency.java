package com.sodlinken.jindexer.model;

/**
 * 构建文件依赖声明
 */
public record Dependency(
    long id,
    String filePath,
    int line,
    String groupId,
    String artifactId,
    String version,
    String scope,
    DepType depType,
    String classifier
) {
    public enum DepType {
        POM, GRADLE, PACKAGE_JSON
    }
}
