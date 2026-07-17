package com.sodlinken.jindexer.storage;

/**
 * SQLite Schema DDL 定义
 */
public final class Schema {

    private Schema() {}

    public static final String CREATE_SYMBOLS = """
        CREATE TABLE IF NOT EXISTS symbols (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            file_path TEXT NOT NULL,
            start_line INTEGER NOT NULL,
            end_line INTEGER NOT NULL,
            kind TEXT NOT NULL CHECK(kind IN ('CLASS','METHOD','FIELD')),
            name TEXT NOT NULL,
            qualified_name TEXT NOT NULL,
            signature TEXT,
            return_type TEXT,
            parent_class TEXT,
            modifiers INTEGER DEFAULT 0,
            javadoc TEXT
        )
        """;

    public static final String CREATE_CODE_REFERENCES = """
        CREATE TABLE IF NOT EXISTS code_references (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            symbol_id INTEGER NOT NULL,
            from_file TEXT NOT NULL,
            from_line INTEGER NOT NULL,
            context TEXT,
            FOREIGN KEY (symbol_id) REFERENCES symbols(id) ON DELETE CASCADE
        )
        """;

    public static final String CREATE_CALLS = """
        CREATE TABLE IF NOT EXISTS calls (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            caller_method TEXT NOT NULL,
            caller_file TEXT NOT NULL,
            caller_line INTEGER NOT NULL,
            callee_method TEXT NOT NULL,
            callee_file TEXT
        )
        """;

    public static final String CREATE_CHUNKS = """
        CREATE TABLE IF NOT EXISTS chunks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            file_path TEXT NOT NULL,
            type TEXT NOT NULL CHECK(type IN ('CLASS','METHOD','ANNOTATION','FILE_HEADER')),
            start_line INTEGER NOT NULL,
            end_line INTEGER NOT NULL,
            name TEXT,
            content TEXT NOT NULL,
            package_name TEXT,
            class_name TEXT,
            signature TEXT
        )
        """;

    public static final String CREATE_FILE_META = """
        CREATE TABLE IF NOT EXISTS file_meta (
            file_path TEXT PRIMARY KEY,
            size INTEGER NOT NULL,
            last_modified INTEGER NOT NULL,
            sha1 TEXT NOT NULL,
            symbol_count INTEGER DEFAULT 0,
            last_indexed_at INTEGER NOT NULL
        )
        """;

    public static final String CREATE_CONFIG_ENTRIES = """
        CREATE TABLE IF NOT EXISTS config_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            file_path TEXT NOT NULL,
            line INTEGER NOT NULL,
            key TEXT NOT NULL,
            value TEXT,
            config_type TEXT NOT NULL CHECK(config_type IN ('YAML','PROPERTIES','ENV')),
            content TEXT
        )
        """;

    public static final String CREATE_DEPENDENCIES = """
        CREATE TABLE IF NOT EXISTS dependencies (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            file_path TEXT NOT NULL,
            line INTEGER NOT NULL,
            group_id TEXT,
            artifact_id TEXT NOT NULL,
            version TEXT,
            scope TEXT,
            dep_type TEXT NOT NULL CHECK(dep_type IN ('POM','GRADLE','PACKAGE_JSON')),
            classifier TEXT
        )
        """;

    // 索引
    public static final String CREATE_INDEX_SYMBOLS_QUALIFIED =
        "CREATE INDEX IF NOT EXISTS idx_symbols_qualified ON symbols(qualified_name)";

    public static final String CREATE_INDEX_SYMBOLS_KIND =
        "CREATE INDEX IF NOT EXISTS idx_symbols_kind ON symbols(kind)";

    public static final String CREATE_INDEX_SYMBOLS_FILE =
        "CREATE INDEX IF NOT EXISTS idx_symbols_file ON symbols(file_path)";

    public static final String CREATE_INDEX_CODE_REFERENCES_SYMBOL =
        "CREATE INDEX IF NOT EXISTS idx_code_references_symbol ON code_references(symbol_id)";

    public static final String CREATE_INDEX_CODE_REFERENCES_FILE =
        "CREATE INDEX IF NOT EXISTS idx_code_references_file ON code_references(from_file)";

