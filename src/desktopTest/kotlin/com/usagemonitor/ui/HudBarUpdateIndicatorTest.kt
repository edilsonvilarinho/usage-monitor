package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.HUD_UPDATE_INDICATOR_TAG
import com.usagemonitor.presentation.ui.HudBar
import com.usagemonitor.presentation.ui.HudQuotaChip
import com.usagemonitor.presentation.ui.HudSourceStatus
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O indicador de atualização pendente na barra HUD (issue #225).
 *
 * A decisão que estes testes travam é a de não existir zona de clique
 * própria no ícone: ele fica dentro da `Column` que já abre a janela padrão
 * em qualquer clique, e é lá — não aqui — que `AppUpdateBanner` oferece
 * "Reiniciar e atualizar agora". Um clique que reiniciasse o app direto da
 * HUD seria pior que o problema original: um clique de rotina na pílula
 * reiniciaria o app sem aviso sempre que houvesse atualização pronta.
 */
@OptIn(ExperimentalTestApi::class)
class HudBarUpdateIndicatorTest {

    private val description = "Versão 40.0.0 pronta — será aplicada ao fechar"

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
    fun `sem indicador nenhum icone de atualizacao aparece`() = runDesktopComposeUiTest {
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

        onAllNodesWithTag(HUD_UPDATE_INDICATOR_TAG).assertCountEquals(0)
        onAllNodesWithContentDescription(description).assertCountEquals(0)
    }

    @Test
    fun `a barra parada mostra o indicador de atualizacao`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        updateIndicator = HudUpdateIndicator(tone = AppTone.OK, description = description),
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithTag(HUD_UPDATE_INDICATOR_TAG).assertIsDisplayed()
        onNodeWithContentDescription(description).assertIsDisplayed()
    }

    /**
     * Mesma prova que a contagem já tem: o polling de atualização é um só
     * para o app inteiro, então o ícone repetido em cada linha afirmaria que
     * cada conta tem uma atualização própria.
     */
    @Test
    fun `aberta o indicador aparece uma vez so`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        expanded = true,
                        updateIndicator = HudUpdateIndicator(tone = AppTone.OK, description = description),
                        onOpenFull = {}
                    )
                }
            }
        }

        // As três contas estão na tela; o ícone, uma vez — não por lista
        // curta, e sim porque o painel está de fato expandido.
        onNodeWithText("INFORMATA2").assertIsDisplayed()
        onNodeWithText("Codex").assertIsDisplayed()
        onAllNodesWithTag(HUD_UPDATE_INDICATOR_TAG).assertCountEquals(1)
    }

    @Test
    fun `a linha de carregamento tambem mostra o indicador`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.NEUTRAL,
                        sources = emptyList(),
                        fallbackLabel = "Carregando",
                        updateIndicator = HudUpdateIndicator(tone = AppTone.OK, description = description),
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithTag(HUD_UPDATE_INDICATOR_TAG).assertIsDisplayed()
    }

    /**
     * O ícone não é botão próprio: `hudPressGesture` ignora consumo de
     * filhos e é ele quem dispara `onOpenFull` quando o clique termina sobre
     * o ícone, exatamente como em qualquer outro ponto da pílula.
     */
    @Test
    fun `clicar no indicador abre a janela completa, nao reinicia direto`() = runDesktopComposeUiTest {
        var openFullCalls = 0

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        updateIndicator = HudUpdateIndicator(tone = AppTone.OK, description = description),
                        onOpenFull = { openFullCalls++ }
                    )
                }
            }
        }

        onNodeWithTag(HUD_UPDATE_INDICATOR_TAG).performClick()

        assertEquals(1, openFullCalls)
    }
}
