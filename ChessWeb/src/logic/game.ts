import { Chess, type Move as CjMove } from 'chess.js'

export type Color = 'w' | 'b'
export type PieceType = 'p' | 'n' | 'b' | 'r' | 'q' | 'k'
export type Promotion = 'q' | 'r' | 'b' | 'n'
export const PROMOTIONS: readonly Promotion[] = ['q', 'r', 'b', 'n']
export const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'

export interface Move {
  from: string
  to: string
  promotion?: Promotion
}

export const opposite = (c: Color): Color => (c === 'w' ? 'b' : 'w')
export const uci = (m: Move): string => m.from + m.to + (m.promotion ?? '')

export function parseUci(text: string): Move | null {
  const m = /^([a-h][1-8])([a-h][1-8])([qrbn])?$/.exec(text)
  return m ? { from: m[1], to: m[2], ...(m[3] ? { promotion: m[3] as Promotion } : {}) } : null
}

export type Termination =
  // Automatic: the game ends by itself.
  | 'checkmate'
  | 'stalemate'
  | 'insufficient_material'
  | 'fivefold_repetition'
  | 'seventy_five_move_rule'
  // Not automatic: a player has to claim it / decide it.
  | 'threefold_repetition_claimed'
  | 'fifty_move_rule_claimed'
  | 'draw_agreed'
  | 'resignation'

export const AUTOMATIC: ReadonlySet<Termination> = new Set<Termination>([
  'checkmate', 'stalemate', 'insufficient_material', 'fivefold_repetition', 'seventy_five_move_rule',
])
export const TERMINATIONS: readonly Termination[] = [
  'checkmate', 'stalemate', 'insufficient_material', 'fivefold_repetition', 'seventy_five_move_rule',
  'threefold_repetition_claimed', 'fifty_move_rule_claimed', 'draw_agreed', 'resignation',
]

/** The outcome of a finished game. `winner` is null for a draw. */
export interface GameResult {
  winner: Color | null
  termination: Termination
}
export const isAutomatic = (r: GameResult): boolean => AUTOMATIC.has(r.termination)
export const scoreOf = (r: GameResult): string => (r.winner === 'w' ? '1-0' : r.winner === 'b' ? '0-1' : '1/2-1/2')

/** Draws that a player may claim (the game goes on if nobody claims). */
export type DrawClaim = 'threefold_repetition' | 'fifty_move_rule'

export interface MoveRecord {
  move: Move
  san: string
  piece: PieceType
  captured: PieceType | null
  isCapture: boolean
  isEnPassant: boolean
  castle: 'k' | 'q' | null
  givesCheck: boolean
}

/**
 * Throws if `fen` is not a usable starting position. chess.js validates the FEN syntax and piece counts; on top of that
 * we reject positions where the side NOT to move is already in check (chess.js accepts those; they cannot arise in play).
 */
export function validateFen(fen: string): void {
  new Chess(fen) // throws on malformed FEN
  const fields = fen.trim().split(/\s+/)
  fields[1] = fields[1] === 'w' ? 'b' : 'w'
  fields[3] = '-'
  let flipped: Chess
  try {
    flipped = new Chess(fields.join(' '), { skipValidation: true })
  } catch {
    return
  }
  if (flipped.isCheck()) throw new Error('The side that is not to move is in check')
}

/**
 * Identity of a position for repetition counting, following FIDE: same pieces on the same squares, same side to move and
 * the same possible moves - so castling rights count, and an en passant square counts only if a legal en passant capture
 * exists. (chess.js already omits the en passant square from `fen()` unless a capture is legal; we double-check.)
 */
export function repetitionKey(fen: string): string {
  const f = fen.split(' ')
  let ep = f[3]
  if (ep !== '-') {
    const hasEp = new Chess(fen).moves({ verbose: true }).some((m) => m.flags.includes('e'))
    if (!hasEp) ep = '-'
  }
  return `${f[0]} ${f[1]} ${f[2]} ${ep}`
}

