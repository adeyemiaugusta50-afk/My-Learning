import { describe, expect, it } from 'vitest'
import { ChessGame, validateFen } from '../src/logic/game'
import { from, fresh, legal, legalFrom, mv, play } from './helpers'

describe('pins and king safety', () => {
  it('a pinned knight cannot move', () => {
    expect(legalFrom(from('4k3/4r3/8/8/8/8/4N3/4K3 w - - 0 1'), 'e2').size).toBe(0)
  })
  it('a pinned rook may only move along the pin', () => {
    expect(legalFrom(from('4k3/4r3/8/8/8/8/4R3/4K3 w - - 0 1'), 'e2')).toEqual(new Set(['e2e3', 'e2e4', 'e2e5', 'e2e6', 'e2e7']))
  })
  it('a pinned bishop cannot leave the diagonal', () => {
    expect(legalFrom(from('4k3/p7/8/b7/8/8/3B4/4K3 w - - 0 1'), 'd2')).toEqual(new Set(['d2c3', 'd2b4', 'd2a5']))
  })
  it('the king cannot step into check, and illegal moves are rejected', () => {
    const g = from('4k3/8/8/8/8/8/5r2/4K3 w - - 0 1')
    expect(legal(g).has('e1f1')).toBe(false)
    expect(legal(g).has('e1e2')).toBe(false)
    expect(() => g.play(mv('e1e2'))).toThrow()
  })
  it('in check only evasions are legal', () => {
    const g = from('4r1k1/8/8/8/8/8/3N4/4K3 w - - 0 1')
    expect(g.isCheck).toBe(true)
    expect(legal(g)).toEqual(new Set(['e1d1', 'e1f1', 'e1f2', 'd2e4']))
  })
  it('rejects own-piece captures and moving out of turn', () => {
    const g = fresh()
    expect(legal(g).has('a1a2')).toBe(false)
    expect(() => g.play(mv('e2e5'))).toThrow()
    expect(() => g.play(mv('e7e5'))).toThrow()
  })
})

describe('castling', () => {
  it('both castles are available when clear', () => {
    const m = legal(from('r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1'))
    expect(m.has('e1g1') && m.has('e1c1')).toBe(true)
  })
  it('cannot castle through an attacked square', () => {
    const m = legal(from('4k3/5r2/8/8/8/8/8/R3K2R w KQ - 0 1'))
    expect(m.has('e1g1')).toBe(false)
    expect(m.has('e1c1')).toBe(true)
  })
  it('cannot castle out of check', () => {
    const m = legal(from('4k3/4r3/8/8/8/8/8/R3K2R w KQ - 0 1'))
    expect(m.has('e1g1') || m.has('e1c1')).toBe(false)
  })
  it('cannot castle into check', () => {
    const m = legal(from('4k3/6r1/8/8/8/8/8/R3K2R w KQ - 0 1'))
    expect(m.has('e1g1')).toBe(false)
    expect(m.has('e1c1')).toBe(true)
  })
  it('queenside castling is allowed when only the rook path (b1) is attacked', () => {
    expect(legal(from('1r2k3/8/8/8/8/8/8/R3K2R w KQ - 0 1')).has('e1c1')).toBe(true)
  })
  it('cannot castle with a piece in the way', () => {
    const m = legal(from('4k3/8/8/8/8/8/8/RN2K2R w KQ - 0 1'))
    expect(m.has('e1c1')).toBe(false)
    expect(m.has('e1g1')).toBe(true)
  })
  it('castling moves the rook and removes rights', () => {
    const g = play(from('r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1'), 'e1g1')
    expect(g.fen.split(' ')[0]).toBe('r3k2r/8/8/8/8/8/8/R4RK1')
    expect(g.fen.split(' ')[2]).toBe('kq')
    expect(g.records[0].san).toBe('O-O')
    expect(play(from('r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1'), 'e8c8').records[0].san).toBe('O-O-O')
  })
  it('moving a rook loses only that side, and moving back does not restore it', () => {
    const g = play(from('r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1'), 'h1g1')
    expect(g.fen.split(' ')[2]).toBe('Qkq')
    const back = play(g, 'h8g8', 'g1h1')
    expect(legal(back).has('e1g1')).toBe(false)
  })
  it('capturing a rook on its home square removes the right', () => {
    const g = play(from('4k3/8/8/8/8/8/6b1/R3K2R b KQ - 0 1'), 'g2h1')
    expect(g.fen.split(' ')[2]).toBe('Q')
  })
  it('a king move loses both rights', () => {
    expect(play(from('r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1'), 'e1e2').fen.split(' ')[2]).toBe('kq')
  })
})

describe('en passant', () => {
  it('is available only immediately', () => {
    const g = play(fresh(), 'e2e4', 'a7a6', 'e4e5', 'd7d5')
    expect(g.fen.split(' ')[3]).toBe('d6')
    expect(legal(g).has('e5d6')).toBe(true)
    const later = play(g, 'g1f3', 'a6a5')
    expect(later.fen.split(' ')[3]).toBe('-')
    expect(legal(later).has('e5d6')).toBe(false)
  })
  it('removes the captured pawn', () => {
    const g = play(fresh(), 'e2e4', 'a7a6', 'e4e5', 'd7d5', 'e5d6')
    expect(g.pieceAt('d6')).toEqual({ type: 'p', color: 'w' })
    expect(g.pieceAt('d5')).toBeNull()
    expect(g.records.at(-1)?.isEnPassant).toBe(true)
    expect(g.records.at(-1)?.san).toBe('exd6')
  })
  it('exposing the king makes it illegal', () => {
    const g = from('8/8/8/KPp4r/8/8/8/4k3 w - c6 0 1')
    expect(legal(g).has('b5c6')).toBe(false)
    expect(legal(g).has('b5b6')).toBe(true)
    expect(() => g.play(mv('b5c6'))).toThrow()
  })
  it('may resolve a check by removing the checking pawn', () => {
    const g = from('4k3/8/8/2Pp4/4K3/8/8/8 w - d6 0 1')
    expect(g.isCheck).toBe(true)
    expect(legal(g).has('c5d6')).toBe(true)
  })
})

