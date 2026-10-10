package com.casualchess.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casualchess.core.BoardTheme
import com.casualchess.core.Color as ChessColor
import com.casualchess.core.Describe
import com.casualchess.core.DrawClaim
import com.casualchess.core.PieceType
import com.casualchess.core.SessionState

private enum class GameDialog { NONE, RESIGN, DRAW, RESTART }

@Composable
fun GameScreen(
    state: SessionState,
    theme: BoardTheme,
    onSquareTap: (Int) -> Unit,
    onChoosePromotion: (PieceType) -> Unit,
    onCancelPromotion: () -> Unit,
    onFlip: () -> Unit,
    onAutoFlip: (Boolean) -> Unit,
    onResign: (ChessColor) -> Unit,
    onAgreeDraw: () -> Unit,
    onClaimDraw: (DrawClaim) -> Unit,
    onNewGame: () -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(GameDialog.NONE) }
    val result = state.game.result
    // Keyed by the result so a later game that ends the same way shows its dialog again.
    var resultDismissed by rememberSaveable(result) { mutableStateOf(false) }

    val info: @Composable (Boolean) -> Unit = { landscape ->
        GameInfo(
            state = state,
            landscape = landscape,
            onFlip = onFlip,
            onAutoFlip = onAutoFlip,
            onResign = { dialog = GameDialog.RESIGN },
            onOfferDraw = { dialog = GameDialog.DRAW },
            onClaimDraw = onClaimDraw,
            onNewGame = onNewGame,
            onRestart = { if (state.needsLeaveConfirmation) dialog = GameDialog.RESTART else onRestart() },
            onMenu = onMenu,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (maxWidth > maxHeight) {
            // Landscape / wide: board on the left at full height, everything else scrolls on the right.
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChessBoard(state, theme, onSquareTap, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { info(true) }
            }
        } else {
            // Portrait: the board takes the full width; the rest scrolls underneath (works on small phones too).
            val topColor = if (state.whiteAtBottom) ChessColor.BLACK else ChessColor.WHITE
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusText(state)
                PlayerTag(state, topColor)
                ChessBoard(state, theme, onSquareTap, Modifier.fillMaxWidth().aspectRatio(1f))
                PlayerTag(state, topColor.opposite)
                info(false)
            }
        }
    }

    when (dialog) {
        GameDialog.RESIGN -> ResignDialog(
            state.whiteName, state.blackName,
            onResign = { dialog = GameDialog.NONE; onResign(it) },
            onDismiss = { dialog = GameDialog.NONE },
        )
        GameDialog.DRAW -> ConfirmDialog(
            title = "Agree to a draw?",
            message = "Both players agree to end the game as a draw.",
            confirmLabel = "Yes, draw",
            dismissLabel = "Keep playing",
            onConfirm = { dialog = GameDialog.NONE; onAgreeDraw() },
            onDismiss = { dialog = GameDialog.NONE },
        )
        GameDialog.RESTART -> ConfirmDialog(
            title = "Restart this game?",
            message = "The game in progress will be lost and a new one will start with the same players.",
            confirmLabel = "Restart",
            dismissLabel = "Keep playing",
            onConfirm = { dialog = GameDialog.NONE; onRestart() },
            onDismiss = { dialog = GameDialog.NONE },
        )
        GameDialog.NONE -> Unit
    }

    if (state.pendingPromotion != null) {
        PromotionDialog(state.sideToMove, theme, onChoose = onChoosePromotion, onCancel = onCancelPromotion)
    }
    if (result != null && !resultDismissed) {
        ResultDialog(
            resultText = Describe.result(result, state.whiteName, state.blackName),
            onNewGame = { resultDismissed = true; onNewGame() },
            onRematch = { resultDismissed = true; onRestart() },
            onClose = { resultDismissed = true },
        )
    }
}

@Composable
private fun GameInfo(
    state: SessionState,
    landscape: Boolean,
    onFlip: () -> Unit,
    onAutoFlip: (Boolean) -> Unit,
    onResign: () -> Unit,
    onOfferDraw: () -> Unit,
    onClaimDraw: (DrawClaim) -> Unit,
    onNewGame: () -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
) {
    val over = state.game.isOver
    if (landscape) {
        StatusText(state)
        val topColor = if (state.whiteAtBottom) ChessColor.BLACK else ChessColor.WHITE
        PlayerTag(state, topColor)
        PlayerTag(state, topColor.opposite)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlButton("Flip board", onFlip, Modifier.weight(1f))
        ControlButton("Menu", onMenu, Modifier.weight(1f))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = state.autoFlip, role = Role.Switch, onValueChange = onAutoFlip),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Rotate board after every move", Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = state.autoFlip, onCheckedChange = null)
    }
    for (claim in state.game.claimableDraws) {
        WideButton(Describe.claimLabel(claim), onClick = { onClaimDraw(claim) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlButton("Resign", onResign, Modifier.weight(1f), enabled = !over)
        ControlButton("Offer draw", onOfferDraw, Modifier.weight(1f), enabled = !over)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlButton("New game", onNewGame, Modifier.weight(1f))
        ControlButton("Restart", onRestart, Modifier.weight(1f))
    }
    MoveHistory(Describe.historyRows(state.game))
}

@Composable
private fun StatusText(state: SessionState) {
    val over = state.game.isOver
    Text(
        Describe.status(state),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (over || state.game.isCheck) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Name tag for one side, with a marker on the player whose turn it is. */
@Composable
private fun PlayerTag(state: SessionState, color: ChessColor) {
    val toMove = !state.game.isOver && state.sideToMove == color
    val label = "${state.nameOf(color)} (${Describe.colorName(color)})" + if (toMove) ", to move" else ""
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (color == ChessColor.WHITE) Color.White else Color.Black)
                .border(1.dp, Color.Gray, CircleShape),
        )
        Text(
            label,
            Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (toMove) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ControlButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(label, textAlign = TextAlign.Center, maxLines = 2, fontSize = 14.sp)
    }
}

@Composable
private fun MoveHistory(rows: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Moves", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        if (rows.isEmpty()) {
            Text("No moves yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(140.dp)) {
                items(rows) { row -> Text(row, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
    }
}
