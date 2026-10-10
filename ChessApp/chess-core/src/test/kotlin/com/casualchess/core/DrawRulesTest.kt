package com.casualchess.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawRulesTest {
    private val knightShuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")

    // ---- repetition -----------------------------------------------------------------------------------------

    @Test fun threefoldRepetitionIsClaimableButDoesNotEndTheGame() {
        var g = ChessGame.new()
        assertEquals(1, g.repetitionCount)
        g = g.uci(*knightShuffle)
        assertEquals(2, g.repetitionCount)
        assertTrue(g.claimableDraws.isEmpty(), "twofold is not enough")
        g = g.uci(*knightShuffle)
        assertEquals(3, g.repetitionCount)
        assertNull(g.result, "threefold repetition does not end the game by itself")
        assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), g.claimableDraws)
        // ...and the players may simply carry on.
        g = g.uci("e2e4")
        assertTrue(g.claimableDraws.isEmpty())
    }

    @Test fun claimingThreefoldRepetitionEndsTheGameAsADraw() {
        val g = ChessGame.new().uci(*knightShuffle, *knightShuffle)
        val claimed = g.claimDraw(DrawClaim.THREEFOLD_REPETITION)
        assertEquals(GameResult(null, Termination.THREEFOLD_REPETITION_CLAIMED), claimed.result)
        assertFalse(claimed.result!!.isAutomatic)
        assertTrue(claimed.legalMoves.isEmpty())
    }

    @Test fun fivefoldRepetitionEndsTheGameAutomatically() {
        var g = ChessGame.new()
        repeat(3) { g = g.uci(*knightShuffle) }
        assertEquals(4, g.repetitionCount)
        assertNull(g.result, "fourfold is still not automatic")
        g = g.uci(*knightShuffle)
        assertEquals(5, g.repetitionCount)
        assertEquals(GameResult(null, Termination.FIVEFOLD_REPETITION), g.result)
        assertTrue(g.result!!.isAutomatic)
        assertTrue(g.claimableDraws.isEmpty())
        assertFailsWith<IllegalStateException> { g.uci("e2e4") }
    }

    @Test fun claimingADrawThatIsNotAvailableIsRejected() {
        val g = ChessGame.new().uci("e2e4")
        assertFailsWith<IllegalArgumentException> { g.claimDraw(DrawClaim.THREEFOLD_REPETITION) }
        assertFailsWith<IllegalArgumentException> { g.claimDraw(DrawClaim.FIFTY_MOVE_RULE) }
    }

    @Test fun losingCastlingRightsMakesAPositionDifferentForRepetition() {
        val start = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
        // Same piece placement after four plies, but both sides have lost a castling right: not a repetition.
        val g = gameFrom(start).uci("h1g1", "h8g8", "g1h1", "g8h8")
        assertEquals(1, g.repetitionCount)
        assertEquals(start.substringBefore(' '), g.position.fen.substringBefore(' '))
        // Going round again repeats the *new* position.
        val again = g.uci("h1g1", "h8g8", "g1h1", "g8h8")
        assertEquals(2, again.repetitionCount)
    }

    @Test fun enPassantSquareOnlyCountsWhenACaptureIsReallyPossible() {
        val noCapture = pos("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        val plain = pos("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")
        assertEquals(plain.repetitionKey, noCapture.repetitionKey)

        val withCapture = pos("rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 3")
        val without = pos("rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 3")
        assertTrue(withCapture.hasLegalEnPassant)
        assertFalse(withCapture.repetitionKey == without.repetitionKey)

        // A pinned pawn's en passant capture is not legal, so it does not distinguish positions either.
        val pinned = pos("8/8/8/KPp4r/8/8/8/4k3 w - c6 0 1")
        val pinnedPlain = pos("8/8/8/KPp4r/8/8/8/4k3 w - - 0 1")
        assertEquals(pinnedPlain.repetitionKey, pinned.repetitionKey)
    }

    // ---- 50 / 75 move rules ---------------------------------------------------------------------------------

    @Test fun fiftyMoveRuleIsClaimableAtOneHundredPlies() {
        val start = gameFrom("4k3/8/8/8/8/8/8/R3K3 w - - 98 80")
        val ninetyNine = start.uci("a1a2")
        assertEquals(99, ninetyNine.position.halfmoveClock)
        assertTrue(ninetyNine.claimableDraws.isEmpty())
        val hundred = ninetyNine.uci("e8d8")
        assertEquals(100, hundred.position.halfmoveClock)
        assertNull(hundred.result, "the 50-move rule does not end the game automatically")
        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), hundred.claimableDraws)
        val claimed = hundred.claimDraw(DrawClaim.FIFTY_MOVE_RULE)
        assertEquals(GameResult(null, Termination.FIFTY_MOVE_RULE_CLAIMED), claimed.result)
    }

    @Test fun aPawnMoveOrCaptureResetsTheFiftyMoveCounter() {
        val pawn = gameFrom("4k3/8/8/8/8/8/4P3/R3K3 w - - 99 80").uci("e2e3")
        assertEquals(0, pawn.position.halfmoveClock)
        assertTrue(pawn.claimableDraws.isEmpty())
        val capture = gameFrom("r3k3/8/8/8/8/8/8/R3K3 w - - 99 80").uci("a1a8")
        assertEquals(0, capture.position.halfmoveClock)
    }

    @Test fun seventyFiveMoveRuleEndsTheGameAutomatically() {
        val g = gameFrom("4k3/8/8/8/8/8/8/R3K3 w - - 148 100")
        val one = g.uci("a1a2")
        assertNull(one.result)
        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), one.claimableDraws)
        val seventyFive = one.uci("e8d8")
        assertEquals(150, seventyFive.position.halfmoveClock)
        assertEquals(GameResult(null, Termination.SEVENTY_FIVE_MOVE_RULE), seventyFive.result)
        assertTrue(seventyFive.result!!.isAutomatic)
    }

    @Test fun checkmateOnTheSeventyFifthMoveIsAWinNotADraw() {
        val g = gameFrom("7k/8/5K2/8/8/8/8/6Q1 w - - 149 100").uci("g1g7")
        assertEquals(150, g.position.halfmoveClock)
        assertEquals(GameResult(Color.WHITE, Termination.CHECKMATE), g.result)
    }

    // ---- dead positions -------------------------------------------------------------------------------------

    @Test fun deadPositionsAreDetectedFromMaterial() {
        val dead = listOf(
            "4k3/8/8/8/8/8/8/4K3 w - - 0 1",        // K v K
            "4k3/8/8/8/8/8/8/3BK3 w - - 0 1",       // K+B v K
            "4k3/8/8/8/8/8/8/3NK3 w - - 0 1",       // K+N v K
            "4kb2/8/8/8/8/8/8/2B1K3 w - - 0 1",     // K+B v K+B, both bishops on dark squares
            "4k3/8/8/8/8/8/8/1B2KB2 b - - 0 1",     // two same-coloured bishops (c1... b1 light, f1 light)
        )
        dead.forEach { assertTrue(pos(it).hasInsufficientMaterial, "should be dead: $it") }
    }

    @Test fun materialThatCanStillMateIsNotDead() {
        val alive = listOf(
            "2b1k3/8/8/8/8/8/8/2B1K3 w - - 0 1",    // opposite-coloured bishops: helpmate is possible
            "4k3/8/8/8/8/8/8/2NNK3 w - - 0 1",      // K+N+N v K
            "4kn2/8/8/8/8/8/8/3NK3 w - - 0 1",      // K+N v K+N
            "4kn2/8/8/8/8/8/8/3BK3 w - - 0 1",      // K+B v K+N
            "4k3/8/8/8/8/8/8/R3K3 w - - 0 1",       // rook
            "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1",      // pawn
            "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1",       // queen
            "4k3/8/8/8/8/8/8/2BBK3 w - - 0 1",      // bishops on opposite colours (c1 dark, d1 light)
        )
        alive.forEach { assertFalse(pos(it).hasInsufficientMaterial, "should NOT be dead: $it") }
    }

    /** Builds a FEN from (square -> piece letter) pairs with Black to move, or null if it is not a valid position. */
    private fun mateSearchPosition(pieces: Map<Int, Char>): Position? {
        val rows = (7 downTo 0).joinToString("/") { rank ->
            buildString {
                var empty = 0
                for (file in 0..7) {
                    val c = pieces[Squares.of(file, rank)]
                    if (c == null) empty++ else { if (empty > 0) append(empty); empty = 0; append(c) }
                }
                if (empty > 0) append(empty)
            }
        }
        return try { Position.fromFen("$rows b - - 0 1") } catch (e: IllegalArgumentException) { null }
    }

    private fun existsCheckmate(minor: Char): Boolean {
        for (blackKing in 0 until 64) for (whiteKing in 0 until 64) for (m in 0 until 64) {
            if (blackKing == whiteKing || m == whiteKing || m == blackKing) continue
            val p = mateSearchPosition(mapOf(blackKing to 'k', whiteKing to 'K', m to minor)) ?: continue
            if (p.isCheck && p.legalMoves.isEmpty()) return true
        }
        return false
    }

    @Test fun bruteForce_noCheckmateExistsWithOnlyAKnightOrABishop() {
        // Enumerates every legal placement of K+N vs K and K+B vs K: none is checkmate, which proves these are
        // dead positions (a mate can never occur, even with the defender's cooperation).
        assertFalse(existsCheckmate('N'), "K+N v K")
        assertFalse(existsCheckmate('B'), "K+B v K")
        // Sanity check of the search itself: with a rook a checkmate position does exist.
        assertTrue(existsCheckmate('R'), "K+R v K has mate positions, so the search must find one")
        assertTrue(existsCheckmate('Q'), "K+Q v K has mate positions, so the search must find one")
    }

    @Test fun twoKnightsCanBeMated_soTheyAreNotDead() {
        // Black Ka8 mated by Nc7, with Kb6 and Nc6 covering a7, b7 and b8: a real (cooperative) mate with K+N+N v K.
        val p = pos("k7/2N5/1KN5/8/8/8/8/8 b - - 0 1")
        assertTrue(p.isCheck && p.legalMoves.isEmpty())
        assertFalse(p.hasInsufficientMaterial)
    }

    @Test fun gameEndsWhenCapturesLeaveADeadPosition() {
        val g = gameFrom("4k3/8/8/8/8/8/3r4/4K3 w - - 0 1")
        assertNull(g.result)
        val after = g.uci("e1d2")
        assertEquals(GameResult(null, Termination.INSUFFICIENT_MATERIAL), after.result)
    }

    @Test fun knownLimitation_blockedPawnFortressIsNotDetectedAsDead() {
        // Both kings and a completely locked pawn pair: no checkmate is possible, but detecting this in general
        // needs search, so the engine (like most rules libraries) does not call it dead. Documented limitation.
        val g = gameFrom("8/8/8/k1p5/2P5/8/8/K7 w - - 0 1")
        assertNull(g.result)
    }

    // ---- resignation / agreement ----------------------------------------------------------------------------

    @Test fun resignationGivesTheOpponentTheWin() {
        val g = ChessGame.new().uci("e2e4").resign(Color.BLACK)
        assertEquals(GameResult(Color.WHITE, Termination.RESIGNATION), g.result)
        assertEquals("1-0", g.result!!.score)
        assertTrue(g.legalMoves.isEmpty())
        assertFailsWith<IllegalStateException> { g.resign(Color.WHITE) }
    }

    @Test fun agreedDrawEndsTheGame() {
        val g = ChessGame.new().uci("e2e4").agreeDraw()
        assertEquals(GameResult(null, Termination.DRAW_AGREED), g.result)
        assertEquals("1/2-1/2", g.result!!.score)
        assertTrue(g.result!!.isDraw)
        assertFalse(g.result!!.isAutomatic)
    }

    @Test fun automaticAndClaimedResultsAreDistinguished() {
        val automatic = listOf(
            Termination.CHECKMATE, Termination.STALEMATE, Termination.INSUFFICIENT_MATERIAL,
            Termination.FIVEFOLD_REPETITION, Termination.SEVENTY_FIVE_MOVE_RULE,
        )
        val manual = listOf(
            Termination.THREEFOLD_REPETITION_CLAIMED, Termination.FIFTY_MOVE_RULE_CLAIMED,
            Termination.DRAW_AGREED, Termination.RESIGNATION,
        )
        automatic.forEach { assertTrue(GameResult(null, it).isAutomatic, "$it") }
        manual.forEach { assertFalse(GameResult(null, it).isAutomatic, "$it") }
    }
}
