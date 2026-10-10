package com.casualchess.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BoardGeometryTest {
    @Test fun normalOrientationPutsWhiteAtTheBottomLeftAsA1() {
        assertEquals(sq("a8"), BoardGeometry.squareAt(0, 0, whiteAtBottom = true))
        assertEquals(sq("h8"), BoardGeometry.squareAt(0, 7, whiteAtBottom = true))
        assertEquals(sq("a1"), BoardGeometry.squareAt(7, 0, whiteAtBottom = true))
        assertEquals(sq("h1"), BoardGeometry.squareAt(7, 7, whiteAtBottom = true))
        assertEquals(sq("e2"), BoardGeometry.squareAt(6, 4, whiteAtBottom = true))
    }

    @Test fun rotatedBoardIsExactlyA180DegreeTurn() {
        assertEquals(sq("h1"), BoardGeometry.squareAt(0, 0, whiteAtBottom = false))
        assertEquals(sq("a1"), BoardGeometry.squareAt(0, 7, whiteAtBottom = false))
        assertEquals(sq("h8"), BoardGeometry.squareAt(7, 0, whiteAtBottom = false))
        assertEquals(sq("a8"), BoardGeometry.squareAt(7, 7, whiteAtBottom = false))
        // A 180 degree rotation maps cell (r, c) to (7 - r, 7 - c).
        for (row in 0..7) for (col in 0..7) {
            assertEquals(
                BoardGeometry.squareAt(row, col, true),
                BoardGeometry.squareAt(7 - row, 7 - col, false),
                "cell ($row,$col)",
            )
        }
    }

    @Test fun cellAndSquareMappingsAreInversesInBothOrientations() {
        for (orientation in listOf(true, false)) {
            val seen = mutableSetOf<Int>()
            for (row in 0..7) for (col in 0..7) {
                val square = BoardGeometry.squareAt(row, col, orientation)
                seen += square
                assertEquals(Cell(row, col), BoardGeometry.cellOf(square, orientation))
            }
            assertEquals(64, seen.size, "every square appears exactly once")
        }
    }

    @Test fun touchPointsMapToTheSameSquaresAsCells() {
        val size = 800f // 100 px cells
        for (orientation in listOf(true, false)) {
            for (row in 0..7) for (col in 0..7) {
                val expected = BoardGeometry.squareAt(row, col, orientation)
                assertEquals(expected, BoardGeometry.squareAtPoint(col * 100f + 1f, row * 100f + 1f, size, orientation))
                assertEquals(expected, BoardGeometry.squareAtPoint(col * 100f + 50f, row * 100f + 50f, size, orientation))
                assertEquals(expected, BoardGeometry.squareAtPoint(col * 100f + 99.9f, row * 100f + 99.9f, size, orientation))
            }
        }
    }

    @Test fun touchesOutsideTheBoardAreIgnored() {
        assertNull(BoardGeometry.squareAtPoint(-1f, 10f, 800f, true))
        assertNull(BoardGeometry.squareAtPoint(10f, -0.1f, 800f, true))
        assertNull(BoardGeometry.squareAtPoint(800f, 10f, 800f, true))
        assertNull(BoardGeometry.squareAtPoint(10f, 800f, 800f, false))
        assertNull(BoardGeometry.squareAtPoint(10f, 10f, 0f, true))
    }

    @Test fun nonSquareBoardSizesScaleCorrectly() {
        // Same logical tap on a phone-sized board: the top-left cell of the rotated board is h1.
        assertEquals(sq("h1"), BoardGeometry.squareAtPoint(10f, 10f, 360f, whiteAtBottom = false))
        assertEquals(sq("a8"), BoardGeometry.squareAtPoint(10f, 10f, 360f, whiteAtBottom = true))
        assertEquals(sq("a1"), BoardGeometry.squareAtPoint(10f, 350f, 360f, whiteAtBottom = true))
    }

    @Test fun coordinateLabelsFollowTheOrientation() {
        assertEquals("abcdefgh".toList(), BoardGeometry.fileLabels(true))
        assertEquals("hgfedcba".toList(), BoardGeometry.fileLabels(false))
        assertEquals("87654321".toList(), BoardGeometry.rankLabels(true))
        assertEquals("12345678".toList(), BoardGeometry.rankLabels(false))
    }

    @Test fun offBoardCellsAreRejected() {
        assertFailsWith<IllegalArgumentException> { BoardGeometry.squareAt(8, 0, true) }
        assertFailsWith<IllegalArgumentException> { BoardGeometry.squareAt(0, -1, false) }
        assertFailsWith<IllegalArgumentException> { BoardGeometry.cellOf(64, true) }
    }
}
