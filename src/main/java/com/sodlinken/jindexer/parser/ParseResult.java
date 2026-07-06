package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.model.Call;
import com.sodlinken.jindexer.model.Reference;
import com.sodlinken.jindexer.model.Symbol;

import java.util.List;

/**
 * Java 文件解析结果
 */
public record ParseResult(
    List<Symbol> symbols,
    List<Reference> references,
    List<Call> calls,
    List<String> errors
) {
    public static ParseResult empty() {
        return new ParseResult(List.of(), List.of(), List.of(), List.of());
    }
}
