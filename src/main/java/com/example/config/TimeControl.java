package com.example.config;

public enum TimeControl {
    NONE("No Limit", 0),
    SEC_30("30 sec", 30),
    SEC_60("60 sec", 60),
    SEC_120("120 sec", 120),
    SEC_300("300 sec", 300);

    private final String label;
    private final int secondsPerTurn;

    TimeControl(String label, int secondsPerTurn) {
        this.label = label;
        this.secondsPerTurn = secondsPerTurn;
    }

    public int secondsPerTurn() {
        return secondsPerTurn;
    }

    public boolean isEnabled() {
        return secondsPerTurn > 0;
    }

    @Override
    public String toString() {
        return label;
    }
}
