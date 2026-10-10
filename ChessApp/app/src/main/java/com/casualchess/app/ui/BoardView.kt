package com.casualchess.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.casualchess.core.BoardGeometry
import com.casualchess.core.BoardTheme
import com.casualchess.core.Describe
import com.casualchess.core.SessionState
import com.casualchess.core.Squares

private val LAST_MOVE = Color(0x77F7E26B)
private val SELECTED = Color(0x8800B0FF)
private val TARGET_MARK = Color(0x66000000)

/**
 * The chess board: an 8x8 grid of tappable squares. Both drawing and touch use [BoardGeometry] with the state's
 * current orientation, so after a 180 degree rotation a tap always lands on the square that is shown under the finger.
 * Pieces are never rotated, only their positions are.
 */
@Composable
fun ChessBoard(
    state: SessionState,
    theme: BoardTheme,
    onSquareTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val light = Color(theme.lightSquare)
    val dark = Color(theme.darkSquare)
    val targets = state.targets
    val lastMove = state.lastMove
    val checked = state.checkedKingSquare
    val files = BoardGeometry.fileLabels(state.whiteAtBottom)
    val ranks = BoardGeometry.rankLabels(state.whiteAtBottom)

    BoxWithConstraints(modifier) {
        val labelSize: TextUnit = with(LocalDensity.current) { (maxWidth / 8 * 0.2f).toSp() }
        Column(Modifier.fillMaxSize()) {
            for (row in 0..7) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0..7) {
                        val square = BoardGeometry.squareAt(row, col, state.whiteAtBottom)
                        val isLightSquare = !Squares.isDark(square)
                        val piece = state.game.position.pieceAt(square)
                        val isSelected = square == state.selected
                        val isTarget = square in targets
                        val description = Describe.square(state, square)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (isLightSquare) light else dark)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = description
                                    this.selected = isSelected
                                }
                                .clickable(role = Role.Button) { onSquareTap(square) },
                        ) {
                            if (lastMove != null && (square == lastMove.from || square == lastMove.to)) {
                                Box(Modifier.fillMaxSize().background(LAST_MOVE))
                            }
                            if (isSelected) Box(Modifier.fillMaxSize().background(SELECTED))
                            if (square == checked) {
                                Box(
                                    Modifier.fillMaxSize().background(
                                        Brush.radialGradient(listOf(Color(0xFFFF1F1F), Color(0x00FF1F1F))),
                                    ),
                                )
                            }
                            if (piece != null) PieceView(piece, Modifier.fillMaxSize().padding(1.dp))
                            if (isTarget) {
                                if (piece == null) {
                                    Box(
                                        Modifier
                                            .align(Alignment.Center)
                                            .fillMaxSize(0.3f)
                                            .clip(CircleShape)
                                            .background(TARGET_MARK),
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .align(Alignment.Center)
                                            .fillMaxSize(0.92f)
                                            .border(3.dp, TARGET_MARK, CircleShape),
                                    )
                                }
                            }
                            val labelColor = if (isLightSquare) dark else light
                            if (col == 0) {
                                Text(
                                    ranks[row].toString(),
                                    Modifier.align(Alignment.TopStart).padding(2.dp),
                                    color = labelColor,
                                    fontSize = labelSize,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (row == 7) {
                                Text(
                                    files[col].toString(),
                                    Modifier.align(Alignment.BottomEnd).padding(2.dp),
                                    color = labelColor,
                                    fontSize = labelSize,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
