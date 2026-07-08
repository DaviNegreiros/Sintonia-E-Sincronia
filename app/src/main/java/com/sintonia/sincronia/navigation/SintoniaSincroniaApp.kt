package com.sintonia.sincronia.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sintonia.sincronia.data.AppContainer
import com.sintonia.sincronia.ui.screens.DancingOverlay
import com.sintonia.sincronia.ui.screens.HomeScreen
import com.sintonia.sincronia.ui.screens.LibraryScreen
import com.sintonia.sincronia.ui.screens.NewDanceScreen
import com.sintonia.sincronia.ui.theme.SintoniaBackground
import com.sintonia.sincronia.ui.theme.SintoniaSurface
import com.sintonia.sincronia.ui.theme.SintoniaSurfaceDeep
import com.sintonia.sincronia.viewmodel.DanceLibraryViewModel
import com.sintonia.sincronia.viewmodel.DanceLibraryViewModelFactory

@Composable
fun SintoniaSincroniaApp() {
    val navController = rememberNavController()
    val repository = remember { AppContainer.danceRepository }
    val factory = remember(repository) { DanceLibraryViewModelFactory(repository) }
    val owner = LocalContext.current as androidx.lifecycle.ViewModelStoreOwner
    val libraryViewModel: DanceLibraryViewModel = viewModel(
        viewModelStoreOwner = owner,
        factory = factory
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SintoniaSurface, SintoniaSurfaceDeep, SintoniaBackground)
                )
            )
    ) {
        NavHost(
            navController = navController,
            startDestination = AppRoute.Home.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppRoute.Home.route) {
                HomeScreen(
                    onNewDance = { navController.navigate(AppRoute.NewDance.route) },
                    onLibrary = { navController.navigate(AppRoute.Library.route) }
                )
            }

            composable(AppRoute.NewDance.route) {
                NewDanceScreen(
                    onBack = { navController.popBackStack() },
                    onCreateDance = { name ->
                        libraryViewModel.createDance(name)
                        navController.navigate(AppRoute.Library.route) {
                            popUpTo(AppRoute.Home.route)
                        }
                    }
                )
            }

            composable(AppRoute.Library.route) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenGame = { navController.navigate(AppRoute.Game.route) }
                )
            }

            composable(AppRoute.Game.route) {
                val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
                uiState.selectedDance?.let { dance ->
                    DancingOverlay(
                        dance = dance,
                        onClose = {
                            libraryViewModel.closeOverlay()
                            navController.popBackStack(AppRoute.Library.route, inclusive = false)
                        }
                    )
                } ?: HomeScreen(
                    onNewDance = { navController.navigate(AppRoute.NewDance.route) },
                    onLibrary = { navController.navigate(AppRoute.Library.route) }
                )
            }
        }
    }
}
