import { type GameResult, type DrawClaim, type PieceType, type Color } from './game'
import { checkedKingSquare, nameOf, targets, type SessionState } from './session'


export const colorName = (c: Color): string => (c === 'w' ? 'White' : 'Black')

const PIECE_NAMES: Record<PieceType, string> = { p: 'pawn', n: 'knight', b: 'bishop', r: 'rook', q: 'queen', k: 'king' }
export const pieceName = (type: PieceType, color: Color): string => `${colorName(color).toLowerCase()} ${PIECE_NAMES[type]}`
export const promotionName = (type: PieceType): string => PIECE_NAMES[type]

/** One-line result such as "Checkmate: Ana (White) wins" or "Draw by stalemate". Same wording as the Android app. */
export function resultText(result: GameResult, whiteName: string, blackName: string): string {
  const player = (c: Color) => `${c === 'w' ? whiteName : blackName} (${colorName(c)})`
  const w = result.winner
  switch (result.termination) {
    case 'checkmate': return `Checkmate: ${player(w!)} wins`
    case 'resignation': return `${player(w === 'w' ? 'b' : 'w')} resigned: ${player(w!)} wins`
    case 'stalemate': return 'Draw by stalemate'
    case 'insufficient_material': return 'Draw: dead position (neither side can checkmate)'
    case 'fivefold_repetition': return 'Draw by fivefold repetition'
    case 'seventy_five_move_rule': return 'Draw by the 75-move rule'
    case 'threefold_repetition_claimed': return 'Draw by threefold repetition (claimed)'
    case 'fifty_move_rule_claimed': return 'Draw by the 50-move rule (claimed)'
    case 'draw_agreed': return 'Draw by agreement'
  }
}

/** Whose turn it is, or the result once the game is over. */
export function statusText(s: SessionState): string {
  if (s.game.result) return resultText(s.game.result, s.whiteName, s.blackName)
  const side = s.game.sideToMove
  const who = `${nameOf(s, side)} (${colorName(side)}) to move`
  return s.game.isCheck ? `${who}: check!` : who
}

export const claimLabel = (claim: DrawClaim): string =>
  claim === 'threefold_repetition' ? 'Claim draw: threefold repetition' : 'Claim draw: 50-move rule'

/** Move list as numbered rows: "1. e4 e5", "2. Nf3 Nc6", ... (a game starting with Black to move begins "1... e5"). */
export function historyRows(game: SessionState['game']): string[] {
  const rows: string[] = []
  const records = game.records
  const initial = game.initialFen.split(' ')
  let number = Number(initial[5])
  let i = 0
  if (initial[1] === 'b' && records.length > 0) {
    rows.push(`${number}... ${records[0].san}`)
    i = 1
    number++
  }
  while (i < records.length) {
    const black = records[i + 1]?.san
    rows.push(`${number}. ${records[i].san}${black ? ` ${black}` : ''}`)
    i += 2
    number++
  }
  return rows
}

/** Accessibility text for a board square. */
export function squareDescription(s: SessionState, square: string, targetSet: Set<string> = targets(s)): string {
  const piece = s.game.pieceAt(square)
  const parts: string[] = [square, piece ? pieceName(piece.type, piece.color) : 'empty']
  if (square === s.selected) parts.push('selected')
  if (targetSet.has(square)) parts.push(piece ? 'capture available' : 'legal move')
  const last = s.game.lastMove
  if (last && (square === last.from || square === last.to)) parts.push('last move')
  if (square === checkedKingSquare(s)) {
    parts.push(s.game.result?.termination === 'checkmate' ? 'checkmated king' : 'king in check')
  }
  return parts.join(', ')
}