const halfmoveClockOf = (fen: string): number => Number(fen.split(' ')[4])

/**
 * An immutable game: the starting position, every move played and the repetition history.
 *
 * Draw rules follow FIDE (Laws of Chess, Article 9 and 5.2):
 *  - stalemate, dead position (see `Chess.isInsufficientMaterial`), fivefold repetition and the 75-move rule end the game
 *    automatically; checkmate is decided first, so a mating 75th move is a win, not a draw;
 *  - threefold repetition and the 50-move rule can only be CLAIMED (`claimableDraws` / `claimDraw`).
 *
 * chess.js itself ends the game on threefold repetition / 50 moves (`isGameOver`) and has no fivefold / 75-move rule, so
 * this class deliberately does NOT use `isGameOver()` / `isDraw()` and implements that distinction on top of the library.
 */
export class ChessGame {
  readonly result: GameResult | null
  private legalCache: CjMove[] | null = null

  private constructor(
    readonly initialFen: string,
    readonly fen: string,
    readonly records: readonly MoveRecord[],
    private readonly counts: ReadonlyMap<string, number>,
    manual: GameResult | null,
  ) {
    this.result = manual ?? this.automaticResult()
  }

  get sideToMove(): Color {
    return this.fen.split(' ')[1] as Color
  }
  get fullmoveNumber(): number {
    return Number(this.fen.split(' ')[5])
  }
  get halfmoveClock(): number {
    return halfmoveClockOf(this.fen)
  }
  get moves(): Move[] {
    return this.records.map((r) => r.move)
  }
  get hasMoves(): boolean {
    return this.records.length > 0
  }
  get isOver(): boolean {
    return this.result !== null
  }
  get isCheck(): boolean {
    return new Chess(this.fen).isCheck()
  }
  get lastMove(): Move | null {
    return this.records.length ? this.records[this.records.length - 1].move : null
  }

  private boardCache: Map<string, { type: PieceType; color: Color }> | null = null

  /** All pieces by square (computed once per game value). */
  get board(): ReadonlyMap<string, { type: PieceType; color: Color }> {
    if (!this.boardCache) {
      const map = new Map<string, { type: PieceType; color: Color }>()
      for (const row of new Chess(this.fen).board()) {
        for (const cell of row) if (cell) map.set(cell.square, { type: cell.type as PieceType, color: cell.color as Color })
      }
      this.boardCache = map
    }
    return this.boardCache
  }

  pieceAt(square: string): { type: PieceType; color: Color } | null {
    return this.board.get(square) ?? null
  }

  /** King square of `color`. */
  kingSquare(color: Color): string | null {
    for (const [square, p] of this.board) if (p.type === 'k' && p.color === color) return square
    return null
  }

  /** Legal moves in the current position (empty once the game is over). Includes one entry per promotion piece. */
  get legalMoves(): readonly CjMove[] {
    if (this.isOver) return []
    return this.rawLegalMoves()
  }

  private rawLegalMoves(): CjMove[] {
    if (!this.legalCache) this.legalCache = new Chess(this.fen).moves({ verbose: true })
    return this.legalCache
  }

  /** Number of times the current position (FIDE sense) has occurred. */
  get repetitionCount(): number {
    return this.counts.get(repetitionKey(this.fen)) ?? 0
  }

  /** Draws available to claim right now (empty once the game is over). */
  get claimableDraws(): DrawClaim[] {
    if (this.isOver) return []
    const claims: DrawClaim[] = []
    if (this.repetitionCount >= 3) claims.push('threefold_repetition')
    if (this.halfmoveClock >= 100) claims.push('fifty_move_rule')
    return claims
  }

