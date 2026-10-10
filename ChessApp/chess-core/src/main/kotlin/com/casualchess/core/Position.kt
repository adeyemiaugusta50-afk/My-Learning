package com.casualchess.core

/**
 * An immutable chess position with full legal-move generation.
 *
 * Holds everything FEN holds: piece placement, side to move, castling rights, the en passant target square, the
 * half-move clock (for the 50/75-move rules) and the full-move number. Applying a move returns a new [Position].
 */
class Position private constructor(
    private val squares: IntArray,
    val sideToMove: Color,
    /** Bit set of [WHITE_KINGSIDE], [WHITE_QUEENSIDE], [BLACK_KINGSIDE], [BLACK_QUEENSIDE]. */
    val castlingRights: Int,
    /** The square a pawn just skipped over with a two-square push (FEN convention), or -1. Lasts one move only. */
    val enPassantSquare: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    fun pieceAt(square: Int): Piece? = Piece.fromCode(squares[square])

    fun hasCastlingRight(right: Int): Boolean = castlingRights and right != 0

    fun kingSquare(color: Color): Int = if (color == Color.WHITE) whiteKing else blackKing

    private val whiteKing: Int = squares.indexOf(Piece.of(Color.WHITE, PieceType.KING).code)
    private val blackKing: Int = squares.indexOf(Piece.of(Color.BLACK, PieceType.KING).code)

    /** True if [square] is attacked by any piece of colour [by]. */
    fun isAttacked(square: Int, by: Color): Boolean {
        val f = Squares.file(square)
        val r = Squares.rank(square)
        // Pawns attack diagonally forward, so the attacker sits one rank "behind" the target from its point of view.
        val pawnRank = if (by == Color.WHITE) r - 1 else r + 1
        if (pawnRank in 0..7) {
            val pawn = Piece.of(by, PieceType.PAWN).code
            if (f > 0 && squares[pawnRank * 8 + f - 1] == pawn) return true
            if (f < 7 && squares[pawnRank * 8 + f + 1] == pawn) return true
        }
        val knight = Piece.of(by, PieceType.KNIGHT).code
        for (t in Tables.KNIGHT[square]) if (squares[t] == knight) return true
        val king = Piece.of(by, PieceType.KING).code
        for (t in Tables.KING[square]) if (squares[t] == king) return true
        val rook = Piece.of(by, PieceType.ROOK).code
        val bishop = Piece.of(by, PieceType.BISHOP).code
        val queen = Piece.of(by, PieceType.QUEEN).code
        for (d in Tables.ORTHOGONAL) if (rayHits(f, r, d[0], d[1], rook, queen)) return true
        for (d in Tables.DIAGONAL) if (rayHits(f, r, d[0], d[1], bishop, queen)) return true
        return false
    }

    private fun rayHits(file: Int, rank: Int, df: Int, dr: Int, a: Int, b: Int): Boolean {
        var f = file + df
        var r = rank + dr
        while (f in 0..7 && r in 0..7) {
            val p = squares[r * 8 + f]
            if (p != 0) return p == a || p == b
            f += df
            r += dr
        }
        return false
    }

    /** True if the side to move is in check. */
    val isCheck: Boolean get() = isAttacked(kingSquare(sideToMove), sideToMove.opposite)

    /** All legal moves for the side to move (cached; positions are immutable). */
    val legalMoves: List<Move> by lazy(LazyThreadSafetyMode.PUBLICATION) { generateLegalMoves() }

    fun isLegal(move: Move): Boolean = move in legalMoves

    /** Generates legal moves without caching. Used by perft-style counting to avoid retaining every list. */
    fun generateLegalMoves(): List<Move> {
        val mover = sideToMove
        val out = ArrayList<Move>(40)
        for (m in pseudoLegalMoves()) {
            val next = applyUnchecked(m)
            if (!next.isAttacked(next.kingSquare(mover), mover.opposite)) out.add(m)
        }
        return out
    }

    private fun pseudoLegalMoves(): List<Move> {
        val moves = ArrayList<Move>(64)
        val me = sideToMove
        val them = me.opposite
        for (from in 0 until 64) {
            val piece = Piece.fromCode(squares[from]) ?: continue
            if (piece.color != me) continue
            when (piece.type) {
                PieceType.PAWN -> pawnMoves(from, me, moves)
                PieceType.KNIGHT -> for (to in Tables.KNIGHT[from]) addIfNotOwn(from, to, me, moves)
                PieceType.KING -> {
                    for (to in Tables.KING[from]) addIfNotOwn(from, to, me, moves)
                    castlingMoves(me, them, moves)
                }
                PieceType.BISHOP -> slide(from, me, Tables.DIAGONAL, moves)
                PieceType.ROOK -> slide(from, me, Tables.ORTHOGONAL, moves)
                PieceType.QUEEN -> {
                    slide(from, me, Tables.DIAGONAL, moves)
                    slide(from, me, Tables.ORTHOGONAL, moves)
                }
            }
        }
        return moves
    }

    private fun colorOf(code: Int): Color? = Piece.fromCode(code)?.color

    private fun addIfNotOwn(from: Int, to: Int, me: Color, moves: MutableList<Move>) {
        if (colorOf(squares[to]) != me) moves.add(Move(from, to))
    }

    private fun slide(from: Int, me: Color, dirs: Array<IntArray>, moves: MutableList<Move>) {
        for (d in dirs) {
            var f = Squares.file(from) + d[0]
            var r = Squares.rank(from) + d[1]
            while (f in 0..7 && r in 0..7) {
                val to = r * 8 + f
                val occupant = colorOf(squares[to])
                if (occupant == null) {
                    moves.add(Move(from, to))
                } else {
                    if (occupant != me) moves.add(Move(from, to))
                    break
                }
                f += d[0]
                r += d[1]
            }
        }
    }

    private fun pawnMoves(from: Int, me: Color, moves: MutableList<Move>) {
        val dir = if (me == Color.WHITE) 1 else -1
        val startRank = if (me == Color.WHITE) 1 else 6
        val lastRank = if (me == Color.WHITE) 7 else 0
        val f = Squares.file(from)
        val r = Squares.rank(from)
        fun add(to: Int) {
            if (Squares.rank(to) == lastRank) {
                for (p in PieceType.PROMOTIONS) moves.add(Move(from, to, p))
            } else {
                moves.add(Move(from, to))
            }
        }
        val one = from + 8 * dir
        if (squares[one] == 0) {
            add(one)
            val two = from + 16 * dir
            if (r == startRank && squares[two] == 0) moves.add(Move(from, two))
        }
        for (df in intArrayOf(-1, 1)) {
            val tf = f + df
            if (tf !in 0..7) continue
            val to = (r + dir) * 8 + tf
            val occupant = colorOf(squares[to])
            if (occupant != null && occupant != me) add(to)
            else if (occupant == null && to == enPassantSquare) add(to)
        }
    }

    private fun castlingMoves(me: Color, them: Color, moves: MutableList<Move>) {
        val rank = if (me == Color.WHITE) 0 else 7
        val kingFrom = Squares.of(4, rank)
        if (squares[kingFrom] != Piece.of(me, PieceType.KING).code) return
        val kingside = if (me == Color.WHITE) WHITE_KINGSIDE else BLACK_KINGSIDE
        val queenside = if (me == Color.WHITE) WHITE_QUEENSIDE else BLACK_QUEENSIDE
        if (!hasCastlingRight(kingside) && !hasCastlingRight(queenside)) return
        if (isAttacked(kingFrom, them)) return // cannot castle out of check
        val rook = Piece.of(me, PieceType.ROOK).code
        if (hasCastlingRight(kingside) && squares[Squares.of(7, rank)] == rook &&
            squares[Squares.of(5, rank)] == 0 && squares[Squares.of(6, rank)] == 0 &&
            !isAttacked(Squares.of(5, rank), them) && !isAttacked(Squares.of(6, rank), them)
        ) moves.add(Move(kingFrom, Squares.of(6, rank)))
        if (hasCastlingRight(queenside) && squares[Squares.of(0, rank)] == rook &&
            squares[Squares.of(1, rank)] == 0 && squares[Squares.of(2, rank)] == 0 && squares[Squares.of(3, rank)] == 0 &&
            !isAttacked(Squares.of(3, rank), them) && !isAttacked(Squares.of(2, rank), them)
        ) moves.add(Move(kingFrom, Squares.of(2, rank)))
    }

    /** Describes what [move] does in this position, or null if there is no piece on its origin square. */
    fun describe(move: Move): MoveInfo? {
        val piece = pieceAt(move.from) ?: return null
        val target = pieceAt(move.to)
        val isEnPassant = piece.type == PieceType.PAWN && target == null && move.to == enPassantSquare &&
            Squares.file(move.from) != Squares.file(move.to)
        val capturedSquare = when {
            isEnPassant -> Squares.of(Squares.file(move.to), Squares.rank(move.from))
            target != null -> move.to
            else -> null
        }
        val captured = if (isEnPassant) pieceAt(capturedSquare!!) else target
        val castle = if (piece.type == PieceType.KING && Squares.file(move.from) == 4) {
            when (Squares.file(move.to)) {
                6 -> CastleSide.KINGSIDE
                2 -> CastleSide.QUEENSIDE
                else -> null
            }
        } else null
        return MoveInfo(piece, captured, capturedSquare, isEnPassant, castle)
    }

    /** Plays a legal move and returns the new position; throws [IllegalArgumentException] if the move is illegal. */
    fun play(move: Move): Position {
        require(isLegal(move)) { "Illegal move ${move.uci} in $fen" }
        return applyUnchecked(move)
    }

    private fun applyUnchecked(move: Move): Position {
        val info = describe(move)!!
        val piece = info.piece
        val next = squares.copyOf()
        next[move.from] = 0
        info.capturedSquare?.let { next[it] = 0 }
        next[move.to] = if (move.promotion != null) Piece.of(piece.color, move.promotion).code else piece.code
        info.castle?.let { side ->
            val rank = Squares.rank(move.from)
            val rookFrom = Squares.of(if (side == CastleSide.KINGSIDE) 7 else 0, rank)
            val rookTo = Squares.of(if (side == CastleSide.KINGSIDE) 5 else 3, rank)
            next[rookTo] = next[rookFrom]
            next[rookFrom] = 0
        }

        var rights = castlingRights
        if (piece.type == PieceType.KING) {
            rights = rights and (if (piece.color == Color.WHITE) (WHITE_KINGSIDE or WHITE_QUEENSIDE) else (BLACK_KINGSIDE or BLACK_QUEENSIDE)).inv()
        }
        // A rook moving from, or being captured on, its home square removes that castling right.
        for (square in intArrayOf(move.from, move.to)) {
            rights = when (square) {
                Squares.A1 -> rights and WHITE_QUEENSIDE.inv()
                Squares.H1 -> rights and WHITE_KINGSIDE.inv()
                Squares.A8 -> rights and BLACK_QUEENSIDE.inv()
                Squares.H8 -> rights and BLACK_KINGSIDE.inv()
                else -> rights
            }
        }

        val isDoublePush = piece.type == PieceType.PAWN && kotlin.math.abs(move.to - move.from) == 16
        val newEnPassant = if (isDoublePush) (move.from + move.to) / 2 else -1
        val resetsClock = piece.type == PieceType.PAWN || info.captured != null
        return Position(
            squares = next,
            sideToMove = sideToMove.opposite,
            castlingRights = rights,
            enPassantSquare = newEnPassant,
            halfmoveClock = if (resetsClock) 0 else halfmoveClock + 1,
            fullmoveNumber = if (sideToMove == Color.BLACK) fullmoveNumber + 1 else fullmoveNumber,
        )
    }

    /** True if the side to move has a *legal* en passant capture available. */
    val hasLegalEnPassant: Boolean
        get() = enPassantSquare >= 0 && legalMoves.any { describe(it)?.isEnPassant == true }

    /**
     * Identity of the position for repetition counting, following the FIDE definition: same pieces on the same
     * squares, same side to move, and the same possible moves (so castling rights count, and an en passant square only
     * counts if a legal en passant capture actually exists).
     */
    val repetitionKey: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildString {
            append(placementFen())
            append(' ').append(if (sideToMove == Color.WHITE) 'w' else 'b')
            append(' ').append(castlingFen())
            append(' ').append(if (hasLegalEnPassant) Squares.name(enPassantSquare) else "-")
        }
    }

    /**
     * True when neither side can ever deliver checkmate by any legal sequence of moves, for the cases that can be
     * detected from material alone: bare kings, king + one minor piece against a bare king, and kings with only
     * bishops that all stand on squares of the same colour (on either side). Blocked-pawn "fortress" dead positions
     * are NOT detected; see docs/KNOWN_LIMITATIONS.md.
     */
    val hasInsufficientMaterial: Boolean
        get() {
            var knights = 0
            var lightBishops = 0
            var darkBishops = 0
            for (sq in 0 until 64) {
                val p = pieceAt(sq) ?: continue
                when (p.type) {
                    PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return false
                    PieceType.KNIGHT -> knights++
                    PieceType.BISHOP -> if (Squares.isDark(sq)) darkBishops++ else lightBishops++
                    PieceType.KING -> Unit
                }
            }
            val minors = knights + lightBishops + darkBishops
            return minors <= 1 || (knights == 0 && (lightBishops == 0 || darkBishops == 0))
        }

    private fun placementFen(): String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = pieceAt(Squares.of(file, rank))
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) append(empty)
                    empty = 0
                    append(p.fenChar)
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
    }

    private fun castlingFen(): String {
        val s = buildString {
            if (hasCastlingRight(WHITE_KINGSIDE)) append('K')
            if (hasCastlingRight(WHITE_QUEENSIDE)) append('Q')
            if (hasCastlingRight(BLACK_KINGSIDE)) append('k')
            if (hasCastlingRight(BLACK_QUEENSIDE)) append('q')
        }
        return s.ifEmpty { "-" }
    }

    /** Full six-field FEN. */
    val fen: String
        get() = "${placementFen()} ${if (sideToMove == Color.WHITE) 'w' else 'b'} ${castlingFen()} " +
            "${if (enPassantSquare >= 0) Squares.name(enPassantSquare) else "-"} $halfmoveClock $fullmoveNumber"

    override fun toString(): String = fen

    override fun equals(other: Any?): Boolean =
        other is Position && fen == other.fen

    override fun hashCode(): Int = fen.hashCode()

    companion object {
        const val WHITE_KINGSIDE = 1
        const val WHITE_QUEENSIDE = 2
        const val BLACK_KINGSIDE = 4
        const val BLACK_QUEENSIDE = 8

        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun initial(): Position = fromFen(START_FEN)

        /** Parses a FEN string; throws [IllegalArgumentException] if it is malformed or describes an impossible position. */
        fun fromFen(fen: String): Position {
            val fields = fen.trim().split(Regex("\\s+"))
            require(fields.size >= 4) { "FEN needs at least 4 fields: '$fen'" }
            val board = IntArray(64)
            val ranks = fields[0].split('/')
            require(ranks.size == 8) { "FEN placement needs 8 ranks: '${fields[0]}'" }
            for ((i, text) in ranks.withIndex()) {
                val rank = 7 - i
                var file = 0
                for (ch in text) {
                    if (ch in '1'..'8') {
                        file += ch - '0'
                    } else {
                        val piece = requireNotNull(Piece.fromFenChar(ch)) { "Bad piece '$ch' in FEN" }
                        require(file < 8) { "Rank '$text' is too long" }
                        require(!(piece.type == PieceType.PAWN && (rank == 0 || rank == 7))) { "Pawn on the first or last rank" }
                        board[Squares.of(file, rank)] = piece.code
                        file++
                    }
                }
                require(file == 8) { "Rank '$text' does not have 8 files" }
            }
            val side = when (fields[1]) {
                "w" -> Color.WHITE
                "b" -> Color.BLACK
                else -> throw IllegalArgumentException("Bad side to move '${fields[1]}'")
            }
            var rights = 0
            if (fields[2] != "-") {
                for (ch in fields[2]) {
                    rights = rights or when (ch) {
                        'K' -> WHITE_KINGSIDE
                        'Q' -> WHITE_QUEENSIDE
                        'k' -> BLACK_KINGSIDE
                        'q' -> BLACK_QUEENSIDE
                        else -> throw IllegalArgumentException("Bad castling flag '$ch'")
                    }
                }
            }
            // Drop castling rights that cannot exist because the king or rook is not on its home square.
            fun has(square: Int, piece: Piece) = board[square] == piece.code
            val wk = Piece.of(Color.WHITE, PieceType.KING)
            val bk = Piece.of(Color.BLACK, PieceType.KING)
            val wr = Piece.of(Color.WHITE, PieceType.ROOK)
            val br = Piece.of(Color.BLACK, PieceType.ROOK)
            if (!has(Squares.E1, wk) || !has(Squares.H1, wr)) rights = rights and WHITE_KINGSIDE.inv()
            if (!has(Squares.E1, wk) || !has(Squares.A1, wr)) rights = rights and WHITE_QUEENSIDE.inv()
            if (!has(Squares.E8, bk) || !has(Squares.H8, br)) rights = rights and BLACK_KINGSIDE.inv()
            if (!has(Squares.E8, bk) || !has(Squares.A8, br)) rights = rights and BLACK_QUEENSIDE.inv()

            var ep = -1
            if (fields[3] != "-") {
                ep = requireNotNull(Squares.parse(fields[3])) { "Bad en passant square '${fields[3]}'" }
                val expectedRank = if (side == Color.WHITE) 5 else 2
                require(Squares.rank(ep) == expectedRank) { "En passant square on the wrong rank" }
                val pawnSquare = ep + if (side == Color.WHITE) -8 else 8
                require(board[pawnSquare] == Piece.of(side.opposite, PieceType.PAWN).code && board[ep] == 0) {
                    "En passant square without a pawn that could have just double-pushed"
                }
            }
            val half = fields.getOrNull(4)?.toIntOrNull() ?: if (fields.size > 4) throw IllegalArgumentException("Bad halfmove clock") else 0
            val full = fields.getOrNull(5)?.toIntOrNull() ?: if (fields.size > 5) throw IllegalArgumentException("Bad fullmove number") else 1
            require(half >= 0 && full >= 1) { "Move counters out of range" }

            val kings = IntArray(2)
            for (code in board) {
                if (code == wk.code) kings[0]++
                if (code == bk.code) kings[1]++
            }
            require(kings[0] == 1 && kings[1] == 1) { "Each side needs exactly one king" }
            val position = Position(board, side, rights, ep, half, full)
            // The side that just moved may not have left its own king capturable.
            require(!position.isAttacked(position.kingSquare(side.opposite), side)) { "The side not to move is in check" }
            return position
        }
    }
}

private object Tables {
    val KNIGHT: Array<IntArray> = Array(64) { sq -> jumps(sq, intArrayOf(1, 2, 2, 1, -1, -2, -2, -1), intArrayOf(2, 1, -1, -2, -2, -1, 1, 2)) }
    val KING: Array<IntArray> = Array(64) { sq -> jumps(sq, intArrayOf(1, 1, 1, 0, 0, -1, -1, -1), intArrayOf(1, 0, -1, 1, -1, 1, 0, -1)) }
    val ORTHOGONAL: Array<IntArray> = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
    val DIAGONAL: Array<IntArray> = arrayOf(intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1))

    private fun jumps(square: Int, dfs: IntArray, drs: IntArray): IntArray {
        val out = ArrayList<Int>()
        for (i in dfs.indices) {
            val f = Squares.file(square) + dfs[i]
            val r = Squares.rank(square) + drs[i]
            if (f in 0..7 && r in 0..7) out.add(Squares.of(f, r))
        }
        return out.toIntArray()
    }
}
