package com.sodlinken.jindexer.indexer;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.parser.PomParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多模块项目自动发现
 * 支持 Maven (pom.xml <modules>) 和 Gradle (settings.gradle include)
 */
public class ModuleDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ModuleDiscovery.class);

    private static final Pattern GRADLE_INCLUDE_PATTERN =
        Pattern.compile("['\"](:[\\w:.-]+)['\"]");

    private ModuleDiscovery() {}

    /**
     * 自动发现所有子模块
     * @return 子模块路径列表（包含根目录本身）
     */
    public static List<Path> discover(Config config) {
        List<Path> modules = new ArrayList<>();
        Path root = config.getProjectRoot();
        modules.add(root); // 根目录本身也是一个模块

        if (Files.exists(root.resolve("pom.xml"))) {
            modules.addAll(discoverMavenModules(root));
        } else if (Files.exists(root.resolve("settings.gradle"))
                || Files.exists(root.resolve("settings.gradle.kts"))) {
            modules.addAll(discoverGradleModules(root));
        } else {
            log.info("未检测到 Maven/Gradle 多模块配置，仅索引根目录");
        }

        log.info("发现 {} 个模块", modules.size());
        return modules;
    }

    /**
     * 递归发现 Maven 子模块
     * 解析 pom.xml 的 <modules><module>xxx</module></modules>
     */
    public static List<Path> discoverMavenModules(Path root) {
        List<Path> modules = new ArrayList<>();
        PomParser parser = new PomParser();

        try {
            Path pomPath = root.resolve("pom.xml");
            List<String> moduleNames = parser.parseModules(pomPath);

            for (String moduleName : moduleNames) {
                Path modulePath = root.resolve(moduleName);
                if (Files.isDirectory(modulePath)) {
                    modules.add(modulePath);
                    // 递归发现子模块
                    modules.addAll(discoverMavenModules(modulePath));
                } else {
                    log.warn("Maven 模块目录不存在: {}", modulePath);
                }
            }
        } catch (Exception e) {
            log.warn("解析 Maven 模块失败: {}", root.resolve("pom.xml"), e);
        }

        return modules;
    }

    /**
     * 发现 Gradle 子模块
     * 解析 settings.gradle / settings.gradle.kts 的 include 语句
     */
    public static List<Path> discoverGradleModules(Path root) {
        List<Path> modules = new ArrayList<>();

        Path settingsGradle = root.resolve("settings.gradle");
        Path settingsGradleKts = root.resolve("settings.gradle.kts");
        Path settingsFile = Files.exists(settingsGradle) ? settingsGradle : settingsGradleKts;

        if (settingsFile == null || !Files.exists(settingsFile)) {
            return modules;
        }

        try {
            String content = Files.readString(settingsFile);
            List<String> moduleNames = parseGradleIncludes(content);

            for (String moduleName : moduleNames) {
                // Gradle include 使用冒号格式: ":module-a" -> "module-a"
                String dirName = moduleName.startsWith(":") ? moduleName.substring(1) : moduleName;
                // 处理嵌套路径: ":parent:child" -> "parent/child"
                dirName = dirName.replace(":", "/");
                Path modulePath = root.resolve(dirName);
                if (Files.isDirectory(modulePath)) {
                    modules.add(modulePath);
                } else {
                    log.warn("Gradle 模块目录不存在: {}", modulePath);
                }
            }
        } catch (IOException e) {
            log.warn("读取 settings.gradle 失败: {}", settingsFile, e);
        }

        return modules;
    }

    /**
     * 从 settings.gradle 内容中解析 include 语句
     */
    static List<String> parseGradleIncludes(String content) {
        List<String> modules = new ArrayList<>();
        Matcher matcher = GRADLE_INCLUDE_PATTERN.matcher(content);
        while (matcher.find()) {
            modules.add(matcher.group(1));
        }
        return modules;
    }
}
