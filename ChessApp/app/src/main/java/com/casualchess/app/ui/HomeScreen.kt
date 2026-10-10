package com.casualchess.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.casualchess.app.AppUiState

@Composable
fun HomeScreen(
    ui: AppUiState,
    onResume: () -> Unit,
    onNewGame: () -> Unit,
    onSettings: () -> Unit,
) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            "Casual Chess",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
        )
        Text(
            "Two players, one phone. Works fully offline.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))

        ui.session?.takeIf { ui.canResume }?.let { session ->
            WideButton("Resume game", onResume)
            Text(
                "${session.whiteName} vs ${session.blackName}, move ${session.game.position.fullmoveNumber}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        WideButton("New two-player game", onNewGame, primary = !ui.canResume)
        WideButton("Play the computer (coming soon)", onClick = {}, enabled = false, primary = false)
        WideButton("Settings", onSettings, primary = false)
        WideButton("About and licenses", { showAbout = true }, primary = false)
    }
    if (showAbout) AboutDialog { showAbout = false }
}
