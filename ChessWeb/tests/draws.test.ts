import { Chess } from 'chess.js'
import { describe, expect, it } from 'vitest'
import { ChessGame, isAutomatic, repetitionKey, TERMINATIONS } from '../src/logic/game'
import { from, fresh, legal, play, SHUFFLE } from './helpers'

describe('repetition', () => {
  it('threefold repetition is claimable but does not end the game', () => {
    let g = fresh()
    expect(g.repetitionCount).toBe(1)
    g = play(g, ...SHUFFLE)
    expect(g.repetitionCount).toBe(2)
    expect(g.claimableDraws).toEqual([])
    g = play(g, ...SHUFFLE)
    expect(g.repetitionCount).toBe(3)
    expect(g.result).toBeNull()
    expect(g.claimableDraws).toEqual(['threefold_repetition'])
    g = play(g, 'e2e4')
    expect(g.claimableDraws).toEqual([])
  })
  it('claiming threefold repetition ends the game as a draw', () => {
    const claimed = play(fresh(), ...SHUFFLE, ...SHUFFLE).claimDraw('threefold_repetition')
    expect(claimed.result).toEqual({ winner: null, termination: 'threefold_repetition_claimed' })
    expect(isAutomatic(claimed.result!)).toBe(false)
    expect(claimed.legalMoves.length).toBe(0)
  })
  it('fivefold repetition ends the game automatically', () => {
    let g = fresh()
    for (let i = 0; i < 3; i++) g = play(g, ...SHUFFLE)
    expect(g.repetitionCount).toBe(4)
    expect(g.result).toBeNull()
    g = play(g, ...SHUFFLE)
    expect(g.repetitionCount).toBe(5)
    expect(g.result).toEqual({ winner: null, termination: 'fivefold_repetition' })
    expect(g.claimableDraws).toEqual([])
    expect(() => play(g, 'e2e4')).toThrow()
  })
  it('claiming an unavailable draw is rejected', () => {
    const g = play(fresh(), 'e2e4')
    expect(() => g.claimDraw('threefold_repetition')).toThrow()
    expect(() => g.claimDraw('fifty_move_rule')).toThrow()
  })
  it('lost castling rights make the position different', () => {
    const g = play(from('r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1'), 'h1g1', 'h8g8', 'g1h1', 'g8h8')
    expect(g.repetitionCount).toBe(1)
    expect(play(g, 'h1g1', 'h8g8', 'g1h1', 'g8h8').repetitionCount).toBe(2)
  })
  it('an en passant square only counts when a capture is really possible', () => {
    const k = (fen: string) => repetitionKey(fen)
    expect(k('rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1')).toBe(k('rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1'))
    expect(k('rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 3'))
      .not.toBe(k('rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 3'))
    // A pinned pawn's en passant capture is not legal, so it does not distinguish positions either.
    expect(k('8/8/8/KPp4r/8/8/8/4k3 w - c6 0 1')).toBe(k('8/8/8/KPp4r/8/8/8/4k3 w - - 0 1'))
  })
})

describe('50 / 75 move rules', () => {
  it('the 50-move rule is claimable at 100 half-moves, not automatic', () => {
    const start = from('4k3/8/8/8/8/8/8/R3K3 w - - 98 80')
    const ninetyNine = play(start, 'a1a2')
    expect(ninetyNine.halfmoveClock).toBe(99)
    expect(ninetyNine.claimableDraws).toEqual([])
    const hundred = play(ninetyNine, 'e8d8')
    expect(hundred.halfmoveClock).toBe(100)
    expect(hundred.result).toBeNull()
    expect(hundred.claimableDraws).toEqual(['fifty_move_rule'])
    expect(hundred.claimDraw('fifty_move_rule').result).toEqual({ winner: null, termination: 'fifty_move_rule_claimed' })
  })
  it('a pawn move or capture resets the counter', () => {
    const pawn = play(from('4k3/8/8/8/8/8/4P3/R3K3 w - - 99 80'), 'e2e3')
    expect(pawn.halfmoveClock).toBe(0)
    expect(pawn.claimableDraws).toEqual([])
    expect(play(from('r3k3/8/8/8/8/8/8/R3K3 w - - 99 80'), 'a1a8').halfmoveClock).toBe(0)
  })
  it('the 75-move rule ends the game automatically', () => {
    const one = play(from('4k3/8/8/8/8/8/8/R3K3 w - - 148 100'), 'a1a2')
    expect(one.result).toBeNull()
    expect(one.claimableDraws).toEqual(['fifty_move_rule'])
    const end = play(one, 'e8d8')
    expect(end.halfmoveClock).toBe(150)
    expect(end.result).toEqual({ winner: null, termination: 'seventy_five_move_rule' })
    expect(isAutomatic(end.result!)).toBe(true)
  })
  it('checkmate on the 75th move is a win, not a draw', () => {
    const g = play(from('7k/8/5K2/8/8/8/8/6Q1 w - - 149 100'), 'g1g7')
    expect(g.halfmoveClock).toBe(150)
    expect(g.result).toEqual({ winner: 'w', termination: 'checkmate' })
  })
})

