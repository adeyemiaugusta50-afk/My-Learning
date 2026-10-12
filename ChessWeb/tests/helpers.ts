import { ChessGame, parseUci, uci, type Move } from '../src/logic/game'

export const mv = (text: string): Move => {
  const m = parseUci(text)
  if (!m) throw new Error(`bad move ${text}`)
  return m
}
export const play = (game: ChessGame, ...moves: string[]): ChessGame => moves.reduce((g, m) => g.play(mv(m)), game)
export const fresh = (): ChessGame => ChessGame.new()
export const from = (fen: string): ChessGame => ChessGame.new(fen)
export const legal = (g: ChessGame): Set<string> => new Set(g.legalMoves.map((m) => m.lan))
export const legalFrom = (g: ChessGame, sq: string): Set<string> =>
  new Set(g.legalMoves.filter((m) => m.from === sq).map((m) => m.lan))
export const SHUFFLE = ['g1f3', 'g8f6', 'f3g1', 'f6g8']
export { uci }
