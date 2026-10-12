import {
  ChessGame, PROMOTIONS, opposite,
  type Color, type DrawClaim, type MoveRecord, type GameResult, type Promotion, type Move, START_FEN,
} from './game'

/** How long the completed move stays visible before the board rotates in two-player mode. */
export const FLIP_DELAY_MS = 900
export const MAX_NAME_LENGTH = 24

export interface PendingPromotion {
  from: string
  to: string
}

export type SessionEvent =
  | { type: 'none' }
  | { type: 'selected' }
  | { type: 'deselected' }
  | { type: 'promotion_requested' }
  /** A tap that arrived while the board was waiting to rotate; it only completes the rotation. */
  | { type: 'flipped_early' }
  | { type: 'move_played'; record: MoveRecord; result: GameResult | null }

export interface SessionState {
  readonly game: ChessGame
  readonly whiteName: string
  readonly blackName: string
  /** The orientation currently drawn. */
  readonly whiteAtBottom: boolean
  readonly autoFlip: boolean
  readonly selected: string | null
  readonly pendingPromotion: PendingPromotion | null
  /** Ply count (moves played so far) at which a rotation is waiting, or null. */
  readonly flipPendingAtPly: number | null
}

export interface Step {
  state: SessionState
  event: SessionEvent
}

const none = (state: SessionState): Step => ({ state, event: { type: 'none' } })

export function cleanName(raw: string, fallback: string): string {
  return raw.trim().replace(/\s+/g, ' ').slice(0, MAX_NAME_LENGTH) || fallback
}

export function newSession(
  whiteName: string, blackName: string, autoFlip: boolean, startFen: string = START_FEN,
): SessionState {
  return {
    game: ChessGame.new(startFen),
    whiteName: cleanName(whiteName, 'White'),
    blackName: cleanName(blackName, 'Black'),
    whiteAtBottom: true,
    autoFlip,
    selected: null,
    pendingPromotion: null,
    flipPendingAtPly: null,
  }
}

export const flipPending = (s: SessionState): boolean => s.flipPendingAtPly !== null
export const nameOf = (s: SessionState, color: Color): string => (color === 'w' ? s.whiteName : s.blackName)

/** Destination squares for the selected piece. */
export function targets(s: SessionState): Set<string> {
  const from = s.selected
  if (from === null) return new Set()
  return new Set(s.game.legalMoves.filter((m) => m.from === from).map((m) => m.to))
}

/** The king square to highlight: the side to move is in check or has been checkmated. */
export function checkedKingSquare(s: SessionState): string | null {
  return s.game.isCheck ? s.game.kingSquare(s.game.sideToMove) : null
}

/** True while the game is running and "Restart" should ask for confirmation first. */
export const needsLeaveConfirmation = (s: SessionState): boolean => s.game.hasMoves && !s.game.isOver

/** Handles a tap on a board square. */
export function tap(s: SessionState, square: string): Step {
  if (s.game.isOver || s.pendingPromotion) return none(s)
  if (flipPending(s)) return { state: finishFlip(s), event: { type: 'flipped_early' } }

  const piece = s.game.pieceAt(square)
  const from = s.selected
  if (from !== null && targets(s).has(square)) {
    const candidates = s.game.legalMoves.filter((m) => m.from === from && m.to === square)
    // Several candidates for one from/to pair can only be the four promotions.
    if (candidates.length > 1 || candidates[0].promotion) {
      return { state: { ...s, pendingPromotion: { from, to: square } }, event: { type: 'promotion_requested' } }
    }
    return playMove(s, { from, to: square })
  }
  if (piece?.color === s.game.sideToMove && square === from) {
    return { state: { ...s, selected: null }, event: { type: 'deselected' } }
  }
  if (piece?.color === s.game.sideToMove) return { state: { ...s, selected: square }, event: { type: 'selected' } }
  if (from !== null) return { state: { ...s, selected: null }, event: { type: 'deselected' } }
  return none(s)
}

/** Completes a pending promotion with the chosen piece. */
export function choosePromotion(s: SessionState, type: Promotion): Step {
  const pending = s.pendingPromotion
  if (!pending) return none(s)
  if (!PROMOTIONS.includes(type)) throw new Error(`Cannot promote to ${type}`)
  return playMove(s, { from: pending.from, to: pending.to, promotion: type })
}

export const cancelPromotion = (s: SessionState): SessionState => ({ ...s, pendingPromotion: null, selected: null })

function playMove(s: SessionState, move: Move): Step {
  const next = s.game.play(move)
  const record = next.records[next.records.length - 1]
  const flip = s.autoFlip && !next.isOver
  const state: SessionState = {
    ...s, game: next, selected: null, pendingPromotion: null, flipPendingAtPly: flip ? next.records.length : null,
  }
  return { state, event: { type: 'move_played', record, result: next.result } }
}

/**
 * Rotates the board by 180 degrees now. When `expectedPly` is given the rotation only happens if it is still the one that
 * was scheduled, so a stale timer (after restart or another move) cannot flip the board twice.
 */
export function finishFlip(s: SessionState, expectedPly?: number): SessionState {
  const pending = s.flipPendingAtPly
  if (pending === null) return s
  if (expectedPly !== undefined && expectedPly !== pending) return s
  return { ...s, whiteAtBottom: !s.whiteAtBottom, flipPendingAtPly: null }
}

/** The manual flip button. If an automatic rotation is waiting this simply performs it, so the board never flips twice. */
export const flipManually = (s: SessionState): SessionState =>
  flipPending(s) ? finishFlip(s) : { ...s, whiteAtBottom: !s.whiteAtBottom }

/** Turns automatic flipping on or off; turning it off cancels a rotation that is waiting. */
export const withAutoFlip = (s: SessionState, enabled: boolean): SessionState =>
  ({ ...s, autoFlip: enabled, flipPendingAtPly: enabled ? s.flipPendingAtPly : null })

const ended = (s: SessionState, game: ChessGame): SessionState =>
  ({ ...s, game, selected: null, pendingPromotion: null, flipPendingAtPly: null })

export const resign = (s: SessionState, color: Color): SessionState => ended(s, s.game.resign(color))
export const agreeDraw = (s: SessionState): SessionState => ended(s, s.game.agreeDraw())
export const claimDraw = (s: SessionState, claim: DrawClaim): SessionState => ended(s, s.game.claimDraw(claim))

/** The state to persist: a waiting rotation is applied first, so after resuming the player to move sees their own side. */
export const settled = (s: SessionState): SessionState => ({ ...finishFlip(s), selected: null, pendingPromotion: null })

export { opposite }
