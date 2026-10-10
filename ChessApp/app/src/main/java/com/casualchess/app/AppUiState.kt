package com.casualchess.app

import com.casualchess.core.AppSettings
import com.casualchess.core.SessionState

enum class Screen { HOME, SETUP, GAME, SETTINGS }

data class AppUiState(
    val screen: Screen = Screen.HOME,
    val settings: AppSettings = AppSettings(),
    /** The current game (also the autosaved one). Null if there is none. */
    val session: SessionState? = null,
) {
    /** A game that can be resumed from the home screen. */
    val canResume: Boolean get() = session?.game?.isOver == false
}
