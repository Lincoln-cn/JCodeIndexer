package com.sodlinken.jindexer.model;

/**
 * 测试覆盖映射：Test 类 → Source 类
 */
public record TestMapping(
    long id,
    long testSymbolId,           // 测试类的 symbol ID
    Long sourceSymbolId,         // 源类的 symbol ID（可能为 null）
    String testClassName,        // 测试类名
    String sourceClassName,      // 源类名
    String mappingType,          // NAME_PATTERN / IMPORT
    String filePath              // 文件相对路径
) {}
