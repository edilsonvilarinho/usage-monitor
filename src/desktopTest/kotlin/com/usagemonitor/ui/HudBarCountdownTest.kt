package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.HudBar
import com.usagemonitor.presentation.ui.HudQuotaChip
import com.usagemonitor.presentation.ui.HudSourceStatus
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test

/**
 * A contagem até a próxima coleta na barra HUD (issue #185).
 *
 * O que a issue pede é o número **à direita da linha**; o que estes testes
 * afirmam é o que a decisão custou: ele sai uma vez só, some onde não deve
 * aparecer, e decrementa até zero sem que a suíte espere segundos reais — o
 * molde é o de `FooterBarTest`, com o relógio e a espera injetados.
 */
@OptIn(ExperimentalTestApi::class)
class HudBarCountdownTest {

    private val now = Instant.parse("2026-09-02T12:00:00Z")

    private val description = "Próxima atualização automática"

    private fun source(
        label: String,
        statusLabel: String,
        tone: AppTone,
        vararg quotas: Pair<String, AppTone>
    ) = HudSourceStatus(
        label = label,
        statusLabel = statusLabel,
        tone = tone,
        quotas = quotas.map { (text, chipTone) -> HudQuotaChip(text = text, tone = chipTone) }
    )

    private val sources = listOf(
        source("INFORMATA2", "Crítico", AppTone.CRITICAL, "5h 45%" to AppTone.OK),
        source("Padrão", "Atenção", AppTone.WARNING, "5h 88%" to AppTone.WARNING),
        source("Codex", "Normal", AppTone.OK, "mensal 75%" to AppTone.OK)
    )

    @Test
    fun `a barra parada mostra a contagem ate a proxima coleta`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        nextRefreshAt = now + 2.minutes + 5.seconds,
                        countdownDescription = description,
                        nowProvider = { now },
                        countdownUpdatesEnabled = false,
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithText("02:05").assertIsDisplayed()
        onNodeWithContentDescription(description).assertIsDisplayed()
    }

    /**
     * **O polling é um só, de dez minutos para o app inteiro.** Uma contagem por
     * linha afirmaria que cada conta tem coleta própria — e é por isso que a
     * asserção conta nós, não apenas verifica presença.
     */
    @Test
    fun `aberta a contagem aparece uma vez so`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        expanded = true,
                        nextRefreshAt = now + 2.minutes + 5.seconds,
                        countdownDescription = description,
                        nowProvider = { now },
                        countdownUpdatesEnabled = false,
                        onOpenFull = {}
                    )
                }
            }
        }

        // As três contas estão na tela; a contagem, uma vez.
        onNodeWithText("INFORMATA2").assertIsDisplayed()
        onNodeWithText("Codex").assertIsDisplayed()
        onAllNodesWithText("02:05").assertCountEquals(1)
    }

    /** Sem relógio não há coluna: é o estado dos geradores de captura. */
    @Test
    fun `sem proxima coleta a coluna nao existe`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        onOpenFull = {}
                    )
                }
            }
        }

        onAllNodesWithText("02:05").assertCountEquals(0)
        onAllNodesWithContentDescription(description).assertCountEquals(0)
    }

    /**
     * Recolhida ao ponto não há texto nenhum — é o estado em que a barra para de
     * ocupar tela enquanto diz que está tudo bem. O hover devolve o painel, e com
     * ele a contagem.
     */
    @Test
    fun `recolhida ao ponto a contagem nao aparece`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.OK,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        dotOnly = true,
                        nextRefreshAt = now + 2.minutes + 5.seconds,
                        countdownDescription = description,
                        nowProvider = { now },
                        countdownUpdatesEnabled = false,
                        onOpenFull = {}
                    )
                }
            }
        }

        onAllNodesWithText("02:05").assertCountEquals(0)
    }

    /**
     * Enquanto nada foi coletado, "quando é a próxima tentativa" é a informação
     * mais útil que a barra tem — e a linha de carregamento é a primeira linha.
     */
    @Test
    fun `a linha de carregamento tambem mostra a contagem`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.NEUTRAL,
                        sources = emptyList(),
                        fallbackLabel = "Carregando",
                        nextRefreshAt = now + 2.minutes + 5.seconds,
                        countdownDescription = description,
                        nowProvider = { now },
                        countdownUpdatesEnabled = false,
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithText("Carregando").assertIsDisplayed()
        onNodeWithText("02:05").assertIsDisplayed()
    }

    /**
     * O decremento sem esperar segundos reais: o relógio é uma variável e o tique
     * é um `Channel`. Sem isso o teste levaria três segundos para afirmar três
     * segundos, e o laço de espera do `FooterBar` já provou que esse molde
     * funciona.
     */
    @Test
    fun `a contagem decrementa e para em zero`() = runDesktopComposeUiTest {
        val tickChannel = Channel<Unit>(capacity = Channel.UNLIMITED)
        var currentNow = now

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        nextRefreshAt = now + 3.seconds,
                        countdownDescription = description,
                        nowProvider = { currentNow },
                        waitNextTick = { tickChannel.receive() },
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithText("00:03").assertIsDisplayed()

        currentNow = now + 1.seconds
        tickChannel.trySend(Unit)
        waitForIdle()
        onNodeWithText("00:02").assertIsDisplayed()

        currentNow = now + 3.seconds
        tickChannel.trySend(Unit)
        waitForIdle()
        onNodeWithText("00:00").assertIsDisplayed()
    }
}
