import { describe, expect, it } from 'vitest'
import { ChessGame, type GameResult } from '../src/logic/game'
import { decodeSession, encodeSession } from '../src/logic/codec'
import { newSession, tap, flipPending, type SessionState } from '../src/logic/session'
import { from, fresh, play, SHUFFLE, legal } from './helpers'

const withGame = (game: ChessGame, extra: Partial<SessionState> = {}): SessionState => ({ ...newSession('A', 'B', true), game, ...extra })
const roundTrip = (s: SessionState): SessionState => {
  const text = encodeSession(s)
  const d = decodeSession(text)
  if (!d) throw new Error(`decode failed for ${text}`)
  return d
}
const sameRules = (a: ChessGame, b: ChessGame) => {
  expect(b.fen).toBe(a.fen)
  expect(legal(b)).toEqual(legal(a))
  expect(b.repetitionCount).toBe(a.repetitionCount)
  expect(b.claimableDraws).toEqual(a.claimableDraws)
  expect(b.result).toEqual(a.result)
  expect(b.moves).toEqual(a.moves)
  expect(b.records.map((r) => r.san)).toEqual(a.records.map((r) => r.san))
  expect(b.initialFen).toBe(a.initialFen)
}

describe('save and resume', () => {
  it('a mid-game position survives, including castling rights', () => {
    const g = play(fresh(), 'e2e4', 'e7e5', 'g1f3', 'b8c6', 'f1c4', 'g8f6', 'e1g1')
    const r = roundTrip(withGame(g)).game
    sameRules(g, r)
    expect(r.fen.split(' ')[2]).toBe('kq')
  })
  it('repetition history and draw eligibility survive', () => {
    const g = play(fresh(), ...SHUFFLE, ...SHUFFLE)
    expect(g.repetitionCount).toBe(3)
    const r = roundTrip(withGame(g)).game
    expect(r.repetitionCount).toBe(3)
    expect(r.claimableDraws).toEqual(['threefold_repetition'])
    // The restored game keeps counting from the saved history: two more cycles reach fivefold.
    expect(play(r, ...SHUFFLE, ...SHUFFLE).result).toEqual({ winner: null, termination: 'fivefold_repetition' })
  })
  it('the 50-move counter survives', () => {
    const g = play(from('4k3/8/8/8/8/8/8/R3K3 w - - 98 80'), 'a1a2', 'e8d8')
    expect(g.claimableDraws).toEqual(['fifty_move_rule'])
    const r = roundTrip(withGame(g)).game
    expect(r.halfmoveClock).toBe(100)
    expect(r.claimableDraws).toEqual(['fifty_move_rule'])
    const later = play(r, 'a2a3', 'd8e8')
    expect(later.halfmoveClock).toBe(102)
    expect(later.claimableDraws).toEqual(['fifty_move_rule'])
  })
  it('the en passant right survives a save made right after the double push', () => {
    const g = play(fresh(), 'e2e4', 'a7a6', 'e4e5', 'd7d5')
    expect(legal(g).has('e5d6')).toBe(true)
    const r = roundTrip(withGame(g)).game
    expect(legal(r).has('e5d6')).toBe(true)
    sameRules(g, r)
  })
  it('a custom start position keeps its counters and rights', () => {
    const fen = 'r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 37 61'
    const g = play(from(fen), 'e1g1')
    const r = roundTrip(withGame(g)).game
    expect(r.initialFen).toBe(fen)
    expect(r.halfmoveClock).toBe(38)
    sameRules(g, r)
  })
  it('games ended by a player stay ended', () => {
    const resigned = play(fresh(), 'e2e4', 'e7e5').resign('w')
    expect(roundTrip(withGame(resigned)).game.result).toEqual(resigned.result)
    const drawn = play(fresh(), 'e2e4').agreeDraw()
    expect(roundTrip(withGame(drawn)).game.result).toEqual(drawn.result)
    const claimed = play(fresh(), ...SHUFFLE, ...SHUFFLE).claimDraw('threefold_repetition')
    expect(roundTrip(withGame(claimed)).game.result).toEqual(claimed.result)
  })
  it('automatic results are re-derived, not stored', () => {
    const mate = play(fresh(), 'f2f3', 'e7e5', 'g2g4', 'd8h4')
    const text = encodeSession(withGame(mate))
    expect(JSON.parse(text).result).toBeNull()
    expect(decodeSession(text)?.game.result).toEqual(mate.result)
  })
  it('names, orientation and auto-flip survive', () => {
    const s = withGame(play(fresh(), 'e2e4'), { whiteName: 'Zoë & Ünal', blackName: 'Black Player', whiteAtBottom: false, autoFlip: false })
    const r = roundTrip(s)
    expect(r.whiteName).toBe('Zoë & Ünal')
    expect(r.blackName).toBe('Black Player')
    expect(r.whiteAtBottom).toBe(false)
    expect(r.autoFlip).toBe(false)
  })
  it('a pending rotation is applied before saving', () => {
    const s = tap(tap(newSession('A', 'B', true), 'e2').state, 'e4').state
    expect(flipPending(s)).toBe(true)
    const r = roundTrip(s)
    expect(r.whiteAtBottom).toBe(false)
    expect(flipPending(r)).toBe(false)
  })
  it('corrupt, truncated or forged saves are rejected', () => {
    const ok = JSON.parse(encodeSession(withGame(fresh())))
    const bad = (patch: object) => decodeSession(JSON.stringify({ ...ok, ...patch }))
    expect(decodeSession(null)).toBeNull()
    expect(decodeSession('')).toBeNull()
    expect(decodeSession('garbage')).toBeNull()
    expect(bad({ v: 2 })).toBeNull()
    expect(bad({ fen: 'nonsense' })).toBeNull()
    expect(bad({ fen: undefined })).toBeNull()
    expect(bad({ moves: ['e2e5'] })).toBeNull()
    expect(bad({ moves: ['zzzz'] })).toBeNull()
    expect(bad({ moves: [42] })).toBeNull()
    expect(bad({ moves: ['e2e4'], result: { winner: null, termination: 'threefold_repetition_claimed' } })).toBeNull()
    expect(bad({ result: { winner: 'w', termination: 'checkmate' } })).toBeNull()
    expect(bad({ result: { winner: 'x', termination: 'resignation' } })).toBeNull()
    const text = encodeSession(withGame(play(fresh(), 'e2e4', 'e7e5', 'g1f3')))
    for (let cut = 0; cut < text.length; cut++) {
      const d = decodeSession(text.slice(0, cut))
      if (d) ChessGame.fromMoves(d.game.initialFen, d.game.moves) // anything that decodes must be a legal game
    }
    const forged: GameResult = { winner: 'w', termination: 'stalemate' }
    expect(bad({ result: forged })).toBeNull()
  })
})