    public static final String CREATE_INDEX_CALLS_CALLER =
        "CREATE INDEX IF NOT EXISTS idx_calls_caller ON calls(caller_method)";

    public static final String CREATE_INDEX_CALLS_CALLEE =
        "CREATE INDEX IF NOT EXISTS idx_calls_callee ON calls(callee_method)";

    public static final String CREATE_INDEX_CHUNKS_FILE =
        "CREATE INDEX IF NOT EXISTS idx_chunks_file ON chunks(file_path)";

    public static final String CREATE_INDEX_CHUNKS_TYPE =
        "CREATE INDEX IF NOT EXISTS idx_chunks_type ON chunks(type)";

    public static final String CREATE_INDEX_CHUNKS_CLASS =
        "CREATE INDEX IF NOT EXISTS idx_chunks_class ON chunks(class_name)";

    public static final String CREATE_INDEX_CONFIG_ENTRIES_KEY =
        "CREATE INDEX IF NOT EXISTS idx_config_entries_key ON config_entries(key)";

    public static final String CREATE_INDEX_CONFIG_ENTRIES_FILE =
        "CREATE INDEX IF NOT EXISTS idx_config_entries_file ON config_entries(file_path)";

    public static final String CREATE_INDEX_CONFIG_ENTRIES_TYPE =
        "CREATE INDEX IF NOT EXISTS idx_config_entries_type ON config_entries(config_type)";

    public static final String CREATE_INDEX_DEPENDENCIES_ARTIFACT =
        "CREATE INDEX IF NOT EXISTS idx_dependencies_artifact ON dependencies(artifact_id)";

    public static final String CREATE_INDEX_DEPENDENCIES_GROUP =
        "CREATE INDEX IF NOT EXISTS idx_dependencies_group ON dependencies(group_id)";

    public static final String CREATE_INDEX_DEPENDENCIES_FILE =
        "CREATE INDEX IF NOT EXISTS idx_dependencies_file ON dependencies(file_path)";

    public static final String CREATE_INDEX_DEPENDENCIES_TYPE =
        "CREATE INDEX IF NOT EXISTS idx_dependencies_type ON dependencies(dep_type)";

    // FTS5 全文搜索表
    public static final String CREATE_SYMBOLS_FTS = """
        CREATE VIRTUAL TABLE IF NOT EXISTS symbols_fts USING fts5(
            name, qualified_name, signature,
            content=symbols, content_rowid=id
        )
        """;

    public static final String CREATE_CHUNKS_FTS = """
        CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
            name, content, package_name, class_name,
            content=chunks, content_rowid=id
        )
        """;

    // FTS 触发器：保持 FTS 表与主表同步
    public static final String CREATE_SYMBOLS_FTS_INSERT = """
        CREATE TRIGGER IF NOT EXISTS symbols_ai AFTER INSERT ON symbols BEGIN
            INSERT INTO symbols_fts(rowid, name, qualified_name, signature)
            VALUES (new.id, new.name, new.qualified_name, new.signature);
        END
        """;

    public static final String CREATE_SYMBOLS_FTS_DELETE = """
        CREATE TRIGGER IF NOT EXISTS symbols_ad AFTER DELETE ON symbols BEGIN
            INSERT INTO symbols_fts(symbols_fts, rowid, name, qualified_name, signature)
            VALUES('delete', old.id, old.name, old.qualified_name, old.signature);
        END
        """;

    public static final String CREATE_SYMBOLS_FTS_UPDATE = """
        CREATE TRIGGER IF NOT EXISTS symbols_au AFTER UPDATE ON symbols BEGIN
            INSERT INTO symbols_fts(symbols_fts, rowid, name, qualified_name, signature)
            VALUES('delete', old.id, old.name, old.qualified_name, old.signature);
            INSERT INTO symbols_fts(rowid, name, qualified_name, signature)
            VALUES (new.id, new.name, new.qualified_name, new.signature);
        END
        """;

    public static final String CREATE_CHUNKS_FTS_INSERT = """
        CREATE TRIGGER IF NOT EXISTS chunks_ai AFTER INSERT ON chunks BEGIN
            INSERT INTO chunks_fts(rowid, name, content, package_name, class_name)
            VALUES (new.id, new.name, new.content, new.package_name, new.class_name);
        END
        """;

