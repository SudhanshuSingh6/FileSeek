package com.fileseek.cli.display;


public class ProgressBar {

    private static final int BAR_WIDTH = 30;
    private static final char FILLED = '█';
    private static final char EMPTY = '░';
    private static final int FALLBACK_EVERY = 1000;
    private static final String ANSI_CLEAR_LINE = "\r\033[K";

    private final int total;
    private final boolean ansiSupported;
    private int lastPercent = -1;

    public ProgressBar(int total) {
        this.total = total;
        this.ansiSupported = isAnsiSupported();
    }

    public void update(int current, String currentFile) {
        if (total <= 0) {
            printIndeterminate(current, currentFile);
            return;
        }

        int percent = Math.min(100, (int) ((current * 100L) / total));

        if (percent == lastPercent && ansiSupported) return;
        lastPercent = percent;

        String bar = buildBar(percent);
        String file = truncate(currentFile, 24);
        String counter = String.format("%,d / %,d files", current, total);

        if (ansiSupported) {
            System.out.printf("%s[%s] %3d%%  |  %s  |  %s",
                    ANSI_CLEAR_LINE, bar, percent, counter, file);
        } else {
            if (current % FALLBACK_EVERY == 0) {
                System.out.printf("  %3d%%  %s%n", percent, counter);
            }
        }
    }

    private void printIndeterminate(int current, String currentFile) {
        if (!ansiSupported) {
            if (current % FALLBACK_EVERY == 0) {
                System.out.printf("  %,d files processed%n", current);
            }
            return;
        }
        System.out.printf("%s  Processed: %,d  |  %s",
                ANSI_CLEAR_LINE, current, truncate(currentFile, 40));
    }

    public void complete(int total, long durationMs) {
        if (ansiSupported) {
            System.out.printf("%s[%s] 100%%  |  %,d files  |  %.2fs%n",
                    ANSI_CLEAR_LINE,
                    String.valueOf(FILLED).repeat(BAR_WIDTH),
                    total,
                    durationMs / 1000.0);
        } else {
            System.out.printf("  Done — %,d files in %.2fs%n",
                    total, durationMs / 1000.0);
        }
    }

    private String buildBar(int percent) {
        int filled = (int) (BAR_WIDTH * percent / 100.0);
        int empty = BAR_WIDTH - filled;
        return String.valueOf(FILLED).repeat(filled)
                + String.valueOf(EMPTY).repeat(empty);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return "..." + s.substring(s.length() - (maxLen - 3));
    }

    private boolean isAnsiSupported() {
        return System.console() != null
                || "true".equalsIgnoreCase(System.getenv("FORCE_COLOR"))
                || System.getProperty("os.name", "").toLowerCase().contains("mac")
                || System.getProperty("os.name", "").toLowerCase().contains("linux");
    }
}