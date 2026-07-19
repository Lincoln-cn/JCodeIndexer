package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shell 脚本解析器
 * 使用正则表达式提取函数、变量、source 等符号
 */
public class ShellParserAdapter {

    private static final Logger log = LoggerFactory.getLogger(ShellParserAdapter.class);
    private final Config config;

    // 匹配函数定义: function_name() { 或 function function_name {
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "^(?:function\\s+)?(\\w+)\\s*\\(\\s*\\)\\s*\\{|" +
        "^function\\s+(\\w+)\\s*\\{",
        Pattern.MULTILINE
    );

    // 匹配变量赋值: VAR_NAME=value 或 export VAR_NAME=value
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
        "^(?:export\\s+)?([A-Z_][A-Z0-9_]*)\\s*=",
        Pattern.MULTILINE
    );

    // 匹配 source 命令: source file.sh 或 . file.sh
    private static final Pattern SOURCE_PATTERN = Pattern.compile(
        "^(?:source|\\.)\\s+['\"]?([^'\"\\s]+)['\"]?",
        Pattern.MULTILINE
    );

    // 匹配函数调用: function_name
    private static final Pattern CALL_PATTERN = Pattern.compile(
        "\\b(\\w+)\\s+",
        Pattern.MULTILINE
    );

    // 匹配 shebang
    private static final Pattern SHEBANG_PATTERN = Pattern.compile(
        "^#!\\s*(.+)$",
        Pattern.MULTILINE
    );

    // 匹配注释
    private static final Pattern COMMENT_PATTERN = Pattern.compile(
        "^\\s*#(.*)$",
        Pattern.MULTILINE
    );

    public ShellParserAdapter(Config config) {
        this.config = config;
    }

    /**
     * 判断是否为 Shell 脚本文件
     */
    public static boolean isShellFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".sh") || lower.endsWith(".bash") ||
               lower.endsWith(".zsh") || lower.endsWith(".ksh") ||
               lower.endsWith(".fish");
    }

    /**
     * 解析单个 Shell 脚本文件
     */
    public ParseResult parse(String relativePath, Path filePath) {
        List<Symbol> symbols = new ArrayList<>();
        List<Reference> references = new ArrayList<>();
        List<Call> calls = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<ApiRoute> apiRoutes = new ArrayList<>();
        List<BeanDependency> beanDependencies = new ArrayList<>();
        List<TestMapping> testMappings = new ArrayList<>();
        List<AnnotationRef> annotationRefs = new ArrayList<>();
        List<BeanSource> beanSources = new ArrayList<>();

        try {
            String content = Files.readString(filePath);
            String[] lines = content.split("\n");

            // 提取 shebang
            extractShebang(content, relativePath, symbols);

            // 提取函数定义
            extractFunctions(content, lines, relativePath, symbols);

            // 提取变量
            extractVariables(content, lines, relativePath, symbols);

            // 提取 source 命令
            extractSources(content, relativePath, references);

            // 提取函数调用
            extractCalls(content, lines, relativePath, calls);

        } catch (IOException e) {
            log.warn("读取 Shell 脚本失败: {}", relativePath, e);
            errors.add(relativePath + ": " + e.getMessage());
        } catch (Exception e) {
            log.warn("解析 Shell 脚本失败: {}", relativePath, e);
            errors.add(relativePath + ": " + e.getMessage());
        }

        return new ParseResult(symbols, references, calls, annotations, errors,
            apiRoutes, beanDependencies, testMappings, annotationRefs, beanSources);
    }

    private void extractShebang(String content, String relativePath, List<Symbol> symbols) {
        Matcher m = SHEBANG_PATTERN.matcher(content);
        if (m.find()) {
            String interpreter = m.group(1).trim();
            symbols.add(new Symbol(
                0, relativePath, 1, 1,
                Symbol.SymbolKind.METHOD, "#!interpreter", "#!interpreter",
                "#! " + interpreter,
                interpreter, null, 0, null,
                null, null, false, false, false, false
            ));
        }
    }

    private void extractFunctions(String content, String[] lines, String relativePath, List<Symbol> symbols) {
        Matcher m = FUNCTION_PATTERN.matcher(content);
        while (m.find()) {
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            int line = findLineNumber(content, m.group(0));

            // 获取函数体的行数范围
            int startLine = line;
            int endLine = findFunctionEndLine(content, line);

            symbols.add(new Symbol(
                0, relativePath, startLine, endLine,
                Symbol.SymbolKind.METHOD, name, name,
                name + "()",
                null, null, 0x0001, null, // public
                null, null, false, false, false, false
            ));
        }
    }

    private void extractVariables(String content, String[] lines, String relativePath, List<Symbol> symbols) {
        Matcher m = VARIABLE_PATTERN.matcher(content);
        while (m.find()) {
            String name = m.group(1);
            int line = findLineNumber(content, m.group(0));

            symbols.add(new Symbol(
                0, relativePath, line, line,
                Symbol.SymbolKind.FIELD, name, name,
                name,
                null, null, 0x0001, null, // public
                null, null, false, false, false, false
            ));
        }
    }

    private void extractSources(String content, String relativePath, List<Reference> references) {
        Matcher m = SOURCE_PATTERN.matcher(content);
        while (m.find()) {
            String sourceFile = m.group(1);
            int line = findLineNumber(content, m.group(0));
            references.add(new Reference(0, 0, relativePath, line, "source " + sourceFile));
        }
    }

    private void extractCalls(String content, String[] lines, String relativePath, List<Call> calls) {
        // 提取命令调用（简化版）
        Pattern cmdPattern = Pattern.compile("^\\s*(\\w+)\\s+", Pattern.MULTILINE);
        Matcher m = cmdPattern.matcher(content);
        while (m.find()) {
            String cmd = m.group(1);
            int line = findLineNumber(content, m.group(0));

            // 过滤掉关键字和常见命令
            if (isKeyword(cmd) || isBuiltinCommand(cmd)) continue;

            calls.add(new Call(
                0, "", relativePath, line,
                cmd, null
            ));
        }
    }

    private int findFunctionEndLine(String content, int startLine) {
        String[] lines = content.split("\n");
        int braceCount = 0;
        boolean found = false;

        for (int i = startLine - 1; i < lines.length; i++) {
            String line = lines[i];
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    braceCount++;
                    found = true;
                } else if (c == '}') {
                    braceCount--;
                    if (found && braceCount == 0) {
                        return i + 1;
                    }
                }
            }
        }
        return startLine;
    }

    private int findLineNumber(String content, String pattern) {
        int index = content.indexOf(pattern);
        if (index == -1) return 0;
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    private boolean isKeyword(String name) {
        return "if".equals(name) || "then".equals(name) || "else".equals(name) ||
               "elif".equals(name) || "fi".equals(name) || "for".equals(name) ||
               "do".equals(name) || "done".equals(name) || "while".equals(name) ||
               "until".equals(name) || "case".equals(name) || "esac".equals(name) ||
               "in".equals(name) || "function".equals(name) || "select".equals(name) ||
               "time".equals(name) || "coproc".equals(name);
    }

    private boolean isBuiltinCommand(String cmd) {
        return "echo".equals(cmd) || "printf".equals(cmd) || "read".equals(cmd) ||
               "test".equals(cmd) || "[".equals(cmd) || "[[".equals(cmd) ||
               "eval".equals(cmd) || "exec".equals(cmd) || "export".equals(cmd) ||
               "declare".equals(cmd) || "typeset".equals(cmd) || "local".equals(cmd) ||
               "readonly".equals(cmd) || "unset".equals(cmd) || "shift".equals(cmd) ||
               "exit".equals(cmd) || "return".equals(cmd) || "break".equals(cmd) ||
               "continue".equals(cmd) || "source".equals(cmd) || ".".equals(cmd) ||
               "cd".equals(cmd) || "pwd".equals(cmd) || "pushd".equals(cmd) ||
               "popd".equals(cmd) || "dirs".equals(cmd) || "wait".equals(cmd) ||
               "kill".equals(cmd) || "trap".equals(cmd) || "jobs".equals(cmd) ||
               "fg".equals(cmd) || "bg".equals(cmd) || "disown".equals(cmd) ||
               "type".equals(cmd) || "which".equals(cmd) || "hash".equals(cmd) ||
               "builtin".equals(cmd) || "command".equals(cmd) || "enable".equals(cmd) ||
               "help".equals(cmd) || "let".equals(cmd) || "set".equals(cmd) ||
               "shopt".equals(cmd) || "ulimit".equals(cmd) || "umask".equals(cmd) ||
               "alias".equals(cmd) || "unalias".equals(cmd) || "complete".equals(cmd) ||
               "compgen".equals(cmd) || "compopt".equals(cmd);
    }
}
