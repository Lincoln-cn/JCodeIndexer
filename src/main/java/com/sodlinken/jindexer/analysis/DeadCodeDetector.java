package com.sodlinken.jindexer.analysis;

import com.sodlinken.jindexer.model.Symbol;
import com.sodlinken.jindexer.storage.StorageService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 死代码检测器
 * 检测未被引用的 public 方法/类
 */
public class DeadCodeDetector {

    private final StorageService storage;

    public DeadCodeDetector(StorageService storage) {
        this.storage = storage;
    }

    /**
     * 检测潜在死代码
     */
    public List<DeadCodeInfo> detect() throws SQLException {
        List<DeadCodeInfo> results = new ArrayList<>();

        // 获取所有符号
        List<Symbol> allSymbols = storage.listAllSymbols(10000);

        for (Symbol sym : allSymbols) {
            // 跳过测试文件
            if (sym.filePath() != null && sym.filePath().contains("test")) continue;
            // 跳过 main 方法
            if ("main".equals(sym.name()) && sym.kind() == Symbol.SymbolKind.METHOD) continue;

            // 检查是否被引用
            var refs = storage.findReferencesBySymbol(sym.id());
            if (refs.isEmpty()) {
                results.add(new DeadCodeInfo(
                    sym.name(), sym.qualifiedName(), sym.kind().name(),
                    sym.filePath(), sym.startLine(), "NO_REFERENCES"
                ));
            }
        }
        return results;
    }

    public record DeadCodeInfo(
        String name,
        String qualifiedName,
        String kind,
        String filePath,
        int startLine,
        String reason
    ) {}
}
