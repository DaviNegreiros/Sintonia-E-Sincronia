package com.sintonia.sincronia.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sintonia.sincronia.domain.Rank

@Composable
fun RankBadge(rank: Rank, modifier: Modifier = Modifier) {
    val style = rankStyle(rank)
    Text(
        text = rank.label,
        color = style.text,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
        modifier = modifier
            .background(style.background, RoundedCornerShape(5.dp))
            .border(1.dp, style.border, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

private data class RankStyle(val text: Color, val background: Color, val border: Color)

fun rankTextColor(rank: Rank): Color = rankStyle(rank).text

private fun rankStyle(rank: Rank): RankStyle = when (rank) {
    Rank.S -> RankStyle(Color(0xFFFDE68A), Color(0x2EFBBD24), Color(0x80FBBB24))
    Rank.A_PLUS -> RankStyle(Color(0xFFFB923C), Color(0x2EFB923C), Color(0x80FB923C))
    Rank.A -> RankStyle(Color(0xFF86EFAC), Color(0x2E4ADE80), Color(0x804ADE80))
    Rank.B -> RankStyle(Color(0xFF93C5FD), Color(0x2E60A5FA), Color(0x8060A5FA))
    Rank.C -> RankStyle(Color(0xFFC4B5FD), Color(0x2EA78BFA), Color(0x80A78BFA))
    Rank.F -> RankStyle(Color(0xFFF87171), Color(0x26EF4444), Color(0x73EF4444))
    Rank.UNKNOWN -> RankStyle(Color(0xFF94A3B8), Color(0x1F94A3B8), Color(0x5994A3B8))
}
