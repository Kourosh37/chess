package com.example;

import com.example.bootstrap.ApplicationContext;
import com.example.controller.MainController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.net.URL;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        ApplicationContext context = ApplicationContext.bootstrap();

        FXMLLoader loader = new FXMLLoader(requireResource("/com/example/fxml/main-view.fxml"));
        loader.setControllerFactory(context::createController);
        Parent root = loader.load();
        MainController controller = loader.getController();

        Rectangle2D primaryBounds = Screen.getPrimary().getBounds();
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double initialWidth = primaryBounds.getWidth();
        double initialHeight = primaryBounds.getHeight();

        Scene scene = new Scene(root, initialWidth, initialHeight);
        context.themeService().applyTheme(scene, context.settings().themeProperty().get());

        stage.setTitle("Chess Studio");
        stage.setMinWidth(Math.min(1040, visualBounds.getWidth()));
        stage.setMinHeight(Math.min(700, visualBounds.getHeight()));
        stage.setFullScreenExitHint("");
        stage.fullScreenProperty().addListener((obs, wasFull, isFull) -> {
            if (!isFull) {
                clampStageToVisualBounds(stage, visualBounds);
            }
        });
        stage.widthProperty().addListener((obs, oldW, newW) -> {
            if (!stage.isFullScreen() && newW.doubleValue() > visualBounds.getWidth()) {
                stage.setWidth(visualBounds.getWidth());
            }
        });
        stage.heightProperty().addListener((obs, oldH, newH) -> {
            if (!stage.isFullScreen() && newH.doubleValue() > visualBounds.getHeight()) {
                stage.setHeight(visualBounds.getHeight());
            }
        });
        stage.xProperty().addListener((obs, oldX, newX) -> {
            if (!stage.isFullScreen()) {
                clampStageToVisualBounds(stage, visualBounds);
            }
        });
        stage.yProperty().addListener((obs, oldY, newY) -> {
            if (!stage.isFullScreen()) {
                clampStageToVisualBounds(stage, visualBounds);
            }
        });
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (controller != null) {
                controller.autoSaveBeforeExit();
            }
            context.shutdown();
        });
        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.show();
        Platform.runLater(() -> {
            if (!stage.isFullScreen()) {
                stage.setFullScreen(true);
            }
        });

        context.attachScene(scene);
    }

    public static void main(String[] args) {
        launch();
    }

    private URL requireResource(String path) {
        URL resource = App.class.getResource(path);
        if (resource == null) {
            throw new IllegalStateException("Missing required resource: " + path);
        }
        return resource;
    }

    private void clampStageToVisualBounds(Stage stage, Rectangle2D bounds) {
        double width = Math.min(stage.getWidth(), bounds.getWidth());
        double height = Math.min(stage.getHeight(), bounds.getHeight());
        if (width != stage.getWidth()) {
            stage.setWidth(width);
        }
        if (height != stage.getHeight()) {
            stage.setHeight(height);
        }

        double minX = bounds.getMinX();
        double minY = bounds.getMinY();
        double maxX = bounds.getMaxX() - stage.getWidth();
        double maxY = bounds.getMaxY() - stage.getHeight();

        if (stage.getX() < minX) {
            stage.setX(minX);
        } else if (stage.getX() > maxX) {
            stage.setX(maxX);
        }
        if (stage.getY() < minY) {
            stage.setY(minY);
        } else if (stage.getY() > maxY) {
            stage.setY(maxY);
        }
    }
}
