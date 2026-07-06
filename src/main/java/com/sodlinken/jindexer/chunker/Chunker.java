package com.sodlinken.jindexer.chunker;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.sodlinken.jindexer.model.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码切片器，将 Java 文件切分为 CHUNK 粒度
 */
public class Chunker {

    private static final Logger log = LoggerFactory.getLogger(Chunker.class);
    private static final int MAX_CHUNK_TOKENS = 1500;

    private static final JavaParser PARSER;

    static {
        ParserConfiguration config = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        PARSER = new JavaParser(config);
    }

    /**
     * 将 Java 文件切分为代码块
     */
    public List<Chunk> chunkFile(String relativePath, Path filePath, String packageName) {
        List<Chunk> chunks = new ArrayList<>();

        try {
            String content = Files.readString(filePath);
            String[] lines = content.split("\n");
            CompilationUnit cu = PARSER.parse(content)
                .getResult()
                .orElseThrow(() -> new RuntimeException("解析失败: " + relativePath));

            String className = cu.findFirst(ClassOrInterfaceDeclaration.class)
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .orElse("");

            // FILE_HEADER: 包声明 + imports（≤10行）
            String fileHeader = extractFileHeader(lines);
            if (!fileHeader.isBlank()) {
                int headerEnd = Math.min(10, lines.length);
                chunks.add(new Chunk(
                    0, relativePath, Chunk.ChunkType.FILE_HEADER,
                    1, headerEnd, null, fileHeader,
                    packageName, className, null
                ));
            }

            // 类定义块
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                String clsName = clazz.getNameAsString();
                int startLine = clazz.getBegin().map(p -> p.line).orElse(1);
                int endLine = clazz.getEnd().map(p -> p.line).orElse(lines.length);
                int chunkLines = endLine - startLine + 1;

                if (chunkLines <= estimateLines(MAX_CHUNK_TOKENS)) {
                    // 类足够小，整个类作为一个块
                    String classContent = extractLines(lines, startLine, endLine);
                    chunks.add(new Chunk(
                        0, relativePath, Chunk.ChunkType.CLASS,
                        startLine, endLine, clsName, classContent,
                        packageName, clsName, buildClassSignature(clazz)
                    ));
                } else {
                    // 类太大，提取类头部（字段声明等）+ 每个方法单独成块
                    String classHeader = extractClassHeader(lines, clazz, className);
                    if (!classHeader.isBlank()) {
                        chunks.add(new Chunk(
                            0, relativePath, Chunk.ChunkType.CLASS,
                            startLine, startLine + countLines(classHeader),
                            clsName, classHeader,
                            packageName, clsName, buildClassSignature(clazz)
                        ));
                    }
                }

                // 每个方法独立成块
                clazz.getMethods().forEach(method -> {
                    String methodName = method.getNameAsString();
                    int mStart = method.getBegin().map(p -> p.line).orElse(1);
                    int mEnd = method.getEnd().map(p -> p.line).orElse(lines.length);
                    String methodContent = extractLines(lines, mStart, mEnd);

                    // 如果方法过长，截断并添加标记
                    if (countLines(methodContent) > estimateLines(MAX_CHUNK_TOKENS)) {
                        methodContent = truncateContent(methodContent, MAX_CHUNK_TOKENS);
                    }

                    chunks.add(new Chunk(
                        0, relativePath, Chunk.ChunkType.METHOD,
                        mStart, mEnd, methodName, methodContent,
                        packageName, clsName, buildMethodSignature(method)
                    ));
                });
            });

