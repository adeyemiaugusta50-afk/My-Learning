package com.casualchess.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Move-generation correctness via perft: count every leaf position reachable in N plies and compare with the widely
 * published reference counts (chessprogramming.org "Perft Results"). These positions exercise castling, en passant,
 * promotions, pins and check evasions, so one wrong rule changes a count.
 */
class PerftTest {
    private fun perft(position: Position, depth: Int): Long {
        if (depth == 0) return 1
        val moves = position.legalMoves
        if (depth == 1) return moves.size.toLong()
        var total = 0L
        for (m in moves) total += perft(position.play(m), depth - 1)
        return total
    }

    private fun check(fen: String, vararg expected: Long) {
        val position = Position.fromFen(fen)
        expected.forEachIndexed { i, count -> assertEquals(count, perft(position, i + 1), "perft(${i + 1}) of $fen") }
    }

    @Test fun startingPosition() =
        check(Position.START_FEN, 20, 400, 8_902, 197_281, 4_865_609)

    @Test fun kiwipete() =
        check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2_039, 97_862, 4_085_603)

    @Test fun position3_enPassantAndPins() =
        check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2_812, 43_238, 674_624)

    @Test fun position4_promotionsAndCastling() =
        check("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9_467, 422_333)

    @Test fun position4_mirrored() =
        check("r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1", 6, 264, 9_467, 422_333)

    @Test fun position5() =
        check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1_486, 62_379)

    @Test fun position6() =
        check("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 46, 2_079, 89_890)
}
