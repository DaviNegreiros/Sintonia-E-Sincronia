package com.sintonia.sincronia.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sintonia.sincronia.components.BackButton
import com.sintonia.sincronia.components.GlowButton
import com.sintonia.sincronia.components.Hairline
import com.sintonia.sincronia.components.SintoniaLogo
import com.sintonia.sincronia.ui.theme.SintoniaPrimary
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted
import com.sintonia.sincronia.viewmodel.NewDanceViewModel

@Composable
fun NewDanceScreen(
    onBack: () -> Unit,
    onCreateDance: (String) -> Unit,
    viewModel: NewDanceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
        ) {
            BackButton(onClick = onBack, modifier = Modifier.padding(16.dp))
            SintoniaLogo(
                size = 132.dp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Hairline()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                placeholder = { Text("Nome da dança", color = SintoniaTextMuted.copy(alpha = 0.6f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = SintoniaPrimary,
                    unfocusedBorderColor = Color(0x557C3AED),
                    cursorColor = SintoniaPrimary,
                    focusedContainerColor = Color(0x127C3AED),
                    unfocusedContainerColor = Color(0x127C3AED)
                )
            )

            VideoImportBox(
                hasVideo = uiState.hasVideo,
                onClick = viewModel::markVideoSelected
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            GlowButton(
                label = "Criar",
                enabled = uiState.canCreate,
                onClick = {
                    onCreateDance(uiState.name)
                    viewModel.reset()
                }
            )
        }
    }
}

@Composable
private fun VideoImportBox(
    hasVideo: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(178.dp)
            .height(316.dp)
            .background(Color(0x107C3AED), RoundedCornerShape(16.dp))
            .border(
                BorderStroke(2.dp, if (hasVideo) Color(0x997C3AED) else Color(0x777C3AED)),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .background(Color(0x337C3AED), CircleShape)
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (hasVideo) "✓" else "+", color = SintoniaTextMuted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = if (hasVideo) "Vídeo selecionado" else "Adicionar vídeo",
                color = SintoniaTextMuted.copy(alpha = 0.72f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}
