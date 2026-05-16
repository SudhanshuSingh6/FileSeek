package com.fileseek.cli;

import picocli.CommandLine.Command;

@Command(
        name = "config",
        mixinStandardHelpOptions = true,
        description = "Display current FileSeek configuration."
)
public class ConfigCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("config: (not yet implemented)");
    }
}