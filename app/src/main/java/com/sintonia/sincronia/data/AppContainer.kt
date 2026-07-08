package com.sintonia.sincronia.data

object AppContainer {
    val danceRepository: DanceRepository by lazy { FakeDanceRepository() }
}