    public static final String CREATE_CHUNKS_FTS_DELETE = """
        CREATE TRIGGER IF NOT EXISTS chunks_ad AFTER DELETE ON chunks BEGIN
            INSERT INTO chunks_fts(chunks_fts, rowid, name, content, package_name, class_name)
            VALUES('delete', old.id, old.name, old.content, old.package_name, old.class_name);
        END
        """;

    public static final String CREATE_CHUNKS_FTS_UPDATE = """
        CREATE TRIGGER IF NOT EXISTS chunks_au AFTER UPDATE ON chunks BEGIN
            INSERT INTO chunks_fts(chunks_fts, rowid, name, content, package_name, class_name)
            VALUES('delete', old.id, old.name, old.content, old.package_name, old.class_name);
            INSERT INTO chunks_fts(rowid, name, content, package_name, class_name)
            VALUES (new.id, new.name, new.content, new.package_name, new.class_name);
        END
        """;

    /**
     * 获取所有 DDL 语句，按顺序执行
     */
    public static String[] allDDL() {
        return new String[] {
            "PRAGMA journal_mode=WAL",
            "PRAGMA foreign_keys=ON",
            "PRAGMA busy_timeout=5000",
            "PRAGMA synchronous=NORMAL",
            "PRAGMA cache_size=-64000",  // 64MB
            "PRAGMA mmap_size=268435456", // 256MB
            CREATE_SYMBOLS,
            CREATE_CODE_REFERENCES,
            CREATE_CALLS,
            CREATE_CHUNKS,
            CREATE_FILE_META,
            CREATE_CONFIG_ENTRIES,
            CREATE_DEPENDENCIES,
            CREATE_SYMBOLS_FTS,
            CREATE_CHUNKS_FTS,
            CREATE_SYMBOLS_FTS_INSERT,
            CREATE_SYMBOLS_FTS_DELETE,
            CREATE_SYMBOLS_FTS_UPDATE,
            CREATE_CHUNKS_FTS_INSERT,
            CREATE_CHUNKS_FTS_DELETE,
            CREATE_CHUNKS_FTS_UPDATE,
            CREATE_INDEX_SYMBOLS_QUALIFIED,
            CREATE_INDEX_SYMBOLS_KIND,
            CREATE_INDEX_SYMBOLS_FILE,
            CREATE_INDEX_CODE_REFERENCES_SYMBOL,
            CREATE_INDEX_CODE_REFERENCES_FILE,
            CREATE_INDEX_CALLS_CALLER,
            CREATE_INDEX_CALLS_CALLEE,
            CREATE_INDEX_CHUNKS_FILE,
            CREATE_INDEX_CHUNKS_TYPE,
            CREATE_INDEX_CHUNKS_CLASS,
            CREATE_INDEX_CONFIG_ENTRIES_KEY,
            CREATE_INDEX_CONFIG_ENTRIES_FILE,
            CREATE_INDEX_CONFIG_ENTRIES_TYPE,
            CREATE_INDEX_DEPENDENCIES_ARTIFACT,
            CREATE_INDEX_DEPENDENCIES_GROUP,
            CREATE_INDEX_DEPENDENCIES_FILE,
            CREATE_INDEX_DEPENDENCIES_TYPE,
            // v1.6.0: API 路由、Bean 依赖、测试映射
            CREATE_API_ROUTES,
            CREATE_BEAN_DEPENDENCIES,
            CREATE_TEST_MAPPINGS,
            CREATE_INDEX_API_ROUTES_PATH,
            CREATE_INDEX_API_ROUTES_METHOD,
            CREATE_INDEX_API_ROUTES_SYMBOL,
            CREATE_INDEX_BEAN_DEP_BEAN,
            CREATE_INDEX_BEAN_DEP_DEPENDS,
            CREATE_INDEX_BEAN_DEP_TYPE,
            CREATE_INDEX_TEST_MAP_SOURCE,
            CREATE_INDEX_TEST_MAP_TEST,
            CREATE_INDEX_TEST_MAP_TYPE,
            // v1.7.0: 索引元数据、代码度量
            CREATE_INDEX_METADATA,
            CREATE_CODE_METRICS,
            CREATE_INDEX_CODE_METRICS_SYMBOL,
            CREATE_INDEX_CODE_METRICS_FILE
        };
    }

