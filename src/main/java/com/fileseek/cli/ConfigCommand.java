package com.fileseek.cli;

import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import picocli.CommandLine.Command;

@Command(
        name = "config",
        mixinStandardHelpOptions = true,
        description = "Display current FileSeek configuration."
)
public class ConfigCommand implements Runnable {

    @Override
    public void run() {
        AppConfig config = ConfigManager.load();

        System.out.println();
        System.out.println("Config file : " + ConfigManager.getConfigFile());
        System.out.println("Index dir   : " + ConfigManager.getIndexDir());
        System.out.println();

        System.out.println("Watched directories:");
        if (config.getWatchedDirectories().isEmpty()) {
            System.out.println("  (none)");
        } else {
            config.getWatchedDirectories().forEach(d -> System.out.println("  " + d));
        }

        System.out.println();
        System.out.println("Ignored directories : " + config.getIgnoredDirectories());
        System.out.println("Supported extensions: " + config.getSupportedExtensions());
        System.out.printf ("Max text file size  : %d MB%n",
                config.getMaxTextFileSizeBytes() / (1024 * 1024));
        System.out.printf ("Max PDF file size   : %d MB%n",
                config.getMaxPdfFileSizeBytes()  / (1024 * 1024));
        System.out.println();
    }
}