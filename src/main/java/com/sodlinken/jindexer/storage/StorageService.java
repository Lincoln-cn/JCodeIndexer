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

    public StorageService(DatabaseManager db) {
        this.db = db;
    }

    // ==================== Symbols ====================

    public long insertSymbol(Symbol s) throws SQLException {
        String sql = """
            INSERT INTO symbols (file_path, start_line, end_line, kind, name, qualified_name,
                signature, return_type, parent_class, modifiers, javadoc)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public List<Symbol> searchSymbolsByName(String query, int limit) throws SQLException {
        String sql = """
            SELECT id, file_path, start_line, end_line, kind, name, qualified_name,
                   signature, return_type, parent_class, modifiers, javadoc
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
            """;
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
            SELECT id, file_path, start_line, end_line, kind, name, qualified_name,
                   signature, return_type, parent_class, modifiers, javadoc
            FROM symbols
            ORDER BY qualified_name
            LIMIT ?
            """;
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
            SELECT id, file_path, start_line, end_line, kind, name, qualified_name,
                   signature, return_type, parent_class, modifiers, javadoc
            FROM symbols WHERE qualified_name = ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, qualifiedName);
            List<Symbol> results = mapSymbols(ps);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
        }
    }

    public Optional<Symbol> findSymbolById(long id) throws SQLException {
        String sql = """
            SELECT id, file_path, start_line, end_line, kind, name, qualified_name,
                   signature, return_type, parent_class, modifiers, javadoc
            FROM symbols WHERE id = ?
            """;
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
            SELECT id, file_path, start_line, end_line, kind, name, qualified_name,
                   signature, return_type, parent_class, modifiers, javadoc
            FROM symbols WHERE file_path = ? ORDER BY start_line
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, filePath);
            return mapSymbols(ps);
        }
    }

    public List<Symbol> findSymbolsByKind(Symbol.SymbolKind kind) throws SQLException {
        String sql = """
            SELECT id, file_path, start_line, end_line, kind, name, qualified_name,
                   signature, return_type, parent_class, modifiers, javadoc
            FROM symbols WHERE kind = ? ORDER BY qualified_name
            """;
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
    }

    // ==================== Diff Helpers ====================

    public void insertSymbols(List<Symbol> symbols) throws SQLException {
        if (symbols.isEmpty()) return;
        String sql = """
            INSERT INTO symbols (file_path, start_line, end_line, kind, name, qualified_name,
                signature, return_type, parent_class, modifiers, javadoc)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                int i = 0;
                while (rs.next() && i < symbols.size()) {
                    Symbol old = symbols.get(i);
                    symbols.set(i, new Symbol(rs.getLong(1), old.filePath(), old.startLine(), old.endLine(),
                        old.kind(), old.name(), old.qualifiedName(), old.signature(), old.returnType(),
                        old.parentClass(), old.modifiers(), old.javadoc()));
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

        return stats;
    }

    // ==================== Mapping Helpers ====================

    private List<Symbol> mapSymbols(PreparedStatement ps) throws SQLException {
        List<Symbol> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
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
                    rs.getString("javadoc")
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

    @Override
    public void close() {
        // DatabaseManager 的 close 由外部管理
    }
}