    /**
     * 获取迁移语句（v1.0.1: 添加继承关系字段）
     * 这些语句在初始化时执行，会自动跳过已存在的字段
     */
    public static String[] migrationV1_0_1() {
        return new String[] {
            "ALTER TABLE symbols ADD COLUMN super_class TEXT",
            "ALTER TABLE symbols ADD COLUMN interfaces TEXT"
        };
    }

    /**
     * 获取迁移语句（v1.1.1: 添加 Kotlin 特有字段）
     */
    public static String[] migrationV1_1_1() {
        return new String[] {
            "ALTER TABLE symbols ADD COLUMN is_data_class INTEGER DEFAULT 0",
            "ALTER TABLE symbols ADD COLUMN is_object INTEGER DEFAULT 0",
            "ALTER TABLE symbols ADD COLUMN is_sealed INTEGER DEFAULT 0",
            "ALTER TABLE symbols ADD COLUMN is_companion INTEGER DEFAULT 0"
        };
    }

    // v1.2.1: 注解表
    public static final String CREATE_ANNOTATIONS = """
        CREATE TABLE IF NOT EXISTS annotations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            symbol_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            attributes TEXT,
            FOREIGN KEY (symbol_id) REFERENCES symbols(id) ON DELETE CASCADE
        )
        """;

    public static final String CREATE_INDEX_ANNOTATIONS_SYMBOL =
        "CREATE INDEX IF NOT EXISTS idx_annotations_symbol ON annotations(symbol_id)";

    public static final String CREATE_INDEX_ANNOTATIONS_NAME =
        "CREATE INDEX IF NOT EXISTS idx_annotations_name ON annotations(name)";

    /**
     * 获取迁移语句（v1.2.1: 添加注解表）
     */
    public static String[] migrationV1_2_1() {
        return new String[] {
            "CREATE TABLE IF NOT EXISTS annotations (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol_id INTEGER NOT NULL, name TEXT NOT NULL, attributes TEXT, FOREIGN KEY (symbol_id) REFERENCES symbols(id) ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS idx_annotations_symbol ON annotations(symbol_id)",
            "CREATE INDEX IF NOT EXISTS idx_annotations_name ON annotations(name)"
        };
    }

    /**
     * 获取迁移语句（v1.3.1: 添加 Scala 特有字段）
     */
    public static String[] migrationV1_3_1() {
        return new String[] {
            "ALTER TABLE symbols ADD COLUMN is_trait INTEGER DEFAULT 0",
            "ALTER TABLE symbols ADD COLUMN is_case_class INTEGER DEFAULT 0"
        };
    }

    // v1.6.0: API 路由表
    public static final String CREATE_API_ROUTES = """
        CREATE TABLE IF NOT EXISTS api_routes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            symbol_id INTEGER NOT NULL,
            http_method TEXT NOT NULL,
            path TEXT NOT NULL,
            base_path TEXT,
            method_path TEXT,
            file_path TEXT NOT NULL,
            start_line INTEGER NOT NULL,
            FOREIGN KEY (symbol_id) REFERENCES symbols(id) ON DELETE CASCADE
        )
        """;

    public static final String CREATE_INDEX_API_ROUTES_PATH =
        "CREATE INDEX IF NOT EXISTS idx_api_routes_path ON api_routes(path)";
    public static final String CREATE_INDEX_API_ROUTES_METHOD =
        "CREATE INDEX IF NOT EXISTS idx_api_routes_method ON api_routes(http_method)";
    public static final String CREATE_INDEX_API_ROUTES_SYMBOL =
        "CREATE INDEX IF NOT EXISTS idx_api_routes_symbol ON api_routes(symbol_id)";

