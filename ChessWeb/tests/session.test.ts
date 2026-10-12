import { describe, expect, it } from 'vitest'
import { ChessGame } from '../src/logic/game'
import { cellOf, squareAt } from '../src/logic/geometry'
import {
  cancelPromotion, checkedKingSquare, choosePromotion, finishFlip, flipManually, flipPending, needsLeaveConfirmation,
  newSession, resign, agreeDraw, tap, targets, withAutoFlip, cleanName, MAX_NAME_LENGTH, type SessionState, type Step,
} from '../src/logic/session'

const fresh = (autoFlip = true) => newSession('Ana', 'Ben', autoFlip)

/** Simulates a finger on the screen cell that currently shows `name`. */
const tapOnScreen = (s: SessionState, name: string): Step => {
  const cell = cellOf(name, s.whiteAtBottom)
  return tap(s, squareAt(cell.row, cell.col, s.whiteAtBottom))
}
const move = (s: SessionState, from: string, to: string): SessionState => tapOnScreen(tapOnScreen(s, from).state, to).state

describe('selection and moving', () => {
  it('tapping your piece shows its legal targets', () => {
    const step = tap(fresh(), 'g1')
    expect(step.event.type).toBe('selected')
    expect(step.state.selected).toBe('g1')
    expect(targets(step.state)).toEqual(new Set(['f3', 'h3']))
  })
  it('tapping the selected piece again deselects it', () => {
    const s = tap(fresh(), 'g1').state
    const step = tap(s, 'g1')
    expect(step.event.type).toBe('deselected')
    expect(step.state.selected).toBeNull()
  })
  it('tapping another own piece switches the selection', () => {
    const step = tap(tap(fresh(), 'g1').state, 'e2')
    expect(step.state.selected).toBe('e2')
    expect(targets(step.state)).toEqual(new Set(['e3', 'e4']))
  })
  it('opponent piece / empty square with nothing selected does nothing', () => {
    expect(tap(fresh(), 'e7').event.type).toBe('none')
    expect(tap(fresh(), 'e4').event.type).toBe('none')
  })
  it('an illegal target clears the selection', () => {
    expect(tap(tap(fresh(), 'g1').state, 'g5').state.selected).toBeNull()
  })
  it('completing a move plays it and reports the record', () => {
    const step = tap(tap(fresh(false), 'e2').state, 'e4')
    expect(step.event).toMatchObject({ type: 'move_played', record: { san: 'e4' } })
    expect(step.state.game.sideToMove).toBe('b')
    expect(step.state.game.lastMove).toEqual({ from: 'e2', to: 'e4' })
    expect(step.state.selected).toBeNull()
  })
  it('the checked king is reported for highlighting, also when mated', () => {
    let s = fresh(false)
    for (const [a, b] of [['e2', 'e4'], ['f7', 'f5'], ['d1', 'h5']]) s = move(s, a, b)
    expect(checkedKingSquare(s)).toBe('e8')
    let m = fresh(false)
    for (const [a, b] of [['f2', 'f3'], ['e7', 'e5'], ['g2', 'g4'], ['d8', 'h4']]) m = move(m, a, b)
    expect(m.game.result?.termination).toBe('checkmate')
    expect(checkedKingSquare(m)).toBe('e1')
    expect(tap(m, 'a2').event.type).toBe('none')
  })
})

describe('promotion', () => {
  const promo = () => ({ ...newSession('Ana', 'Ben', false, '8/P6k/8/8/8/8/8/K7 w - - 0 1') })

  it('reaching the last rank asks which piece to promote to', () => {
    const step = tap(tap(promo(), 'a7').state, 'a8')
    expect(step.event.type).toBe('promotion_requested')
    expect(step.state.pendingPromotion).toEqual({ from: 'a7', to: 'a8' })
    expect(step.state.game.sideToMove).toBe('w')
    expect(tap(step.state, 'h7').state).toBe(step.state) // taps ignored while the chooser is open
  })
  it('choosing a piece completes it, including underpromotion', () => {
    for (const p of ['q', 'r', 'b', 'n'] as const) {
      const pending = tap(tap(promo(), 'a7').state, 'a8').state
      const done = choosePromotion(pending, p)
      expect(done.state.game.pieceAt('a8')).toEqual({ type: p, color: 'w' })
      expect(done.state.pendingPromotion).toBeNull()
      expect(done.event.type === 'move_played' && done.event.record.san.includes(`=${p.toUpperCase()}`)).toBe(true)
    }
  })
  it('cancelling leaves the position unchanged', () => {
    const pending = tap(tap(promo(), 'a7').state, 'a8').state
    const cancelled = cancelPromotion(pending)
    expect(cancelled.pendingPromotion).toBeNull()
    expect(cancelled.selected).toBeNull()
    expect(cancelled.game.fen).toBe(pending.game.fen)
  })
})

