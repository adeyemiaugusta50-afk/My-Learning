package com.casualchess.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.casualchess.app.AppUiState
import com.casualchess.core.MAX_NAME_LENGTH

@Composable
fun SetupScreen(
    ui: AppUiState,
    onStart: (whiteName: String, blackName: String, autoFlip: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var white by rememberSaveable { mutableStateOf("White") }
    var black by rememberSaveable { mutableStateOf("Black") }
    var autoFlip by rememberSaveable { mutableStateOf(ui.settings.autoFlip) }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val inProgress = ui.session?.needsLeaveConfirmation == true

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("New game", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })

        Text("Mode", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = true, role = Role.RadioButton, onClick = {}),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = true, onClick = null)
            Text("Two players on one phone", Modifier.padding(start = 8.dp))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = false, onClick = null, enabled = false)
            Text("Against the computer (coming soon)", Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.outline)
        }

        Text("Players", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = white,
            onValueChange = { white = it.take(MAX_NAME_LENGTH) },
            label = { Text("White player") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = black,
            onValueChange = { black = it.take(MAX_NAME_LENGTH) },
            label = { Text("Black player") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = autoFlip, role = Role.Switch, onValueChange = { autoFlip = it }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Rotate the board after every move", Modifier.weight(1f))
            Switch(checked = autoFlip, onCheckedChange = null)
        }

        WideButton("Start game", onClick = { if (inProgress) confirmReplace = true else onStart(white, black, autoFlip) })
        WideButton("Back", onBack, primary = false)
    }

    if (confirmReplace) {
        ConfirmDialog(
            title = "Discard the game in progress?",
            message = "Starting a new game will replace the unfinished game (${ui.session?.whiteName} vs ${ui.session?.blackName}). " +
                "This cannot be undone.",
            confirmLabel = "Discard and start",
            dismissLabel = "Keep playing",
            onConfirm = { confirmReplace = false; onStart(white, black, autoFlip) },
            onDismiss = { confirmReplace = false },
        )
    }
}
