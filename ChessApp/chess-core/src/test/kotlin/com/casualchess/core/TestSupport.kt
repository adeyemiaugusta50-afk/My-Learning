package com.casualchess.core

fun pos(fen: String): Position = Position.fromFen(fen)

fun sq(name: String): Int = requireNotNull(Squares.parse(name)) { "bad square $name" }

/** Plays moves given in UCI text (e.g. "e2e4", "e7e8q") one after another. */
fun ChessGame.uci(vararg moves: String): ChessGame =
    moves.fold(this) { game, text -> game.play(requireNotNull(Move.fromUci(text)) { "bad move $text" }) }

fun gameFrom(fen: String): ChessGame = ChessGame.new(pos(fen))

fun Position.movesFrom(square: String): Set<String> =
    legalMoves.filter { it.from == sq(square) }.map { it.uci }.toSet()

fun Position.uciMoves(): Set<String> = legalMoves.map { it.uci }.toSet()
