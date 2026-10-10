# Decision: original rules engine instead of a third-party chess library

## Requirement
Use a maintained chess-rules library if a suitable Android-compatible one exists; check licence and rule coverage first.

## What was (and was not) established
* Candidate considered: **chesslib** (`com.github.bhlangonijr:chesslib`, a Java library). It could **not be downloaded or
  inspected** in the environment this project was written in (Maven Central returned HTTP 429 for that artifact), so its
  licence, current release date and rule coverage were **not verified here**. Nothing below claims facts about chesslib
  that were not checked.
* The requirements that decide the question are specific and unusual for a library:
  1. FIDE-correct **claimable vs automatic** draws (threefold/50-move *claim*, fivefold/75-move *automatic*, with a mating
     75th move still a win);
  2. repetition keyed on castling rights and *legal* en passant capture;
  3. a **dead-position** check whose limits are documented;
  4. exact **save/restore** of repetition history, counters and en passant state;
  5. one engine core shared by the UI state machine, usable in plain JVM unit tests.
  A library would still have needed a wrapper for (1)-(4), and any gap in it would silently weaken "do not substitute a
  visually convincing board with incomplete chess rules".

## Decision
Write the rules engine (about 760 lines of Kotlin across `Types`, `Position`, `ChessGame` and `San`, no dependencies) and verify it independently:
* **Perft** against the published reference counts for the start position and the standard stress positions
  (castling, en passant, promotions, pins): all match.
* Targeted tests for each rule in the specification and a **mutation check** (breaking four rules makes tests fail).
* A brute-force enumeration proving that king + knight vs king and king + bishop vs king contain no checkmate position
  (so they are genuinely dead), and an explicit mate with king + two knights (so that case is not dead).

## Reversibility
`chess-core`'s public API (`Position`, `ChessGame`, `Move`) is small. If a vetted library is preferred later, it can be
introduced behind the same API and the existing test suite (including perft and the draw-rule tests) can be run
against it as an oracle - or the library can be used purely as a cross-check in tests.
