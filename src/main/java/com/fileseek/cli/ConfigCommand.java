package com.fileseek.cli;

import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import com.fileseek.index.IndexManager;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "config",
        mixinStandardHelpOptions = true,
        description = "Display current configuration and index status."
)
public class ConfigCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        AppConfig config = ConfigManager.load();

        System.out.println();
        System.out.println("Paths");
        System.out.println("  Config : " + ConfigManager.getConfigFile());
        System.out.println("  Index  : " + ConfigManager.getIndexDir());
        System.out.println();

        System.out.println("Watched Directories");
        if (config.getWatchedDirectories().isEmpty()) {
            System.out.println("  (none — run 'fileseek add <directory>')");
        } else {
            config.getWatchedDirectories().forEach(dir -> {
                boolean exists = Files.isDirectory(Path.of(dir));
                System.out.printf("  %s%s%n",
                        dir, exists ? "" : "  [missing]");
            });
        }

        System.out.println();
        System.out.println("Rules");
        System.out.println("  Ignored dirs : " + config.getIgnoredDirectories());
        System.out.println("  Extensions   : " + config.getSupportedExtensions());
        System.out.printf("  Max text size: %d MB%n",
                config.getMaxTextFileSizeBytes() / (1024 * 1024));
        System.out.printf("  Max PDF size : %d MB%n",
                config.getMaxPdfFileSizeBytes() / (1024 * 1024));

        System.out.println();
        System.out.println("Index Status");
        if (IndexManager.indexExists()) {
            IndexManager mgr = new IndexManager();
            mgr.load();
            System.out.printf("  Documents : %,d%n", mgr.documentCount());
            System.out.printf("  Terms     : %,d%n", mgr.termCount());
            try {
                long bytes = Files.size(IndexManager.getIndexFile());
                System.out.printf("  File size : %.1f MB%n",
                        bytes / (1024.0 * 1024));
            } catch (Exception ignored) {
            }
        } else {
            System.out.println("  No index — run 'fileseek add <directory>'");
        }

        System.out.println();
        return 0;
    }
}