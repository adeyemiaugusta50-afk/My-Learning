package com.casualchess.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DescribeTest {
    @Test fun resultTextForEveryTermination() {
        fun text(winner: Color?, t: Termination) = Describe.result(GameResult(winner, t), "Ana", "Ben")
        assertEquals("Checkmate: Ana (White) wins", text(Color.WHITE, Termination.CHECKMATE))
        assertEquals("Ben (Black) resigned: Ana (White) wins", text(Color.WHITE, Termination.RESIGNATION))
        assertEquals("Draw by stalemate", text(null, Termination.STALEMATE))
        assertEquals("Draw by fivefold repetition", text(null, Termination.FIVEFOLD_REPETITION))
        assertEquals("Draw by the 75-move rule", text(null, Termination.SEVENTY_FIVE_MOVE_RULE))
        assertEquals("Draw by threefold repetition (claimed)", text(null, Termination.THREEFOLD_REPETITION_CLAIMED))
        assertEquals("Draw by the 50-move rule (claimed)", text(null, Termination.FIFTY_MOVE_RULE_CLAIMED))
        assertEquals("Draw by agreement", text(null, Termination.DRAW_AGREED))
        // Every termination has text (no `when` branch silently missing).
        Termination.entries.forEach { t ->
            val winner = if (t == Termination.CHECKMATE || t == Termination.RESIGNATION) Color.WHITE else null
            text(winner, t)
        }
    }

    @Test fun statusShowsTurnCheckAndResult() {
        val s = SessionState.newGame("Ana", "Ben", autoFlip = false)
        assertEquals("Ana (White) to move", Describe.status(s))
        val check = s.copy(game = ChessGame.new().uci("e2e4", "f7f5", "d1h5"))
        assertEquals("Ben (Black) to move: check!", Describe.status(check))
        val mate = s.copy(game = ChessGame.new().uci("f2f3", "e7e5", "g2g4", "d8h4"))
        assertEquals("Checkmate: Ben (Black) wins", Describe.status(mate))
    }

    @Test fun historyRowsPairWhiteAndBlackMoves() {
        val game = ChessGame.new().uci("e2e4", "e7e5", "g1f3")
        assertEquals(listOf("1. e4 e5", "2. Nf3"), Describe.historyRows(game))
    }

    @Test fun historyRowsHandleGamesThatStartWithBlackToMove() {
        val game = gameFrom("4k3/8/8/8/8/8/4P3/4K3 b - - 0 7").uci("e8d8", "e2e4")
        assertEquals(listOf("7... Kd8", "8. e4"), Describe.historyRows(game))
    }

    @Test fun squareDescriptionsHelpScreenReaders() {
        val s = SessionState.newGame("Ana", "Ben", autoFlip = false).tap(sq("g1")).state
        assertEquals("g1, white knight, selected", Describe.square(s, sq("g1")))
        assertEquals("f3, empty, legal move", Describe.square(s, sq("f3")))
        assertEquals("e7, black pawn", Describe.square(s, sq("e7")))
        val played = s.tap(sq("f3")).state
        assertEquals("f3, white knight, last move", Describe.square(played, sq("f3")))
    }
}
