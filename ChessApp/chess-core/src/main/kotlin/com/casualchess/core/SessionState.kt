package com.casualchess.core

/** How long the completed move stays visible before the board rotates in two-player mode. */
const val FLIP_DELAY_MS: Long = 900L

const val MAX_NAME_LENGTH = 24

/** A pawn move to the last rank that is waiting for the player to choose the promotion piece. */
data class PendingPromotion(val from: Int, val to: Int)

/** What happened as the result of a user action; the UI uses it for sounds and announcements. */
sealed interface SessionEvent {
    data object None : SessionEvent
    data object Selected : SessionEvent
    data object Deselected : SessionEvent
    data object PromotionRequested : SessionEvent

    /** A tap that arrived while the board was waiting to rotate; it only completes the rotation. */
    data object FlippedEarly : SessionEvent
    data class MovePlayed(val record: MoveRecord, val result: GameResult?) : SessionEvent
}

data class Step(val state: SessionState, val event: SessionEvent)

/**
 * Everything the game screen needs, as one immutable value. All user actions are pure functions returning a new
 * state, which makes the behaviour easy to unit-test without any Android classes.
 *
 * Board orientation: [whiteAtBottom] is the orientation currently drawn. With [autoFlip] on (two players, one phone),
 * a completed move sets [flipPendingAtPly]; the UI keeps showing the move for [FLIP_DELAY_MS] and then calls
 * [finishFlip], which rotates the board by 180 degrees. Pieces are always drawn upright; only the squares rotate.
 */
data class SessionState(
    val game: ChessGame,
    val whiteName: String = "White",
    val blackName: String = "Black",
    val whiteAtBottom: Boolean = true,
    val autoFlip: Boolean = true,
    val selected: Int? = null,
    val pendingPromotion: PendingPromotion? = null,
    /** Ply count (moves played so far) at which a rotation is waiting, or null. */
    val flipPendingAtPly: Int? = null,
) {
    val flipPending: Boolean get() = flipPendingAtPly != null
    val sideToMove: Color get() = game.position.sideToMove
    val lastMove: Move? get() = game.records.lastOrNull()?.move

    /** Destination squares for the selected piece. */
    val targets: Set<Int>
        get() = selected?.let { from -> game.legalMoves.filter { it.from == from }.map { it.to }.toSet() } ?: emptySet()

    /** The king square to highlight: the side to move is in check or has been checkmated. */
    val checkedKingSquare: Int?
        get() = if (game.position.isCheck) game.position.kingSquare(game.position.sideToMove) else null

    fun nameOf(color: Color): String = if (color == Color.WHITE) whiteName else blackName

    /** True once any move was played or the game ended, i.e. leaving would lose something worth confirming. */
    val hasProgress: Boolean get() = game.hasMoves || game.isOver

    /** True while the game is running and a "New game" or "Restart" should ask for confirmation first. */
    val needsLeaveConfirmation: Boolean get() = game.hasMoves && !game.isOver

    /** Handles a tap on a board [square] (already converted from the screen cell via [BoardGeometry]). */
    fun tap(square: Int): Step {
        if (game.isOver || pendingPromotion != null) return Step(this, SessionEvent.None)
        if (flipPending) return Step(finishFlip(), SessionEvent.FlippedEarly)

        val piece = game.position.pieceAt(square)
        val from = selected
        if (from != null && square in targets) {
            val candidates = game.legalMoves.filter { it.from == from && it.to == square }
            // Several candidates for one from/to pair can only be the four promotions.
            return if (candidates.size > 1 || candidates.first().promotion != null) {
                Step(copy(pendingPromotion = PendingPromotion(from, square)), SessionEvent.PromotionRequested)
            } else {
                playMove(candidates.first())
            }
        }
        return when {
            piece?.color == sideToMove && square == from -> Step(copy(selected = null), SessionEvent.Deselected)
            piece?.color == sideToMove -> Step(copy(selected = square), SessionEvent.Selected)
            from != null -> Step(copy(selected = null), SessionEvent.Deselected)
            else -> Step(this, SessionEvent.None)
        }
    }

    /** Completes a pending promotion with the chosen piece. */
    fun choosePromotion(type: PieceType): Step {
        val pending = pendingPromotion ?: return Step(this, SessionEvent.None)
        require(type in PieceType.PROMOTIONS) { "Cannot promote to $type" }
        return playMove(Move(pending.from, pending.to, type))
    }

    fun cancelPromotion(): SessionState = copy(pendingPromotion = null, selected = null)

    private fun playMove(move: Move): Step {
        val next = game.play(move)
        val record = next.records.last()
        val flip = autoFlip && !next.isOver
        val state = copy(
            game = next,
            selected = null,
            pendingPromotion = null,
            flipPendingAtPly = if (flip) next.records.size else null,
        )
        return Step(state, SessionEvent.MovePlayed(record, next.result))
    }

    /**
     * Rotates the board by 180 degrees now. When [expectedPly] is given the rotation only happens if it is still the
     * one that was scheduled, so a stale timer (after undo, restart or another move) cannot flip the board twice.
     */
    fun finishFlip(expectedPly: Int? = null): SessionState {
        val pending = flipPendingAtPly ?: return this
        if (expectedPly != null && expectedPly != pending) return this
        return copy(whiteAtBottom = !whiteAtBottom, flipPendingAtPly = null)
    }

    /** The manual flip button. If an automatic rotation is waiting this simply performs it, so the board never flips twice. */
    fun flipManually(): SessionState =
        if (flipPending) finishFlip() else copy(whiteAtBottom = !whiteAtBottom)

    /** Turns automatic flipping on or off; turning it off cancels a rotation that is waiting. */
    fun withAutoFlip(enabled: Boolean): SessionState =
        copy(autoFlip = enabled, flipPendingAtPly = if (enabled) flipPendingAtPly else null)

    fun resign(color: Color): SessionState = ended(game.resign(color))
    fun agreeDraw(): SessionState = ended(game.agreeDraw())
    fun claimDraw(claim: DrawClaim): SessionState = ended(game.claimDraw(claim))

    private fun ended(next: ChessGame) =
        copy(game = next, selected = null, pendingPromotion = null, flipPendingAtPly = null)

    /**
     * The state to persist: a waiting rotation is applied first, so that after resuming the player to move is
     * looking at the board from their own side.
     */
    fun settled(): SessionState = finishFlip().copy(selected = null, pendingPromotion = null)

    companion object {
        fun newGame(whiteName: String, blackName: String, autoFlip: Boolean): SessionState =
            SessionState(
                game = ChessGame.new(),
                whiteName = cleanName(whiteName, "White"),
                blackName = cleanName(blackName, "Black"),
                autoFlip = autoFlip,
            )

        fun cleanName(raw: String, fallback: String): String =
            raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).ifEmpty { fallback }
    }
}
