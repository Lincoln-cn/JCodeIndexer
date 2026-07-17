package com.sodlinken.jindexer.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.Annotation;
import com.sodlinken.jindexer.model.ApiRoute;
import com.sodlinken.jindexer.model.BeanDependency;
import com.sodlinken.jindexer.model.BeanSource;
import com.sodlinken.jindexer.model.Call;
import com.sodlinken.jindexer.model.Reference;
import com.sodlinken.jindexer.model.Symbol;
import com.sodlinken.jindexer.model.TestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * JavaParser 适配器，解析 Java 文件提取符号/引用/调用关系
 */
public class JavaParserAdapter {

    private static final Logger log = LoggerFactory.getLogger(JavaParserAdapter.class);
    private final Config config;

    private static final JavaParser PARSER;

    static {
        ParserConfiguration config = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        PARSER = new JavaParser(config);
    }

    public JavaParserAdapter(Config config) {
        this.config = config;
    }

    /**
     * 解析单个 Java 文件
     */
    public ParseResult parse(String relativePath, Path filePath) {
        List<Symbol> symbols = new ArrayList<>();
        List<Reference> references = new ArrayList<>();
        List<Call> calls = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<ApiRoute> apiRoutes = new ArrayList<>();
        List<BeanDependency> beanDependencies = new ArrayList<>();
        List<BeanSource> beanSources = new ArrayList<>();

        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = PARSER.parse(content)
                .getResult()
                .orElseThrow(() -> new RuntimeException("解析失败: " + relativePath));

            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            // 提取类声明
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                String className = clazz.getNameAsString();
                String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

                // 提取继承信息
                String superClass = clazz.getExtendedTypes().isEmpty()
                    ? null
                    : clazz.getExtendedTypes().get(0).getNameAsString();
                List<String> interfaces = clazz.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .toList();

                // 类符号
                symbols.add(new Symbol(
                    0, relativePath,
                    clazz.getBegin().map(p -> p.line).orElse(0),
                    clazz.getEnd().map(p -> p.line).orElse(0),
                    Symbol.SymbolKind.CLASS,
                    className, qualifiedName,
                    buildClassSignature(clazz),
                    null, null,
                    extractModifiers(clazz.getModifiers()),
                    config.isExtractJavadoc() ? extractJavadoc(clazz) : null,
                    superClass,
                    interfaces.isEmpty() ? null : interfaces
                ));

                // 提取类注解
                extractAnnotations(clazz.getAnnotations(), qualifiedName, annotations);

                // 提取 API 路由
                apiRoutes.addAll(extractApiRoutes(clazz, relativePath));

                // 提取 Bean 依赖
                beanDependencies.addAll(extractBeanDependencies(clazz, relativePath));

                // 提取 Bean 定义来源
                beanSources.addAll(extractBeanSources(clazz, relativePath));

                // 方法符号
                clazz.getMethods().forEach(method -> {
                    String methodName = method.getNameAsString();
                    String methodQualified = qualifiedName + "." + methodName;
                    String signature = buildMethodSignature(method);

                    symbols.add(new Symbol(
                        0, relativePath,
                        method.getBegin().map(p -> p.line).orElse(0),
                        method.getEnd().map(p -> p.line).orElse(0),
                        Symbol.SymbolKind.METHOD,
                        methodName, methodQualified,
                        signature,
                        method.getTypeAsString(),
                        className,
                        extractModifiers(method.getModifiers()),
                        config.isExtractJavadoc() ? extractJavadoc(method) : null
                    ));

                    // 提取方法注解
                    extractAnnotations(method.getAnnotations(), methodQualified, annotations);

                    // 提取方法内的调用关系
                    method.findAll(MethodCallExpr.class).forEach(callExpr -> {
                        String calleeName = resolveCallName(callExpr);
                        if (calleeName != null) {
                            calls.add(new Call(
                                0, methodQualified, relativePath,
                                callExpr.getBegin().map(p -> p.line).orElse(0),
                                calleeName, null
                            ));
                        }
                    });
                });

                // 字段符号
                clazz.getFields().forEach(field -> {
                    field.getVariables().forEach(var -> {
                        String fieldName = var.getNameAsString();
                        String fieldQualified = qualifiedName + "." + fieldName;

                        symbols.add(new Symbol(
                            0, relativePath,
                            field.getBegin().map(p -> p.line).orElse(0),
                            field.getEnd().map(p -> p.line).orElse(0),
                            Symbol.SymbolKind.FIELD,
                            fieldName, fieldQualified,
                            var.getTypeAsString() + " " + fieldName,
                            var.getTypeAsString(),
                            className,
                            extractModifiers(field.getModifiers()),
                            null
                        ));
                    });
                });
            });

            // 提取 Record 声明
            cu.findAll(RecordDeclaration.class).forEach(record -> {
                String recordName = record.getNameAsString();
                String qualifiedName = packageName.isEmpty() ? recordName : packageName + "." + recordName;

                // 提取 Record 实现的接口
                List<String> recordInterfaces = record.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .toList();

                // record 作为 CLASS 类型符号
                symbols.add(new Symbol(
                    0, relativePath,
                    record.getBegin().map(p -> p.line).orElse(0),
                    record.getEnd().map(p -> p.line).orElse(0),
                    Symbol.SymbolKind.CLASS,
                    recordName, qualifiedName,
                    buildRecordSignature(record),
                    null, null,
                    extractModifiers(record.getModifiers()),
                    config.isExtractJavadoc() ? extractJavadoc(record) : null,
                    null, // Record 没有父类
                    recordInterfaces.isEmpty() ? null : recordInterfaces
                ));

                // record 组件作为 FIELD 符号
                record.getParameters().forEach(param -> {
                    String paramName = param.getNameAsString();
                    String paramQualified = qualifiedName + "." + paramName;

                    symbols.add(new Symbol(
                        0, relativePath,
                        record.getBegin().map(p -> p.line).orElse(0),
                        record.getEnd().map(p -> p.line).orElse(0),
                        Symbol.SymbolKind.FIELD,
                        paramName, paramQualified,
                        param.getTypeAsString() + " " + paramName,
                        param.getTypeAsString(),
                        recordName,
                        0x0001 | 0x0010, // public final
                        null
                    ));
                });

                // record 内部的方法
                record.getMethods().forEach(method -> {
                    String methodName = method.getNameAsString();
                    String methodQualified = qualifiedName + "." + methodName;

                    symbols.add(new Symbol(
                        0, relativePath,
                        method.getBegin().map(p -> p.line).orElse(0),
                        method.getEnd().map(p -> p.line).orElse(0),
                        Symbol.SymbolKind.METHOD,
                        methodName, methodQualified,
                        buildMethodSignature(method),
                        method.getTypeAsString(),
                        recordName,
                        extractModifiers(method.getModifiers()),
                        config.isExtractJavadoc() ? extractJavadoc(method) : null
                    ));

                    method.findAll(MethodCallExpr.class).forEach(callExpr -> {
                        String calleeName = resolveCallName(callExpr);
                        if (calleeName != null) {
                            calls.add(new Call(
                                0, methodQualified, relativePath,
                                callExpr.getBegin().map(p -> p.line).orElse(0),
                                calleeName, null
                            ));
                        }
                    });
                });

                // record 内部的字段
                record.getFields().forEach(field -> {
                    field.getVariables().forEach(var -> {
                        String fieldName = var.getNameAsString();
                        String fieldQualified = qualifiedName + "." + fieldName;

                        symbols.add(new Symbol(
                            0, relativePath,
                            field.getBegin().map(p -> p.line).orElse(0),
                            field.getEnd().map(p -> p.line).orElse(0),
                            Symbol.SymbolKind.FIELD,
                            fieldName, fieldQualified,
                            var.getTypeAsString() + " " + fieldName,
                            var.getTypeAsString(),
                            recordName,
                            extractModifiers(field.getModifiers()),
                            null
                        ));
                    });
                });

                // record 构造器中的调用
                record.getConstructors().forEach(ctor -> {
                    ctor.findAll(MethodCallExpr.class).forEach(callExpr -> {
                        String calleeName = resolveCallName(callExpr);
                        if (calleeName != null) {
                            calls.add(new Call(
                                0, qualifiedName + "." + recordName, relativePath,
                                callExpr.getBegin().map(p -> p.line).orElse(0),
                                calleeName, null
                            ));
                        }
                    });
                });
            });

            // 提取类型引用（import 和类型使用）
            cu.getImports().forEach(importDecl -> {
                String importName = importDecl.getNameAsString();
                // 为每个 import 创建一个引用（symbol_id 在插入时由数据库分配，这里先用 0 占位）
                references.add(new Reference(
                    0, 0, relativePath,
                    importDecl.getBegin().map(p -> p.line).orElse(0),
                    "import " + importName
                ));
            });

            // 提取类型使用引用
            cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
                String typeName = type.getNameAsString();
                // 只记录非 java.lang 基础类型的引用
                if (!typeName.equals("String") && !typeName.equals("Object") &&
                    !typeName.equals("Integer") && !typeName.equals("Long") &&
                    !typeName.equals("Boolean") && !typeName.equals("Double") &&
                    !typeName.equals("Float") && !typeName.equals("List") &&
                    !typeName.equals("Map") && !typeName.equals("Set")) {
                    references.add(new Reference(
                        0, 0, relativePath,
                        type.getBegin().map(p -> p.line).orElse(0),
                        "type " + typeName
                    ));
                }
            });

        } catch (IOException e) {
            errors.add("读取文件失败: " + e.getMessage());
            log.warn("解析文件失败: {}", relativePath, e);
        } catch (Exception e) {
            errors.add("解析失败: " + e.getMessage());
            log.warn("解析文件失败: {}", relativePath, e);
        }

        // 提取测试映射
        List<TestMapping> testMappings = extractTestMappings(relativePath, symbols);

        return new ParseResult(symbols, references, calls, annotations, errors,
            apiRoutes, beanDependencies, testMappings, List.of(), beanSources);
    }

    private String buildClassSignature(ClassOrInterfaceDeclaration clazz) {
        StringBuilder sb = new StringBuilder();
        if (clazz.isInterface()) sb.append("interface ");
        else sb.append("class ");
        sb.append(clazz.getNameAsString());

        NodeList<ClassOrInterfaceType> extendedTypes = clazz.getExtendedTypes();
        if (!extendedTypes.isEmpty()) {
            sb.append(" extends ");
            for (int i = 0; i < extendedTypes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(extendedTypes.get(i).getNameAsString());
            }
        }

        NodeList<ClassOrInterfaceType> implementedTypes = clazz.getImplementedTypes();
        if (!implementedTypes.isEmpty()) {
            sb.append(" implements ");
            for (int i = 0; i < implementedTypes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(implementedTypes.get(i).getNameAsString());
            }
        }

        return sb.toString();
    }

    private String buildRecordSignature(RecordDeclaration record) {
        StringBuilder sb = new StringBuilder();
        sb.append("record ").append(record.getNameAsString());

        NodeList<Parameter> params = record.getParameters();
        sb.append("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getTypeAsString()).append(" ").append(params.get(i).getNameAsString());
        }
        sb.append(")");

        NodeList<ClassOrInterfaceType> implementedTypes = record.getImplementedTypes();
        if (!implementedTypes.isEmpty()) {
            sb.append(" implements ");
            for (int i = 0; i < implementedTypes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(implementedTypes.get(i).getNameAsString());
            }
        }

        return sb.toString();
    }

    private String buildMethodSignature(MethodDeclaration method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getTypeAsString()).append(" ");
        sb.append(method.getNameAsString()).append("(");

        NodeList<Parameter> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getTypeAsString()).append(" ");
            sb.append(params.get(i).getNameAsString());
        }
        sb.append(")");

        NodeList<com.github.javaparser.ast.type.TypeParameter> typeParams = method.getTypeParameters();
        if (!typeParams.isEmpty()) {
            sb.insert(method.getNameAsString().length(), typeParams.toString());
        }

        return sb.toString();
    }

    private int extractModifiers(NodeList<Modifier> modifiers) {
        int result = 0;
        for (Modifier mod : modifiers) {
            if (mod.getKeyword() == Modifier.Keyword.PUBLIC) result |= 0x0001;
            else if (mod.getKeyword() == Modifier.Keyword.PROTECTED) result |= 0x0004;
            else if (mod.getKeyword() == Modifier.Keyword.PRIVATE) result |= 0x0002;

            if (mod.getKeyword() == Modifier.Keyword.STATIC) result |= 0x0008;
            if (mod.getKeyword() == Modifier.Keyword.FINAL) result |= 0x0010;
            if (mod.getKeyword() == Modifier.Keyword.ABSTRACT) result |= 0x0400;
            if (mod.getKeyword() == Modifier.Keyword.SYNCHRONIZED) result |= 0x0020;
            if (mod.getKeyword() == Modifier.Keyword.NATIVE) result |= 0x0100;
            if (mod.getKeyword() == Modifier.Keyword.TRANSIENT) result |= 0x0080;
            if (mod.getKeyword() == Modifier.Keyword.VOLATILE) result |= 0x0040;
        }
        return result;
    }

    private String extractJavadoc(NodeWithJavadoc<?> node) {
        return node.getJavadocComment()
            .map(jd -> jd.getContent())
            .orElse(null);
    }

    private String resolveCallName(MethodCallExpr callExpr) {
        // 尝试解析调用的完整名称
        Optional<Expression> scope = callExpr.getScope();
        if (scope.isPresent()) {
            return scope.get().toString() + "." + callExpr.getNameAsString();
        }
        return callExpr.getNameAsString();
    }

    /**
     * 提取注解信息
     */
    private void extractAnnotations(NodeList<AnnotationExpr> annotationExprs,
                                    String qualifiedName,
                                    List<Annotation> annotations) {
        for (AnnotationExpr annExpr : annotationExprs) {
            String annName = annExpr.getNameAsString();
            Map<String, String> attributes = new LinkedHashMap<>();

            if (annExpr instanceof SingleMemberAnnotationExpr single) {
                // @RequestMapping("/api/users")
                attributes.put("value", single.getMemberValue().toString());
            } else if (annExpr instanceof NormalAnnotationExpr normal) {
                // @RequestMapping(path = "/api/users", method = GET)
                normal.getPairs().forEach(pair -> {
                    attributes.put(pair.getNameAsString(), pair.getValue().toString());
                });
            }
            // MarkerAnnotationExpr (@Override) has no attributes

            // 使用 qualifiedName 作为 symbolId 的占位符（实际 ID 在存储时分配）
            annotations.add(new Annotation(0, 0, annName, attributes));
        }
    }

    /**
     * 提取 API 路由注解（@RequestMapping, @GetMapping 等）
     */
    private List<ApiRoute> extractApiRoutes(ClassOrInterfaceDeclaration clazz, String relativePath) {
        List<ApiRoute> routes = new ArrayList<>();

        // 类级别 @RequestMapping → basePath
        String basePath = "";
        for (AnnotationExpr ann : clazz.getAnnotations()) {
            if (ann.getNameAsString().equals("RequestMapping")) {
                basePath = cleanString(extractPathAttribute(ann));
            }
        }

        // 方法级别注解 → route
        for (MethodDeclaration method : clazz.getMethods()) {
            for (AnnotationExpr ann : method.getAnnotations()) {
                String httpMethod = getHttpMethod(ann.getNameAsString());
                if (httpMethod == null) continue;

                String methodPath = "";
                if (ann instanceof NormalAnnotationExpr normal) {
                    // 从 method 属性提取（@RequestMapping(method = GET)）
                    if (ann.getNameAsString().equals("RequestMapping")) {
                        for (var pair : normal.getPairs()) {
                            if ("method".equals(pair.getNameAsString())) {
                                httpMethod = extractRequestMethod(pair.getValue());
                            }
                        }
                    }
                    methodPath = cleanString(extractPathAttribute(ann));
                } else if (ann instanceof SingleMemberAnnotationExpr single) {
                    methodPath = cleanString(single.getMemberValue().toString());
                }

                String fullPath = basePath + methodPath;
                if (fullPath.isEmpty()) fullPath = "/";

                routes.add(new ApiRoute(0, 0, httpMethod, fullPath, basePath, methodPath,
                    relativePath, method.getBegin().map(p -> p.line).orElse(0)));
            }
        }
        return routes;
    }

    private String getHttpMethod(String name) {
        return switch (name) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "DeleteMapping" -> "DELETE";
            case "PatchMapping" -> "PATCH";
            case "RequestMapping" -> "GET"; // 默认 GET，后面可能覆盖
            default -> null;
        };
    }

    private String extractPathAttribute(AnnotationExpr ann) {
        if (ann instanceof SingleMemberAnnotationExpr single) {
            return single.getMemberValue().toString();
        }
        if (ann instanceof NormalAnnotationExpr normal) {
            for (var pair : normal.getPairs()) {
                if ("value".equals(pair.getNameAsString()) || "path".equals(pair.getNameAsString())) {
                    return pair.getValue().toString();
                }
            }
        }
        return "";
    }

    private String extractRequestMethod(Expression expr) {
        String s = expr.toString();
        // 处理 RequestMethod.GET / GET 等格式
        if (s.contains(".")) {
            s = s.substring(s.lastIndexOf('.') + 1);
        }
        return switch (s.trim().toUpperCase()) {
            case "GET" -> "GET";
            case "POST" -> "POST";
            case "PUT" -> "PUT";
            case "DELETE" -> "DELETE";
            case "PATCH" -> "PATCH";
            default -> "GET";
        };
    }

    private String cleanString(String s) {
        if (s == null) return "";
        return s.replace("\"", "").replace("'", "").trim();
    }

    /**
     * 提取 Bean 依赖关系（@Autowired, @Inject）
     */
    private List<BeanDependency> extractBeanDependencies(ClassOrInterfaceDeclaration clazz, String relativePath) {
        List<BeanDependency> deps = new ArrayList<>();

        // 1. 字段注入
        for (FieldDeclaration field : clazz.getFields()) {
            if (hasAnyAnnotation(field, "Autowired", "Inject", "Resource")) {
                String type = field.getElementType().asString();
                String fieldName = field.getVariable(0).getNameAsString();
                deps.add(new BeanDependency(0, 0, null, type, "FIELD", fieldName,
                    relativePath, field.getBegin().map(p -> p.line).orElse(0)));
            }
        }

        // 2. 构造函数注入
        for (ConstructorDeclaration ctor : clazz.getConstructors()) {
            boolean autowired = hasAnyAnnotation(ctor, "Autowired", "Inject");
            if (autowired || ctor.getParameters().size() > 1) {
                for (Parameter param : ctor.getParameters()) {
                    String type = param.getType().asString();
                    deps.add(new BeanDependency(0, 0, null, type, "CONSTRUCTOR",
                        param.getNameAsString(), relativePath,
                        ctor.getBegin().map(p -> p.line).orElse(0)));
                }
            }
        }

        // 3. Setter 注入
        for (MethodDeclaration method : clazz.getMethods()) {
            if (method.getNameAsString().startsWith("set") && method.getParameters().size() == 1
                && hasAnyAnnotation(method, "Autowired", "Inject")) {
                String type = method.getParameter(0).getType().asString();
                deps.add(new BeanDependency(0, 0, null, type, "SETTER",
                    method.getNameAsString(), relativePath,
                    method.getBegin().map(p -> p.line).orElse(0)));
            }
        }

        return deps;
    }

    /**
     * 提取 Bean 定义来源（@Bean 方法）
     */
    private List<BeanSource> extractBeanSources(ClassOrInterfaceDeclaration clazz, String relativePath) {
        List<BeanSource> sources = new ArrayList<>();

        // 只处理 @Configuration 类
        if (!hasAnyAnnotation(clazz, "Configuration")) return sources;

        for (MethodDeclaration method : clazz.getMethods()) {
            var beanAnn = method.getAnnotationByName("Bean");
            if (beanAnn.isPresent()) {
                String returnType = method.getTypeAsString();
                String methodName = method.getNameAsString();
                String beanName = extractBeanName(method);
                if (beanName == null) beanName = toLowerCaseFirst(methodName);
                int startLine = method.getBegin().map(p -> p.line).orElse(0);
                sources.add(new BeanSource(0, 0, returnType, beanName, "@Bean", relativePath, startLine));
            }
        }
        return sources;
    }

    private String extractBeanName(MethodDeclaration method) {
        var ann = method.getAnnotationByName("Bean");
        if (ann.isEmpty()) return null;
        var expr = ann.get();
        if (expr instanceof SingleMemberAnnotationExpr single) {
            String value = single.getMemberValue().toString();
            return cleanString(value);
        } else if (expr instanceof NormalAnnotationExpr normal) {
            for (var pair : normal.getPairs()) {
                if ("value".equals(pair.getNameAsString()) || "name".equals(pair.getNameAsString())) {
                    return cleanString(pair.getValue().toString());
                }
            }
        }
        return null;
    }

    private String toLowerCaseFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private boolean hasAnyAnnotation(NodeWithAnnotations<?> node, String... names) {
        for (AnnotationExpr ann : node.getAnnotations()) {
            for (String name : names) {
                if (ann.getNameAsString().equals(name)) return true;
            }
        }
        return false;
    }

    /**
     * 提取测试映射：XxxTest → Xxx, XxxTests → Xxx, XxxSpec → Xxx, TestXxx → Xxx
     */
    private List<TestMapping> extractTestMappings(String relativePath, List<Symbol> symbols) {
        List<TestMapping> mappings = new ArrayList<>();

        for (Symbol s : symbols) {
            if (s.kind() != Symbol.SymbolKind.CLASS) continue;

            String testName = s.name();
            String sourceName = inferSourceClassName(testName);
            if (sourceName == null) continue;

            mappings.add(new TestMapping(0, 0, null, testName, sourceName,
                "NAME_PATTERN", relativePath));
        }
        return mappings;
    }

    /**
     * 从测试类名推断源类名
     */
    private String inferSourceClassName(String name) {
        if (name.endsWith("Test") && name.length() > 4) {
            return name.substring(0, name.length() - 4);
        }
        if (name.endsWith("Tests") && name.length() > 5) {
            return name.substring(0, name.length() - 5);
        }
        if (name.endsWith("Spec") && name.length() > 4) {
            return name.substring(0, name.length() - 4);
        }
        if (name.startsWith("Test") && name.length() > 4) {
            return name.substring(4);
        }
        return null;
    }
}
