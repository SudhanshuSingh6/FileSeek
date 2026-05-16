package com.fileseek.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

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
        System.out.println("remove: " + path);
    }
}