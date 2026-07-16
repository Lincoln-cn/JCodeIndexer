package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.model.*;

import java.util.List;

/**
 * Java/Kotlin 文件解析结果
 */
public record ParseResult(
    List<Symbol> symbols,
    List<Reference> references,
    List<Call> calls,
    List<Annotation> annotations,
    List<String> errors,
    List<ApiRoute> apiRoutes,
    List<BeanDependency> beanDependencies,
    List<TestMapping> testMappings,
    List<AnnotationRef> annotationRefs
) {
    public static ParseResult empty() {
        return new ParseResult(List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of());
    }
}
