package com.sintonia.sincronia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sintonia.sincronia.components.Hairline
import com.sintonia.sincronia.components.PillButton
import com.sintonia.sincronia.components.SintoniaLogo
import com.sintonia.sincronia.ui.theme.SintoniaPrimary
import com.sintonia.sincronia.ui.theme.SintoniaText
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted

@Composable
fun HomeScreen(
    onNewDance: () -> Unit,
    onLibrary: () -> Unit
) {
    var showTips by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x228B5CF6), Color.Transparent)
                    )
                )
        )

        FloatingNotes()

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.28f),
                contentAlignment = Alignment.Center
            ) {
                SintoniaLogo(size = 210.dp)
            }
            Hairline()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.72f)
                    .padding(horizontal = 34.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color(0x177C3AED), CircleShape)
                            .clickable { showTips = !showTips },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("i", color = SintoniaTextMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    PillButton(label = "Nova Dança", leading = "▶", primary = true, onClick = onNewDance, modifier = Modifier.weight(1f))
                }
                if (showTips) {
                    InfoPopover()
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(modifier = Modifier.size(30.dp))
                    PillButton(label = "Danças Salvas", leading = "▦", primary = false, onClick = onLibrary, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FloatingNotes() {
    val notes = listOf(
        Triple(0.08f, 0.07f, "♪"),
        Triple(0.14f, 0.82f, "♫"),
        Triple(0.34f, 0.05f, "♪"),
        Triple(0.32f, 0.86f, "♬"),
        Triple(0.62f, 0.10f, "♫"),
        Triple(0.68f, 0.78f, "♪")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        notes.forEach { (top, start, symbol) ->
            Text(
                text = symbol,
                color = SintoniaPrimary.copy(alpha = 0.22f),
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = (top * 720).dp, start = (start * 360).dp)
            )
        }
    }
}

@Composable
private fun InfoPopover() {
    Column(
        modifier = Modifier
            .padding(start = 38.dp, end = 4.dp)
            .background(Color(0xEE160730), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Dicas de gravação", color = SintoniaText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        listOf(
            "Use boa iluminação.",
            "Mantenha o corpo inteiro no quadro.",
            "Grave na vertical.",
            "Evite fundos muito movimentados."
        ).forEach {
            Text(it, color = SintoniaTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}
