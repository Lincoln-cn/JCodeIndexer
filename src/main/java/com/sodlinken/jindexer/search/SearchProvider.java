package com.sodlinken.jindexer.search;

import com.sodlinken.jindexer.model.SearchResult;

/**
 * 搜索提供者接口
 */
public interface SearchProvider {
    SearchResult search(String query, int limit);
}
