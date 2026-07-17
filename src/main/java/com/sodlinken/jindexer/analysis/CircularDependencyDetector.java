package com.sodlinken.jindexer.analysis;

import com.sodlinken.jindexer.storage.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 循环依赖检测器
 * 构建有向图，使用 DFS 检测环
 */
public class CircularDependencyDetector {

    private final DatabaseManager db;

    public CircularDependencyDetector(DatabaseManager db) {
        this.db = db;
    }

    /**
     * 检测循环依赖
     * @return 所有循环依赖路径
     */
    public List<Cycle> detect() throws SQLException {
        Map<String, Set<String>> graph = buildDependencyGraph();
        List<Cycle> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                dfs(node, graph, visited, recursionStack, new ArrayList<>(), cycles);
            }
        }
        return cycles;
    }

    /**
     * 检测指定类的循环依赖
     */
    public List<Cycle> detectForClass(String className) throws SQLException {
        Map<String, Set<String>> graph = buildDependencyGraph();
        List<Cycle> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        if (graph.containsKey(className)) {
            dfs(className, graph, visited, recursionStack, new ArrayList<>(), cycles);
        }
        return cycles;
    }

    private void dfs(String node, Map<String, Set<String>> graph,
                     Set<String> visited, Set<String> recursionStack,
                     List<String> path, List<Cycle> cycles) {
        visited.add(node);
        recursionStack.add(node);
        path.add(node);

        for (String neighbor : graph.getOrDefault(node, Set.of())) {
            if (!visited.contains(neighbor)) {
                dfs(neighbor, graph, visited, recursionStack, path, cycles);
            } else if (recursionStack.contains(neighbor)) {
                int idx = path.indexOf(neighbor);
                if (idx >= 0) {
                    List<String> cyclePath = new ArrayList<>(path.subList(idx, path.size()));
                    cyclePath.add(neighbor);
                    cycles.add(new Cycle(cyclePath));
                }
            }
        }

        path.remove(path.size() - 1);
        recursionStack.remove(node);
    }

    private Map<String, Set<String>> buildDependencyGraph() throws SQLException {
        Map<String, Set<String>> graph = new HashMap<>();

        // 从 bean_dependencies 表构建
        String sql = """
            SELECT s1.qualified_name as source, s2.qualified_name as target
            FROM bean_dependencies bd
            JOIN symbols s1 ON bd.bean_symbol_id = s1.id
            LEFT JOIN symbols s2 ON bd.depends_on_symbol_id = s2.id
            WHERE s2.qualified_name IS NOT NULL
            """;

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String source = rs.getString("source");
                String target = rs.getString("target");
                if (source != null && target != null) {
                    graph.computeIfAbsent(source, k -> new HashSet<>()).add(target);
                }
            }
        }

        return graph;
    }

    /**
     * 循环依赖路径
     */
    public record Cycle(List<String> path) {}
}
