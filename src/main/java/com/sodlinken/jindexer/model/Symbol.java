package com.sodlinken.jindexer.model;

import java.util.List;

/**
 * 代码符号实体，覆盖 class / method / field
 * 支持 Java 和 Kotlin 语言
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
    String javadoc,         // Javadoc（可选，需配置开启）
    String superClass,      // 父类限定名（CLASS 时填充）
    List<String> interfaces,// 实现的接口列表（CLASS 时填充）
    // Kotlin 特有字段
    boolean isDataClass,    // data class 标记
    boolean isObject,       // object 单例标记
    boolean isSealed,       // sealed class 标记
    boolean isCompanion     // companion object 标记
) {
    public enum SymbolKind {
        CLASS, METHOD, FIELD
    }

    /**
     * 兼容旧构造函数（不含继承信息和 Kotlin 字段）
     */
    public Symbol(long id, String filePath, int startLine, int endLine,
                  SymbolKind kind, String name, String qualifiedName,
                  String signature, String returnType, String parentClass,
                  int modifiers, String javadoc) {
        this(id, filePath, startLine, endLine, kind, name, qualifiedName,
             signature, returnType, parentClass, modifiers, javadoc, null, null,
             false, false, false, false);
    }

    /**
     * 兼容旧构造函数（含继承信息，不含 Kotlin 字段）
     */
    public Symbol(long id, String filePath, int startLine, int endLine,
                  SymbolKind kind, String name, String qualifiedName,
                  String signature, String returnType, String parentClass,
                  int modifiers, String javadoc, String superClass, List<String> interfaces) {
        this(id, filePath, startLine, endLine, kind, name, qualifiedName,
             signature, returnType, parentClass, modifiers, javadoc, superClass, interfaces,
             false, false, false, false);
    }
}
