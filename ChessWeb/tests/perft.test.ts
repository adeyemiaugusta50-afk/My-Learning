import { Chess } from 'chess.js'
import { describe, expect, it } from 'vitest'
import { START_FEN } from '../src/logic/game'

/**
 * Move-generation correctness via perft: count every leaf position reachable in N plies and compare with the widely
 * published reference counts (chessprogramming.org "Perft Results"). The positions exercise castling, en passant,
 * promotions, pins and check evasions, so one wrong rule changes a count. This independently checks the chess.js
 * version we ship in THIS environment, and the same table is used for the Android app's own engine.
 */
const CASES: Array<[string, string, number[]]> = [
  ['start position', START_FEN, [20, 400, 8_902, 197_281]],
  ['kiwipete', 'r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1', [48, 2_039, 97_862]],
  ['position 3 (en passant + pins)', '8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1', [14, 191, 2_812, 43_238, 674_624]],
  ['position 4 (promotions + castling)', 'r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1', [6, 264, 9_467, 422_333]],
  ['position 4 mirrored', 'r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1', [6, 264, 9_467, 422_333]],
  ['position 5', 'rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8', [44, 1_486, 62_379]],
  ['position 6', 'r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10', [46, 2_079, 89_890]],
]

describe('perft (chess.js against published reference counts)', () => {
  for (const [name, fen, counts] of CASES) {
    it(name, () => {
      const chess = new Chess(fen)
      counts.forEach((expected, i) => expect(chess.perft(i + 1), `perft(${i + 1}) of ${fen}`).toBe(expected))
    })
  }
})
