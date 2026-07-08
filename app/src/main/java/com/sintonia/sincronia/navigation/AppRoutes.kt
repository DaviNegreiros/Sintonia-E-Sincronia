package com.sintonia.sincronia.navigation

sealed class AppRoute(val route: String) {
    data object Home : AppRoute("home")
    data object NewDance : AppRoute("nova-danca")
    data object Library : AppRoute("biblioteca")
    data object Game : AppRoute("jogo")
}
