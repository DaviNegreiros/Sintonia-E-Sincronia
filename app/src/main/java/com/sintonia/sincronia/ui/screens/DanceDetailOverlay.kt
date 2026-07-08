package com.sintonia.sincronia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sintonia.sincronia.components.BackButton
import com.sintonia.sincronia.components.DancePreview
import com.sintonia.sincronia.components.GlowButton
import com.sintonia.sincronia.components.Hairline
import com.sintonia.sincronia.domain.Dance
import com.sintonia.sincronia.ui.theme.SintoniaDanger
import com.sintonia.sincronia.ui.theme.SintoniaText
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted

@Composable
fun DanceDetailOverlay(
    dance: Dance,
    onBack: () -> Unit,
    onDance: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var playing by remember(dance.id) { mutableStateOf(false) }
    var confirmDelete by remember(dance.id) { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Text(
                    dance.name,
                    modifier = Modifier.weight(1f),
                    color = SintoniaText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0x22FF2D55), RoundedCornerShape(9.dp))
                        .clickable { confirmDelete = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = SintoniaDanger, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                }
            }
            Hairline()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 28.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                DancePreview(
                    dance = dance,
                    playing = playing,
                    modifier = Modifier.width(210.dp),
                    centerContent = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xAA000000), androidx.compose.foundation.shape.CircleShape)
                                .clickable { playing = !playing },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (playing) "II" else "▶", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                GlowButton(label = "Dançar", onClick = onDance)
            }
        }

        if (confirmDelete) {
            DeleteConfirmation(
                danceName = dance.name,
                onCancel = { confirmDelete = false },
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun DeleteConfirmation(
    danceName: String,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD04000E))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A0830), RoundedCornerShape(18.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Apagar dança?", color = SintoniaText, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(
                "Tem certeza que deseja apagar $danceName? Esta ação não pode ser desfeita.",
                color = SintoniaTextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                GlowButton(label = "Cancelar", onClick = onCancel, modifier = Modifier.weight(1f))
                GlowButton(label = "Apagar", onClick = onDelete, destructive = true, modifier = Modifier.weight(1f))
            }
        }
    }
}
