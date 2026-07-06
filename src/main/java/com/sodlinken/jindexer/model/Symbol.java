package com.sodlinken.jindexer.model;

/**
 * 代码符号实体，覆盖 class / method / field
 */
public record Symbol(
    long id,
    String filePath,        // 相对于项目根目录的路径
    int startLine,          // 起始行号
    int endLine,            // 结束行号
    SymbolKind kind,        // CLASS | METHOD | FIELD
    String name,            // 符号名称
    String qualifiedName,   // 限定名（含包名）
    String signature,       // 签名
    String returnType,      // 返回类型
    String parentClass,     // 所属类（METHOD/FIELD 时填充）
    int modifiers,          // Java 修饰符位掩码
    String javadoc          // Javadoc（可选，需配置开启）
) {
    public enum SymbolKind {
        CLASS, METHOD, FIELD
    }
}
