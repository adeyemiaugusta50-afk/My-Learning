# Computer opponent: engine evaluation (for milestone 2)

Nothing in this document is integrated yet. It records what was checked and what must be decided first.

## Stockfish - licence facts (read from the official repository on 2026-10-10)

* `Copying.txt` is the **GNU GPL version 3**; source file headers say "GPL ... either version 3 of the License, or (at your
  option) any later version".
* The README's *Terms of use* says Stockfish may be used, redistributed and sold, but that **whenever you distribute
  Stockfish you must make the complete corresponding source available** (enough to generate the exact binary you
  distribute), and **any changes you make must also be released under GPL v3**.
* Binary distributions embed a `.nnue` neural-network file. The README acknowledges the networks are trained on data from the
  Leela Chess Zero project, made available under the **Open Database License (ODbL)**.

### What that means for this app
1. **Shipping Stockfish in the APK is "distributing Stockfish".** You would have to offer the full corresponding source for
   the Stockfish build you ship and keep its copyright/licence notices.
2. **Whether the rest of the app must also become GPLv3 depends on how it is combined.** Linking the engine into the app
   process (NDK/JNI) is the clear case where the combined work is GPLv3. Running it as a separate executable that the app
   talks to over UCI is generally argued to be "mere aggregation", but this is a legal judgement, not a technical fact.
   **Get legal advice before relying on it.**
3. **The GPL and app-store terms can conflict** (additional restrictions on recipients). Several GPL apps are on Google Play, but
   check the current Play terms and the licence with a lawyer before depending on it.
4. If you later add accounts/servers, Stockfish licence obligations apply to whatever you distribute or (for network-served
   software under AGPL-style rules, not GPLv3) host - re-check then.
5. The app's own licence would then have to be GPLv3-compatible, which constrains closed-source or commercial plans.

## Options

| Option | Licence impact | Strength | Effort | Notes |
|---|---|---|---|---|
| **A. Original alpha-beta engine in Kotlin on `chess-core`** (material + piece-square tables, iterative deepening, time limit, optional randomness for weaker levels) | None (own code) | Fine for casual players; levels from depth/time/noise | Low-medium | **Recommended first step.** The rules engine already gives fast legal-move generation (perft-verified). |
| **B. Stockfish bundled via NDK** | GPLv3 obligations above | Far stronger than needed | Medium (native build for several ABIs, UCI plumbing, APK size grows by the network file) | Needs an explicit owner decision on GPLv3 for the app. |
| **C. Another GPL engine (Fairy-Stockfish, Leela, etc.)** | GPL family, same analysis | varies | varies | No advantage over B for this product. |
| **D. A permissively licensed engine** | Must be verified per project | varies | Medium | Candidates were **not** researched or verified here; do not assume a free engine is permissive. |

Recommendation: start with **A**. Revisit **B** only if (a) players ask for stronger play and (b) the owner accepts GPLv3
and the source-offer duty after legal review.

## Engineering requirements (apply to any option)
* **Off the main thread:** run the search in a coroutine on `Dispatchers.Default` (or a single dedicated thread).
* **Bounded:** a hard time limit per move (e.g. 0.3 s to 3 s by level) and a node/depth cap; use at most 1-2 threads and
  a small hash table to protect battery and low-end phones.
* **Cancellation:** searches are cancellable (coroutine cancellation checked inside the search loop). Cancel on restart, undo,
  leaving the game screen and activity destruction. Tag each search with `(gameId, ply)` and ignore any result that does not
  match the current state - a late answer must never be played into a different game.
* **Difficulty:** vary depth/time and add controlled randomness among near-best moves; **do not advertise Elo** unless it has
  been measured by running matches against reference opponents.
* **Computer mode UI:** the human's side stays at the bottom (no automatic rotation), and the human may choose White or Black.

## Clocks (for milestone 2): proposed rules
* Flag fall = loss, **unless the opponent has no way to checkmate** (insufficient material in the dead-position sense), then a
  draw. `Position.hasInsufficientMaterial` is the starting point; FIDE's exact rule is about the opponent being unable to
  mate by any series of legal moves.
* Backgrounding: the clock **pauses** when the app leaves the foreground (casual-play policy), shows "paused", and resumes on
  return. Remaining time is saved with the game so a killed process resumes correctly.