  private automaticResult(): GameResult | null {
    const chess = new Chess(this.fen)
    if (this.rawLegalMoves().length === 0) {
      return chess.isCheck()
        ? { winner: opposite(this.sideToMove), termination: 'checkmate' }
        : { winner: null, termination: 'stalemate' }
    }
    if (chess.isInsufficientMaterial()) return { winner: null, termination: 'insufficient_material' }
    if ((this.counts.get(repetitionKey(this.fen)) ?? 0) >= 5) return { winner: null, termination: 'fivefold_repetition' }
    if (this.halfmoveClock >= 150) return { winner: null, termination: 'seventy_five_move_rule' }
    return null
  }

  /** Plays `move`; throws if the game is over or the move is illegal. */
  play(move: Move): ChessGame {
    if (this.isOver) throw new Error('The game is over')
    const wanted = uci(move)
    const legal = this.rawLegalMoves().find((m) => m.lan === wanted)
    if (!legal) throw new Error(`Illegal move ${wanted} in ${this.fen}`)
    const chess = new Chess(this.fen)
    const played = chess.move({ from: move.from, to: move.to, promotion: move.promotion })
    const record: MoveRecord = {
      move: { from: played.from, to: played.to, ...(played.promotion ? { promotion: played.promotion as Promotion } : {}) },
      san: played.san,
      piece: played.piece as PieceType,
      captured: (played.captured as PieceType | undefined) ?? null,
      isCapture: played.captured !== undefined,
      isEnPassant: played.flags.includes('e'),
      castle: played.flags.includes('k') ? 'k' : played.flags.includes('q') ? 'q' : null,
      givesCheck: chess.isCheck(),
    }
    const fen = chess.fen()
    const key = repetitionKey(fen)
    const counts = new Map(this.counts)
    counts.set(key, (counts.get(key) ?? 0) + 1)
    return new ChessGame(this.initialFen, fen, [...this.records, record], counts, null)
  }

  resign(color: Color): ChessGame {
    return this.finish({ winner: opposite(color), termination: 'resignation' })
  }

  /** Both players agreed to a draw. */
  agreeDraw(): ChessGame {
    return this.finish({ winner: null, termination: 'draw_agreed' })
  }

  /** The player to move claims a draw; throws if that draw is not currently claimable. */
  claimDraw(claim: DrawClaim): ChessGame {
    if (!this.claimableDraws.includes(claim)) throw new Error(`${claim} is not claimable now`)
    return this.finish({
      winner: null,
      termination: claim === 'threefold_repetition' ? 'threefold_repetition_claimed' : 'fifty_move_rule_claimed',
    })
  }

  private finish(result: GameResult): ChessGame {
    if (this.isOver) throw new Error('The game is over')
    return new ChessGame(this.initialFen, this.fen, this.records, this.counts, result)
  }

  static new(initialFen: string = START_FEN): ChessGame {
    validateFen(initialFen)
    const chess = new Chess(initialFen)
    const fen = chess.fen()
    return new ChessGame(fen, fen, [], new Map([[repetitionKey(fen), 1]]), null)
  }

  /** Replays `moves` from `initialFen`; throws if any move is illegal. */
  static fromMoves(initialFen: string, moves: readonly Move[]): ChessGame {
    return moves.reduce<ChessGame>((g, m) => g.play(m), ChessGame.new(initialFen))
  }

  /**
   * Rebuilds a game that ended by a player's decision (`manual`). Claimed draws are only accepted if the claim was really
   * available at that point.
   */
  static restore(initialFen: string, moves: readonly Move[], manual: GameResult | null): ChessGame {
    const game = ChessGame.fromMoves(initialFen, moves)
    if (manual === null) return game
    if (game.isOver) throw new Error('Game already ended automatically')
    switch (manual.termination) {
      case 'resignation':
        if (manual.winner === null) throw new Error('Resignation needs a winner')
        return game.resign(opposite(manual.winner))
      case 'draw_agreed':
        return game.agreeDraw()
      case 'threefold_repetition_claimed':
        return game.claimDraw('threefold_repetition')
      case 'fifty_move_rule_claimed':
        return game.claimDraw('fifty_move_rule')
      default:
        throw new Error(`Not a manual termination: ${manual.termination}`)
    }
  }
}
