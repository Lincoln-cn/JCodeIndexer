package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TypeScript 文件解析器
 * 使用正则表达式提取类、函数、接口等符号
 */
public class TypeScriptParserAdapter {

    private static final Logger log = LoggerFactory.getLogger(TypeScriptParserAdapter.class);
    private final Config config;

    // 匹配类声明
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "(?:export\\s+|abstract\\s+|readonly\\s+|public\\s+|private\\s+|protected\\s+|static\\s+)*" +
        "(class|interface|type|enum)\\s+(\\w+)(?:<[^>]*>)?" +
        "(?:\\s+extends\\s+([^{]+))?" +
        "(?:\\s+implements\\s+([^{]+))?",
        Pattern.MULTILINE
    );

    // 匹配函数声明
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "(?:export\\s+|async\\s+|public\\s+|private\\s+|protected\\s+|static\\s+|readonly\\s+)*" +
        "(?:function)\\s+(\\w+)\\s*(?:<[^>]*>)?\\s*\\(([^)]*)\\)" +
        "(?:\\s*:\\s*(\\S+))?",
        Pattern.MULTILINE
    );

    // 匹配箭头函数和常量函数
    private static final Pattern ARROW_FUNCTION_PATTERN = Pattern.compile(
        "(?:export\\s+|async\\s+|const\\s+|let\\s+|var\\s+)*(\\w+)\\s*=\\s*(?:\\([^)]*\\)|[^=])\\s*=>",
        Pattern.MULTILINE
    );

    // 匹配方法声明（类内部）
    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "(?:public\\s+|private\\s+|protected\\s+|static\\s+|async\\s+|readonly\\s+)*" +
        "(\\w+)\\s*(?:<[^>]*>)?\\s*\\(([^)]*)\\)" +
        "(?:\\s*:\\s*(\\S+))?",
        Pattern.MULTILINE
    );

    // 匹配属性声明
    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
        "(?:public\\s+|private\\s+|protected\\s+|static\\s+|readonly\\s+|readonly\\s+)*" +
        "(\\w+)\\s*(?::\\s*(\\S+))?",
        Pattern.MULTILINE
    );

    // 匹配 import 声明
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "^import\\s+(?:\\{([^}]+)\\}|([\\w.*]+))\\s+from\\s+['\"]([^'\"]+)['\"]",
        Pattern.MULTILINE
    );

    // 匹配 export 声明
    private static final Pattern EXPORT_PATTERN = Pattern.compile(
        "^export\\s+(?:default\\s+|class\\s+|interface\\s+|function\\s+|const\\s+|let\\s+|type\\s+|enum\\s+)*(\\w+)",
        Pattern.MULTILINE
    );

    // 匹配函数调用
    private static final Pattern CALL_PATTERN = Pattern.compile(
        "(\\w+)\\s*\\(",
        Pattern.MULTILINE
    );

    public TypeScriptParserAdapter(Config config) {
        this.config = config;
    }

    /**
     * 判断是否为 TypeScript 文件
     */
    public static boolean isTypeScriptFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".mts") || lower.endsWith(".cts");
    }

    /**
     * 解析单个 TypeScript 文件
     */
    public ParseResult parse(String relativePath, Path filePath) {
        List<Symbol> symbols = new ArrayList<>();
        List<Reference> references = new ArrayList<>();
        List<Call> calls = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<ApiRoute> apiRoutes = new ArrayList<>();
        List<BeanDependency> beanDependencies = new ArrayList<>();
        List<TestMapping> testMappings = new ArrayList<>();
        List<AnnotationRef> annotationRefs = new ArrayList<>();
        List<BeanSource> beanSources = new ArrayList<>();

        try {
            String content = Files.readString(filePath);
            String[] lines = content.split("\n");

            // 提取 import 引用
            extractImports(content, relativePath, references);

            // 提取类/接口/类型声明
            extractClasses(content, lines, relativePath, symbols);

            // 提取函数声明
            extractFunctions(content, lines, relativePath, symbols);

            // 提取函数调用
            extractCalls(content, lines, relativePath, calls);

        } catch (IOException e) {
            log.warn("读取 TypeScript 文件失败: {}", relativePath, e);
            errors.add(relativePath + ": " + e.getMessage());
        } catch (Exception e) {
            log.warn("解析 TypeScript 文件失败: {}", relativePath, e);
            errors.add(relativePath + ": " + e.getMessage());
        }

        return new ParseResult(symbols, references, calls, annotations, errors,
            apiRoutes, beanDependencies, testMappings, annotationRefs, beanSources);
    }

    private void extractImports(String content, String relativePath, List<Reference> references) {
        Matcher m = IMPORT_PATTERN.matcher(content);
        while (m.find()) {
            String namedImports = m.group(1);
            String defaultImport = m.group(2);
            String modulePath = m.group(3);
            int line = findLineNumber(content, m.group(0));

            if (namedImports != null) {
                // import { A, B } from 'module'
                for (String importName : namedImports.split(",")) {
                    String trimmed = importName.trim();
                    if (!trimmed.isEmpty()) {
                        references.add(new Reference(0, 0, relativePath, line, "import " + trimmed));
                    }
                }
            } else if (defaultImport != null) {
                // import Default from 'module'
                references.add(new Reference(0, 0, relativePath, line, "import " + defaultImport));
            }
        }
    }

    private void extractClasses(String content, String[] lines, String relativePath, List<Symbol> symbols) {
        Matcher m = CLASS_PATTERN.matcher(content);
        while (m.find()) {
            String type = m.group(1); // class, interface, type, enum
            String name = m.group(2);
            String superClass = m.group(3);
            String interfaces = m.group(4);

            int line = findLineNumber(content, m.group(0));
            int modifiers = extractModifiers(m.group(0));

            // 构建限定名（简化版，没有包名）
            String qualifiedName = name;

            Symbol.SymbolKind kind = switch (type) {
                case "class" -> Symbol.SymbolKind.CLASS;
                case "interface" -> Symbol.SymbolKind.CLASS;
                case "type" -> Symbol.SymbolKind.CLASS;
                case "enum" -> Symbol.SymbolKind.CLASS;
                default -> Symbol.SymbolKind.CLASS;
            };

            symbols.add(new Symbol(
                0, relativePath, line, line,
                kind, name, qualifiedName,
                buildClassSignature(type, name, superClass, interfaces),
                null, null, modifiers, null,
                superClass, null,
                false, false, false, false, false, false
            ));
        }
    }

    private void extractFunctions(String content, String[] lines, String relativePath, List<Symbol> symbols) {
        // 匹配普通函数声明
        Matcher m = FUNCTION_PATTERN.matcher(content);
        while (m.find()) {
            String name = m.group(1);
            String params = m.group(2);
            String returnType = m.group(3);

            int line = findLineNumber(content, m.group(0));
            int modifiers = extractModifiers(m.group(0));

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.METHOD, name, name,
                buildMethodSignature(name, params, returnType),
                returnType, null, modifiers, null,
                null, null, false, false, false, false
            ));
        }

        // 匹配箭头函数
        Matcher arrowMatcher = ARROW_FUNCTION_PATTERN.matcher(content);
        while (arrowMatcher.find()) {
            String name = arrowMatcher.group(1);

            // 检查是否已经作为普通函数添加
            String finalName = name;
            if (symbols.stream().anyMatch(s -> s.name().equals(finalName))) {
                continue;
            }

            int line = findLineNumber(content, arrowMatcher.group(0));
            int modifiers = extractModifiers(arrowMatcher.group(0));

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.METHOD, name, name,
                name + "()",
                null, null, modifiers, null,
                null, null, false, false, false, false
            ));
        }
    }

    private void extractCalls(String content, String[] lines, String relativePath, List<Call> calls) {
        Matcher m = CALL_PATTERN.matcher(content);
        while (m.find()) {
            String funcName = m.group(1);
            int line = findLineNumber(content, m.group(0));

            // 过滤掉关键字
            if (isKeyword(funcName)) continue;

            calls.add(new Call(
                0, "", relativePath, line,
                funcName, null
            ));
        }
    }

    private String buildClassSignature(String type, String name, String superClass, String interfaces) {
        StringBuilder sb = new StringBuilder();
        sb.append(type).append(" ").append(name);
        if (superClass != null && !superClass.isEmpty()) {
            sb.append(" extends ").append(superClass.trim());
        }
        if (interfaces != null && !interfaces.isEmpty()) {
            sb.append(" implements ").append(interfaces.trim());
        }
        return sb.toString();
    }

    private String buildMethodSignature(String name, String params, String returnType) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("(");
        if (params != null && !params.isEmpty()) {
            sb.append(params.trim());
        }
        sb.append(")");
        if (returnType != null && !returnType.isEmpty()) {
            sb.append(": ").append(returnType.trim());
        }
        return sb.toString();
    }

    private int extractModifiers(String text) {
        int mods = 0;
        if (text.contains("public")) mods |= 0x0001;
        if (text.contains("private")) mods |= 0x0002;
        if (text.contains("protected")) mods |= 0x0004;
        if (text.contains("static")) mods |= 0x0008;
        if (text.contains("abstract")) mods |= 0x0400;
        if (text.contains("readonly")) mods |= 0x0010;
        if (text.contains("async")) mods |= 0x1000;
        return mods;
    }

    private int findLineNumber(String content, String pattern) {
        int index = content.indexOf(pattern);
        if (index == -1) return 0;
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    private boolean isKeyword(String name) {
        return "if".equals(name) || "else".equals(name) || "for".equals(name) ||
               "while".equals(name) || "do".equals(name) || "switch".equals(name) ||
               "case".equals(name) || "break".equals(name) || "continue".equals(name) ||
               "return".equals(name) || "throw".equals(name) || "try".equals(name) ||
               "catch".equals(name) || "finally".equals(name) || "new".equals(name) ||
               "delete".equals(name) || "typeof".equals(name) || "instanceof".equals(name) ||
               "void".equals(name) || "null".equals(name) || "undefined".equals(name) ||
               "true".equals(name) || "false".equals(name) || "this".equals(name) ||
               "class".equals(name) || "interface".equals(name) || "type".equals(name) ||
               "enum".equals(name) || "extends".equals(name) || "implements".equals(name) ||
               "import".equals(name) || "export".equals(name) || "default".equals(name) ||
               "from".equals(name) || "as".equals(name) || "async".equals(name) ||
               "await".equals(name) || "yield".equals(name);
    }
}
