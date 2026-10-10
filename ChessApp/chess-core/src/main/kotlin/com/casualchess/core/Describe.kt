package com.casualchess.core

/** Human-readable English text for game state, kept out of the UI layer so it can be unit-tested. */
object Describe {
    fun colorName(color: Color): String = if (color == Color.WHITE) "White" else "Black"

    /** One-line result such as "Checkmate: Ana (White) wins" or "Draw by stalemate". */
    fun result(result: GameResult, whiteName: String, blackName: String): String {
        fun player(color: Color) = "${if (color == Color.WHITE) whiteName else blackName} (${colorName(color)})"
        val winner = result.winner
        return when (result.termination) {
            Termination.CHECKMATE -> "Checkmate: ${player(winner!!)} wins"
            Termination.RESIGNATION -> "${player(winner!!.opposite)} resigned: ${player(winner)} wins"
            Termination.STALEMATE -> "Draw by stalemate"
            Termination.INSUFFICIENT_MATERIAL -> "Draw: dead position (neither side can checkmate)"
            Termination.FIVEFOLD_REPETITION -> "Draw by fivefold repetition"
            Termination.SEVENTY_FIVE_MOVE_RULE -> "Draw by the 75-move rule"
            Termination.THREEFOLD_REPETITION_CLAIMED -> "Draw by threefold repetition (claimed)"
            Termination.FIFTY_MOVE_RULE_CLAIMED -> "Draw by the 50-move rule (claimed)"
            Termination.DRAW_AGREED -> "Draw by agreement"
        }
    }

    /** Whose turn it is, or the result once the game is over. */
    fun status(state: SessionState): String {
        state.game.result?.let { return result(it, state.whiteName, state.blackName) }
        val side = state.sideToMove
        val who = "${state.nameOf(side)} (${colorName(side)}) to move"
        return if (state.game.isCheck) "$who: check!" else who
    }

    fun claimLabel(claim: DrawClaim): String = when (claim) {
        DrawClaim.THREEFOLD_REPETITION -> "Claim draw: threefold repetition"
        DrawClaim.FIFTY_MOVE_RULE -> "Claim draw: 50-move rule"
    }

    fun pieceName(piece: Piece): String =
        "${colorName(piece.color).lowercase()} ${piece.type.name.lowercase()}"

    /** Move list as numbered rows: "1. e4 e5", "2. Nf3 Nc6", ... (a game starting with Black to move begins "1... e5"). */
    fun historyRows(game: ChessGame): List<String> {
        val records = game.records
        val rows = ArrayList<String>()
        var number = game.initialPosition.fullmoveNumber
        var i = 0
        if (game.initialPosition.sideToMove == Color.BLACK && records.isNotEmpty()) {
            rows += "$number... ${records[0].san}"
            i = 1
            number++
        }
        while (i < records.size) {
            val black = records.getOrNull(i + 1)?.san
            rows += "$number. ${records[i].san}${if (black != null) " $black" else ""}"
            i += 2
            number++
        }
        return rows
    }

    /** Accessibility text for a board square. */
    fun square(state: SessionState, square: Int): String {
        val piece = state.game.position.pieceAt(square)
        val parts = ArrayList<String>()
        parts += Squares.name(square)
        parts += piece?.let { pieceName(it) } ?: "empty"
        if (square == state.selected) parts += "selected"
        if (square in state.targets) parts += if (piece != null) "capture available" else "legal move"
        val last = state.lastMove
        if (last != null && (square == last.from || square == last.to)) parts += "last move"
        if (square == state.checkedKingSquare) parts += if (state.game.result?.termination == Termination.CHECKMATE) "checkmated king" else "king in check"
        return parts.joinToString(", ")
    }
}