describe('dead positions', () => {
  it('detects dead positions from material', () => {
    for (const fen of ['4k3/8/8/8/8/8/8/4K3 w - - 0 1', '4k3/8/8/8/8/8/8/3BK3 w - - 0 1', '4k3/8/8/8/8/8/8/3NK3 w - - 0 1',
      '4kb2/8/8/8/8/8/8/2B1K3 w - - 0 1', '4k3/8/8/8/8/8/8/1B2KB2 b - - 0 1']) {
      expect(from(fen).result, fen).toEqual({ winner: null, termination: 'insufficient_material' })
    }
  })
  it('material that can still mate is not dead', () => {
    for (const fen of ['2b1k3/8/8/8/8/8/8/2B1K3 w - - 0 1', '4k3/8/8/8/8/8/8/2NNK3 w - - 0 1', '4kn2/8/8/8/8/8/8/3NK3 w - - 0 1',
      '4kn2/8/8/8/8/8/8/3BK3 w - - 0 1', '4k3/8/8/8/8/8/8/R3K3 w - - 0 1', '4k3/8/8/8/8/8/4P3/4K3 w - - 0 1',
      '4k3/8/8/8/8/8/8/Q3K3 w - - 0 1', '4k3/8/8/8/8/8/8/2BBK3 w - - 0 1']) {
      expect(from(fen).result, fen).toBeNull()
    }
  })
  it('the game ends when captures leave a dead position', () => {
    const g = from('4k3/8/8/8/8/8/3r4/4K3 w - - 0 1')
    expect(g.result).toBeNull()
    expect(play(g, 'e1d2').result).toEqual({ winner: null, termination: 'insufficient_material' })
  })
  it('known limitation: a locked pawn fortress is not detected as dead', () => {
    expect(from('8/8/8/k1p5/2P5/8/8/K7 w - - 0 1').result).toBeNull()
  })

  /** Every legal placement of K+minor vs K with Black to move; true if any is checkmate (black king in 10 canonical squares). */
  function mateExists(minor: string): boolean {
    const files = 'abcdefgh'
    const name = (i: number) => `${files[i % 8]}${Math.floor(i / 8) + 1}`
    const canonical = ['a1', 'b1', 'c1', 'd1', 'b2', 'c2', 'd2', 'c3', 'd3', 'd4']
    for (const bk of canonical.map((s) => files.indexOf(s[0]) + 8 * (Number(s[1]) - 1))) {
      for (let wk = 0; wk < 64; wk++) {
        for (let m = 0; m < 64; m++) {
          if (wk === bk || m === bk || m === wk) continue
          if (Math.max(Math.abs((wk % 8) - (bk % 8)), Math.abs(Math.floor(wk / 8) - Math.floor(bk / 8))) <= 1) continue
          const board: Record<number, string> = { [bk]: 'k', [wk]: 'K', [m]: minor }
          const rows: string[] = []
          for (let r = 7; r >= 0; r--) {
            let row = '', empty = 0
            for (let f = 0; f < 8; f++) {
              const p = board[r * 8 + f]
              if (p) { row += (empty || '') + p; empty = 0 } else empty++
            }
            rows.push(row + (empty || ''))
          }
          const c = new Chess(`${rows.join('/')} b - - 0 1`, { skipValidation: true })
          if (c.isCheckmate()) return true
        }
      }
    }
    void name
    return false
  }
  it('brute force: no checkmate exists with only a knight or a bishop (so those are truly dead)', () => {
    expect(mateExists('N'), 'K+N v K').toBe(false)
    expect(mateExists('B'), 'K+B v K').toBe(false)
    expect(mateExists('R'), 'K+R v K has mates, so the search works').toBe(true)
    expect(mateExists('Q'), 'K+Q v K has mates, so the search works').toBe(true)
  })
  it('K+N+N v K has a mate position, so it is not dead', () => {
    const c = new Chess('k7/2N5/1KN5/8/8/8/8/8 b - - 0 1')
    expect(c.isCheckmate()).toBe(true)
    expect(c.isInsufficientMaterial()).toBe(false)
  })
})

describe('resignation and agreement', () => {
  it('resignation gives the opponent the win', () => {
    const g = play(fresh(), 'e2e4').resign('b')
    expect(g.result).toEqual({ winner: 'w', termination: 'resignation' })
    expect(g.legalMoves.length).toBe(0)
    expect(() => g.resign('w')).toThrow()
  })
  it('an agreed draw ends the game', () => {
    expect(play(fresh(), 'e2e4').agreeDraw().result).toEqual({ winner: null, termination: 'draw_agreed' })
  })
  it('automatic and claimed results are distinguished', () => {
    const automatic = ['checkmate', 'stalemate', 'insufficient_material', 'fivefold_repetition', 'seventy_five_move_rule']
    for (const t of TERMINATIONS) expect(isAutomatic({ winner: null, termination: t }), t).toBe(automatic.includes(t))
  })
  it('legal() on a finished game is empty', () => {
    expect(legal(play(fresh(), 'e2e4').agreeDraw()).size).toBe(0)
    expect(ChessGame.new().legalMoves.length).toBe(20)
  })
})
