package com.example.controller;

import com.example.ai.ChessAiService;
import com.example.audio.AudioService;
import com.example.audio.SoundEffect;
import com.example.config.AppSettings;
import com.example.config.Difficulty;
import com.example.config.GameMode;
import com.example.config.PieceStyle;
import com.example.config.Theme;
import com.example.config.TimeControl;
import com.example.game.ChessGameService;
import com.example.game.MoveOutcome;
import com.example.persistence.GamePersistenceService;
import com.example.persistence.GameSaveRecord;
import com.example.ui.ChessBoardView;
import com.example.ui.ThemeService;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MainController {

    private enum Page {
        MAIN_MENU,
        SETTINGS,
        LOAD,
        GAME
    }

    private static final DateTimeFormatter SAVE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());
    private static final long MIN_AI_MOVE_DELAY_MILLIS = 2_000L;

    private final AppSettings settings;
    private final ChessGameService gameService;
    private final ChessAiService aiService;
    private final AudioService audioService;
    private final ThemeService themeService;
    private final GamePersistenceService persistenceService;
    private final ExecutorService aiExecutor;
    private final ExecutorService ioExecutor;
    private final AppSettings defaults = AppSettings.defaultSettings();

    @FXML
    private StackPane rootStack;
    @FXML
    private VBox toastContainer;

    @FXML
    private VBox mainMenuPane;
    @FXML
    private VBox settingsPane;
    @FXML
    private VBox loadPane;
    @FXML
    private BorderPane gamePane;

    @FXML
    private ComboBox<GameMode> settingsGameModeCombo;
    @FXML
    private ComboBox<Difficulty> settingsDifficultyCombo;
    @FXML
    private ComboBox<TimeControl> settingsTimeControlCombo;
    @FXML
    private ComboBox<Theme> settingsThemeCombo;
    @FXML
    private ComboBox<PieceStyle> settingsPieceStyleCombo;
    @FXML
    private ToggleButton settingsTouchMoveToggle;
    @FXML
    private TextField settingsSaveDirectoryField;
    @FXML
    private ToggleButton settingsSoundToggle;
    @FXML
    private Slider settingsSfxSlider;
    @FXML
    private Slider settingsMenuMusicSlider;
    @FXML
    private Label settingsInfoLabel;

    @FXML
    private ListView<GameSaveRecord> loadGamesListView;
    @FXML
    private Label loadStatusLabel;

    @FXML
    private StackPane boardContainer;
    @FXML
    private Label gameStatusLabel;
    @FXML
    private Label turnLabel;
    @FXML
    private Label aiStateLabel;
    @FXML
    private Label whiteTimerLabel;
    @FXML
    private Label blackTimerLabel;
    @FXML
    private Label messageLabel;
    @FXML
    private ListView<String> movesListView;
    @FXML
    private Label capturedByWhiteLabel;
    @FXML
    private Label capturedByBlackLabel;

    @FXML
    private StackPane confirmOverlay;
    @FXML
    private Label confirmTitleLabel;
    @FXML
    private Label confirmMessageLabel;
    @FXML
    private Button confirmYesButton;
    @FXML
    private Button confirmNoButton;

    private ChessBoardView boardView;

    private String selectedSquare;
    private Set<String> legalTargets = new HashSet<>();
    private boolean aiThinking;
    private boolean paused;
    private boolean animatingMove;
    private boolean gameOverDialogShown;
    private int saveSelectionAnchor = -1;
    private GameSaveRecord currentGameSave;
    private String currentGameName;
    private String currentGameSaveId;
    private final AtomicLong saveRefreshToken = new AtomicLong();
    private final AtomicBoolean saveWriteInProgress = new AtomicBoolean(false);
    private final AtomicReference<GameSaveRecord> pendingSaveSnapshot = new AtomicReference<>();
    private boolean savesDirty = true;
    private Map<String, Piece> boardSnapshot = Collections.emptyMap();
    private final AtomicLong aiRequestToken = new AtomicLong();
    private PauseTransition aiMoveDelayTransition;

    private Page currentPage = Page.MAIN_MENU;

    private Runnable confirmAction;

    private Timeline turnClock;
    private int whiteSecondsRemaining;
    private int blackSecondsRemaining;
    private boolean timeOutEnded;

    public MainController(
        AppSettings settings,
        ChessGameService gameService,
        ChessAiService aiService,
        AudioService audioService,
        ThemeService themeService,
        GamePersistenceService persistenceService,
        ExecutorService aiExecutor,
        ExecutorService ioExecutor
    ) {
        this.settings = settings;
        this.gameService = gameService;
        this.aiService = aiService;
        this.audioService = audioService;
        this.themeService = themeService;
        this.persistenceService = persistenceService;
        this.aiExecutor = aiExecutor;
        this.ioExecutor = ioExecutor;
    }

    @FXML
    private void initialize() {
        boardView = new ChessBoardView(this::onSquareClicked);
        boardContainer.getChildren().setAll(boardView);

        setupSettingsControls();
        setupLoadList();

        gameService.resetGame();
        currentGameSave = null;
        currentGameName = null;
        currentGameSaveId = null;
        setAiThinkingState(false);
        reloadBoardSnapshot();
        resetTimersForNewGame();
        refreshBoard();
        refreshMeta();
        showPage(Page.MAIN_MENU, false);

        Platform.runLater(() -> {
            installButtonAnimations();
            rootStack.requestFocus();
        });
    }

    @FXML
    private void onRootKeyPressed(KeyEvent event) {
        if (confirmOverlay.isVisible()) {
            if (event.getCode() == KeyCode.ENTER) {
                onConfirmYes();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                onConfirmNo();
                event.consume();
            }
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.A && currentPage == Page.LOAD) {
            loadGamesListView.getSelectionModel().selectAll();
            showToast("All saves selected", "toast-info");
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.DELETE && currentPage == Page.LOAD) {
            onDeleteSelectedSave();
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.ENTER) {
            switch (currentPage) {
                case MAIN_MENU -> onStartNewGame();
                case SETTINGS -> onSettingsSaveAndBack();
                case LOAD -> onLoadSelectedGame();
                case GAME -> showToast("Auto-save is enabled", "toast-info");
            }
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.ESCAPE) {
            switch (currentPage) {
                case SETTINGS -> onSettingsBack();
                case LOAD -> onLoadBack();
                case GAME -> onGameBackToMenu();
                default -> {
                }
            }
            event.consume();
        }
    }

    @FXML
    private void onStartNewGame() {
        cancelPendingAiMove();
        autoSaveCurrentGame();
        currentGameSave = null;
        currentGameName = null;
        currentGameSaveId = null;
        resetSelection();
        paused = false;
        timeOutEnded = false;
        gameOverDialogShown = false;
        gameService.resetGame();
        reloadBoardSnapshot();
        resetTimersForNewGame();
        refreshBoard();
        refreshMeta();
        showPage(Page.GAME, false);
        audioService.play(SoundEffect.GAME_START);
        showToast("New game started", "toast-success");
        requestAiIfNeeded();
    }

    @FXML
    private void onOpenSettings() {
        loadSettingsIntoControls();
        showPage(Page.SETTINGS, true);
    }

    @FXML
    private void onOpenLoadGame() {
        refreshSaves(true);
        showPage(Page.LOAD, true);
    }

    @FXML
    private void onExitApp() {
        showConfirm("Exit", "Are you sure you want to exit?", () -> {
            cancelPendingAiMove();
            autoSaveCurrentGame();
            showToast("Session saved", "toast-success");
            PauseTransition pause = new PauseTransition(Duration.millis(220));
            pause.setOnFinished(e -> Platform.exit());
            pause.play();
        });
    }

    @FXML
    private void onSettingsSaveAndBack() {
        if (!applyControlsToSettings()) {
            return;
        }
        settingsInfoLabel.setText("Settings saved.");
        showToast("Settings persisted", "toast-success");
        if (currentPage == Page.GAME) {
            resetTimersForCurrentTurn();
        }
        showPage(Page.MAIN_MENU, true);
    }

    @FXML
    private void onSettingsApply() {
        if (!applyControlsToSettings()) {
            return;
        }
        settingsInfoLabel.setText("Settings applied.");
        showToast("Settings applied", "toast-success");
        if (currentPage == Page.GAME) {
            resetTimersForCurrentTurn();
        }
    }

    @FXML
    private void onSettingsBack() {
        loadSettingsIntoControls();
        showPage(Page.MAIN_MENU, true);
    }

    @FXML
    private void onSettingsResetDefaults() {
        settingsGameModeCombo.setValue(defaults.gameModeProperty().get());
        settingsDifficultyCombo.setValue(defaults.difficultyProperty().get());
        settingsTimeControlCombo.setValue(defaults.timeControlProperty().get());
        settingsThemeCombo.setValue(defaults.themeProperty().get());
        settingsPieceStyleCombo.setValue(defaults.pieceStyleProperty().get());
        settingsTouchMoveToggle.setSelected(defaults.touchMoveRuleProperty().get());
        settingsSaveDirectoryField.setText(defaults.saveDirectoryProperty().get());
        settingsSoundToggle.setSelected(defaults.soundEnabledProperty().get());
        settingsSfxSlider.setValue(defaults.sfxVolumeProperty().get());
        settingsMenuMusicSlider.setValue(defaults.menuMusicVolumeProperty().get());
        settingsInfoLabel.setText("Defaults loaded. Click Apply or Save & Back.");
        showToast("Default settings loaded", "toast-info");
    }

    @FXML
    private void onSettingsUseDefaultSaveDirectory() {
        settingsSaveDirectoryField.setText(AppSettings.defaultSaveDirectoryPath().toString());
        settingsInfoLabel.setText("Default save folder selected. Click Apply or Save & Back.");
    }

    @FXML
    private void onSettingsBrowseSaveDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Save Folder");

        Path initialDir = resolveInitialDirectory(settingsSaveDirectoryField.getText());
        if (initialDir != null && Files.isDirectory(initialDir)) {
            chooser.setInitialDirectory(initialDir.toFile());
        }

        Window owner = rootStack != null && rootStack.getScene() != null ? rootStack.getScene().getWindow() : null;
        File selected = chooser.showDialog(owner);
        if (selected == null) {
            return;
        }

        String resolved = selected.toPath().toAbsolutePath().normalize().toString();
        settingsSaveDirectoryField.setText(resolved);
        settingsInfoLabel.setText("Save folder selected. Click Apply or Save & Back.");
    }

    @FXML
    private void onLoadBack() {
        showPage(Page.MAIN_MENU, true);
    }

    @FXML
    private void onLoadRefresh() {
        refreshSaves(true);
    }

    @FXML
    private void onLoadSelectedGame() {
        GameSaveRecord selected = loadGamesListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showToast("Select a save first", "toast-error");
            return;
        }

        cancelPendingAiMove();
        gameService.restore(selected.fen(), selected.moveHistory());
        reloadBoardSnapshot();
        currentGameSave = selected;
        currentGameName = selected.name();
        currentGameSaveId = selected.id();
        resetSelection();
        paused = false;
        timeOutEnded = false;
        gameOverDialogShown = false;
        resetTimersForCurrentTurn();

        showPage(Page.GAME, false);
        refreshBoard();
        refreshMeta();
        audioService.play(SoundEffect.GAME_START);
        showToast("Loaded: " + selected.name(), "toast-success");
        requestAiIfNeeded();
    }

    @FXML
    private void onDeleteSelectedSave() {
        List<GameSaveRecord> selectedItems = List.copyOf(loadGamesListView.getSelectionModel().getSelectedItems());
        if (selectedItems.isEmpty()) {
            showToast("Select at least one save to delete", "toast-error");
            return;
        }

        String msg = selectedItems.size() == 1
            ? "Delete selected save?"
            : "Delete " + selectedItems.size() + " selected saves?";
        showConfirm("Delete Save", msg, () -> {
            loadStatusLabel.setText("Deleting...");
            String activeSaveId = currentGameSaveId;
            ioExecutor.execute(() -> {
                int deletedCount = 0;
                boolean deletedActiveGame = false;
                for (GameSaveRecord item : selectedItems) {
                    if (persistenceService.delete(item)) {
                        deletedCount++;
                        if (activeSaveId != null && Objects.equals(activeSaveId, item.id())) {
                            deletedActiveGame = true;
                        }
                    }
                }
                final int finalDeletedCount = deletedCount;
                final boolean finalDeletedActiveGame = deletedActiveGame;
                Platform.runLater(() -> {
                    if (finalDeletedActiveGame) {
                        currentGameSave = null;
                        currentGameName = null;
                        currentGameSaveId = null;
                    }

                    boolean ok = finalDeletedCount == selectedItems.size();
                    String text = ok
                        ? "Deleted " + finalDeletedCount + " save(s)"
                        : "Deleted " + finalDeletedCount + "/" + selectedItems.size() + " save(s)";
                    showToast(text, ok ? "toast-success" : "toast-error");
                    savesDirty = true;
                    refreshSaves(true);
                });
            });
        });
    }

    @FXML
    private void onGameBackToMenu() {
        showConfirm("Leave Game", "Return to main menu? The game will be auto-saved.", () -> {
            cancelPendingAiMove();
            autoSaveCurrentGame();
            showToast("Game auto-saved", "toast-success");
            showPage(Page.MAIN_MENU, true);
        });
    }

    @FXML
    private void onGamePauseResume() {
        paused = !paused;
        if (paused) {
            stopTurnClock();
        } else {
            startTurnClock();
        }
        showToast(paused ? "Game paused" : "Game resumed", "toast-info");
    }

    @FXML
    private void onConfirmYes() {
        hideConfirm();
        if (confirmAction != null) {
            Runnable action = confirmAction;
            confirmAction = null;
            action.run();
        }
    }

    @FXML
    private void onConfirmNo() {
        hideConfirm();
        confirmAction = null;
    }

    private void setupSettingsControls() {
        settingsGameModeCombo.getItems().setAll(GameMode.values());
        settingsDifficultyCombo.getItems().setAll(Difficulty.values());
        settingsTimeControlCombo.getItems().setAll(TimeControl.values());
        settingsThemeCombo.getItems().setAll(Theme.values());
        settingsThemeCombo.setCellFactory(list -> createThemeCell());
        settingsThemeCombo.setButtonCell(createThemeCell());
        settingsPieceStyleCombo.getItems().setAll(PieceStyle.values());
        settingsTouchMoveToggle.selectedProperty().addListener((obs, oldValue, newValue) -> updateToggleText(settingsTouchMoveToggle));
        settingsSoundToggle.selectedProperty().addListener((obs, oldValue, newValue) -> updateToggleText(settingsSoundToggle));
        settings.pieceStyleProperty().addListener((obs, oldStyle, newStyle) -> refreshBoard());
        settings.gameModeProperty().addListener((obs, oldValue, newValue) -> refreshAiStateLabel());
        settings.timeControlProperty().addListener((obs, oldValue, newValue) -> {
            if (currentPage == Page.GAME) {
                resetTimersForCurrentTurn();
                refreshMeta();
            }
        });

        if (boardContainer.getScene() != null) {
            themeService.applyTheme(boardContainer.getScene(), settings.themeProperty().get());
        }
        loadSettingsIntoControls();
    }

    private void setupLoadList() {
        loadGamesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        loadGamesListView.setCellFactory(list -> new ListCell<>() {
            {
                setOnMouseClicked(event -> {
                    if (isEmpty()) {
                        return;
                    }

                    int index = getIndex();
                    if (index < 0) {
                        return;
                    }

                    if (event.isShiftDown() && saveSelectionAnchor >= 0) {
                        int start = Math.min(saveSelectionAnchor, index);
                        int end = Math.max(saveSelectionAnchor, index);
                        loadGamesListView.getSelectionModel().clearSelection();
                        for (int i = start; i <= end; i++) {
                            loadGamesListView.getSelectionModel().select(i);
                        }
                        loadGamesListView.requestFocus();
                        event.consume();
                        return;
                    }

                    if (!event.isShiftDown()) {
                        Platform.runLater(() -> {
                            int selectedIndex = loadGamesListView.getSelectionModel().getSelectedIndex();
                            if (selectedIndex >= 0) {
                                saveSelectionAnchor = selectedIndex;
                            }
                        });
                    }
                });
            }

            @Override
            protected void updateItem(GameSaveRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.name() + " | " + SAVE_TIME_FORMAT.format(item.savedAt()) + " | " + item.gameMode());
                }
            }
        });
    }

    private void refreshSaves(boolean force) {
        if (!force && !savesDirty) {
            return;
        }
        long token = saveRefreshToken.incrementAndGet();
        loadStatusLabel.setText("Loading saves...");
        ioExecutor.execute(() -> {
            List<GameSaveRecord> saves;
            String error = null;
            try {
                saves = persistenceService.list();
            } catch (RuntimeException e) {
                saves = List.of();
                error = "Unable to load save files.";
            }
            final List<GameSaveRecord> finalSaves = saves;
            final String finalError = error;
            Platform.runLater(() -> {
                if (token != saveRefreshToken.get()) {
                    return;
                }
                if (finalError != null) {
                    loadStatusLabel.setText(finalError);
                    showToast(finalError, "toast-error");
                    return;
                }

                loadGamesListView.getItems().setAll(finalSaves);
                savesDirty = false;
                if (loadGamesListView.getItems().isEmpty()) {
                    saveSelectionAnchor = -1;
                } else if (saveSelectionAnchor >= loadGamesListView.getItems().size()) {
                    saveSelectionAnchor = loadGamesListView.getItems().size() - 1;
                }
                loadStatusLabel.setText(loadGamesListView.getItems().isEmpty()
                    ? "No saved games available."
                    : "Enter=Load, Shift+Click=Range, Ctrl+A=Select all, Delete=Delete selected.");
            });
        });
    }

    private void onSquareClicked(String square) {
        if (!gamePane.isVisible() || paused || aiThinking || animatingMove || gameService.isGameOver() || timeOutEnded) {
            return;
        }
        if (gameService.isAiTurn()) {
            showToast("AI is playing", "toast-info");
            return;
        }

        if (selectedSquare == null) {
            Piece clickedPiece = gameService.pieceAt(square);
            if (clickedPiece == Piece.NONE || !belongsToTurn(clickedPiece, gameService.getTurn())) {
                return;
            }
            List<String> targets = gameService.legalTargets(square);
            if (settings.touchMoveRuleProperty().get() && targets.isEmpty()) {
                showToast("Selected piece has no legal moves", "toast-info");
                return;
            }
            selectedSquare = square;
            legalTargets = new HashSet<>(targets);
            audioService.play(SoundEffect.PREMOVE);
            refreshBoard();
            return;
        }

        if (selectedSquare.equals(square)) {
            if (settings.touchMoveRuleProperty().get() && !legalTargets.isEmpty()) {
                audioService.play(SoundEffect.ILLEGAL);
                showToast("Touch-move is enabled. Complete this move.", "toast-info");
                return;
            }
            resetSelection();
            refreshBoard();
            return;
        }

        MoveOutcome outcome = gameService.playHumanMove(selectedSquare, square);
        if (!outcome.valid()) {
            boolean touchMoveEnabled = settings.touchMoveRuleProperty().get();
            if (!touchMoveEnabled) {
                Piece clickedPiece = gameService.pieceAt(square);
                if (clickedPiece != Piece.NONE && belongsToTurn(clickedPiece, gameService.getTurn())) {
                    selectedSquare = square;
                    legalTargets = new HashSet<>(gameService.legalTargets(square));
                    refreshBoard();
                    return;
                }
                if (outcome.message() != null && !outcome.message().isBlank()) {
                    showToast(outcome.message(), "toast-error");
                }
                return;
            }
            audioService.play(SoundEffect.ILLEGAL);
            if (touchMoveEnabled && !legalTargets.isEmpty()) {
                showToast("Touch-move: choose a legal target square.", "toast-info");
            } else {
                showToast(outcome.message(), "toast-error");
            }
            return;
        }

        resetSelection();
        playMoveAnimation(outcome, () -> {
            reloadBoardSnapshot();
            resetTimersForCurrentTurn();
            refreshBoard();
            refreshMeta();
            autoSaveCurrentGame();
            requestAiIfNeeded();
        });
    }

    private void requestAiIfNeeded() {
        if (aiThinking || !gameService.isAiTurn() || gameService.isGameOver() || timeOutEnded || currentPage != Page.GAME) {
            return;
        }

        long requestToken = aiRequestToken.incrementAndGet();
        Board boardSnapshot = gameService.copyBoard();
        int searchDepth = settings.difficultyProperty().get().searchDepth();
        setAiThinkingState(true);
        showToast("AI is thinking...", "toast-info");

        aiExecutor.execute(() -> {
            long startedAt = System.nanoTime();
            try {
                String uciMove = aiService.chooseMove(boardSnapshot, searchDepth);
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                long remainingDelayMillis = Math.max(0L, MIN_AI_MOVE_DELAY_MILLIS - elapsedMillis);
                Platform.runLater(() -> scheduleAiMoveApplication(requestToken, uciMove, remainingDelayMillis));
            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    if (requestToken != aiRequestToken.get()) {
                        return;
                    }
                    setAiThinkingState(false);
                    showToast("AI failed to generate move.", "toast-error");
                });
            }
        });
    }

    private void scheduleAiMoveApplication(long requestToken, String uciMove, long remainingDelayMillis) {
        if (requestToken != aiRequestToken.get()) {
            return;
        }

        clearAiDelayTransition();
        aiMoveDelayTransition = new PauseTransition(Duration.millis(remainingDelayMillis));
        aiMoveDelayTransition.setOnFinished(event -> {
            aiMoveDelayTransition = null;
            applyAiMoveIfCurrent(requestToken, uciMove);
        });
        aiMoveDelayTransition.play();
    }

    private void applyAiMoveIfCurrent(long requestToken, String uciMove) {
        if (requestToken != aiRequestToken.get()) {
            return;
        }
        if (currentPage != Page.GAME || paused || timeOutEnded || gameService.isGameOver() || !gameService.isAiTurn()) {
            setAiThinkingState(false);
            return;
        }

        MoveOutcome outcome = gameService.playAiMove(uciMove);
        setAiThinkingState(false);

        if (!outcome.valid()) {
            showToast(outcome.message(), "toast-error");
            return;
        }

        playMoveAnimation(outcome, () -> {
            reloadBoardSnapshot();
            resetTimersForCurrentTurn();
            refreshBoard();
            refreshMeta();
            autoSaveCurrentGame();
        });
    }

    private void cancelPendingAiMove() {
        aiRequestToken.incrementAndGet();
        clearAiDelayTransition();
        setAiThinkingState(false);
    }

    private void clearAiDelayTransition() {
        if (aiMoveDelayTransition != null) {
            aiMoveDelayTransition.stop();
            aiMoveDelayTransition = null;
        }
    }

    private void setAiThinkingState(boolean thinking) {
        aiThinking = thinking;
        refreshAiStateLabel();
    }

    private void refreshAiStateLabel() {
        if (aiStateLabel == null) {
            return;
        }
        if (settings.gameModeProperty().get() == GameMode.TWO_PLAYER) {
            aiStateLabel.setText("AI: Off");
        } else {
            aiStateLabel.setText(aiThinking ? "AI: Thinking..." : "AI: Ready");
        }
    }

    private void playMoveAnimation(MoveOutcome outcome, Runnable after) {
        animatingMove = true;
        boolean animated = boardView.animateMove(outcome.fromSquare(), outcome.toSquare(), outcome.movedPiece(), () -> {
            animatingMove = false;
            after.run();
        });

        if (!animated) {
            animatingMove = false;
            after.run();
        }
    }

    private void autoSaveCurrentGame() {
        autoSaveCurrentGame(false);
    }

    private void autoSaveCurrentGame(boolean synchronous) {
        if (gameService.moveHistory().isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        if (currentGameName == null || currentGameName.isBlank()) {
            currentGameName = "Auto Save " + SAVE_TIME_FORMAT.format(now);
        }
        if (currentGameSaveId == null || currentGameSaveId.isBlank()) {
            currentGameSaveId = java.util.UUID.randomUUID().toString();
        }

        String targetSaveId = currentGameSaveId;
        Path targetFile = currentGameSave != null ? currentGameSave.file() : null;

        GameSaveRecord snapshot = new GameSaveRecord(
            targetSaveId,
            currentGameName,
            now,
            gameService.currentFen(),
            settings.gameModeProperty().get(),
            settings.difficultyProperty().get(),
            settings.themeProperty().get(),
            settings.soundEnabledProperty().get(),
            gameService.moveHistory(),
            targetFile
        );

        if (synchronous) {
            try {
                currentGameSave = persistenceService.save(snapshot);
                savesDirty = true;
            } catch (RuntimeException e) {
                showToast("Auto-save failed", "toast-error");
            }
            return;
        }

        pendingSaveSnapshot.set(snapshot);
        drainPendingSaves();
    }

    private void drainPendingSaves() {
        if (!saveWriteInProgress.compareAndSet(false, true)) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                while (true) {
                    GameSaveRecord next = pendingSaveSnapshot.getAndSet(null);
                    if (next == null) {
                        break;
                    }
                    GameSaveRecord saved = persistenceService.save(next);
                    Platform.runLater(() -> {
                        if (!Objects.equals(currentGameSaveId, next.id())) {
                            return;
                        }
                        currentGameSave = saved;
                        savesDirty = true;
                    });
                }
            } catch (RuntimeException e) {
                Platform.runLater(() -> showToast("Auto-save failed", "toast-error"));
            } finally {
                saveWriteInProgress.set(false);
                if (pendingSaveSnapshot.get() != null) {
                    drainPendingSaves();
                }
            }
        });
    }

    public void autoSaveBeforeExit() {
        cancelPendingAiMove();
        autoSaveCurrentGame(true);
    }

    private void refreshBoard() {
        boardView.setPieceStyle(settings.pieceStyleProperty().get());
        boardView.render(boardSnapshot, selectedSquare, legalTargets);
    }

    private void refreshMeta() {
        turnLabel.setText("Turn: " + gameService.getTurn().name());
        refreshAiStateLabel();
        gameStatusLabel.setText(timeOutEnded ? "Time out" : "Status: " + gameService.gameStatusText());
        messageLabel.setText(timeOutEnded ? "Turn time expired." : gameService.gameStatusText());
        List<String> history = gameService.moveHistory();
        if (history.size() == movesListView.getItems().size() + 1
            && movesListView.getItems().equals(history.subList(0, movesListView.getItems().size()))) {
            movesListView.getItems().add(history.get(history.size() - 1));
        } else if (!movesListView.getItems().equals(history)) {
            movesListView.getItems().setAll(history);
        }

        capturedByWhiteLabel.setText(toCapturedGlyphs(gameService.capturedByWhite()));
        capturedByBlackLabel.setText(toCapturedGlyphs(gameService.capturedByBlack()));
        refreshTimerLabels();
        showGameOverDialogIfNeeded();
    }

    private String toCapturedGlyphs(List<Piece> pieces) {
        StringBuilder builder = new StringBuilder();
        for (Piece piece : pieces) {
            builder.append(boardView.glyph(piece)).append(' ');
        }
        return builder.isEmpty() ? "-" : builder.toString().trim();
    }

    private void showPage(Page page, boolean animated) {
        if (currentPage == page) {
            return;
        }
        if (page != Page.GAME) {
            cancelPendingAiMove();
        }

        Node incoming = nodeFor(page);
        Node outgoing = nodeFor(currentPage);
        currentPage = page;

        incoming.setVisible(true);
        incoming.setManaged(true);

        if (page == Page.GAME) {
            audioService.stopMusic();
            startTurnClock();
        } else {
            audioService.playMenuMusic();
            stopTurnClock();
        }

        if (!animated) {
            incoming.setOpacity(1);
            incoming.setTranslateY(0);
            if (outgoing != null) {
                outgoing.setVisible(false);
                outgoing.setManaged(false);
                outgoing.setOpacity(1);
                outgoing.setTranslateY(0);
            }
            rootStack.requestFocus();
            return;
        }

        FadeTransition inFade = new FadeTransition(Duration.millis(240), incoming);
        inFade.setFromValue(0);
        inFade.setToValue(1);
        inFade.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition inSlide = new TranslateTransition(Duration.millis(240), incoming);
        inSlide.setFromY(14);
        inSlide.setToY(0);
        inSlide.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition inAnim = new ParallelTransition(inFade, inSlide);

        if (outgoing != null) {
            FadeTransition outFade = new FadeTransition(Duration.millis(180), outgoing);
            outFade.setFromValue(1);
            outFade.setToValue(0);
            TranslateTransition outSlide = new TranslateTransition(Duration.millis(180), outgoing);
            outSlide.setFromY(0);
            outSlide.setToY(-8);
            ParallelTransition outAnim = new ParallelTransition(outFade, outSlide);
            outAnim.setOnFinished(e -> {
                outgoing.setVisible(false);
                outgoing.setManaged(false);
                outgoing.setOpacity(1);
                outgoing.setTranslateY(0);
            });
            outAnim.play();
        }

        inAnim.setOnFinished(e -> rootStack.requestFocus());
        inAnim.play();
    }

    private Node nodeFor(Page page) {
        return switch (page) {
            case MAIN_MENU -> mainMenuPane;
            case SETTINGS -> settingsPane;
            case LOAD -> loadPane;
            case GAME -> gamePane;
        };
    }

    private void installButtonAnimations() {
        Set<Node> buttons = rootStack.lookupAll(".button");
        for (Node node : buttons) {
            if (!(node instanceof Button button)) {
                continue;
            }
            // Board squares receive very frequent hover events; skip scale transitions to avoid UI jank.
            if (button.getStyleClass().contains("square-cell")) {
                continue;
            }
            if (Boolean.TRUE.equals(button.getProperties().get("motionInstalled"))) {
                continue;
            }
            button.getProperties().put("motionInstalled", true);

            button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> animateButtonScale(button, 1.04));
            button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> animateButtonScale(button, 1.0));
            button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> animateButtonScale(button, 0.97));
            button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> animateButtonScale(button, button.isHover() ? 1.04 : 1.0));
        }
    }

    private void animateButtonScale(Button button, double scale) {
        ScaleTransition st = new ScaleTransition(Duration.millis(120), button);
        st.setInterpolator(Interpolator.EASE_BOTH);
        st.setToX(scale);
        st.setToY(scale);
        st.play();
    }

    private void showToast(String message, String styleClass) {
        Label toast = new Label(message);
        toast.getStyleClass().addAll("toast", styleClass);
        toast.setOpacity(0);

        toastContainer.getChildren().add(0, toast);

        FadeTransition in = new FadeTransition(Duration.millis(160), toast);
        in.setFromValue(0);
        in.setToValue(1);

        PauseTransition hold = new PauseTransition(Duration.millis(1200));

        FadeTransition out = new FadeTransition(Duration.millis(220), toast);
        out.setFromValue(1);
        out.setToValue(0);
        out.setOnFinished(e -> toastContainer.getChildren().remove(toast));

        new SequentialTransition(in, hold, out).play();
    }

    private void showConfirm(String title, String message, Runnable onConfirm) {
        confirmAction = onConfirm;
        confirmTitleLabel.setText(title);
        confirmMessageLabel.setText(message);
        confirmYesButton.setText("Yes");
        confirmNoButton.setVisible(true);
        confirmNoButton.setManaged(true);
        confirmOverlay.setVisible(true);
        confirmOverlay.setManaged(true);
        audioService.play(SoundEffect.NOTIFY);
        confirmYesButton.requestFocus();
    }

    private void showInfoDialog(String title, String message) {
        confirmAction = null;
        confirmTitleLabel.setText(title);
        confirmMessageLabel.setText(message);
        confirmYesButton.setText("OK");
        confirmNoButton.setVisible(false);
        confirmNoButton.setManaged(false);
        confirmOverlay.setVisible(true);
        confirmOverlay.setManaged(true);
        audioService.play(SoundEffect.NOTIFY);
        confirmYesButton.requestFocus();
    }

    private void hideConfirm() {
        confirmOverlay.setVisible(false);
        confirmOverlay.setManaged(false);
        rootStack.requestFocus();
    }

    private void showGameOverDialogIfNeeded() {
        if (gameOverDialogShown || currentPage != Page.GAME) {
            return;
        }
        if (!timeOutEnded && !gameService.isGameOver()) {
            return;
        }
        if (confirmOverlay.isVisible()) {
            return;
        }

        gameOverDialogShown = true;
        String message = timeOutEnded
            ? messageLabel.getText()
            : gameService.gameStatusText();
        showInfoDialog("Game Over", message);
    }

    private void resetSelection() {
        selectedSquare = null;
        legalTargets = new HashSet<>();
    }

    private boolean belongsToTurn(Piece piece, Side turn) {
        return switch (turn) {
            case WHITE -> piece.getPieceSide() == Side.WHITE;
            case BLACK -> piece.getPieceSide() == Side.BLACK;
        };
    }

    private void resetTimersForNewGame() {
        int perTurn = settings.timeControlProperty().get().secondsPerTurn();
        whiteSecondsRemaining = perTurn;
        blackSecondsRemaining = perTurn;
        timeOutEnded = false;
        startTurnClock();
    }

    private void resetTimersForCurrentTurn() {
        int perTurn = settings.timeControlProperty().get().secondsPerTurn();
        if (perTurn <= 0) {
            whiteSecondsRemaining = 0;
            blackSecondsRemaining = 0;
            stopTurnClock();
            return;
        }

        if (gameService.getTurn() == Side.WHITE) {
            whiteSecondsRemaining = perTurn;
        } else {
            blackSecondsRemaining = perTurn;
        }
        startTurnClock();
    }

    private void startTurnClock() {
        stopTurnClock();

        if (!settings.timeControlProperty().get().isEnabled() || currentPage != Page.GAME || paused || timeOutEnded) {
            refreshTimerLabels();
            return;
        }

        turnClock = new Timeline(new KeyFrame(Duration.seconds(1), e -> onClockTick()));
        turnClock.setCycleCount(Timeline.INDEFINITE);
        turnClock.play();
        refreshTimerLabels();
    }

    private void stopTurnClock() {
        if (turnClock != null) {
            turnClock.stop();
            turnClock = null;
        }
    }

    private void onClockTick() {
        if (!settings.timeControlProperty().get().isEnabled()) {
            return;
        }

        Side turn = gameService.getTurn();
        if (turn == Side.WHITE) {
            whiteSecondsRemaining--;
            if (whiteSecondsRemaining <= 0) {
                whiteSecondsRemaining = 0;
                onTimeOut(Side.BLACK);
            }
        } else {
            blackSecondsRemaining--;
            if (blackSecondsRemaining <= 0) {
                blackSecondsRemaining = 0;
                onTimeOut(Side.WHITE);
            }
        }
        refreshTimerLabels();
    }

    private void onTimeOut(Side winner) {
        timeOutEnded = true;
        paused = true;
        cancelPendingAiMove();
        stopTurnClock();
        gameStatusLabel.setText("Status: Time out. Winner: " + winner.name());
        messageLabel.setText("Time out. Winner: " + winner.name());
        showToast("Time out. Winner: " + winner.name(), "toast-error");
        showGameOverDialogIfNeeded();
    }

    private void refreshTimerLabels() {
        if (!settings.timeControlProperty().get().isEnabled()) {
            whiteTimerLabel.setText("White: --:--");
            blackTimerLabel.setText("Black: --:--");
            return;
        }

        whiteTimerLabel.setText("White: " + formatTime(whiteSecondsRemaining));
        blackTimerLabel.setText("Black: " + formatTime(blackSecondsRemaining));
    }

    private String formatTime(int totalSeconds) {
        int m = Math.max(0, totalSeconds) / 60;
        int s = Math.max(0, totalSeconds) % 60;
        return String.format("%02d:%02d", m, s);
    }

    private void loadSettingsIntoControls() {
        settingsGameModeCombo.setValue(settings.gameModeProperty().get());
        settingsDifficultyCombo.setValue(settings.difficultyProperty().get());
        settingsTimeControlCombo.setValue(settings.timeControlProperty().get());
        settingsThemeCombo.setValue(settings.themeProperty().get());
        settingsPieceStyleCombo.setValue(settings.pieceStyleProperty().get());
        settingsTouchMoveToggle.setSelected(settings.touchMoveRuleProperty().get());
        settingsSaveDirectoryField.setText(settings.saveDirectoryProperty().get());
        settingsSoundToggle.setSelected(settings.soundEnabledProperty().get());
        settingsSfxSlider.setValue(settings.sfxVolumeProperty().get());
        settingsMenuMusicSlider.setValue(settings.menuMusicVolumeProperty().get());
    }

    private boolean applyControlsToSettings() {
        if (settingsGameModeCombo.getValue() != null) {
            settings.gameModeProperty().set(settingsGameModeCombo.getValue());
        }
        if (settingsDifficultyCombo.getValue() != null) {
            settings.difficultyProperty().set(settingsDifficultyCombo.getValue());
        }
        if (settingsTimeControlCombo.getValue() != null) {
            settings.timeControlProperty().set(settingsTimeControlCombo.getValue());
        }
        if (settingsThemeCombo.getValue() != null) {
            settings.themeProperty().set(settingsThemeCombo.getValue());
        }
        if (settingsPieceStyleCombo.getValue() != null) {
            settings.pieceStyleProperty().set(settingsPieceStyleCombo.getValue());
        }
        settings.touchMoveRuleProperty().set(settingsTouchMoveToggle.isSelected());
        String resolvedSavePath = normalizeSaveDirectory(settingsSaveDirectoryField.getText());
        if (resolvedSavePath == null) {
            settingsInfoLabel.setText("Invalid save folder path.");
            showToast("Save folder path is invalid", "toast-error");
            return false;
        }
        settings.saveDirectoryProperty().set(resolvedSavePath);
        settingsSaveDirectoryField.setText(resolvedSavePath);
        settings.soundEnabledProperty().set(settingsSoundToggle.isSelected());
        settings.sfxVolumeProperty().set(settingsSfxSlider.getValue());
        settings.menuMusicVolumeProperty().set(settingsMenuMusicSlider.getValue());
        return true;
    }

    private String normalizeSaveDirectory(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return AppSettings.defaultSaveDirectoryPath().toString();
        }
        try {
            return Path.of(rawInput.trim()).toAbsolutePath().normalize().toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Path resolveInitialDirectory(String rawInput) {
        String normalized = normalizeSaveDirectory(rawInput);
        Path candidate = normalized == null ? AppSettings.defaultSaveDirectoryPath() : Path.of(normalized);
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        Path parent = candidate.getParent();
        if (parent != null && Files.isDirectory(parent)) {
            return parent;
        }
        Path fallback = AppSettings.defaultSaveDirectoryPath();
        return Files.isDirectory(fallback) ? fallback : Path.of(System.getProperty("user.home"));
    }

    private ListCell<Theme> createThemeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Theme theme, boolean empty) {
                super.updateItem(theme, empty);
                if (empty || theme == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label label = new Label(theme.toString());
                label.getStyleClass().add("theme-combo-title");
                label.setAlignment(Pos.CENTER_LEFT);
                label.setMaxHeight(Double.MAX_VALUE);

                HBox swatches = new HBox(4);
                swatches.getStyleClass().add("theme-combo-swatches");
                swatches.setAlignment(Pos.CENTER_LEFT);
                for (String color : theme.previewColors()) {
                    Region swatch = new Region();
                    swatch.getStyleClass().add("theme-combo-swatch");
                    swatch.setStyle("-fx-background-color: " + color + ";");
                    swatches.getChildren().add(swatch);
                }

                HBox row = new HBox(8, swatches, label);
                row.getStyleClass().add("theme-combo-row");
                row.setAlignment(Pos.CENTER_LEFT);
                setAlignment(Pos.CENTER_LEFT);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setGraphic(row);
            }
        };
    }

    private void updateToggleText(ToggleButton toggleButton) {
        if (toggleButton == null) {
            return;
        }
        toggleButton.setText(toggleButton.isSelected() ? "ON" : "OFF");
    }

    private void reloadBoardSnapshot() {
        boardSnapshot = gameService.currentPosition();
    }
}
