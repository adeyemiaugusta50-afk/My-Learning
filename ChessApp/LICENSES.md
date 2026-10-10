# Licences and notices

The app's own source code has **no licence file yet** - choose one before publishing or accepting contributions.

## Assets shipped in the app

| Asset | Author | Licence | Where |
|---|---|---|---|
| Chess pieces ("Chessnut" set, converted from SVG to vector path data; artwork otherwise unchanged) | Alexis Luengas, https://github.com/LexLuengas/chessnut-pieces | **Apache License 2.0** | `assets-src/chessnut/` (SVGs + `LICENSE.txt`), generated `app/src/main/java/.../ui/PieceArt.kt`, licence text bundled in `app/src/main/assets/licenses/Apache-2.0-chessnut-pieces.txt`, attribution in the in-app *About and licenses* dialog |
| Sound effects (`move.wav`, `capture.wav`, `game_end.wav`) | Synthesised for this project by `tools/gen_sounds.py` | CC0 1.0 (public domain) | `app/src/main/res/raw/` |
| Launcher icon (simple pawn silhouette) | Drawn for this project | same licence as the app code (to be chosen) | `app/src/main/res/drawable/ic_launcher_*.xml` |

How the Chessnut licence was checked: the lichess `COPYING.md` lists the set as "Alexis Luengas, Apache 2.0", and the
upstream repository's `LICENSE.txt` is the Apache 2.0 text. Apache 2.0 requires giving recipients a copy of the licence and
keeping attribution, which the bundled licence file and the About dialog do. If you modify the pieces, mark the changes.

The pieces were chosen over other sets because many popular piece sets are GPL (which would affect the whole app),
CC BY-NC / CC BY-SA (non-commercial or share-alike), or AGPL, per the same lichess `COPYING.md`.

## Libraries

| Component | Used for | Licence |
|---|---|---|
| Kotlin standard library / Kotlin Gradle plugin / Compose compiler plugin | language, build | Apache 2.0 |
| AndroidX (activity-compose, lifecycle) and Jetpack Compose (ui, foundation, material3) | UI | Apache 2.0 |
| Android Gradle Plugin, Gradle (wrapper) | build tooling (not shipped) | Apache 2.0 |
| JUnit 5 / `kotlin-test` | unit tests (not shipped) | EPL 2.0 / Apache 2.0 |

These licence statements are from general knowledge of these projects and were **not** re-verified file by file here.
Before a store release, generate the exact dependency licence list for the shipped APK (for example with Android Studio's
dependency view or a Gradle licence-report plugin) and include any required notices.

## Chess rules

No third-party chess library is used; the rules engine is original code (see `docs/CHESS_LIBRARY_DECISION.md`).

## Not used (and why it matters)

Stockfish and other engines are **not** included. Stockfish is GPL-3.0-or-later; see `docs/ENGINE_EVALUATION.md`
before deciding to add it.
