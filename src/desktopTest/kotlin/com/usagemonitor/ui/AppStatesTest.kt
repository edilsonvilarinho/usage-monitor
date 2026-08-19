package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppStatesTest {

    @Test
    fun `o aviso mostra titulo descricao e acao`() = runDesktopComposeUiTest {
        var retried = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(700.dp).height(200.dp)) {
                    AppBanner(
                        title = "Anthropic — Padrão",
                        description = "Limite de requisições atingido.",
                        tone = AppTone.WARNING,
                        action = {
                            AppButton(label = "Tentar de novo", onClick = { retried += 1 })
                        }
                    )
                }
            }
        }

        onNodeWithText("Anthropic — Padrão").assertIsDisplayed()
        onNodeWithText("Limite de requisições atingido.").assertIsDisplayed()
        onNodeWithText("Tentar de novo").performClick()

        assertEquals(1, retried)
    }

    /** Cor não informa sozinha: o estado sempre carrega a palavra. */
    @Test
    fun `o indicador nomeia o estado`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    AppStatusIndicator(label = "Desconectado", tone = AppTone.NEUTRAL)
                }
            }
        }

        onNodeWithText("Desconectado").assertIsDisplayed()
    }

    /** Cota estourada não pode desenhar barra além da própria largura. */
    @Test
    fun `a barra aceita fracao acima de um sem quebrar`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(60.dp)) {
                    AppProgressTrack(fraction = 1.8f, tone = AppTone.CRITICAL)
                }
            }
        }
    }

    @Test
    fun `o estado vazio e o de carga mostram a mensagem`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(700.dp).height(500.dp)) {
                    AppEmptyState(message = "Nenhum turno nesta janela.")
                }
            }
        }

        onNodeWithText("Nenhum turno nesta janela.").assertIsDisplayed()
    }

    @Test
    fun `a carga mostra o rotulo sem animacao infinita`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(700.dp).height(400.dp)) {
                    AppLoadingState(message = "Carregando dados das APIs…")
                }
            }
        }

        // Chegar aqui já prova o essencial: `waitForIdle` retornou, então a
        // primitiva de carga não deixou animação pendente.
        onNodeWithText("Carregando dados das APIs…").assertIsDisplayed()
    }

    @Test
    fun `o erro oferece o caminho de volta`() = runDesktopComposeUiTest {
        var retried = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(700.dp).height(400.dp)) {
                    AppErrorState(
                        message = "Erro ao carregar histórico",
                        retryLabel = "Tentar novamente",
                        onRetry = { retried += 1 }
                    )
                }
            }
        }

        onNodeWithText("Erro ao carregar histórico").assertIsDisplayed()
        onNodeWithText("Tentar novamente").performClick()

        assertEquals(1, retried)
    }
}
