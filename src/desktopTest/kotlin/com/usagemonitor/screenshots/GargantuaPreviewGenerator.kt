package com.usagemonitor.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.usagemonitor.HudEdge
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudNotch
import com.usagemonitor.presentation.ui.HudAppBalloonContent
import com.usagemonitor.presentation.ui.components.FooterActionGroup
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.hudAppBalloonHeight
import com.usagemonitor.presentation.ui.rememberHudPresence
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** Captura a HUD real e sua animação contínua, com dados públicos inteiramente sintéticos. */
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/reports/gargantua-preview")
    output.mkdirs()
    captureGargantua(output, "hud-gargantua-horizontal", 920, 132) {
        PreviewNotch(GargantuaPreviewFixtures.showcase, HudEdge.TOP)
    }
    captureGargantua(output, "hud-gargantua-vertical", 320, 540) {
        PreviewNotch(GargantuaPreviewFixtures.showcase, HudEdge.RIGHT)
    }
    captureGargantua(output, "hud-gargantua-light", 920, 132, isDark = false) {
        PreviewNotch(GargantuaPreviewFixtures.showcase, HudEdge.TOP)
    }
    captureGargantua(output, "hud-gargantua-providers", 1080, 720) { ProviderMatrix() }
    val recorder = SceneRecorder(widthDp = 920, heightDp = 132, frameMillis = 50L)
    try {
        recorder.setContent {
            CompositionLocalProvider(LocalAppMotionPolicy provides AppMotionPolicy.Live) {
                PreviewNotch(GargantuaPreviewFixtures.showcase, HudEdge.TOP)
            }
        }
        // Não usar hold: o quadro longo comprime justamente a animação em avaliação.
        recorder.animate(6_000) { }
        GifEncoder.write(File(output, "hud-gargantua-live.gif"), recorder.frames)
    } finally {
        recorder.close()
    }
    recordPresence(output)
    recordRefresh(output)
    recordBalloon(output)
    println("Prévia Gargantua: ${output.absolutePath}")
}

/**
 * Início do app (nascimento em cascata), API desativada (colapso) e reativada
 * (nascimento), pela mesma lista com presença que a janela da HUD usa.
 */
private fun recordPresence(output: File) {
    val all = GargantuaPreviewFixtures.showcase
    var live by mutableStateOf(all)
    val recorder = SceneRecorder(widthDp = 920, heightDp = 132, frameMillis = 40L)
    try {
        recorder.setContent {
            CompositionLocalProvider(LocalAppMotionPolicy provides AppMotionPolicy.Live) {
                PreviewNotch(rememberHudPresence(live, AppMotionPolicy.Live), HudEdge.TOP)
            }
        }
        recorder.animate(2_000) { }
        live = all.filterIndexed { index, _ -> index != 1 }
        recorder.animate(1_200) { }
        live = all
        recorder.animate(1_800) { }
        GifEncoder.write(File(output, "hud-gargantua-presence.gif"), recorder.frames)
    } finally {
        recorder.close()
    }
}

/** R1: coletando (ondas) e concluído (plasma desliza ao valor novo e onda final). */
private fun recordRefresh(output: File) {
    val all = GargantuaPreviewFixtures.showcase
    var shown by mutableStateOf(all)
    val recorder = SceneRecorder(widthDp = 920, heightDp = 132, frameMillis = 40L)
    try {
        recorder.setContent {
            CompositionLocalProvider(LocalAppMotionPolicy provides AppMotionPolicy.Live) {
                PreviewNotch(shown, HudEdge.TOP)
            }
        }
        recorder.animate(600) { }
        shown = all.map { account -> account.copy(refreshing = true) }
        recorder.animate(1_800) { }
        shown = all.map { account ->
            account.copy(quotas = account.quotas.map { quota -> quota.copy(fraction = (quota.fraction + 0.08f).coerceAtMost(1f)) })
        }
        recorder.animate(1_400) { }
        GifEncoder.write(File(output, "hud-gargantua-refresh.gif"), recorder.frames)
    } finally {
        recorder.close()
    }
}

/**
 * B3 · jato relativístico com o ponteiro de verdade: abre no primeiro anel, troca
 * para os outros e para a engrenagem (cada troca repete o jato) e fecha. A borda
 * direita é a das capturas de referência do usuário.
 */
private fun recordBalloon(output: File) {
    val accounts = GargantuaPreviewFixtures.showcase
    var expanded by mutableStateOf(false)
    val recorder = SceneRecorder(widthDp = 460, heightDp = 540, frameMillis = 30L)
    try {
        recorder.setContent {
            CompositionLocalProvider(LocalAppMotionPolicy provides AppMotionPolicy.Live) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    HudNotch(
                        accounts = accounts,
                        edge = HudEdge.RIGHT,
                        sizes = hudNotchSizes(accounts, HudEdge.RIGHT, "Carregando", hasUpdateIndicator = false),
                        fallbackLabel = "Carregando",
                        expanded = expanded,
                        appBalloon = {
                            HudAppBalloonContent(
                                language = AppLanguage.PT,
                                appVersion = "41.5.0",
                                countdown = null,
                                updateIndicator = null,
                                actions = { FooterActionGroup(language = AppLanguage.PT, onRefresh = {}, onOpenSettings = {}) }
                            )
                        },
                        appBalloonHeight = hudAppBalloonHeight(hasUpdateIndicator = false),
                        gearDescription = "Configurações"
                    )
                }
            }
        }
        expanded = true
        recorder.animate(300) { }
        // Centros dos três anéis e da engrenagem nesta cena, em dp.
        for ((x, y) in listOf(416f to 124f, 416f to 240f, 416f to 360f, 416f to 486f)) {
            recorder.moveMouse(x, y)
            recorder.animate(1_200) { }
        }
        expanded = false
        recorder.animate(600) { }
        GifEncoder.write(File(output, "hud-gargantua-balloon.gif"), recorder.frames)
    } finally {
        recorder.close()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun captureGargantua(
    output: File,
    name: String,
    width: Int,
    height: Int,
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val scene = ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f))
    try {
        scene.setContent {
            AppTheme(isDark = isDark, motion = AppMotionPolicy.Reduced) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        scene.render(0L)
        repeat(4) { frame ->
            Thread.sleep(20)
            scene.render((frame + 1) * 20_000_000L)
        }
        val data = checkNotNull(scene.render(100_000_000L).encodeToData(EncodedImageFormat.PNG))
        File(output, "$name.png").writeBytes(data.bytes)
    } finally {
        scene.close()
    }
}

@Composable
private fun PreviewNotch(accounts: List<HudAccount>, edge: HudEdge) {
    val alignment = if (edge.isHorizontal) Alignment.TopCenter else Alignment.CenterEnd
    Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
        HudNotch(
            accounts = accounts,
            edge = edge,
            sizes = hudNotchSizes(accounts, edge, "Carregando", hasUpdateIndicator = false),
            fallbackLabel = "Carregando",
            expanded = false
        )
    }
}

@Composable
private fun ProviderMatrix() {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gargantua · 11 provedores", style = MaterialTheme.typography.titleLarge)
        Text("Dados sintéticos · cotas, saldo e atividade observada", style = MaterialTheme.typography.bodySmall)
        GargantuaPreviewFixtures.accounts.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { account ->
                    Column(
                        modifier = Modifier.size(246.dp, 192.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(account.label, style = MaterialTheme.typography.labelMedium)
                        PreviewNotch(listOf(account), HudEdge.TOP)
                    }
                }
            }
        }
    }
}
