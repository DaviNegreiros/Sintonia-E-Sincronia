package com.sintonia.sincronia.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sintonia.sincronia.R

@Composable
fun SintoniaLogo(modifier: Modifier = Modifier, size: Dp = 190.dp) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size * 0.95f)
                .blur(28.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x667C3AED), Color.Transparent)
                    )
                )
        )
        Image(
            painter = painterResource(R.drawable.ss_logo),
            contentDescription = "Sintonia & Sincronia",
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0x667C3AED), Color.Transparent)
                )
            )
    )
}
