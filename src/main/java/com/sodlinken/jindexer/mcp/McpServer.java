package com.sodlinken.jindexer.mcp;

import com.google.gson.*;
import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.*;
import com.sodlinken.jindexer.search.SearchProvider;
import com.sodlinken.jindexer.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MCP Server：JSON-RPC over stdio 实现
 * 支持单项目和多项目模式，注册9个工具
 */
public class McpServer {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);
    private static final Gson gson = new GsonBuilder().create();

    private final Config config;
    private final Map<String, ProjectContext> projects = new LinkedHashMap<>();
    private String defaultProjectName;
    private final long startTime = System.currentTimeMillis();

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final DataInputStream dataIn;
    private final BufferedWriter writer;
    private volatile boolean running = true;
    private final java.util.concurrent.atomic.AtomicInteger pendingTasks = new java.util.concurrent.atomic.AtomicInteger(0);

    public McpServer(Config config) {
        this.config = config;

        if (config.isMultiProject()) {
            // 多项目模式：每个项目使用自己的 .jindexer/ 作为数据目录
            String dataDirName = config.getDataDir().getFileName().toString(); // "jindexer" or ".jindexer"
            for (Config.Project proj : config.getProjects()) {
                Config projConfig = new Config();
                projConfig.setProjectRoot(proj.root());
                projConfig.setDataDir(proj.root().resolve(dataDirName));
                projConfig.setDbName(config.getDbName());
                projConfig.setExtractJavadoc(config.isExtractJavadoc());
                projConfig.setFollowSymlinks(config.isFollowSymlinks());
                projConfig.setMaxFileSizeKB(config.getMaxFileSizeKB());
                projConfig.setIndexingThreads(config.getIndexingThreads());
                log.info("加载项目 {}: root={}, dbPath={}", proj.name(), proj.root(), projConfig.getDbPath());
                projects.put(proj.name(), ProjectContext.create(projConfig));
            }
            defaultProjectName = config.getProjects().get(0).name();
            log.info("多项目模式: {} 个项目, 默认项目={}", projects.size(), defaultProjectName);
        } else {
            // 单项目模式（向后兼容）
            String name = "default";
            projects.put(name, ProjectContext.create(config));
            defaultProjectName = name;
            log.info("单项目模式: projectRoot={}", config.getProjectRoot());
        }

        this.dataIn = new DataInputStream(System.in);
        this.writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
    }

    /**
     * 启动 MCP 服务
     */
    public void start() {
        try {
            log.info("MCP Server 已启动, {} 个项目已加载", projects.size());

            // 主消息循环：按 MCP 规范读取 Content-Length 帧
            while (running) {
                String message = readMessage();
                if (message == null) break;

                // 异步处理请求
                pendingTasks.incrementAndGet();
                executor.submit(() -> {
                    try {
                        handleMessage(message);
                    } finally {
                        pendingTasks.decrementAndGet();
                    }
                });
            }

            // 等待所有异步工具执行完成
            long deadline = System.currentTimeMillis() + 120_000;
            while (pendingTasks.get() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            if (pendingTasks.get() > 0) {
                log.warn("仍有 {} 个异步任务未完成，强制关闭", pendingTasks.get());
            }
        } catch (Exception e) {
            log.error("MCP Server 运行异常", e);
        } finally {
            shutdown();
        }
    }

    /**
     * 读取一条 MCP 消息。支持两种格式：
     * 1. Content-Length 帧：Content-Length: N\r\n\r\n + N bytes JSON body
     * 2. 裸 JSON 行：一行 JSON + 换行符（MCP stdio 规范）
     */
    private String readMessage() {
        try {
            String line;
            while ((line = dataIn.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                // 检查是否为 Content-Length 头
                if (trimmed.toLowerCase().startsWith("content-length:")) {
                    int contentLength = Integer.parseInt(trimmed.substring(15).trim());
                    // 防止 OOM：限制最大消息大小为 10MB
                    if (contentLength > 10 * 1024 * 1024) {
                        log.warn("消息过大: {} bytes，跳过", contentLength);
                        // 读取并丢弃
                        byte[] skip = new byte[Math.min(contentLength, 4096)];
                        int remaining = contentLength;
                        while (remaining > 0) {
                            int read = dataIn.read(skip, 0, Math.min(remaining, skip.length));
                            if (read == -1) break;
                            remaining -= read;
                        }
                        return null;
                    }
                    // 读取剩余的头（跳过空行分隔符）
                    String headerLine;
                    while ((headerLine = dataIn.readLine()) != null) {
                        if (headerLine.trim().isEmpty()) break;
                    }
                    // 读取 body
                    byte[] body = new byte[contentLength];
                    dataIn.readFully(body);
                    return new String(body, StandardCharsets.UTF_8);
                }

                // 非 Content-Length 行 → 视为完整的 JSON 消息
                return trimmed;
            }
            return null;
        } catch (EOFException e) {
            return null;
        } catch (Exception e) {
            log.error("读取消息失败", e);
            return null;
        }
    }

    private void handleMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String method = json.has("method") ? json.get("method").getAsString() : null;
            JsonElement idElement = json.get("id");

            if (method == null) return;

            // MCP 规范：无 id 的消息是通知（notification），应静默忽略
            if (idElement == null) {
                log.debug("忽略通知消息: {}", method);
                return;
            }

            JsonObject params = json.has("params") ? json.getAsJsonObject("params") : new JsonObject();

            switch (method) {
                case "initialize" -> handleInitialize(idElement, params);
                case "tools/list" -> handleToolsList(idElement);
                case "tools/call" -> handleToolsCall(idElement, params);
                case "ping" -> sendResult(idElement, Map.of("pong", true));
                default -> sendError(idElement, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            log.error("处理消息失败", e);
            sendError(null, -32603, "Internal error: " + e.getMessage());
        }
    }

    private void handleInitialize(JsonElement id, JsonObject params) {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", "2024-11-05");

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "java-code-indexer");
        serverInfo.addProperty("version", "0.7.2");
        result.add("serverInfo", serverInfo);

        JsonObject capabilities = new JsonObject();
        JsonObject tools = new JsonObject();
        tools.addProperty("listChanged", false);
        capabilities.add("tools", tools);
        result.add("capabilities", capabilities);

        sendResult(id, result);
    }

    // ==================== 多项目路由 ====================

    /**
     * 从 arguments 中解析目标项目，返回 ProjectContext
     */
    private ProjectContext resolveProject(JsonObject args) {
        String projectName = args.has("project") ? args.get("project").getAsString() : defaultProjectName;
        ProjectContext ctx = projects.get(projectName);
        if (ctx == null) {
            throw new IllegalArgumentException("项目不存在: " + projectName + "，可用项目: " + projects.keySet());
        }
        return ctx;
    }

    private void handleToolsList(JsonElement id) {
        JsonArray toolsArray = new JsonArray();

        boolean multiProject = projects.size() > 1;

        // 项目参数定义（多项目时所有工具均可选此参数）
        Map<String, Object> projectParam = Map.of(
            "type", "string",
            "description", "目标项目名称（多项目模式下必需，单项目模式可省略）"
        );

        // 1. list_projects（仅多项目模式下可用）
        if (multiProject) {
            Map<String, Object> listProjectsParams = new LinkedHashMap<>();
            toolsArray.add(createTool(
                "list_projects",
                "列出所有已索引的项目及其状态",
                listProjectsParams
            ));
        }

        // 2. find_symbol
        Map<String, Object> findSymbolParams = new LinkedHashMap<>();
        findSymbolParams.put("query", Map.of("type", "string", "description", "符号名称或限定名，支持 * 通配符（如 UserService*, *Controller）"));
        findSymbolParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) findSymbolParams.put("project", projectParam);
        toolsArray.add(createTool("find_symbol",
            "【快速定位代码】按名称查找类、方法、字段。比 grep 更高效，返回结构化结果（含文件路径、行号、签名）。使用场景：查找某个类/方法在哪里定义，快速定位代码位置。",
            findSymbolParams));

        // 3. find_references
        Map<String, Object> findRefParams = new LinkedHashMap<>();
        findRefParams.put("symbol_id", Map.of("type", "integer", "description", "符号 ID（精确匹配）"));
        findRefParams.put("symbol_name", Map.of("type", "string", "description", "符号名称或限定名（模糊匹配，支持 * 通配符）"));
        findRefParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 50));
        if (multiProject) findRefParams.put("project", projectParam);
        toolsArray.add(createTool("find_references",
            "【影响分析】查找某个类/方法/字段被哪些地方引用。使用场景：修改代码前评估影响范围，了解哪些地方依赖这个符号。",
            findRefParams));

        // 4. get_call_graph
        Map<String, Object> callGraphParams = new LinkedHashMap<>();
        callGraphParams.put("method_name", Map.of("type", "string", "description", "方法限定名（如 com.example.Service.save）"));
        callGraphParams.put("direction", Map.of("type", "string", "description", "callers（谁调用了我）| callees（我调用了谁）| both（双向）", "default", "both"));
        if (multiProject) callGraphParams.put("project", projectParam);
        toolsArray.add(createTool("get_call_graph",
            "【调用链分析】追踪方法的调用关系：谁调用了这个方法（callers），这个方法调用了谁（callees）。使用场景：理解代码执行流程，调试时追踪调用链路。",
            callGraphParams));

        // 5. search_code
        Map<String, Object> searchCodeParams = new LinkedHashMap<>();
        searchCodeParams.put("query", Map.of("type", "string", "description", "搜索关键词（支持 * 通配符，如 *Service, spring*）"));
        searchCodeParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) searchCodeParams.put("project", projectParam);
        toolsArray.add(createTool("search_code",
            "【全文搜索】同时搜索符号名和代码内容，基于 SQLite FTS5 全文索引。使用场景：查找包含特定关键词的代码，比 grep 更快且支持中文分词。",
            searchCodeParams));

        // 6. get_file_info
        Map<String, Object> fileInfoParams = new LinkedHashMap<>();
        fileInfoParams.put("file_path", Map.of("type", "string", "description", "文件相对路径（如 src/main/java/com/example/Service.java）"));
        if (multiProject) fileInfoParams.put("project", projectParam);
        toolsArray.add(createTool("get_file_info",
            "【文件概览】获取指定文件的所有符号、代码块和调用关系。使用场景：快速了解一个文件的结构，包含哪些类/方法/字段。",
            fileInfoParams));

        // 7. search_config
        Map<String, Object> searchConfigParams = new LinkedHashMap<>();
        searchConfigParams.put("query", Map.of("type", "string", "description", "搜索关键词（匹配配置文件的 key/value/content）"));
        searchConfigParams.put("config_type", Map.of("type", "string", "description", "配置类型过滤: YAML | PROPERTIES | ENV"));
        searchConfigParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) searchConfigParams.put("project", projectParam);
        toolsArray.add(createTool("search_config",
            "【配置搜索】搜索 YAML/Properties/ENV 配置文件。使用场景：查找某个配置项在哪里定义，查看配置值是什么。",
            searchConfigParams));

        // 9. find_dependencies
        Map<String, Object> findDepsParams = new LinkedHashMap<>();
        findDepsParams.put("query", Map.of("type", "string", "description", "搜索关键词（匹配 artifactId/groupId/version，如 spring, mybatis）。省略或传 * 返回所有依赖"));
        findDepsParams.put("dep_type", Map.of("type", "string", "description", "依赖类型过滤: POM | GRADLE"));
        findDepsParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) findDepsParams.put("project", projectParam);
        toolsArray.add(createTool("find_dependencies",
            "【依赖分析】查找项目的 Maven/Gradle 依赖。使用场景：确认某个库是否已引入，查看依赖版本，分析依赖关系。",
            findDepsParams));

        // 10. health（健康检查）
        Map<String, Object> healthParams = new LinkedHashMap<>();
        toolsArray.add(createTool("health",
            "【状态检查】查看索引服务器状态和统计信息：已索引的文件数、符号数、代码行数等。使用场景：确认索引是否最新，了解项目规模。",
            healthParams));

        // 11. search_all_projects（多项目搜索，仅多项目模式下可用）
        if (multiProject) {
            Map<String, Object> searchAllParams = new LinkedHashMap<>();
            searchAllParams.put("query", Map.of("type", "string", "description", "搜索关键词"));
            searchAllParams.put("limit", Map.of("type", "integer", "description", "每个项目的最大返回数", "default", 10));
            toolsArray.add(createTool("search_all_projects", "跨所有项目搜索，返回每个项目的结果", searchAllParams));
        }

        // 12. find_implementations（查找接口实现）
        Map<String, Object> findImplParams = new LinkedHashMap<>();
        findImplParams.put("interface_name", Map.of("type", "string", "description", "接口名称或限定名（如 UserService, WebFilter）"));
        findImplParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) findImplParams.put("project", projectParam);
        toolsArray.add(createTool("find_implementations",
            "【接口实现分析】查找接口的所有实现类。使用场景：了解接口有哪些实现，分析依赖注入的可能实现，重构时评估影响。",
            findImplParams));

        // 13. find_overrides（查找方法重写）
        Map<String, Object> findOverridesParams = new LinkedHashMap<>();
        findOverridesParams.put("method_name", Map.of("type", "string", "description", "方法名（如 save, toString）"));
        findOverridesParams.put("class_name", Map.of("type", "string", "description", "父类限定名（如 java.lang.Object, com.example.BaseService）"));
        findOverridesParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) findOverridesParams.put("project", projectParam);
        toolsArray.add(createTool("find_overrides",
            "【重写分析】查找方法在子类中的所有重写实现。使用场景：理解多态行为，查看某个方法被哪些子类重写。",
            findOverridesParams));

        // 14. find_usages（查找字段使用）
        Map<String, Object> findUsagesParams = new LinkedHashMap<>();
        findUsagesParams.put("field_name", Map.of("type", "string", "description", "字段限定名（如 userService, config.path）"));
        findUsagesParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 50));
        if (multiProject) findUsagesParams.put("project", projectParam);
        toolsArray.add(createTool("find_usages",
            "【使用分析】查找字段/变量的所有读写位置。使用场景：重构时了解字段被哪些地方读取或修改。",
            findUsagesParams));

        // 15. find_annotations（查找符号的所有注解）
        Map<String, Object> findAnnotationsParams = new LinkedHashMap<>();
        findAnnotationsParams.put("symbol_name", Map.of("type", "string", "description", "符号名称或限定名（如 UserService, save）"));
        if (multiProject) findAnnotationsParams.put("project", projectParam);
        toolsArray.add(createTool("find_annotations",
            "【注解查询】查看某个类/方法/字段上有哪些注解。使用场景：了解代码的元数据配置，如 Spring 注解、Lombok 注解等。",
            findAnnotationsParams));

        // 16. find_by_annotation（查找带特定注解的符号）
        Map<String, Object> findByAnnotationParams = new LinkedHashMap<>();
        findByAnnotationParams.put("annotation_name", Map.of("type", "string", "description", "注解名（如 RestController, Service, Component）"));
        findByAnnotationParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) findByAnnotationParams.put("project", projectParam);
        toolsArray.add(createTool("find_by_annotation",
            "【注解搜索】查找所有使用了特定注解的代码。使用场景：找出所有 Controller、Service、Repository 等。",
            findByAnnotationParams));

        // 17. find_api_routes（查找 API 路由映射）
        Map<String, Object> findApiRoutesParams = new LinkedHashMap<>();
        findApiRoutesParams.put("query", Map.of("type", "string", "description", "路径关键词（如 /api, /users, /health）"));
        findApiRoutesParams.put("http_method", Map.of("type", "string", "description", "HTTP 方法过滤: GET/POST/PUT/DELETE/PATCH"));
        findApiRoutesParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 50));
        if (multiProject) findApiRoutesParams.put("project", projectParam);
        toolsArray.add(createTool("find_api_routes",
            "【API 路由分析】查找 Spring Boot 应用的所有 API 端点。使用场景：了解应用有哪些 API，查看路由映射关系。",
            findApiRoutesParams));

        // 18. find_route（根据 HTTP 方法和路径查找路由）
        Map<String, Object> findRouteParams = new LinkedHashMap<>();
        findRouteParams.put("http_method", Map.of("type", "string", "description", "HTTP 方法: GET/POST/PUT/DELETE/PATCH"));
        findRouteParams.put("path", Map.of("type", "string", "description", "URL 路径（如 /api/users/123, /actuator/health）"));
        if (multiProject) findRouteParams.put("project", projectParam);
        toolsArray.add(createTool("find_route",
            "【路由定位】根据 HTTP 方法和路径精确查找对应的 Controller 方法。使用场景：调试 API 请求，找到处理特定请求的代码。",
            findRouteParams));

        // 19. get_type_hierarchy（获取类的继承层次结构）
        Map<String, Object> typeHierarchyParams = new LinkedHashMap<>();
        typeHierarchyParams.put("class_name", Map.of("type", "string", "description", "类名或限定名（如 UserService, ArrayList）"));
        typeHierarchyParams.put("direction", Map.of("type", "string", "description", "up（父类链）| down（子类树）| both（双向）", "default", "both"));
        typeHierarchyParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 50));
        if (multiProject) typeHierarchyParams.put("project", projectParam);
        toolsArray.add(createTool("get_type_hierarchy",
            "【继承分析】查看类的完整继承层次：父类链（extends）和子类树（implements）。使用场景：理解类的继承关系，分析多态设计。",
            typeHierarchyParams));

        // 20. get_bean_dependencies（查找 Bean 的依赖）
        Map<String, Object> beanDepsParams = new LinkedHashMap<>();
        beanDepsParams.put("bean_name", Map.of("type", "string", "description", "Bean 名称或限定名（如 UserService, OrderService）"));
        beanDepsParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) beanDepsParams.put("project", projectParam);
        toolsArray.add(createTool("get_bean_dependencies",
            "【依赖注入分析】查看某个 Spring Bean 注入了哪些其他 Bean。使用场景：理解 Bean 的依赖关系，排查循环依赖问题。",
            beanDepsParams));

        // 21. get_bean_dependents（查找依赖该 Bean 的其他 Bean）
        Map<String, Object> beanDependentsParams = new LinkedHashMap<>();
        beanDependentsParams.put("bean_name", Map.of("type", "string", "description", "Bean 名称或限定名（如 UserService, UserRepository）"));
        beanDependentsParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) beanDependentsParams.put("project", projectParam);
        toolsArray.add(createTool("get_bean_dependents",
            "【反向依赖分析】查看哪些 Bean 注入了指定的 Bean。使用场景：修改某个 Bean 前评估影响范围。",
            beanDependentsParams));

        // 22. find_related_tests（查找与源代码相关的测试类）
        Map<String, Object> relatedTestsParams = new LinkedHashMap<>();
        relatedTestsParams.put("class_name", Map.of("type", "string", "description", "源类名或限定名（如 UserService, OrderController）"));
        relatedTestsParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 10));
        if (multiProject) relatedTestsParams.put("project", projectParam);
        toolsArray.add(createTool("find_related_tests",
            "【测试关联】查找与源代码相关的测试类。使用场景：修改代码后知道需要运行哪些测试，了解测试覆盖情况。",
            relatedTestsParams));

        JsonObject result = new JsonObject();
        result.add("tools", toolsArray);
        sendResult(id, result);
    }

    private void handleToolsCall(JsonElement id, JsonObject params) {
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

        try {
            var result = switch (toolName) {
                case "list_projects" -> callListProjects(arguments);
                case "find_symbol" -> callFindSymbol(arguments);
                case "find_references" -> callFindReferences(arguments);
                case "get_call_graph" -> callGetCallGraph(arguments);
                case "search_code" -> callSearchCode(arguments);
                case "get_file_info" -> callGetFileInfo(arguments);
                case "search_config" -> callSearchConfig(arguments);
                case "find_dependencies" -> callFindDependencies(arguments);
                case "health" -> callHealth(arguments);
                case "search_all_projects" -> callSearchAllProjects(arguments);
                case "find_implementations" -> callFindImplementations(arguments);
                case "find_overrides" -> callFindOverrides(arguments);
                case "find_usages" -> callFindUsages(arguments);
                case "find_annotations" -> callFindAnnotations(arguments);
                case "find_by_annotation" -> callFindByAnnotation(arguments);
                case "find_api_routes" -> callFindApiRoutes(arguments);
                case "find_route" -> callFindRoute(arguments);
                case "get_type_hierarchy" -> callGetTypeHierarchy(arguments);
                case "get_bean_dependencies" -> callGetBeanDependencies(arguments);
                case "get_bean_dependents" -> callGetBeanDependents(arguments);
                case "find_related_tests" -> callFindRelatedTests(arguments);
                default -> Map.of("error", "Unknown tool: " + toolName);
            };
            sendToolResult(id, result);
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolName, e);
            sendError(id, -32603, "Tool execution failed: " + e.getMessage());
        }
    }

    // ==================== Tool Implementations ====================

    private Map<String, Object> callListProjects(JsonObject args) {
        List<Map<String, Object>> projectList = new ArrayList<>();
        for (var entry : projects.entrySet()) {
            ProjectContext ctx = entry.getValue();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", entry.getKey());
            info.put("root", ctx.config().getProjectRoot().toString());
            info.put("data_dir", ctx.config().getDataDir().toString());
            projectList.add(info);
        }
        return Map.of(
            "projects", projectList,
            "total", projectList.size(),
            "default_project", defaultProjectName
        );
    }

    private Map<String, Object> callFindSymbol(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String query = args.get("query").getAsString();
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 20;

        var symbols = "*".equals(query) ? storage.listAllSymbols(limit) : storage.searchSymbolsByName(query, limit);
        return Map.of(
            "project", resolveProjectName(args),
            "symbols", symbols.stream().map(s -> Map.of(
                "id", s.id(),
                "name", s.name(),
                "qualified_name", s.qualifiedName(),
                "kind", s.kind().name(),
                "file", s.filePath(),
                "line", s.startLine(),
                "signature", s.signature() != null ? s.signature() : ""
            )).toList(),
            "total", symbols.size()
        );
    }

    private Map<String, Object> callFindReferences(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 50;

        if (args.has("symbol_id")) {
            long symbolId = args.get("symbol_id").getAsLong();
            var references = storage.findReferencesBySymbol(symbolId);
            return Map.of(
                "project", resolveProjectName(args),
                "references", references.stream().map(r -> Map.of(
                    "id", r.id(),
                    "from_file", r.fromFile(),
                    "from_line", r.fromLine(),
                    "context", r.context() != null ? r.context() : ""
                )).toList(),
                "total", references.size()
            );
        } else if (args.has("symbol_name")) {
            String namePattern = args.get("symbol_name").getAsString();
            var references = storage.findReferencesBySymbolName(namePattern, limit);
            return Map.of(
                "project", resolveProjectName(args),
                "references", references.stream().map(r -> Map.of(
                    "id", r.id(),
                    "symbol_id", r.symbolId(),
                    "from_file", r.fromFile(),
                    "from_line", r.fromLine(),
                    "context", r.context() != null ? r.context() : ""
                )).toList(),
                "total", references.size()
            );
        } else {
            return Map.of("error", "必须提供 symbol_id 或 symbol_name 参数");
        }
    }

    private Map<String, Object> callGetCallGraph(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String methodName = args.get("method_name").getAsString();
        String direction = args.has("direction") ? args.get("direction").getAsString() : "both";

        var calls = storage.findCallsByMethod(methodName);
        var callers = "callers".equals(direction) || "both".equals(direction)
            ? calls.stream().filter(c -> c.calleeMethod().equals(methodName)).toList()
            : List.<Call>of();
        var callees = "callees".equals(direction) || "both".equals(direction)
            ? calls.stream().filter(c -> c.callerMethod().equals(methodName)).toList()
            : List.<Call>of();

        return Map.of(
            "project", resolveProjectName(args),
            "method", methodName,
            "callers", callers.stream().map(c -> Map.of(
                "method", c.callerMethod(),
                "file", c.callerFile(),
                "line", c.callerLine()
            )).toList(),
            "callees", callees.stream().map(c -> Map.of(
                "method", c.calleeMethod(),
                "file", c.calleeFile() != null ? c.calleeFile() : "unknown",
                "line", c.callerLine()
            )).toList()
        );
    }

    private Map<String, Object> callSearchCode(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        String query = args.get("query").getAsString();
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 20;

        SearchResult result = ctx.searchProvider().search(query, limit);
        return Map.of(
            "project", resolveProjectName(args),
            "symbols", result.symbols().stream().map(s -> {
                Map<String, Object> symbolMap = new LinkedHashMap<>();
                symbolMap.put("name", s.name());
                symbolMap.put("qualified_name", s.qualifiedName());
                symbolMap.put("kind", s.kind().name());
                symbolMap.put("file", s.filePath());
                symbolMap.put("line", s.startLine());
                // 添加高亮信息
                symbolMap.put("highlights", computeHighlights(s.name(), query));
                return symbolMap;
            }).toList(),
            "chunks", result.chunks().stream().map(c -> {
                Map<String, Object> chunkMap = new LinkedHashMap<>();
                chunkMap.put("file", c.filePath());
                chunkMap.put("name", c.name() != null ? c.name() : "");
                chunkMap.put("type", c.type().name());
                chunkMap.put("line", c.startLine());
                // 为代码块内容添加高亮
                if (c.content() != null) {
                    chunkMap.put("highlights", computeHighlights(c.content(), query));
                }
                return chunkMap;
            }).toList(),
            "total_hits", result.totalHits(),
            "query_time_ms", result.queryTimeMs()
        );
    }

    private Map<String, Object> callGetFileInfo(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String filePath = args.get("file_path").getAsString();
        
        // 统一路径分隔符为正斜杠
        filePath = filePath.replace("\\", "/");

        var symbols = storage.findSymbolsByFile(filePath);
        var chunks = storage.findChunksByFile(filePath);

        return Map.of(
            "project", resolveProjectName(args),
            "file", filePath,
            "symbol_count", symbols.size(),
            "chunk_count", chunks.size(),
            "symbols", symbols.stream().map(s -> Map.of(
                "name", s.name(),
                "kind", s.kind().name(),
                "line", s.startLine(),
                "signature", s.signature() != null ? s.signature() : ""
            )).toList(),
            "chunks", chunks.stream().map(c -> Map.of(
                "type", c.type().name(),
                "name", c.name() != null ? c.name() : "",
                "line", c.startLine()
            )).toList()
        );
    }

    private Map<String, Object> callSearchConfig(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String query = args.get("query").getAsString();
        String configType = args.has("config_type") ? args.get("config_type").getAsString() : null;
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 20;

        var entries = storage.searchConfigEntries(query, configType, limit);
        return Map.of(
            "project", resolveProjectName(args),
            "config_entries", entries.stream().map(e -> Map.of(
                "id", e.id(),
                "file", e.filePath(),
                "line", e.line(),
                "key", e.key(),
                "value", e.value() != null ? e.value() : "",
                "type", e.configType().name(),
                "content", e.content() != null ? e.content() : ""
            )).toList(),
            "total", entries.size()
        );
    }

    private Map<String, Object> callFindDependencies(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String query = args.has("query") ? args.get("query").getAsString() : "*";
        String depType = args.has("dep_type") ? args.get("dep_type").getAsString() : null;
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 20;

        var deps = storage.searchDependencies(query, depType, limit);
        return Map.of(
            "project", resolveProjectName(args),
            "dependencies", deps.stream().map(d -> Map.of(
                "id", d.id(),
                "file", d.filePath(),
                "line", d.line(),
                "group_id", d.groupId() != null ? d.groupId() : "",
                "artifact_id", d.artifactId(),
                "version", d.version() != null ? d.version() : "",
                "scope", d.scope(),
                "type", d.depType().name(),
                "classifier", d.classifier() != null ? d.classifier() : ""
            )).toList(),
            "total", deps.size()
        );
    }

    private Map<String, Object> callFindImplementations(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String interfaceName = args.get("interface_name").getAsString();
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 20;

        var implementations = storage.findImplementations(interfaceName, limit);
        return Map.of(
            "project", resolveProjectName(args),
            "interface", interfaceName,
            "implementations", implementations.stream().map(s -> Map.of(
                "id", s.id(),
                "name", s.name(),
                "qualified_name", s.qualifiedName(),
                "file", s.filePath(),
                "line", s.startLine(),
                "super_class", s.superClass() != null ? s.superClass() : "",
                "interfaces", s.interfaces() != null ? s.interfaces() : List.of()
            )).toList(),
            "total", implementations.size()
        );
    }

    private Map<String, Object> callFindOverrides(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String methodName = args.get("method_name").getAsString();
        String className = args.get("class_name").getAsString();
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 20;

        var overrides = storage.findOverrides(methodName, className, limit);
        return Map.of(
            "project", resolveProjectName(args),
            "method", methodName,
            "parent_class", className,
            "overrides", overrides.stream().map(s -> Map.of(
                "id", s.id(),
                "name", s.name(),
                "qualified_name", s.qualifiedName(),
                "file", s.filePath(),
                "line", s.startLine(),
                "signature", s.signature() != null ? s.signature() : "",
                "return_type", s.returnType() != null ? s.returnType() : ""
            )).toList(),
            "total", overrides.size()
        );
    }

    private Map<String, Object> callFindUsages(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String fieldName = args.get("field_name").getAsString();
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 50;

        var usages = storage.findFieldUsages(fieldName, limit);
        return Map.of(
            "project", resolveProjectName(args),
            "field", fieldName,
            "usages", usages.stream().map(r -> Map.of(
                "id", r.id(),
                "file", r.fromFile(),
                "line", r.fromLine(),
                "context", r.context() != null ? r.context() : ""
            )).toList(),
            "total", usages.size()
        );
    }

    private Map<String, Object> callFindAnnotations(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String symbolName = args.get("symbol_name").getAsString();

        var annotations = storage.findAnnotationsBySymbolName(symbolName);
        return Map.of(
            "project", resolveProjectName(args),
            "symbol", symbolName,
            "annotations", annotations.stream().map(a -> Map.of(
                "id", a.id(),
                "name", a.name(),
                "attributes", a.attributes() != null ? a.attributes() : Map.of()
            )).toList(),
            "total", annotations.size()
        );
    }

    private Map<String, Object> callFindByAnnotation(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String annotationName = args.get("annotation_name").getAsString();
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 20;

        var symbols = storage.findByAnnotation(annotationName, limit);
        return Map.of(
            "project", resolveProjectName(args),
            "annotation", annotationName,
            "symbols", symbols.stream().map(s -> Map.of(
                "id", s.id(),
                "name", s.name(),
                "qualified_name", s.qualifiedName(),
                "kind", s.kind().name(),
                "file", s.filePath(),
                "line", s.startLine()
            )).toList(),
            "total", symbols.size()
        );
    }

    // ==================== v1.6.0 Tools ====================

    private Map<String, Object> callFindApiRoutes(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String query = args.has("query") ? args.get("query").getAsString() : "";
        String httpMethod = args.has("http_method") ? args.get("http_method").getAsString() : null;
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 50;

        var routes = storage.searchApiRoutes(query, httpMethod, limit);
        return Map.of(
            "project", resolveProjectName(args),
            "routes", routes.stream().map(r -> {
                // 查找对应的 Controller 信息
                String controllerName = "";
                String methodName = "";
                try {
                    var symbols = storage.findSymbolsByFile(r.filePath());
                    for (var s : symbols) {
                        if (s.kind() == Symbol.SymbolKind.CLASS) {
                            controllerName = s.name();
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
                return Map.of(
                    "http_method", r.httpMethod(),
                    "path", r.path(),
                    "base_path", r.basePath() != null ? r.basePath() : "",
                    "method_path", r.methodPath() != null ? r.methodPath() : "",
                    "controller", controllerName,
                    "file", r.filePath(),
                    "line", r.startLine()
                );
            }).toList(),
            "total", routes.size()
        );
    }

    private Map<String, Object> callFindRoute(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String httpMethod = args.get("http_method").getAsString();
        String path = args.get("path").getAsString();

        // 精确匹配
        var routes = storage.searchApiRoutes(path, httpMethod, 10);
        
        // 如果没有精确匹配，尝试模糊匹配
        if (routes.isEmpty()) {
            routes = storage.searchApiRoutes(path.substring(1), httpMethod, 10);
        }

        if (routes.isEmpty()) {
            return Map.of(
                "project", resolveProjectName(args),
                "http_method", httpMethod,
                "path", path,
                "matched", false
            );
        }

        var route = routes.getFirst();
        String controllerName = "";
        try {
            var symbols = storage.findSymbolsByFile(route.filePath());
            for (var s : symbols) {
                if (s.kind() == Symbol.SymbolKind.CLASS) {
                    controllerName = s.name();
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return Map.of(
            "project", resolveProjectName(args),
            "http_method", httpMethod,
            "path", path,
            "matched", true,
            "matched_route", route.path(),
            "controller", controllerName,
            "file", route.filePath(),
            "line", route.startLine()
        );
    }

    private Map<String, Object> callGetTypeHierarchy(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String className = args.get("class_name").getAsString();
        String direction = args.has("direction") ? args.get("direction").getAsString() : "both";
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 50;

        List<Symbol> parents = List.of();
        List<Symbol> children = List.of();

        if ("up".equals(direction) || "both".equals(direction)) {
            parents = storage.findTypeHierarchyUp(className, limit);
        }
        if ("down".equals(direction) || "both".equals(direction)) {
            children = storage.findTypeHierarchyDown(className, limit);
        }

        return Map.of(
            "project", resolveProjectName(args),
            "class", className,
            "parents", parents.stream().map(s -> Map.of(
                "name", s.name(),
                "qualified_name", s.qualifiedName() != null ? s.qualifiedName() : "",
                "type", s.superClass() != null && s.superClass().equals(className) ? "extends" : "implements"
            )).toList(),
            "children", children.stream().map(s -> Map.of(
                "name", s.name(),
                "qualified_name", s.qualifiedName() != null ? s.qualifiedName() : "",
                "type", className.equals(s.superClass()) ? "extends" : "implements"
            )).toList(),
            "depth", Math.max(parents.size(), children.size())
        );
    }

    private Map<String, Object> callGetBeanDependencies(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String beanName = args.get("bean_name").getAsString();
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 20;

        // 查找 Bean 的 symbolId
        var symbols = storage.searchSymbolsByName(beanName, 1);
        if (symbols.isEmpty()) {
            return Map.of(
                "project", resolveProjectName(args),
                "bean", beanName,
                "dependencies", List.of(),
                "total", 0
            );
        }

        long beanSymbolId = symbols.getFirst().id();
        var deps = storage.findBeanDependencies(beanSymbolId, limit);

        return Map.of(
            "project", resolveProjectName(args),
            "bean", beanName,
            "dependencies", deps.stream().map(d -> {
                String dependsOnName = d.dependsOnType();
                // 尝试获取被依赖方的详细信息
                if (d.dependsOnSymbolId() != null) {
                    try {
                        var depSymbols = storage.searchSymbolsByName(dependsOnName, 1);
                        if (!depSymbols.isEmpty()) {
                            dependsOnName = depSymbols.getFirst().qualifiedName();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                return Map.of(
                    "type", dependsOnName,
                    "injection_type", d.injectionType(),
                    "field_name", d.fieldName() != null ? d.fieldName() : "",
                    "file", d.filePath(),
                    "line", d.startLine()
                );
            }).toList(),
            "total", deps.size()
        );
    }

    private Map<String, Object> callGetBeanDependents(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String beanName = args.get("bean_name").getAsString();
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 20;

        // 查找 Bean 的 symbolId
        var symbols = storage.searchSymbolsByName(beanName, 1);
        if (symbols.isEmpty()) {
            return Map.of(
                "project", resolveProjectName(args),
                "bean", beanName,
                "dependents", List.of(),
                "total", 0
            );
        }

        long beanSymbolId = symbols.getFirst().id();
        var dependents = storage.findBeanDependents(beanSymbolId, limit);

        return Map.of(
            "project", resolveProjectName(args),
            "bean", beanName,
            "dependents", dependents.stream().map(d -> {
                String beanFullName = beanName;
                // 尝试获取依赖方的详细信息
                try {
                    var beanSymbols = storage.findSymbolsByFile(d.filePath());
                    for (var s : beanSymbols) {
                        if (s.kind() == Symbol.SymbolKind.CLASS) {
                            beanFullName = s.name();
                            break;
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
                return Map.of(
                    "name", beanFullName,
                    "injection_type", d.injectionType(),
                    "file", d.filePath(),
                    "line", d.startLine()
                );
            }).toList(),
            "total", dependents.size()
        );
    }

    private Map<String, Object> callFindRelatedTests(JsonObject args) throws Exception {
        ProjectContext ctx = resolveProject(args);
        StorageService storage = ctx.storage();
        String className = args.get("class_name").getAsString();
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 10;

        var mappings = storage.findTestMappingsBySource(className, limit);

        return Map.of(
            "project", resolveProjectName(args),
            "source_class", className,
            "related_tests", mappings.stream().map(m -> Map.of(
                "name", m.testClassName(),
                "file", m.filePath(),
                "mapping_type", m.mappingType()
            )).toList(),
            "total", mappings.size()
        );
    }

    // ==================== Helpers ====================

    /**
     * 计算文本中匹配关键词的位置，用于高亮显示
     * 返回匹配的起止位置列表
     */
    private List<Map<String, Object>> computeHighlights(String text, String query) {
        if (text == null || query == null || query.isEmpty() || "*".equals(query)) {
            return List.of();
        }

        List<Map<String, Object>> highlights = new ArrayList<>();
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int startPos = 0;

        while (startPos < lowerText.length()) {
            int found = lowerText.indexOf(lowerQuery, startPos);
            if (found == -1) break;

            highlights.add(Map.of(
                "start", found,
                "end", found + query.length()
            ));
            startPos = found + query.length();
        }

        return highlights;
    }

    private Map<String, Object> callHealth(JsonObject args) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "ok");
        health.put("version", "0.7.2");
        health.put("projects", projects.size());
        health.put("uptime_ms", System.currentTimeMillis() - startTime);

        // 收集每个项目的详细统计信息
        List<Map<String, Object>> projectStats = new ArrayList<>();
        for (var entry : projects.entrySet()) {
            ProjectContext ctx = entry.getValue();
            try {
                Map<String, Object> stat = ctx.storage().getDetailedStats();
                stat.put("name", entry.getKey());
                projectStats.add(stat);
            } catch (Exception e) {
                Map<String, Object> stat = new LinkedHashMap<>();
                stat.put("name", entry.getKey());
                stat.put("error", e.getMessage());
                projectStats.add(stat);
            }
        }
        health.put("project_stats", projectStats);
        return health;
    }

    private Map<String, Object> callSearchAllProjects(JsonObject args) throws Exception {
        String query = args.get("query").getAsString();
        int limit = args.has("limit") ? args.get("limit").getAsInt() : 10;

        List<Map<String, Object>> projectResults = new ArrayList<>();
        int totalHits = 0;

        for (var entry : projects.entrySet()) {
            ProjectContext ctx = entry.getValue();
            SearchResult result = ctx.searchProvider().search(query, limit);

            Map<String, Object> projectResult = new LinkedHashMap<>();
            projectResult.put("project", entry.getKey());
            projectResult.put("symbols", result.symbols().stream().map(s -> Map.of(
                "name", s.name(),
                "qualified_name", s.qualifiedName(),
                "kind", s.kind().name(),
                "file", s.filePath(),
                "line", s.startLine()
            )).toList());
            projectResult.put("chunks", result.chunks().stream().map(c -> {
                Map<String, Object> chunkMap = new LinkedHashMap<>();
                chunkMap.put("file", c.filePath());
                chunkMap.put("name", c.name() != null ? c.name() : "");
                chunkMap.put("type", c.type().name());
                chunkMap.put("line", c.startLine());
                return chunkMap;
            }).toList());
            projectResult.put("total_hits", result.totalHits());

            projectResults.add(projectResult);
            totalHits += result.totalHits();
        }

        return Map.of(
            "results", projectResults,
            "total_hits", totalHits
        );
    }

    private String resolveProjectName(JsonObject args) {
        return args.has("project") ? args.get("project").getAsString() : defaultProjectName;
    }

    // ==================== Response Helpers ====================

    private void sendResult(JsonElement id, Object result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) response.add("id", id);
        response.add("result", gson.toJsonTree(result));
        send(response);
    }

    private void sendToolResult(JsonElement id, Object result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) response.add("id", id);

        JsonObject toolResult = new JsonObject();
        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", gson.toJson(result));
        content.add(textContent);
        toolResult.add("content", content);

        response.add("result", toolResult);
        send(response);
    }

    private void sendError(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) response.add("id", id);

        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);

        send(response);
    }

    private void sendNotification(String method, Object params) {
        JsonObject notification = new JsonObject();
        notification.addProperty("jsonrpc", "2.0");
        notification.addProperty("method", method);
        notification.add("params", gson.toJsonTree(params));
        send(notification);
    }

    private synchronized void send(JsonObject json) {
        try {
            String message = gson.toJson(json);
            writer.write(message);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            log.error("发送响应失败", e);
        }
    }

    private JsonObject createTool(String name, String description, Map<String, Object> inputSchema) {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", name);
        tool.addProperty("description", description);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        List<String> requiredFields = new ArrayList<>();

        for (var entry : inputSchema.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
            JsonObject prop = new JsonObject();
            prop.addProperty("type", (String) propDef.get("type"));
            prop.addProperty("description", (String) propDef.get("description"));
            if (propDef.containsKey("default")) {
                prop.add("default", gson.toJsonTree(propDef.get("default")));
            }
            if (Boolean.TRUE.equals(propDef.get("required"))) {
                requiredFields.add(entry.getKey());
            }
            properties.add(entry.getKey(), prop);
        }

        schema.add("properties", properties);
        if (!requiredFields.isEmpty()) {
            JsonArray reqArray = new JsonArray();
            requiredFields.forEach(reqArray::add);
            schema.add("required", reqArray);
        }
        tool.add("inputSchema", schema);

        return tool;
    }

    public void shutdown() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(120, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("等待异步任务超时，强制关闭");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("等待异步任务被中断，强制关闭");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // 关闭所有项目上下文
        for (var entry : projects.entrySet()) {
            try {
                entry.getValue().close();
                log.info("项目 '{}' 已关闭", entry.getKey());
            } catch (Exception e) {
                log.warn("关闭项目 '{}' 失败: {}", entry.getKey(), e.getMessage());
            }
        }
        log.info("MCP Server 已关闭");
    }
}
