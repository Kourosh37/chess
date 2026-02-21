package com.example.persistence;

import com.example.config.AppSettings;
import com.example.config.Difficulty;
import com.example.config.GameMode;
import com.example.config.PieceStyle;
import com.example.config.TimeControl;
import com.example.config.Theme;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class SettingsPersistenceService {

    private final Path settingsFile;

    public SettingsPersistenceService() {
        this(Paths.get(System.getProperty("user.home"), ".chess-studio", "settings.properties"));
    }

    public SettingsPersistenceService(Path settingsFile) {
        this.settingsFile = settingsFile;
    }

    public void loadInto(AppSettings settings) {
        if (!Files.exists(settingsFile)) {
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(settingsFile)) {
            props.load(in);
        } catch (IOException ignored) {
            return;
        }

        settings.gameModeProperty().set(parseEnum(props.getProperty("gameMode"), GameMode.class, settings.gameModeProperty().get()));
        settings.difficultyProperty().set(parseEnum(props.getProperty("difficulty"), Difficulty.class, settings.difficultyProperty().get()));
        settings.themeProperty().set(parseEnum(props.getProperty("theme"), Theme.class, settings.themeProperty().get()));
        settings.pieceStyleProperty().set(parseEnum(props.getProperty("pieceStyle"), PieceStyle.class, settings.pieceStyleProperty().get()));
        settings.timeControlProperty().set(parseEnum(props.getProperty("timeControl"), TimeControl.class, settings.timeControlProperty().get()));
        settings.saveDirectoryProperty().set(sanitizeSaveDirectory(props.getProperty("saveDirectory"), settings.saveDirectoryProperty().get()));
        settings.touchMoveRuleProperty().set(Boolean.parseBoolean(props.getProperty("touchMoveRule", String.valueOf(settings.touchMoveRuleProperty().get()))));
        settings.soundEnabledProperty().set(Boolean.parseBoolean(props.getProperty("soundEnabled", String.valueOf(settings.soundEnabledProperty().get()))));
        settings.sfxVolumeProperty().set(parseDouble(props.getProperty("sfxVolume"), settings.sfxVolumeProperty().get()));
        double legacyMusic = parseDouble(props.getProperty("musicVolume"), settings.menuMusicVolumeProperty().get());
        settings.menuMusicVolumeProperty().set(parseDouble(props.getProperty("menuMusicVolume"), legacyMusic));
    }

    public void save(AppSettings settings) {
        try {
            Files.createDirectories(settingsFile.getParent());
        } catch (IOException e) {
            return;
        }

        Properties props = new Properties();
        props.setProperty("gameMode", settings.gameModeProperty().get().name());
        props.setProperty("difficulty", settings.difficultyProperty().get().name());
        props.setProperty("theme", settings.themeProperty().get().name());
        props.setProperty("pieceStyle", settings.pieceStyleProperty().get().name());
        props.setProperty("timeControl", settings.timeControlProperty().get().name());
        props.setProperty("saveDirectory", settings.saveDirectoryProperty().get());
        props.setProperty("touchMoveRule", String.valueOf(settings.touchMoveRuleProperty().get()));
        props.setProperty("soundEnabled", String.valueOf(settings.soundEnabledProperty().get()));
        props.setProperty("sfxVolume", String.valueOf(settings.sfxVolumeProperty().get()));
        props.setProperty("menuMusicVolume", String.valueOf(settings.menuMusicVolumeProperty().get()));
        props.setProperty("musicVolume", String.valueOf(settings.menuMusicVolumeProperty().get()));

        try (OutputStream out = Files.newOutputStream(settingsFile)) {
            props.store(out, "chess Settings");
        } catch (IOException ignored) {
        }
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> type, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String sanitizeSaveDirectory(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Path.of(value).toAbsolutePath().normalize().toString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
