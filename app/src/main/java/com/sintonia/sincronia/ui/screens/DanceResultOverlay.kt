package com.sintonia.sincronia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sintonia.sincronia.components.GlowButton
import com.sintonia.sincronia.components.RankBadge
import com.sintonia.sincronia.components.rankTextColor
import com.sintonia.sincronia.domain.DanceResult
import com.sintonia.sincronia.ui.theme.SintoniaPrimary
import com.sintonia.sincronia.ui.theme.SintoniaText
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted

@Composable
fun DanceResultOverlay(
    result: DanceResult,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xDD04000E))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .background(Color(0xFF210D43), RoundedCornerShape(20.dp))
                .border(1.dp, SintoniaPrimary.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(SintoniaPrimary.copy(alpha = 0.28f), RoundedCornerShape(9.dp))
                        .border(1.dp, SintoniaPrimary.copy(alpha = 0.44f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("|||", color = SintoniaText, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Text("Resultados", color = SintoniaText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }

            ResultRow(label = "Dança", value = result.danceName)
            ResultRow(label = "Ranque") {
                RankBadge(rank = result.rank)
            }
            ResultRow(label = "Porcentagem de sucesso") {
                Text(
                    text = "${result.successPercentage.coerceIn(0, 100)}%",
                    color = rankTextColor(result.rank),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }

            GlowButton(
                label = "Fechar",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String
) {
    ResultRow(label = label) {
        Text(
            text = value,
            color = SintoniaText,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ResultRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2B1152), RoundedCornerShape(11.dp))
            .border(1.dp, SintoniaPrimary.copy(alpha = 0.28f), RoundedCornerShape(11.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = SintoniaTextMuted.copy(alpha = 0.72f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
        content()
    }
}
