package com.example.bootstrap;

import com.example.ai.ChessAiService;
import com.example.ai.HybridChessAiService;
import com.example.audio.AudioService;
import com.example.audio.JavaFxAudioService;
import com.example.config.AppSettings;
import com.example.controller.MainController;
import com.example.game.ChessGameService;
import com.example.persistence.GamePersistenceService;
import com.example.persistence.SettingsPersistenceService;
import com.example.ui.ThemeService;
import javafx.scene.Scene;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApplicationContext {

    private final AppSettings settings;
    private final AudioService audioService;
    private final ChessAiService aiService;
    private final ChessGameService gameService;
    private final ThemeService themeService;
    private final GamePersistenceService persistenceService;
    private final SettingsPersistenceService settingsPersistenceService;
    private final ExecutorService aiExecutor;
    private final ExecutorService ioExecutor;

    private ApplicationContext(
        AppSettings settings,
        AudioService audioService,
        ChessAiService aiService,
        ChessGameService gameService,
        ThemeService themeService,
        GamePersistenceService persistenceService,
        SettingsPersistenceService settingsPersistenceService,
        ExecutorService aiExecutor,
        ExecutorService ioExecutor
    ) {
        this.settings = settings;
        this.audioService = audioService;
        this.aiService = aiService;
        this.gameService = gameService;
        this.themeService = themeService;
        this.persistenceService = persistenceService;
        this.settingsPersistenceService = settingsPersistenceService;
        this.aiExecutor = aiExecutor;
        this.ioExecutor = ioExecutor;
    }

    public static ApplicationContext bootstrap() {
        AppSettings settings = AppSettings.defaultSettings();
        SettingsPersistenceService settingsPersistenceService = new SettingsPersistenceService();
        settingsPersistenceService.loadInto(settings);

        AudioService audioService = new JavaFxAudioService(settings);
        audioService.playMenuMusic();
        ChessAiService aiService = new HybridChessAiService();
        ChessGameService gameService = new ChessGameService(settings, audioService);
        ThemeService themeService = new ThemeService();
        GamePersistenceService persistenceService = new GamePersistenceService(resolveSaveDirectory(settings));
        ExecutorService aiExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "chess-ai-worker");
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            return thread;
        });
        ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "chess-persistence-worker");
            thread.setDaemon(true);
            return thread;
        });

        ApplicationContext context = new ApplicationContext(
            settings,
            audioService,
            aiService,
            gameService,
            themeService,
            persistenceService,
            settingsPersistenceService,
            aiExecutor,
            ioExecutor
        );
        context.registerSettingsAutoSave();
        return context;
    }

    public Object createController(Class<?> controllerClass) {
        if (controllerClass == MainController.class) {
            return new MainController(settings, gameService, aiService, audioService, themeService, persistenceService, aiExecutor, ioExecutor);
        }

        try {
            return controllerClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Cannot create controller: " + controllerClass.getName(), e);
        }
    }

    public void attachScene(Scene scene) {
        settings.themeProperty().addListener((obs, oldTheme, newTheme) -> themeService.applyTheme(scene, newTheme));
    }

    public void shutdown() {
        settingsPersistenceService.save(settings);
        aiExecutor.shutdownNow();
        ioExecutor.shutdownNow();
    }

    public AppSettings settings() {
        return settings;
    }

    public ThemeService themeService() {
        return themeService;
    }

    private void registerSettingsAutoSave() {
        settings.gameModeProperty().addListener((obs, oldValue, newValue) -> settingsPersistenceService.save(settings));
        settings.difficultyProperty().addListener((obs, oldValue, newValue) -> settingsPersistenceService.save(settings));
        settings.themeProperty().addListener((obs, oldValue, newValue) -> settingsPersistenceService.save(settings));
        settings.pieceStyleProperty().addListener((obs, oldValue, newValue) -> settingsPersistenceService.save(settings));
        settings.timeControlProperty().addListener((obs, oldValue, newValue) -> settingsPersistenceService.save(settings));
        settings.saveDirectoryProperty().addListener((obs, oldValue, newValue) -> {
            persistenceService.setSaveDir(resolveSaveDirectory(settings));
            settingsPersistenceService.save(settings);
        });
        settings.touchMoveRuleProperty().addListener((obs, oldValue, newValue) -> settingsPersistenceService.save(settings));
        settings.soundEnabledProperty().addListener((obs, oldValue, newValue) -> settingsPersistenceService.save(settings));
        settings.sfxVolumeProperty().addListener((obs, oldValue, newValue) -> settingsPersistenceService.save(settings));
        settings.menuMusicVolumeProperty().addListener((obs, oldValue, newValue) -> settingsPersistenceService.save(settings));
    }

    private static Path resolveSaveDirectory(AppSettings settings) {
        String raw = settings.saveDirectoryProperty().get();
        if (raw == null || raw.isBlank()) {
            return AppSettings.defaultSaveDirectoryPath();
        }
        try {
            return Path.of(raw).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return AppSettings.defaultSaveDirectoryPath();
        }
    }
}
