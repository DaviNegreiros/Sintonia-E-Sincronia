package com.sintonia.sincronia.data

import com.sintonia.sincronia.domain.Dance
import kotlinx.coroutines.flow.StateFlow

interface DanceRepository {
    val dances: StateFlow<List<Dance>>

    fun createDance(name: String)

    fun deleteDance(id: Long)
}