describe('automatic and manual board flipping', () => {
  it('a completed move schedules a rotation and keeps the move visible first', () => {
    const s = tap(tap(fresh(), 'e2').state, 'e4').state
    expect(flipPending(s)).toBe(true)
    expect(s.whiteAtBottom).toBe(true) // NOT rotated yet, so the completed move can be seen
    expect(s.game.lastMove).toEqual({ from: 'e2', to: 'e4' })
    const flipped = finishFlip(s, 1)
    expect(flipped.whiteAtBottom).toBe(false)
    expect(flipPending(flipped)).toBe(false)
    expect(flipped.game.lastMove).toEqual({ from: 'e2', to: 'e4' }) // last-move highlight survives the rotation
  })
  it('the board alternates after every move', () => {
    let s = fresh()
    let white = true
    for (const [a, b] of [['e2', 'e4'], ['e7', 'e5'], ['g1', 'f3'], ['b8', 'c6']]) {
      expect(s.whiteAtBottom).toBe(white)
      s = finishFlip(move(s, a, b))
      white = !white
    }
    expect(s.whiteAtBottom).toBe(white)
  })
  it('touch mapping stays correct after rotation', () => {
    let s = finishFlip(move(fresh(), 'e2', 'e4'))
    expect(s.whiteAtBottom).toBe(false)
    const e7 = cellOf('e7', false)
    expect(e7).toEqual({ row: 6, col: 3 })
    const selected = tap(s, squareAt(e7.row, e7.col, s.whiteAtBottom)).state
    expect(selected.selected).toBe('e7')
    const e5 = cellOf('e5', false)
    s = tap(selected, squareAt(e5.row, e5.col, s.whiteAtBottom)).state
    expect(s.game.lastMove).toEqual({ from: 'e7', to: 'e5' })
    expect(squareAt(e7.row, e7.col, true)).not.toBe('e7') // the old mapping would have hit another square
  })
  it('a tap during the short delay only rotates immediately', () => {
    const moved = tap(tap(fresh(), 'e2').state, 'e4').state
    const step = tap(moved, 'e7')
    expect(step.event.type).toBe('flipped_early')
    expect(step.state.whiteAtBottom).toBe(false)
    expect(step.state.selected).toBeNull()
  })
  it('a stale timer cannot flip the board twice', () => {
    const first = tap(tap(fresh(), 'e2').state, 'e4').state
    const flipped = finishFlip(first, 1)
    expect(finishFlip(flipped, 1)).toBe(flipped)
    const second = tap(tap(flipped, 'e7').state, 'e5').state
    expect(finishFlip(second, 1)).toBe(second)
    expect(flipPending(second)).toBe(true)
  })
  it('manual flip toggles; during a pending rotation it performs it once', () => {
    const s = flipManually(fresh(false))
    expect(s.whiteAtBottom).toBe(false)
    expect(flipManually(s).whiteAtBottom).toBe(true)
    const moved = tap(tap(fresh(), 'e2').state, 'e4').state
    const flipped = flipManually(moved)
    expect(flipped.whiteAtBottom).toBe(false)
    expect(flipPending(flipped)).toBe(false)
    expect(finishFlip(flipped).whiteAtBottom).toBe(false)
  })
  it('auto-flip off never rotates; turning it off cancels a pending rotation', () => {
    const off = tap(tap(fresh(false), 'e2').state, 'e4').state
    expect(flipPending(off)).toBe(false)
    expect(off.whiteAtBottom).toBe(true)
    const moved = tap(tap(fresh(), 'e2').state, 'e4').state
    const cancelled = withAutoFlip(moved, false)
    expect(flipPending(cancelled)).toBe(false)
    expect(flipPending(withAutoFlip(cancelled, true))).toBe(false)
  })
  it('no rotation when the game ends', () => {
    let s = fresh()
    for (const [a, b] of [['f2', 'f3'], ['e7', 'e5'], ['g2', 'g4']]) s = finishFlip(move(s, a, b))
    s = move(s, 'd8', 'h4')
    expect(s.game.isOver).toBe(true)
    expect(flipPending(s)).toBe(false)
  })
})

describe('ending the game, leaving, names', () => {
  it('resignation and agreement end the game and clear transient state', () => {
    const base = tap(tap(fresh(), 'e2').state, 'e4').state
    const resigned = resign(base, 'b')
    expect(resigned.game.result).toEqual({ winner: 'w', termination: 'resignation' })
    expect(flipPending(resigned)).toBe(false)
    expect(agreeDraw(base).game.result).toEqual({ winner: null, termination: 'draw_agreed' })
  })
  it('leaving needs confirmation only while a game is in progress', () => {
    const s = fresh()
    expect(needsLeaveConfirmation(s)).toBe(false)
    const playing = tap(tap(s, 'e2').state, 'e4').state
    expect(needsLeaveConfirmation(playing)).toBe(true)
    expect(needsLeaveConfirmation(resign(playing, 'w'))).toBe(false)
  })
  it('names are cleaned', () => {
    expect(cleanName('   ', 'White')).toBe('White')
    expect(cleanName('  A \n  B ', 'White')).toBe('A B')
    expect(cleanName('x'.repeat(100), 'White').length).toBe(MAX_NAME_LENGTH)
  })
  it('a fresh session starts from the standard position', () => {
    expect(fresh().game.legalMoves.length).toBe(20)
    expect(ChessGame.new().fen).toBe(fresh().game.fen)
  })
})
