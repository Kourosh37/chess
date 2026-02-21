package com.example.ui;

import com.example.config.Theme;
import javafx.scene.Scene;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ThemeService {

    public void applyTheme(Scene scene, Theme theme) {
        List<String> stylesheets = new ArrayList<>();
        stylesheets.add(require("/com/example/css/base.css"));
        stylesheets.add(require(theme.stylesheet()));
        scene.getStylesheets().setAll(stylesheets);
    }

    private String require(String path) {
        URL url = getClass().getResource(path);
        if (url == null) {
            throw new IllegalStateException("Missing stylesheet: " + path);
        }
        return url.toExternalForm();
    }
}
