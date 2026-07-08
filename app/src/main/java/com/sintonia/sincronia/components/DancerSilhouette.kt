package com.sintonia.sincronia.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun DancerSilhouette(
    color: Color,
    modifier: Modifier = Modifier,
    alpha: Float = 0.2f
) {
    Canvas(modifier = modifier.size(width = 96.dp, height = 134.dp)) {
        val stroke = Stroke(width = 9f, cap = StrokeCap.Round)
        val c = color.copy(alpha = alpha)

        drawCircle(c, radius = 14f, center = Offset(size.width * 0.54f, size.height * 0.14f))
        drawLine(c, Offset(size.width * 0.53f, size.height * 0.25f), Offset(size.width * 0.44f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(c, Offset(size.width * 0.50f, size.height * 0.34f), Offset(size.width * 0.20f, size.height * 0.25f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(c, Offset(size.width * 0.50f, size.height * 0.34f), Offset(size.width * 0.80f, size.height * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(c, Offset(size.width * 0.44f, size.height * 0.50f), Offset(size.width * 0.27f, size.height * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(c, Offset(size.width * 0.44f, size.height * 0.50f), Offset(size.width * 0.70f, size.height * 0.80f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}
