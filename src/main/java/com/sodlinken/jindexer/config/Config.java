package com.sodlinken.jindexer.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用配置，支持 config.yaml + 环境变量覆盖
 * 支持多项目模式：通过 projects 列表配置多个项目
 */
public class Config {

    /**
     * 多项目配置项
     */
    public record Project(String name, Path root) {}

    // === 项目路径 ===
    private Path projectRoot;
    private Path dataDir;

    // === 多项目 ===
    private List<Project> projects = new ArrayList<>();

    // === 索引 ===
    private int indexingThreads = 4;
    private boolean extractJavadoc = false;
    private boolean followSymlinks = false;
    private int maxFileSizeKB = 512;
    private String[] includePatterns = new String[]{
        "**/*.java", "**/*.yml", "**/*.yaml", "**/*.properties", "**/*.env",
        "**/pom.xml", "**/build.gradle", "**/build.gradle.kts"
    };
    private String[] excludePatterns = new String[]{
        "**/target/**", "**/build/**", "**/.git/**", "**/node_modules/**"
    };

    // === 存储 ===
    private String dbName = "index.db";

    // === 服务 ===
    private String logLevel = "INFO";
    private boolean verbose = false;

    // === 文件监听 ===
    private boolean watchEnabled = true;
    private int watchIntervalSeconds = 5;
    private String[] watchExclude = new String[]{
        "**/target/**", "**/build/**", "**/.git/**", "**/node_modules/**"
    };

    // === Getters / Setters ===

    public Path getProjectRoot() { return projectRoot; }
    public void setProjectRoot(Path projectRoot) { this.projectRoot = projectRoot; }

    public Path getDataDir() { return dataDir; }
    public void setDataDir(Path dataDir) { this.dataDir = dataDir; }

    public List<Project> getProjects() { return projects; }
    public void setProjects(List<Project> projects) { this.projects = projects; }

    public boolean isMultiProject() { return !projects.isEmpty(); }

    /**
     * 为指定项目生成数据目录路径
     */
    public Path getProjectDataDir(String projectName) {
        return dataDir.resolve(projectName);
    }

    /**
     * 为指定项目生成数据库路径
     */
    public Path getProjectDbPath(String projectName) {
        return getProjectDataDir(projectName).resolve(dbName);
    }

    public int getIndexingThreads() { return indexingThreads; }
    public void setIndexingThreads(int indexingThreads) { this.indexingThreads = indexingThreads; }

    public boolean isExtractJavadoc() { return extractJavadoc; }
    public void setExtractJavadoc(boolean extractJavadoc) { this.extractJavadoc = extractJavadoc; }

    public boolean isFollowSymlinks() { return followSymlinks; }
    public void setFollowSymlinks(boolean followSymlinks) { this.followSymlinks = followSymlinks; }

    public int getMaxFileSizeKB() { return maxFileSizeKB; }
    public void setMaxFileSizeKB(int maxFileSizeKB) { this.maxFileSizeKB = maxFileSizeKB; }

    public String[] getIncludePatterns() { return includePatterns; }
    public void setIncludePatterns(String[] includePatterns) { this.includePatterns = includePatterns; }

    public String[] getExcludePatterns() { return excludePatterns; }
    public void setExcludePatterns(String[] excludePatterns) { this.excludePatterns = excludePatterns; }

    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }

    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }

    public boolean isVerbose() { return verbose; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public boolean isWatchEnabled() { return watchEnabled; }
    public void setWatchEnabled(boolean watchEnabled) { this.watchEnabled = watchEnabled; }

    public int getWatchIntervalSeconds() { return watchIntervalSeconds; }
    public void setWatchIntervalSeconds(int watchIntervalSeconds) { this.watchIntervalSeconds = watchIntervalSeconds; }

    public String[] getWatchExclude() { return watchExclude; }
    public void setWatchExclude(String[] watchExclude) { this.watchExclude = watchExclude; }

    /**
     * 获取数据库文件的完整路径
     */
    public Path getDbPath() {
        return dataDir.resolve(dbName);
    }
}
