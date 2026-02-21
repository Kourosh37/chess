package com.example.game;

import com.github.bhlangonijr.chesslib.Piece;

public record MoveOutcome(
    boolean valid,
    String message,
    boolean humanMove,
    String fromSquare,
    String toSquare,
    Piece movedPiece
) {

    public static MoveOutcome valid(String notation, boolean humanMove, String fromSquare, String toSquare, Piece movedPiece) {
        return new MoveOutcome(true, notation, humanMove, fromSquare, toSquare, movedPiece);
    }

    public static MoveOutcome invalid(String message) {
        return new MoveOutcome(false, message, false, null, null, Piece.NONE);
    }
}
