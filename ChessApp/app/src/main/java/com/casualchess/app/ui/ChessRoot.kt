package com.casualchess.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.casualchess.app.AppViewModel
import com.casualchess.app.Screen

/** Root composable: picks the screen from the ViewModel state. All game logic lives in `chess-core`. */
@Composable
fun ChessRoot(vm: AppViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    BackHandler(enabled = ui.screen != Screen.HOME) { vm.back() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val session = ui.session
        when {
            ui.screen == Screen.SETUP -> SetupScreen(ui, onStart = vm::startNewGame, onBack = vm::goHome)
            ui.screen == Screen.SETTINGS -> SettingsScreen(ui.settings, onChange = vm::updateSettings, onBack = vm::goHome)
            ui.screen == Screen.GAME && session != null -> GameScreen(
                state = session,
                theme = ui.settings.boardTheme,
                onSquareTap = vm::onSquareTapped,
                onChoosePromotion = vm::onChoosePromotion,
                onCancelPromotion = vm::onCancelPromotion,
                onFlip = vm::flipBoard,
                onAutoFlip = vm::setGameAutoFlip,
                onResign = vm::resign,
                onAgreeDraw = vm::agreeDraw,
                onClaimDraw = vm::claimDraw,
                onNewGame = vm::openSetup,
                onRestart = vm::restartGame,
                onMenu = vm::goHome,
            )
            else -> HomeScreen(ui, onResume = vm::resume, onNewGame = vm::openSetup, onSettings = vm::openSettings)
        }
    }
}
