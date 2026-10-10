package com.casualchess.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RulesTest {
    // ---- pins and king safety -------------------------------------------------------------------------------

    @Test fun pinnedKnightCannotMove() {
        val p = pos("4k3/4r3/8/8/8/8/4N3/4K3 w - - 0 1")
        assertTrue(p.movesFrom("e2").isEmpty(), "A knight pinned on the e-file has no legal move")
    }

    @Test fun pinnedRookMayOnlyMoveAlongThePin() {
        val p = pos("4k3/4r3/8/8/8/8/4R3/4K3 w - - 0 1")
        assertEquals(setOf("e2e3", "e2e4", "e2e5", "e2e6", "e2e7"), p.movesFrom("e2"))
    }

    @Test fun pinnedBishopCannotMoveOffTheDiagonal() {
        val p = pos("4k3/8/8/b7/8/8/3B4/4K3 w - - 0 1")
        // Bishop d2 is pinned by the bishop on a5 against the king on e1; it may only move along a5-e1.
        assertEquals(setOf("d2c3", "d2b4", "d2a5"), p.movesFrom("d2"))
    }

    @Test fun kingCannotStepIntoCheck() {
        val p = pos("4k3/8/8/8/8/8/5r2/4K3 w - - 0 1")
        assertFalse("e1f1" in p.uciMoves())
        assertFalse("e1e2" in p.uciMoves())
        assertFailsWith<IllegalArgumentException> { p.play(Move(sq("e1"), sq("e2"))) }
    }

    @Test fun inCheckOnlyEvasionsAreLegal() {
        // The white king on e1 is checked by the rook on e8. Legal replies: step aside (not onto the e-file) or block
        // with the knight on e4. Every other knight move leaves the king in check.
        val p = pos("4r1k1/8/8/8/8/8/3N4/4K3 w - - 0 1")
        assertTrue(p.isCheck)
        assertEquals(setOf("e1d1", "e1f1", "e1f2", "d2e4"), p.uciMoves())
    }

    @Test fun cannotCaptureOwnPieceOrMoveOffBoard() {
        val p = Position.initial()
        assertFalse("a1a2" in p.uciMoves())
        assertFalse("e1e2" in p.uciMoves())
        assertFailsWith<IllegalArgumentException> { p.play(Move(sq("e2"), sq("e5"))) }
        assertFailsWith<IllegalArgumentException> { p.play(Move(sq("e7"), sq("e5"))) } // not black's turn
    }

    // ---- castling -------------------------------------------------------------------------------------------

    @Test fun bothCastlesAvailableWhenClear() {
        val moves = pos("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").uciMoves()
        assertTrue("e1g1" in moves && "e1c1" in moves)
    }

    @Test fun cannotCastleThroughAttackedSquare() {
        // Black rook on f7 attacks f1: kingside castling would pass through check, queenside is fine.
        val moves = pos("4k3/5r2/8/8/8/8/8/R3K2R w KQ - 0 1").uciMoves()
        assertFalse("e1g1" in moves, "castling through an attacked square")
        assertTrue("e1c1" in moves)
    }

    @Test fun cannotCastleOutOfCheck() {
        val moves = pos("4k3/4r3/8/8/8/8/8/R3K2R w KQ - 0 1").uciMoves()
        assertFalse("e1g1" in moves)
        assertFalse("e1c1" in moves)
    }

    @Test fun cannotCastleIntoCheck() {
        val moves = pos("4k3/6r1/8/8/8/8/8/R3K2R w KQ - 0 1").uciMoves()
        assertFalse("e1g1" in moves, "g1 is attacked")
        assertTrue("e1c1" in moves)
    }

    @Test fun queensideCastlingAllowedWhenOnlyTheRooksPathIsAttacked() {
        // b1 is attacked by the black rook on b8, but only the king's squares (e1, d1, c1) must be safe.
        val moves = pos("1r2k3/8/8/8/8/8/8/R3K2R w KQ - 0 1").uciMoves()
        assertTrue("e1c1" in moves)
    }

    @Test fun cannotCastleWithPieceInTheWay() {
        val moves = pos("4k3/8/8/8/8/8/8/RN2K2R w KQ - 0 1").uciMoves()
        assertFalse("e1c1" in moves)
        assertTrue("e1g1" in moves)
    }

    @Test fun castlingMovesTheRookAndRemovesRights() {
        val after = pos("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").play(Move(sq("e1"), sq("g1")))
        assertEquals(Piece.of(Color.WHITE, PieceType.KING), after.pieceAt(sq("g1")))
        assertEquals(Piece.of(Color.WHITE, PieceType.ROOK), after.pieceAt(sq("f1")))
        assertEquals(null, after.pieceAt(sq("h1")))
        assertEquals(Position.BLACK_KINGSIDE or Position.BLACK_QUEENSIDE, after.castlingRights)
        val queenSide = pos("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1").play(Move(sq("e8"), sq("c8")))
        assertEquals(Piece.of(Color.BLACK, PieceType.ROOK), queenSide.pieceAt(sq("d8")))
        assertEquals(null, queenSide.pieceAt(sq("a8")))
    }

    @Test fun movingARookLosesOnlyThatSidesRight() {
        val g = gameFrom("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").uci("h1g1")
        assertEquals(Position.WHITE_QUEENSIDE or Position.BLACK_KINGSIDE or Position.BLACK_QUEENSIDE, g.position.castlingRights)
        // Moving the rook back does not give the right back.
        val back = g.uci("h8g8", "g1h1")
        assertFalse(back.position.hasCastlingRight(Position.WHITE_KINGSIDE))
        assertFalse("e1g1" in back.position.uciMoves())
    }

    @Test fun capturingARookOnItsHomeSquareRemovesTheRight() {
        val g = gameFrom("4k3/8/8/8/8/8/6b1/R3K2R b KQ - 0 1").uci("g2h1")
        assertFalse(g.position.hasCastlingRight(Position.WHITE_KINGSIDE))
        assertTrue(g.position.hasCastlingRight(Position.WHITE_QUEENSIDE))
    }

    @Test fun kingMoveLosesBothRights() {
        val g = gameFrom("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").uci("e1e2")
        assertFalse(g.position.hasCastlingRight(Position.WHITE_KINGSIDE))
        assertFalse(g.position.hasCastlingRight(Position.WHITE_QUEENSIDE))
        assertTrue(g.position.hasCastlingRight(Position.BLACK_KINGSIDE))
    }

    // ---- en passant -----------------------------------------------------------------------------------------

    @Test fun enPassantIsAvailableOnlyImmediately() {
        val g = Position.initial().let { ChessGame.new(it) }.uci("e2e4", "a7a6", "e4e5", "d7d5")
        assertEquals(sq("d6"), g.position.enPassantSquare)
        assertTrue("e5d6" in g.position.uciMoves(), "en passant is possible right after the double push")
        // If white plays something else, the right is gone for good.
        val later = g.uci("g1f3", "a6a5")
        assertEquals(-1, later.position.enPassantSquare)
        assertFalse("e5d6" in later.position.uciMoves())
    }

    @Test fun enPassantCaptureRemovesTheCapturedPawn() {
        val g = ChessGame.new().uci("e2e4", "a7a6", "e4e5", "d7d5", "e5d6")
        assertEquals(Piece.of(Color.WHITE, PieceType.PAWN), g.position.pieceAt(sq("d6")))
        assertEquals(null, g.position.pieceAt(sq("d5")), "the captured pawn disappears from d5")
        assertTrue(g.records.last().info.isEnPassant)
        assertEquals("exd6", g.records.last().san)
    }

    @Test fun noEnPassantWhenNoPawnCanCapture() {
        // The double push only sets an en passant square; it is not a *legal capture* unless a pawn can take.
        val p = Position.initial().play(Move(sq("e2"), sq("e4")))
        assertEquals(sq("e3"), p.enPassantSquare)
        assertFalse(p.hasLegalEnPassant)
    }

    @Test fun enPassantThatExposesTheKingIsIllegal() {
        // White Ka5 and pawn b5, black pawn c5 just moved c7-c5, black rook h5. bxc6 e.p. would clear both pawns
        // off the fifth rank and expose the king to the rook.
        val p = pos("8/8/8/KPp4r/8/8/8/4k3 w - c6 0 1")
        assertFalse("b5c6" in p.uciMoves())
        assertTrue("b5b6" in p.uciMoves())
        assertFalse(p.hasLegalEnPassant)
        assertFailsWith<IllegalArgumentException> { p.play(Move(sq("b5"), sq("c6"))) }
    }

    @Test fun enPassantMayResolveACheckByRemovingTheCheckingPawn() {
        val p = pos("4k3/8/8/2Pp4/4K3/8/8/8 w - d6 0 1")
        assertTrue(p.isCheck)
        assertTrue("c5d6" in p.uciMoves())
    }

    // ---- promotion ------------------------------------------------------------------------------------------

    @Test fun promotionOffersAllFourPieces() {
        val p = pos("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        assertEquals(setOf("a7a8q", "a7a8r", "a7a8b", "a7a8n"), p.movesFrom("a7"))
    }

    @Test fun pawnCannotReachTheLastRankWithoutChoosingAPiece() {
        val p = pos("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        assertFailsWith<IllegalArgumentException> { p.play(Move(sq("a7"), sq("a8"))) }
    }

    @Test fun everyUnderpromotionProducesTheChosenPiece() {
        val start = pos("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        for (type in PieceType.PROMOTIONS) {
            val after = start.play(Move(sq("a7"), sq("a8"), type))
            assertEquals(Piece.of(Color.WHITE, type), after.pieceAt(sq("a8")))
        }
    }

    @Test fun knightUnderpromotionCanGiveCheckWhereTheQueenCannot() {
        val p = pos("8/2k1P3/8/8/8/8/8/K7 w - - 0 1")
        val knight = San.of(p, Move(sq("e7"), sq("e8"), PieceType.KNIGHT))
        val queen = San.of(p, Move(sq("e7"), sq("e8"), PieceType.QUEEN))
        assertEquals("e8=N+", knight)
        assertEquals("e8=Q", queen)
    }

    @Test fun promotionByCaptureAndBlackPromotion() {
        val white = pos("3r4/4P3/8/8/8/8/p7/K6k w - - 0 1")
        assertEquals(
            setOf("e7e8q", "e7e8r", "e7e8b", "e7e8n", "e7d8q", "e7d8r", "e7d8b", "e7d8n"),
            white.movesFrom("e7"),
        )
        val black = pos("7k/8/8/8/8/8/p7/2K5 b - - 0 1")
        assertEquals(setOf("a2a1q", "a2a1r", "a2a1b", "a2a1n"), black.movesFrom("a2"))
    }

    // ---- check, mate, stalemate -----------------------------------------------------------------------------

    @Test fun foolsMateIsCheckmate() {
        val g = ChessGame.new().uci("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(GameResult(Color.BLACK, Termination.CHECKMATE), g.result)
        assertTrue(g.legalMoves.isEmpty())
        assertEquals("Qh4#", g.records.last().san)
        assertFailsWith<IllegalStateException> { g.play(Move(sq("e2"), sq("e3"))) }
    }

    @Test fun scholarsMate() {
        val g = ChessGame.new().uci("e2e4", "e7e5", "d1h5", "b8c6", "f1c4", "g8f6", "h5f7")
        assertEquals(GameResult(Color.WHITE, Termination.CHECKMATE), g.result)
        assertEquals("Qxf7#", g.records.last().san)
    }

    @Test fun stalemateIsADrawNotALoss() {
        val g = gameFrom("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertFalse(g.position.isCheck)
        assertEquals(GameResult(null, Termination.STALEMATE), g.result)
        assertTrue(g.legalMoves.isEmpty())
    }

    @Test fun checkIsReportedWithoutEndingTheGame() {
        val g = ChessGame.new().uci("e2e4", "f7f5", "d1h5")
        assertTrue(g.isCheck)
        assertEquals(null, g.result)
        assertEquals("Qh5+", g.records.last().san)
        assertTrue(g.records.last().givesCheck)
    }

    // ---- notation -------------------------------------------------------------------------------------------

    @Test fun sanDisambiguatesByFile() {
        val p = pos("4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1")
        assertEquals("Nbd2", San.of(p, Move(sq("b1"), sq("d2"))))
        assertEquals("Nfd2", San.of(p, Move(sq("f1"), sq("d2"))))
    }

    @Test fun sanDisambiguatesByRank() {
        val p = pos("4k3/8/8/N7/8/8/8/N3K3 w - - 0 1")
        assertEquals("N5b3", San.of(p, Move(sq("a5"), sq("b3"))))
        assertEquals("N1b3", San.of(p, Move(sq("a1"), sq("b3"))))
    }

    @Test fun sanForCastlingAndCaptures() {
        val g = gameFrom("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").uci("e1g1", "e8c8")
        assertEquals(listOf("O-O", "O-O-O"), g.records.map { it.san })
        val cap = ChessGame.new().uci("e2e4", "d7d5", "e4d5", "d8d5", "b1c3", "d5e5")
        assertEquals(listOf("e4", "d5", "exd5", "Qxd5", "Nc3", "Qe5+"), cap.records.map { it.san })
    }

    @Test fun movesReportWhatTheyDo() {
        val g = ChessGame.new().uci("e2e4", "d7d5", "e4d5")
        val info = assertNotNull(g.records.last().info)
        assertEquals(Piece.of(Color.BLACK, PieceType.PAWN), info.captured)
        assertTrue(g.records.last().isCapture)
        assertFalse(g.records.first().isCapture)
    }
}
