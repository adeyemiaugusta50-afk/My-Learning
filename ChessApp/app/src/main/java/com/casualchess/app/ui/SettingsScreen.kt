package com.casualchess.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casualchess.core.AppSettings
import com.casualchess.core.BoardTheme

@Composable
fun SettingsScreen(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })

        Text("Board appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        for (theme in BoardTheme.entries) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(
                        selected = settings.boardTheme == theme,
                        role = Role.RadioButton,
                        onClick = { onChange { it.copy(boardTheme = theme) } },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = settings.boardTheme == theme, onClick = null)
                Row(Modifier.padding(start = 12.dp)) {
                    Box(Modifier.size(28.dp).background(Color(theme.lightSquare)))
                    Box(Modifier.size(28.dp).background(Color(theme.darkSquare)))
                }
                Text(theme.displayName, Modifier.padding(start = 12.dp))
            }
        }

        Text("Gameplay", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        SwitchRow("Sound effects", settings.soundEnabled) { on -> onChange { it.copy(soundEnabled = on) } }
        SwitchRow("Rotate the board after every move (new games)", settings.autoFlip) { on -> onChange { it.copy(autoFlip = on) } }

        WideButton("Back", onBack, primary = false, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
