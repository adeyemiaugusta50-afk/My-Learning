package com.casualchess.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.casualchess.app.ui.CasualChessTheme
import com.casualchess.app.ui.ChessRoot

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CasualChessTheme {
                ChessRoot(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.persistNow() // the game is autosaved after every move; this also covers settled board orientation
    }
}
