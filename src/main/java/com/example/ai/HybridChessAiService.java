package com.example.ai;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveGenerator;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class HybridChessAiService implements ChessAiService {

    private static final int MATE_SCORE = 100_000;
    private final Random random = new Random();

    @Override
    public String chooseMove(Board board, int searchDepth) {
        List<Move> legalMoves = legalMoves(board);
        if (legalMoves.isEmpty()) {
            return null;
        }

        if (searchDepth <= 1) {
            Move randomMove = legalMoves.get(random.nextInt(legalMoves.size()));
            return toUci(randomMove);
        }

        Side rootSide = board.getSideToMove();
        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE;

        for (Move move : legalMoves) {
            board.doMove(move);
            int score = -negamax(board, searchDepth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1, rootSide.flip());
            board.undoMove();

            if (bestMove == null || score > bestScore) {
                bestMove = move;
                bestScore = score;
            }
        }

        return bestMove != null ? toUci(bestMove) : null;
    }

    private int negamax(Board board, int depth, int alpha, int beta, Side perspectiveSide) {
        if (depth == 0 || board.isMated() || board.isDraw()) {
            return evaluate(board, perspectiveSide);
        }

        List<Move> legalMoves = legalMoves(board);
        if (legalMoves.isEmpty()) {
            return evaluate(board, perspectiveSide);
        }

        int bestScore = Integer.MIN_VALUE;
        for (Move move : legalMoves) {
            board.doMove(move);
            int score = -negamax(board, depth - 1, -beta, -alpha, perspectiveSide.flip());
            board.undoMove();

            bestScore = Math.max(bestScore, score);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                break;
            }
        }
        return bestScore;
    }

    private int evaluate(Board board, Side perspectiveSide) {
        if (board.isMated()) {
            return board.getSideToMove() == perspectiveSide ? -MATE_SCORE : MATE_SCORE;
        }

        int material = 0;
        for (Piece piece : board.boardToArray()) {
            material += pieceValue(piece);
        }

        int mobility = legalMoves(board).size();
        int signedMobility = board.getSideToMove() == Side.WHITE ? mobility : -mobility;

        return perspectiveSide == Side.WHITE ? material + signedMobility * 3 : -material - signedMobility * 3;
    }

    private int pieceValue(Piece piece) {
        return switch (piece) {
            case WHITE_PAWN -> 100;
            case WHITE_KNIGHT -> 320;
            case WHITE_BISHOP -> 330;
            case WHITE_ROOK -> 500;
            case WHITE_QUEEN -> 900;
            case WHITE_KING -> 20_000;
            case BLACK_PAWN -> -100;
            case BLACK_KNIGHT -> -320;
            case BLACK_BISHOP -> -330;
            case BLACK_ROOK -> -500;
            case BLACK_QUEEN -> -900;
            case BLACK_KING -> -20_000;
            default -> 0;
        };
    }

    private List<Move> legalMoves(Board board) {
        try {
            return MoveGenerator.generateLegalMoves(board);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String toUci(Move move) {
        StringBuilder builder = new StringBuilder();
        builder.append(move.getFrom().value().toLowerCase());
        builder.append(move.getTo().value().toLowerCase());

        Piece promotion = move.getPromotion();
        if (promotion != Piece.NONE) {
            String symbol = promotion.value().toLowerCase();
            builder.append(symbol.charAt(symbol.length() - 1));
        }

        return builder.toString();
    }
}
