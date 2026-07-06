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

    // === Embedding（可选） ===
    private boolean embeddingEnabled = false;
    private String embeddingApiUrl = "";
    private String embeddingApiKey = "";
    private String embeddingModel = "text-embedding-3-small";
    private int embeddingBatchSize = 50;

    // === 服务 ===
    private String logLevel = "INFO";
    private boolean verbose = false;

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

    public boolean isEmbeddingEnabled() { return embeddingEnabled; }
    public void setEmbeddingEnabled(boolean embeddingEnabled) { this.embeddingEnabled = embeddingEnabled; }

    public String getEmbeddingApiUrl() { return embeddingApiUrl; }
    public void setEmbeddingApiUrl(String embeddingApiUrl) { this.embeddingApiUrl = embeddingApiUrl; }

    public String getEmbeddingApiKey() { return embeddingApiKey; }
    public void setEmbeddingApiKey(String embeddingApiKey) { this.embeddingApiKey = embeddingApiKey; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public int getEmbeddingBatchSize() { return embeddingBatchSize; }
    public void setEmbeddingBatchSize(int embeddingBatchSize) { this.embeddingBatchSize = embeddingBatchSize; }

    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }

    public boolean isVerbose() { return verbose; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    /**
     * 获取数据库文件的完整路径
     */
    public Path getDbPath() {
        return dataDir.resolve(dbName);
    }
}