    // v1.6.0: Bean 依赖表
    public static final String CREATE_BEAN_DEPENDENCIES = """
        CREATE TABLE IF NOT EXISTS bean_dependencies (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            bean_symbol_id INTEGER NOT NULL,
            depends_on_symbol_id INTEGER,
            depends_on_type TEXT NOT NULL,
            injection_type TEXT NOT NULL,
            field_name TEXT,
            file_path TEXT NOT NULL,
            start_line INTEGER NOT NULL,
            FOREIGN KEY (bean_symbol_id) REFERENCES symbols(id) ON DELETE CASCADE,
            FOREIGN KEY (depends_on_symbol_id) REFERENCES symbols(id) ON DELETE SET NULL
        )
        """;

    public static final String CREATE_INDEX_BEAN_DEP_BEAN =
        "CREATE INDEX IF NOT EXISTS idx_bean_dep_bean ON bean_dependencies(bean_symbol_id)";
    public static final String CREATE_INDEX_BEAN_DEP_DEPENDS =
        "CREATE INDEX IF NOT EXISTS idx_bean_dep_depends ON bean_dependencies(depends_on_symbol_id)";
    public static final String CREATE_INDEX_BEAN_DEP_TYPE =
        "CREATE INDEX IF NOT EXISTS idx_bean_dep_type ON bean_dependencies(depends_on_type)";

    // v1.6.0: 测试映射表
    public static final String CREATE_TEST_MAPPINGS = """
        CREATE TABLE IF NOT EXISTS test_mappings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            test_symbol_id INTEGER NOT NULL,
            source_symbol_id INTEGER,
            test_class_name TEXT NOT NULL,
            source_class_name TEXT,
            mapping_type TEXT NOT NULL,
            file_path TEXT NOT NULL,
            FOREIGN KEY (test_symbol_id) REFERENCES symbols(id) ON DELETE CASCADE,
            FOREIGN KEY (source_symbol_id) REFERENCES symbols(id) ON DELETE SET NULL
        )
        """;

    public static final String CREATE_INDEX_TEST_MAP_SOURCE =
        "CREATE INDEX IF NOT EXISTS idx_test_map_source ON test_mappings(source_symbol_id)";
    public static final String CREATE_INDEX_TEST_MAP_TEST =
        "CREATE INDEX IF NOT EXISTS idx_test_map_test ON test_mappings(test_symbol_id)";
    public static final String CREATE_INDEX_TEST_MAP_TYPE =
        "CREATE INDEX IF NOT EXISTS idx_test_map_type ON test_mappings(mapping_type)";

    /**
     * 获取迁移语句（v1.6.0: 添加 API 路由、Bean 依赖、测试映射表）
     */
    public static String[] migrationV1_6_0() {
        return new String[] {
            "CREATE TABLE IF NOT EXISTS api_routes (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol_id INTEGER NOT NULL, http_method TEXT NOT NULL, path TEXT NOT NULL, base_path TEXT, method_path TEXT, file_path TEXT NOT NULL, start_line INTEGER NOT NULL, FOREIGN KEY (symbol_id) REFERENCES symbols(id) ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS idx_api_routes_path ON api_routes(path)",
            "CREATE INDEX IF NOT EXISTS idx_api_routes_method ON api_routes(http_method)",
            "CREATE INDEX IF NOT EXISTS idx_api_routes_symbol ON api_routes(symbol_id)",
            "CREATE TABLE IF NOT EXISTS bean_dependencies (id INTEGER PRIMARY KEY AUTOINCREMENT, bean_symbol_id INTEGER NOT NULL, depends_on_symbol_id INTEGER, depends_on_type TEXT NOT NULL, injection_type TEXT NOT NULL, field_name TEXT, file_path TEXT NOT NULL, start_line INTEGER NOT NULL, FOREIGN KEY (bean_symbol_id) REFERENCES symbols(id) ON DELETE CASCADE, FOREIGN KEY (depends_on_symbol_id) REFERENCES symbols(id) ON DELETE SET NULL)",
            "CREATE INDEX IF NOT EXISTS idx_bean_dep_bean ON bean_dependencies(bean_symbol_id)",
            "CREATE INDEX IF NOT EXISTS idx_bean_dep_depends ON bean_dependencies(depends_on_symbol_id)",
            "CREATE INDEX IF NOT EXISTS idx_bean_dep_type ON bean_dependencies(depends_on_type)",
            "CREATE TABLE IF NOT EXISTS test_mappings (id INTEGER PRIMARY KEY AUTOINCREMENT, test_symbol_id INTEGER NOT NULL, source_symbol_id INTEGER, test_class_name TEXT NOT NULL, source_class_name TEXT, mapping_type TEXT NOT NULL, file_path TEXT NOT NULL, FOREIGN KEY (test_symbol_id) REFERENCES symbols(id) ON DELETE CASCADE, FOREIGN KEY (source_symbol_id) REFERENCES symbols(id) ON DELETE SET NULL)",
            "CREATE INDEX IF NOT EXISTS idx_test_map_source ON test_mappings(source_symbol_id)",
            "CREATE INDEX IF NOT EXISTS idx_test_map_test ON test_mappings(test_symbol_id)",
            "CREATE INDEX IF NOT EXISTS idx_test_map_type ON test_mappings(mapping_type)"
        };
    }

