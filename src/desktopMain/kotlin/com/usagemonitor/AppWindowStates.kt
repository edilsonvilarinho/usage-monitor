package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import com.russhwolf.settings.PreferencesSettings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.window.Window
import io.ktor.client.request.get
import kotlin.math.roundToInt

/** O estado de cada janela do app: tamanho, posição e disposição. */
internal class AppWindowStates(
    val history: WindowState,
    val cliSessions: WindowState,
    val codexCliSessions: WindowState,
    val comparison: WindowState,
    val teamUsage: WindowState,
    val teamPresence: WindowState,
    val teamKeys: DialogState,
    val settings: DialogState,
    /** O relatório de bug: a HUD é pequena demais para hospedar o formulário. */
    val bugReport: DialogState
)

/**
 * Os estados das janelas, lidos do que foi gravado. A escala entra no tamanho
 * default de cada uma quando não há nada gravado; tamanho gravado é escolha do
 * usuário e não é reescalado. Todas são presas à área útil da tela: nenhuma tem
 * moldura do sistema, então nascer maior que o monitor é nascer sem botão de
 * fechar.
 */
@Composable
internal fun rememberAppWindowStates(
    settings: PreferencesSettings,
    uiScalePercent: Int,
    workArea: ScreenWorkArea
): AppWindowStates {
    val scale = uiScaleFactor(uiScalePercent)
    val persistedHistory = remember(settings) { readPersistedHistoryWindowState(settings) }
    val persistedCliSessions = remember(settings) { readPersistedCliSessionsWindowState(settings) }
    val persistedTeamUsage = remember(settings) { readPersistedTeamUsageWindowState(settings) }
    val persistedTeamPresence = remember(settings) { readPersistedTeamPresenceWindowState(settings) }
    return AppWindowStates(
        history = rememberPersistedHistoryWindowState(persistedHistory, uiScalePercent, workArea),
        cliSessions = rememberPersistedCliSessionsWindowState(persistedCliSessions, uiScalePercent, workArea),
        codexCliSessions = rememberWindowState(
            size = fitWindowSize(DpSize(980.dp * scale, 640.dp * scale), workArea)
        ),
        comparison = rememberWindowState(
            size = fitWindowSize(DpSize(1000.dp * scale, 620.dp * scale), workArea)
        ),
        teamUsage = rememberPersistedTeamUsageWindowState(persistedTeamUsage, uiScalePercent, workArea),
        teamPresence = rememberPersistedTeamPresenceWindowState(persistedTeamPresence, uiScalePercent, workArea),
        teamKeys = rememberDialogState(size = fitWindowSize(DpSize(760.dp * scale, 640.dp * scale), workArea)),
        // 820 de largura: as Configurações têm navegação lateral de 150dp, e em
        // 620 o conteúdo ficava com menos de 470 — estreito demais para as
        // linhas de rótulo + controle das seções de Time.
        settings = rememberDialogState(size = fitWindowSize(DpSize(820.dp * scale, 720.dp * scale), workArea)),
        bugReport = rememberDialogState(size = fitWindowSize(DpSize(640.dp * scale, 680.dp * scale), workArea))
    )
}

/** A moldura de uma janela que pode ser gravada: posição, tamanho e disposição. */
private data class WindowFrame(val position: WindowPosition, val size: DpSize, val placement: WindowPlacement)

/** Grava a geometria das janelas com debounce de 250ms — o arrasto emitiria uma gravação por pixel. */
@OptIn(FlowPreview::class)
@Composable
internal fun PersistAppWindowStates(
    windows: AppWindowStates,
    settings: PreferencesSettings
) {
    PersistWindowFrame(windows.history, settings) { frame ->
        persistHistoryWindowState(
            settings = settings,
            snapshot = HistoryWindowSnapshot(
                widthDp = frame.size.width.value,
                heightDp = frame.size.height.value,
                xDp = frame.xDp,
                yDp = frame.yDp,
                placement = frame.placement
            )
        )
    }
    PersistWindowFrame(windows.cliSessions, settings) { frame ->
        persistCliSessionsWindowState(
            settings = settings,
            snapshot = CliSessionsWindowSnapshot(
                widthDp = frame.size.width.value,
                heightDp = frame.size.height.value,
                xDp = frame.xDp,
                yDp = frame.yDp,
                placement = frame.placement
            )
        )
    }
    PersistWindowFrame(windows.teamUsage, settings) { frame ->
        persistTeamUsageWindowState(
            settings = settings,
            snapshot = TeamUsageWindowSnapshot(
                widthDp = frame.size.width.value,
                heightDp = frame.size.height.value,
                xDp = frame.xDp,
                yDp = frame.yDp,
                placement = frame.placement
            )
        )
    }
    PersistWindowFrame(windows.teamPresence, settings) { frame ->
        persistTeamPresenceWindowState(
            settings = settings,
            snapshot = TeamPresenceWindowSnapshot(
                widthDp = frame.size.width.value,
                heightDp = frame.size.height.value,
                xDp = frame.xDp,
                yDp = frame.yDp,
                placement = frame.placement
            )
        )
    }
}

private val WindowFrame.xDp: Float?
    get() = if (position.isSpecified) position.x.value else null

private val WindowFrame.yDp: Float?
    get() = if (position.isSpecified) position.y.value else null

@OptIn(FlowPreview::class)
@Composable
private fun PersistWindowFrame(state: WindowState, settings: PreferencesSettings, persist: (WindowFrame) -> Unit) {
    LaunchedEffect(state, settings) {
        snapshotFlow { WindowFrame(state.position, state.size, state.placement) }
            .distinctUntilChanged()
            .debounce(250.milliseconds)
            .collect { frame -> persist(frame) }
    }
}

/**
 * Piso de arrasto da janela AWT, em `Dp` de interface.
 *
 * As três janelas de lista — Sessões CLI, Sessões do time e Presença — têm faixa
 * de legendas de coluna sobre linhas de largura fixa. Abaixo do orçamento de
 * colunas a linha quebra e as colunas param de alinhar; o tamanho persistido não
 * protege nada, porque quem arrasta a borda é o usuário.
 *
 * A unidade da janela AWT é a mesma `Dp` do `WindowState` — o Compose Desktop
 * converte 1:1 (`setSizeImpl` usa `size.width.value`), e a densidade do sistema
 * fica só no desenho. O que entra aqui é a **escala da interface**, pelo mesmo
 * motivo de `scaledWindowSize`: ela multiplica a densidade do conteúdo, então a
 * 150% a mesma janela mostra menos colunas e o piso precisa subir junto.
 *
 * O piso também é preso à área útil: a 150% ele daria 1410dp de largura, mais que
 * um monitor de 1366, e um piso maior que a tela não é piso — é janela que nem
 * arrastando a borda cabe.
 *
 * Um efeito só, e não três cópias: seriam três lugares para o orçamento divergir.
 */
@Composable
internal fun ApplyWindowMinimumSize(
    window: java.awt.Window,
    widthDp: Int,
    heightDp: Int,
    uiScalePercent: Int,
    workArea: ScreenWorkArea
) {
    val scale = uiScaleFactor(uiScalePercent)
    LaunchedEffect(window, scale, workArea, widthDp, heightDp) {
        awaitAwtEventTurn()
        val minimum = fitWindowSize(
            DpSize(width = widthDp.dp * scale, height = heightDp.dp * scale),
            workArea
        )
        window.minimumSize = java.awt.Dimension(
            minimum.width.value.roundToInt(),
            minimum.height.value.roundToInt()
        )
    }
}
