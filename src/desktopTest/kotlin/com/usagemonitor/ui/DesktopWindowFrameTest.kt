package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.COMPACT_EXIT_DESCRIPTION
import com.usagemonitor.presentation.ui.HUD_BAR_OPEN_DESCRIPTION
import com.usagemonitor.presentation.ui.HudBar
import com.usagemonitor.presentation.ui.HudSourceStatus
import com.usagemonitor.presentation.ui.TitleBarButton
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppChrome
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O cromo das janelas não tinha teste nenhum.
 *
 * Ele vive em `desktopMain` porque mexe com a janela AWT, e a maior parte dele é
 * `WindowScope.` — `DesktopWindowFrame`, `DesktopTitleBar`, `DesktopDialogTitleBar`
 * e `CompactTitleBarOverlay` todos precisam de uma janela real para o
 * `WindowDraggableArea`, e `runDesktopComposeUiTest` não fornece uma. O que dá
 * para exercitar aqui é o botão de cromo, que é o único que não depende do escopo
 * de janela — e é ele que carrega as três decisões que este arquivo registra: a
 * altura vinda do token, o retângulo que preenche a barra e a descrição semântica
 * dos glifos que não se explicam.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopWindowFrameTest {

    private val sources = listOf(
        HudSourceStatus(
            label = "Anthropic · Padrão",
            statusLabel = "Crítico",
            tone = AppTone.CRITICAL,
            percentLabel = "92%",
            resetLabel = "Ter 22h59"
        ),
        HudSourceStatus(
            label = "Anthropic · Empresa",
            statusLabel = "Atenção",
            tone = AppTone.WARNING,
            percentLabel = "41%",
            resetLabel = "4h12"
        ),
        HudSourceStatus(
            label = "OpenCode Go",
            statusLabel = "Normal",
            tone = AppTone.OK,
            percentLabel = "12%"
        )
    )

    /**
     * O botão preenche a altura da barra menos a divisória de 1dp.
     *
     * Não é detalhe: o botão arredondado flutuando dentro da barra era o único
     * lugar do app onde um controle não encostava na própria moldura, e é por
     * isso que ele não é um `AppIconButton`, que tem 26dp e borda.
     */
    @Test
    fun `o botao de cromo preenche a altura da barra`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    TitleBarButton(label = "×", onClick = {})
                }
            }
        }

        onNodeWithText("×").assertIsDisplayed()
        onNodeWithText("×").assertHeightIsEqualTo(AppChrome.titleBar - 1.dp)
    }

    /**
     * Minimizar, maximizar e fechar são o vocabulário de janela que todo sistema
     * desenha igual; o quadrado do modo somente cards, não — e por isso só ele
     * carrega descrição. É a semântica que o leitor de tela lê, não a tooltip.
     */
    @Test
    fun `o glifo que nao se explica carrega descricao`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    TitleBarButton(
                        label = "▣",
                        onClick = {},
                        description = COMPACT_EXIT_DESCRIPTION
                    )
                }
            }
        }

        onNodeWithContentDescription(COMPACT_EXIT_DESCRIPTION).assertIsDisplayed()
    }

    @Test
    fun `o botao de cromo despacha o clique`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    TitleBarButton(label = "–", onClick = { clicks += 1 })
                }
            }
        }

        onNodeWithText("–").performClick()
        assertEquals(1, clicks)
    }

    /**
     * `HudBar` não depende de `WindowScope` — o arrasto sai daqui como
     * callback e é `Main.kt` que move a janela AWT —, e é isso que a deixa
     * exercitável inteira aqui, ao contrário do resto do cromo.
     *
     * **Uma linha por fonte, não só a que perde.** Mostrar apenas a pior era a
     * queixa que abriu esta passada: com várias contas, as outras não tinham
     * sinal nenhum de que existiam.
     */
    @Test
    fun `a barra HUD lista todas as fontes com percentual e reset`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(200.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithText("Anthropic · Padrão").assertIsDisplayed()
        onNodeWithText("Anthropic · Empresa").assertIsDisplayed()
        onNodeWithText("OpenCode Go").assertIsDisplayed()
        onNodeWithText("92%").assertIsDisplayed()
        onNodeWithText("41%").assertIsDisplayed()
        onNodeWithText("Ter 22h59").assertIsDisplayed()
    }

    /**
     * Cada linha carrega ponto **e** palavra. O percentual descreve o consumo,
     * não o risco — 40% às onze da manhã pode ser pior que 80% dez minutos antes
     * do reinício —, e sem a palavra a cor informaria o estado sozinha.
     */
    @Test
    fun `cada linha da barra HUD carrega a palavra do estado`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(200.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithText("Crítico").assertIsDisplayed()
        onNodeWithText("Atenção").assertIsDisplayed()
        onNodeWithText("Normal").assertIsDisplayed()
    }

    /** Fonte sem reset conhecido não imprime traço: a coluna some. */
    @Test
    fun `fonte sem reset nao desenha a coluna`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(200.dp)) {
                    HudBar(
                        statusTone = AppTone.OK,
                        sources = listOf(sources[2]),
                        fallbackLabel = "Carregando",
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithText("OpenCode Go").assertIsDisplayed()
        onNodeWithText("12%").assertIsDisplayed()
        onNodeWithText("—").assertDoesNotExist()
    }

    /** Antes da primeira coleta há uma linha só, e ela diz que está carregando. */
    @Test
    fun `a barra HUD sem fontes mostra a linha de carregamento`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(200.dp)) {
                    HudBar(
                        statusTone = AppTone.NEUTRAL,
                        sources = emptyList(),
                        fallbackLabel = "Carregando",
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithText("Carregando").assertIsDisplayed()
    }

    @Test
    fun `o rodape de sessao aparece abaixo das fontes`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(240.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        footerLabel = "2 sessões · \$4.21 · 1.2M tok",
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithText("2 sessões · \$4.21 · 1.2M tok").assertIsDisplayed()
    }

    /** Não há botão próprio: o painel inteiro é o alvo de clique. */
    @Test
    fun `a barra HUD despacha o clique em qualquer ponto do painel`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(200.dp)) {
                    HudBar(
                        statusTone = AppTone.OK,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        onOpenFull = { clicks += 1 }
                    )
                }
            }
        }

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performClick()
        assertEquals(1, clicks)
    }

    /**
     * O que separa clique de arrasto é o limiar de deslocamento, e é por isso
     * que o painel não usa `clickable`: aquele consumiria o `down` e o arrasto
     * nunca começaria. Arrastar não pode abrir a janela completa por engano.
     */
    @Test
    fun `arrastar a barra HUD nao abre a janela completa`() = runDesktopComposeUiTest {
        var clicks = 0
        val events = mutableListOf<String>()
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(200.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        onDragStart = { events += "start" },
                        onDragMove = { events += "move" },
                        onDragEnd = { events += "end" },
                        onOpenFull = { clicks += 1 }
                    )
                }
            }
        }

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performMouseInput {
            moveTo(center)
            press()
            moveTo(center + Offset(60f, 0f))
            release()
        }
        waitForIdle()

        assertEquals(0, clicks)
        assertEquals("start", events.first())
        assertEquals("end", events.last())
        assertTrue(events.contains("move"), "esperava movimento em $events")
    }

    /**
     * O arrasto tem de sobreviver à recomposição que ele mesmo provoca: em
     * `Main.kt` cada movimento move a âncora, que é estado, e as lambdas
     * passadas para `HudBar` viram objetos novos.
     *
     * **Este teste não discrimina a estratégia de chave do `pointerInput`, e
     * isso foi medido**: com as lambdas como chave — a versão que se suspeitava
     * defeituosa — ele também passa, com a mesma contagem. Ele trava o
     * comportamento observável, não a implementação.
     *
     * Cada movimento vai numa injeção própria com `waitForIdle` entre elas: um
     * `performMouseInput` único despacha o gesto inteiro antes de a composição
     * refazer, e aí não há recomposição durante o arrasto para observar.
     */
    @Test
    fun `o arrasto da barra HUD sobrevive a recomposicao a cada movimento`() = runDesktopComposeUiTest {
        var moves by mutableStateOf(0)
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(200.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        // Recompõe de verdade: o texto depende do contador.
                        fallbackLabel = "movimentos $moves",
                        onDragMove = { moves += 1 },
                        onOpenFull = {}
                    )
                }
            }
        }

        val pill = onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION)
        pill.performMouseInput {
            moveTo(center)
            press()
        }
        waitForIdle()
        repeat(3) { step ->
            pill.performMouseInput { moveTo(center + Offset(30f * (step + 1), 0f)) }
            waitForIdle()
        }
        pill.performMouseInput { release() }
        waitForIdle()

        assertTrue(moves >= 3, "esperava o arrasto continuar depois de recompor, veio $moves")
    }

    /** Pressionar e soltar sem sair do lugar continua sendo clique. */
    @Test
    fun `clicar sem arrastar a barra HUD abre a janela completa`() = runDesktopComposeUiTest {
        var clicks = 0
        val events = mutableListOf<String>()
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(200.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        onDragStart = { events += "start" },
                        onDragEnd = { events += "end" },
                        onOpenFull = { clicks += 1 }
                    )
                }
            }
        }

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performMouseInput {
            moveTo(center)
            press()
            release()
        }
        waitForIdle()

        assertEquals(1, clicks)
        assertTrue(events.isEmpty(), "esperava nenhum evento de arrasto, veio $events")
    }

    /**
     * Com tudo em `ON_TRACK` o painel recolhe ao ponto: o dado para de ocupar
     * tela enquanto diz que está tudo bem. O ponto continua lá, e é o único
     * lugar do app em que ele aparece sem palavra — a palavra está a um
     * movimento de mouse.
     */
    @Test
    fun `a barra HUD recolhida ao ponto esconde o texto`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(200.dp)) {
                    HudBar(
                        statusTone = AppTone.OK,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        footerLabel = "2 sessões",
                        dotOnly = true,
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithText("OpenCode Go").assertDoesNotExist()
        onNodeWithText("12%").assertDoesNotExist()
        onNodeWithText("2 sessões").assertDoesNotExist()
        // A altura de 24dp do estado recolhido é decidida por `hudWindowSize`,
        // que dimensiona a janela; aqui o nó raiz preenche a cena de teste.
    }

    /** Recolhida ao ponto, ela continua sendo o caminho para a janela completa. */
    @Test
    fun `a barra HUD recolhida ao ponto continua despachando o clique`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(200.dp)) {
                    HudBar(
                        statusTone = AppTone.OK,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        dotOnly = true,
                        onOpenFull = { clicks += 1 }
                    )
                }
            }
        }

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performClick()
        assertEquals(1, clicks)
    }

    /**
     * É `onHoverChange` que faz `Main.kt` desfazer o recolhimento ao ponto. Sem
     * esta fiação o painel existe e nunca volta — e o teste do estado recolhido
     * passaria mesmo assim, porque ele injeta `dotOnly` direto.
     */
    @Test
    fun `a barra HUD avisa quando o ponteiro entra e sai`() = runDesktopComposeUiTest {
        val reported = mutableListOf<Boolean>()
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(500.dp).height(200.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        onHoverChange = { hovered -> reported += hovered },
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performMouseInput { enter(center) }
        waitForIdle()
        assertEquals(true, reported.last())

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performMouseInput { exit(Offset(-1f, -1f)) }
        waitForIdle()
        assertEquals(false, reported.last())
    }

    @Test
    fun `as alturas do cromo batem com o contrato do design system`() {
        assertEquals(34.dp, AppChrome.titleBar)
        assertEquals(34.dp, AppChrome.toolbar)
        assertEquals(30.dp, AppChrome.statusBar)
        assertEquals(28.dp, AppChrome.control)
        assertEquals(28.dp, AppChrome.updateStrip)
        assertEquals(AppChrome.titleBar, AppChrome.toolbar)
        assertEquals(24.dp, AppChrome.hud)
    }
}
