package com.example.config;

public enum GameMode {
    SINGLE_PLAYER("Single Player"),
    TWO_PLAYER("Two Player");

    private final String label;

    GameMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
