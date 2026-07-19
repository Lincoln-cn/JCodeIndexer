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
 * Python 文件解析器
 * 使用正则表达式提取类、函数、装饰器等符号
 */
public class PythonParserAdapter {

    private static final Logger log = LoggerFactory.getLogger(PythonParserAdapter.class);
    private final Config config;

    // 匹配类声明
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "^class\\s+(\\w+)(?:\\s*\\(([^)]*)\\))?:",
        Pattern.MULTILINE
    );

    // 匹配函数声明
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "^(?:async\\s+)?def\\s+(\\w+)\\s*\\(([^)]*)\\)(?:\\s*->\\s*(\\S+))?:",
        Pattern.MULTILINE
    );

    // 匹配装饰器
    private static final Pattern DECORATOR_PATTERN = Pattern.compile(
        "^@(\\w+)(?:\\(([^)]*)\\))?",
        Pattern.MULTILINE
    );

    // 匹配 import 语句
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "^import\\s+([\\w.,\\s]+)|^from\\s+([\\w.]+)\\s+import\\s+([\\w.,\\s*]+)",
        Pattern.MULTILINE
    );

    // 匹配变量赋值
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
        "^(\\w+)\\s*(?::\\s*(\\S+))?\\s*=",
        Pattern.MULTILINE
    );

    public PythonParserAdapter(Config config) {
        this.config = config;
    }

    /**
     * 判断是否为 Python 文件
     */
    public static boolean isPythonFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".py") || lower.endsWith(".pyw");
    }

    /**
     * 解析单个 Python 文件
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

            // 提取类声明
            extractClasses(content, lines, relativePath, symbols, annotations, annotationRefs);

            // 提取函数声明
            extractFunctions(content, lines, relativePath, symbols, annotations, annotationRefs);

            // 提取变量
            extractVariables(content, lines, relativePath, symbols);

            // 提取函数调用
            extractCalls(content, lines, relativePath, calls);

        } catch (IOException e) {
            log.warn("读取 Python 文件失败: {}", relativePath, e);
            errors.add(relativePath + ": " + e.getMessage());
        } catch (Exception e) {
            log.warn("解析 Python 文件失败: {}", relativePath, e);
            errors.add(relativePath + ": " + e.getMessage());
        }

        return new ParseResult(symbols, references, calls, annotations, errors,
            apiRoutes, beanDependencies, testMappings, annotationRefs, beanSources);
    }

    private void extractImports(String content, String relativePath, List<Reference> references) {
        Matcher m = IMPORT_PATTERN.matcher(content);
        while (m.find()) {
            String imports = m.group(1);
            String fromModule = m.group(2);
            String namedImports = m.group(3);
            int line = findLineNumber(content, m.group(0));

            if (imports != null) {
                // import module1, module2
                for (String importName : imports.split(",")) {
                    String trimmed = importName.trim();
                    if (!trimmed.isEmpty()) {
                        references.add(new Reference(0, 0, relativePath, line, "import " + trimmed));
                    }
                }
            } else if (fromModule != null && namedImports != null) {
                // from module import name1, name2
                for (String importName : namedImports.split(",")) {
                    String trimmed = importName.trim();
                    if (!trimmed.isEmpty() && !trimmed.equals("*")) {
                        references.add(new Reference(0, 0, relativePath, line, "import " + trimmed));
                    }
                }
            }
        }
    }

    private void extractClasses(String content, String[] lines, String relativePath,
                               List<Symbol> symbols, List<Annotation> annotations,
                               List<AnnotationRef> annotationRefs) {
        Matcher m = CLASS_PATTERN.matcher(content);
        while (m.find()) {
            String name = m.group(1);
            String bases = m.group(2);

            int line = findLineNumber(content, m.group(0));

            // 查找类前面的装饰器
            String beforeClass = content.substring(0, m.start());
            Matcher decoratorMatcher = DECORATOR_PATTERN.matcher(beforeClass);
            String lastDecorator = null;
            while (decoratorMatcher.find()) {
                lastDecorator = decoratorMatcher.group(1);
            }

            // 如果有装饰器，添加注解
            if (lastDecorator != null) {
                int annotationIndex = annotations.size();
                annotations.add(new Annotation(0, 0, lastDecorator, null));
                annotationRefs.add(new AnnotationRef(annotationIndex, name, Symbol.SymbolKind.CLASS));
            }

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.CLASS, name, name,
                buildClassSignature(name, bases),
                null, null, 0x0001, null, // public
                bases, null,
                false, false, false, false, false, false
            ));
        }
    }

    private void extractFunctions(String content, String[] lines, String relativePath,
                                 List<Symbol> symbols, List<Annotation> annotations,
                                 List<AnnotationRef> annotationRefs) {
        Matcher m = FUNCTION_PATTERN.matcher(content);
        while (m.find()) {
            String name = m.group(1);
            String params = m.group(2);
            String returnType = m.group(3);

            int line = findLineNumber(content, m.group(0));

            // 查找函数前面的装饰器
            String beforeFunc = content.substring(0, m.start());
            Matcher decoratorMatcher = DECORATOR_PATTERN.matcher(beforeFunc);
            String lastDecorator = null;
            while (decoratorMatcher.find()) {
                lastDecorator = decoratorMatcher.group(1);
            }

            // 如果有装饰器，添加注解
            if (lastDecorator != null) {
                int annotationIndex = annotations.size();
                annotations.add(new Annotation(0, 0, lastDecorator, null));
                annotationRefs.add(new AnnotationRef(annotationIndex, name, Symbol.SymbolKind.METHOD));
            }

            // 判断是否为静态方法
            boolean isStatic = beforeFunc.contains("    @staticmethod") ||
                               (lines.length > line - 1 && lines[line - 1].contains("@staticmethod"));

            int modifiers = 0x0001; // public
            if (isStatic) modifiers |= 0x0008; // static

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.METHOD, name, name,
                buildMethodSignature(name, params, returnType),
                returnType, null, modifiers, null,
                null, null, false, false, false, false
            ));
        }
    }

    private void extractVariables(String content, String[] lines, String relativePath, List<Symbol> symbols) {
        Matcher m = VARIABLE_PATTERN.matcher(content);
        while (m.find()) {
            String name = m.group(1);
            String type = m.group(2);

            // 过滤掉关键字和函数参数
            if (isKeyword(name) || name.startsWith("_")) continue;

            int line = findLineNumber(content, m.group(0));

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.FIELD, name, name,
                type != null ? type + " " + name : name,
                type, null, 0x0001, null, // public
                null, null, false, false, false, false
            ));
        }
    }

    private void extractCalls(String content, String[] lines, String relativePath, List<Call> calls) {
        // 简单的函数调用匹配
        Pattern callPattern = Pattern.compile("(\\w+)\\s*\\(");
        Matcher m = callPattern.matcher(content);
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

    private String buildClassSignature(String name, String bases) {
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(name);
        if (bases != null && !bases.isEmpty()) {
            sb.append("(").append(bases.trim()).append(")");
        }
        return sb.toString();
    }

    private String buildMethodSignature(String name, String params, String returnType) {
        StringBuilder sb = new StringBuilder();
        sb.append("def ").append(name).append("(");
        if (params != null && !params.isEmpty()) {
            sb.append(params.trim());
        }
        sb.append(")");
        if (returnType != null && !returnType.isEmpty()) {
            sb.append(" -> ").append(returnType.trim());
        }
        return sb.toString();
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
        return "if".equals(name) || "else".equals(name) || "elif".equals(name) ||
               "for".equals(name) || "while".equals(name) || "break".equals(name) ||
               "continue".equals(name) || "return".equals(name) || "yield".equals(name) ||
               "try".equals(name) || "except".equals(name) || "finally".equals(name) ||
               "raise".equals(name) || "pass".equals(name) || "del".equals(name) ||
               "import".equals(name) || "from".equals(name) || "as".equals(name) ||
               "class".equals(name) || "def".equals(name) || "lambda".equals(name) ||
               "with".equals(name) || "assert".equals(name) || "global".equals(name) ||
               "nonlocal".equals(name) || "True".equals(name) || "False".equals(name) ||
               "None".equals(name) || "and".equals(name) || "or".equals(name) ||
               "not".equals(name) || "in".equals(name) || "is".equals(name);
    }
}
