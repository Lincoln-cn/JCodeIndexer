package com.sodlinken.jindexer.model;

/**
 * Annotation 关联信息：解析时临时使用，将 Annotation 关联到所属 Symbol
 */
public record AnnotationRef(
    int annotationIndex,         // annotations 列表中的索引
    String ownerQualifiedName,   // 所属 Symbol 的限定名
    Symbol.SymbolKind ownerKind  // 所属 Symbol 的类型
) {}
