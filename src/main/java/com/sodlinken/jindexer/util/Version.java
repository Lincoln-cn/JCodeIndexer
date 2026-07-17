package com.sodlinken.jindexer.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 版本信息工具类，从 version.properties 读取版本号
 * version.properties 由 Maven 资源过滤自动替换 ${project.version}
 */
public final class Version {

    private static final String VERSION;
    private static final String APP_NAME;

    static {
        String version = "unknown";
        String appName = "java-code-indexer";
        try (InputStream is = Version.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                version = props.getProperty("app.version", "unknown");
                appName = props.getProperty("app.name", "java-code-indexer");
            }
        } catch (IOException e) {
            // ignore
        }
        VERSION = version;
        APP_NAME = appName;
    }

    private Version() {}

    /**
     * 获取版本号（如 "1.7.0"）
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * 获取应用名称
     */
    public static String getAppName() {
        return APP_NAME;
    }

    /**
     * 获取完整版本字符串（如 "java-code-indexer v1.7.0"）
     */
    public static String getFullVersion() {
        return APP_NAME + " v" + VERSION;
    }
}
