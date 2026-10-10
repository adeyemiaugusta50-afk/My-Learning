package com.casualchess.core

/** Standard Algebraic Notation (English piece letters) for move history display. */
object San {
    fun pieceLetter(type: PieceType): Char = when (type) {
        PieceType.KNIGHT -> 'N'
        PieceType.BISHOP -> 'B'
        PieceType.ROOK -> 'R'
        PieceType.QUEEN -> 'Q'
        PieceType.KING -> 'K'
        PieceType.PAWN -> 'P'
    }

    /** SAN of a legal [move] in [position], including the check (+) or checkmate (#) suffix. */
    fun of(position: Position, move: Move): String {
        val info = requireNotNull(position.describe(move)) { "No piece on ${Squares.name(move.from)}" }
        val sb = StringBuilder()
        val dest = Squares.name(move.to)
        when {
            info.castle == CastleSide.KINGSIDE -> sb.append("O-O")
            info.castle == CastleSide.QUEENSIDE -> sb.append("O-O-O")
            info.piece.type == PieceType.PAWN -> {
                if (info.captured != null) sb.append('a' + Squares.file(move.from)).append('x')
                sb.append(dest)
                move.promotion?.let { sb.append('=').append(pieceLetter(it)) }
            }
            else -> {
                sb.append(pieceLetter(info.piece.type))
                val rivals = position.legalMoves.filter {
                    it != move && it.to == move.to && position.pieceAt(it.from)?.type == info.piece.type
                }
                if (rivals.isNotEmpty()) {
                    val sameFile = rivals.any { Squares.file(it.from) == Squares.file(move.from) }
                    val sameRank = rivals.any { Squares.rank(it.from) == Squares.rank(move.from) }
                    when {
                        !sameFile -> sb.append('a' + Squares.file(move.from))
                        !sameRank -> sb.append('1' + Squares.rank(move.from))
                        else -> sb.append(Squares.name(move.from))
                    }
                }
                if (info.captured != null) sb.append('x')
                sb.append(dest)
            }
        }
        val next = position.play(move)
        if (next.isCheck) sb.append(if (next.legalMoves.isEmpty()) '#' else '+')
        return sb.toString()
    }
}
