package com.fileseek;

import com.fileseek.cli.FileSeekCommand;
import com.fileseek.config.ConfigManager;
import com.fileseek.config.FirstRunSetup;
import picocli.CommandLine;

public class FileSeekApplication {

    public static void main(String[] args) {
        if (ConfigManager.isFirstRun() && !isBypassCommand(args)) {
            new FirstRunSetup().run();
        }

        int exitCode = new CommandLine(new FileSeekCommand()).execute(args);
        System.exit(exitCode);
    }

    private static boolean isBypassCommand(String[] args) {
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")
                    || arg.equals("--version") || arg.equals("-V")) {
                return true;
            }
        }
        return false;
    }
}