package com.fileseek.cli;

import picocli.CommandLine.Command;

@Command(
        name = "reset",
        mixinStandardHelpOptions = true,
        description = "Reset all FileSeek configuration and indexes."
)
public class ResetCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("reset: (not yet implemented)");
    }
}