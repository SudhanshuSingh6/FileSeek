package com.fileseek.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.*;

public class ConfigManager {

    private static final String CONFIG_DIR  = System.getProperty("user.home") + "/.fileseek";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.json";
    public  static final String INDEX_DIR   = CONFIG_DIR + "/index";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean isFirstRun() {
        return !Files.exists(Path.of(CONFIG_FILE));
    }

    public static AppConfig load() {
        Path file = Path.of(CONFIG_FILE);
        if (!Files.exists(file)) {
            return new AppConfig();
        }
        try {
            String json = Files.readString(file);
            return GSON.fromJson(json, AppConfig.class);
        } catch (IOException e) {
            System.err.println("[warn] Could not read config, using defaults: " + e.getMessage());
            return new AppConfig();
        }
    }

    public static void save(AppConfig config) {
        try {
            Files.createDirectories(Path.of(CONFIG_DIR));
            Files.createDirectories(Path.of(INDEX_DIR));
            Files.writeString(Path.of(CONFIG_FILE), GSON.toJson(config));
        } catch (IOException e) {
            System.err.println("[error] Could not save config: " + e.getMessage());
        }
    }

    public static void delete() {
        try {
            Files.deleteIfExists(Path.of(CONFIG_FILE));
        } catch (IOException e) {
            System.err.println("[error] Could not delete config: " + e.getMessage());
        }
    }

    public static String getIndexDir()  { return INDEX_DIR;   }
    public static String getConfigDir() { return CONFIG_DIR;  }
    public static String getConfigFile(){ return CONFIG_FILE; }
}