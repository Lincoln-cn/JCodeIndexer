package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.Annotation;
import com.sodlinken.jindexer.model.Call;
import com.sodlinken.jindexer.model.Reference;
import com.sodlinken.jindexer.model.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scala 文件解析器
 * 使用正则表达式提取类、对象、trait、函数等符号
 */
public class ScalaParserAdapter {

    private static final Logger log = LoggerFactory.getLogger(ScalaParserAdapter.class);
    private final Config config;

    // 匹配类声明: class, case class, abstract class, sealed class
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "(?:case\\s+|sealed\\s+|abstract\\s+|final\\s+|private\\s+|protected\\s+)*" +
        "(class)\\s+(\\w+)(?:\\[[^\\]]*\\])?" +
        "(?:\\s*\\([^)]*\\))?" +
        "(?:\\s*extends\\s+([^{]+))?",
        Pattern.MULTILINE
    );

    // 匹配对象声明
    private static final Pattern OBJECT_PATTERN = Pattern.compile(
        "(?:private\\s+|protected\\s+)*" +
        "object\\s+(\\w+)" +
        "(?:\\s*extends\\s+([^{]+))?",
        Pattern.MULTILINE
    );

    // 匹配 trait 声明
    private static final Pattern TRAIT_PATTERN = Pattern.compile(
        "(?:sealed\\s+|abstract\\s+)*" +
        "trait\\s+(\\w+)(?:\\[[^\\]]*\\])?" +
        "(?:\\s*extends\\s+([^{]+))?",
        Pattern.MULTILINE
    );

    // 匹配函数声明
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "(?:override\\s+|private\\s+|protected\\s+|final\\s+|abstract\\s+|implicit\\s+|lazy\\s+)*" +
        "def\\s+(\\w+)(?:\\[[^\\]]*\\])?\\s*\\(([^)]*)\\)" +
        "(?:\\s*:\\s*(\\S+))?",
        Pattern.MULTILINE
    );

    // 匹配 val/var 声明
    private static final Pattern VAL_PATTERN = Pattern.compile(
        "(?:private\\s+|protected\\s+|override\\s+|lazy\\s+|val|var)\\s+" +
        "(\\w+)(?:\\s*:\\s*(\\S+))?",
        Pattern.MULTILINE
    );

    // 匹配 package 声明
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
        "^package\\s+([\\w.]+)",
        Pattern.MULTILINE
    );

    // 匹配 import 声明
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "^import\\s+([\\w.*]+)",
        Pattern.MULTILINE
    );

    public ScalaParserAdapter(Config config) {
        this.config = config;
    }

    /**
     * 解析单个 Scala 文件
     */
    public ParseResult parse(String relativePath, Path filePath) {
        List<Symbol> symbols = new ArrayList<>();
        List<Reference> references = new ArrayList<>();
        List<Call> calls = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            String content = Files.readString(filePath);
            String[] lines = content.split("\n");

            // 提取包名
            String packageName = extractPackage(content);

            // 提取 import 引用
            extractImports(content, relativePath, references);

            // 提取类声明
            extractClasses(content, lines, relativePath, packageName, symbols);

            // 提取对象声明
            extractObjects(content, lines, relativePath, packageName, symbols);

            // 提取 trait 声明
            extractTraits(content, lines, relativePath, packageName, symbols);

            // 提取函数声明
            extractFunctions(content, lines, relativePath, packageName, symbols);

            // 提取 val/var 声明
            extractValues(content, lines, relativePath, packageName, symbols);

            // 提取注解
            extractAnnotations(content, annotations);

        } catch (IOException e) {
            log.warn("读取 Scala 文件失败: {}", relativePath, e);
            errors.add(relativePath + ": " + e.getMessage());
        } catch (Exception e) {
            log.warn("解析 Scala 文件失败: {}", relativePath, e);
            errors.add(relativePath + ": " + e.getMessage());
        }

        return new ParseResult(symbols, references, calls, annotations, errors,
            List.of(), List.of(), List.of(), List.of());
    }

    /**
     * 判断是否为 Scala 文件
     */
    public static boolean isScalaFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".scala") || lower.endsWith(".sc");
    }

    private String extractPackage(String content) {
        Matcher m = PACKAGE_PATTERN.matcher(content);
        return m.find() ? m.group(1) : "";
    }

    private void extractImports(String content, String relativePath, List<Reference> references) {
        Matcher m = IMPORT_PATTERN.matcher(content);
        while (m.find()) {
            String importPath = m.group(1);
            int line = findLineNumber(content, m.group(0));
            references.add(new Reference(0, 0, relativePath, line, "import " + importPath));
        }
    }

    private void extractClasses(String content, String[] lines, String relativePath,
                               String packageName, List<Symbol> symbols) {
        Matcher m = CLASS_PATTERN.matcher(content);
        while (m.find()) {
            String className = m.group(2);
            String supertypes = m.group(3);

            String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
            int line = findLineNumber(content, m.group(0));
            int modifiers = extractModifiers(m.group(0));

            // 检查 Scala 特性
            String beforeClass = content.substring(0, m.start());
            boolean isCaseClass = beforeClass.contains("case class") || m.group(0).contains("case class");

            // 解析继承信息
            String superClass = null;
            List<String> interfaces = new ArrayList<>();
            if (supertypes != null) {
                parseSupertypes(supertypes, superClass, interfaces);
            }

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.CLASS, className, qualifiedName,
                buildClassSignature(className, isCaseClass, supertypes),
                null, null, modifiers, null,
                superClass, interfaces.isEmpty() ? null : interfaces,
                false, false, false, false,
                false, isCaseClass
            ));
        }
    }

    private void extractObjects(String content, String[] lines, String relativePath,
                               String packageName, List<Symbol> symbols) {
        Matcher m = OBJECT_PATTERN.matcher(content);
        while (m.find()) {
            String objectName = m.group(1);
            String supertypes = m.group(2);

            String qualifiedName = packageName.isEmpty() ? objectName : packageName + "." + objectName;
            int line = findLineNumber(content, m.group(0));
            int modifiers = extractModifiers(m.group(0));

            // 解析继承信息
            String superClass = null;
            List<String> interfaces = new ArrayList<>();
            if (supertypes != null) {
                parseSupertypes(supertypes, superClass, interfaces);
            }

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.CLASS, objectName, qualifiedName,
                buildObjectSignature(objectName, supertypes),
                null, null, modifiers, null,
                superClass, interfaces.isEmpty() ? null : interfaces,
                false, true, false, false,
                false, false
            ));
        }
    }

    private void extractTraits(String content, String[] lines, String relativePath,
                              String packageName, List<Symbol> symbols) {
        Matcher m = TRAIT_PATTERN.matcher(content);
        while (m.find()) {
            String traitName = m.group(1);
            String supertypes = m.group(2);

            String qualifiedName = packageName.isEmpty() ? traitName : packageName + "." + traitName;
            int line = findLineNumber(content, m.group(0));
            int modifiers = extractModifiers(m.group(0));

            // 解析继承信息
            String superClass = null;
            List<String> interfaces = new ArrayList<>();
            if (supertypes != null) {
                parseSupertypes(supertypes, superClass, interfaces);
            }

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.CLASS, traitName, qualifiedName,
                buildTraitSignature(traitName, supertypes),
                null, null, modifiers, null,
                superClass, interfaces.isEmpty() ? null : interfaces,
                false, false, false, false,
                true, false
            ));
        }
    }

    private void extractFunctions(String content, String[] lines, String relativePath,
                                  String packageName, List<Symbol> symbols) {
        Matcher m = FUNCTION_PATTERN.matcher(content);
        while (m.find()) {
            String funcName = m.group(1);
            String params = m.group(2);
            String returnType = m.group(3);

            int line = findLineNumber(content, m.group(0));
            int modifiers = extractModifiers(m.group(0));

            String parentClass = findParentClass(content, m.start());
            String qualifiedName = parentClass != null
                ? packageName + "." + parentClass + "." + funcName
                : packageName + "." + funcName;

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.METHOD, funcName, qualifiedName,
                buildMethodSignature(funcName, params, returnType),
                returnType, parentClass, modifiers, null,
                null, null, false, false, false, false,
                false, false
            ));
        }
    }

    private void extractValues(String content, String[] lines, String relativePath,
                              String packageName, List<Symbol> symbols) {
        Matcher m = VAL_PATTERN.matcher(content);
        while (m.find()) {
            String valName = m.group(1);
            String valType = m.group(2);

            // 排除误匹配
            String before = content.substring(0, m.start());
            if (before.endsWith("def ") || before.endsWith("class ") || before.endsWith("object ") || before.endsWith("trait ")) {
                continue;
            }

            int line = findLineNumber(content, m.group(0));
            int modifiers = extractModifiers(m.group(0));

            String parentClass = findParentClass(content, m.start());
            String qualifiedName = parentClass != null
                ? packageName + "." + parentClass + "." + valName
                : packageName + "." + valName;

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.FIELD, valName, qualifiedName,
                valType != null ? valType + " " + valName : valName,
                valType, parentClass, modifiers, null,
                null, null, false, false, false, false,
                false, false
            ));
        }
    }

    private void extractAnnotations(String content, List<Annotation> annotations) {
        Pattern annPattern = Pattern.compile("@(\\w+)(?:\\(([^)]*)\\))?");
        Matcher m = annPattern.matcher(content);

        while (m.find()) {
            String annName = m.group(1);
            String params = m.group(2);
            Map<String, String> attributes = new LinkedHashMap<>();

            if (params != null && !params.isEmpty()) {
                attributes.put("value", params);
            }

            annotations.add(new Annotation(0, 0, annName, attributes));
        }
    }

    private int extractModifiers(String text) {
        int mods = 0;
        if (text.contains("public")) mods |= 0x0001;
        if (text.contains("private")) mods |= 0x0002;
        if (text.contains("protected")) mods |= 0x0004;
        if (text.contains("abstract")) mods |= 0x0400;
        if (text.contains("final")) mods |= 0x0010;
        if (text.contains("override")) mods |= 0x0040;
        if (text.contains("implicit")) mods |= 0x1000;
        if (text.contains("lazy")) mods |= 0x2000;
        return mods;
    }

    private void parseSupertypes(String signature, String superClass, List<String> interfaces) {
        if (signature == null) return;

        String[] types = signature.split(",");
        for (String type : types) {
            String trimmed = type.trim();
            if (trimmed.isEmpty()) continue;

            // 移除泛型参数
            int genericsIdx = trimmed.indexOf('[');
            if (genericsIdx > 0) {
                trimmed = trimmed.substring(0, genericsIdx);
            }

            // 移除构造函数调用
            int parenIdx = trimmed.indexOf('(');
            if (parenIdx > 0) {
                trimmed = trimmed.substring(0, parenIdx);
            }

            if (!trimmed.isEmpty()) {
                interfaces.add(trimmed);
            }
        }
    }

    private int findLineNumber(String content, String pattern) {
        int idx = content.indexOf(pattern);
        if (idx < 0) return 1;
        int line = 1;
        for (int i = 0; i < idx; i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    private String findParentClass(String content, int position) {
        int searchPos = Math.min(position, content.length());
        String searchArea = content.substring(0, searchPos);

        // 查找最后一个 class 或 object 或 trait 声明
        Matcher m = Pattern.compile("(?:class|object|trait)\\s+(\\w+)").matcher(searchArea);
        String lastClass = null;
        while (m.find()) {
            lastClass = m.group(1);
        }
        return lastClass;
    }

    private String buildClassSignature(String name, boolean isCaseClass, String supertypes) {
        StringBuilder sb = new StringBuilder();
        if (isCaseClass) sb.append("case class ");
        else sb.append("class ");
        sb.append(name);
        if (supertypes != null && !supertypes.isEmpty()) {
            sb.append(" extends ").append(supertypes);
        }
        return sb.toString();
    }

    private String buildObjectSignature(String name, String supertypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("object ").append(name);
        if (supertypes != null && !supertypes.isEmpty()) {
            sb.append(" extends ").append(supertypes);
        }
        return sb.toString();
    }

    private String buildTraitSignature(String name, String supertypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("trait ").append(name);
        if (supertypes != null && !supertypes.isEmpty()) {
            sb.append(" extends ").append(supertypes);
        }
        return sb.toString();
    }

    private String buildMethodSignature(String name, String params, String returnType) {
        StringBuilder sb = new StringBuilder();
        sb.append("def ").append(name).append("(");
        if (params != null && !params.isEmpty()) {
            sb.append(params);
        }
        sb.append(")");
        if (returnType != null && !returnType.isEmpty()) {
            sb.append(": ").append(returnType);
        }
        return sb.toString();
    }
}
