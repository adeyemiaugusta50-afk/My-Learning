package com.casualchess.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FenTest {
    @Test fun startPositionRoundTrips() {
        assertEquals(Position.START_FEN, Position.initial().fen)
    }

    @Test fun complexFenRoundTrips() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R b Kq - 12 34"
        assertEquals(fen, pos(fen).fen)
    }

    @Test fun enPassantSquareRoundTrips() {
        val fen = "rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 3"
        assertEquals(fen, pos(fen).fen)
    }

    @Test fun missingCountersDefault() {
        val p = pos("4k3/8/8/8/8/8/8/4K3 w - -")
        assertEquals(0, p.halfmoveClock)
        assertEquals(1, p.fullmoveNumber)
    }

    @Test fun impossibleCastlingRightsAreDropped() {
        // Rights claim a white rook on h1, but there is none.
        val p = pos("4k3/8/8/8/8/8/8/4K3 w KQkq - 0 1")
        assertEquals(0, p.castlingRights)
    }

    @Test fun rejectsMalformedFen() {
        listOf(
            "",
            "not a fen",
            "8/8/8/8/8/8/8/8 w - - 0 1",                              // no kings
            "4k3/8/8/8/8/8/8/4K3 x - - 0 1",                          // bad side
            "4k3/8/8/8/8/8/8/4K2 w - - 0 1",                          // short rank
            "4k3/8/8/8/8/8/8/4K4 w - - 0 1",                          // long rank
            "4k3/8/8/8/8/8/8/4K3/8 w - - 0 1",                        // nine ranks
            "P3k3/8/8/8/8/8/8/4K3 w - - 0 1",                         // pawn on last rank
            "4k3/8/8/8/8/8/8/4K3 w - e3 0 1",                         // ep without pawn / wrong rank
            "4k3/8/8/8/8/8/8/4K3 w - - -1 1",                         // negative clock
            "4k3/8/8/8/8/8/8/4K3 w - - 0 0",                          // fullmove 0
            "4k3/4R3/8/8/8/8/8/4K3 w - - 0 1",                        // side not to move (black) is in check
            "4k3/8/8/8/8/8/8/3KK3 w - - 0 1",                         // two white kings
        ).forEach { fen ->
            assertFailsWith<IllegalArgumentException>("should reject '$fen'") { Position.fromFen(fen) }
        }
    }

    @Test fun uciParsing() {
        assertEquals(Move(sq("e2"), sq("e4")), Move.fromUci("e2e4"))
        assertEquals(Move(sq("e7"), sq("e8"), PieceType.KNIGHT), Move.fromUci("e7e8n"))
        assertNull(Move.fromUci("e7e8k"))
        assertNull(Move.fromUci("e7e9"))
        assertNull(Move.fromUci("e2"))
        assertEquals("a7a8q", Move(sq("a7"), sq("a8"), PieceType.QUEEN).uci)
    }
}
