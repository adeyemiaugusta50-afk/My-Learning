# Roadmap

## Milestone 1 - done (this repository)
Home/setup, complete two-player chess on one phone, rules (incl. claimable vs automatic draws), history and results,
automatic board rotation, autosave/resume, new-game/restart protection, settings.
**Still to do before calling it released:** build and run on a real device (see README), run the instrumented tests,
test with TalkBack, replace the placeholder `applicationId`, choose a licence for the app code, set up signing.

## Milestone 2 - next (not started)
1. **Computer opponent, fully offline**, several difficulty levels, play either colour (board stays with the human at the
   bottom; no automatic rotation in this mode).
   * Engine choice: see `ENGINE_EVALUATION.md`. Recommended first step: a small original alpha-beta engine on top of
     `chess-core` (no licence obligations); Stockfish only if the owner accepts GPLv3 for the app.
   * Run searches off the main thread (`Dispatchers.Default`), cancel cooperatively, enforce a time/node budget and a
     capped thread count, and tag every search with a game id + ply so a stale result is discarded after undo, restart or
     leaving the screen.
   * Do **not** advertise Elo ratings unless measured (e.g. self-play / gauntlet vs reference engines).
2. **Practice mode:** undo (`ChessGame` is immutable and replay-based, so undo = replay without the last move) and hints
   (engine best move, shown on request).
3. **Optional clocks:** per-side time + increment. Timeout rules: a player whose time runs out loses *unless* the opponent
   cannot possibly checkmate (then a draw, FIDE 6.9). Policy proposal for backgrounding: the running clock **pauses**
   when the app leaves the foreground and shows "paused"; it resumes on return. Persist remaining time with the save.

## Future (recorded only, not planned in detail)
* Online matchmaking and friend invitations
* Accounts and cloud saves
* Rankings and tournaments
* Advanced analysis
* Lessons and puzzle collections
* Chat

These need servers, accounts and moderation, i.e. paid infrastructure and privacy/policy work; each needs a separate decision.
