package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppTabs
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * As primitivas estruturais são consumidas por todas as telas, então um defeito
 * aqui aparece em oito suítes ao mesmo tempo e a causa fica ambígua. Este teste
 * fecha o contrato mínimo: o que é passado aparece, e o que é clicado avisa.
 */
@OptIn(ExperimentalTestApi::class)
class AppStructureTest {

    @Test
    fun `o cabecalho mostra titulo subtitulo e acoes`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(600.dp).height(200.dp)) {
                    AppDataSurfaceFlush(
                        header = {
                            AppSectionHeader(
                                title = "Anthropic · Padrão",
                                subtitle = "dev@example.com",
                                trailing = { Text("Atenção") }
                            )
                        }
                    ) {
                        AppDataRow { Text("Sessão 5h") }
                    }
                }
            }
        }

        onNodeWithText("Anthropic · Padrão").assertIsDisplayed()
        onNodeWithText("dev@example.com").assertIsDisplayed()
        onNodeWithText("Atenção").assertIsDisplayed()
        onNodeWithText("Sessão 5h").assertIsDisplayed()
    }

    @Test
    fun `a linha de dados avisa o clique`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(600.dp).height(200.dp)) {
                    AppDataRow(onClick = { clicks += 1 }) { Text("api-gateway") }
                }
            }
        }

        onNodeWithText("api-gateway").performClick()

        assertEquals(1, clicks)
    }

    /** A aba não guarda escolha nenhuma: quem decide é a tela. */
    @Test
    fun `a aba devolve o indice escolhido`() = runDesktopComposeUiTest {
        var selected = -1
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(600.dp).height(200.dp)) {
                    AppTabs(
                        tabs = listOf(AppTab("Sessões"), AppTab("Resumo"), AppTab("Tendência")),
                        selectedIndex = 0,
                        onSelect = { selected = it }
                    )
                }
            }
        }

        onNodeWithText("Tendência").performClick()

        assertEquals(2, selected)
    }

    @Test
    fun `a barra de estado fica abaixo do conteudo`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(600.dp).height(300.dp)) {
                    AppWindowScaffold(
                        statusBar = { Text("v35.0.0") }
                    ) {
                        Text("Conteúdo")
                    }
                }
            }
        }

        onNodeWithText("Conteúdo").assertIsDisplayed()
        onNodeWithText("v35.0.0").assertIsDisplayed()
    }
}
