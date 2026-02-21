package com.example.config;

import java.util.List;

public enum Theme {
    SANDSTONE(
        "Sandstone",
        "/com/example/css/theme-sandstone.css",
        "#3c352d",
        "#d6c2a6",
        "#6f5942",
        "#d9b04b"
    ),
    OCEAN_DUSK(
        "Ocean Dusk",
        "/com/example/css/theme-ocean-dusk.css",
        "#243744",
        "#bdd7e4",
        "#4a6777",
        "#67c3a4"
    ),
    FOREST_FOG(
        "Forest Fog",
        "/com/example/css/theme-forest-fog.css",
        "#2d3932",
        "#c9d5c4",
        "#597060",
        "#7ac79a"
    ),
    ROSE_ASH(
        "Rose Ash",
        "/com/example/css/theme-rose-ash.css",
        "#3b313b",
        "#e0c8cf",
        "#7a5c67",
        "#d8a2b4"
    ),
    SLATE_GOLD(
        "Slate Gold",
        "/com/example/css/theme-slate-gold.css",
        "#2d333c",
        "#d3c6ac",
        "#5b6675",
        "#ccad72"
    ),
    AURORA_TEAL(
        "Aurora Teal",
        "/com/example/css/theme-aurora-teal.css",
        "#254049",
        "#c4ddd6",
        "#4f7580",
        "#73d6c6"
    ),
    GRAPHITE_CREAM(
        "Graphite Cream",
        "/com/example/css/theme-graphite-cream.css",
        "#282a2f",
        "#d2cab8",
        "#676b75",
        "#c9b994"
    );

    private final String label;
    private final String stylesheet;
    private final String shellColor;
    private final String lightBoardColor;
    private final String darkBoardColor;
    private final String accentColor;

    Theme(
        String label,
        String stylesheet,
        String shellColor,
        String lightBoardColor,
        String darkBoardColor,
        String accentColor
    ) {
        this.label = label;
        this.stylesheet = stylesheet;
        this.shellColor = shellColor;
        this.lightBoardColor = lightBoardColor;
        this.darkBoardColor = darkBoardColor;
        this.accentColor = accentColor;
    }

    public String stylesheet() {
        return stylesheet;
    }

    public List<String> previewColors() {
        return List.of(shellColor, lightBoardColor, darkBoardColor, accentColor);
    }

    @Override
    public String toString() {
        return label;
    }
}
