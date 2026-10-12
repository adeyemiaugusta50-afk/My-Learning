import { ChessGame, TERMINATIONS, isAutomatic, parseUci, uci, type GameResult, type Move } from './game'
import { cleanName, type SessionState } from './session'
import { settled } from './session'

/** Versioned JSON save format. Only the start position and the moves are stored; everything else is rebuilt by replay. */
const VERSION = 1

interface SaveFile {
  v: number
  fen: string
  moves: string[]
  result: { winner: 'w' | 'b' | null; termination: string } | null
  white: string
  black: string
  whiteAtBottom: boolean
  autoFlip: boolean
}

/**
 * Serialises a session. Castling rights, en passant state, move counters and the whole repetition history are NOT stored:
 * they are rebuilt by replaying the moves through the rules engine, so a resumed game has identical legal moves and draw
 * eligibility, and a corrupted or hand-edited save is rejected instead of producing an illegal game.
 */
export function encodeSession(state: SessionState): string {
  const s = settled(state)
  const manual = s.game.result && !isAutomatic(s.game.result) ? s.game.result : null
  const file: SaveFile = {
    v: VERSION,
    fen: s.game.initialFen,
    moves: s.game.moves.map(uci),
    result: manual,
    white: s.whiteName,
    black: s.blackName,
    whiteAtBottom: s.whiteAtBottom,
    autoFlip: s.autoFlip,
  }
  return JSON.stringify(file)
}

/** Returns the restored session, or null if the text is not a valid save (wrong version, corrupt, or illegal moves). */
export function decodeSession(text: string | null): SessionState | null {
  if (!text) return null
  try {
    const data = JSON.parse(text) as Partial<SaveFile>
    if (data.v !== VERSION || typeof data.fen !== 'string' || !Array.isArray(data.moves)) return null
    const moves: Move[] = data.moves.map((m) => {
      const parsed = typeof m === 'string' ? parseUci(m) : null
      if (!parsed) throw new Error(`Bad move ${String(m)}`)
      return parsed
    })
    let manual: GameResult | null = null
    if (data.result) {
      const { winner, termination } = data.result
      if (winner !== 'w' && winner !== 'b' && winner !== null) throw new Error('Bad winner')
      const t = TERMINATIONS.find((x) => x === termination)
      if (!t) throw new Error('Bad termination')
      manual = { winner, termination: t }
    }
    const game = ChessGame.restore(data.fen, moves, manual)
    return {
      game,
      whiteName: cleanName(String(data.white ?? ''), 'White'),
      blackName: cleanName(String(data.black ?? ''), 'Black'),
      whiteAtBottom: data.whiteAtBottom !== false,
      autoFlip: data.autoFlip !== false,
      selected: null,
      pendingPromotion: null,
      flipPendingAtPly: null,
    }
  } catch {
    return null
  }
}
