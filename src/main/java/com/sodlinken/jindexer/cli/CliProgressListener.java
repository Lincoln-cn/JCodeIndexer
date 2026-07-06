package com.sodlinken.jindexer.cli;

import com.sodlinken.jindexer.indexer.ProgressListener;

import java.io.PrintStream;

/**
 * 终端进度条实现
 *
 * 输出到 stderr，不影响 stdout 的 JSON-RPC 通信
 */
public class CliProgressListener implements ProgressListener {

    private final PrintStream out;
    private final boolean ansiSupported;
    private int lastBarLength = 0;

    public CliProgressListener(PrintStream out) {
        this.out = out;
        this.ansiSupported = System.console() != null && System.getenv("TERM") != null;
    }

    @Override
    public void onPhaseStart(String phase, int total) {
        out.printf("\r  %-14s", phase);
        if (total > 0) {
            renderBar(0, total, "");
        }
        out.println();
    }

    @Override
    public void onProgress(String phase, int current, int total) {
        if (total <= 0) return;
        int pct = (int) ((long) current * 100 / total);
        String suffix = " (" + current + "/" + total + ")";
        renderBar(current, total, suffix);
    }

    @Override
    public void onPhaseEnd(String phase) {
        renderBar(1, 1, " ✓");
        out.println();
    }

    @Override
    public void onError(String message) {
        out.println("\r  ✗ " + message);
    }

    private void renderBar(int current, int total, String suffix) {
        int width = 30;
        int filled = total > 0 ? (int) ((long) current * width / total) : 0;
        if (filled > width) filled = width;

        StringBuilder bar = new StringBuilder("\r    [");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? '█' : '░');
        }
        bar.append(']');
        bar.append(suffix);

        // 填充空格覆盖上一次更长的输出
        int pad = Math.max(0, lastBarLength - bar.length());
        bar.append(" ".repeat(pad));
        lastBarLength = bar.length();

        out.print(bar.toString());
        out.flush();
    }
}