            // Record 声明块
            cu.findAll(RecordDeclaration.class).forEach(record -> {
                String recName = record.getNameAsString();
                int startLine = record.getBegin().map(p -> p.line).orElse(1);
                int endLine = record.getEnd().map(p -> p.line).orElse(lines.length);
                int chunkLines = endLine - startLine + 1;

                if (chunkLines <= estimateLines(MAX_CHUNK_TOKENS)) {
                    String recordContent = extractLines(lines, startLine, endLine);
                    chunks.add(new Chunk(
                        0, relativePath, Chunk.ChunkType.CLASS,
                        startLine, endLine, recName, recordContent,
                        packageName, recName, buildRecordSignature(record)
                    ));
                } else {
                    // Record 太大，提取头部 + 方法
                    String recordHeader = extractLines(lines, startLine,
                        Math.min(startLine + 50, endLine));
                    if (!recordHeader.isBlank()) {
                        chunks.add(new Chunk(
                            0, relativePath, Chunk.ChunkType.CLASS,
                            startLine, startLine + countLines(recordHeader),
                            recName, recordHeader,
                            packageName, recName, buildRecordSignature(record)
                        ));
                    }
                }

                // Record 方法独立成块
                record.getMethods().forEach(method -> {
                    String methodName = method.getNameAsString();
                    int mStart = method.getBegin().map(p -> p.line).orElse(1);
                    int mEnd = method.getEnd().map(p -> p.line).orElse(lines.length);
                    String methodContent = extractLines(lines, mStart, mEnd);

                    if (countLines(methodContent) > estimateLines(MAX_CHUNK_TOKENS)) {
                        methodContent = truncateContent(methodContent, MAX_CHUNK_TOKENS);
                    }

                    chunks.add(new Chunk(
                        0, relativePath, Chunk.ChunkType.METHOD,
                        mStart, mEnd, methodName, methodContent,
                        packageName, recName, buildMethodSignature(method)
                    ));
                });
            });

        } catch (IOException e) {
            log.warn("读取文件失败: {}", relativePath, e);
        } catch (Exception e) {
            log.warn("切片失败: {}", relativePath, e);
        }

        return chunks;
    }

    private String extractFileHeader(String[] lines) {
        StringBuilder sb = new StringBuilder();
        int lineCount = 0;
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
                sb.append(line).append("\n");
                lineCount++;
                if (lineCount >= 10) break;
            } else if (!trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*")) {
                break; // 遇到非注释、非空行的代码，停止
            } else {
                sb.append(line).append("\n");
                lineCount++;
                if (lineCount >= 10) break;
            }
        }
        return sb.toString().stripTrailing();
    }

    private String extractClassHeader(String[] lines, ClassOrInterfaceDeclaration clazz, String className) {
        int startLine = clazz.getBegin().map(p -> p.line).orElse(1);
        // 提取类声明到第一个方法之前的内容
        int endLine = clazz.getMethods().isEmpty()
            ? clazz.getEnd().map(p -> p.line).orElse(lines.length)
            : clazz.getMethods().getFirst().getBegin().map(p -> p.line).orElse(lines.length) - 1;
        return extractLines(lines, startLine, Math.min(endLine, startLine + 50));
    }

    private String extractLines(String[] lines, int startLine, int endLine) {
        StringBuilder sb = new StringBuilder();
        for (int i = startLine - 1; i < Math.min(endLine, lines.length); i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private String truncateContent(String content, int maxTokens) {
        String[] lines = content.split("\n");
        int maxLines = estimateLines(maxTokens);
        if (lines.length <= maxLines) return content;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines - 1; i++) {
            sb.append(lines[i]).append("\n");
        }
        sb.append("// ... (truncated, total ").append(lines.length).append(" lines)");
        return sb.toString();
    }

    private int countLines(String content) {
        return content.split("\n").length;
    }

    private int estimateLines(int tokens) {
        // 粗略估计：1行 ≈ 8 tokens
        return Math.max(1, tokens / 8);
    }

    private String buildClassSignature(ClassOrInterfaceDeclaration clazz) {
        StringBuilder sb = new StringBuilder();
        if (clazz.isInterface()) sb.append("interface ");
        else sb.append("class ");
        sb.append(clazz.getNameAsString());

        NodeList<ClassOrInterfaceType> extendedTypes = clazz.getExtendedTypes();
        if (!extendedTypes.isEmpty()) {
            sb.append(" extends ");
            for (int i = 0; i < extendedTypes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(extendedTypes.get(i).getNameAsString());
            }
        }

        NodeList<ClassOrInterfaceType> implementedTypes = clazz.getImplementedTypes();
        if (!implementedTypes.isEmpty()) {
            sb.append(" implements ");
            for (int i = 0; i < implementedTypes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(implementedTypes.get(i).getNameAsString());
            }
        }

        return sb.toString();
    }

    private String buildRecordSignature(RecordDeclaration record) {
        StringBuilder sb = new StringBuilder();
        sb.append("record ").append(record.getNameAsString());

        NodeList<Parameter> params = record.getParameters();
        sb.append("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getTypeAsString()).append(" ").append(params.get(i).getNameAsString());
        }
        sb.append(")");

        NodeList<ClassOrInterfaceType> implementedTypes = record.getImplementedTypes();
        if (!implementedTypes.isEmpty()) {
            sb.append(" implements ");
            for (int i = 0; i < implementedTypes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(implementedTypes.get(i).getNameAsString());
            }
        }

        return sb.toString();
    }

    private String buildMethodSignature(MethodDeclaration method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getTypeAsString()).append(" ");
        sb.append(method.getNameAsString()).append("(");
        NodeList<Parameter> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getTypeAsString());
        }
        sb.append(")");
        return sb.toString();
    }
}
