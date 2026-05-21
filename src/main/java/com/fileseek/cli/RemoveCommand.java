package com.fileseek.cli;

import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import com.fileseek.util.PathUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = "remove",
        mixinStandardHelpOptions = true,
        description = "Remove a directory from the FileSeek index."
)
public class RemoveCommand implements Runnable {

    @Parameters(index = "0", description = "Directory path to remove.")
    private String path;

    @Override
    public void run() {
        String resolved = path.replace("~", System.getProperty("user.home"));
        String absolute = PathUtils.expand(path).toString();

        AppConfig config = ConfigManager.load();
        boolean removed = config.removeWatchedDirectory(absolute);
        ConfigManager.save(config);

        if (removed) {
            System.out.println("Removed: " + absolute);
        } else {
            System.out.println("Not found in config: " + absolute);
        }
    }
}