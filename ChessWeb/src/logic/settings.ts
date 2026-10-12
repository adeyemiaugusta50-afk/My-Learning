export interface BoardTheme {
  id: string
  name: string
  light: string
  dark: string
}

/** Same four board colour schemes as the Android app. */
export const BOARD_THEMES: readonly BoardTheme[] = [
  { id: 'green', name: 'Classic green', light: '#EEEED2', dark: '#769656' },
  { id: 'wood', name: 'Wood', light: '#F0D9B5', dark: '#B58863' },
  { id: 'blue', name: 'Ocean blue', light: '#DEE3E6', dark: '#8CA2AD' },
  { id: 'gray', name: 'High contrast gray', light: '#FFFFFF', dark: '#7A7A7A' },
]

export interface AppSettings {
  boardTheme: string
  soundEnabled: boolean
  /** Default for new games: rotate the board 180 degrees after every move. */
  autoFlip: boolean
}

export const DEFAULT_SETTINGS: AppSettings = { boardTheme: 'green', soundEnabled: true, autoFlip: true }

export function themeById(id: string): BoardTheme {
  return BOARD_THEMES.find((t) => t.id === id) ?? BOARD_THEMES[0]
}

export function parseSettings(text: string | null): AppSettings {
  if (!text) return { ...DEFAULT_SETTINGS }
  try {
    const d = JSON.parse(text) as Partial<AppSettings>
    return {
      boardTheme: themeById(String(d.boardTheme)).id,
      soundEnabled: d.soundEnabled !== false,
      autoFlip: d.autoFlip !== false,
    }
  } catch {
    return { ...DEFAULT_SETTINGS }
  }
}
