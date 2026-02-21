package com.example.ui;

import com.example.config.PieceStyle;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class ChessBoardView extends StackPane {

    private static final Duration MOVE_ANIMATION_DURATION = Duration.millis(170);

    private final BorderPane frame = new BorderPane();
    private final GridPane grid = new GridPane();
    private final Pane animationLayer = new Pane();
    private final Label movingPieceNode = createMovingPieceNode();
    private final Map<String, Button> cells = new HashMap<>();
    private final Map<String, CellVisualState> cellStates = new HashMap<>();
    private PieceStyle pieceStyle = PieceStyle.CLASSIC;

    public ChessBoardView(Consumer<String> squareClickHandler) {
        getStyleClass().add("board-host");

        frame.getStyleClass().add("board-frame");
        frame.setCenter(grid);
        frame.setTop(createFileLabels(false));
        frame.setBottom(createFileLabels(true));
        frame.setLeft(createRankLabels());
        frame.setRight(createRankLabels());

        grid.getStyleClass().add("board-grid");
        grid.setAlignment(Pos.CENTER);

        animationLayer.setManaged(false);
        animationLayer.setMouseTransparent(true);

        buildGrid(squareClickHandler);
        animationLayer.getChildren().add(movingPieceNode);
        getChildren().addAll(frame, animationLayer);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        animationLayer.resizeRelocate(0, 0, getWidth(), getHeight());
    }

    public void render(Map<String, Piece> position, String selectedSquare, Set<String> legalTargets) {
        for (Map.Entry<String, Button> entry : cells.entrySet()) {
            String square = entry.getKey();
            Button cell = entry.getValue();
            Piece piece = position.getOrDefault(square, Piece.NONE);
            String pieceGlyph = glyph(piece);
            String pieceColorClass = piece == Piece.NONE ? null : (piece.getPieceSide() == Side.WHITE ? "piece-white" : "piece-black");
            String pieceStyleClass = piece == Piece.NONE ? null : styleClassFor(pieceStyle);
            boolean selected = square.equals(selectedSquare);
            boolean target = legalTargets.contains(square);

            CellVisualState nextState = new CellVisualState(pieceGlyph, pieceColorClass, pieceStyleClass, selected, target);
            CellVisualState previousState = cellStates.get(square);
            if (nextState.equals(previousState)) {
                continue;
            }
            cellStates.put(square, nextState);

            cell.setText(pieceGlyph);
            cell.getStyleClass().removeAll("selected-square", "target-square");
            cell.getStyleClass().removeAll(
                "piece-white",
                "piece-black",
                "piece-style-classic",
                "piece-style-minimal",
                "piece-style-tournament"
            );

            if (pieceColorClass != null && pieceStyleClass != null) {
                cell.getStyleClass().add(pieceColorClass);
                cell.getStyleClass().add(pieceStyleClass);
            }

            if (selected) {
                cell.getStyleClass().add("selected-square");
            }
            if (target) {
                cell.getStyleClass().add("target-square");
            }
        }
    }

    public boolean animateMove(String fromSquare, String toSquare, Piece movedPiece, Runnable onFinished) {
        if (fromSquare == null || toSquare == null || movedPiece == Piece.NONE) {
            return false;
        }

        Button from = cells.get(fromSquare);
        Button to = cells.get(toSquare);
        if (from == null || to == null || getScene() == null) {
            return false;
        }

        String pieceGlyph = glyph(movedPiece);
        if (pieceGlyph.isBlank()) {
            return false;
        }
        if (movingPieceNode.isVisible()) {
            return false;
        }

        Point2D sceneFrom = from.localToScene(from.getWidth() / 2.0, from.getHeight() / 2.0);
        Point2D sceneTo = to.localToScene(to.getWidth() / 2.0, to.getHeight() / 2.0);
        if (!isFinite(sceneFrom) || !isFinite(sceneTo)) {
            return false;
        }

        Point2D layerFrom = animationLayer.sceneToLocal(sceneFrom);
        Point2D layerTo = animationLayer.sceneToLocal(sceneTo);
        if (!isFinite(layerFrom) || !isFinite(layerTo)) {
            return false;
        }

        Label movingPiece = movingPieceNode;
        movingPiece.setText(pieceGlyph);
        movingPiece.getStyleClass().setAll("moving-piece", styleClassFor(pieceStyle), movedPiece.getPieceSide() == Side.WHITE ? "piece-white" : "piece-black");
        movingPiece.setTranslateX(0);
        movingPiece.setTranslateY(0);
        movingPiece.setVisible(true);
        movingPiece.toFront();

        double pieceWidth = Math.max(1.0, from.getWidth());
        double pieceHeight = Math.max(1.0, from.getHeight());
        movingPiece.resizeRelocate(
            layerFrom.getX() - pieceWidth / 2.0,
            layerFrom.getY() - pieceHeight / 2.0,
            pieceWidth,
            pieceHeight
        );

        String fromTextBefore = from.getText();
        String toTextBefore = to.getText();
        from.setText("");
        to.setText("");

        double dx = layerTo.getX() - layerFrom.getX();
        double dy = layerTo.getY() - layerFrom.getY();
        try {
            TranslateTransition move = new TranslateTransition(MOVE_ANIMATION_DURATION, movingPiece);
            move.setToX(dx);
            move.setToY(dy);
            move.setInterpolator(Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0));
            move.setOnFinished(event -> {
                movingPiece.setTranslateX(0);
                movingPiece.setTranslateY(0);
                movingPiece.setVisible(false);
                to.setText(pieceGlyph);
                onFinished.run();
            });
            move.play();
            return true;
        } catch (RuntimeException ex) {
            movingPiece.setVisible(false);
            from.setText(fromTextBefore);
            to.setText(toTextBefore);
            return false;
        }
    }

    private boolean isFinite(Point2D point) {
        return point != null && Double.isFinite(point.getX()) && Double.isFinite(point.getY());
    }

    private Label createMovingPieceNode() {
        Label label = new Label();
        label.setAlignment(Pos.CENTER);
        label.setManaged(false);
        label.setMouseTransparent(true);
        label.setViewOrder(-1000);
        label.setCache(true);
        label.setCacheHint(CacheHint.SPEED);
        label.setVisible(false);
        return label;
    }

    private void buildGrid(Consumer<String> squareClickHandler) {
        for (int rank = 8; rank >= 1; rank--) {
            for (int file = 0; file < 8; file++) {
                String square = String.valueOf((char) ('a' + file)) + rank;
                Button cell = new Button();
                cell.setFocusTraversable(false);
                cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                cell.setMinSize(72, 72);
                cell.setPrefSize(82, 82);
                cell.getStyleClass().add((rank + file) % 2 == 0 ? "square-light" : "square-dark");
                cell.getStyleClass().add("square-cell");
                cell.setOnAction(event -> squareClickHandler.accept(square));

                grid.add(cell, file, 8 - rank);
                cells.put(square, cell);
            }
        }
    }

    private HBox createFileLabels(boolean reverse) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("board-legend-row");
        for (int i = 0; i < 8; i++) {
            int idx = reverse ? 7 - i : i;
            Label label = new Label(String.valueOf((char) ('A' + idx)));
            label.getStyleClass().add("board-legend");
            StackPane holder = new StackPane(label);
            holder.setPrefWidth(82);
            holder.setMinWidth(Region.USE_PREF_SIZE);
            box.getChildren().add(holder);
        }
        return box;
    }

    private VBox createRankLabels() {
        VBox box = new VBox();
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("board-legend-col");
        for (int rank = 8; rank >= 1; rank--) {
            Label label = new Label(String.valueOf(rank));
            label.getStyleClass().add("board-legend");
            StackPane holder = new StackPane(label);
            holder.setPrefHeight(82);
            holder.setMinHeight(Region.USE_PREF_SIZE);
            holder.setPrefWidth(14);
            holder.setMinWidth(Region.USE_PREF_SIZE);
            holder.setMaxWidth(Region.USE_PREF_SIZE);
            box.getChildren().add(holder);
        }
        return box;
    }

    public void setPieceStyle(PieceStyle pieceStyle) {
        this.pieceStyle = pieceStyle;
    }

    public String glyph(Piece piece) {
        return switch (pieceStyle) {
            case CLASSIC -> classicGlyph(piece);
            case MINIMAL -> minimalGlyph(piece);
            case TOURNAMENT -> tournamentGlyph(piece);
        };
    }

    private String styleClassFor(PieceStyle style) {
        return switch (style) {
            case CLASSIC -> "piece-style-classic";
            case MINIMAL -> "piece-style-minimal";
            case TOURNAMENT -> "piece-style-tournament";
        };
    }

    private String classicGlyph(Piece piece) {
        return switch (piece) {
            case WHITE_KING, BLACK_KING -> "\u265A";
            case WHITE_QUEEN, BLACK_QUEEN -> "\u265B";
            case WHITE_ROOK, BLACK_ROOK -> "\u265C";
            case WHITE_BISHOP, BLACK_BISHOP -> "\u265D";
            case WHITE_KNIGHT, BLACK_KNIGHT -> "\u265E";
            case WHITE_PAWN, BLACK_PAWN -> "\u265F";
            default -> "";
        };
    }

    private String minimalGlyph(Piece piece) {
        return switch (piece) {
            case WHITE_KING, BLACK_KING -> "\u2654";
            case WHITE_QUEEN, BLACK_QUEEN -> "\u2655";
            case WHITE_ROOK, BLACK_ROOK -> "\u2656";
            case WHITE_BISHOP, BLACK_BISHOP -> "\u2657";
            case WHITE_KNIGHT, BLACK_KNIGHT -> "\u2658";
            case WHITE_PAWN, BLACK_PAWN -> "\u2659";
            default -> "";
        };
    }

    private String tournamentGlyph(Piece piece) {
        return switch (piece) {
            case WHITE_KING, BLACK_KING -> "\u265A";
            case WHITE_QUEEN, BLACK_QUEEN -> "\u265B";
            case WHITE_ROOK, BLACK_ROOK -> "\u265C";
            case WHITE_BISHOP, BLACK_BISHOP -> "\u265D";
            case WHITE_KNIGHT, BLACK_KNIGHT -> "\u265E";
            case WHITE_PAWN, BLACK_PAWN -> "\u265F";
            default -> "";
        };
    }

    private record CellVisualState(
        String glyph,
        String pieceColorClass,
        String pieceStyleClass,
        boolean selected,
        boolean target
    ) {
    }
}
