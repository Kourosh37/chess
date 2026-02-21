package com.example.config;

public enum PieceStyle {
    CLASSIC("Classic"),
    MINIMAL("Minimal"),
    TOURNAMENT("Tournament");

    private final String label;

    PieceStyle(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
