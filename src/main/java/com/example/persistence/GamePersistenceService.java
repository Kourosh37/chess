package com.example.persistence;

import com.example.config.Difficulty;
import com.example.config.GameMode;
import com.example.config.Theme;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

public class GamePersistenceService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private Path saveDir;

    public GamePersistenceService() {
        this(Paths.get(System.getProperty("user.home"), ".chess-studio", "saves"));
    }

    public GamePersistenceService(Path saveDir) {
        setSaveDir(saveDir);
    }

    public synchronized void setSaveDir(Path saveDir) {
        if (saveDir == null) {
            throw new IllegalArgumentException("saveDir cannot be null");
        }
        this.saveDir = saveDir.toAbsolutePath().normalize();
    }

    public synchronized Path getSaveDir() {
        return saveDir;
    }

    public synchronized GameSaveRecord save(GameSaveRecord record) {
        ensureSaveDir();
        String id = record.id() == null || record.id().isBlank() ? UUID.randomUUID().toString() : record.id();
        Instant now = Instant.now();
        Path target = resolveTarget(record, id);

        Properties properties = new Properties();
        properties.setProperty("id", id);
        properties.setProperty("name", valueOrDefault(record.name(), "Saved Game"));
        properties.setProperty("savedAt", FORMATTER.format(now));
        properties.setProperty("fen", record.fen());
        properties.setProperty("gameMode", record.gameMode().name());
        properties.setProperty("difficulty", record.difficulty().name());
        properties.setProperty("theme", record.theme().name());
        properties.setProperty("soundEnabled", String.valueOf(record.soundEnabled()));
        properties.setProperty("moveCount", String.valueOf(record.moveHistory().size()));
        for (int i = 0; i < record.moveHistory().size(); i++) {
            properties.setProperty("move." + i, record.moveHistory().get(i));
        }

        try (OutputStream out = Files.newOutputStream(target)) {
            properties.store(out, "chess Save");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save game", e);
        }

        return new GameSaveRecord(
            id,
            valueOrDefault(record.name(), "Saved Game"),
            now,
            record.fen(),
            record.gameMode(),
            record.difficulty(),
            record.theme(),
            record.soundEnabled(),
            List.copyOf(record.moveHistory()),
            target
        );
    }

    public synchronized List<GameSaveRecord> list() {
        ensureSaveDir();
        try (Stream<Path> stream = Files.list(saveDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".save"))
                .map(this::loadFromFile)
                .filter(record -> record != null)
                .sorted(Comparator.comparing(GameSaveRecord::savedAt).reversed())
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to list saves", e);
        }
    }

    public synchronized GameSaveRecord load(Path file) {
        return loadFromFile(file);
    }

    public synchronized boolean delete(GameSaveRecord record) {
        if (record == null || record.file() == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(record.file());
        } catch (IOException e) {
            return false;
        }
    }

    private GameSaveRecord loadFromFile(Path file) {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            return null;
        }

        String id = properties.getProperty("id", file.getFileName().toString());
        String name = properties.getProperty("name", "Saved Game");
        Instant savedAt = Instant.parse(properties.getProperty("savedAt", FORMATTER.format(Instant.now())));
        String fen = properties.getProperty("fen");
        if (fen == null || fen.isBlank()) {
            return null;
        }

        GameMode mode = parseEnum(properties.getProperty("gameMode"), GameMode.class, GameMode.SINGLE_PLAYER);
        Difficulty difficulty = parseEnum(properties.getProperty("difficulty"), Difficulty.class, Difficulty.MEDIUM);
        Theme theme = parseEnum(properties.getProperty("theme"), Theme.class, Theme.SANDSTONE);
        boolean soundEnabled = Boolean.parseBoolean(properties.getProperty("soundEnabled", "true"));

        int moveCount = Integer.parseInt(properties.getProperty("moveCount", "0"));
        List<String> history = new ArrayList<>();
        for (int i = 0; i < moveCount; i++) {
            history.add(properties.getProperty("move." + i, ""));
        }

        return new GameSaveRecord(id, name, savedAt, fen, mode, difficulty, theme, soundEnabled, history, file);
    }

    private void ensureSaveDir() {
        try {
            Files.createDirectories(saveDir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initialize save directory: " + saveDir, e);
        }
    }

    private Path resolveTarget(GameSaveRecord record, String id) {
        if (record.file() != null) {
            return record.file();
        }
        return saveDir.resolve(id + ".save");
    }

    private String valueOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
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
}
