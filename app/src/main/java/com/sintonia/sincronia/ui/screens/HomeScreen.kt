package com.sintonia.sincronia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sintonia.sincronia.components.Hairline
import com.sintonia.sincronia.components.PillButton
import com.sintonia.sincronia.components.SintoniaLogo
import com.sintonia.sincronia.settings.AppSettings
import com.sintonia.sincronia.ui.theme.SintoniaPrimary
import com.sintonia.sincronia.ui.theme.SintoniaText
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted
import com.sintonia.sincronia.viewmodel.SettingsViewModel

@Composable
fun HomeScreen(
    onNewDance: () -> Unit,
    onLibrary: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    var showTips by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 18.dp)
                .size(34.dp)
                .background(Color(0x177C3AED), CircleShape)
                .clickable { showSettings = true },
            contentAlignment = Alignment.Center
        ) {
            Text("⚙", color = SintoniaTextMuted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-86).dp)
                .padding(horizontal = 34.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SintoniaLogo(size = 230.dp)
            Hairline(modifier = Modifier.padding(top = 28.dp, bottom = 42.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
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
                    PillButton(label = "Nova Dança", leading = "+", primary = true, onClick = onNewDance, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(modifier = Modifier.size(30.dp))
                    PillButton(label = "Danças Salvas", leading = "▶", primary = false, onClick = onLibrary, modifier = Modifier.weight(1f))
                }
            }
        }

        if (showTips) {
            InfoDialog(onDismiss = { showTips = false })
        }
        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onShowSkeletonChange = settingsViewModel::setShowSkeleton,
                onCountdownChange = settingsViewModel::setCountdownSeconds,
                onLicensesClick = {
                    showSettings = false
                    showLicenses = true
                },
                onDismiss = { showSettings = false }
            )
        }
        if (showLicenses) {
            ThirdPartyLicensesDialog(onDismiss = { showLicenses = false })
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
private fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF160730),
        title = {
            Text("Para melhores resultados:", color = SintoniaText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "- Garanta boa iluminação",
                    "- Mantenha o corpo inteiro no quadro",
                    "- Utilize o celular sempre na vertical",
                    "- Danças gravadas na vertical",
                    "- Apenas um dançarino",
                    "- Garanta que a câmera não se mova durante a dança"
                ).forEach {
                    Text(it, color = SintoniaTextMuted, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendi", color = SintoniaPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SettingsDialog(
    settings: AppSettings,
    onShowSkeletonChange: (Boolean) -> Unit,
    onCountdownChange: (Int) -> Unit,
    onLicensesClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF160730),
        title = {
            Text("Configurações", color = SintoniaText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowSkeletonChange(!settings.showSkeleton) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mostrar esqueleto", color = SintoniaText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Checkbox(
                        checked = settings.showSkeleton,
                        onCheckedChange = onShowSkeletonChange
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Contagem regressiva", color = SintoniaText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSettings.allowedCountdownSeconds.sorted().forEach { seconds ->
                            val selected = settings.countdownSeconds == seconds
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (selected) SintoniaPrimary.copy(alpha = 0.95f) else Color(0x227C3AED),
                                        CircleShape
                                    )
                                    .clickable { onCountdownChange(seconds) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$seconds",
                                    color = if (selected) Color.White else SintoniaTextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                TextButton(onClick = onLicensesClick) {
                    Text("Licenças de terceiros", color = SintoniaPrimary, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = SintoniaPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ThirdPartyLicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val licensesText = remember(context) {
        runCatching {
            context.assets.open("third_party_licenses.txt").bufferedReader().use { it.readText() }
        }.getOrElse {
            "Não foi possível abrir as licenças de terceiros."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF160730),
        title = {
            Text("Licenças de terceiros", color = SintoniaText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = licensesText,
                    color = SintoniaTextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = SintoniaPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}
