package com.sodlinken.jindexer.analysis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComplexityAnalyzerTest {

    @Test
    void simpleMethod() {
        MethodDeclaration method = parseMethod("void foo() { int x = 1; }");
        assertEquals(1, ComplexityAnalyzer.calculateCyclomaticComplexity(method));
    }

    @Test
    void ifStatement() {
        MethodDeclaration method = parseMethod("void foo() { if (x) { } }");
        assertEquals(2, ComplexityAnalyzer.calculateCyclomaticComplexity(method));
    }

    @Test
    void ifElseStatement() {
        MethodDeclaration method = parseMethod("void foo() { if (x) { } else { } }");
        assertEquals(2, ComplexityAnalyzer.calculateCyclomaticComplexity(method));
    }

    @Test
    void forLoop() {
        MethodDeclaration method = parseMethod("void foo() { for (int i=0; i<10; i++) { } }");
        assertEquals(2, ComplexityAnalyzer.calculateCyclomaticComplexity(method));
    }

    @Test
    void whileLoop() {
        MethodDeclaration method = parseMethod("void foo() { while (x) { } }");
        assertEquals(2, ComplexityAnalyzer.calculateCyclomaticComplexity(method));
    }

    @Test
    void nestedConditions() {
        MethodDeclaration method = parseMethod("void foo() { if (a) { if (b) { } } }");
        assertEquals(3, ComplexityAnalyzer.calculateCyclomaticComplexity(method));
    }

    @Test
    void logicalOperators() {
        MethodDeclaration method = parseMethod("void foo() { if (a && b || c) { } }");
        assertEquals(4, ComplexityAnalyzer.calculateCyclomaticComplexity(method));
    }

    @Test
    void switchStatement() {
        MethodDeclaration method = parseMethod("void foo() { switch(x) { case 1: break; case 2: break; } }");
        assertEquals(3, ComplexityAnalyzer.calculateCyclomaticComplexity(method));
    }

    @Test
    void tryCatch() {
        MethodDeclaration method = parseMethod("void foo() { try { } catch (Exception e) { } }");
        assertEquals(2, ComplexityAnalyzer.calculateCyclomaticComplexity(method));
    }

    @Test
    void cognitiveComplexitySimple() {
        MethodDeclaration method = parseMethod("void foo() { if (x) { } }");
        assertEquals(1, ComplexityAnalyzer.calculateCognitiveComplexity(method));
    }

    @Test
    void cognitiveComplexityNested() {
        MethodDeclaration method = parseMethod("void foo() { if (a) { if (b) { } } }");
        // 简化实现：统计控制流结构数量
        assertEquals(2, ComplexityAnalyzer.calculateCognitiveComplexity(method));
    }

    private MethodDeclaration parseMethod(String code) {
        String full = "class Foo { " + code + " }";
        return StaticJavaParser.parse(full)
            .findFirst(MethodDeclaration.class)
            .orElseThrow();
    }
}
