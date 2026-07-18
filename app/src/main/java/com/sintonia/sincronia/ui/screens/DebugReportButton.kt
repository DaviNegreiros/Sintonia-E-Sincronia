package com.sintonia.sincronia.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sintonia.sincronia.components.GlowButton
import com.sintonia.sincronia.ui.theme.SintoniaPrimary
import com.sintonia.sincronia.ui.theme.SintoniaText
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun DebugReportButton(
    path: String,
    modifier: Modifier = Modifier
) {
    var showDebugReport by remember(path) { mutableStateOf(false) }

    GlowButton(
        label = "Ver relatório debug",
        onClick = { showDebugReport = true },
        modifier = modifier
    )

    if (showDebugReport) {
        DebugReportDialog(
            path = path,
            onClose = { showDebugReport = false }
        )
    }
}

@Composable
private fun DebugReportDialog(
    path: String,
    onClose: () -> Unit
) {
    val reportText = remember(path) {
        runCatching { buildDebugReportDisplayText(path) }
            .getOrElse { error -> "Não foi possível abrir o relatório.\n\n$path\n\n${error.message.orEmpty()}" }
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF160730),
        title = {
            Text(
                text = "Relatório Debug",
                color = SintoniaText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = reportText,
                    color = SintoniaTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Fechar", color = SintoniaPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun buildDebugReportDisplayText(path: String): String {
    val root = JSONObject(File(path).readText(Charsets.UTF_8))
    val general = root.optJSONObject("general")
    val finalStats = root.optJSONObject("final_statistics")
    val sync = root.optJSONObject("temporal_synchronization")
    val regions = root.optJSONArray("body_regions")
    val timeline = root.optJSONArray("timeline")
    val diagnostics = root.optJSONArray("possible_causes_for_score_loss")

    return buildString {
        appendLine("Arquivo:")
        appendLine(path)
        appendLine()
        appendLine("Resultado:")
        appendLine("Rank: ${finalStats.optStringOrDash("rank")}")
        appendLine("Similaridade média: ${finalStats.optFormattedDouble("similarity_average")}%")
        appendLine("Score dos feedbacks: ${finalStats.optFormattedDouble("feedback_score")}%")
        appendLine()
        appendLine("Frames:")
        appendLine("Comparados: ${general.optIntOrZero("total_frames_compared")}")
        appendLine("Com comparação válida: ${general.optIntOrZero("frames_with_valid_comparison")}")
        appendLine("Sem comparação: ${general.optIntOrZero("frames_without_comparison")}")
        appendLine("FPS câmera: ${general.optFormattedDouble("average_camera_fps")}")
        appendLine("FPS processamento: ${general.optFormattedDouble("average_processing_fps")}")
        appendLine()
        appendLine("Tempo:")
        appendLine("Duração da dança: ${general.optFormattedDouble("dance_duration_seconds")}s")
        appendLine("Tempo comparado: ${finalStats.optFormattedDouble("time_compared_seconds")}s")
        appendLine("Tempo ignorado: ${finalStats.optFormattedDouble("time_ignored_seconds")}s")
        appendLine("Diferença temporal média: ${sync.optFormattedDouble("average_temporal_difference_ms")}ms")
        appendLine("Maior diferença temporal: ${sync.optFormattedDouble("max_temporal_difference_ms")}ms")
        appendLine()
        appendLine("Landmarks:")
        appendLine("Comparados: ${finalStats.optIntOrZero("total_landmarks_compared")}")
        appendLine("Ignorados pelo moveset: ${finalStats.optIntOrZero("total_landmarks_ignored")}")
        appendLine("Ausentes no jogador: ${finalStats.optIntOrZero("total_landmarks_missing_in_player")}")
        appendLine()
        appendLine("Regiões do corpo:")
        appendRegions(regions)
        appendLine()
        appendLine("Piores momentos:")
        appendWorstTimeline(timeline)
        appendLine()
        appendLine("Possíveis causas:")
        appendDiagnostics(diagnostics)
    }
}

private fun StringBuilder.appendRegions(regions: JSONArray?) {
    val items = regions.objects()
        .sortedBy { it.optDouble("average_score", 100.0) }
    if (items.isEmpty()) {
        appendLine("Nenhuma região registrada.")
        return
    }
    items.forEach { item ->
        appendLine(
            "${item.optString("region", "-")}: " +
                "média ${item.optFormattedDouble("average_score")}%, " +
                "pior ${item.optFormattedDouble("worst_score")}%, " +
                "comparações ${item.optInt("comparisons", 0)}"
        )
    }
}

private fun StringBuilder.appendWorstTimeline(timeline: JSONArray?) {
    val items = timeline.objects()
        .sortedBy { it.optDouble("instant_score", 100.0) }
        .take(8)
    if (items.isEmpty()) {
        appendLine("Nenhum frame registrado.")
        return
    }
    items.forEach { item ->
        appendLine(
            "${item.optFormattedDouble("timestamp_ms")}ms: " +
                "${item.optFormattedDouble("instant_score")}% " +
                "(moveset frame ${item.opt("moveset_frame")}, " +
                "landmarks ${item.optInt("valid_landmarks", 0)}, " +
                "ausentes ${item.optInt("missing_player_landmarks", 0)})"
        )
    }
}

private fun StringBuilder.appendDiagnostics(diagnostics: JSONArray?) {
    val messages = diagnostics.strings()
    if (messages.isEmpty()) {
        appendLine("Nenhum diagnóstico registrado.")
        return
    }
    messages.forEach { message -> appendLine("- $message") }
}

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun JSONObject?.optStringOrDash(name: String): String =
    this?.optString(name, "-")?.takeIf { it.isNotBlank() } ?: "-"

private fun JSONObject?.optIntOrZero(name: String): Int =
    this?.optInt(name, 0) ?: 0

private fun JSONObject?.optFormattedDouble(name: String): String =
    this?.optDouble(name, Double.NaN)
        ?.takeIf { !it.isNaN() }
        ?.let { ((it * 10.0).toInt() / 10.0).toString() }
        ?: "-"
