package com.sodlinken.jindexer.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;

    @BeforeEach
    void setUp() {
        db = new DatabaseManager(tempDir.resolve("test.db"));
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void initializeCreatesDatabase() throws SQLException {
        db.initialize();
        Connection conn = db.getConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
    }

    @Test
    void initializeIdempotent() throws SQLException {
        db.initialize();
        db.initialize(); // Second call should not fail
        Connection conn = db.getConnection();
        assertNotNull(conn);
    }

    @Test
    void getConnectionBeforeInitializeThrows() {
        assertThrows(IllegalStateException.class, () -> db.getConnection());
    }

    @Test
    void closeThenGetConnectionThrows() throws SQLException {
        db.initialize();
        db.close();
        assertThrows(IllegalStateException.class, () -> db.getConnection());
    }

    @Test
    void executeInTransactionSuccess() throws Exception {
        db.initialize();

        String result = db.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT 'hello'")) {
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? rs.getString(1) : null;
            }
        });

        assertEquals("hello", result);
    }

    @Test
    void executeInTransactionRollback() throws Exception {
        db.initialize();

        // Insert a row
        db.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO symbols (file_path, start_line, end_line, kind, name, qualified_name) VALUES (?, ?, ?, ?, ?, ?)")) {
                stmt.setString(1, "test.java");
                stmt.setInt(2, 1);
                stmt.setInt(3, 10);
                stmt.setString(4, "CLASS");
                stmt.setString(5, "Test");
                stmt.setString(6, "com.Test");
                stmt.executeUpdate();
            }
            return null;
        });

        // Verify inserted
        int count = db.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM symbols")) {
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
        assertEquals(1, count);

        // Transaction that throws should rollback
        assertThrows(Exception.class, () -> db.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO symbols (file_path, start_line, end_line, kind, name, qualified_name) VALUES (?, ?, ?, ?, ?, ?)")) {
                stmt.setString(1, "test2.java");
                stmt.setInt(2, 1);
                stmt.setInt(3, 10);
                stmt.setString(4, "CLASS");
                stmt.setString(5, "Test2");
                stmt.setString(6, "com.Test2");
                stmt.executeUpdate();
            }
            throw new RuntimeException("Intentional failure");
        }));

        // Verify rollback - still only 1 row
        count = db.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM symbols")) {
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
        assertEquals(1, count);
    }

    @Test
    void executeInTransactionVoid() throws Exception {
        db.initialize();

        db.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO symbols (file_path, start_line, end_line, kind, name, qualified_name) VALUES (?, ?, ?, ?, ?, ?)")) {
                stmt.setString(1, "test.java");
                stmt.setInt(2, 1);
                stmt.setInt(3, 10);
                stmt.setString(4, "CLASS");
                stmt.setString(5, "Test");
                stmt.setString(6, "com.Test");
                stmt.executeUpdate();
            }
            return null;
        });

        int count = db.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM symbols")) {
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
        assertEquals(1, count);
    }

    @Test
    void initializeCreatesSchema() throws SQLException {
        db.initialize();
        Connection conn = db.getConnection();

        // Verify tables exist
        String[] tables = {"symbols", "code_references", "calls", "chunks", "config_entries", "dependencies", "file_meta"};
        for (String table : tables) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
                stmt.setString(1, table);
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next(), "Table " + table + " should exist");
            }
        }
    }

    @Test
    void initializeSetsPragmas() throws SQLException {
        db.initialize();
        Connection conn = db.getConnection();

        // Check WAL mode
        try (PreparedStatement stmt = conn.prepareStatement("PRAGMA journal_mode")) {
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next());
            assertEquals("wal", rs.getString(1));
        }

        // Check foreign keys
        try (PreparedStatement stmt = conn.prepareStatement("PRAGMA foreign_keys")) {
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void closeIsIdempotent() throws SQLException {
        db.initialize();
        db.close();
        db.close(); // Should not throw
    }
}
