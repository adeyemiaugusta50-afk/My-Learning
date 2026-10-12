/**
 * The ONE mapping between screen cells and chess squares, used for drawing AND for touch/click handling, in both
 * board orientations. Row 0 is the top row, column 0 the left column. `whiteAtBottom = false` is the board rotated by
 * 180 degrees (black at the bottom, files running h..a from left to right).
 */
export interface Cell {
  row: number
  col: number
}

const FILES = 'abcdefgh'

export function squareAt(row: number, col: number, whiteAtBottom: boolean): string {
  if (!Number.isInteger(row) || !Number.isInteger(col) || row < 0 || row > 7 || col < 0 || col > 7) {
    throw new RangeError(`Cell (${row}, ${col}) is off the board`)
  }
  return whiteAtBottom ? `${FILES[col]}${8 - row}` : `${FILES[7 - col]}${row + 1}`
}

export function cellOf(square: string, whiteAtBottom: boolean): Cell {
  const file = FILES.indexOf(square[0])
  const rank = Number(square[1]) - 1
  if (file < 0 || !(rank >= 0 && rank <= 7) || square.length !== 2) throw new RangeError(`Bad square ${square}`)
  return whiteAtBottom ? { row: 7 - rank, col: file } : { row: rank, col: 7 - file }
}

/** Pixel-level version of squareAt: a point inside a square board of side `boardSize`, or null if outside. */
export function squareAtPoint(x: number, y: number, boardSize: number, whiteAtBottom: boolean): string | null {
  if (!(boardSize > 0) || x < 0 || y < 0 || x >= boardSize || y >= boardSize) return null
  const cell = boardSize / 8
  const col = Math.min(7, Math.max(0, Math.floor(x / cell)))
  const row = Math.min(7, Math.max(0, Math.floor(y / cell)))
  return squareAt(row, col, whiteAtBottom)
}

/** File letters in screen order, left to right. */
export const fileLabels = (whiteAtBottom: boolean): string[] =>
  Array.from({ length: 8 }, (_, col) => squareAt(7, col, whiteAtBottom)[0])

/** Rank digits in screen order, top to bottom. */
export const rankLabels = (whiteAtBottom: boolean): string[] =>
  Array.from({ length: 8 }, (_, row) => squareAt(row, 0, whiteAtBottom)[1])

/** a1, h1, a8, h8 are dark squares: (file + rank) even. */
export const isDarkSquare = (square: string): boolean => (FILES.indexOf(square[0]) + Number(square[1]) - 1) % 2 === 0
