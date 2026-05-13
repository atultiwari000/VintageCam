package com.vintagecam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import com.vintagecam.app.ui.gallery.GalleryScreen
import com.vintagecam.app.ui.viewfinder.ViewfinderScreen
import com.vintagecam.app.ui.viewfinder.ViewfinderViewModel
import com.vintagecam.app.ui.theme.VintageCamTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            setContentForPermission(isGranted)
        }

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                setContentForPermission(true)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun setContentForPermission(isGranted: Boolean) {
        setContent {
            VintageCamTheme {
                if (isGranted) {
                    val viewModel: ViewfinderViewModel = hiltViewModel()
                    val uiState by viewModel.uiState.collectAsState()
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "viewfinder") {
                        composable("viewfinder") {
                            ViewfinderScreen(
                                viewModel = viewModel,
                                onOpenFilmRoll = { navController.navigate("gallery") },
                            )
                        }
                        composable("gallery") {
                            GalleryScreen(
                                onClose = { navController.popBackStack() },
                            )
                        }
                    }
                } else {
                    PermissionDeniedScreen()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun PermissionDeniedScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Camera permission is required to use VintageCam")
        }
    }
}
