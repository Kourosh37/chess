package com.example.config;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AppSettings {

    private final ObjectProperty<GameMode> gameMode = new SimpleObjectProperty<>(GameMode.SINGLE_PLAYER);
    private final ObjectProperty<Difficulty> difficulty = new SimpleObjectProperty<>(Difficulty.MEDIUM);
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.SANDSTONE);
    private final ObjectProperty<PieceStyle> pieceStyle = new SimpleObjectProperty<>(PieceStyle.CLASSIC);
    private final ObjectProperty<TimeControl> timeControl = new SimpleObjectProperty<>(TimeControl.NONE);
    private final StringProperty saveDirectory = new SimpleStringProperty(defaultSaveDirectoryPath().toString());
    private final BooleanProperty touchMoveRule = new SimpleBooleanProperty(false);
    private final BooleanProperty soundEnabled = new SimpleBooleanProperty(true);
    private final DoubleProperty sfxVolume = new SimpleDoubleProperty(0.8);
    private final DoubleProperty menuMusicVolume = new SimpleDoubleProperty(0.55);

    public static AppSettings defaultSettings() {
        return new AppSettings();
    }

    public ObjectProperty<GameMode> gameModeProperty() {
        return gameMode;
    }

    public ObjectProperty<Difficulty> difficultyProperty() {
        return difficulty;
    }

    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }

    public ObjectProperty<PieceStyle> pieceStyleProperty() {
        return pieceStyle;
    }

    public ObjectProperty<TimeControl> timeControlProperty() {
        return timeControl;
    }

    public StringProperty saveDirectoryProperty() {
        return saveDirectory;
    }

    public BooleanProperty touchMoveRuleProperty() {
        return touchMoveRule;
    }

    public BooleanProperty soundEnabledProperty() {
        return soundEnabled;
    }

    public DoubleProperty sfxVolumeProperty() {
        return sfxVolume;
    }

    public DoubleProperty menuMusicVolumeProperty() {
        return menuMusicVolume;
    }

    public static Path defaultSaveDirectoryPath() {
        return Paths.get(System.getProperty("user.home"), ".chess-studio", "saves").toAbsolutePath().normalize();
    }
}
