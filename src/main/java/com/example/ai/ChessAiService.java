package com.example.ai;

import com.github.bhlangonijr.chesslib.Board;

public interface ChessAiService {

    String chooseMove(Board board, int searchDepth);
}
