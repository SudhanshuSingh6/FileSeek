package com.fileseek.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Command(
        name = "fileseek",
        mixinStandardHelpOptions = true,
        version = "FileSeek 1.0",
        description = {
                "",
                "Local file indexing and full-text search engine.",
                "Indexes your directories once, then searches in milliseconds.",
                ""
        },
        header = {
        "@|bold,cyan  _______ _________ _        _______  _______  _______  _______  _       |@",
                "@|bold,cyan (  ____ \\\\__   __/( \\      (  ____ \\(  ____ \\(  ____ \\(  ____ \\| \\    /\\|@",
                "@|bold,cyan | (    \\/   ) (   | (      | (    \\/| (    \\/| (    \\/| (    \\/|  \\  / /|@",
                "@|bold,cyan | (__       | |   | |      | (__    | (_____ | (__    | (__    |  (_/ / |@",
                "@|bold,cyan |  __)      | |   | |      |  __)   (_____  )|  __)   |  __)   |   _ (  |@",
                "@|bold,cyan | (         | |   | |      | (            ) || (      | (      |  ( \\ \\ |@",
                "@|bold,cyan | )      ___) (___| (____/\\| (____/\\/\\____) || (____/\\| (____/\\|  /  \\ \\ |@",
                "@|bold,cyan |/       \\_______/(_______/(_______/\\_______)(_______/(_______/|_/    \\/|@",
                ""
        },
        footer = {
                "",
                "Examples:",
                "  @|yellow fileseek add ~/Projects|@              index a directory",
                "  @|yellow fileseek search \"redis\"|@              keyword search",
                "  @|yellow fileseek search \"\\\"spring boot\\\"\"|@     phrase search",
                "  @|yellow fileseek search \"sprng\" --fuzzy|@      typo-tolerant",
                "  @|yellow fileseek search \"dock\" --prefix|@      autocomplete",
                "  @|yellow fileseek search \"s.*boot\" --regex|@    regex search",
                "  @|yellow fileseek search \"redis\" --ext .java|@  filter by extension",
                "  @|yellow fileseek watch|@                       live index updates",
                "  @|yellow fileseek stats|@                       index statistics",
                "",
                "Documentation: https://github.com/SudhanshuSingh6/fileseek",
                ""
        },
        subcommands = {
                SearchCommand.class,
                AddCommand.class,
                RemoveCommand.class,
                ConfigCommand.class,
                ResetCommand.class,
                WatchCommand.class,
                HistoryCommand.class,
                StatsCommand.class,
                picocli.AutoComplete.GenerateCompletion.class,   // shell completion
                CommandLine.HelpCommand.class
        }
)
public class FileSeekCommand implements Runnable {

    @Option(
            names = {"-v", "--verbose"},
            description = "Show query tokens, score breakdowns, and index details.",
            scope = CommandLine.ScopeType.INHERIT
    )
    public static boolean verbose = false;

    @Override
    public void run() {
        printBanner();
        CommandLine.usage(this, System.out);
    }

    private void printBanner() {
        try (InputStream is = getClass().getResourceAsStream("/banner.txt")) {
            if (is != null) {
                System.out.println(
                        new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }
}