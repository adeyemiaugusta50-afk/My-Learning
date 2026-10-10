package com.casualchess.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI tests. They need a device or emulator: `./gradlew :app:connectedDebugAndroidTest`.
 *
 * NOT RUN in the environment where this project was written (no Android SDK / emulator available there).
 * Chess correctness is covered by the JVM tests in `chess-core`; these tests only check the Android wiring:
 * navigation, tapping squares, board rotation and recreation of the activity.
 */
class GameFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Starts a fresh game whatever the saved state of previous runs was. */
    private fun startNewGame() {
        compose.onNodeWithText("New two-player game").performClick()
        compose.onNodeWithText("Start game").performClick()
        // If an unfinished game was saved, the app asks before discarding it.
        if (compose.onAllNodesWithText("Discard and start").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Discard and start").performClick()
        }
    }

    private fun top(description: String): Float =
        compose.onAllNodesWithContentDescription(description, substring = true)
            .fetchSemanticsNodes().first().boundsInRoot.top

    @Test
    fun playingAMoveRotatesTheBoardAfterAShortDelay() {
        startNewGame()
        val whiteKingBefore = top("e1, white king")
        compose.onNodeWithContentDescription("e2, white pawn").performClick()
        compose.onNodeWithContentDescription("e4, empty, legal move").performClick()

        // Auto-rotation happens after FLIP_DELAY_MS: the white king (bottom row) moves to the top row.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("e1, white king", substring = true).fetchSemanticsNodes()
                .firstOrNull()?.boundsInRoot?.top?.let { it < whiteKingBefore } == true
        }
        assertTrue(top("e1, white king") < whiteKingBefore)
        // The completed move is still highlighted after the rotation.
        compose.onNodeWithContentDescription("e4, white pawn, last move").assertExists()
    }

    @Test
    fun gameSurvivesRecreationOfTheActivity() {
        startNewGame()
        compose.onNodeWithContentDescription("g1, white knight").performClick()
        compose.onNodeWithContentDescription("f3, empty, legal move").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        // After recreation (e.g. rotating the phone) the position is intact.
        compose.onNodeWithContentDescription("f3, white knight", substring = true).assertExists()
    }
}
