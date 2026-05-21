package com.fileseek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.*;

public class ConfigManager {

    private static final Path CONFIG_DIR_PATH =
            Path.of(System.getProperty("user.home"), ".fileseek");

    private static final Path CONFIG_FILE_PATH =
            CONFIG_DIR_PATH.resolve("config.json");

    private static final Path INDEX_DIR_PATH =
            CONFIG_DIR_PATH.resolve("index");

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    public static boolean isFirstRun() {
        return !Files.exists(CONFIG_FILE_PATH);
    }

    public static AppConfig load() {
        if (!Files.exists(CONFIG_FILE_PATH)) {
            return new AppConfig();
        }
        try {
            String json = Files.readString(CONFIG_FILE_PATH);
            return GSON.fromJson(json, AppConfig.class);
        } catch (IOException e) {
            System.err.println("[warn] Could not read config: " + e.getMessage());
            return new AppConfig();
        }
    }

    public static void save(AppConfig config) {
        try {
            Files.createDirectories(CONFIG_DIR_PATH);
            Files.createDirectories(INDEX_DIR_PATH);
            Files.writeString(CONFIG_FILE_PATH, GSON.toJson(config));
        } catch (IOException e) {
            System.err.println("[error] Could not save config: " + e.getMessage());
        }
    }

    public static void delete() {
        try {
            Files.deleteIfExists(CONFIG_FILE_PATH);
        } catch (IOException e) {
            System.err.println("[error] Could not delete config: " + e.getMessage());
        }
    }

    public static Path getConfigDirPath() {
        return CONFIG_DIR_PATH;
    }

    public static Path getIndexDirPath() {
        return INDEX_DIR_PATH;
    }

    public static Path getConfigFilePath() {
        return CONFIG_FILE_PATH;
    }

    public static String getConfigDir() {
        return CONFIG_DIR_PATH.toString();
    }

    public static String getIndexDir() {
        return INDEX_DIR_PATH.toString();
    }

    public static String getConfigFile() {
        return CONFIG_FILE_PATH.toString();
    }
}