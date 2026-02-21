package com.example.persistence;

import com.example.config.Difficulty;
import com.example.config.GameMode;
import com.example.config.Theme;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record GameSaveRecord(
    String id,
    String name,
    Instant savedAt,
    String fen,
    GameMode gameMode,
    Difficulty difficulty,
    Theme theme,
    boolean soundEnabled,
    List<String> moveHistory,
    Path file
) {
}
