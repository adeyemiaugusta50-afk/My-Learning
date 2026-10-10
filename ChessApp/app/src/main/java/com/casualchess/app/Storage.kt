package com.casualchess.app

import android.content.Context
import com.casualchess.core.AppSettings
import com.casualchess.core.BoardTheme
import com.casualchess.core.GameCodec
import com.casualchess.core.SavedSession

/**
 * Local persistence in app-private SharedPreferences. Nothing leaves the device.
 * The game is stored in [GameCodec]'s text format (start position + moves), which keeps every rule-relevant detail.
 */
class Storage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings = AppSettings(
        boardTheme = BoardTheme.fromId(prefs.getString(KEY_THEME, null)),
        soundEnabled = prefs.getBoolean(KEY_SOUND, true),
        autoFlip = prefs.getBoolean(KEY_AUTO_FLIP, true),
    )

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_THEME, settings.boardTheme.id)
            .putBoolean(KEY_SOUND, settings.soundEnabled)
            .putBoolean(KEY_AUTO_FLIP, settings.autoFlip)
            .apply()
    }

    /** The autosaved game, or null if there is none or the file is unreadable (a corrupt save is ignored, not fatal). */
    fun loadGame(): SavedSession? = prefs.getString(KEY_GAME, null)?.let { GameCodec.decode(it) }

    fun saveGame(session: SavedSession?) {
        val editor = prefs.edit()
        if (session == null) editor.remove(KEY_GAME) else editor.putString(KEY_GAME, GameCodec.encode(session))
        editor.apply()
    }

    private companion object {
        const val FILE = "casual_chess"
        const val KEY_GAME = "game_v1"
        const val KEY_THEME = "board_theme"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_AUTO_FLIP = "auto_flip"
    }
}
