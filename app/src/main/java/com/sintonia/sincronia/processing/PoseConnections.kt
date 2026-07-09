package com.sintonia.sincronia.processing

object PoseConnections {
    val connections: List<Pair<Int, Int>> = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 7,
        0 to 4, 4 to 5, 5 to 6, 6 to 8,
        9 to 10,
        11 to 12, 11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
        12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
        11 to 23, 12 to 24, 23 to 24,
        23 to 25, 24 to 26, 25 to 27, 26 to 28,
        27 to 29, 28 to 30, 29 to 31, 30 to 32, 27 to 31, 28 to 32
    )
}

