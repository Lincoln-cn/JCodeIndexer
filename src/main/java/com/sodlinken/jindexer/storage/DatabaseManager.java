package com.sodlinken.jindexer.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SQLite 连接管理器
 */
public class DatabaseManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final String dbUrl;
    private volatile Connection connection;

    public DatabaseManager(Path dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            throw new RuntimeException("无法创建数据目录: " + dbPath.getParent(), e);
        }
    }

    /**
     * 初始化数据库：建立连接 + 执行 DDL + 完整性检查
     */
    public synchronized void initialize() throws SQLException {
        if (connection != null) {
            try {
                if (!connection.isClosed()) return;
            } catch (SQLException e) {
                log.warn("检查连接状态失败，重新初始化", e);
            }
        }

        log.info("初始化数据库: {}", dbUrl);
        try {
            connection = DriverManager.getConnection(dbUrl);
        } catch (SQLException e) {
            // 数据库可能损坏，尝试删除重建
            log.warn("数据库连接失败，尝试删除重建: {}", e.getMessage());
            try {
                if (connection != null) connection.close();
            } catch (SQLException ignored) {}
            connection = null;

            // 删除损坏的数据库文件
            try {
                String dbPathStr = dbUrl.substring("jdbc:sqlite:".length());
                Files.deleteIfExists(Path.of(dbPathStr));
                Files.deleteIfExists(Path.of(dbPathStr + "-wal"));
                Files.deleteIfExists(Path.of(dbPathStr + "-shm"));
                log.info("已删除损坏的数据库文件");
            } catch (Exception ignored) {}

            // 重新连接
            connection = DriverManager.getConnection(dbUrl);
        }

        // PRAGMA 必须在事务外执行
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA busy_timeout=5000");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA cache_size=-64000");
            stmt.execute("PRAGMA mmap_size=268435456");
        }

        // 完整性检查
        try (Statement stmt = connection.createStatement()) {
            var rs = stmt.executeQuery("PRAGMA integrity_check");
            if (rs.next()) {
                String result = rs.getString(1);
                if (!"ok".equals(result)) {
                    log.warn("数据库完整性检查异常: {}", result);
                }
            }
        }

        // DDL 在事务内执行
        connection.setAutoCommit(false);
        try (Statement stmt = connection.createStatement()) {
            for (String ddl : Schema.allDDL()) {
                if (!ddl.startsWith("PRAGMA")) {
                    stmt.execute(ddl);
                }
            }
            connection.commit();
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }

        // 执行数据库迁移（v1.0.1: 添加继承关系字段）
        runMigrations();

        log.info("数据库初始化完成");
    }

    /**
     * 执行数据库迁移
     * ALTER TABLE 语句会自动忽略已存在的字段
     */
    private void runMigrations() {
        try (Statement stmt = connection.createStatement()) {
            // v1.0.1: 添加继承关系字段
            for (String sql : Schema.migrationV1_0_1()) {
                try {
                    stmt.execute(sql);
                    log.debug("迁移执行成功: {}", sql);
                } catch (SQLException e) {
                    // 字段已存在，忽略
                    if (!e.getMessage().contains("already exists")) {
                        log.warn("迁移执行失败: {}", sql, e);
                    }
                }
            }
            // v1.1.1: 添加 Kotlin 特有字段
            for (String sql : Schema.migrationV1_1_1()) {
                try {
                    stmt.execute(sql);
                    log.debug("迁移执行成功: {}", sql);
                } catch (SQLException e) {
                    // 字段已存在，忽略
                    if (!e.getMessage().contains("already exists")) {
                        log.warn("迁移执行失败: {}", sql, e);
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("迁移过程出错", e);
        }
    }

    /**
     * 获取当前连接（单连接模式）
     */
    public Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException("数据库未初始化，请先调用 initialize()");
        }
        try {
            if (connection.isClosed()) {
                throw new IllegalStateException("数据库连接已关闭，请重新调用 initialize()");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("检查数据库连接状态失败", e);
        }
        return connection;
    }

    /**
     * 在事务中执行操作（synchronized，SQLite 单连接不支持并发写）
     */
    public synchronized <T> T executeInTransaction(TransactionAction<T> action) throws Exception {
        Connection conn = getConnection();
        boolean origAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            T result = action.execute(conn);
            conn.commit();
            return result;
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                log.error("事务回滚失败", rollbackEx);
                e.addSuppressed(rollbackEx);
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(origAutoCommit);
            } catch (SQLException e) {
                log.error("恢复 autoCommit 失败", e);
            }
        }
    }

    /**
     * 执行无返回值的事务操作
     */
    public void executeInTransactionVoid(TransactionAction<Void> action) throws Exception {
        executeInTransaction(action);
    }

    @FunctionalInterface
    public interface TransactionAction<T> {
        T execute(Connection conn) throws Exception;
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.info("数据库连接已关闭");
            } catch (SQLException e) {
                log.error("关闭数据库连接失败", e);
            }
            connection = null;
        }
    }
}
