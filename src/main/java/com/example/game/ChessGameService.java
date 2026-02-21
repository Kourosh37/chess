package com.example.game;

import com.example.audio.AudioService;
import com.example.audio.SoundEffect;
import com.example.config.AppSettings;
import com.example.config.GameMode;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChessGameService {

    private final AppSettings settings;
    private final AudioService audioService;
    private final Board board = new Board();
    private final List<String> moveHistory = new ArrayList<>();
    private final List<Piece> capturedByWhite = new ArrayList<>();
    private final List<Piece> capturedByBlack = new ArrayList<>();
    private List<Move> cachedLegalMoves = Collections.emptyList();
    private Map<Square, List<Move>> cachedMovesByFrom = Collections.emptyMap();
    private boolean legalMovesDirty = true;

    private static final Map<Piece, Integer> STARTING_COUNTS = startingPieceCounts();

    public ChessGameService(AppSettings settings, AudioService audioService) {
        this.settings = settings;
        this.audioService = audioService;
    }

    public synchronized void resetGame() {
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        moveHistory.clear();
        capturedByWhite.clear();
        capturedByBlack.clear();
        invalidateLegalMovesCache();
    }

    public synchronized void restore(String fen, List<String> history) {
        board.loadFromFen(fen);
        moveHistory.clear();
        capturedByWhite.clear();
        capturedByBlack.clear();
        if (history != null) {
            moveHistory.addAll(history);
        }
        rebuildCapturedFromBoard();
        invalidateLegalMovesCache();
    }

    public synchronized boolean isAiTurn() {
        if (settings.gameModeProperty().get() == GameMode.TWO_PLAYER) {
            return false;
        }
        return board.getSideToMove() == Side.BLACK;
    }

    public synchronized Side getTurn() {
        return board.getSideToMove();
    }

    public synchronized Piece pieceAt(String square) {
        return board.getPiece(toSquare(square));
    }

    public synchronized List<String> legalTargets(String fromSquare) {
        Square from = toSquare(fromSquare);
        ensureLegalMovesCache();
        List<String> targets = new ArrayList<>();
        for (Move move : cachedMovesByFrom.getOrDefault(from, Collections.emptyList())) {
            targets.add(move.getTo().value().toLowerCase());
        }
        return targets;
    }

    public synchronized MoveOutcome playHumanMove(String fromSquare, String toSquare) {
        return applyMove(fromSquare, toSquare, true);
    }

    public synchronized MoveOutcome playAiMove(String uciMove) {
        if (uciMove == null || uciMove.length() < 4) {
            return MoveOutcome.invalid("AI could not find a valid move.");
        }
        return applyMove(uciMove.substring(0, 2), uciMove.substring(2, 4), false);
    }

    public synchronized boolean isGameOver() {
        return board.isMated() || board.isDraw();
    }

    public synchronized String gameStatusText() {
        if (board.isMated()) {
            Side winner = board.getSideToMove().flip();
            return "Checkmate. Winner: " + winner.name();
        }
        if (board.isDraw()) {
            return "Game ended in draw.";
        }
        return "In progress";
    }

    public synchronized Map<String, Piece> currentPosition() {
        Map<String, Piece> map = new LinkedHashMap<>();
        for (Square square : Square.values()) {
            if (square == Square.NONE) {
                continue;
            }
            map.put(square.value().toLowerCase(), board.getPiece(square));
        }
        return map;
    }

    public synchronized List<String> moveHistory() {
        return List.copyOf(moveHistory);
    }

    public synchronized Board copyBoard() {
        return board.clone();
    }

    public synchronized String currentFen() {
        return board.getFen();
    }

    public synchronized List<Piece> capturedByWhite() {
        return List.copyOf(capturedByWhite);
    }

    public synchronized List<Piece> capturedByBlack() {
        return List.copyOf(capturedByBlack);
    }

    private MoveOutcome applyMove(String fromSquare, String toSquare, boolean humanMove) {
        Square from = toSquare(fromSquare);
        Square to = toSquare(toSquare);

        ensureLegalMovesCache();
        List<Move> candidates = new ArrayList<>();
        for (Move move : cachedMovesByFrom.getOrDefault(from, Collections.emptyList())) {
            if (move.getTo() == to) {
                candidates.add(move);
            }
        }
        if (candidates.isEmpty()) {
            return MoveOutcome.invalid("Illegal move.");
        }

        Move selectedMove = candidates.get(0);
        if (candidates.size() > 1) {
            Piece preferredPromotion = board.getSideToMove() == Side.WHITE ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN;
            for (Move candidate : candidates) {
                if (candidate.getPromotion() == preferredPromotion) {
                    selectedMove = candidate;
                    break;
                }
            }
        }

        Piece targetBeforeMove = board.getPiece(to);
        Piece movingPiece = board.getPiece(from);
        board.doMove(selectedMove);
        invalidateLegalMovesCache();
        String notation = selectedMove.getFrom().value().toLowerCase() + " -> " + selectedMove.getTo().value().toLowerCase();
        moveHistory.add(notation);

        if (targetBeforeMove != Piece.NONE) {
            if (movingPiece.getPieceSide() == Side.WHITE) {
                capturedByWhite.add(targetBeforeMove);
            } else {
                capturedByBlack.add(targetBeforeMove);
            }
            audioService.play(SoundEffect.CAPTURE);
        } else {
            audioService.play(SoundEffect.MOVE);
        }
        if (board.isKingAttacked()) {
            audioService.play(SoundEffect.CHECK);
        }
        if (board.isMated() || board.isDraw()) {
            audioService.play(SoundEffect.GAME_END);
        }

        return MoveOutcome.valid(
            notation,
            humanMove,
            selectedMove.getFrom().value().toLowerCase(),
            selectedMove.getTo().value().toLowerCase(),
            movingPiece
        );
    }

    private Square toSquare(String value) {
        return Square.valueOf(value.toUpperCase());
    }

    private List<Move> legalMoves() {
        ensureLegalMovesCache();
        return cachedLegalMoves;
    }

    private void ensureLegalMovesCache() {
        if (!legalMovesDirty) {
            return;
        }
        try {
            cachedLegalMoves = MoveGenerator.generateLegalMoves(board);
        } catch (Exception e) {
            cachedLegalMoves = Collections.emptyList();
            cachedMovesByFrom = Collections.emptyMap();
            legalMovesDirty = false;
            return;
        }

        Map<Square, List<Move>> byFrom = new EnumMap<>(Square.class);
        for (Move move : cachedLegalMoves) {
            byFrom.computeIfAbsent(move.getFrom(), key -> new ArrayList<>()).add(move);
        }
        cachedMovesByFrom = byFrom;
        legalMovesDirty = false;
    }

    private void invalidateLegalMovesCache() {
        legalMovesDirty = true;
        cachedLegalMoves = Collections.emptyList();
        cachedMovesByFrom = Collections.emptyMap();
    }

    private static Map<Piece, Integer> startingPieceCounts() {
        Map<Piece, Integer> map = new EnumMap<>(Piece.class);
        map.put(Piece.WHITE_PAWN, 8);
        map.put(Piece.BLACK_PAWN, 8);
        map.put(Piece.WHITE_KNIGHT, 2);
        map.put(Piece.BLACK_KNIGHT, 2);
        map.put(Piece.WHITE_BISHOP, 2);
        map.put(Piece.BLACK_BISHOP, 2);
        map.put(Piece.WHITE_ROOK, 2);
        map.put(Piece.BLACK_ROOK, 2);
        map.put(Piece.WHITE_QUEEN, 1);
        map.put(Piece.BLACK_QUEEN, 1);
        map.put(Piece.WHITE_KING, 1);
        map.put(Piece.BLACK_KING, 1);
        return map;
    }

    private void rebuildCapturedFromBoard() {
        Map<Piece, Integer> liveCounts = new EnumMap<>(Piece.class);
        for (Piece piece : Piece.values()) {
            if (piece != Piece.NONE) {
                liveCounts.put(piece, 0);
            }
        }

        for (Square square : Square.values()) {
            if (square == Square.NONE) {
                continue;
            }
            Piece piece = board.getPiece(square);
            if (piece != Piece.NONE) {
                liveCounts.put(piece, liveCounts.getOrDefault(piece, 0) + 1);
            }
        }

        for (Map.Entry<Piece, Integer> entry : STARTING_COUNTS.entrySet()) {
            Piece piece = entry.getKey();
            int missing = entry.getValue() - liveCounts.getOrDefault(piece, 0);
            for (int i = 0; i < Math.max(0, missing); i++) {
                if (piece.getPieceSide() == Side.WHITE) {
                    capturedByBlack.add(piece);
                } else {
                    capturedByWhite.add(piece);
                }
            }
        }
    }
}
