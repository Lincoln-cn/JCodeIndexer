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
        serverInfo.addProperty("version", "0.6.2");
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
        findSymbolParams.put("query", Map.of("type", "string", "description", "符号名称或限定名"));
        findSymbolParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) findSymbolParams.put("project", projectParam);
        toolsArray.add(createTool("find_symbol", "按限定名或名称查找符号（类/方法/字段）", findSymbolParams));

        // 3. find_references
        Map<String, Object> findRefParams = new LinkedHashMap<>();
        findRefParams.put("symbol_id", Map.of("type", "integer", "description", "符号 ID（精确匹配）"));
        findRefParams.put("symbol_name", Map.of("type", "string", "description", "符号名称或限定名（模糊匹配，支持 * 通配符）"));
        findRefParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 50));
        if (multiProject) findRefParams.put("project", projectParam);
        toolsArray.add(createTool("find_references",
            "查找某个符号的所有引用位置。支持两种模式：按 symbol_id 精确查找，或按 symbol_name 模糊查找",
            findRefParams));

        // 4. get_call_graph
        Map<String, Object> callGraphParams = new LinkedHashMap<>();
        callGraphParams.put("method_name", Map.of("type", "string", "description", "方法限定名"));
        callGraphParams.put("direction", Map.of("type", "string", "description", "callers | callees | both", "default", "both"));
        if (multiProject) callGraphParams.put("project", projectParam);
        toolsArray.add(createTool("get_call_graph", "获取方法的调用图（调用者/被调用者）", callGraphParams));

        // 5. search_code
        Map<String, Object> searchCodeParams = new LinkedHashMap<>();
        searchCodeParams.put("query", Map.of("type", "string", "description", "搜索关键词（使用 * 返回全部结果）"));
        searchCodeParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) searchCodeParams.put("project", projectParam);
        toolsArray.add(createTool("search_code", "结构化搜索：符号名 + 代码内容。使用 * 通配符可返回所有结果", searchCodeParams));

        // 6. get_file_info
        Map<String, Object> fileInfoParams = new LinkedHashMap<>();
        fileInfoParams.put("file_path", Map.of("type", "string", "description", "文件相对路径"));
        if (multiProject) fileInfoParams.put("project", projectParam);
        toolsArray.add(createTool("get_file_info", "获取指定文件的符号/代码块/调用信息", fileInfoParams));

        // 7. search_config
        Map<String, Object> searchConfigParams = new LinkedHashMap<>();
        searchConfigParams.put("query", Map.of("type", "string", "description", "搜索关键词（匹配 key/value/content）"));
        searchConfigParams.put("config_type", Map.of("type", "string", "description", "配置类型过滤: YAML | PROPERTIES | ENV"));
        searchConfigParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) searchConfigParams.put("project", projectParam);
        toolsArray.add(createTool("search_config", "搜索配置文件中的 key-value（YAML/Properties/ENV）", searchConfigParams));

        // 9. find_dependencies
        Map<String, Object> findDepsParams = new LinkedHashMap<>();
        findDepsParams.put("query", Map.of("type", "string", "description", "搜索关键词（匹配 artifactId/groupId/version）"));
        findDepsParams.put("dep_type", Map.of("type", "string", "description", "依赖类型过滤: POM | GRADLE"));
        findDepsParams.put("limit", Map.of("type", "integer", "description", "最大返回数", "default", 20));
        if (multiProject) findDepsParams.put("project", projectParam);
        toolsArray.add(createTool("find_dependencies", "查找项目依赖（Maven POM / Gradle）", findDepsParams));

        // 10. health（健康检查）
        Map<String, Object> healthParams = new LinkedHashMap<>();
        toolsArray.add(createTool("health", "检查服务器健康状态和索引统计", healthParams));

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
            "symbols", result.symbols().stream().map(s -> Map.of(
                "name", s.name(),
                "qualified_name", s.qualifiedName(),
                "kind", s.kind().name(),
                "file", s.filePath(),
                "line", s.startLine()
            )).toList(),
            "chunks", result.chunks().stream().map(c -> Map.of(
                "file", c.filePath(),
                "name", c.name() != null ? c.name() : "",
                "type", c.type().name(),
                "line", c.startLine()
            )).toList(),
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
        String query = args.get("query").getAsString();
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

    // ==================== Helpers ====================

    private Map<String, Object> callHealth(JsonObject args) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "ok");
        health.put("version", "0.6.2");
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
