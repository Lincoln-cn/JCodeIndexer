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
     * 初始化数据库：建立连接 + 执行 DDL
     */
    public synchronized void initialize() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        log.info("初始化数据库: {}", dbUrl);
        connection = DriverManager.getConnection(dbUrl);

        // PRAGMA 必须在事务外执行
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA busy_timeout=5000");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA cache_size=-64000");
            stmt.execute("PRAGMA mmap_size=268435456");
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
        }
        log.info("数据库初始化完成");
    }

    /**
     * 获取当前连接（单连接模式）
     */
    public Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException("数据库未初始化，请先调用 initialize()");
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
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(origAutoCommit);
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
        }
    }
}
