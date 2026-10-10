# Known limitations

## Verification gaps (important)
* The Android module was **never built into an APK** and no instrumented test, lint run, emulator or TalkBack check was
  possible where it was written. Only `chess-core` was built and tested; the app code was type-checked against stand-ins.
  See the table in the README.
* Dependency versions come from official documentation and have not been built together.
* Screenshots in the README are desktop renders with an older Compose, not device screenshots.

## Rules
* **Dead positions:** detected only from material - bare kings, king + one minor piece vs king, and kings with only
  same-coloured bishops. Blocked "fortress" positions (e.g. locked pawns with kings that can only shuffle) are not
  recognised, so the game continues until another rule (repetition, 75-move) ends it. A fully general test is a search
  problem. K+N+N vs K is correctly *not* dead (a helpmate exists).
* **Threefold repetition / 50-move claims** are offered once the *current* position has occurred three times / the clock
  reads 100 half-moves. FIDE also lets a player claim by announcing a move that *will* create the repetition; that is
  not offered. Either player can press the claim button (the app cannot tell who is holding the phone).
* Repetition uses castling *rights* (not "castling currently possible"), the usual rules-library interpretation.
* Games start from the standard position. The engine reads any FEN, but there is no setup-position screen.

## App
* No undo, hints, clocks or computer opponent yet (milestone 2). The "Play the computer" entry is disabled.
* After the phone process is killed, the app reopens at the Home screen (the game is autosaved and offered via
  *Resume game*). Open dialogs and the current selection are not restored.
* Pieces stay upright after the rotation (only the squares turn). There is no "face-to-face / tabletop" mode that turns
  the opponent's pieces upside-down.
* Board squares are ~40 dp wide on a 320 dp phone (under the 48 dp touch-target guideline); controls are 48 dp.
  Screen-reader descriptions exist for every square but have not been tested with TalkBack.
* On a large portrait screen (tablet) the board grows to the full screen width, which is very large; there is no maximum board size yet.
* The save format is `CCGAME 1`; there is no migration code yet because there is only one version.
* `applicationId` is a placeholder; the release build enables R8 shrinking with default rules and has not been tried.
* Sounds are three short synthesised effects; there is no haptic feedback.
