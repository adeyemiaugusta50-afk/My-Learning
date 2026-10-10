package com.casualchess.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.runtime.Composable
import com.casualchess.core.Piece

private class ParsedShape(val path: Path, val shape: PieceShape)

/** Paths are parsed once, on first use, and shared by every square. */
private val parsedPieces: Map<Char, List<ParsedShape>> by lazy {
    PIECE_ART.mapValues { (_, shapes) ->
        shapes.map { ParsedShape(PathParser().parsePathString(it.pathData).toPath(), it) }
    }
}

private fun cap(code: Int) = when (code) {
    1 -> StrokeCap.Round
    2 -> StrokeCap.Square
    else -> StrokeCap.Butt
}

private fun join(code: Int) = when (code) {
    1 -> StrokeJoin.Round
    2 -> StrokeJoin.Bevel
    else -> StrokeJoin.Miter
}

/** Draws a chess piece (Chessnut set, Apache-2.0) scaled to fill its box. Pieces are always drawn upright. */
@Composable
fun PieceView(piece: Piece, modifier: Modifier = Modifier) {
    val shapes = parsedPieces.getValue(piece.fenChar)
    Canvas(modifier) {
        val factor = size.minDimension / PIECE_VIEWPORT
        scale(factor, factor, pivot = Offset.Zero) {
            for (parsed in shapes) {
                val s = parsed.shape
                s.fill?.let { drawPath(parsed.path, Color(it)) }
                s.stroke?.let {
                    drawPath(
                        parsed.path,
                        Color(it),
                        style = Stroke(width = s.strokeWidth, miter = s.miterLimit, cap = cap(s.cap), join = join(s.join)),
                    )
                }
            }
        }
    }
}
