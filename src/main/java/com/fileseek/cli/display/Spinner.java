package com.fileseek.cli.display;


public class Spinner {

    private static final String[] FRAMES = {
            "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"
    };
    private static final long FRAME_DELAY_MS = 80;

    private final String label;
    private final Thread thread;
    private volatile boolean running = false;

    public Spinner(String label) {
        this.label = label;
        this.thread = new Thread(this::spin);
        this.thread.setDaemon(true);
    }

    public void start() {
        if (System.console() == null) return; // no spinner in non-TTY
        running = true;
        thread.start();
    }

    public void stop() {
        running = false;
        try {
            thread.join(FRAME_DELAY_MS * 2);
        } catch (InterruptedException ignored) {
        }
        if (System.console() != null) {
            System.out.print("\r\033[K");
        }
    }

    private void spin() {
        int frame = 0;
        while (running) {
            System.out.printf("\r  %s %s", label, FRAMES[frame % FRAMES.length]);
            frame++;
            try {
                Thread.sleep(FRAME_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}