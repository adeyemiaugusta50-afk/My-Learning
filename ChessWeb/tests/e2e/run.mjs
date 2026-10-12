// End-to-end tests in a real browser (Chromium via Playwright) against the production build served by `vite preview`.
// Usage: npm run test:e2e        (set CHROMIUM_PATH=/path/to/chrome to use an existing browser instead of Playwright's)
import { spawn } from 'node:child_process'
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'

const PORT = 4173
const SHOTS = process.env.SHOTS_DIR
if (SHOTS) mkdirSync(SHOTS, { recursive: true })

// E2E_BASE_URL lets the same suite run against any already-served build (e.g. the single-file build or a deployed URL).
const BASE = process.env.E2E_BASE_URL || `http://localhost:${PORT}/`
const server = process.env.E2E_BASE_URL ? { kill() {} } : spawn('npx', ['vite', 'preview', '--port', String(PORT), '--strictPort'], { stdio: 'ignore' })
const stop = () => { try { server.kill() } catch { /* ignore */ } }
process.on('exit', stop)
for (let i = 0; i < 100; i++) {
  try { if ((await fetch(BASE)).ok) break } catch { /* not up yet */ }
  await new Promise((r) => setTimeout(r, 200))
}

const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined })
const results = []

async function newPage(viewport = { width: 420, height: 900 }) {
  const context = await browser.newContext({ viewport })
  const page = await context.newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`))
  page.on('console', (m) => { if (m.type() === 'error') errors.push(`console: ${m.text()}`) })
  return { page, context, errors }
}
async function test(name, fn, viewport) {
  const { page, context, errors } = await newPage(viewport)
  try {
    await fn(page, context)
    assert.deepEqual(errors, [], 'no console / page errors')
    results.push([name, true])
    console.log(`PASS  ${name}`)
  } catch (e) {
    results.push([name, false])
    console.log(`FAIL  ${name}\n      ${String(e.message).split('\n').join('\n      ')}`)
    if (SHOTS) await page.screenshot({ path: `${SHOTS}/FAIL-${name.replace(/\W+/g, '-')}.png` }).catch(() => {})
  } finally {
    await context.close()
  }
}

const sq = (page, s) => page.locator(`[data-square="${s}"]`)
const fen = (page) => page.locator('.board').getAttribute('data-fen')
const orientation = (page) => page.locator('.board').getAttribute('data-orientation')
async function clickAt(page, s) { // a REAL pointer click at the on-screen centre of the square (exercises the rotated mapping)
  const b = await sq(page, s).boundingBox()
  await page.mouse.click(b.x + b.width / 2, b.y + b.height / 2)
}
async function newGame(page, query = '') {
  await page.goto(BASE + query + (query ? '#/setup' : ''))
  if (!query) await page.getByRole('button', { name: 'New two-player game' }).click()
  await page.getByRole('button', { name: 'Start game' }).click()
  const discard = page.getByRole('button', { name: 'Discard and start' })
  if (await discard.count()) await discard.click()
  await page.locator('.board').waitFor()
}
const shot = async (page, name) => { if (SHOTS) await page.screenshot({ path: `${SHOTS}/${name}.png` }) }
const START = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'

await test('home screen renders with the expected actions', async (page) => {
  await page.goto(BASE)
  assert.equal(await page.title(), 'Casual Chess')
  await page.getByRole('heading', { name: 'Casual Chess' }).waitFor()
  for (const n of ['New two-player game', 'Settings', 'About and licenses']) await page.getByRole('button', { name: n }).waitFor()
  assert.equal(await page.getByRole('button', { name: /Play the computer/ }).isDisabled(), true)
  assert.equal(await page.getByRole('button', { name: 'Resume game' }).count(), 0)
  await shot(page, '01-home')
})

await test('new game: start position, legal-move highlights, tap-to-move', async (page) => {
  await newGame(page)
  assert.equal(await fen(page), START)
  assert.equal(await page.locator('.sq').count(), 64)
  assert.equal(await orientation(page), 'white-bottom')
  await clickAt(page, 'e2')
  assert.equal(await sq(page, 'e2').getAttribute('aria-pressed'), 'true')
  assert.equal(await page.locator('.sq .target').count(), 2)
  assert.equal(await sq(page, 'e3').locator('.target').count(), 1)
  assert.equal(await sq(page, 'e4').locator('.target').count(), 1)
  assert.match(await sq(page, 'e4').getAttribute('aria-label'), /legal move/)
  await shot(page, '02-selected')
  await clickAt(page, 'e4')
  assert.match(await fen(page), /^rnbqkbnr\/pppppppp\/8\/8\/4P3\/8\/PPPP1PPP\/RNBQKBNR b KQkq - 0 1$/)
  assert.equal(await page.locator('.sq.last').count(), 2)
  assert.equal((await page.locator('[data-testid=history] li').allTextContents()).join('|'), '1. e4')
  assert.equal(await page.locator('.sq .target').count(), 0)
})

await test('illegal / opponent taps do nothing; own piece switches selection', async (page) => {
  await newGame(page)
  await clickAt(page, 'e7') // opponent piece, nothing selected
  assert.equal(await page.locator('.sq.selected').count(), 0)
  await clickAt(page, 'g1'); await clickAt(page, 'e2')
  assert.equal(await sq(page, 'e2').getAttribute('aria-pressed'), 'true')
  await clickAt(page, 'e5') // not a legal target
  assert.equal(await page.locator('.sq.selected').count(), 0)
  assert.equal(await fen(page), START)
})

await test('board rotates 180 degrees after the move is shown, and clicks map correctly afterwards', async (page) => {
  await newGame(page)
  const e7yBefore = (await sq(page, 'e7').boundingBox()).y // near the top while White is at the bottom
  await clickAt(page, 'e2'); await clickAt(page, 'e4')
  assert.equal(await orientation(page), 'white-bottom', 'the completed move is shown first, before rotating')
  await page.waitForFunction(() => document.querySelector('.board')?.getAttribute('data-orientation') === 'black-bottom', null, { timeout: 4000 })
  const e7yAfter = (await sq(page, 'e7').boundingBox()).y
  assert.ok(e7yAfter > e7yBefore + 200, `after rotation e7 moved from the top to the bottom of the board (${e7yBefore} -> ${e7yAfter})`)
  assert.equal(await page.locator('.sq.last').count(), 2, 'the last-move highlight survives the rotation')
  await shot(page, '03-rotated')
  // Real pointer clicks at the squares as displayed now (black at the bottom):
  await clickAt(page, 'e7'); await clickAt(page, 'e5')
  assert.match(await fen(page), /^rnbqkbnr\/pppp1ppp\/8\/4p3\/4P3\/8\/PPPP1PPP\/RNBQKBNR w KQkq - 0 2$/)
  await page.waitForFunction(() => document.querySelector('.board')?.getAttribute('data-orientation') === 'white-bottom', null, { timeout: 4000 })
  assert.equal((await page.locator('[data-testid=history] li').allTextContents()).join('|'), '1. e4 e5')
})

await test('tapping during the short delay rotates immediately and is not also a move', async (page) => {
  await newGame(page)
  await clickAt(page, 'e2'); await clickAt(page, 'e4')
  await clickAt(page, 'e7')
  assert.equal(await orientation(page), 'black-bottom')
  assert.equal(await page.locator('.sq.selected').count(), 0)
})

await test('manual flip and the auto-rotate switch', async (page) => {
  await newGame(page)
  await page.getByRole('button', { name: 'Flip board' }).click()
  assert.equal(await orientation(page), 'black-bottom')
  await page.getByRole('button', { name: 'Flip board' }).click()
  assert.equal(await orientation(page), 'white-bottom')
  await page.getByRole('switch', { name: 'Rotate board after every move' }).uncheck()
  await clickAt(page, 'e2'); await clickAt(page, 'e4')
  await page.waitForTimeout(1300)
  assert.equal(await orientation(page), 'white-bottom', 'no rotation when auto-rotate is off')
})

await test('promotion chooser offers four pieces; underpromotion works', async (page) => {
  await newGame(page, '?fen=' + encodeURIComponent('8/P6k/8/8/8/8/6p1/K7 w - - 0 1'))
  await clickAt(page, 'a7'); await clickAt(page, 'a8')
  const dialog = page.locator('dialog[data-dialog=promotion]')
  await dialog.waitFor()
  assert.equal(await dialog.locator('button[data-promote]').count(), 4)
  await shot(page, '04-promotion')
  await dialog.getByRole('button', { name: 'Promote to knight' }).click()
  await page.locator('dialog').waitFor({ state: 'detached' })
  assert.match(await sq(page, 'a8').getAttribute('aria-label'), /white knight/)
  assert.equal((await page.locator('[data-testid=history] li').allTextContents()).join('|'), '1. a8=N')
})

await test('promotion can be cancelled and leaves the pawn in place', async (page) => {
  await newGame(page, '?fen=' + encodeURIComponent('8/P6k/8/8/8/8/6p1/K7 w - - 0 1'))
  await clickAt(page, 'a7'); await clickAt(page, 'a8')
  await page.locator('dialog[data-dialog=promotion]').getByRole('button', { name: 'Cancel' }).click()
  await page.locator('dialog').waitFor({ state: 'detached' })
  assert.match(await sq(page, 'a7').getAttribute('aria-label'), /white pawn/)
  assert.equal(await page.locator('.sq.selected').count(), 0)
})

await test("fool's mate: check highlight, result dialog, no more moves", async (page) => {
  await newGame(page)
  for (const [a, b] of [['f2', 'f3'], ['e7', 'e5'], ['g2', 'g4']]) {
    await clickAt(page, a); await clickAt(page, b)
    await page.waitForFunction(() => document.querySelector('.board')?.getAttribute('data-fen') !== null)
    await page.getByRole('button', { name: 'Flip board' }) // (just ensures the controls are present)
    await page.waitForTimeout(1050) // wait for the automatic rotation
  }
  await clickAt(page, 'd8'); await clickAt(page, 'h4')
  const dialog = page.locator('dialog[data-dialog=result]')
  await dialog.waitFor()
  assert.match(await dialog.innerText(), /Checkmate: Black \(Black\) wins/)
  await shot(page, '05-checkmate')
  await dialog.getByRole('button', { name: 'Close' }).click()
  assert.equal(await page.getByTestId('status').innerText(), 'Checkmate: Black (Black) wins')
  assert.equal(await sq(page, 'e1').evaluate((el) => el.classList.contains('check')), true)
  assert.equal(await page.getByRole('button', { name: 'Resign' }).isDisabled(), true)
  await clickAt(page, 'e2')
  assert.equal(await page.locator('.sq.selected').count(), 0)
})

await test('50-move rule is claimable (not automatic) and claiming ends the game', async (page) => {
  await newGame(page, '?fen=' + encodeURIComponent('4k3/8/8/8/8/8/8/R3K3 w - - 98 80'))
  await page.getByRole('switch', { name: 'Rotate board after every move' }).uncheck()
  await clickAt(page, 'a1'); await clickAt(page, 'a2')
  assert.equal(await page.getByRole('button', { name: /Claim draw/ }).count(), 0)
  await clickAt(page, 'e8'); await clickAt(page, 'd8')
  const claim = page.getByRole('button', { name: 'Claim draw: 50-move rule' })
  await claim.waitFor()
  assert.equal(await page.getByTestId('status').innerText(), 'White (White) to move', 'the game goes on until somebody claims')
  await claim.click()
  assert.match(await page.locator('dialog[data-dialog=result]').innerText(), /Draw by the 50-move rule \(claimed\)/)
})

await test('resign and offered draw (with confirmation)', async (page) => {
  await newGame(page)
  await clickAt(page, 'e2'); await clickAt(page, 'e4')
  await page.getByRole('button', { name: 'Offer draw' }).click()
  await page.locator('dialog[data-dialog=draw]').getByRole('button', { name: 'Keep playing' }).click()
  assert.equal(await page.getByTestId('status').innerText(), 'Black (Black) to move')
  await page.getByRole('button', { name: 'Resign' }).click()
  await page.getByRole('button', { name: 'Black (Black) resigns' }).click()
  assert.match(await page.locator('dialog[data-dialog=result]').innerText(), /Black \(Black\) resigned: White \(White\) wins/)
})

await test('autosave: reload and resume keeps position, orientation and legal moves', async (page) => {
  await newGame(page)
  await clickAt(page, 'e2'); await clickAt(page, 'e4')
  await page.reload() // immediately, while the rotation is still pending
  await page.getByRole('button', { name: 'Resume game' }).click()
  await page.locator('.board').waitFor()
  assert.match(await fen(page), /^rnbqkbnr\/pppppppp\/8\/8\/4P3\/8\/PPPP1PPP\/RNBQKBNR b KQkq - 0 1$/)
  assert.equal(await orientation(page), 'black-bottom', 'the player to move sees their own side after resuming')
  assert.equal((await page.locator('[data-testid=history] li').allTextContents()).join('|'), '1. e4')
  await clickAt(page, 'e7'); await clickAt(page, 'e5')
  assert.match(await fen(page), /4p3\/4P3/)
})

await test('restart and new game ask before discarding an unfinished game', async (page) => {
  await newGame(page)
  await clickAt(page, 'e2'); await clickAt(page, 'e4')
  await page.getByRole('button', { name: 'Restart' }).click()
  await page.locator('dialog[data-dialog=restart]').getByRole('button', { name: 'Keep playing' }).click()
  assert.notEqual(await fen(page), START)
  await page.getByRole('button', { name: 'New game' }).click() // goes to setup; nothing lost yet
  await page.getByRole('button', { name: 'Start game' }).click()
  await page.locator('dialog[data-dialog=replace]').getByRole('button', { name: 'Keep playing' }).click()
  await page.getByRole('button', { name: 'Back' }).click()
  await page.getByRole('button', { name: 'Resume game' }).click()
  assert.notEqual(await fen(page), START)
  await page.getByRole('button', { name: 'Restart' }).click()
  await page.locator('dialog[data-dialog=restart]').getByRole('button', { name: 'Restart' }).click()
  assert.equal(await fen(page), START)
})

await test('settings: board theme and sound persist across reloads', async (page) => {
  await page.goto(BASE)
  await page.getByRole('button', { name: 'Settings' }).click()
  await page.getByRole('radio', { name: /Wood/ }).check()
  await page.getByRole('switch', { name: 'Sound effects' }).uncheck()
  await page.reload()
  await page.getByRole('button', { name: 'Settings' }).click()
  assert.equal(await page.getByRole('radio', { name: /Wood/ }).isChecked(), true)
  assert.equal(await page.getByRole('switch', { name: 'Sound effects' }).isChecked(), false)
  await page.getByRole('button', { name: 'Back' }).click()
  await page.getByRole('button', { name: 'New two-player game' }).click()
  await page.getByRole('button', { name: 'Start game' }).click()
  assert.match(await page.locator('.board').getAttribute('style'), /#F0D9B5/)
  await shot(page, '06-wood-theme')
})

await test('keyboard: squares are focusable buttons (Tab + Enter), and browser Back returns home', async (page) => {
  await newGame(page)
  await sq(page, 'g1').focus()
  await page.keyboard.press('Enter')
  assert.equal(await sq(page, 'g1').getAttribute('aria-pressed'), 'true')
  await page.keyboard.press('Tab'); await page.keyboard.press('Tab') // moves focus along the board
  await sq(page, 'f3').focus(); await page.keyboard.press('Enter')
  assert.match(await fen(page), /5N2/)
  await page.goBack() // from the game straight to Home (the setup screen is not left in the history)
  await page.getByRole('heading', { name: 'Casual Chess' }).waitFor()
  await page.getByRole('button', { name: 'Resume game' }).waitFor()
})

await test('invalid custom start position is rejected with a message', async (page) => {
  await page.goto(BASE + '?fen=' + encodeURIComponent('4k3/4R3/8/8/8/8/8/4K3 w - - 0 1') + '#/setup')
  await page.getByRole('button', { name: 'Start game' }).click()
  await page.getByRole('alert').waitFor()
  assert.match(await page.getByRole('alert').innerText(), /not valid/)
})

await test('about dialog shows the licence attributions', async (page) => {
  await page.goto(BASE)
  await page.getByRole('button', { name: 'About and licenses' }).click()
  const text = await page.locator('dialog[data-dialog=about]').innerText()
  assert.match(text, /chess\.js \(BSD-2-Clause/)
  assert.match(text, /Chessnut/)
  assert.match(text, /Apache License/)
})

await test('layout: 320x568 phone has no horizontal scroll and the board fits the width', async (page) => {
  await newGame(page)
  const m = await page.evaluate(() => ({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth }))
  assert.ok(m.sw <= m.cw, `no horizontal scroll (${m.sw} <= ${m.cw})`)
  const b = await page.locator('.board').boundingBox()
  assert.ok(b.x >= 0 && b.x + b.width <= 320 && Math.abs(b.width - b.height) < 1, `square board inside the viewport: ${JSON.stringify(b)}`)
  await shot(page, '07-phone-320')
}, { width: 320, height: 568 })

await test('layout: landscape 800x360 keeps the whole board visible', async (page) => {
  await newGame(page)
  const b = await page.locator('.board').boundingBox()
  assert.ok(b.y >= 0 && b.y + b.height <= 360 + 1, `board fits the height: ${JSON.stringify(b)}`)
  await shot(page, '08-landscape')
}, { width: 800, height: 360 })

await test('layout: desktop 1366x768 uses the two-column layout', async (page) => {
  await newGame(page)
  const b = await page.locator('.board').boundingBox()
  assert.ok(b.height <= 768 && b.width > 500, `large board that fits: ${JSON.stringify(b)}`)
  await shot(page, '09-desktop')
}, { width: 1366, height: 768 })

await browser.close()
stop()
const failed = results.filter(([, ok]) => !ok)
console.log(`\n${results.length - failed.length}/${results.length} end-to-end tests passed`)
process.exit(failed.length ? 1 : 0)
