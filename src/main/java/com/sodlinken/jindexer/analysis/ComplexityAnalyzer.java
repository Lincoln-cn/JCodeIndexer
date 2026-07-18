package com.sodlinken.jindexer.analysis;

import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * 代码复杂度分析器
 * 支持圈复杂度 (Cyclomatic Complexity) 和认知复杂度 (Cognitive Complexity)
 */
public final class ComplexityAnalyzer {

    private ComplexityAnalyzer() {}

    /**
     * 计算方法的圈复杂度 (Cyclomatic Complexity)
     * CC = 1 + (每个决策点)
     */
    public static int calculateCyclomaticComplexity(MethodDeclaration method) {
        String code = method.toString();
        return calculateCyclomaticComplexity(code);
    }

    /**
     * 从源码字符串计算圈复杂度（指定行范围）
     * 用于索引时预计算
     */
    public static int calculateCyclomaticComplexity(String content, int startLine, int endLine) {
        String[] lines = content.split("\n");
        StringBuilder methodBody = new StringBuilder();
        for (int i = startLine - 1; i < Math.min(endLine, lines.length); i++) {
            methodBody.append(lines[i]).append("\n");
        }
        return calculateCyclomaticComplexity(methodBody.toString());
    }

    /**
     * 从代码字符串计算圈复杂度
     */
    public static int calculateCyclomaticComplexity(String code) {
        int cc = 1;
        cc += countPattern(code, "\\bif\\s*\\(");
        cc += countPattern(code, "\\belse\\s+if\\s*\\(");
        cc += countPattern(code, "\\bfor\\s*\\(");
        cc += countPattern(code, "\\bwhile\\s*\\(");
        cc += countPattern(code, "\\bdo\\s*\\{");
        cc += countPattern(code, "\\bcatch\\s*\\(");
        cc += countPattern(code, "\\bcase\\s+");
        cc += countPattern(code, "\\?\\s*[^?]");
        cc += countPattern(code, "&&");
        cc += countPattern(code, "\\|\\|");
        return cc;
    }

    /**
     * 计算方法的认知复杂度 (Cognitive Complexity)
     * 基于 SonarSource 规范的简化实现
     */
    public static int calculateCognitiveComplexity(MethodDeclaration method) {
        String code = method.toString();
        return calculateCognitiveComplexity(code);
    }

    /**
     * 从代码字符串计算认知复杂度
     */
    public static int calculateCognitiveComplexity(String code) {
        int complexity = 0;
        complexity += countPattern(code, "\\bif\\s*\\(");
        complexity += countPattern(code, "\\belse\\s+if\\s*\\(");
        complexity += countPattern(code, "\\bfor\\s*\\(");
        complexity += countPattern(code, "\\bwhile\\s*\\(");
        complexity += countPattern(code, "\\bdo\\s*\\{");
        complexity += countPattern(code, "\\bcatch\\s*\\(");
        complexity += countPattern(code, "\\bswitch\\s*\\(");
        complexity += countPattern(code, "&&");
        complexity += countPattern(code, "\\|\\|");
        return complexity;
    }

    private static int countPattern(String text, String regex) {
        int count = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
        while (m.find()) {
            count++;
        }
        return count;
    }
}
