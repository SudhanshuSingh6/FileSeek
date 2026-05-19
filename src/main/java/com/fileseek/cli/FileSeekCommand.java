package com.fileseek.cli;

import com.fileseek.cli.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Command(
        name = "fileseek",
        mixinStandardHelpOptions = true,
        version = "FileSeek 1.0",
        description = "A fast local file indexing and full-text search engine.",
        subcommands = {
                SearchCommand.class,
                AddCommand.class,
                RemoveCommand.class,
                ConfigCommand.class,
                ResetCommand.class,
                WatchCommand.class,          // added
                CommandLine.HelpCommand.class
        }
)
public class FileSeekCommand implements Runnable {

    @Override
    public void run() {
        printBanner();
        CommandLine.usage(this, System.out);
    }

    private void printBanner() {
        try (InputStream is = getClass().getResourceAsStream("/banner.txt")) {
            if (is != null) {
                String banner = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println(banner);
            }
        } catch (Exception e) {
        }
    }
}