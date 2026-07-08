package com.sintonia.sincronia.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sintonia.sincronia.ui.theme.SintoniaPrimary
import com.sintonia.sincronia.ui.theme.SintoniaPrimaryDark
import com.sintonia.sincronia.ui.theme.SintoniaText
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .border(1.dp, Color(0x337C3AED), RoundedCornerShape(9.dp))
            .background(Color(0x147C3AED), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("<", color = SintoniaText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PillButton(
    label: String,
    leading: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (primary) {
        Brush.horizontalGradient(listOf(SintoniaPrimaryDark, SintoniaPrimary))
    } else {
        Brush.horizontalGradient(listOf(Color(0x332D1B69), Color(0x227C3AED)))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (primary) 18.dp else 0.dp, RoundedCornerShape(26.dp), clip = false)
            .background(background, RoundedCornerShape(26.dp))
            .border(1.dp, Color(0x4DA78BFA), RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(leading, color = SintoniaText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, color = SintoniaText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(">", color = SintoniaTextMuted, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GlowButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val colors = if (destructive) {
        ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D55), disabledContainerColor = Color(0x33FF2D55))
    } else {
        ButtonDefaults.buttonColors(containerColor = SintoniaPrimary, disabledContainerColor = Color(0x337C3AED))
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, if (enabled) Color(0x55A78BFA) else Color(0x337C3AED)),
        colors = colors,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 44.dp, vertical = 13.dp)
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color(0x66C4B5FD),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
    }
}
