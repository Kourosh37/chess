package com.example.config;

public enum Difficulty {
    EASY("Easy", 1),
    MEDIUM("Medium", 2),
    HARD("Hard", 4);

    private final String label;
    private final int searchDepth;

    Difficulty(String label, int searchDepth) {
        this.label = label;
        this.searchDepth = searchDepth;
    }

    public int searchDepth() {
        return searchDepth;
    }

    @Override
    public String toString() {
        return label;
    }
}
