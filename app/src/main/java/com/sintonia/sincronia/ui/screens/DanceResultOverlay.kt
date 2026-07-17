package com.sintonia.sincronia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun DanceResultOverlay(
    result: DanceResult,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)
    var showDebugReport by remember(result.debugReportPath) { mutableStateOf(false) }

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

            if (result.debugReportPath != null) {
                GlowButton(
                    label = "Ver relatório debug",
                    onClick = { showDebugReport = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            GlowButton(
                label = "Fechar",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDebugReport && result.debugReportPath != null) {
        DebugReportDialog(
            path = result.debugReportPath,
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
        appendLine("Pontuação final: ${finalStats.optFormattedDouble("final_score")}%")
        appendLine("Rank: ${finalStats.optString("rank", "-")}")
        appendLine("Similaridade média: ${finalStats.optFormattedDouble("similarity_average")}%")
        appendLine("Score dos feedbacks: ${finalStats.optFormattedDouble("feedback_score")}%")
        appendLine()
        appendLine("Frames:")
        appendLine("Comparados: ${general.optInt("total_frames_compared", 0)}")
        appendLine("Com comparação válida: ${general.optInt("frames_with_valid_comparison", 0)}")
        appendLine("Sem comparação: ${general.optInt("frames_without_comparison", 0)}")
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
        appendLine("Comparados: ${finalStats.optInt("total_landmarks_compared", 0)}")
        appendLine("Ignorados pelo moveset: ${finalStats.optInt("total_landmarks_ignored", 0)}")
        appendLine("Ausentes no jogador: ${finalStats.optInt("total_landmarks_missing_in_player", 0)}")
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

private fun JSONObject?.optFormattedDouble(name: String): String =
    this?.optDouble(name, Double.NaN)
        ?.takeIf { !it.isNaN() }
        ?.let { ((it * 10.0).toInt() / 10.0).toString() }
        ?: "-"

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
