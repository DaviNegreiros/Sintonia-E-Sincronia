package com.sintonia.sincronia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sintonia.sincronia.components.BackButton
import com.sintonia.sincronia.components.DanceVideoPlayer
import com.sintonia.sincronia.components.GlowButton
import com.sintonia.sincronia.components.Hairline
import com.sintonia.sincronia.components.SintoniaLogo
import com.sintonia.sincronia.ui.theme.SintoniaPrimary
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted
import com.sintonia.sincronia.viewmodel.NewDanceViewModel

@Composable
fun NewDanceScreen(
    onBack: () -> Unit,
    onVideoSelected: () -> Unit,
    viewModel: NewDanceViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showNameHint by remember { mutableStateOf(false) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            viewModel.selectVideo(it)
            onVideoSelected()
        }
    }

    fun pickVideo() {
        if (uiState.name.trim().isEmpty()) {
            showNameHint = true
            return
        }
        if (uiState.isDuplicateName) {
            showNameHint = false
            return
        }
        showNameHint = false
        videoPicker.launch("video/*")
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            BackButton(onClick = onBack, modifier = Modifier.padding(top = 18.dp, start = 16.dp))
            SintoniaLogo(
                size = 132.dp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Hairline()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = {
                    viewModel.updateName(it)
                    showNameHint = false
                },
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
                sourceUri = uiState.sourceUri,
                onClick = ::pickVideo
            )

            if (showNameHint || uiState.isDuplicateName || uiState.errorMessage != null) {
                Text(
                    text = when {
                        showNameHint -> "Informe o nome da dança antes de escolher o vídeo."
                        uiState.isDuplicateName -> "Esse nome já está em uso."
                        else -> uiState.errorMessage.orEmpty()
                    },
                    color = if (uiState.isDuplicateName || uiState.errorMessage != null) {
                        Color(0xFFFF7A95)
                    } else {
                        SintoniaTextMuted.copy(alpha = 0.78f)
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 34.dp),
                contentAlignment = Alignment.Center
            ) {
                GlowButton(
                    label = "Criar",
                    enabled = uiState.canPickVideo,
                    onClick = ::pickVideo
                )
            }
        }
    }
}

@Composable
private fun VideoImportBox(
    hasVideo: Boolean,
    sourceUri: android.net.Uri?,
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
        if (sourceUri != null) {
            DanceVideoPlayer(
                uri = sourceUri,
                playWhenReady = true,
                muted = true,
                modifier = Modifier.fillMaxSize()
            )
        } else {
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
}
