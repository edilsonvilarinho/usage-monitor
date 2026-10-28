package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.russhwolf.settings.PreferencesSettings
import kotlin.math.roundToInt

/**
 * Geometria gravada de uma janela redimensionável: tamanho, posição e disposição.
 *
 * Uma implementação para as quatro janelas que lembram onde ficaram (histórico,
 * sessões CLI, sessões do time e presença). Eram quatro cópias de ~160 linhas que
 * só diferiam nas chaves e no tamanho padrão (plano `cobertura-95-execucao.md`).
 *
 * Cada janela tem **chaves próprias**: elas costumam ficar abertas lado a lado, e
 * herdar a posição uma da outra faria uma nascer exatamente em cima da outra. As
 * chaves gravadas são as mesmas de antes (`<prefixo>Width`, `Height`, `X`, `Y`,
 * `Placement`), então a geometria de quem já usa o app continua valendo.
 */
internal data class WindowGeometrySpec(
    val keyPrefix: String,
    val defaultWidthDp: Int,
    val defaultHeightDp: Int
) {
    val widthKey: String get() = "${keyPrefix}Width"
    val heightKey: String get() = "${keyPrefix}Height"
    val xKey: String get() = "${keyPrefix}X"
    val yKey: String get() = "${keyPrefix}Y"
    val placementKey: String get() = "${keyPrefix}Placement"
}

internal val HISTORY_WINDOW_GEOMETRY = WindowGeometrySpec("historyWindow", defaultWidthDp = 860, defaultHeightDp = 760)
internal val CLI_SESSIONS_WINDOW_GEOMETRY = WindowGeometrySpec("cliSessionsWindow", defaultWidthDp = 960, defaultHeightDp = 780)
internal val TEAM_USAGE_WINDOW_GEOMETRY = WindowGeometrySpec("teamUsageWindow", defaultWidthDp = 960, defaultHeightDp = 780)

// Igual à janela de consumo, e não menor como antes: a lista de presença tem
// cinco colunas fixas mais o botão de remover, e em 860dp o pior caso passava da
// largura útil — o `FlowRow` quebrava e as colunas deixavam de alinhar entre as
// linhas, que é justamente o que as larguras fixas existem para impedir. A conta
// está em `TeamPresenceScreen.PRESENCE_COLUMN_*`.
internal val TEAM_PRESENCE_WINDOW_GEOMETRY = WindowGeometrySpec("teamPresenceWindow", defaultWidthDp = 1_030, defaultHeightDp = 620)

/**
 * Piso da janela, aplicado em `ModalWindowsHost.kt` via `minimumSize` da janela AWT.
 *
 * A lista de sessões passou a ter uma **faixa de legendas de coluna**, e uma faixa
 * dessas só cumpre a promessa se a linha não quebrar: as seis colunas fixas mais o
 * padding, a barra de rolagem e a coluna de ação cabem em 960dp e não em menos. O
 * tamanho default já era esse; o que faltava era impedir o arrasto da borda para
 * baixo do orçamento, que é a mesma porta que a tela de presença fechou.
 *
 * A conta está em `CliSessionsScreen.SESSION_COLUMN_*`.
 */
internal const val CLI_SESSIONS_MIN_WINDOW_WIDTH_DP = 960
internal const val CLI_SESSIONS_MIN_WINDOW_HEIGHT_DP = 460

/**
 * Piso da janela, aplicado em `ModalWindowsHost.kt` via `minimumSize` da janela AWT.
 *
 * Mesma razão da janela de Sessões CLI e da de presença: a lista ganhou faixa de
 * legendas de coluna, e faixa sobre linha que quebra promete um alinhamento que o
 * conteúdo não cumpre. As sete colunas de `TeamUsageScreen.TEAM_COLUMN_*` somam
 * 740dp e o cromo em volta come o resto dos 960.
 */
internal const val TEAM_USAGE_MIN_WINDOW_WIDTH_DP = 960
internal const val TEAM_USAGE_MIN_WINDOW_HEIGHT_DP = 460

/**
 * Piso da janela, aplicado em `ModalWindowsHost.kt` via `minimumSize` da janela AWT.
 *
 * O tamanho default já cabia; o que faltava era impedir o arrasto da borda para
 * baixo do orçamento. Abaixo dele a soma das colunas mais a coluna de ação passa
 * da largura útil, a linha quebra e o botão de apagar conta cai numa linha
 * própria.
 *
 * Subiu de 940 para 1030 na issue #81: os dois carimbos que moravam dentro das
 * células de Estado e de Trabalhando viraram colunas próprias, e são sete colunas
 * onde antes eram cinco. As 802dp somadas em `TeamPresenceScreen.PRESENCE_COLUMN_*`
 * mais o vão, o marcador, a barra de rolagem, a ação e o cromo da janela dão 1022,
 * arredondado para cima. A janela de presença é a mais larga das três porque é a
 * única que imprime dois carimbos de data por linha.
 */
internal const val TEAM_PRESENCE_MIN_WINDOW_WIDTH_DP = 1_030
internal const val TEAM_PRESENCE_MIN_WINDOW_HEIGHT_DP = 460

