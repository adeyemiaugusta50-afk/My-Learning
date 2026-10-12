import { describe, expect, it } from 'vitest'
import { ChessGame, TERMINATIONS, type GameResult } from '../src/logic/game'
import { historyRows, resultText, squareDescription, statusText } from '../src/logic/describe'
import { newSession, tap } from '../src/logic/session'
import { from, fresh, play } from './helpers'

describe('English text (same wording as the Android app)', () => {
  it('result text for every termination', () => {
    const t = (winner: 'w' | 'b' | null, termination: GameResult['termination']) => resultText({ winner, termination }, 'Ana', 'Ben')
    expect(t('w', 'checkmate')).toBe('Checkmate: Ana (White) wins')
    expect(t('w', 'resignation')).toBe('Ben (Black) resigned: Ana (White) wins')
    expect(t(null, 'stalemate')).toBe('Draw by stalemate')
    expect(t(null, 'fivefold_repetition')).toBe('Draw by fivefold repetition')
    expect(t(null, 'seventy_five_move_rule')).toBe('Draw by the 75-move rule')
    expect(t(null, 'threefold_repetition_claimed')).toBe('Draw by threefold repetition (claimed)')
    expect(t(null, 'fifty_move_rule_claimed')).toBe('Draw by the 50-move rule (claimed)')
    expect(t(null, 'draw_agreed')).toBe('Draw by agreement')
    for (const term of TERMINATIONS) expect(t(term === 'checkmate' || term === 'resignation' ? 'w' : null, term).length).toBeGreaterThan(0)
  })
  it('status shows turn, check and result', () => {
    const s = newSession('Ana', 'Ben', false)
    expect(statusText(s)).toBe('Ana (White) to move')
    expect(statusText({ ...s, game: play(fresh(), 'e2e4', 'f7f5', 'd1h5') })).toBe('Ben (Black) to move: check!')
    expect(statusText({ ...s, game: play(fresh(), 'f2f3', 'e7e5', 'g2g4', 'd8h4') })).toBe('Checkmate: Ben (Black) wins')
  })
  it('history rows pair White and Black moves', () => {
    expect(historyRows(play(fresh(), 'e2e4', 'e7e5', 'g1f3'))).toEqual(['1. e4 e5', '2. Nf3'])
  })
  it('history rows handle a game that starts with Black to move', () => {
    expect(historyRows(play(from('4k3/8/8/8/8/8/4P3/4K3 b - - 0 7'), 'e8d8', 'e2e4'))).toEqual(['7... Kd8', '8. e4'])
  })
  it('square descriptions help screen readers', () => {
    const s = tap(newSession('Ana', 'Ben', false), 'g1').state
    expect(squareDescription(s, 'g1')).toBe('g1, white knight, selected')
    expect(squareDescription(s, 'f3')).toBe('f3, empty, legal move')
    expect(squareDescription(s, 'e7')).toBe('e7, black pawn')
    expect(squareDescription(tap(s, 'f3').state, 'f3')).toBe('f3, white knight, last move')
  })
  it('a finished ChessGame has no legal moves', () => {
    expect(ChessGame.new().agreeDraw().legalMoves.length).toBe(0)
  })
})
