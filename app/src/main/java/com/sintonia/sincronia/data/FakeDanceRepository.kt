package com.sintonia.sincronia.data

import com.sintonia.sincronia.domain.Dance
import com.sintonia.sincronia.domain.DanceMetadata
import com.sintonia.sincronia.domain.DanceSource
import com.sintonia.sincronia.domain.Difficulty
import com.sintonia.sincronia.domain.Rank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeDanceRepository : DanceRepository {
    private val _dances = MutableStateFlow(initialDances())
    override val dances: StateFlow<List<Dance>> = _dances.asStateFlow()

    override fun createDance(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        val nextId = (_dances.value.maxOfOrNull { it.id } ?: 0L) + 1L
        val newDance = Dance(
            id = nextId,
            name = trimmedName,
            rank = Rank.UNKNOWN,
            accentColor = 0xFF8B5CF6,
            gradientStart = 0xFF2D1B69,
            gradientEnd = 0xFF140030,
            metadata = DanceMetadata(
                durationSeconds = 0,
                bpm = null,
                difficulty = Difficulty.BEGINNER,
                source = DanceSource.LocalVideo
            )
        )

        _dances.value = listOf(newDance) + _dances.value
    }

    override fun deleteDance(id: Long) {
        _dances.value = _dances.value.filterNot { it.id == id }
    }

    private fun initialDances(): List<Dance> = listOf(
        mockDance(1, "Salsa Cubana", Rank.S, 0xFF9747FF, 0xFF4B0082, 0xFF1A0040, 132),
        mockDance(2, "Tango Argentino", Rank.A_PLUS, 0xFF7C3AED, 0xFF1E0855, 0xFF0D0028, 118),
        mockDance(3, "Forro Universitario", Rank.A, 0xFF8B5CF6, 0xFF2D1B69, 0xFF140030, 124),
        mockDance(4, "Samba de Gafieira", Rank.B, 0xFFA855F7, 0xFF5B0E91, 0xFF240040, 126),
        mockDance(5, "Bachata", Rank.C, 0xFF7C3AED, 0xFF180D4F, 0xFF09001F, 122),
        mockDance(6, "Zouk Brasileiro", Rank.UNKNOWN, 0xFF9333EA, 0xFF3D0F75, 0xFF19003A, 96),
        mockDance(7, "Kizomba", Rank.D, 0xFF8B5CF6, 0xFF270A58, 0xFF0F0026, 92)
    )

    private fun mockDance(
        id: Long,
        name: String,
        rank: Rank,
        accentColor: Long,
        gradientStart: Long,
        gradientEnd: Long,
        bpm: Int
    ) = Dance(
        id = id,
        name = name,
        rank = rank,
        accentColor = accentColor,
        gradientStart = gradientStart,
        gradientEnd = gradientEnd,
        metadata = DanceMetadata(
            durationSeconds = 90,
            bpm = bpm,
            difficulty = Difficulty.INTERMEDIATE,
            source = DanceSource.Mock
        )
    )
}
