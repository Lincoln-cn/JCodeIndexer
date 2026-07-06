package com.sodlinken.jindexer.model;

/**
 * 代码块（用于语义搜索的切片单元）
 */
public record Chunk(
    long id,
    String filePath,        // 相对于项目根目录的路径
    ChunkType type,         // 代码块类型
    int startLine,          // 起始行号
    int endLine,            // 结束行号
    String name,            // 代码块名称
    String content,         // 切片后的代码内容
    String packageName,     // 包名
    String className,       // 所属类名
    String signature        // 方法签名（METHOD 类型时）
) {
    public enum ChunkType {
        CLASS,       // 类定义（含成员摘要）
        METHOD,      // 方法实现
        ANNOTATION,  // 注解定义
        FILE_HEADER  // 文件头部（imports + 包声明，≤10 行）
    }
}
