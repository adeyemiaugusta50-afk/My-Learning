import { PIECE_SVG } from '../pieces.generated'
import { fileLabels, isDarkSquare, rankLabels, squareAt } from '../logic/geometry'
import { squareDescription } from '../logic/describe'
import { checkedKingSquare, targets, type SessionState } from '../logic/session'
import type { BoardTheme } from '../logic/settings'
import { h } from './dom'

export const pieceSvg = (type: string, color: 'w' | 'b'): string => PIECE_SVG[color === 'w' ? type.toUpperCase() : type]

/**
 * The chess board: an 8x8 grid of focusable buttons. Both drawing and clicking use `geometry.squareAt` with the session's
 * current orientation, so after a 180 degree rotation a click always lands on the square that is shown under the pointer.
 * Pieces are never rotated, only their positions are.
 */
export function renderBoard(s: SessionState, theme: BoardTheme, onSquare: (square: string) => void): HTMLElement {
  const targetSet = targets(s)
  const last = s.game.lastMove
  const checked = checkedKingSquare(s)
  const files = fileLabels(s.whiteAtBottom)
  const ranks = rankLabels(s.whiteAtBottom)
  const board = h('div', {
    class: 'board', role: 'group', 'aria-label': 'Chess board',
    'data-orientation': s.whiteAtBottom ? 'white-bottom' : 'black-bottom', 'data-fen': s.game.fen,
    style: `--light-sq:${theme.light};--dark-sq:${theme.dark}`,
  })
  for (let row = 0; row < 8; row++) {
    for (let col = 0; col < 8; col++) {
      const square = squareAt(row, col, s.whiteAtBottom)
      const piece = s.game.pieceAt(square)
      const isTarget = targetSet.has(square)
      const classes = ['sq', isDarkSquare(square) ? 'dark' : 'light']
      if (last && (square === last.from || square === last.to)) classes.push('last')
      if (square === s.selected) classes.push('selected')
      if (square === checked) classes.push('check')
      const btn = h('button', {
        type: 'button', class: classes.join(' '), 'data-square': square, 'data-fk': `sq-${square}`,
        'aria-label': squareDescription(s, square, targetSet), 'aria-pressed': square === s.selected ? 'true' : 'false',
        onclick: () => onSquare(square),
      },
      h('span', { class: 'hl last' }), h('span', { class: 'hl sel' }), h('span', { class: 'hl chk' }),
      piece ? h('span', { class: 'piece', html: pieceSvg(piece.type, piece.color) }) : null,
      isTarget ? h('span', { class: piece ? 'target cap' : 'target' }) : null,
      col === 0 ? h('span', { class: 'coord rank' }, ranks[row]) : null,
      row === 7 ? h('span', { class: 'coord file' }, files[col]) : null)
      board.append(btn)
    }
  }
  return board
}
