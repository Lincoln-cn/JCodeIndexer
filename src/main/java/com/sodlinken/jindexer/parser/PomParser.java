package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.model.Dependency;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * POM XML 依赖解析器
 * 使用 javax.xml 解析 Maven pom.xml 中的 dependency 声明
 */
public class PomParser {

    /**
     * 解析 pom.xml，返回依赖列表
     * 同时解析 properties 中的版本变量引用（如 ${xxx.version}）
     */
    public List<Dependency> parse(String relativePath, Path filePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(filePath.toFile());

        Map<String, String> properties = extractProperties(doc);
        String parentVersion = extractParentVersion(doc);
        String projectVersion = extractProjectVersion(doc);
        String projectGroupId = extractProjectGroupId(doc);
        String projectArtifactId = extractProjectArtifactId(doc);

        List<Dependency> deps = new ArrayList<>();

        // 解析 <dependencies> 下的 <dependency>
        NodeList depNodes = doc.getElementsByTagName("dependency");
        for (int i = 0; i < depNodes.getLength(); i++) {
            Element dep = (Element) depNodes.item(i);

            // 跳过 <dependencyManagement> 中的 dependency（只取实际声明的）
            if (isInsideDependencyManagement(dep)) continue;

            String groupId = getChildText(dep, "groupId");
            String artifactId = getChildText(dep, "artifactId");
            String version = getChildText(dep, "version");
            String scope = getChildText(dep, "scope");
            String classifier = getChildText(dep, "classifier");

            // 解析变量引用
            version = resolveVariable(version, properties, parentVersion, projectVersion, projectGroupId, projectArtifactId);
            if (scope == null || scope.isEmpty()) scope = "compile";

            if (artifactId != null && !artifactId.isEmpty()) {
                int line = findLineNumber(filePath, artifactId);
                deps.add(new Dependency(
                    0, relativePath, line,
                    groupId, artifactId, version, scope,
                    Dependency.DepType.POM, classifier
                ));
            }
        }

        return deps;
    }

    /**
     * 判断是否为 pom.xml
     */
    public static boolean isPomFile(String fileName) {
        return "pom.xml".equals(fileName.toLowerCase());
    }

    private Map<String, String> extractProperties(Document doc) {
        Map<String, String> props = new LinkedHashMap<>();
        NodeList propNodes = doc.getElementsByTagName("properties");
        if (propNodes.getLength() > 0) {
            Element propsElem = (Element) propNodes.item(0);
            NodeList children = propsElem.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element child) {
                    String key = child.getTagName();
                    String value = child.getTextContent().trim();
                    props.put(key, value);
                }
            }
        }
        return props;
    }

    private String extractParentVersion(Document doc) {
        NodeList parentNodes = doc.getElementsByTagName("parent");
        if (parentNodes.getLength() > 0) {
            Element parent = (Element) parentNodes.item(0);
            return getChildText(parent, "version");
        }
        return null;
    }

    private String extractProjectVersion(Document doc) {
        NodeList versionNodes = doc.getElementsByTagName("version");
        for (int i = 0; i < versionNodes.getLength(); i++) {
            Node node = versionNodes.item(i);
            Node parent = node.getParentNode();
            // 排除 <parent> 内的 version
            if (parent != null && parent.getParentNode() != null
                && "parent".equals(parent.getParentNode().getNodeName())) {
                continue;
            }
            String val = node.getTextContent().trim();
            if (!val.isEmpty()) return val;
        }
        return null;
    }

    private String extractProjectGroupId(Document doc) {
        // 优先取 <groupId>（非 parent 内的）
        NodeList groupNodes = doc.getElementsByTagName("groupId");
        for (int i = 0; i < groupNodes.getLength(); i++) {
            Node node = groupNodes.item(i);
            // 排除 <parent> 内的 groupId
            Node parent = node.getParentNode();
            if (parent != null && parent.getParentNode() != null
                && "parent".equals(parent.getParentNode().getNodeName())) {
                continue;
            }
            String val = node.getTextContent().trim();
            if (!val.isEmpty()) return val;
        }
        return null;
    }

    private String extractProjectArtifactId(Document doc) {
        NodeList artifactNodes = doc.getElementsByTagName("artifactId");
        for (int i = 0; i < artifactNodes.getLength(); i++) {
            Node node = artifactNodes.item(i);
            Node parent = node.getParentNode();
            if (parent != null && parent.getParentNode() != null
                && "parent".equals(parent.getParentNode().getNodeName())) {
                continue;
            }
            String val = node.getTextContent().trim();
            if (!val.isEmpty()) return val;
        }
        return null;
    }

    private String resolveVariable(String value, Map<String, String> properties,
                                   String parentVersion, String projectVersion,
                                   String projectGroupId, String projectArtifactId) {
        if (value == null) return null;

        // 处理 ${project.version} - 优先使用项目自身版本
        if ("${project.version}".equals(value) || "${pom.version}".equals(value)) {
            return projectVersion != null ? projectVersion : parentVersion;
        }

        // 处理 ${project.groupId}
        if ("${project.groupId}".equals(value)) {
            return projectGroupId != null ? projectGroupId : value;
        }

        // 处理 ${project.artifactId}
        if ("${project.artifactId}".equals(value)) {
            return projectArtifactId != null ? projectArtifactId : value;
        }

        // 处理 ${env.VAR_NAME}
        if (value.startsWith("${env.") && value.endsWith("}")) {
            String envKey = value.substring(6, value.length() - 1);
            String envVal = System.getenv(envKey);
            return envVal != null ? envVal : value;
        }

        // 处理 ${xxx.version} 等 properties 引用
        if (value.startsWith("${") && value.endsWith("}")) {
            String propKey = value.substring(2, value.length() - 1);
            return properties.getOrDefault(propKey, value);
        }

        return value;
    }

    private boolean isInsideDependencyManagement(Element dep) {
        Node parent = dep.getParentNode();
        while (parent != null) {
            if (parent instanceof Element elem && "dependencyManagement".equals(elem.getTagName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    private String getChildText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    private int findLineNumber(Path filePath, String artifactId) {
        if (filePath == null) return 1;
        try {
            List<String> lines = Files.readAllLines(filePath);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("<artifactId>" + artifactId + "</artifactId>")) {
                    return i + 1;
                }
            }
        } catch (IOException e) {
            // 忽略读取错误
        }
        return 1;
    }
}
