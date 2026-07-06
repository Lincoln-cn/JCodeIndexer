package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.model.Dependency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

/**
 * Gradle 构建文件依赖解析器
 * 支持 Groovy DSL (build.gradle) 和 Kotlin DSL (build.gradle.kts)
 * 使用正则表达式提取 implementation / api / compileOnly / runtimeOnly / testImplementation 等依赖声明
 */
public class GradleParser {

    // 匹配 dependencies { ... } 块中的依赖声明
    // 支持: implementation 'group:artifact:version'
    // 支持: implementation("group:artifact:version")
    // 支持: implementation group: 'group', name: 'artifact', version: 'version'
    private static final Pattern DEP_SHORT_PATTERN = Pattern.compile(
        "(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|annotationProcessor|kapt)\\s*\\(?\\s*['\"]([^'\"]+)['\"]"
    );

    private static final Pattern DEP_MAP_PATTERN = Pattern.compile(
        "(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|annotationProcessor|kapt)\\s*\\(?\\s*\\{[^}]*?group\\s*[:=]\\s*['\"]([^'\"]+)['\"][^}]*?name\\s*[:=]\\s*['\"]([^'\"]+)['\"][^}]*?(?:version\\s*[:=]\\s*['\"]([^'\"]+)['\"])?",
        Pattern.DOTALL
    );

    // 匹配 ext { ... } 或 gradle.properties 中的版本变量
    private static final Pattern VERSION_VAR_PATTERN = Pattern.compile(
        "(\\w[\\w.]*(?:Version|VER|ver)?)\\s*[:=]\\s*['\"]([^'\"]+)['\"]"
    );

    private static final Pattern SCOPE_SEPARATOR = Pattern.compile("['\"],\\s*['\"]");

    /**
     * 解析 build.gradle / build.gradle.kts 文件
     */
    public List<Dependency> parse(String relativePath, Path filePath) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        List<Dependency> deps = new ArrayList<>();

        // 提取版本变量
        Map<String, String> versionVars = extractVersionVars(content);

        // 提取短格式依赖: implementation 'group:artifact:version'
        Matcher shortMatcher = DEP_SHORT_PATTERN.matcher(content);
        while (shortMatcher.find()) {
            String scope = mapScope(shortMatcher.group(1));
            String coordinate = shortMatcher.group(2);
            parseCoordinate(relativePath, coordinate, scope, versionVars, content, deps);
        }

        // 提取 Map 格式依赖
        Matcher mapMatcher = DEP_MAP_PATTERN.matcher(content);
        while (mapMatcher.find()) {
            String scope = mapScope(mapMatcher.group(1));
            String groupId = mapMatcher.group(2);
            String artifactId = mapMatcher.group(3);
            String version = mapMatcher.group(4);

            if (version != null) {
                version = resolveVar(version, versionVars);
            }

            int line = findLine(content, artifactId);
            deps.add(new Dependency(
                0, relativePath, line,
                groupId, artifactId, version, scope,
                Dependency.DepType.GRADLE, null
            ));
        }

        return deps;
    }

    /**
     * 判断是否为 Gradle 构建文件
     */
    public static boolean isGradleFile(String fileName) {
        String lower = fileName.toLowerCase();
        return "build.gradle".equals(lower) || "build.gradle.kts".equals(lower);
    }

    private void parseCoordinate(String relativePath, String coordinate, String scope,
                                  Map<String, String> versionVars, String content,
                                  List<Dependency> deps) {
        // 支持 'group:artifact:version' 和 'group:artifact:version:classifier'
        String[] parts = coordinate.split(":");
        if (parts.length < 2) return;

        String groupId = parts[0];
        String artifactId = parts[1];
        String version = parts.length >= 3 ? resolveVar(parts[2], versionVars) : null;
        String classifier = parts.length >= 4 ? parts[3] : null;

        int line = findLine(content, artifactId);
        deps.add(new Dependency(
            0, relativePath, line,
            groupId, artifactId, version, scope,
            Dependency.DepType.GRADLE, classifier
        ));
    }

    private Map<String, String> extractVersionVars(String content) {
        Map<String, String> vars = new LinkedHashMap<>();
        Matcher m = VERSION_VAR_PATTERN.matcher(content);
        while (m.find()) {
            vars.put(m.group(1), m.group(2));
        }
        return vars;
    }

    private String resolveVar(String value, Map<String, String> vars) {
        if (value == null) return null;

        // 简单变量引用: versionVariables
        if (vars.containsKey(value)) {
            return vars.get(value);
        }

        // Groovy GString 变量引用: "${versionVariables}"
        for (var entry : vars.entrySet()) {
            if (value.contains("${" + entry.getKey() + "}")) {
                value = value.replace("${" + entry.getKey() + "}", entry.getValue());
            }
        }

        return value;
    }

    private String mapScope(String gradleScope) {
        return switch (gradleScope.toLowerCase()) {
            case "api" -> "compile";
            case "compileonly", "kapt" -> "provided";
            case "testimplementation", "testruntimeonly" -> "test";
            default -> "compile";
        };
    }

    private int findLine(String content, String keyword) {
        String[] lines = content.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(keyword)) {
                return i + 1;
            }
        }
        return 1;
    }
}
