package com.sodlinken.jindexer.indexer;

import com.sodlinken.jindexer.model.*;

import java.util.List;

/**
 * 单个文件的解析结果（不含 DB 操作）
 */
record FileParseResult(
    String relativePath,
    String fileName,
    String sha1,
    List<Symbol> symbols,
    List<Reference> references,
    List<Call> calls,
    List<Chunk> chunks,
    List<ConfigEntry> configEntries,
    List<Dependency> dependencies,
    List<Annotation> annotations,
    List<ApiRoute> apiRoutes,
    List<BeanDependency> beanDependencies,
    List<TestMapping> testMappings,
    List<AnnotationRef> annotationRefs,
    List<BeanSource> beanSources
) {
}
