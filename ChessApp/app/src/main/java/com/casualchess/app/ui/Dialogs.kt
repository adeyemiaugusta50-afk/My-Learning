package com.casualchess.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casualchess.core.BoardTheme
import com.casualchess.core.Color as ChessColor
import com.casualchess.core.Piece
import com.casualchess.core.PieceType

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(dismissLabel) } },
    )
}

/** Asks which player resigns (both play on one phone, so the app cannot assume it is the player to move). */
@Composable
fun ResignDialog(whiteName: String, blackName: String, onResign: (ChessColor) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who is resigning?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The other player wins the game.")
                Button(onClick = { onResign(ChessColor.WHITE) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("$whiteName (White) resigns")
                }
                Button(onClick = { onResign(ChessColor.BLACK) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("$blackName (Black) resigns")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}

/** The promotion chooser: queen, rook, bishop or knight (underpromotion included). */
@Composable
fun PromotionDialog(color: ChessColor, theme: BoardTheme, onChoose: (PieceType) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Promote pawn to") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (type in PieceType.PROMOTIONS) {
                    val name = type.name.lowercase()
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(theme.darkSquare))
                            .semantics { contentDescription = "Promote to $name" }
                            .clickable(role = Role.Button) { onChoose(type) },
                    ) {
                        PieceView(Piece.of(color, type), Modifier.size(64.dp).padding(2.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}

@Composable
fun ResultDialog(resultText: String, onNewGame: () -> Unit, onRematch: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Game over") },
        text = { Text(resultText) },
        confirmButton = { TextButton(onClick = onRematch, modifier = Modifier.heightIn(min = 48.dp)) { Text("Play again") } },
        dismissButton = {
            Row {
                TextButton(onClick = onNewGame, modifier = Modifier.heightIn(min = 48.dp)) { Text("New game") }
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
            }
        },
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About and licenses") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(LICENSE_NOTICE) } },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") } },
    )
}

/** Small helper used by dialogs and screens for consistent full-width action buttons. */
@Composable
fun WideButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = true) {
    val m = modifier.fillMaxWidth().heightIn(min = 52.dp)
    if (primary) Button(onClick = onClick, modifier = m, enabled = enabled) { Text(text) }
    else OutlinedButton(onClick = onClick, modifier = m, enabled = enabled) { Text(text) }
}

