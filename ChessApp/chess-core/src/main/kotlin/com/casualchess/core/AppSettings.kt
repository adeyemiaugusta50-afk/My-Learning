package com.casualchess.core

/** Board colour schemes. Colours are 0xAARRGGBB so this stays UI-framework free. */
enum class BoardTheme(
    val id: String,
    val displayName: String,
    val lightSquare: Long,
    val darkSquare: Long,
) {
    CLASSIC_GREEN("green", "Classic green", 0xFFEEEED2, 0xFF769656),
    WOOD("wood", "Wood", 0xFFF0D9B5, 0xFFB58863),
    BLUE("blue", "Ocean blue", 0xFFDEE3E6, 0xFF8CA2AD),
    GRAY("gray", "High contrast gray", 0xFFFFFFFF, 0xFF7A7A7A);

    companion object {
        fun fromId(id: String?): BoardTheme = entries.firstOrNull { it.id == id } ?: CLASSIC_GREEN
    }
}

data class AppSettings(
    val boardTheme: BoardTheme = BoardTheme.CLASSIC_GREEN,
    val soundEnabled: Boolean = true,
    /** Default for new games: rotate the board 180 degrees after every move in two-player mode. */
    val autoFlip: Boolean = true,
)
