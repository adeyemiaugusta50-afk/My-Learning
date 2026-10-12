# Licences and notices (web version)

The app's own source code has **no licence file yet** (same as the Android app) - choose one before publishing or accepting
contributions.

| Component | Used for | Licence |
|---|---|---|
| [chess.js](https://github.com/jhlywa/chess.js) 1.4.0, Copyright (c) 2025 Jeff Hlywa | move generation, rules, notation (shipped in the bundle) | BSD-2-Clause - text in `public/licenses/BSD-2-Clause-chess.js.txt` |
| "Chessnut" pieces by Alexis Luengas (embedded as SVG in `src/pieces.generated.ts`) | piece graphics (shipped) | Apache License 2.0 - text in `public/licenses/Apache-2.0-chessnut-pieces.txt` |
| Vite, Vitest, TypeScript, Playwright, vite-plugin-singlefile | build and test tooling (not shipped) | MIT / Apache-2.0 (per their package metadata) |

The in-app *About and licenses* dialog carries the attribution. `public/licenses/` is copied to the deployed site
(`/licenses/...`). Sound effects are generated in the browser; there are no audio files.
If you redistribute a build, keep the chess.js copyright notice and licence text.