internal data class PersistedWindowGeometry(
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val xDp: Int? = null,
    val yDp: Int? = null,
    val placement: PersistedWindowPlacement = PersistedWindowPlacement.FLOATING
) {
    val composePlacement: WindowPlacement
        get() = when (placement) {
            PersistedWindowPlacement.MAXIMIZED -> WindowPlacement.Maximized
            PersistedWindowPlacement.FLOATING -> WindowPlacement.Floating
        }
}

internal data class WindowGeometrySnapshot(
    val widthDp: Float,
    val heightDp: Float,
    val xDp: Float?,
    val yDp: Float?,
    val placement: WindowPlacement
)

internal fun readPersistedWindowGeometry(settings: PreferencesSettings, spec: WindowGeometrySpec): PersistedWindowGeometry {
    val widthDp = settings.getStringOrNull(spec.widthKey)?.toIntOrNull()?.takeIf { value -> value > 0 }
    val heightDp = settings.getStringOrNull(spec.heightKey)?.toIntOrNull()?.takeIf { value -> value > 0 }
    val xDp = settings.getStringOrNull(spec.xKey)?.toIntOrNull()
    val yDp = settings.getStringOrNull(spec.yKey)?.toIntOrNull()
    val placement = when (settings.getStringOrNull(spec.placementKey)) {
        PersistedWindowPlacement.MAXIMIZED.name -> PersistedWindowPlacement.MAXIMIZED
        else -> PersistedWindowPlacement.FLOATING
    }
    return PersistedWindowGeometry(widthDp = widthDp, heightDp = heightDp, xDp = xDp, yDp = yDp, placement = placement)
}

internal fun persistWindowGeometry(settings: PreferencesSettings, spec: WindowGeometrySpec, snapshot: WindowGeometrySnapshot) {
    val placement = if (snapshot.placement == WindowPlacement.Maximized) {
        PersistedWindowPlacement.MAXIMIZED
    } else {
        PersistedWindowPlacement.FLOATING
    }
    settings.putString(spec.placementKey, placement.name)

    // Maximizada não grava tamanho: guardar a área da tela inteira faria a
    // janela restaurada nascer do tamanho do monitor.
    if (placement != PersistedWindowPlacement.FLOATING) {
        return
    }

    snapshot.widthDp.toPersistableDp()?.let { value -> settings.putString(spec.widthKey, value.toString()) }
    snapshot.heightDp.toPersistableDp()?.let { value -> settings.putString(spec.heightKey, value.toString()) }
    snapshot.xDp?.toPersistableDp()?.let { value -> settings.putString(spec.xKey, value.toString()) }
    snapshot.yDp?.toPersistableDp()?.let { value -> settings.putString(spec.yKey, value.toString()) }
}

@Composable
internal fun rememberPersistedWindowGeometry(
    persisted: PersistedWindowGeometry,
    spec: WindowGeometrySpec,
    uiScalePercent: Int,
    workArea: ScreenWorkArea,
    /** Os monitores; parâmetro para o teste não perguntar ao sistema de janelas. */
    screens: () -> List<ScreenInfo> = ::availableScreens
): WindowState {
    // Só o tamanho default acompanha a escala: tamanho persistido é escolha do
    // usuário. O ajuste à tela, esse, vale para os dois — janela salva num monitor
    // maior volta com a barra de título fora da tela no menor, e a barra é a única
    // que esta janela `undecorated` tem (issue #72).
    val scale = uiScaleFactor(uiScalePercent)
    val desiredSize = DpSize(
        width = persisted.widthDp?.dp ?: (spec.defaultWidthDp.dp * scale),
        height = persisted.heightDp?.dp ?: (spec.defaultHeightDp.dp * scale)
    )
    // A área útil do monitor em que a janela foi deixada, e não a do primário
    // (issue #273): a posição salva num secundário era presa ao principal e a
    // janela voltava para ele a cada abertura. Sem posição salva vale o primário.
    val area = remember(persisted, workArea) {
        workAreaForPosition(persisted.xDp?.dp, persisted.yDp?.dp, desiredSize, fallback = workArea, screens = screens())
    }
    val initialSize = fitWindowSize(desiredSize, area)
    val initialPosition = if (persisted.xDp != null && persisted.yDp != null) {
        fitWindowPosition(x = persisted.xDp.dp, y = persisted.yDp.dp, size = initialSize, workArea = area)
    } else {
        WindowPosition(Alignment.Center)
    }

    return rememberWindowState(placement = persisted.composePlacement, position = initialPosition, size = initialSize)
}

/** `NaN` ou infinito não vão para o disco: viraria uma janela de tamanho inválido na volta. */
private fun Float.toPersistableDp(): Int? {
    if (!isFinite()) {
        return null
    }
    return roundToInt()
}

/** A moldura da janela como a geometria gravada a descreve. */
internal fun windowGeometrySnapshotOf(position: WindowPosition, size: DpSize, placement: WindowPlacement) =
    WindowGeometrySnapshot(
        widthDp = size.width.value,
        heightDp = size.height.value,
        xDp = if (position.isSpecified) position.x.value else null,
        yDp = if (position.isSpecified) position.y.value else null,
        placement = placement
    )
