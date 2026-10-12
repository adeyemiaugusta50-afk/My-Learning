import { START_FEN, type Color, type DrawClaim, type Promotion, PROMOTIONS } from '../logic/game'
import { decodeSession, encodeSession } from '../logic/codec'
import { claimLabel, colorName, historyRows, promotionName, resultText, statusText } from '../logic/describe'
import {
  FLIP_DELAY_MS, MAX_NAME_LENGTH, agreeDraw, cancelPromotion, choosePromotion, claimDraw, finishFlip, flipManually,
  needsLeaveConfirmation, newSession, nameOf, resign, tap, withAutoFlip, type SessionEvent, type SessionState,
} from '../logic/session'
import { BOARD_THEMES, parseSettings, themeById, type AppSettings } from '../logic/settings'
import { pieceSvg, renderBoard } from './board'
import { h } from './dom'
import { playSound } from './sound'

type Screen = 'home' | 'setup' | 'game' | 'settings'

const GAME_KEY = 'casualchess.game.v1'
const SETTINGS_KEY = 'casualchess.settings.v1'

const ABOUT_TEXT = `Casual Chess works entirely in your browser. There are no accounts, no ads, no analytics and no server: your game and settings are stored only in this browser (localStorage).

Chess rules
Move generation, check, checkmate, stalemate, castling, en passant, promotion and notation come from chess.js (BSD-2-Clause, Copyright (c) 2025 Jeff Hlywa). The draw rules that need FIDE's "claimable" vs "automatic" distinction (threefold repetition and the 50-move rule are claimed; fivefold repetition and the 75-move rule end the game by themselves) are implemented by this app on top of chess.js.

Chess pieces
"Chessnut" piece set by Alexis Luengas, licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0). Source: https://github.com/LexLuengas/chessnut-pieces. The artwork was embedded as vector paths and not otherwise changed.

Sound effects
Synthesised in your browser with the Web Audio API; there are no audio files.

This is the browser version of the Casual Chess Android app. See the README for what differs.`

// ---------------------------------------------------------------------------------------------------------------------
// storage helpers (private browsing / blocked storage must never break the app)

function readStorage(key: string): string | null {
  try { return localStorage.getItem(key) } catch { return null }
}
function writeStorage(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch { /* ignore */ }
}

// ---------------------------------------------------------------------------------------------------------------------

