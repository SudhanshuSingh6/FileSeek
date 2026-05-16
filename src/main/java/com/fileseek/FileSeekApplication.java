package com.fileseek;

import com.fileseek.cli.FileSeekCommand;
import picocli.CommandLine;

public class FileSeekApplication {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new FileSeekCommand()).execute(args);
        System.exit(exitCode);
    }
}