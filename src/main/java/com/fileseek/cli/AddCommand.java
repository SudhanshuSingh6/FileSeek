package com.fileseek.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
        name = "add",
        mixinStandardHelpOptions = true,
        description = "Add a directory to the FileSeek index."
)
public class AddCommand implements Runnable {

    @Parameters(index = "0", description = "Directory path to add.")
    private String path;

    @Override
    public void run() {
        System.out.println("add: " + path);
    }
}