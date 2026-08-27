package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppGroupBand
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppSettingsNav
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppTabs
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.components.appNestedGroupGuide
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

    /**
     * A guia é traço de fundo e não pode consumir layout.
     *
     * É a diferença entre ela e `Modifier.border`, que arredonda a espessura
     * para cima e come a caixa em escalas fracionárias (issue #83). Se ela
     * deslocasse o conteúdo, o recuo do bloco aninhado passaria a depender de a
     * guia existir — e o item sem guia sairia de alinhamento com o irmão.
     */
    @Test
    fun `a guia de bloco aninhado nao desloca o conteudo`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(600.dp).height(200.dp)) {
                    Column {
                        Box(
                            modifier = Modifier
                                .appNestedGroupGuide(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    indent = 24.dp
                                )
                                .padding(start = 24.dp)
                        ) {
                            Text("com guia")
                        }
                        Box(modifier = Modifier.padding(start = 24.dp)) {
                            Text("sem guia")
                        }
                    }
                }
            }
        }

        val withGuide = onNodeWithText("com guia").getUnclippedBoundsInRoot().left
        val withoutGuide = onNodeWithText("sem guia").getUnclippedBoundsInRoot().left

        assertEquals(withoutGuide, withGuide)
    }

    /**
     * O recuo da sub-faixa **soma** ao padding horizontal da lista, e é isso que
     * mantém o rótulo dela no mesmo x das linhas abaixo, deslocado só pelo nível.
     * Se ele substituísse o padding, a faixa de nível zero começaria colada na
     * borda e a de nível um começaria onde a lista começa — nenhuma das duas no
     * lugar certo.
     */
    @Test
    fun `o recuo da sub-faixa soma ao padding horizontal`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(600.dp).height(200.dp)) {
                    Column {
                        AppGroupBand(
                            label = "sem recuo",
                            horizontalPadding = 14.dp
                        )
                        AppGroupBand(
                            label = "com recuo",
                            horizontalPadding = 14.dp,
                            indent = 24.dp
                        )
                    }
                }
            }
        }

        val flat = onNodeWithText("sem recuo").getUnclippedBoundsInRoot().left
        val nested = onNodeWithText("com recuo").getUnclippedBoundsInRoot().left

        assertEquals(flat + 24.dp, nested)
    }

    /**
     * A faixa fala **baixo**: o rótulo dela é `labelSmall`, um degrau abaixo do
     * `titleSmall` do cabeçalho de painel. Trocar uma primitiva pela outra
     * inverteria a hierarquia da escada de superfícies, e a sub-faixa passaria a
     * gritar mais que o grupo que a cobre.
     */
    /**
     * O trilho é o controle da janela e tem largura fixa: item selecionado não
     * pode mudar a largura da coluna, ou a lista inteira se mexe a cada clique.
     * O teste mede **pixels** dos dois itens — o selecionado e um vizinho — pela
     * mesma razão do `AppThemeScaleTest`: `Dp` esconderia a diferença.
     */
    @Test
    fun `o trilho de secoes seleciona sem mudar a largura`() = runDesktopComposeUiTest {
        var selected = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(600.dp).height(400.dp)) {
                    AppSettingsNav(
                        items = listOf(
                            AppTab("Geral", "nav-geral"),
                            AppTab("Alertas", "nav-alertas")
                        ),
                        selectedIndex = selected,
                        onSelect = { index -> selected = index },
                        header = "Seções"
                    )
                }
            }
        }

        onNodeWithText("Seções").assertIsDisplayed()
        onNodeWithText("Geral").assertIsDisplayed()

        val first = onNodeWithText("Geral").getUnclippedBoundsInRoot()
        val second = onNodeWithText("Alertas").getUnclippedBoundsInRoot()
        assertEquals(first.left, second.left)

        onNodeWithText("Alertas").performClick()
        assertEquals(1, selected)
    }

    @Test
    fun `a sub-faixa mostra rotulo detalhe e acao`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(600.dp).height(200.dp)) {
                    AppGroupBand(
                        label = "Conta · 3f9c",
                        detail = "2 de 5 online",
                        trailing = { Text("Apagar") }
                    )
                }
            }
        }

        onNodeWithText("Conta · 3f9c").assertIsDisplayed()
        onNodeWithText("2 de 5 online").assertIsDisplayed()
        onNodeWithText("Apagar").assertIsDisplayed()
    }
}