describe('promotion', () => {
  it('offers all four pieces', () => {
    expect(legalFrom(from('8/P6k/8/8/8/8/8/K7 w - - 0 1'), 'a7')).toEqual(new Set(['a7a8q', 'a7a8r', 'a7a8b', 'a7a8n']))
  })
  it('requires choosing a piece', () => {
    expect(() => from('8/P6k/8/8/8/8/8/K7 w - - 0 1').play(mv('a7a8'))).toThrow()
  })
  it('every underpromotion produces the chosen piece', () => {
    for (const p of ['q', 'r', 'b', 'n']) {
      const g = play(from('8/P6k/8/8/8/8/8/K7 w - - 0 1'), `a7a8${p}`)
      expect(g.pieceAt('a8')).toEqual({ type: p, color: 'w' })
    }
  })
  it('a knight underpromotion can give check where the queen cannot', () => {
    const g = from('8/2k1P3/8/8/8/8/8/K7 w - - 0 1')
    const san = (lan: string) => g.legalMoves.find((m) => m.lan === lan)?.san
    expect(san('e7e8n')).toBe('e8=N+')
    expect(san('e7e8q')).toBe('e8=Q')
  })
  it('promotion by capture, and Black promotion', () => {
    expect(legalFrom(from('3r4/4P3/8/8/8/8/p7/K6k w - - 0 1'), 'e7')).toEqual(
      new Set(['e7e8q', 'e7e8r', 'e7e8b', 'e7e8n', 'e7d8q', 'e7d8r', 'e7d8b', 'e7d8n']))
    expect(legalFrom(from('7k/8/8/8/8/8/p7/2K5 b - - 0 1'), 'a2')).toEqual(new Set(['a2a1q', 'a2a1r', 'a2a1b', 'a2a1n']))
  })
})

describe('check, mate, stalemate, notation', () => {
  it("fool's mate", () => {
    const g = play(fresh(), 'f2f3', 'e7e5', 'g2g4', 'd8h4')
    expect(g.result).toEqual({ winner: 'b', termination: 'checkmate' })
    expect(g.legalMoves.length).toBe(0)
    expect(g.records.at(-1)?.san).toBe('Qh4#')
    expect(() => g.play(mv('e2e3'))).toThrow()
  })
  it("scholar's mate", () => {
    const g = play(fresh(), 'e2e4', 'e7e5', 'd1h5', 'b8c6', 'f1c4', 'g8f6', 'h5f7')
    expect(g.result).toEqual({ winner: 'w', termination: 'checkmate' })
    expect(g.records.at(-1)?.san).toBe('Qxf7#')
  })
  it('stalemate is a draw, not a loss', () => {
    const g = from('7k/5Q2/6K1/8/8/8/8/8 b - - 0 1')
    expect(g.isCheck).toBe(false)
    expect(g.result).toEqual({ winner: null, termination: 'stalemate' })
  })
  it('check does not end the game', () => {
    const g = play(fresh(), 'e2e4', 'f7f5', 'd1h5')
    expect(g.isCheck).toBe(true)
    expect(g.result).toBeNull()
    expect(g.records.at(-1)).toMatchObject({ san: 'Qh5+', givesCheck: true })
  })
  it('SAN disambiguation by file, rank, castling and captures', () => {
    const f = from('4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1')
    expect(f.legalMoves.find((m) => m.lan === 'b1d2')?.san).toBe('Nbd2')
    expect(f.legalMoves.find((m) => m.lan === 'f1d2')?.san).toBe('Nfd2')
    const r = from('4k3/8/8/N7/8/8/8/N3K3 w - - 0 1')
    expect(r.legalMoves.find((m) => m.lan === 'a5b3')?.san).toBe('N5b3')
    expect(r.legalMoves.find((m) => m.lan === 'a1b3')?.san).toBe('N1b3')
    expect(play(from('r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1'), 'e1g1', 'e8c8').records.map((x) => x.san)).toEqual(['O-O', 'O-O-O'])
    expect(play(fresh(), 'e2e4', 'd7d5', 'e4d5', 'd8d5', 'b1c3', 'd5e5').records.map((x) => x.san))
      .toEqual(['e4', 'd5', 'exd5', 'Qxd5', 'Nc3', 'Qe5+'])
  })
  it('records report what a move did', () => {
    const g = play(fresh(), 'e2e4', 'd7d5', 'e4d5')
    expect(g.records.at(-1)).toMatchObject({ captured: 'p', isCapture: true })
    expect(g.records[0].isCapture).toBe(false)
  })
})

describe('start-position validation', () => {
  it('rejects malformed positions, including the side not to move being in check', () => {
    for (const fen of ['', 'not a fen', '8/8/8/8/8/8/8/8 w - - 0 1', '4k3/8/8/8/8/8/8/4K3 x - - 0 1',
      '4k3/4R3/8/8/8/8/8/4K3 w - - 0 1', '4k3/8/8/8/8/8/8/3KK3 w - - 0 1']) {
      expect(() => validateFen(fen), fen).toThrow()
    }
    expect(() => ChessGame.new('4k3/8/8/8/8/8/8/4K3 w - - 0 1')).not.toThrow()
  })
})
