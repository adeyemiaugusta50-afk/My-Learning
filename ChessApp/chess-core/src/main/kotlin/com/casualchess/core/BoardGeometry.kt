package com.casualchess.core

/** A cell on the screen's 8x8 grid. Row 0 is the top row, column 0 the left column. */
data class Cell(val row: Int, val col: Int)

/**
 * Maps between screen cells and chess squares for either board orientation. [whiteAtBottom] = true is the normal
 * view; false is the board rotated 180 degrees (black at the bottom, files running h..a from left to right).
 * Both drawing and touch handling go through this one mapping so they can never disagree.
 */
object BoardGeometry {
    fun squareAt(cell: Cell, whiteAtBottom: Boolean): Int = squareAt(cell.row, cell.col, whiteAtBottom)

    fun squareAt(row: Int, col: Int, whiteAtBottom: Boolean): Int {
        require(row in 0..7 && col in 0..7) { "Cell ($row, $col) is off the board" }
        return if (whiteAtBottom) Squares.of(col, 7 - row) else Squares.of(7 - col, row)
    }

    fun cellOf(square: Int, whiteAtBottom: Boolean): Cell {
        require(Squares.isValid(square)) { "Bad square $square" }
        val file = Squares.file(square)
        val rank = Squares.rank(square)
        return if (whiteAtBottom) Cell(7 - rank, file) else Cell(rank, 7 - file)
    }

    /**
     * Converts a touch point inside a square board of side [boardSizePx] to a chess square, or null if the point is
     * outside the board. This is the pixel-level version of [squareAt].
     */
    fun squareAtPoint(x: Float, y: Float, boardSizePx: Float, whiteAtBottom: Boolean): Int? {
        if (boardSizePx <= 0f || x < 0f || y < 0f || x >= boardSizePx || y >= boardSizePx) return null
        val cellSize = boardSizePx / 8f
        val col = (x / cellSize).toInt().coerceIn(0, 7)
        val row = (y / cellSize).toInt().coerceIn(0, 7)
        return squareAt(row, col, whiteAtBottom)
    }

    /** File letters in screen order, left to right. */
    fun fileLabels(whiteAtBottom: Boolean): List<Char> =
        (0..7).map { col -> 'a' + Squares.file(squareAt(7, col, whiteAtBottom)) }

    /** Rank digits in screen order, top to bottom. */
    fun rankLabels(whiteAtBottom: Boolean): List<Char> =
        (0..7).map { row -> '1' + Squares.rank(squareAt(row, 0, whiteAtBottom)) }
}
