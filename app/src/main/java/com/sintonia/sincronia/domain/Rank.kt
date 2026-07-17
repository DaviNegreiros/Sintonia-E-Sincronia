package com.sintonia.sincronia.domain

enum class Rank(val label: String, val quality: Int) {
    S("SS!", 7),
    A_PLUS("S", 6),
    A("A", 5),
    B("B", 4),
    C("C", 3),
    F("F", 1),
    UNKNOWN("?", 0)
}
