package com.sintonia.sincronia.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

class PoseSkeletonDrawer(
    private val visibilityThreshold: Float = 0.35f,
    connectionColor: Int = Color.rgb(0, 255, 0),
    landmarkColor: Int = Color.rgb(255, 128, 0),
    connectionThickness: Float = 2f,
    private val landmarkRadius: Float = 4f
) {
    private val connectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = connectionColor
        strokeWidth = connectionThickness
        style = Paint.Style.STROKE
    }
    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = landmarkColor
        style = Paint.Style.FILL
    }

    fun draw(source: Bitmap, landmarks: List<PoseLandmark>, width: Int, height: Int): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, null, Rect(0, 0, width, height), null)
        if (landmarks.isEmpty()) return output

        for ((startIndex, endIndex) in PoseConnections.connections) {
            val start = landmarks[startIndex]
            val end = landmarks[endIndex]
            if (start.visibility >= visibilityThreshold && end.visibility >= visibilityThreshold) {
                canvas.drawLine(
                    start.x * width,
                    start.y * height,
                    end.x * width,
                    end.y * height,
                    connectionPaint
                )
            }
        }

        for (landmark in landmarks) {
            if (landmark.visibility >= visibilityThreshold) {
                canvas.drawCircle(landmark.x * width, landmark.y * height, landmarkRadius, landmarkPaint)
            }
        }

        return output
    }
}

