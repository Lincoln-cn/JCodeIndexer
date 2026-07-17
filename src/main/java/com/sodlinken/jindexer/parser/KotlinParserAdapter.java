package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.Annotation;
import com.sodlinken.jindexer.model.ApiRoute;
import com.sodlinken.jindexer.model.BeanDependency;
import com.sodlinken.jindexer.model.Call;
import com.sodlinken.jindexer.model.Reference;
import com.sodlinken.jindexer.model.Symbol;
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
 * Kotlin 文件解析器
 * 使用正则表达式提取类、函数、属性等符号
 */
public class KotlinParserAdapter {

    private static final Logger log = LoggerFactory.getLogger(KotlinParserAdapter.class);
    private final Config config;

    // 匹配类声明: class, data class, object, sealed class, open class
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "(?:data\\s+|sealed\\s+|open\\s+|abstract\\s+|internal\\s+|private\\s+|protected\\s+)*" +
        "(class|object)\\s+(\\w+)(?:<[^>]*>)?" +
        "(?:\\s*:\\s*([^{]+))?",
        Pattern.MULTILINE
    );

    // 匹配函数声明
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "(?:override\\s+|open\\s+|abstract\\s+|private\\s+|protected\\s+|internal\\s+|suspend\\s+|inline\\s+|operator\\s+)*" +
        "fun\\s+(?:<[^>]*>\\s+)?(\\w+)\\s*\\(([^)]*)\\)" +
        "(?:\\s*:\\s*(\\S+))?",
        Pattern.MULTILINE
    );

    // 匹配属性声明
    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
        "(?:private\\s+|protected\\s+|internal\\s+|override\\s+|open\\s+|val|var)\\s+" +
        "(\\w+)\\s*(?::\\s*(\\S+))?",
        Pattern.MULTILINE
    );

    // 匹配 companion object
    private static final Pattern COMPANION_PATTERN = Pattern.compile(
        "companion\\s+object",
        Pattern.MULTILINE
    );

    // 匹配函数调用
    private static final Pattern CALL_PATTERN = Pattern.compile(
        "(\\w+)\\s*\\(",
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

    public KotlinParserAdapter(Config config) {
        this.config = config;
    }

    /**
     * 解析单个 Kotlin 文件
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

            // 提取函数声明
            extractFunctions(content, lines, relativePath, packageName, symbols);

            // 提取属性声明
            extractProperties(content, lines, relativePath, packageName, symbols);

            // 提取函数调用
            extractCalls(content, lines, relativePath, calls);

            // 提取注解（简单正则匹配）
            extractAnnotations(content, symbols, annotations);

            // 提取 Spring 路由和 Bean 依赖
            List<ApiRoute> apiRoutes = extractApiRoutes(content, relativePath);
            List<BeanDependency> beanDependencies = extractBeanDependencies(content, relativePath);

            return new ParseResult(symbols, references, calls, annotations, errors,
                apiRoutes, beanDependencies, List.of(), List.of(), List.of());

        } catch (IOException e) {
            log.warn("读取 Kotlin 文件失败: {}", relativePath, e);
            errors.add(relativePath + ": " + e.getMessage());
        } catch (Exception e) {
            log.warn("解析 Kotlin 文件失败: {}", relativePath, e);
            errors.add(relativePath + ": " + e.getMessage());
        }

        return new ParseResult(symbols, references, calls, annotations, errors,
            List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * 判断是否为 Kotlin 文件
     */
    public static boolean isKotlinFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".kt") || lower.endsWith(".kts");
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
            String type = m.group(1); // class or object
            String className = m.group(2);
            String signature = m.group(3); // supertypes

            String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
            int line = findLineNumber(content, m.group(0));

            // 提取修饰符
            int modifiers = extractModifiers(m.group(0));

            // 检查 Kotlin 特性
            String beforeClass = content.substring(0, m.start());
            boolean isDataClass = beforeClass.contains("data class") || m.group(0).contains("data class");
            boolean isObject = "object".equals(type);
            boolean isSealed = beforeClass.contains("sealed class") || m.group(0).contains("sealed class");
            boolean isCompanion = false;

            // 查找 companion object
            if (isObject) {
                String classContent = extractClassContent(content, m.start());
                isCompanion = classContent.contains("companion object");
            }

            // 解析继承信息
            String superClass = null;
            List<String> interfaces = new ArrayList<>();
            if (signature != null) {
                parseSupertypes(signature, superClass, interfaces);
            }

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.CLASS, className, qualifiedName,
                buildClassSignature(className, isDataClass, isObject, isSealed, isCompanion, signature),
                null, null, modifiers, null,
                superClass, interfaces.isEmpty() ? null : interfaces,
                isDataClass, isObject, isSealed, isCompanion
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

            // 查找父类（通过查找最近的类声明）
            String parentClass = findParentClass(content, m.start());

            String qualifiedName = parentClass != null
                ? packageName + "." + parentClass + "." + funcName
                : packageName + "." + funcName;

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.METHOD, funcName, qualifiedName,
                buildMethodSignature(funcName, params, returnType),
                returnType, parentClass, modifiers, null,
                null, null, false, false, false, false
            ));
        }
    }

    private void extractProperties(String content, String[] lines, String relativePath,
                                   String packageName, List<Symbol> symbols) {
        Matcher m = PROPERTY_PATTERN.matcher(content);
        while (m.find()) {
            String propName = m.group(1);
            String propType = m.group(2);

            // 排除误匹配（函数参数、类声明等）
            String before = content.substring(0, m.start());
            if (before.endsWith("fun ") || before.endsWith("class ") || before.endsWith("object ")) {
                continue;
            }

            int line = findLineNumber(content, m.group(0));
            int modifiers = extractModifiers(m.group(0));

            String parentClass = findParentClass(content, m.start());
            String qualifiedName = parentClass != null
                ? packageName + "." + parentClass + "." + propName
                : packageName + "." + propName;

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.FIELD, propName, qualifiedName,
                propType != null ? propType + " " + propName : propName,
                propType, parentClass, modifiers, null,
                null, null, false, false, false, false
            ));
        }
    }

    private void extractCalls(String content, String[] lines, String relativePath, List<Call> calls) {
        Matcher m = CALL_PATTERN.matcher(content);
        while (m.find()) {
            String funcName = m.group(1);
            int line = findLineNumber(content, m.group(0));

            // 排除关键字
            if (isKeyword(funcName)) continue;

            calls.add(new Call(
                0, "", relativePath, line,
                funcName, null
            ));
        }
    }

    private int extractModifiers(String text) {
        int mods = 0;
        if (text.contains("public")) mods |= 0x0001;
        if (text.contains("private")) mods |= 0x0002;
        if (text.contains("protected")) mods |= 0x0004;
        if (text.contains("internal")) mods |= 0x0008;
        if (text.contains("static")) mods |= 0x0010;
        if (text.contains("abstract")) mods |= 0x0400;
        if (text.contains("final")) mods |= 0x0010;
        if (text.contains("open")) mods |= 0x0001; // open ≈ public for access
        if (text.contains("override")) mods |= 0x0040;
        if (text.contains("suspend")) mods |= 0x1000;
        return mods;
    }

    private void parseSupertypes(String signature, String superClass, List<String> interfaces) {
        if (signature == null) return;

        String[] types = signature.split(",");
        for (String type : types) {
            String trimmed = type.trim();
            if (trimmed.isEmpty()) continue;

            // 移除泛型参数
            int genericsIdx = trimmed.indexOf('<');
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

    private String extractClassContent(String content, int startPos) {
        int braceCount = 0;
        boolean found = false;
        for (int i = startPos; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                braceCount++;
                found = true;
            } else if (c == '}') {
                braceCount--;
                if (found && braceCount == 0) {
                    return content.substring(startPos, i + 1);
                }
            }
        }
        return content.substring(startPos, Math.min(startPos + 5000, content.length()));
    }

    private String findParentClass(String content, int position) {
        // 向前查找最近的类声明
        int searchPos = Math.min(position, content.length());
        String searchArea = content.substring(0, searchPos);

        // 查找最后一个 class 或 object 声明
        Matcher m = Pattern.compile("(?:class|object)\\s+(\\w+)").matcher(searchArea);
        String lastClass = null;
        while (m.find()) {
            lastClass = m.group(1);
        }
        return lastClass;
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

    private String buildClassSignature(String name, boolean isDataClass, boolean isObject,
                                       boolean isSealed, boolean isCompanion, String supertypes) {
        StringBuilder sb = new StringBuilder();
        if (isDataClass) sb.append("data ");
        if (isSealed) sb.append("sealed ");
        if (isCompanion) sb.append("companion ");
        if (isObject) sb.append("object ");
        else sb.append("class ");
        sb.append(name);
        if (supertypes != null && !supertypes.isEmpty()) {
            sb.append(" : ").append(supertypes);
        }
        return sb.toString();
    }

    private String buildMethodSignature(String name, String params, String returnType) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("(");
        if (params != null && !params.isEmpty()) {
            sb.append(params);
        }
        sb.append(")");
        if (returnType != null && !returnType.isEmpty()) {
            sb.append(": ").append(returnType);
        }
        return sb.toString();
    }

    private boolean isKeyword(String word) {
        return switch (word) {
            case "val", "var", "fun", "class", "object", "interface", "return",
                 "if", "else", "when", "for", "while", "do", "try", "catch",
                 "finally", "throw", "is", "in", "as", "by", "companion",
                 "override", "open", "abstract", "data", "sealed", "suspend" -> true;
            default -> false;
        };
    }

    /**
     * 提取 Kotlin Spring API 路由
     */
    private List<ApiRoute> extractApiRoutes(String content, String relativePath) {
        List<ApiRoute> routes = new ArrayList<>();

        // 类级别 @RequestMapping
        String classBasePath = "";
        Matcher classMatcher = Pattern.compile(
            "@RequestMapping\\s*\\(?(?:.*?(?:value|path)\\s*=\\s*)?\"([^\"]+)\".*?\\)?"
        ).matcher(content);
        if (classMatcher.find()) {
            classBasePath = classMatcher.group(1);
        }

        // 方法级别路由注解
        String[] httpMappings = {"GetMapping:GET", "PostMapping:POST", "PutMapping:PUT",
                                "DeleteMapping:DELETE", "PatchMapping:PATCH"};

        for (String mapping : httpMappings) {
            String[] parts = mapping.split(":");
            String annotationName = parts[0];
            String httpMethod = parts[1];

            Pattern methodPattern = Pattern.compile(
                "@(" + annotationName + ")\\s*\\(?(?:.*?(?:value|path)\\s*=\\s*)?\"([^\"]+)\".*?\\)?"
            );
            Matcher methodMatcher = methodPattern.matcher(content);
            while (methodMatcher.find()) {
                String methodPath = methodMatcher.group(2);
                String fullPath = classBasePath + methodPath;
                if (fullPath.isEmpty()) fullPath = "/";
                int lineNum = findLineNumber(content, methodMatcher.group(0));
                routes.add(new ApiRoute(0, 0, httpMethod, fullPath, classBasePath, methodPath, relativePath, lineNum));
            }
        }
        return routes;
    }

    /**
     * 提取 Kotlin Bean 依赖
     */
    private List<BeanDependency> extractBeanDependencies(String content, String relativePath) {
        List<BeanDependency> deps = new ArrayList<>();

        // 字段注入: @Autowired private val service: UserService
        Pattern fieldPattern = Pattern.compile(
            "@(Autowired|Inject|Resource)\\s+(?:private\\s+)?(?:val|var)\\s+(\\w+)\\s*:\\s*(\\w+)"
        );
        Matcher fieldMatcher = fieldPattern.matcher(content);
        while (fieldMatcher.find()) {
            int lineNum = findLineNumber(content, fieldMatcher.group(0));
            deps.add(new BeanDependency(0, 0, null, fieldMatcher.group(3),
                "FIELD", fieldMatcher.group(2), relativePath, lineNum));
        }

        // 构造函数注入
        Pattern ctorPattern = Pattern.compile(
            "(?:@(?:Autowired|Inject)\\s+)?constructor\\s*\\(([^)]+)\\)"
        );
        Matcher ctorMatcher = ctorPattern.matcher(content);
        if (ctorMatcher.find()) {
            String params = ctorMatcher.group(1);
            for (String param : params.split(",")) {
                param = param.trim();
                Matcher paramMatcher = Pattern.compile("(?:val|var)\\s+(\\w+)\\s*:\\s*(\\w+)").matcher(param);
                if (paramMatcher.find()) {
                    int lineNum = findLineNumber(content, ctorMatcher.group(0));
                    deps.add(new BeanDependency(0, 0, null, paramMatcher.group(2),
                        "CONSTRUCTOR", paramMatcher.group(1), relativePath, lineNum));
                }
            }
        }
        return deps;
    }

    /**
     * 提取 Kotlin 注解（简单正则匹配）
     */
    private void extractAnnotations(String content, List<Symbol> symbols, List<Annotation> annotations) {
        // 匹配 @AnnotationName 或 @AnnotationName(params)
        Pattern annPattern = Pattern.compile("@(\\w+)(?:\\(([^)]*)\\))?");
        Matcher m = annPattern.matcher(content);

        while (m.find()) {
            String annName = m.group(1);
            String params = m.group(2);
            Map<String, String> attributes = new LinkedHashMap<>();

            if (params != null && !params.isEmpty()) {
                attributes.put("value", params);
            }

            // 使用空 symbolId 占位（实际 ID 在存储时分配）
            annotations.add(new Annotation(0, 0, annName, attributes));
        }
    }
}
