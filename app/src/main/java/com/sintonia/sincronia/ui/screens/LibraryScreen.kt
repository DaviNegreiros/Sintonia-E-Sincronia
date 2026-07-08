package com.sintonia.sincronia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sintonia.sincronia.components.BackButton
import com.sintonia.sincronia.components.DanceCard
import com.sintonia.sincronia.components.Hairline
import com.sintonia.sincronia.ui.theme.SintoniaSurface
import com.sintonia.sincronia.ui.theme.SintoniaSurfaceDeep
import com.sintonia.sincronia.ui.theme.SintoniaText
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted
import com.sintonia.sincronia.viewmodel.DanceLibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: DanceLibraryViewModel,
    onBack: () -> Unit,
    onOpenGame: () -> Unit
) {
    val dances by viewModel.dances.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        when {
            uiState.result != null -> viewModel.closeResult()
            uiState.selectedDance != null -> viewModel.closeOverlay()
            else -> onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Text("Danças Salvas", color = SintoniaText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Hairline()

            if (dances.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma dança salva",
                        color = SintoniaTextMuted.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(dances, key = { it.id }) { dance ->
                        DanceCard(dance = dance, onClick = { viewModel.selectDance(dance) })
                    }
                }
            }
        }

        uiState.selectedDance?.let { dance ->
            if (!uiState.isDancing && uiState.result == null) {
                DanceDetailOverlay(
                    dance = dance,
                    onBack = viewModel::closeOverlay,
                    onDance = {
                        viewModel.startDancing()
                        onOpenGame()
                    },
                    onDelete = viewModel::deleteSelectedDance,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(SintoniaSurface, SintoniaSurfaceDeep, Color(0xFF080014))))
                )
            }
        }

        uiState.result?.let { result ->
            DanceResultOverlay(
                result = result,
                onClose = viewModel::closeResult,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
