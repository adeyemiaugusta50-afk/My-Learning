package com.casualchess.core

enum class Termination {
    // Automatic: the game ends by itself.
    CHECKMATE,
    STALEMATE,
    INSUFFICIENT_MATERIAL,
    FIVEFOLD_REPETITION,
    SEVENTY_FIVE_MOVE_RULE,

    // Not automatic: a player has to ask for it (or, for resignation / agreement, decide it).
    THREEFOLD_REPETITION_CLAIMED,
    FIFTY_MOVE_RULE_CLAIMED,
    DRAW_AGREED,
    RESIGNATION,
}

/** The outcome of a finished game. [winner] is null for a draw. */
data class GameResult(val winner: Color?, val termination: Termination) {
    val isDraw: Boolean get() = winner == null

    /** PGN-style score. */
    val score: String
        get() = when (winner) {
            Color.WHITE -> "1-0"
            Color.BLACK -> "0-1"
            null -> "1/2-1/2"
        }

    /** Whether the game stopped on its own, as opposed to a player's decision. */
    val isAutomatic: Boolean
        get() = termination in AUTOMATIC

    companion object {
        private val AUTOMATIC = setOf(
            Termination.CHECKMATE, Termination.STALEMATE, Termination.INSUFFICIENT_MATERIAL,
            Termination.FIVEFOLD_REPETITION, Termination.SEVENTY_FIVE_MOVE_RULE,
        )
    }
}

/** Draws that a player *may claim* (the game goes on if nobody claims). */
enum class DrawClaim { THREEFOLD_REPETITION, FIFTY_MOVE_RULE }

/** One move of the game with everything the UI needs to show it. */
data class MoveRecord(
    val move: Move,
    val san: String,
    val info: MoveInfo,
    val givesCheck: Boolean,
) {
    val isCapture: Boolean get() = info.captured != null
}

/**
 * An immutable game: the starting position, every move played and every position reached.
 *
 * Draw rules follow FIDE (Laws of Chess, Article 9 and 5.2):
 *  - Stalemate, dead position (see [Position.hasInsufficientMaterial]), fivefold repetition and the 75-move rule end
 *    the game automatically. Checkmate is decided first, so a 75th move that mates is a win, not a draw.
 *  - Threefold repetition and the 50-move rule can only be *claimed* ([claimableDraws] / [claimDraw]).
 *
 * All rule-relevant history (repetition counts, castling rights, en passant state, move counters) is derived by
 * replaying the moves from [initialPosition], so [GameCodec] only has to store the start position and the moves.
 */
class ChessGame private constructor(
    val initialPosition: Position,
    val records: List<MoveRecord>,
    private val positions: List<Position>,
    private val repetitionCounts: Map<String, Int>,
    private val manualResult: GameResult?,
) {
    val position: Position get() = positions.last()
    val moves: List<Move> get() = records.map { it.move }

    /** Result of the game, or null while it is still going. */
    val result: GameResult? = manualResult ?: automaticResult()
    val isOver: Boolean get() = result != null

    val legalMoves: List<Move> get() = if (isOver) emptyList() else position.legalMoves

    /** Number of times the current position (FIDE sense, see [Position.repetitionKey]) has occurred. */
    val repetitionCount: Int get() = repetitionCounts[position.repetitionKey] ?: 0

    val isCheck: Boolean get() = position.isCheck

    /** Draws available to claim right now (empty once the game is over). */
    val claimableDraws: Set<DrawClaim>
        get() {
            if (isOver) return emptySet()
            val claims = mutableSetOf<DrawClaim>()
            if (repetitionCount >= 3) claims += DrawClaim.THREEFOLD_REPETITION
            if (position.halfmoveClock >= 100) claims += DrawClaim.FIFTY_MOVE_RULE
            return claims
        }

    private fun automaticResult(): GameResult? {
        val pos = position
        if (pos.legalMoves.isEmpty()) {
            return if (pos.isCheck) GameResult(pos.sideToMove.opposite, Termination.CHECKMATE)
            else GameResult(null, Termination.STALEMATE)
        }
        if (pos.hasInsufficientMaterial) return GameResult(null, Termination.INSUFFICIENT_MATERIAL)
        if ((repetitionCounts[pos.repetitionKey] ?: 0) >= 5) return GameResult(null, Termination.FIVEFOLD_REPETITION)
        if (pos.halfmoveClock >= 150) return GameResult(null, Termination.SEVENTY_FIVE_MOVE_RULE)
        return null
    }

    /** Plays [move]; throws if the game is over or the move is illegal. */
    fun play(move: Move): ChessGame {
        check(!isOver) { "The game is over" }
        val before = position
        require(before.isLegal(move)) { "Illegal move ${move.uci} in ${before.fen}" }
        val after = before.play(move)
        val record = MoveRecord(move, San.of(before, move), before.describe(move)!!, after.isCheck)
        val counts = repetitionCounts + (after.repetitionKey to ((repetitionCounts[after.repetitionKey] ?: 0) + 1))
        return ChessGame(initialPosition, records + record, positions + after, counts, null)
    }

    fun resign(color: Color): ChessGame = finish(GameResult(color.opposite, Termination.RESIGNATION))

    /** Both players agreed to a draw. */
    fun agreeDraw(): ChessGame = finish(GameResult(null, Termination.DRAW_AGREED))

    /** The player to move claims a draw; throws if that draw is not currently claimable. */
    fun claimDraw(claim: DrawClaim): ChessGame {
        require(claim in claimableDraws) { "$claim is not claimable now" }
        return finish(
            GameResult(
                null,
                if (claim == DrawClaim.THREEFOLD_REPETITION) Termination.THREEFOLD_REPETITION_CLAIMED
                else Termination.FIFTY_MOVE_RULE_CLAIMED,
            ),
        )
    }

    private fun finish(result: GameResult): ChessGame {
        check(!isOver) { "The game is over" }
        return ChessGame(initialPosition, records, positions, repetitionCounts, result)
    }

    val hasMoves: Boolean get() = records.isNotEmpty()

    companion object {
        fun new(initial: Position = Position.initial()): ChessGame =
            ChessGame(initial, emptyList(), listOf(initial), mapOf(initial.repetitionKey to 1), null)

        /** Replays [moves] from [initial]; throws if any move is illegal. */
        fun fromMoves(initial: Position, moves: List<Move>): ChessGame =
            moves.fold(new(initial)) { game, move -> game.play(move) }

        /**
         * Rebuilds a game that ended by a player's decision ([manual]). Claimed draws are only accepted if the claim
         * was really available at that point.
         */
        fun restore(initial: Position, moves: List<Move>, manual: GameResult?): ChessGame {
            val game = fromMoves(initial, moves)
            if (manual == null) return game
            require(!game.isOver) { "Game already ended automatically" }
            return when (manual.termination) {
                Termination.RESIGNATION -> game.resign(requireNotNull(manual.winner) { "Resignation needs a winner" }.opposite)
                Termination.DRAW_AGREED -> game.agreeDraw()
                Termination.THREEFOLD_REPETITION_CLAIMED -> game.claimDraw(DrawClaim.THREEFOLD_REPETITION)
                Termination.FIFTY_MOVE_RULE_CLAIMED -> game.claimDraw(DrawClaim.FIFTY_MOVE_RULE)
                else -> throw IllegalArgumentException("Not a manual termination: ${manual.termination}")
            }
        }
    }
}
