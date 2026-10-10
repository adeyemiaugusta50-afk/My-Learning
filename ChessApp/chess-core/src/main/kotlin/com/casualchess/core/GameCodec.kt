package com.casualchess.core

import java.net.URLDecoder
import java.net.URLEncoder

/** What gets written to disk for "resume game". Selection and pending promotion are deliberately not saved. */
data class SavedSession(
    val game: ChessGame,
    val whiteName: String,
    val blackName: String,
    val whiteAtBottom: Boolean,
    val autoFlip: Boolean,
) {
    fun toState(): SessionState = SessionState(
        game = game,
        whiteName = whiteName,
        blackName = blackName,
        whiteAtBottom = whiteAtBottom,
        autoFlip = autoFlip,
    )

    companion object {
        /** Builds the save for [state]; a waiting board rotation is applied first (see [SessionState.settled]). */
        fun from(state: SessionState): SavedSession {
            val s = state.settled()
            return SavedSession(s.game, s.whiteName, s.blackName, s.whiteAtBottom, s.autoFlip)
        }
    }
}

/**
 * A small, versioned text format for autosave:
 *
 * ```
 * CCGAME 1
 * fen <start position FEN, including its half-move clock and move number>
 * moves e2e4 e7e5 ...
 * result <W|B|-> <TERMINATION>        (only if a player ended the game: resignation, agreed draw, claimed draw)
 * white <url-encoded name>
 * black <url-encoded name>
 * bottom <w|b>
 * autoflip <0|1>
 * ```
 *
 * Only the start position and the moves are stored. Castling rights, en passant state, move counters and the
 * repetition history are rebuilt by replaying the moves through the rules engine, so they cannot drift out of sync
 * and a corrupted or hand-edited file is rejected instead of producing an illegal game.
 */
object GameCodec {
    private const val HEADER = "CCGAME 1"

    fun encode(save: SavedSession): String {
        val game = save.game
        val manual = game.result?.takeUnless { it.isAutomatic }
        return buildString {
            appendLine(HEADER)
            appendLine("fen ${game.initialPosition.fen}")
            appendLine("moves ${game.moves.joinToString(" ") { it.uci }}")
            if (manual != null) {
                val who = when (manual.winner) {
                    Color.WHITE -> "W"
                    Color.BLACK -> "B"
                    null -> "-"
                }
                appendLine("result $who ${manual.termination.name}")
            }
            appendLine("white ${URLEncoder.encode(save.whiteName, "UTF-8")}")
            appendLine("black ${URLEncoder.encode(save.blackName, "UTF-8")}")
            appendLine("bottom ${if (save.whiteAtBottom) "w" else "b"}")
            appendLine("autoflip ${if (save.autoFlip) 1 else 0}")
        }
    }

    /** Returns the saved session, or null if the text is not a valid save (wrong version, corrupt, or illegal moves). */
    fun decode(text: String): SavedSession? = try {
        val lines = text.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
        require(lines.firstOrNull() == HEADER) { "Unknown save format" }
        val fields = lines.drop(1).associate { line ->
            val space = line.indexOf(' ')
            if (space < 0) line to "" else line.substring(0, space) to line.substring(space + 1)
        }
        val initial = Position.fromFen(requireNotNull(fields["fen"]) { "Missing fen" })
        val moves = fields["moves"].orEmpty().split(' ').filter { it.isNotEmpty() }
            .map { requireNotNull(Move.fromUci(it)) { "Bad move '$it'" } }
        val manual = fields["result"]?.let { parseResult(it) }
        val game = ChessGame.restore(initial, moves, manual)
        SavedSession(
            game = game,
            whiteName = SessionState.cleanName(URLDecoder.decode(fields["white"].orEmpty(), "UTF-8"), "White"),
            blackName = SessionState.cleanName(URLDecoder.decode(fields["black"].orEmpty(), "UTF-8"), "Black"),
            whiteAtBottom = fields["bottom"] != "b",
            autoFlip = fields["autoflip"] != "0",
        )
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IllegalStateException) {
        null
    }

    private fun parseResult(value: String): GameResult {
        val parts = value.split(' ')
        require(parts.size == 2) { "Bad result '$value'" }
        val winner = when (parts[0]) {
            "W" -> Color.WHITE
            "B" -> Color.BLACK
            "-" -> null
            else -> throw IllegalArgumentException("Bad winner '${parts[0]}'")
        }
        val termination = Termination.entries.firstOrNull { it.name == parts[1] }
            ?: throw IllegalArgumentException("Bad termination '${parts[1]}'")
        return GameResult(winner, termination)
    }
}
