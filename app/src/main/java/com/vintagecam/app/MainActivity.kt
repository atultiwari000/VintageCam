package com.vintagecam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vintagecam.app.ui.gallery.FilmRollScreen
import com.vintagecam.app.ui.viewfinder.ViewfinderScreen
import com.vintagecam.app.ui.viewfinder.ViewfinderViewModel
import com.vintagecam.app.ui.theme.VintageCamTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VintageCamTheme {
                val viewModel: ViewfinderViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "viewfinder") {
                    composable("viewfinder") {
                        ViewfinderScreen(
                            viewModel = viewModel,
                            onOpenFilmRoll = { navController.navigate("filmroll") },
                        )
                    }
                    composable("filmroll") {
                        FilmRollScreen(
                            photos = uiState.capturedPhotos,
                            onClose = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