export function startApp(root: HTMLElement): void {
  let screen: Screen = 'home'
  let settings: AppSettings = parseSettings(readStorage(SETTINGS_KEY))
  let session: SessionState | null = decodeSession(readStorage(GAME_KEY))
  let flipTimer: number | undefined
  let gameId = 0
  let resultShownFor = ''
  let openDialogEl: HTMLDialogElement | null = null
  let openDialogKind = ''
  let setupDraft = { white: 'White', black: 'Black', autoFlip: settings.autoFlip }
  let setupError = ''

  // ---------- persistence ----------
  const persistGame = () => writeStorage(GAME_KEY, session ? encodeSession(session) : null)
  const persistSettings = () => writeStorage(SETTINGS_KEY, JSON.stringify(settings))
  const canResume = () => session !== null && !session.game.isOver

  // ---------- navigation ----------
  const hashFor = (s: Screen) => (s === 'home' ? '#/' : `#/${s}`)
  function go(next: Screen, push = true): void {
    if (next === 'game' && !session) next = 'home'
    // Starting a game from the setup screen replaces that history entry, so browser Back from the game goes Home (as on Android).
    const replace = screen === 'setup' && next === 'game'
    screen = next
    closeDialog()
    if (push && location.hash !== hashFor(next)) {
      try {
        if (replace) history.replaceState({ screen: next }, '', hashFor(next))
        else history.pushState({ screen: next }, '', hashFor(next))
      } catch { /* sandboxed frames may forbid history changes; navigation still works without them */ }
    }
    render()
  }
  window.addEventListener('popstate', () => {
    const s = (location.hash.replace('#/', '') || 'home') as Screen
    go(['home', 'setup', 'game', 'settings'].includes(s) ? s : 'home', false)
  })
  window.addEventListener('pagehide', persistGame)
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') persistGame() })

  // ---------- game actions ----------
  function cancelFlip(): void {
    if (flipTimer !== undefined) window.clearTimeout(flipTimer)
    flipTimer = undefined
  }

  /** Keeps the completed move on screen for a moment, then rotates the board (unless something changed meanwhile). */
  function scheduleFlip(): void {
    if (!session) return
    const ply = session.flipPendingAtPly
    if (ply === null) return
    cancelFlip()
    flipTimer = window.setTimeout(() => {
      flipTimer = undefined
      if (!session) return
      session = finishFlip(session, ply)
      persistGame()
      render()
    }, FLIP_DELAY_MS)
  }

  function sound(event: SessionEvent): void {
    if (!settings.soundEnabled || event.type !== 'move_played') return
    playSound(event.result ? 'end' : event.record.isCapture ? 'capture' : 'move')
  }

  function onSquare(square: string): void {
    if (!session) return
    const step = tap(session, square)
    session = step.state
    if (step.event.type === 'flipped_early') cancelFlip()
    sound(step.event)
    if (step.event.type === 'move_played') {
      persistGame()
      scheduleFlip()
    } else if (step.event.type === 'flipped_early') {
      persistGame()
    }
    render()
  }

  function choose(type: Promotion): void {
    if (!session) return
    const step = choosePromotion(session, type)
    session = step.state
    sound(step.event)
    persistGame()
    scheduleFlip()
    closeDialog()
    render()
  }

  function ended(next: SessionState): void {
    cancelFlip()
    session = next
    if (settings.soundEnabled) playSound('end')
    persistGame()
    render()
  }

  function startNewGame(white: string, black: string, autoFlip: boolean, fen: string = START_FEN): void {
    cancelFlip()
    session = newSession(white, black, autoFlip, fen)
    gameId++
    resultShownFor = ''
    persistGame()
    go('game')
  }

  // ---------- dialogs ----------
  function closeDialog(): void {
    const d = openDialogEl
    openDialogEl = null
    openDialogKind = ''
    if (d) { d.removeEventListener('close', onDialogClose); if (d.open) d.close(); d.remove() }
  }
  let onDialogCancel: (() => void) | null = null
  function onDialogClose(): void {
    const cb = onDialogCancel
    onDialogCancel = null
    openDialogEl?.remove()
    openDialogEl = null
    openDialogKind = ''
    cb?.()
  }
  function openDialog(kind: string, content: Array<Node | string>, onCancel: (() => void) | null = null): void {
    closeDialog()
    const d = h('dialog', { 'data-dialog': kind, 'aria-label': kind }, ...content)
    document.body.append(d)
    openDialogEl = d
    openDialogKind = kind
    onDialogCancel = onCancel
    d.addEventListener('close', onDialogClose)
    d.showModal()
  }
  const actionsRow = (...buttons: HTMLElement[]) => h('div', { class: 'actions' }, ...buttons)
  const textButton = (label: string, onclick: () => void, cls = 'btn small', fk?: string) =>
    h('button', { type: 'button', class: cls, onclick, 'data-fk': fk }, label)

  function confirmDialog(kind: string, title: string, message: string, confirmLabel: string, dismissLabel: string, onConfirm: () => void): void {
    openDialog(kind, [
      h('h2', {}, title), h('p', {}, message),
      actionsRow(textButton(dismissLabel, closeDialog), textButton(confirmLabel, () => { closeDialog(); onConfirm() }, 'btn small primary')),
    ])
  }

  function showAbout(): void {
    openDialog('about', [h('h2', {}, 'About and licenses'), h('div', { class: 'about' }, ABOUT_TEXT), actionsRow(textButton('Close', closeDialog, 'btn small primary'))])
  }

  /** Opens/closes dialogs that mirror game state (promotion chooser, game-over). Called after every render of the game. */
  function syncGameDialogs(): void {
    if (screen !== 'game' || !session) return
    const s = session
    if (s.pendingPromotion) {
      if (openDialogKind !== 'promotion') {
        const color = s.game.sideToMove
        const buttons = PROMOTIONS.map((p) =>
          h('button', { type: 'button', 'aria-label': `Promote to ${promotionName(p)}`, 'data-promote': p, html: pieceSvg(p, color), onclick: () => choose(p) }))
        openDialog('promotion', [
          h('h2', {}, 'Promote pawn to'), h('div', { class: 'promo', style: `--dark-sq:${themeById(settings.boardTheme).dark}` }, ...buttons),
          actionsRow(textButton('Cancel', () => { closeDialog(); session = cancelPromotion(session!); render() })),
        ], () => { if (session?.pendingPromotion) { session = cancelPromotion(session); render() } })
      }
      return
    }
    if (openDialogKind === 'promotion') closeDialog()
    const result = s.game.result
    if (result && openDialogKind === '') {
      const key = `${gameId}:${result.termination}:${s.game.records.length}`
      if (key !== resultShownFor) {
        resultShownFor = key
        openDialog('result', [
          h('h2', {}, 'Game over'), h('p', {}, resultText(result, s.whiteName, s.blackName)),
          actionsRow(
            textButton('Close', closeDialog),
            textButton('New game', () => { closeDialog(); go('setup') }),
            textButton('Play again', () => { closeDialog(); startNewGame(s.whiteName, s.blackName, s.autoFlip) }, 'btn small primary')),
        ])
      }
    }
  }

  // ---------- screens ----------
  function wideButton(label: string, onclick: () => void, primary = false, disabled = false, fk?: string): HTMLButtonElement {
    return h('button', { type: 'button', class: `btn${primary ? ' primary' : ''}`, onclick, disabled, 'data-fk': fk }, label)
  }

  function homeScreen(): HTMLElement {
    const items: Array<HTMLElement | null> = [
      h('h1', {}, 'Casual Chess'),
      h('p', { class: 'sub' }, 'Two players, one device. Runs entirely in your browser; nothing is sent anywhere.'),
    ]
    if (canResume() && session) {
      items.push(wideButton('Resume game', () => go('game'), true, false, 'resume'))
      items.push(h('p', { class: 'note' }, `${session.whiteName} vs ${session.blackName}, move ${session.game.fullmoveNumber}`))
    }
    items.push(
      wideButton('New two-player game', () => { setupError = ''; go('setup') }, !canResume(), false, 'new'),
      wideButton('Play the computer (coming soon)', () => {}, false, true),
      wideButton('Settings', () => go('settings'), false, false, 'settings'),
      wideButton('About and licenses', showAbout, false, false, 'about'),
    )
    return h('main', { class: 'screen' }, ...items)
  }

  function switchRow(label: string, checked: boolean, onChange: (v: boolean) => void, fk: string): HTMLElement {
    const input = h('input', { type: 'checkbox', class: 'switch', role: 'switch', 'data-fk': fk, checked, id: fk })
    input.checked = checked
    input.addEventListener('change', () => onChange(input.checked))
    return h('div', { class: 'row between' }, h('label', { class: 'grow', for: fk }, label), input)
  }

  function setupScreen(): HTMLElement {
    const params = new URLSearchParams(location.search)
    const customFen = params.get('fen')
    const white = h('input', { type: 'text', id: 'name-w', maxlength: MAX_NAME_LENGTH, value: setupDraft.white, 'data-fk': 'name-w' })
    const black = h('input', { type: 'text', id: 'name-b', maxlength: MAX_NAME_LENGTH, value: setupDraft.black, 'data-fk': 'name-b' })
    white.addEventListener('input', () => { setupDraft.white = white.value })
    black.addEventListener('input', () => { setupDraft.black = black.value })
    const start = () => {
      setupDraft.white = white.value
      setupDraft.black = black.value
      const begin = () => {
        try {
          startNewGame(setupDraft.white, setupDraft.black, setupDraft.autoFlip, customFen ?? START_FEN)
        } catch (e) {
          setupError = `That start position is not valid: ${(e as Error).message}`
          render()
        }
      }
      if (session && needsLeaveConfirmation(session)) {
        confirmDialog('replace', 'Discard the game in progress?',
          `Starting a new game will replace the unfinished game (${session.whiteName} vs ${session.blackName}). This cannot be undone.`,
          'Discard and start', 'Keep playing', begin)
      } else begin()
    }
    return h('main', { class: 'screen top' },
      h('h2', {}, 'New game'),
      h('div', { class: 'section' }, 'Mode'),
      h('label', { class: 'radio' }, h('input', { type: 'radio', name: 'mode', checked: true }), 'Two players on one device'),
      h('label', { class: 'radio disabled' }, h('input', { type: 'radio', name: 'mode', disabled: true }), 'Against the computer (coming soon)'),
      h('div', { class: 'section' }, 'Players'),
      h('div', { class: 'field' }, h('label', { for: 'name-w' }, 'White player'), white),
      h('div', { class: 'field' }, h('label', { for: 'name-b' }, 'Black player'), black),
      switchRow('Rotate the board after every move', setupDraft.autoFlip, (v) => { setupDraft.autoFlip = v }, 'setup-autoflip'),
      customFen ? h('p', { class: 'note', 'data-testid': 'custom-fen' }, `Custom start position (test hook): ${customFen}`) : null,
      setupError ? h('p', { class: 'note', role: 'alert' }, setupError) : null,
      wideButton('Start game', start, true, false, 'start'),
      wideButton('Back', () => go('home'), false, false, 'back'),
    )
  }

  function settingsScreen(): HTMLElement {
    const themes = BOARD_THEMES.map((t) => {
      const input = h('input', { type: 'radio', name: 'theme', value: t.id, checked: settings.boardTheme === t.id, 'data-fk': `theme-${t.id}` })
      input.checked = settings.boardTheme === t.id
      input.addEventListener('change', () => { settings = { ...settings, boardTheme: t.id }; persistSettings(); render() })
      return h('label', { class: 'radio' }, input,
        h('span', { class: 'swatch' }, h('i', { style: `background:${t.light}` }), h('i', { style: `background:${t.dark}` })), t.name)
    })
    return h('main', { class: 'screen top' },
      h('h2', {}, 'Settings'),
      h('div', { class: 'section' }, 'Board appearance'), ...themes,
      h('div', { class: 'section' }, 'Gameplay'),
      switchRow('Sound effects', settings.soundEnabled, (v) => { settings = { ...settings, soundEnabled: v }; persistSettings() }, 'set-sound'),
      switchRow('Rotate the board after every move (new games)', settings.autoFlip, (v) => { settings = { ...settings, autoFlip: v }; setupDraft.autoFlip = v; persistSettings() }, 'set-autoflip'),
      wideButton('Back', () => go('home'), false, false, 'back'),
    )
  }

  function playerTag(s: SessionState, color: Color): HTMLElement {
    const toMove = !s.game.isOver && s.game.sideToMove === color
    return h('div', { class: `player${toMove ? ' turn' : ''}` },
      h('i', { class: `dot ${color}` }),
      h('span', {}, `${nameOf(s, color)} (${colorName(color)})${toMove ? ', to move' : ''}`))
  }

  function gameScreen(s: SessionState): HTMLElement {
    const top: Color = s.whiteAtBottom ? 'b' : 'w'
    const over = s.game.isOver
    const status = h('p', { class: `status${over || s.game.isCheck ? ' strong' : ''}`, 'aria-live': 'polite', 'data-testid': 'status' }, statusText(s))
    const rows = historyRows(s.game)
    const autoInput = h('input', { type: 'checkbox', class: 'switch', role: 'switch', id: 'game-autoflip', 'data-fk': 'game-autoflip' })
    autoInput.checked = s.autoFlip
    autoInput.addEventListener('change', () => {
      if (!session) return
      session = withAutoFlip(session, autoInput.checked)
      if (!autoInput.checked) cancelFlip()
      persistGame()
      render()
    })
    const claims = s.game.claimableDraws.map((c: DrawClaim) =>
      h('button', { type: 'button', class: 'btn wide', 'data-fk': `claim-${c}`, onclick: () => { if (session) ended(claimDraw(session, c)) } }, claimLabel(c)))
    const btn = (label: string, onclick: () => void, fk: string, disabled = false) =>
      h('button', { type: 'button', class: 'btn small', onclick, disabled, 'data-fk': fk }, label)

    const panel = h('div', { class: 'panel' },
      status,
      h('div', { class: 'players-side' }, playerTag(s, top), playerTag(s, top === 'w' ? 'b' : 'w')),
      h('div', { class: 'controls' },
        btn('Flip board', () => { if (session) { cancelFlip(); session = flipManually(session); persistGame(); render() } }, 'flip'),
        btn('Menu', () => go('home'), 'menu')),
      h('div', { class: 'row between' }, h('label', { class: 'grow', for: 'game-autoflip' }, 'Rotate board after every move'), autoInput),
      ...claims,
      h('div', { class: 'controls' },
        btn('Resign', () => openDialog('resign', [
          h('h2', {}, 'Who is resigning?'), h('p', {}, 'The other player wins the game.'),
          h('div', { class: 'stack' },
            textButton(`${s.whiteName} (White) resigns`, () => { closeDialog(); if (session) ended(resign(session, 'w')) }, 'btn small primary', 'resign-w'),
            textButton(`${s.blackName} (Black) resigns`, () => { closeDialog(); if (session) ended(resign(session, 'b')) }, 'btn small primary', 'resign-b')),
          actionsRow(textButton('Cancel', closeDialog)),
        ]), 'resign', over),
        btn('Offer draw', () => confirmDialog('draw', 'Agree to a draw?', 'Both players agree to end the game as a draw.', 'Yes, draw', 'Keep playing',
          () => { if (session) ended(agreeDraw(session)) }), 'draw', over)),
      h('div', { class: 'controls' },
        btn('New game', () => go('setup'), 'newgame'),
        btn('Restart', () => {
          const restart = () => { if (session) startNewGame(session.whiteName, session.blackName, session.autoFlip) }
          if (needsLeaveConfirmation(s)) {
            confirmDialog('restart', 'Restart this game?', 'The game in progress will be lost and a new one will start with the same players.', 'Restart', 'Keep playing', restart)
          } else restart()
        }, 'restart')),
      h('div', { class: 'moves' }, h('h3', {}, 'Moves'),
        rows.length ? h('ol', { 'data-testid': 'history' }, ...rows.map((r) => h('li', {}, r))) : h('p', { class: 'note' }, 'No moves yet.')),
    )
    const board = renderBoard(s, themeById(settings.boardTheme), onSquare)
    return h('main', { class: 'game' },
      h('div', { class: 'board-col' }, h('div', { class: 'players-top' }, playerTag(s, top)), board, h('div', { class: 'players-bottom' }, playerTag(s, top === 'w' ? 'b' : 'w'))),
      panel)
  }

  // ---------- render ----------
  function render(): void {
    const focused = document.activeElement instanceof HTMLElement ? document.activeElement.dataset.fk : undefined
    const scrollY = window.scrollY
    let content: HTMLElement
    if (screen === 'game' && session) content = gameScreen(session)
    else if (screen === 'setup') content = setupScreen()
    else if (screen === 'settings') content = settingsScreen()
    else content = homeScreen()
    root.replaceChildren(content)
    if (focused && !openDialogEl) (root.querySelector(`[data-fk="${CSS.escape(focused)}"]`) as HTMLElement | null)?.focus({ preventScroll: true })
    if (screen === 'game') {
      window.scrollTo(0, scrollY)
      const ol = root.querySelector('.moves ol')
      if (ol) ol.scrollTop = ol.scrollHeight
    }
    syncGameDialogs()
  }

  // Start on the home screen; a direct link to #/setup is allowed (used by the test hook ?fen=...).
  const initial = location.hash.replace('#/', '')
  screen = initial === 'setup' ? 'setup' : 'home'
  render()
}
