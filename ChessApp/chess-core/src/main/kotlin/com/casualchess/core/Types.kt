package com.casualchess.core

enum class Color {
    WHITE, BLACK;

    val opposite: Color get() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val char: Char) {
    PAWN('p'), KNIGHT('n'), BISHOP('b'), ROOK('r'), QUEEN('q'), KING('k');

    companion object {
        fun fromChar(c: Char): PieceType? = entries.firstOrNull { it.char == c.lowercaseChar() }

        /** The four pieces a pawn may promote to, strongest first. */
        val PROMOTIONS: List<PieceType> = listOf(QUEEN, ROOK, BISHOP, KNIGHT)
    }
}

data class Piece(val color: Color, val type: PieceType) {
    /** Compact board code: 0 is reserved for "empty", white pieces are 1..6, black pieces 7..12. */
    val code: Int get() = color.ordinal * 6 + type.ordinal + 1

    /** FEN letter: upper case for white, lower case for black. */
    val fenChar: Char get() = if (color == Color.WHITE) type.char.uppercaseChar() else type.char

    companion object {
        private val ALL: Array<Piece> = Array(12) { Piece(Color.entries[it / 6], PieceType.entries[it % 6]) }

        fun of(color: Color, type: PieceType): Piece = ALL[color.ordinal * 6 + type.ordinal]

        fun fromCode(code: Int): Piece? = if (code in 1..12) ALL[code - 1] else null

        fun fromFenChar(c: Char): Piece? {
            val type = PieceType.fromChar(c) ?: return null
            return of(if (c.isUpperCase()) Color.WHITE else Color.BLACK, type)
        }
    }
}

/** Squares are numbered 0..63 with a1 = 0, b1 = 1, ... h1 = 7, a2 = 8, ... h8 = 63. */
object Squares {
    fun file(square: Int): Int = square and 7
    fun rank(square: Int): Int = square shr 3
    fun of(file: Int, rank: Int): Int = rank * 8 + file
    fun isValid(square: Int): Boolean = square in 0..63

    /** a1, h1, a8 and h8 are dark squares: (file + rank) is even. */
    fun isDark(square: Int): Boolean = (file(square) + rank(square)) % 2 == 0

    fun name(square: Int): String = "${'a' + file(square)}${'1' + rank(square)}"

    fun parse(name: String): Int? {
        if (name.length != 2) return null
        val file = name[0] - 'a'
        val rank = name[1] - '1'
        return if (file in 0..7 && rank in 0..7) of(file, rank) else null
    }

    const val A1 = 0
    const val B1 = 1
    const val C1 = 2
    const val D1 = 3
    const val E1 = 4
    const val F1 = 5
    const val G1 = 6
    const val H1 = 7
    const val A8 = 56
    const val B8 = 57
    const val C8 = 58
    const val D8 = 59
    const val E8 = 60
    const val F8 = 61
    const val G8 = 62
    const val H8 = 63
}

/** A move in coordinate form. Castling is the king's two-square move; en passant is the pawn's diagonal move. */
data class Move(val from: Int, val to: Int, val promotion: PieceType? = null) {
    /** Long algebraic / UCI text such as `e2e4` or `e7e8q`. */
    val uci: String get() = Squares.name(from) + Squares.name(to) + (promotion?.char?.toString() ?: "")

    override fun toString(): String = uci

    companion object {
        fun fromUci(text: String): Move? {
            if (text.length != 4 && text.length != 5) return null
            val from = Squares.parse(text.substring(0, 2)) ?: return null
            val to = Squares.parse(text.substring(2, 4)) ?: return null
            val promotion = if (text.length == 5) {
                PieceType.fromChar(text[4])?.takeIf { it in PieceType.PROMOTIONS } ?: return null
            } else null
            return Move(from, to, promotion)
        }
    }
}

enum class CastleSide { KINGSIDE, QUEENSIDE }

/** What a legal move does, derived from the position it is played in. */
data class MoveInfo(
    val piece: Piece,
    val captured: Piece?,
    val capturedSquare: Int?,
    val isEnPassant: Boolean,
    val castle: CastleSide?,
)
