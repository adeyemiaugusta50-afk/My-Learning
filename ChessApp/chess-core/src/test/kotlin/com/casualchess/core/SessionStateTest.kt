package com.casualchess.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionStateTest {
    private fun fresh(autoFlip: Boolean = true) = SessionState.newGame("Ana", "Ben", autoFlip)

    /** Simulates a finger on the screen cell that currently shows [square]. */
    private fun SessionState.tapSquareOnScreen(name: String): Step {
        val cell = BoardGeometry.cellOf(sq(name), whiteAtBottom)
        return tap(BoardGeometry.squareAt(cell, whiteAtBottom))
    }

    private fun SessionState.move(from: String, to: String): SessionState {
        val a = tapSquareOnScreen(from)
        return a.state.tapSquareOnScreen(to).state
    }

    // ---- selection and moving -------------------------------------------------------------------------------

    @Test fun tappingYourPieceShowsItsLegalTargets() {
        val step = fresh().tap(sq("g1"))
        assertEquals(SessionEvent.Selected, step.event)
        assertEquals(sq("g1"), step.state.selected)
        assertEquals(setOf(sq("f3"), sq("h3")), step.state.targets)
    }

    @Test fun tappingTheSelectedPieceAgainDeselectsIt() {
        val s = fresh().tap(sq("g1")).state
        val step = s.tap(sq("g1"))
        assertEquals(SessionEvent.Deselected, step.event)
        assertNull(step.state.selected)
    }

    @Test fun tappingAnotherOwnPieceSwitchesTheSelection() {
        val step = fresh().tap(sq("g1")).state.tap(sq("e2"))
        assertEquals(sq("e2"), step.state.selected)
        assertEquals(setOf(sq("e3"), sq("e4")), step.state.targets)
    }

    @Test fun tappingOpponentPieceOrEmptySquareWithNothingSelectedDoesNothing() {
        assertEquals(SessionEvent.None, fresh().tap(sq("e7")).event)
        assertEquals(SessionEvent.None, fresh().tap(sq("e4")).event)
        assertNull(fresh().tap(sq("e7")).state.selected)
    }

    @Test fun tappingAnIllegalTargetClearsTheSelection() {
        val step = fresh().tap(sq("g1")).state.tap(sq("g5"))
        assertNull(step.state.selected)
    }

    @Test fun completingAMovePlaysItAndReportsTheRecord() {
        val step = fresh(autoFlip = false).tap(sq("e2")).state.tap(sq("e4"))
        val event = assertIs<SessionEvent.MovePlayed>(step.event)
        assertEquals("e4", event.record.san)
        assertEquals(Color.BLACK, step.state.sideToMove)
        assertEquals(Move(sq("e2"), sq("e4")), step.state.lastMove)
        assertNull(step.state.selected)
    }

    @Test fun checkedKingIsReportedForHighlighting() {
        var s = fresh(autoFlip = false)
        for ((a, b) in listOf("e2" to "e4", "f7" to "f5", "d1" to "h5")) s = s.move(a, b)
        assertEquals(sq("e8"), s.checkedKingSquare)
    }

    @Test fun gameOverIgnoresTaps() {
        var s = fresh(autoFlip = false)
        for ((a, b) in listOf("f2" to "f3", "e7" to "e5", "g2" to "g4", "d8" to "h4")) s = s.move(a, b)
        assertEquals(Termination.CHECKMATE, s.game.result?.termination)
        assertEquals(SessionEvent.None, s.tap(sq("a2")).event)
        assertEquals(sq("e1"), s.checkedKingSquare, "the mated king is highlighted")
    }

    // ---- promotion ------------------------------------------------------------------------------------------

    private fun promotionState() = SessionState(ChessGame.new(pos("8/P6k/8/8/8/8/8/K7 w - - 0 1")), autoFlip = false)

    @Test fun reachingTheLastRankAsksWhichPieceToPromoteTo() {
        val step = promotionState().tap(sq("a7")).state.tap(sq("a8"))
        assertEquals(SessionEvent.PromotionRequested, step.event)
        assertEquals(PendingPromotion(sq("a7"), sq("a8")), step.state.pendingPromotion)
        assertEquals(Color.WHITE, step.state.sideToMove, "the pawn has not moved yet")
        // Taps are ignored while the chooser is open.
        assertSame(step.state, step.state.tap(sq("h7")).state)
    }

    @Test fun choosingAPieceCompletesThePromotion_includingUnderpromotion() {
        for (type in PieceType.PROMOTIONS) {
            val chosen = promotionState().tap(sq("a7")).state.tap(sq("a8")).state.choosePromotion(type)
            assertEquals(Piece.of(Color.WHITE, type), chosen.state.game.position.pieceAt(sq("a8")))
            assertNull(chosen.state.pendingPromotion)
            val event = assertIs<SessionEvent.MovePlayed>(chosen.event)
            assertTrue(event.record.san.contains("=${San.pieceLetter(type)}"), event.record.san)
        }
    }

    @Test fun cancellingThePromotionChooserLeavesThePositionUnchanged() {
        val pending = promotionState().tap(sq("a7")).state.tap(sq("a8")).state
        val cancelled = pending.cancelPromotion()
        assertNull(cancelled.pendingPromotion)
        assertNull(cancelled.selected)
        assertEquals(pending.game.position.fen, cancelled.game.position.fen)
    }

    // ---- automatic and manual flipping ----------------------------------------------------------------------

    @Test fun completedMoveSchedulesARotationThatKeepsTheMoveVisibleFirst() {
        val step = fresh().tap(sq("e2")).state.tap(sq("e4"))
        val s = step.state
        assertTrue(s.flipPending)
        assertTrue(s.whiteAtBottom, "board has NOT rotated yet, so the completed move can be seen")
        assertEquals(Move(sq("e2"), sq("e4")), s.lastMove)
        val flipped = s.finishFlip(expectedPly = 1)
        assertFalse(flipped.whiteAtBottom)
        assertFalse(flipped.flipPending)
        assertEquals(Move(sq("e2"), sq("e4")), flipped.lastMove, "last-move highlight survives the rotation")
    }

    @Test fun boardAlternatesAfterEveryMove() {
        var s = fresh()
        val moves = listOf("e2" to "e4", "e7" to "e5", "g1" to "f3", "b8" to "c6")
        var expectWhiteAtBottom = true
        for ((from, to) in moves) {
            assertEquals(expectWhiteAtBottom, s.whiteAtBottom)
            s = s.move(from, to)
            s = s.finishFlip()
            expectWhiteAtBottom = !expectWhiteAtBottom
        }
        assertEquals(expectWhiteAtBottom, s.whiteAtBottom)
    }

    @Test fun touchMappingStaysCorrectAfterRotation() {
        // After White's e4 the board rotates. Black must be able to tap the cell that SHOWS e7, then the cell that
        // SHOWS e5 - with the rotated mapping - and get the move e7-e5.
        var s = fresh().move("e2", "e4").finishFlip()
        assertFalse(s.whiteAtBottom)
        val e7Cell = BoardGeometry.cellOf(sq("e7"), whiteAtBottom = false)
        assertEquals(Cell(row = 6, col = 3), e7Cell)
        val selected = s.tap(BoardGeometry.squareAt(e7Cell, s.whiteAtBottom)).state
        assertEquals(sq("e7"), selected.selected)
        val e5Cell = BoardGeometry.cellOf(sq("e5"), whiteAtBottom = false)
        s = selected.tap(BoardGeometry.squareAt(e5Cell, s.whiteAtBottom)).state
        assertEquals(Move(sq("e7"), sq("e5")), s.lastMove)
        // Tapping with the OLD (unrotated) mapping would have hit a different square entirely.
        assertTrue(BoardGeometry.squareAt(e7Cell, true) != sq("e7"))
    }

    @Test fun tapDuringTheShortDelayOnlyRotatesImmediately() {
        val moved = fresh().tap(sq("e2")).state.tap(sq("e4")).state
        val step = moved.tap(sq("e7"))
        assertEquals(SessionEvent.FlippedEarly, step.event)
        assertFalse(step.state.whiteAtBottom)
        assertNull(step.state.selected, "the tap that skipped the wait is not also a selection")
    }

    @Test fun aStaleTimerCannotFlipTheBoardTwice() {
        val first = fresh().tap(sq("e2")).state.tap(sq("e4")).state            // schedules flip for ply 1
        val flipped = first.finishFlip(expectedPly = 1)
        assertFalse(flipped.whiteAtBottom)
        assertSame(flipped, flipped.finishFlip(expectedPly = 1), "nothing is pending any more")
        val second = flipped.tap(sq("e7")).state.tap(sq("e5")).state          // schedules flip for ply 2
        assertSame(second, second.finishFlip(expectedPly = 1), "timer for ply 1 must be ignored")
        assertTrue(second.flipPending)
    }

    @Test fun manualFlipTogglesImmediately() {
        val s = fresh(autoFlip = false).flipManually()
        assertFalse(s.whiteAtBottom)
        assertTrue(s.flipManually().whiteAtBottom)
    }

    @Test fun manualFlipDuringAPendingRotationDoesNotFlipTwice() {
        val moved = fresh().tap(sq("e2")).state.tap(sq("e4")).state
        val flipped = moved.flipManually()
        assertFalse(flipped.whiteAtBottom, "performed the pending rotation once")
        assertFalse(flipped.flipPending)
        assertFalse(flipped.finishFlip().whiteAtBottom, "a late timer changes nothing")
    }

    @Test fun autoFlipOffNeverRotates() {
        val s = fresh(autoFlip = false).tap(sq("e2")).state.tap(sq("e4")).state
        assertFalse(s.flipPending)
        assertTrue(s.whiteAtBottom)
    }

    @Test fun switchingAutoFlipOffCancelsAPendingRotation() {
        val moved = fresh().tap(sq("e2")).state.tap(sq("e4")).state
        assertTrue(moved.flipPending)
        val off = moved.withAutoFlip(false)
        assertFalse(off.flipPending)
        assertTrue(off.whiteAtBottom)
        val on = off.withAutoFlip(true)
        assertFalse(on.flipPending, "turning it back on affects the next move, not the past one")
    }

    @Test fun noRotationWhenTheGameEnds() {
        var s = fresh()
        for ((a, b) in listOf("f2" to "f3", "e7" to "e5", "g2" to "g4")) s = s.move(a, b).finishFlip()
        s = s.move("d8", "h4")
        assertTrue(s.game.isOver)
        assertFalse(s.flipPending)
    }

    // ---- ending the game, leaving, names --------------------------------------------------------------------

    @Test fun resignationAgreementAndClaimsEndTheGameAndClearTransientState() {
        val base = fresh().tap(sq("e2")).state.tap(sq("e4")).state
        val resigned = base.resign(Color.BLACK)
        assertEquals(GameResult(Color.WHITE, Termination.RESIGNATION), resigned.game.result)
        assertFalse(resigned.flipPending)
        assertEquals(GameResult(null, Termination.DRAW_AGREED), base.agreeDraw().game.result)
    }

    @Test fun leavingNeedsConfirmationOnlyWhileAGameIsInProgress() {
        val s = fresh()
        assertFalse(s.needsLeaveConfirmation, "nothing to lose before the first move")
        val playing = s.tap(sq("e2")).state.tap(sq("e4")).state
        assertTrue(playing.needsLeaveConfirmation)
        assertFalse(playing.resign(Color.WHITE).needsLeaveConfirmation, "finished game")
        assertTrue(playing.resign(Color.WHITE).hasProgress)
    }

    @Test fun playerNamesAreCleaned() {
        assertEquals("White", SessionState.cleanName("   ", "White"))
        assertEquals("A B", SessionState.cleanName("  A \n  B ", "White"))
        assertEquals(MAX_NAME_LENGTH, SessionState.cleanName("x".repeat(100), "White").length)
    }
}