    // v1.7.0: 索引元数据表
    public static final String CREATE_INDEX_METADATA = """
        CREATE TABLE IF NOT EXISTS index_metadata (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL,
            updated_at INTEGER NOT NULL
        )
        """;

    // v1.7.0: 代码度量表
    public static final String CREATE_CODE_METRICS = """
        CREATE TABLE IF NOT EXISTS code_metrics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            symbol_id INTEGER,
            file_path TEXT NOT NULL,
            class_name TEXT,
            package_name TEXT,
            lines_of_code INTEGER,
            method_count INTEGER,
            field_count INTEGER,
            complexity_estimate INTEGER,
            updated_at INTEGER NOT NULL,
            FOREIGN KEY (symbol_id) REFERENCES symbols(id) ON DELETE CASCADE
        )
        """;

    public static final String CREATE_INDEX_CODE_METRICS_SYMBOL =
        "CREATE INDEX IF NOT EXISTS idx_code_metrics_symbol ON code_metrics(symbol_id)";
    public static final String CREATE_INDEX_CODE_METRICS_FILE =
        "CREATE INDEX IF NOT EXISTS idx_code_metrics_file ON code_metrics(file_path)";

    /**
     * 获取迁移语句（v1.7.0: 添加索引元数据、代码度量表）
     */
    public static String[] migrationV1_7_0() {
        return new String[] {
            "CREATE TABLE IF NOT EXISTS index_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS code_metrics (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol_id INTEGER, file_path TEXT NOT NULL, class_name TEXT, package_name TEXT, lines_of_code INTEGER, method_count INTEGER, field_count INTEGER, complexity_estimate INTEGER, updated_at INTEGER NOT NULL, FOREIGN KEY (symbol_id) REFERENCES symbols(id) ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS idx_code_metrics_symbol ON code_metrics(symbol_id)",
            "CREATE INDEX IF NOT EXISTS idx_code_metrics_file ON code_metrics(file_path)"
        };
    }

    /**
     * 获取迁移语句（v1.8.0: 添加 bean_sources、config_bindings 表，code_metrics 新增复杂度字段）
     */
    public static String[] migrationV1_8_0() {
        return new String[] {
            "CREATE TABLE IF NOT EXISTS bean_sources (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol_id INTEGER, return_type TEXT NOT NULL, bean_name TEXT NOT NULL, source_type TEXT NOT NULL, file_path TEXT NOT NULL, start_line INTEGER, FOREIGN KEY (symbol_id) REFERENCES symbols(id) ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS idx_bean_sources_name ON bean_sources(bean_name)",
            "CREATE INDEX IF NOT EXISTS idx_bean_sources_type ON bean_sources(return_type)",
            "CREATE TABLE IF NOT EXISTS config_bindings (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol_id INTEGER, config_key TEXT NOT NULL, field_name TEXT NOT NULL, binding_type TEXT NOT NULL, file_path TEXT NOT NULL, start_line INTEGER, FOREIGN KEY (symbol_id) REFERENCES symbols(id) ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS idx_config_bindings_key ON config_bindings(config_key)",
            "ALTER TABLE code_metrics ADD COLUMN cyclomatic_complexity INTEGER DEFAULT 0",
            "ALTER TABLE code_metrics ADD COLUMN cognitive_complexity INTEGER DEFAULT 0"
        };
    }
}
