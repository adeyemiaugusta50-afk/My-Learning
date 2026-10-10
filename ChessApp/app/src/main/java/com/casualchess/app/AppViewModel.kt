package com.casualchess.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.casualchess.core.AppSettings
import com.casualchess.core.Color
import com.casualchess.core.DrawClaim
import com.casualchess.core.FLIP_DELAY_MS
import com.casualchess.core.PieceType
import com.casualchess.core.SavedSession
import com.casualchess.core.SessionEvent
import com.casualchess.core.SessionState
import com.casualchess.core.Step
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the game, the settings and the board-rotation timer. It survives configuration changes (rotation, resizing,
 * dark-mode switches); after process death the game is restored from the autosave via [Storage].
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = Storage(application)
    private val sounds = SoundPlayer(application)

    private val _ui = MutableStateFlow(
        AppUiState(settings = storage.loadSettings(), session = storage.loadGame()?.toState()),
    )
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private var flipJob: Job? = null

    // ---- navigation ------------------------------------------------------------------------------------------

    fun openSetup() = _ui.update { it.copy(screen = Screen.SETUP) }
    fun openSettings() = _ui.update { it.copy(screen = Screen.SETTINGS) }
    fun goHome() = _ui.update { it.copy(screen = Screen.HOME) }

    /** System back: always returns to the home screen; the game stays autosaved. */
    fun back() = goHome()

    fun resume() {
        if (_ui.value.session != null) _ui.update { it.copy(screen = Screen.GAME) }
    }

    // ---- game lifecycle --------------------------------------------------------------------------------------

    fun startNewGame(whiteName: String, blackName: String, autoFlip: Boolean) {
        cancelFlip()
        val session = SessionState.newGame(whiteName, blackName, autoFlip)
        _ui.update { it.copy(session = session, screen = Screen.GAME) }
        persistNow()
    }

    /** Same players and settings, fresh board. The UI asks for confirmation first if a game is in progress. */
    fun restartGame() {
        val current = _ui.value.session ?: return
        startNewGame(current.whiteName, current.blackName, current.autoFlip)
    }

    // ---- playing ---------------------------------------------------------------------------------------------

    fun onSquareTapped(square: Int) = act { it.tap(square) }

    fun onChoosePromotion(type: PieceType) = act { it.choosePromotion(type) }

    fun onCancelPromotion() = mutate { it.cancelPromotion() }

    fun flipBoard() {
        cancelFlip()
        mutate { it.flipManually() }
        persistNow()
    }

    fun setGameAutoFlip(enabled: Boolean) {
        if (!enabled) cancelFlip()
        mutate { it.withAutoFlip(enabled) }
        persistNow()
    }

    fun resign(color: Color) {
        cancelFlip()
        mutate { it.resign(color) }
        gameEnded()
    }

    fun agreeDraw() {
        cancelFlip()
        mutate { it.agreeDraw() }
        gameEnded()
    }

    fun claimDraw(claim: DrawClaim) {
        cancelFlip()
        mutate { it.claimDraw(claim) }
        gameEnded()
    }

    // ---- settings --------------------------------------------------------------------------------------------

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val next = change(_ui.value.settings)
        _ui.update { it.copy(settings = next) }
        storage.saveSettings(next)
    }

    // ---- persistence -----------------------------------------------------------------------------------------

    /** Writes the autosave. Also called when the activity stops, so nothing is lost if the process is killed. */
    fun persistNow() {
        storage.saveGame(_ui.value.session?.let { SavedSession.from(it) })
    }

    // ---- internals -------------------------------------------------------------------------------------------

    private fun mutate(change: (SessionState) -> SessionState) {
        _ui.update { ui -> ui.session?.let { ui.copy(session = change(it)) } ?: ui }
    }

    private fun act(action: (SessionState) -> Step) {
        val session = _ui.value.session ?: return
        val step = action(session)
        _ui.update { it.copy(session = step.state) }
        when (val event = step.event) {
            is SessionEvent.MovePlayed -> {
                playSound(
                    when {
                        event.result != null -> SoundCue.GAME_END
                        event.record.isCapture -> SoundCue.CAPTURE
                        else -> SoundCue.MOVE
                    },
                )
                persistNow()
                scheduleFlip(step.state)
            }
            SessionEvent.FlippedEarly -> {
                cancelFlip()
                persistNow()
            }
            else -> Unit
        }
    }

    /** Keeps the completed move on screen for a moment, then rotates the board (unless something changed meanwhile). */
    private fun scheduleFlip(state: SessionState) {
        val ply = state.flipPendingAtPly ?: return
        cancelFlip()
        flipJob = viewModelScope.launch {
            delay(FLIP_DELAY_MS)
            _ui.update { ui -> ui.session?.let { ui.copy(session = it.finishFlip(expectedPly = ply)) } ?: ui }
            persistNow()
        }
    }

    private fun cancelFlip() {
        flipJob?.cancel()
        flipJob = null
    }

    private fun gameEnded() {
        playSound(SoundCue.GAME_END)
        persistNow()
    }

    private fun playSound(cue: SoundCue) {
        if (_ui.value.settings.soundEnabled) sounds.play(cue)
    }

    override fun onCleared() {
        cancelFlip()
        persistNow()
        sounds.release()
    }
}
