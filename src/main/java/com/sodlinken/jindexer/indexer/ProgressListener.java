package com.sodlinken.jindexer.indexer;

/**
 * 索引进度回调
 */
public interface ProgressListener {

    /**
     * 阶段开始
     */
    void onPhaseStart(String phase, int total);

    /**
     * 进度更新（current 从 0 到 total）
     */
    void onProgress(String phase, int current, int total);

    /**
     * 阶段完成
     */
    void onPhaseEnd(String phase);

    /**
     * 错误信息
     */
    void onError(String message);

    /** 无操作实现 */
    ProgressListener NONE = new ProgressListener() {
        @Override public void onPhaseStart(String phase, int total) {}
        @Override public void onProgress(String phase, int current, int total) {}
        @Override public void onPhaseEnd(String phase) {}
        @Override public void onError(String message) {}
    };
}
