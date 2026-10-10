package com.casualchess.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameCodecTest {
    private fun roundTrip(state: SessionState): SavedSession {
        val text = GameCodec.encode(SavedSession.from(state))
        return assertNotNull(GameCodec.decode(text), "decode failed for:\n$text")
    }

    private fun assertSameRulesState(a: ChessGame, b: ChessGame) {
        assertEquals(a.position.fen, b.position.fen, "FEN (placement, rights, en passant, counters)")
        assertEquals(a.position.uciMoves(), b.position.uciMoves(), "legal moves")
        assertEquals(a.repetitionCount, b.repetitionCount, "repetition count")
        assertEquals(a.claimableDraws, b.claimableDraws, "claimable draws")
        assertEquals(a.result, b.result, "result")
        assertEquals(a.moves, b.moves, "move list")
        assertEquals(a.records.map { it.san }, b.records.map { it.san }, "SAN history")
        assertEquals(a.initialPosition.fen, b.initialPosition.fen)
    }

    @Test fun midGameSurvivesSaveAndResume() {
        val game = ChessGame.new().uci("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1")
        val resumed = roundTrip(SessionState(game)).game
        assertSameRulesState(game, resumed)
        // White castled: it has no castling rights left, Black still has both.
        assertEquals(Position.BLACK_KINGSIDE or Position.BLACK_QUEENSIDE, resumed.position.castlingRights)
    }

    @Test fun repetitionHistoryAndDrawEligibilitySurvive() {
        val shuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")
        val game = ChessGame.new().uci(*shuffle, *shuffle)
        assertEquals(3, game.repetitionCount)
        val resumed = roundTrip(SessionState(game)).game
        assertEquals(3, resumed.repetitionCount)
        assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), resumed.claimableDraws)
        // The restored game keeps counting from the saved history: two more cycles make it fivefold.
        val fivefold = resumed.uci(*shuffle, *shuffle)
        assertEquals(GameResult(null, Termination.FIVEFOLD_REPETITION), fivefold.result)
    }

    @Test fun fiftyMoveCounterSurvives() {
        val game = gameFrom("4k3/8/8/8/8/8/8/R3K3 w - - 98 80").uci("a1a2", "e8d8")
        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), game.claimableDraws)
        val resumed = roundTrip(SessionState(game)).game
        assertEquals(100, resumed.position.halfmoveClock)
        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), resumed.claimableDraws)
        // Play on after resuming: the counter keeps running from the saved value and the claim stays available.
        val later = resumed.uci("a2a3", "d8e8")
        assertEquals(102, later.position.halfmoveClock)
        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), later.claimableDraws)
    }

    @Test fun enPassantRightSurvivesWhenSavedImmediatelyAfterADoublePush() {
        val game = ChessGame.new().uci("e2e4", "a7a6", "e4e5", "d7d5")
        assertTrue("e5d6" in game.position.uciMoves())
        val resumed = roundTrip(SessionState(game)).game
        assertTrue("e5d6" in resumed.position.uciMoves(), "en passant must still be available after resume")
        assertSameRulesState(game, resumed)
    }

    @Test fun customStartPositionKeepsItsCountersAndRights() {
        val fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 37 61"
        val game = gameFrom(fen).uci("e1g1")
        val resumed = roundTrip(SessionState(game)).game
        assertEquals(fen, resumed.initialPosition.fen)
        assertSameRulesState(game, resumed)
        assertEquals(38, resumed.position.halfmoveClock)
    }

    @Test fun gameEndedByAPlayerStaysEnded() {
        val resigned = ChessGame.new().uci("e2e4", "e7e5").resign(Color.WHITE)
        assertEquals(resigned.result, roundTrip(SessionState(resigned)).game.result)

        val drawn = ChessGame.new().uci("e2e4").agreeDraw()
        assertEquals(drawn.result, roundTrip(SessionState(drawn)).game.result)

        val shuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")
        val claimed = ChessGame.new().uci(*shuffle, *shuffle).claimDraw(DrawClaim.THREEFOLD_REPETITION)
        assertEquals(claimed.result, roundTrip(SessionState(claimed)).game.result)
    }

    @Test fun automaticResultsAreRederivedNotStored() {
        val mate = ChessGame.new().uci("f2f3", "e7e5", "g2g4", "d8h4")
        val text = GameCodec.encode(SavedSession.from(SessionState(mate)))
        assertFalse(text.lines().any { it.startsWith("result") }, "checkmate is derived from the moves")
        assertEquals(mate.result, GameCodec.decode(text)!!.game.result)
    }

    @Test fun sessionSettingsSurvive() {
        val state = SessionState.newGame("Zoë & Ünal", "Black\nPlayer", autoFlip = false)
            .copy(game = ChessGame.new().uci("e2e4"), whiteAtBottom = false)
        val resumed = roundTrip(state)
        assertEquals("Zoë & Ünal", resumed.whiteName)
        assertEquals("Black Player", resumed.blackName)
        assertFalse(resumed.whiteAtBottom)
        assertFalse(resumed.autoFlip)
    }

    @Test fun pendingRotationIsAppliedBeforeSaving() {
        // After White moves, the board is waiting to rotate for Black. If the app is killed in that moment, the saved
        // orientation must already be the one Black should see.
        val step = SessionState.newGame("A", "B", autoFlip = true).tap(sq("e2")).state.tap(sq("e4"))
        assertTrue(step.state.flipPending)
        assertTrue(step.state.whiteAtBottom)
        val resumed = roundTrip(step.state)
        assertFalse(resumed.whiteAtBottom)
        assertFalse(resumed.toState().flipPending)
    }

    @Test fun corruptOrUnsafeSavesAreRejected() {
        assertNull(GameCodec.decode(""))
        assertNull(GameCodec.decode("garbage"))
        assertNull(GameCodec.decode("CCGAME 2\nfen ${Position.START_FEN}\nmoves e2e4\n"))
        assertNull(GameCodec.decode("CCGAME 1\nmoves e2e4\n"), "missing fen")
        assertNull(GameCodec.decode("CCGAME 1\nfen ${Position.START_FEN}\nmoves e2e5\n"), "illegal move")
        assertNull(GameCodec.decode("CCGAME 1\nfen ${Position.START_FEN}\nmoves e2e4 zzzz\n"), "unparsable move")
        assertNull(GameCodec.decode("CCGAME 1\nfen nonsense\nmoves\n"), "bad fen")
        assertNull(GameCodec.decode("CCGAME 1\nfen ${Position.START_FEN}\nmoves e2e4\nresult - THREEFOLD_REPETITION_CLAIMED\n"), "claim that was never available")
        assertNull(GameCodec.decode("CCGAME 1\nfen ${Position.START_FEN}\nmoves\nresult W CHECKMATE\n"), "automatic result cannot be injected")
        assertNull(GameCodec.decode("CCGAME 1\nfen ${Position.START_FEN}\nmoves\nresult X RESIGNATION\n"))
    }

    @Test fun truncatedSaveIsRejectedOrStillConsistent() {
        val text = GameCodec.encode(SavedSession.from(SessionState(ChessGame.new().uci("e2e4", "e7e5", "g1f3"))))
        for (cut in 0 until text.length) {
            val decoded = GameCodec.decode(text.substring(0, cut)) ?: continue
            // Any save that does decode must be a legal game.
            ChessGame.fromMoves(decoded.game.initialPosition, decoded.game.moves)
        }
    }
}
