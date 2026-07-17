package com.sodlinken.jindexer.storage;

import com.sodlinken.jindexer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 存储服务，提供符号/引用/调用/代码块/文件元信息的 CRUD 操作
 */
public class StorageService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private final DatabaseManager db;

    // 符号查询的公共列（包含继承信息、Kotlin 和 Scala 字段）
    private static final String SYMBOL_COLUMNS =
        "id, file_path, start_line, end_line, kind, name, qualified_name, " +
        "signature, return_type, parent_class, modifiers, javadoc, super_class, interfaces, " +
        "is_data_class, is_object, is_sealed, is_companion, is_trait, is_case_class";

    public StorageService(DatabaseManager db) {
        this.db = db;
    }

    // ==================== Symbols ====================

    public long insertSymbol(Symbol s) throws SQLException {
        String sql = """
            INSERT INTO symbols (file_path, start_line, end_line, kind, name, qualified_name,
                signature, return_type, parent_class, modifiers, javadoc, super_class, interfaces,
                is_data_class, is_object, is_sealed, is_companion, is_trait, is_case_class)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.filePath());
            ps.setInt(2, s.startLine());
            ps.setInt(3, s.endLine());
            ps.setString(4, s.kind().name());
            ps.setString(5, s.name());
            ps.setString(6, s.qualifiedName());
            ps.setString(7, s.signature());
            ps.setString(8, s.returnType());
            ps.setString(9, s.parentClass());
            ps.setInt(10, s.modifiers());
            ps.setString(11, s.javadoc());
            ps.setString(12, s.superClass());
            ps.setString(13, s.interfaces() != null ? s.interfaces().toString() : null);
            ps.setInt(14, s.isDataClass() ? 1 : 0);
            ps.setInt(15, s.isObject() ? 1 : 0);
            ps.setInt(16, s.isSealed() ? 1 : 0);
            ps.setInt(17, s.isCompanion() ? 1 : 0);
            ps.setInt(18, s.isTrait() ? 1 : 0);
            ps.setInt(19, s.isCaseClass() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public List<Symbol> searchSymbolsByName(String query, int limit) throws SQLException {
        String sql = """
            SELECT SYMBOL_COLUMNS_PLACEHOLDER
            FROM symbols
            WHERE qualified_name LIKE ? OR name LIKE ?
            ORDER BY
                CASE WHEN name = ? THEN 0
                     WHEN name LIKE ? THEN 1
                     WHEN name LIKE ? THEN 2
                     WHEN qualified_name LIKE ? THEN 3
                     ELSE 4 END,
                CASE kind WHEN 'CLASS' THEN 0
                          WHEN 'METHOD' THEN 1
                          WHEN 'FIELD' THEN 2 END,
                qualified_name
            LIMIT ?
            """.replace("SYMBOL_COLUMNS_PLACEHOLDER", SYMBOL_COLUMNS);
        String contains = "%" + query + "%";
        String prefix = query + "%";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, contains);
            ps.setString(2, contains);
            ps.setString(3, query);
            ps.setString(4, prefix);
            ps.setString(5, contains);
            ps.setString(6, contains);
            ps.setInt(7, limit);
            return mapSymbols(ps);
        }
    }

    /**
     * FTS5 全文搜索符号（性能优化版）
     * 支持布尔操作：AND, OR, NOT
     * 支持前缀搜索：term*
     * 支持短语搜索："term1 term2"
     * 排序：精确匹配 > 类型优先级 > FTS5 rank
     */
    public List<Symbol> searchSymbolsFts(String query, int limit) throws SQLException {
        String ftsQuery = convertToFtsQuery(query);
        String sql = """
            SELECT s.id, s.file_path, s.start_line, s.end_line, s.kind, s.name, s.qualified_name,
                   s.signature, s.return_type, s.parent_class, s.modifiers, s.javadoc,
                   s.super_class, s.interfaces, s.is_data_class, s.is_object, s.is_sealed, s.is_companion,
                   s.is_trait, s.is_case_class,
                   rank AS fts_rank
            FROM symbols s
            JOIN symbols_fts fts ON s.id = fts.rowid
            WHERE symbols_fts MATCH ?
            ORDER BY
                CASE WHEN s.name = ? THEN 0
                     WHEN s.qualified_name = ? THEN 1
                     ELSE 2 END,
                CASE s.kind WHEN 'CLASS' THEN 0
                            WHEN 'METHOD' THEN 1
                            WHEN 'FIELD' THEN 2 END,
                rank
            LIMIT ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, ftsQuery);
            ps.setString(2, query);
            ps.setString(3, query);
            ps.setInt(4, limit);
            return mapSymbols(ps);
        }
    }

    /**
     * FTS5 全文搜索代码块（性能优化版）
     * 排序：精确匹配 > 类型优先级 > FTS5 rank
     */
    public List<Chunk> searchChunksFts(String query, int limit) throws SQLException {
        String ftsQuery = convertToFtsQuery(query);
        String sql = """
            SELECT c.id, c.file_path, c.type, c.start_line, c.end_line, c.name,
                   c.content, c.package_name, c.class_name, c.signature,
                   rank AS fts_rank
            FROM chunks c
            JOIN chunks_fts fts ON c.id = fts.rowid
            WHERE chunks_fts MATCH ?
            ORDER BY
                CASE c.type WHEN 'CLASS' THEN 0
                            WHEN 'METHOD' THEN 1
                            WHEN 'FILE_HEADER' THEN 2
                            WHEN 'ANNOTATION' THEN 3 END,
                rank
            LIMIT ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, ftsQuery);
            ps.setInt(2, limit);
            return mapChunks(ps);
        }
    }

    /**
     * 将用户查询转换为 FTS5 查询语法
     * - 普通关键词 → 关键词*
     * - 布尔操作 → AND/OR/NOT + 前缀匹配
     * - 短语搜索 → 保持引号
     * - 通配符 → 保持原样
     */
    private String convertToFtsQuery(String query) {
        if (query == null || query.isEmpty()) return "*";

        // 如果包含引号，保持原样（短语搜索）
        if (query.contains("\"")) {
            return query;
        }

        // 如果已有通配符，保持原样
        if (query.endsWith("*")) {
            return query;
        }

        // 处理布尔操作符：将每个 term 转换为前缀匹配
        if (query.contains(" AND ") || query.contains(" OR ") || query.contains(" NOT ")) {
            return convertBooleanQuery(query);
        }

        // 单个关键词，添加前缀通配符
        return query + "*";
    }

    /**
     * 转换布尔查询为 FTS5 格式
     * 将 "Config AND Loader" 转换为 "Config* AND Loader*"
     */
    private String convertBooleanQuery(String query) {
        StringBuilder result = new StringBuilder();
        String[] parts = query.split("(\\s+AND\\s+|\\s+OR\\s+|\\s+NOT\\s+)");

        // 保留操作符
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\s+AND\\s+|\\s+OR\\s+|\\s+NOT\\s+)").matcher(query);
        java.util.List<String> operators = new ArrayList<>();
        while (matcher.find()) {
            operators.add(matcher.group());
        }

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (!part.isEmpty()) {
                // 对每个 term 添加前缀通配符
                if (!part.endsWith("*")) {
                    result.append(part).append("*");
                } else {
                    result.append(part);
                }
            }
            // 添加操作符（如果有）
            if (i < operators.size()) {
                result.append(operators.get(i));
            }
        }

        return result.toString();
    }

    /**
     * 列出所有符号（通配符 * 搜索用）
     */
    public List<Symbol> listAllSymbols(int limit) throws SQLException {
        String sql = """
            SELECT SYMBOL_COLUMNS_PLACEHOLDER
            FROM symbols
            ORDER BY qualified_name
            LIMIT ?
            """.replace("SYMBOL_COLUMNS_PLACEHOLDER", SYMBOL_COLUMNS);
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            return mapSymbols(ps);
        }
    }

    /**
     * 通过符号名模式查找引用（支持模糊匹配）
     */
    public List<Reference> findReferencesBySymbolName(String namePattern, int limit) throws SQLException {
        String sql = """
            SELECT cr.id, cr.symbol_id, cr.from_file, cr.from_line, cr.context
            FROM code_references cr
            JOIN symbols s ON cr.symbol_id = s.id
            WHERE s.qualified_name LIKE ? OR s.name LIKE ?
            ORDER BY cr.from_file, cr.from_line
            LIMIT ?
            """;
        String pattern = "%" + namePattern + "%";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setInt(3, limit);
            return mapReferences(ps);
        }
    }

    public Optional<Symbol> findSymbolByQualifiedName(String qualifiedName) throws SQLException {
        String sql = """
            SELECT SYMBOL_COLUMNS_PLACEHOLDER
            FROM symbols WHERE qualified_name = ?
            """.replace("SYMBOL_COLUMNS_PLACEHOLDER", SYMBOL_COLUMNS);
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, qualifiedName);
            List<Symbol> results = mapSymbols(ps);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
        }
    }

    public Optional<Symbol> findSymbolById(long id) throws SQLException {
        String sql = """
            SELECT SYMBOL_COLUMNS_PLACEHOLDER
            FROM symbols WHERE id = ?
            """.replace("SYMBOL_COLUMNS_PLACEHOLDER", SYMBOL_COLUMNS);
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            List<Symbol> results = mapSymbols(ps);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
        }
    }

    /**
     * 按限定名精确查找符号 ID
     */
    public long findSymbolIdByQualifiedName(String qualifiedName) throws SQLException {
        String sql = "SELECT id FROM symbols WHERE qualified_name = ? LIMIT 1";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, qualifiedName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("id") : -1;
            }
        }
    }

    /**
     * 批量按限定名查找符号 ID
     */
    public Map<String, Long> findSymbolIdsByQualifiedNames(List<String> qualifiedNames) throws SQLException {
        Map<String, Long> result = new HashMap<>();
        if (qualifiedNames.isEmpty()) return result;

        String sql = "SELECT id, qualified_name FROM symbols WHERE qualified_name IN (" +
                     qualifiedNames.stream().map(n -> "?").reduce((a, b) -> a + "," + b).orElse("") + ")";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < qualifiedNames.size(); i++) {
                ps.setString(i + 1, qualifiedNames.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("qualified_name"), rs.getLong("id"));
                }
            }
        }
        return result;
    }

    public List<Symbol> findSymbolsByFile(String filePath) throws SQLException {
        // 统一路径分隔符为正斜杠
        filePath = filePath.replace("\\", "/");
        String sql = """
            SELECT SYMBOL_COLUMNS_PLACEHOLDER
            FROM symbols WHERE file_path = ? ORDER BY start_line
            """.replace("SYMBOL_COLUMNS_PLACEHOLDER", SYMBOL_COLUMNS);
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, filePath);
            return mapSymbols(ps);
        }
    }

    public List<Symbol> findSymbolsByKind(Symbol.SymbolKind kind) throws SQLException {
        String sql = """
            SELECT SYMBOL_COLUMNS_PLACEHOLDER
            FROM symbols WHERE kind = ? ORDER BY qualified_name
            """.replace("SYMBOL_COLUMNS_PLACEHOLDER", SYMBOL_COLUMNS);
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, kind.name());
            return mapSymbols(ps);
        }
    }

    public void deleteSymbolsByFile(String filePath) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM symbols WHERE file_path = ?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    // ==================== Inheritance Queries ====================

    /**
     * 查找接口的所有实现类
     */
    public List<Symbol> findImplementations(String interfaceName, int limit) throws SQLException {
        String sql = """
            SELECT SYMBOL_COLUMNS_PLACEHOLDER
            FROM symbols
            WHERE kind = 'CLASS'
            AND (interfaces LIKE ? OR super_class LIKE ?)
            ORDER BY qualified_name
            LIMIT ?
            """.replace("SYMBOL_COLUMNS_PLACEHOLDER", SYMBOL_COLUMNS);
        String pattern = "%" + interfaceName + "%";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setInt(3, limit);
            return mapSymbols(ps);
        }
    }

    /**
     * 查找方法的所有重写（子类重写）
     */
    public List<Symbol> findOverrides(String methodName, String superClassQualifiedName, int limit) throws SQLException {
        String sql = """
            SELECT SYMBOL_COLUMNS_PLACEHOLDER
            FROM symbols
            WHERE kind = 'METHOD'
            AND name = ?
            AND parent_class IN (
                SELECT name FROM symbols
                WHERE kind = 'CLASS'
                AND (super_class = ? OR interfaces LIKE ?)
            )
            ORDER BY qualified_name
            LIMIT ?
            """.replace("SYMBOL_COLUMNS_PLACEHOLDER", SYMBOL_COLUMNS);
        String interfacePattern = "%" + superClassQualifiedName + "%";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, methodName);
            ps.setString(2, superClassQualifiedName);
            ps.setString(3, interfacePattern);
            ps.setInt(4, limit);
            return mapSymbols(ps);
        }
    }

    /**
     * 查找字段/变量的所有使用位置
     */
    public List<Reference> findFieldUsages(String fieldQualifiedName, int limit) throws SQLException {
        String sql = """
            SELECT cr.id, cr.symbol_id, cr.from_file, cr.from_line, cr.context
            FROM code_references cr
            JOIN symbols s ON cr.symbol_id = s.id
            WHERE s.qualified_name = ? OR s.name = ?
            ORDER BY cr.from_file, cr.from_line
            LIMIT ?
            """;
        // 提取字段名（最后一段）
        String fieldName = fieldQualifiedName.contains(".")
            ? fieldQualifiedName.substring(fieldQualifiedName.lastIndexOf('.') + 1)
            : fieldQualifiedName;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, fieldQualifiedName);
            ps.setString(2, fieldName);
            ps.setInt(3, limit);
            return mapReferences(ps);
        }
    }

    /**
     * 类型层次查询：向上查找父类链和实现的接口
     */
    public List<Symbol> findTypeHierarchyUp(String className, int limit) throws SQLException {
        List<Symbol> result = new ArrayList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        queue.add(className);

        while (!queue.isEmpty() && result.size() < limit) {
            String current = queue.poll();
            if (!visited.add(current)) continue;

            // 精确匹配优先，然后模糊匹配
            String sql = """
                SELECT SYMBOL_COLUMNS_PLACEHOLDER
                FROM symbols
                WHERE kind = 'CLASS' AND (qualified_name = ? OR name = ?)
                LIMIT 1
                """.replace("SYMBOL_COLUMNS_PLACEHOLDER", SYMBOL_COLUMNS);
            try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
                ps.setString(1, current);
                ps.setString(2, current);
                List<Symbol> symbols = mapSymbols(ps);
                if (symbols.isEmpty()) continue;

                Symbol sym = symbols.getFirst();
                result.add(sym);

                // 添加父类到队列
                if (sym.superClass() != null && !sym.superClass().isEmpty()) {
                    queue.add(sym.superClass());
                }
                // 添加接口到队列
                if (sym.interfaces() != null) {
                    for (String iface : sym.interfaces()) {
                        queue.add(iface.trim());
                    }
                }
            }
        }
        return result;
    }

    /**
     * 类型层次查询：向下查找所有子类
     */
    public List<Symbol> findTypeHierarchyDown(String className, int limit) throws SQLException {
        String sql = """
            SELECT SYMBOL_COLUMNS_PLACEHOLDER
            FROM symbols
            WHERE kind = 'CLASS' AND (super_class = ? OR interfaces LIKE ?)
            ORDER BY qualified_name
            LIMIT ?
            """.replace("SYMBOL_COLUMNS_PLACEHOLDER", SYMBOL_COLUMNS);
        String pattern = "%" + className + "%";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, className);
            ps.setString(2, pattern);
            ps.setInt(3, limit);
            return mapSymbols(ps);
        }
    }

    // ==================== References ====================

    public long insertReference(Reference ref) throws SQLException {
        String sql = """
            INSERT INTO code_references (symbol_id, from_file, from_line, context)
            VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, ref.symbolId());
            ps.setString(2, ref.fromFile());
            ps.setInt(3, ref.fromLine());
            ps.setString(4, ref.context());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public List<Reference> findReferencesBySymbol(long symbolId) throws SQLException {
        String sql = "SELECT id, symbol_id, from_file, from_line, context FROM code_references WHERE symbol_id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, symbolId);
            return mapReferences(ps);
        }
    }

    public List<Reference> findReferencesByFile(String filePath) throws SQLException {
        String sql = "SELECT id, symbol_id, from_file, from_line, context FROM code_references WHERE from_file = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, filePath);
            return mapReferences(ps);
        }
    }

    public void deleteReferencesByFile(String filePath) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM code_references WHERE from_file = ?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    // ==================== Calls ====================

    public long insertCall(Call call) throws SQLException {
        String sql = """
            INSERT INTO calls (caller_method, caller_file, caller_line, callee_method, callee_file)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, call.callerMethod());
            ps.setString(2, call.callerFile());
            ps.setInt(3, call.callerLine());
            ps.setString(4, call.calleeMethod());
            ps.setString(5, call.calleeFile());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public List<Call> findCallsByMethod(String qualifiedName) throws SQLException {
        String sql = """
            SELECT id, caller_method, caller_file, caller_line, callee_method, callee_file
            FROM calls WHERE caller_method = ? OR callee_method = ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, qualifiedName);
            ps.setString(2, qualifiedName);
            return mapCalls(ps);
        }
    }

    public List<Call> findCallers(String qualifiedName) throws SQLException {
        String sql = """
            SELECT id, caller_method, caller_file, caller_line, callee_method, callee_file
            FROM calls WHERE callee_method = ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, qualifiedName);
            return mapCalls(ps);
        }
    }

    public List<Call> findCallees(String qualifiedName) throws SQLException {
        String sql = """
            SELECT id, caller_method, caller_file, caller_line, callee_method, callee_file
            FROM calls WHERE caller_method = ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, qualifiedName);
            return mapCalls(ps);
        }
    }

    public void deleteCallsByFile(String filePath) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM calls WHERE caller_file = ?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    // ==================== Chunks ====================

    public long insertChunk(Chunk chunk) throws SQLException {
        String sql = """
            INSERT INTO chunks (file_path, type, start_line, end_line, name, content,
                package_name, class_name, signature)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, chunk.filePath());
            ps.setString(2, chunk.type().name());
            ps.setInt(3, chunk.startLine());
            ps.setInt(4, chunk.endLine());
            ps.setString(5, chunk.name());
            ps.setString(6, chunk.content());
            ps.setString(7, chunk.packageName());
            ps.setString(8, chunk.className());
            ps.setString(9, chunk.signature());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public List<Chunk> searchChunksByContent(String query, int limit) throws SQLException {
        String sql = """
            SELECT id, file_path, type, start_line, end_line, name, content,
                   package_name, class_name, signature
            FROM chunks
            WHERE content LIKE ? OR name LIKE ? OR class_name LIKE ?
            ORDER BY
                CASE WHEN name = ? THEN 0
                     WHEN name LIKE ? THEN 1
                     WHEN class_name LIKE ? THEN 2
                     WHEN content LIKE ? THEN 3
                     ELSE 4 END,
                CASE type WHEN 'CLASS' THEN 0
                          WHEN 'METHOD' THEN 1
                          WHEN 'ANNOTATION' THEN 2
                          WHEN 'FILE_HEADER' THEN 3 END,
                file_path, start_line
            LIMIT ?
            """;
        String prefix = "%" + query + "%";
        String exactPrefix = query + "%";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, prefix);
            ps.setString(2, prefix);
            ps.setString(3, prefix);
            ps.setString(4, query);
            ps.setString(5, exactPrefix);
            ps.setString(6, exactPrefix);
            ps.setString(7, prefix);
            ps.setInt(8, limit);
            return mapChunks(ps);
        }
    }

    /**
     * 列出所有代码块（通配符 * 搜索用）
     */
    public List<Chunk> listAllChunks(int limit) throws SQLException {
        String sql = """
            SELECT id, file_path, type, start_line, end_line, name, content,
                   package_name, class_name, signature
            FROM chunks
            ORDER BY file_path, start_line
            LIMIT ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            return mapChunks(ps);
        }
    }

    public List<Chunk> findChunksByFile(String filePath) throws SQLException {
        // 统一路径分隔符为正斜杠
        filePath = filePath.replace("\\", "/");
        String sql = """
            SELECT id, file_path, type, start_line, end_line, name, content,
                   package_name, class_name, signature
            FROM chunks WHERE file_path = ? ORDER BY start_line
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, filePath);
            return mapChunks(ps);
        }
    }

    public List<Chunk> findChunksByType(Chunk.ChunkType type) throws SQLException {
        String sql = """
            SELECT id, file_path, type, start_line, end_line, name, content,
                   package_name, class_name, signature
            FROM chunks WHERE type = ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, type.name());
            return mapChunks(ps);
        }
    }

    public void deleteChunksByFile(String filePath) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM chunks WHERE file_path = ?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    // ==================== ConfigEntries ====================

    public void insertConfigEntries(List<ConfigEntry> entries) throws SQLException {
        if (entries.isEmpty()) return;
        String sql = """
            INSERT INTO config_entries (file_path, line, key, value, config_type, content)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (ConfigEntry e : entries) {
                ps.setString(1, e.filePath());
                ps.setInt(2, e.line());
                ps.setString(3, e.key());
                ps.setString(4, e.value());
                ps.setString(5, e.configType().name());
                ps.setString(6, e.content());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<ConfigEntry> searchConfigEntries(String query, String configType, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT id, file_path, line, key, value, config_type, content
            FROM config_entries
            WHERE (key LIKE ? OR value LIKE ? OR content LIKE ?)
            """);
        if (configType != null && !configType.isEmpty()) {
            sql.append(" AND config_type = ?");
        }
        sql.append(" ORDER BY file_path, line LIMIT ?");

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
            String pattern = "%" + query + "%";
            int idx = 1;
            ps.setString(idx++, pattern);
            ps.setString(idx++, pattern);
            ps.setString(idx++, pattern);
            if (configType != null && !configType.isEmpty()) {
                ps.setString(idx++, configType.toUpperCase());
            }
            ps.setInt(idx, limit);

            List<ConfigEntry> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ConfigEntry(
                        rs.getLong("id"),
                        rs.getString("file_path"),
                        rs.getInt("line"),
                        rs.getString("key"),
                        rs.getString("value"),
                        ConfigEntry.ConfigType.valueOf(rs.getString("config_type")),
                        rs.getString("content")
                    ));
                }
            }
            return list;
        }
    }

    public List<ConfigEntry> findConfigEntriesByFile(String filePath) throws SQLException {
        String sql = """
            SELECT id, file_path, line, key, value, config_type, content
            FROM config_entries WHERE file_path = ? ORDER BY line
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, filePath);
            List<ConfigEntry> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ConfigEntry(
                        rs.getLong("id"),
                        rs.getString("file_path"),
                        rs.getInt("line"),
                        rs.getString("key"),
                        rs.getString("value"),
                        ConfigEntry.ConfigType.valueOf(rs.getString("config_type")),
                        rs.getString("content")
                    ));
                }
            }
            return list;
        }
    }

    public void deleteConfigEntriesByFile(String filePath) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM config_entries WHERE file_path = ?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    // ==================== Dependencies ====================

    public void insertDependencies(List<Dependency> deps) throws SQLException {
        if (deps.isEmpty()) return;
        String sql = """
            INSERT INTO dependencies (file_path, line, group_id, artifact_id, version, scope, dep_type, classifier)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (Dependency d : deps) {
                ps.setString(1, d.filePath());
                ps.setInt(2, d.line());
                ps.setString(3, d.groupId());
                ps.setString(4, d.artifactId());
                ps.setString(5, d.version());
                ps.setString(6, d.scope());
                ps.setString(7, d.depType().name());
                ps.setString(8, d.classifier());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<Dependency> searchDependencies(String query, String depType, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT id, file_path, line, group_id, artifact_id, version, scope, dep_type, classifier
            FROM dependencies
            WHERE (? = '*' OR artifact_id LIKE ? OR group_id LIKE ? OR version LIKE ?)
            """);
        if (depType != null && !depType.isEmpty()) {
            sql.append(" AND dep_type = ?");
        }
        sql.append(" ORDER BY file_path, line LIMIT ?");

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
            String pattern = "%" + query + "%";
            int idx = 1;
            ps.setString(idx++, query);
            ps.setString(idx++, pattern);
            ps.setString(idx++, pattern);
            ps.setString(idx++, pattern);
            if (depType != null && !depType.isEmpty()) {
                ps.setString(idx++, depType.toUpperCase());
            }
            ps.setInt(idx, limit);

            List<Dependency> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Dependency(
                        rs.getLong("id"),
                        rs.getString("file_path"),
                        rs.getInt("line"),
                        rs.getString("group_id"),
                        rs.getString("artifact_id"),
                        rs.getString("version"),
                        rs.getString("scope"),
                        Dependency.DepType.valueOf(rs.getString("dep_type")),
                        rs.getString("classifier")
                    ));
                }
            }
            return list;
        }
    }

    public List<Dependency> findDependenciesByFile(String filePath) throws SQLException {
        String sql = """
            SELECT id, file_path, line, group_id, artifact_id, version, scope, dep_type, classifier
            FROM dependencies WHERE file_path = ? ORDER BY line
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, filePath);
            List<Dependency> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Dependency(
                        rs.getLong("id"),
                        rs.getString("file_path"),
                        rs.getInt("line"),
                        rs.getString("group_id"),
                        rs.getString("artifact_id"),
                        rs.getString("version"),
                        rs.getString("scope"),
                        Dependency.DepType.valueOf(rs.getString("dep_type")),
                        rs.getString("classifier")
                    ));
                }
            }
            return list;
        }
    }

    public void deleteDependenciesByFile(String filePath) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM dependencies WHERE file_path = ?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    // ==================== FileMeta ====================

    public Optional<FileMeta> findFileMeta(String filePath) throws SQLException {
        String sql = """
            SELECT file_path, size, last_modified, sha1, symbol_count, last_indexed_at
            FROM file_meta WHERE file_path = ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapFileMeta(rs)) : Optional.empty();
            }
        }
    }

    public List<FileMeta> findAllFileMeta() throws SQLException {
        String sql = """
            SELECT file_path, size, last_modified, sha1, symbol_count, last_indexed_at
            FROM file_meta
            """;
        List<FileMeta> list = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapFileMeta(rs));
            }
        }
        return list;
    }

    public void upsertFileMeta(FileMeta meta) throws SQLException {
        String sql = """
            INSERT INTO file_meta (file_path, size, last_modified, sha1, symbol_count, last_indexed_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(file_path) DO UPDATE SET
                size=excluded.size,
                last_modified=excluded.last_modified,
                sha1=excluded.sha1,
                symbol_count=excluded.symbol_count,
                last_indexed_at=excluded.last_indexed_at
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, meta.filePath());
            ps.setLong(2, meta.size());
            ps.setLong(3, meta.lastModified());
            ps.setString(4, meta.sha1());
            ps.setInt(5, meta.symbolCount());
            ps.setLong(6, meta.lastIndexedAt());
            ps.executeUpdate();
        }
    }

    public void deleteFileMeta(String filePath) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM file_meta WHERE file_path = ?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    /**
     * 删除文件的所有关联数据（符号、引用、调用、代码块、元信息）
     */
    public void deleteAllByFile(String filePath) throws SQLException {
        deleteReferencesByFile(filePath);
        deleteCallsByFile(filePath);
        deleteChunksByFile(filePath);
        deleteSymbolsByFile(filePath);
        deleteConfigEntriesByFile(filePath);
        deleteDependenciesByFile(filePath);
        deleteFileMeta(filePath);
        deleteAnnotationsByFile(filePath);
        deleteBeanSourcesByFile(filePath);
    }

    // ==================== BeanSources ====================

    public void insertBeanSources(List<BeanSource> sources) throws SQLException {
        String sql = "INSERT INTO bean_sources (symbol_id, return_type, bean_name, source_type, file_path, start_line) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (BeanSource s : sources) {
                ps.setLong(1, s.symbolId());
                ps.setString(2, s.returnType());
                ps.setString(3, s.beanName());
                ps.setString(4, s.sourceType());
                ps.setString(5, s.filePath());
                ps.setInt(6, s.startLine());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void deleteBeanSourcesByFile(String filePath) throws SQLException {
        String sql = "DELETE FROM bean_sources WHERE file_path = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    public List<BeanSource> findBeanSourcesByType(String typeName) throws SQLException {
        String sql = "SELECT id, symbol_id, return_type, bean_name, source_type, file_path, start_line FROM bean_sources WHERE return_type = ?";
        List<BeanSource> list = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, typeName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BeanSource(
                        rs.getLong("id"), rs.getLong("symbol_id"),
                        rs.getString("return_type"), rs.getString("bean_name"),
                        rs.getString("source_type"), rs.getString("file_path"),
                        rs.getInt("start_line")
                    ));
                }
            }
        }
        return list;
    }

    public List<BeanSource> findBeanSourcesByName(String beanName) throws SQLException {
        String sql = "SELECT id, symbol_id, return_type, bean_name, source_type, file_path, start_line FROM bean_sources WHERE bean_name = ?";
        List<BeanSource> list = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, beanName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BeanSource(
                        rs.getLong("id"), rs.getLong("symbol_id"),
                        rs.getString("return_type"), rs.getString("bean_name"),
                        rs.getString("source_type"), rs.getString("file_path"),
                        rs.getInt("start_line")
                    ));
                }
            }
        }
        return list;
    }

    // ==================== ConfigBindings ====================

    public void insertConfigBindings(List<ConfigBinding> bindings) throws SQLException {
        String sql = "INSERT INTO config_bindings (symbol_id, config_key, field_name, binding_type, file_path, start_line) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (ConfigBinding b : bindings) {
                ps.setLong(1, b.symbolId());
                ps.setString(2, b.configKey());
                ps.setString(3, b.fieldName());
                ps.setString(4, b.bindingType());
                ps.setString(5, b.filePath());
                ps.setInt(6, b.startLine());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void deleteConfigBindingsByFile(String filePath) throws SQLException {
        String sql = "DELETE FROM config_bindings WHERE file_path = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    public List<ConfigBinding> findConfigBindingsByPrefix(String prefix) throws SQLException {
        String sql = "SELECT id, symbol_id, config_key, field_name, binding_type, file_path, start_line FROM config_bindings WHERE config_key LIKE ?";
        List<ConfigBinding> list = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ConfigBinding(
                        rs.getLong("id"), rs.getLong("symbol_id"),
                        rs.getString("config_key"), rs.getString("field_name"),
                        rs.getString("binding_type"), rs.getString("file_path"),
                        rs.getInt("start_line")
                    ));
                }
            }
        }
        return list;
    }

    // ==================== Annotations ====================

    public long insertAnnotation(Annotation annotation) throws SQLException {
        String sql = "INSERT INTO annotations (symbol_id, name, attributes) VALUES (?, ?, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, annotation.symbolId());
            ps.setString(2, annotation.name());
            ps.setString(3, annotation.attributes() != null ? mapToJson(annotation.attributes()) : null);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public void insertAnnotations(List<Annotation> annotations) throws SQLException {
        if (annotations.isEmpty()) return;
        String sql = "INSERT INTO annotations (symbol_id, name, attributes) VALUES (?, ?, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (Annotation a : annotations) {
                ps.setLong(1, a.symbolId());
                ps.setString(2, a.name());
                ps.setString(3, a.attributes() != null ? mapToJson(a.attributes()) : null);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<Annotation> findAnnotationsBySymbol(long symbolId) throws SQLException {
        String sql = "SELECT id, symbol_id, name, attributes FROM annotations WHERE symbol_id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, symbolId);
            return mapAnnotations(ps);
        }
    }

    public List<Annotation> findAnnotationsBySymbolName(String symbolName) throws SQLException {
        String sql = """
            SELECT a.id, a.symbol_id, a.name, a.attributes
            FROM annotations a
            JOIN symbols s ON a.symbol_id = s.id
            WHERE s.name = ? OR s.qualified_name = ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, symbolName);
            ps.setString(2, symbolName);
            return mapAnnotations(ps);
        }
    }

    public List<Symbol> findByAnnotation(String annotationName, int limit) throws SQLException {
        String sql = """
            SELECT s.id, s.file_path, s.start_line, s.end_line, s.kind, s.name, s.qualified_name,
                   s.signature, s.return_type, s.parent_class, s.modifiers, s.javadoc, s.super_class, s.interfaces,
                   s.is_data_class, s.is_object, s.is_sealed, s.is_companion, s.is_trait, s.is_case_class
            FROM symbols s
            JOIN annotations a ON s.id = a.symbol_id
            WHERE a.name = ?
            ORDER BY s.qualified_name
            LIMIT ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, annotationName);
            ps.setInt(2, limit);
            return mapSymbols(ps);
        }
    }

    public void deleteAnnotationsBySymbol(long symbolId) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM annotations WHERE symbol_id = ?")) {
            ps.setLong(1, symbolId);
            ps.executeUpdate();
        }
    }

    public void deleteAnnotationsByFile(String filePath) throws SQLException {
        String sql = """
            DELETE FROM annotations WHERE symbol_id IN (
                SELECT id FROM symbols WHERE file_path = ?
            )
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    private List<Annotation> mapAnnotations(PreparedStatement ps) throws SQLException {
        List<Annotation> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String attrsJson = rs.getString("attributes");
                Map<String, String> attributes = null;
                if (attrsJson != null && !attrsJson.isEmpty()) {
                    attributes = jsonToMap(attrsJson);
                }
                list.add(new Annotation(
                    rs.getLong("id"),
                    rs.getLong("symbol_id"),
                    rs.getString("name"),
                    attributes
                ));
            }
        }
        return list;
    }

    private String mapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"").append(escapeJson(entry.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, String> jsonToMap(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isEmpty() || json.equals("{}")) return map;
        // 简单 JSON 解析
        json = json.substring(1, json.length() - 1); // 移除 {}
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                String key = unescapeJson(kv[0].trim());
                String value = unescapeJson(kv[1].trim());
                map.put(key, value);
            }
        }
        return map;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        s = s.replace("\"", "");
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    // ==================== Diff Helpers ====================

    public void insertSymbols(List<Symbol> symbols) throws SQLException {
        if (symbols.isEmpty()) return;
        String sql = """
            INSERT INTO symbols (file_path, start_line, end_line, kind, name, qualified_name,
                signature, return_type, parent_class, modifiers, javadoc, super_class, interfaces,
                is_data_class, is_object, is_sealed, is_companion, is_trait, is_case_class)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (Symbol s : symbols) {
                ps.setString(1, s.filePath());
                ps.setInt(2, s.startLine());
                ps.setInt(3, s.endLine());
                ps.setString(4, s.kind().name());
                ps.setString(5, s.name());
                ps.setString(6, s.qualifiedName());
                ps.setString(7, s.signature());
                ps.setString(8, s.returnType());
                ps.setString(9, s.parentClass());
                ps.setInt(10, s.modifiers());
                ps.setString(11, s.javadoc());
                ps.setString(12, s.superClass());
                ps.setString(13, s.interfaces() != null ? s.interfaces().toString() : null);
                ps.setInt(14, s.isDataClass() ? 1 : 0);
                ps.setInt(15, s.isObject() ? 1 : 0);
                ps.setInt(16, s.isSealed() ? 1 : 0);
                ps.setInt(17, s.isCompanion() ? 1 : 0);
                ps.setInt(18, s.isTrait() ? 1 : 0);
                ps.setInt(19, s.isCaseClass() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                int i = 0;
                while (rs.next() && i < symbols.size()) {
                    Symbol old = symbols.get(i);
                    symbols.set(i, new Symbol(rs.getLong(1), old.filePath(), old.startLine(), old.endLine(),
                        old.kind(), old.name(), old.qualifiedName(), old.signature(), old.returnType(),
                        old.parentClass(), old.modifiers(), old.javadoc(), old.superClass(), old.interfaces(),
                        old.isDataClass(), old.isObject(), old.isSealed(), old.isCompanion(),
                        old.isTrait(), old.isCaseClass()));
                    i++;
                }
            }
        }
    }

    public void insertChunks(List<Chunk> chunks) throws SQLException {
        if (chunks.isEmpty()) return;
        String sql = """
            INSERT INTO chunks (file_path, type, start_line, end_line, name, content,
                package_name, class_name, signature)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (Chunk c : chunks) {
                ps.setString(1, c.filePath());
                ps.setString(2, c.type().name());
                ps.setInt(3, c.startLine());
                ps.setInt(4, c.endLine());
                ps.setString(5, c.name());
                ps.setString(6, c.content());
                ps.setString(7, c.packageName());
                ps.setString(8, c.className());
                ps.setString(9, c.signature());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void deleteSymbolsByIds(List<Long> ids) throws SQLException {
        if (ids.isEmpty()) return;
        StringBuilder sql = new StringBuilder("DELETE FROM symbols WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            ps.executeUpdate();
        }
    }

    public void deleteChunksByIds(List<Long> ids) throws SQLException {
        if (ids.isEmpty()) return;
        StringBuilder sql = new StringBuilder("DELETE FROM chunks WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            ps.executeUpdate();
        }
    }

    public void deleteReferencesBySymbolIds(List<Long> symbolIds) throws SQLException {
        if (symbolIds.isEmpty()) return;
        StringBuilder sql = new StringBuilder("DELETE FROM code_references WHERE symbol_id IN (");
        for (int i = 0; i < symbolIds.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < symbolIds.size(); i++) {
                ps.setLong(i + 1, symbolIds.get(i));
            }
            ps.executeUpdate();
        }
    }

    public void deleteConfigEntriesByKeys(String filePath, List<String> keys) throws SQLException {
        if (keys.isEmpty()) return;
        StringBuilder sql = new StringBuilder("DELETE FROM config_entries WHERE file_path = ? AND key IN (");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
            ps.setString(1, filePath);
            for (int i = 0; i < keys.size(); i++) {
                ps.setString(i + 2, keys.get(i));
            }
            ps.executeUpdate();
        }
    }

    public void deleteDependenciesByKeys(String filePath, List<String> keys) throws SQLException {
        if (keys.isEmpty()) return;
        StringBuilder sql = new StringBuilder(
            "DELETE FROM dependencies WHERE file_path = ? AND (group_id || ':' || artifact_id) IN (");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
            ps.setString(1, filePath);
            for (int i = 0; i < keys.size(); i++) {
                ps.setString(i + 2, keys.get(i));
            }
            ps.executeUpdate();
        }
    }

    /**
     * 获取项目统计信息
     */
    public int[] getProjectStats() throws SQLException {
        int[] stats = new int[7]; // symbols, references, calls, chunks, files, configs, dependencies
        try (Statement stmt = db.getConnection().createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM symbols")) {
                stats[0] = rs.next() ? rs.getInt(1) : 0;
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM code_references")) {
                stats[1] = rs.next() ? rs.getInt(1) : 0;
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM calls")) {
                stats[2] = rs.next() ? rs.getInt(1) : 0;
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM chunks")) {
                stats[3] = rs.next() ? rs.getInt(1) : 0;
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM file_meta")) {
                stats[4] = rs.next() ? rs.getInt(1) : 0;
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM config_entries")) {
                stats[5] = rs.next() ? rs.getInt(1) : 0;
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM dependencies")) {
                stats[6] = rs.next() ? rs.getInt(1) : 0;
            }
        }
        return stats;
    }

    /**
     * 获取详细统计信息
     */
    public java.util.Map<String, Object> getDetailedStats() throws SQLException {
        var stats = new java.util.LinkedHashMap<String, Object>();

        // 基本统计
        int[] basic = getProjectStats();
        stats.put("symbols", basic[0]);
        stats.put("references", basic[1]);
        stats.put("calls", basic[2]);
        stats.put("chunks", basic[3]);
        stats.put("files", basic[4]);
        stats.put("config_entries", basic[5]);
        stats.put("dependencies", basic[6]);

        // 符号按类型统计
        try (Statement stmt = db.getConnection().createStatement()) {
            var rs = stmt.executeQuery("SELECT kind, COUNT(*) as cnt FROM symbols GROUP BY kind ORDER BY cnt DESC");
            var symbolKinds = new java.util.LinkedHashMap<String, Integer>();
            while (rs.next()) {
                symbolKinds.put(rs.getString("kind"), rs.getInt("cnt"));
            }
            stats.put("symbols_by_kind", symbolKinds);
        }

        // 代码块按类型统计
        try (Statement stmt = db.getConnection().createStatement()) {
            var rs = stmt.executeQuery("SELECT type, COUNT(*) as cnt FROM chunks GROUP BY type ORDER BY cnt DESC");
            var chunkTypes = new java.util.LinkedHashMap<String, Integer>();
            while (rs.next()) {
                chunkTypes.put(rs.getString("type"), rs.getInt("cnt"));
            }
            stats.put("chunks_by_type", chunkTypes);
        }

        // 文件按扩展名统计
        try (Statement stmt = db.getConnection().createStatement()) {
            var rs = stmt.executeQuery("""
                SELECT
                    CASE
                        WHEN file_path LIKE '%.java' THEN 'java'
                        WHEN file_path LIKE '%.yml' OR file_path LIKE '%.yaml' THEN 'yaml'
                        WHEN file_path LIKE '%.properties' THEN 'properties'
                        WHEN file_path LIKE '%.xml' THEN 'xml'
                        WHEN file_path LIKE '%.gradle' OR file_path LIKE '%.gradle.kts' THEN 'gradle'
                        WHEN file_path LIKE '%.env' THEN 'env'
                        ELSE 'other'
                    END as file_type,
                    COUNT(*) as cnt
                FROM file_meta
                GROUP BY file_type
                ORDER BY cnt DESC
                """);
            var fileTypes = new java.util.LinkedHashMap<String, Integer>();
            while (rs.next()) {
                fileTypes.put(rs.getString("file_type"), rs.getInt("cnt"));
            }
            stats.put("files_by_type", fileTypes);
        }

        // 依赖按类型统计
        try (Statement stmt = db.getConnection().createStatement()) {
            var rs = stmt.executeQuery("SELECT dep_type, COUNT(*) as cnt FROM dependencies GROUP BY dep_type ORDER BY cnt DESC");
            var depTypes = new java.util.LinkedHashMap<String, Integer>();
            while (rs.next()) {
                depTypes.put(rs.getString("dep_type"), rs.getInt("cnt"));
            }
            stats.put("dependencies_by_type", depTypes);
        }

        // Top 5 文件（按符号数）
        try (Statement stmt = db.getConnection().createStatement()) {
            var rs = stmt.executeQuery("""
                SELECT file_path, COUNT(*) as symbol_count
                FROM symbols
                GROUP BY file_path
                ORDER BY symbol_count DESC
                LIMIT 5
                """);
            var topFiles = new java.util.ArrayList<Map<String, Object>>();
            while (rs.next()) {
                var fileStat = new java.util.LinkedHashMap<String, Object>();
                fileStat.put("file", rs.getString("file_path"));
                fileStat.put("symbols", rs.getInt("symbol_count"));
                topFiles.add(fileStat);
            }
            stats.put("top_files", topFiles);
        }

        // 代码总行数
        try (Statement stmt = db.getConnection().createStatement()) {
            var rs = stmt.executeQuery("SELECT SUM(end_line - start_line + 1) as total_lines FROM chunks");
            if (rs.next()) {
                stats.put("total_code_lines", rs.getInt("total_lines"));
            }
        }

        // 平均每文件符号数
        try (Statement stmt = db.getConnection().createStatement()) {
            var rs = stmt.executeQuery("SELECT AVG(symbol_count) as avg FROM file_meta");
            if (rs.next()) {
                stats.put("avg_symbols_per_file", Math.round(rs.getDouble("avg") * 10.0) / 10.0);
            }
        }

        // 最近索引时间
        try (Statement stmt = db.getConnection().createStatement()) {
            var rs = stmt.executeQuery("SELECT MAX(last_indexed_at) as last_indexed FROM file_meta");
            if (rs.next()) {
                long lastIndexed = rs.getLong("last_indexed");
                if (lastIndexed > 0) {
                    stats.put("last_indexed_at", lastIndexed);
                    stats.put("last_indexed_ago_ms", System.currentTimeMillis() - lastIndexed);
                }
            }
        }

        return stats;
    }

    /**
     * 导出所有索引数据为 Map
     */
    public java.util.Map<String, Object> exportAll() throws SQLException {
        var data = new java.util.LinkedHashMap<String, Object>();

        // 导出符号
        data.put("symbols", listAllSymbols(100000));

        // 导出引用
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, symbol_id, from_file, from_line, context FROM code_references")) {
            var refs = new java.util.ArrayList<Reference>();
            while (rs.next()) {
                refs.add(new Reference(rs.getLong("id"), rs.getLong("symbol_id"),
                    rs.getString("from_file"), rs.getInt("from_line"), rs.getString("context")));
            }
            data.put("references", refs);
        }

        // 导出调用关系
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, caller_method, caller_file, caller_line, callee_method, callee_file FROM calls")) {
            var calls = new java.util.ArrayList<Call>();
            while (rs.next()) {
                calls.add(new Call(rs.getLong("id"), rs.getString("caller_method"),
                    rs.getString("caller_file"), rs.getInt("caller_line"),
                    rs.getString("callee_method"), rs.getString("callee_file")));
            }
            data.put("calls", calls);
        }

        // 导出代码块
        data.put("chunks", listAllChunks(100000));

        // 导出配置
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, file_path, line, key, value, config_type, content FROM config_entries")) {
            var configs = new java.util.ArrayList<ConfigEntry>();
            while (rs.next()) {
                configs.add(new ConfigEntry(rs.getLong("id"), rs.getString("file_path"),
                    rs.getInt("line"), rs.getString("key"), rs.getString("value"),
                    ConfigEntry.ConfigType.valueOf(rs.getString("config_type")), rs.getString("content")));
            }
            data.put("config_entries", configs);
        }

        // 导出依赖
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, file_path, line, group_id, artifact_id, version, scope, dep_type, classifier FROM dependencies")) {
            var deps = new java.util.ArrayList<Dependency>();
            while (rs.next()) {
                deps.add(new Dependency(rs.getLong("id"), rs.getString("file_path"),
                    rs.getInt("line"), rs.getString("group_id"), rs.getString("artifact_id"),
                    rs.getString("version"), rs.getString("scope"),
                    Dependency.DepType.valueOf(rs.getString("dep_type")), rs.getString("classifier")));
            }
            data.put("dependencies", deps);
        }

        return data;
    }

    // ==================== Mapping Helpers ====================

    private List<Symbol> mapSymbols(PreparedStatement ps) throws SQLException {
        List<Symbol> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                // 解析 interfaces JSON 数组
                String interfacesStr = rs.getString("interfaces");
                List<String> interfaces = null;
                if (interfacesStr != null && !interfacesStr.isEmpty()) {
                    interfaces = new ArrayList<>();
                    // 移除方括号并分割
                    String cleaned = interfacesStr.replaceAll("[\\[\\]\\s]", "");
                    if (!cleaned.isEmpty()) {
                        for (String s : cleaned.split(",")) {
                            interfaces.add(s.trim());
                        }
                    }
                }

                list.add(new Symbol(
                    rs.getLong("id"),
                    rs.getString("file_path"),
                    rs.getInt("start_line"),
                    rs.getInt("end_line"),
                    Symbol.SymbolKind.valueOf(rs.getString("kind")),
                    rs.getString("name"),
                    rs.getString("qualified_name"),
                    rs.getString("signature"),
                    rs.getString("return_type"),
                    rs.getString("parent_class"),
                    rs.getInt("modifiers"),
                    rs.getString("javadoc"),
                    rs.getString("super_class"),
                    interfaces,
                    rs.getInt("is_data_class") == 1,
                    rs.getInt("is_object") == 1,
                    rs.getInt("is_sealed") == 1,
                    rs.getInt("is_companion") == 1,
                    rs.getInt("is_trait") == 1,
                    rs.getInt("is_case_class") == 1
                ));
            }
        }
        return list;
    }

    private List<Reference> mapReferences(PreparedStatement ps) throws SQLException {
        List<Reference> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Reference(
                    rs.getLong("id"),
                    rs.getLong("symbol_id"),
                    rs.getString("from_file"),
                    rs.getInt("from_line"),
                    rs.getString("context")
                ));
            }
        }
        return list;
    }

    private List<Call> mapCalls(PreparedStatement ps) throws SQLException {
        List<Call> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Call(
                    rs.getLong("id"),
                    rs.getString("caller_method"),
                    rs.getString("caller_file"),
                    rs.getInt("caller_line"),
                    rs.getString("callee_method"),
                    rs.getString("callee_file")
                ));
            }
        }
        return list;
    }

    private List<Chunk> mapChunks(PreparedStatement ps) throws SQLException {
        List<Chunk> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Chunk(
                    rs.getLong("id"),
                    rs.getString("file_path"),
                    Chunk.ChunkType.valueOf(rs.getString("type")),
                    rs.getInt("start_line"),
                    rs.getInt("end_line"),
                    rs.getString("name"),
                    rs.getString("content"),
                    rs.getString("package_name"),
                    rs.getString("class_name"),
                    rs.getString("signature")
                ));
            }
        }
        return list;
    }

    private FileMeta mapFileMeta(ResultSet rs) throws SQLException {
        return new FileMeta(
            rs.getString("file_path"),
            rs.getLong("size"),
            rs.getLong("last_modified"),
            rs.getString("sha1"),
            rs.getInt("symbol_count"),
            rs.getLong("last_indexed_at")
        );
    }

    /**
     * 获取 chunk 内容
     */
    public Optional<Chunk> findChunkById(long chunkId) throws SQLException {
        String sql = """
            SELECT id, file_path, type, start_line, end_line, name,
                   content, package_name, class_name, signature
            FROM chunks WHERE id = ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, chunkId);
            List<Chunk> chunks = mapChunks(ps);
            return chunks.isEmpty() ? Optional.empty() : Optional.of(chunks.get(0));
        }
    }

    // ==================== API Routes ====================

    public void insertApiRoutes(List<ApiRoute> routes) throws SQLException {
        if (routes.isEmpty()) return;
        String sql = """
            INSERT INTO api_routes (symbol_id, http_method, path, base_path, method_path, file_path, start_line)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (ApiRoute r : routes) {
                ps.setLong(1, r.symbolId());
                ps.setString(2, r.httpMethod());
                ps.setString(3, r.path());
                ps.setString(4, r.basePath());
                ps.setString(5, r.methodPath());
                ps.setString(6, r.filePath());
                ps.setInt(7, r.startLine());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void deleteApiRoutesByFile(String filePath) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM api_routes WHERE file_path = ?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    public List<ApiRoute> searchApiRoutes(String query, String httpMethod, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT ar.id, ar.symbol_id, ar.http_method, ar.path, ar.base_path, ar.method_path,
                   ar.file_path, ar.start_line
            FROM api_routes ar
            WHERE (ar.path LIKE ? OR ar.base_path LIKE ?)
            """);
        if (httpMethod != null && !httpMethod.isEmpty()) {
            sql.append(" AND ar.http_method = ?");
        }
        sql.append(" ORDER BY ar.path LIMIT ?");

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
            String pattern = "%" + query + "%";
            int idx = 1;
            ps.setString(idx++, pattern);
            ps.setString(idx++, pattern);
            if (httpMethod != null && !httpMethod.isEmpty()) {
                ps.setString(idx++, httpMethod.toUpperCase());
            }
            ps.setInt(idx, limit);

            List<ApiRoute> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ApiRoute(
                        rs.getLong("id"),
                        rs.getLong("symbol_id"),
                        rs.getString("http_method"),
                        rs.getString("path"),
                        rs.getString("base_path"),
                        rs.getString("method_path"),
                        rs.getString("file_path"),
                        rs.getInt("start_line")
                    ));
                }
            }
            return list;
        }
    }

    // ==================== Bean Dependencies ====================

    public void insertBeanDependencies(List<BeanDependency> deps) throws SQLException {
        if (deps.isEmpty()) return;
        String sql = """
            INSERT INTO bean_dependencies (bean_symbol_id, depends_on_symbol_id, depends_on_type,
                injection_type, field_name, file_path, start_line)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (BeanDependency d : deps) {
                ps.setLong(1, d.beanSymbolId());
                if (d.dependsOnSymbolId() != null) {
                    ps.setLong(2, d.dependsOnSymbolId());
                } else {
                    ps.setNull(2, Types.BIGINT);
                }
                ps.setString(3, d.dependsOnType());
                ps.setString(4, d.injectionType());
                ps.setString(5, d.fieldName());
                ps.setString(6, d.filePath());
                ps.setInt(7, d.startLine());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void deleteBeanDependenciesByFile(String filePath) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM bean_dependencies WHERE file_path = ?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    public List<BeanDependency> findBeanDependencies(long beanSymbolId, int limit) throws SQLException {
        String sql = """
            SELECT id, bean_symbol_id, depends_on_symbol_id, depends_on_type,
                   injection_type, field_name, file_path, start_line
            FROM bean_dependencies
            WHERE bean_symbol_id = ?
            LIMIT ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, beanSymbolId);
            ps.setInt(2, limit);
            return mapBeanDependencies(ps);
        }
    }

    public List<BeanDependency> findBeanDependents(long dependsOnSymbolId, int limit) throws SQLException {
        String sql = """
            SELECT id, bean_symbol_id, depends_on_symbol_id, depends_on_type,
                   injection_type, field_name, file_path, start_line
            FROM bean_dependencies
            WHERE depends_on_symbol_id = ?
            LIMIT ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, dependsOnSymbolId);
            ps.setInt(2, limit);
            return mapBeanDependencies(ps);
        }
    }

    private List<BeanDependency> mapBeanDependencies(PreparedStatement ps) throws SQLException {
        List<BeanDependency> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long dependsOnId = rs.getLong("depends_on_symbol_id");
                list.add(new BeanDependency(
                    rs.getLong("id"),
                    rs.getLong("bean_symbol_id"),
                    rs.wasNull() ? null : dependsOnId,
                    rs.getString("depends_on_type"),
                    rs.getString("injection_type"),
                    rs.getString("field_name"),
                    rs.getString("file_path"),
                    rs.getInt("start_line")
                ));
            }
        }
        return list;
    }

    // ==================== Test Mappings ====================

    public void insertTestMappings(List<TestMapping> mappings) throws SQLException {
        if (mappings.isEmpty()) return;
        String sql = """
            INSERT INTO test_mappings (test_symbol_id, source_symbol_id, test_class_name,
                source_class_name, mapping_type, file_path)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (TestMapping m : mappings) {
                ps.setLong(1, m.testSymbolId());
                if (m.sourceSymbolId() != null) {
                    ps.setLong(2, m.sourceSymbolId());
                } else {
                    ps.setNull(2, Types.BIGINT);
                }
                ps.setString(3, m.testClassName());
                ps.setString(4, m.sourceClassName());
                ps.setString(5, m.mappingType());
                ps.setString(6, m.filePath());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void deleteTestMappingsByFile(String filePath) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM test_mappings WHERE file_path = ?")) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    public List<TestMapping> findTestMappingsBySource(String sourceClassName, int limit) throws SQLException {
        String sql = """
            SELECT id, test_symbol_id, source_symbol_id, test_class_name,
                   source_class_name, mapping_type, file_path
            FROM test_mappings
            WHERE source_class_name = ?
            LIMIT ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, sourceClassName);
            ps.setInt(2, limit);
            return mapTestMappings(ps);
        }
    }

    private List<TestMapping> mapTestMappings(PreparedStatement ps) throws SQLException {
        List<TestMapping> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long sourceId = rs.getLong("source_symbol_id");
                list.add(new TestMapping(
                    rs.getLong("id"),
                    rs.getLong("test_symbol_id"),
                    rs.wasNull() ? null : sourceId,
                    rs.getString("test_class_name"),
                    rs.getString("source_class_name"),
                    rs.getString("mapping_type"),
                    rs.getString("file_path")
                ));
            }
        }
        return list;
    }

    // ==================== Index Metadata ====================

    public void upsertIndexMetadata(String key, String value) throws SQLException {
        String sql = """
            INSERT INTO index_metadata (key, value, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET
                value = excluded.value,
                updated_at = excluded.updated_at
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public Optional<String> getIndexMetadata(String key) throws SQLException {
        String sql = "SELECT value FROM index_metadata WHERE key = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString("value")) : Optional.empty();
            }
        }
    }

    // ==================== Code Metrics ====================

    public void upsertCodeMetrics(CodeMetrics metrics) throws SQLException {
        // 先检查是否存在
        Optional<CodeMetrics> existing = findCodeMetricsByFile(metrics.filePath(), metrics.className());
        
        if (existing.isPresent()) {
            String sql = """
                UPDATE code_metrics SET
                    symbol_id = ?,
                    package_name = ?,
                    lines_of_code = ?,
                    method_count = ?,
                    field_count = ?,
                    complexity_estimate = ?,
                    updated_at = ?
                WHERE file_path = ? AND class_name = ?
                """;
            try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
                if (metrics.symbolId() != null) {
                    ps.setLong(1, metrics.symbolId());
                } else {
                    ps.setNull(1, Types.BIGINT);
                }
                ps.setString(2, metrics.packageName());
                ps.setInt(3, metrics.linesOfCode());
                ps.setInt(4, metrics.methodCount());
                ps.setInt(5, metrics.fieldCount());
                ps.setInt(6, metrics.complexityEstimate());
                ps.setLong(7, System.currentTimeMillis());
                ps.setString(8, metrics.filePath());
                ps.setString(9, metrics.className());
                ps.executeUpdate();
            }
        } else {
            String sql = """
                INSERT INTO code_metrics (symbol_id, file_path, class_name, package_name,
                    lines_of_code, method_count, field_count, complexity_estimate, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
                if (metrics.symbolId() != null) {
                    ps.setLong(1, metrics.symbolId());
                } else {
                    ps.setNull(1, Types.BIGINT);
                }
                ps.setString(2, metrics.filePath());
                ps.setString(3, metrics.className());
                ps.setString(4, metrics.packageName());
                ps.setInt(5, metrics.linesOfCode());
                ps.setInt(6, metrics.methodCount());
                ps.setInt(7, metrics.fieldCount());
                ps.setInt(8, metrics.complexityEstimate());
                ps.setLong(9, System.currentTimeMillis());
                ps.executeUpdate();
            }
        }
    }

    public Optional<CodeMetrics> findCodeMetricsByFile(String filePath, String className) throws SQLException {
        String sql = """
            SELECT id, symbol_id, file_path, class_name, package_name,
                   lines_of_code, method_count, field_count, complexity_estimate, updated_at
            FROM code_metrics
            WHERE file_path = ? AND class_name = ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, filePath);
            ps.setString(2, className);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long symbolId = rs.getLong("symbol_id");
                    return Optional.of(new CodeMetrics(
                        rs.getLong("id"),
                        rs.wasNull() ? null : symbolId,
                        rs.getString("file_path"),
                        rs.getString("class_name"),
                        rs.getString("package_name"),
                        rs.getInt("lines_of_code"),
                        rs.getInt("method_count"),
                        rs.getInt("field_count"),
                        rs.getInt("complexity_estimate"),
                        rs.getLong("updated_at")
                    ));
                }
                return Optional.empty();
            }
        }
    }

    public List<CodeMetrics> findCodeMetricsByPackageName(String packageName, int limit) throws SQLException {
        String sql = """
            SELECT id, symbol_id, file_path, class_name, package_name,
                   lines_of_code, method_count, field_count, complexity_estimate, updated_at
            FROM code_metrics
            WHERE package_name = ?
            ORDER BY lines_of_code DESC
            LIMIT ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, packageName);
            ps.setInt(2, limit);
            List<CodeMetrics> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long symbolId = rs.getLong("symbol_id");
                    list.add(new CodeMetrics(
                        rs.getLong("id"),
                        rs.wasNull() ? null : symbolId,
                        rs.getString("file_path"),
                        rs.getString("class_name"),
                        rs.getString("package_name"),
                        rs.getInt("lines_of_code"),
                        rs.getInt("method_count"),
                        rs.getInt("field_count"),
                        rs.getInt("complexity_estimate"),
                        rs.getLong("updated_at")
                    ));
                }
            }
            return list;
        }
    }

    @Override
    public void close() {
        // DatabaseManager 的 close 由外部管理
    }
}
