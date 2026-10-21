package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingWindow
import com.usagemonitor.data.diagnostics.AppLogFile
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.Point
import javax.swing.WindowConstants
import kotlin.math.roundToInt

/**
 * A moldura da janela da HUD, com **dois caminhos** (issue #426).
 *
 * No Windows e no macOS, o `Window` de alto nível do Compose, que é o que
 * sempre foi usado e o que está medido em produção.
 *
 * No Linux, o caminho de baixo nível, que permite **escolher a
 * `GraphicsConfiguration`** com que a janela nasce. É isso que o de alto nível
 * não deixa fazer, e é o que o defeito exige: toda `GraphicsConfiguration`
 * padrão do X11 medida aqui é de 24 bits, e janela de 24 bits não tem canal
 * alfa. Nascendo no primário e sendo posicionada no secundário, a janela sofre
 * a troca de `GraphicsDevice` do AWT, perde a configuração com alfa e vira um
 * retângulo preto.
 *
 * **Por que não remendar depois.** Recuperar o alfa numa janela já aberta
 * obriga o AWT a recriar a janela nativa (a visual é propriedade dela). Medido:
 * o remendo devolve os 32 bits, mas o Compose para de emitir quadros — a barra
 * fica correta e **congelada**, e só volta a pintar na próxima recriação. Com a
 * janela nascendo certa, nada disso acontece.
 *
 * **Por que não no Windows também.** A conversão de dp para pixel da janela, o
 * ícone e o fechar são do `Window` de alto nível, e ali eles estão medidos em
 * HiDPI. Comportamento nativo medido numa plataforma nasce restrito a ela
 * (regra do #340): o Linux é o que foi medido, e é só ele que muda.
 */
@Composable
internal fun HudWindowFrame(
    /** Só no Linux; nas outras plataformas o caminho de alto nível é usado. */
    useExplicitConfiguration: Boolean,
    /** A configuração com alfa do monitor da HUD; `null` deixa o AWT escolher. */
    graphicsConfiguration: GraphicsConfiguration?,
    state: WindowState,
    /**
     * O tamanho e a posição **desta composição**, não os do [state].
     *
     * O `state` só é atualizado num efeito, depois da composição: no instante
     * em que a janela renasce ele ainda guarda a posição do arrasto. A janela
     * nascia então fora do monitor da própria configuração, o AWT disparava a
     * troca de tela e a devolvia para a configuração **padrão** daquele
     * monitor, de 24 bits — segunda tarja preta e segundo renascimento por
     * travessia (medido na #426). Usados só no caminho de baixo nível.
     */
    size: DpSize,
    position: WindowPosition,
    icon: Painter?,
    iconImage: java.awt.Image?,
    onCloseRequest: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    /** Em arrasto: o movimento é do app, e a sonda do sistema não o registra (#430). */
    dragging: Boolean,
    content: @Composable FrameWindowScope.() -> Unit
) {
    if (!useExplicitConfiguration) {
        Window(
            onCloseRequest = onCloseRequest,
            title = HUD_WINDOW_TITLE,
            icon = icon,
            state = state,
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = true,
            onKeyEvent = onKeyEvent,
            content = content
        )
        return
    }
    // Lidos por referência: o `create` roda uma vez só, e a lambda que ele
    // guarda não pode congelar a versão do primeiro quadro.
    val currentClose by rememberUpdatedState(onCloseRequest)
    val currentDragging by rememberUpdatedState(dragging)
    // Uma por janela: cada renascimento é uma janela nova, com o teto zerado.
    val moveProbe = remember { HudSystemMoveProbe() }
    // [size] e [position] chegam prontos do host, lidos no corpo da composição
    // dele. Ler `state.size`/`state.position` aqui dentro do `update` não
    // registraria dependência no Compose: a sincronização rodava uma vez e
    // nunca mais, e depois de um arrasto a janela ficava no tamanho do gesto
    // enquanto o app pedia outro (#426).
    val floating = state.placement == WindowPlacement.Floating
    // `SwingWindow` e o `Window` de baixo nivel com o nome de hoje: e o unico
    // que aceita a janela ja construida, e por isso o unico que deixa escolher
    // a `GraphicsConfiguration` com que ela nasce.
    SwingWindow(
        visible = true,
        onPreviewKeyEvent = { false },
        onKeyEvent = onKeyEvent,
        create = {
            ComposeWindow(graphicsConfiguration).apply {
                title = HUD_WINDOW_TITLE
                defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                // `isTransparent` exige a janela sem decoração e ainda não
                // exibida; esta ordem é a que o AWT aceita.
                isUndecorated = true
                isTransparent = true
                isResizable = false
                isAlwaysOnTop = true
                iconImage?.let { image -> setIconImage(image) }
                applyHudWindowBounds(this, floating, size, position, moveProbe)
                addWindowListener(object : java.awt.event.WindowAdapter() {
                    override fun windowClosing(event: java.awt.event.WindowEvent) {
                        currentClose()
                    }
                })
                // Só leitura: o que o compositor fez com a posição pedida (#430).
                addComponentListener(object : java.awt.event.ComponentAdapter() {
                    override fun componentMoved(event: java.awt.event.ComponentEvent) {
                        moveProbe.moved(event.component.location, event.component.size, currentDragging)
                    }
                })
            }
        },
        dispose = ::disposeHudWindowAfterSuccessor,
        update = { window -> applyHudWindowBounds(window, floating, size, position, moveProbe) },
        content = content
    )
}

/** O título é o que a segunda instância e a bandeja procuram; não muda. */
internal const val HUD_WINDOW_TITLE = "Usage Monitor"

/**
 * Tamanho e posição do [state] na janela AWT.
 *
 * Um dp vale um pixel lógico da AWT: a HUD já aplica a escala da interface na
 * geometria (`uiScaleFactor`), e a medição do `hud-window` confirma pedido e
 * obtido idênticos. `WindowPlacement` e minimizar não entram — a HUD não tem
 * nem um nem outro.
 */
private fun applyHudWindowBounds(
    window: ComposeWindow,
    floating: Boolean,
    size: DpSize,
    position: WindowPosition,
    moveProbe: HudSystemMoveProbe
) {
    if (!floating) {
        return
    }
    val dimension = Dimension(size.width.value.roundToInt(), size.height.value.roundToInt())
    if (dimension.width > 0 && dimension.height > 0 && window.size != dimension) {
        window.size = dimension
    }
    if (position is WindowPosition.Absolute) {
        val point = Point(position.x.value.roundToInt(), position.y.value.roundToInt())
        moveProbe.requested(point)
        if (window.location != point) {
            window.location = point
        }
    }
}

/**
 * Em que `GraphicsConfiguration` a janela da HUD nasce, e quando ela **renasce**
 * (issue #426).
 *
 * Nasce na configuração com alfa do monitor em que a HUD vive. Renasce em dois
 * casos: o monitor mudou, ou o alfa caiu — o segundo é o arrasto que cruza a
 * fronteira e **volta** para o mesmo monitor, em que o id final é o mesmo mas o
 * AWT já trocou a configuração no caminho.
 *
 * O orçamento existe porque num ambiente que simplesmente não compõe alfa o
 * renascimento nunca o traria de volta, e repetir seria um pisca eterno. Depois
 * dele, quem assume é o recorte de emergência ([HudHitRegionMode]).
 *
 * **No COSMIC a janela renasce onde o compositor a pôs** (#430,
 * [followsCompositor]). Ele ignora a posição pedida e mapeia a janela no
 * monitor que escolhe; renascer de novo no monitor gravado repetia o erro, e o
 * relator da #416 viu as três tentativas acabarem em meio segundo, sem alfa.
 */
@Stable
internal class HudWindowRebirth(
    internal val explicit: Boolean,
    /** Só no COSMIC ([hudFollowsCompositorScreen]); fora dele, o renascimento é o da #426. */
    internal val followsCompositor: Boolean = false,
    private val appLog: AppLogFile = AppLogFile()
) {
    var configuration by mutableStateOf<GraphicsConfiguration?>(null)
        internal set

    /** Entra na chave da composição: mudar isto faz a janela nascer de novo. */
    var generation by mutableStateOf(0)
        private set

    private var rebuilds = 0

    /** O esgotamento vira **uma** linha por ciclo de orçamento, não uma por sondagem. */
    private var exhaustionReported = false

    /**
     * O monitor que esta classe acabou de adotar. A troca de monitor que ela
     * mesma causou **não** zera o orçamento: se zerasse, um compositor que
     * alternasse os monitores viraria um pisca eterno (#430).
     */
    private var adoptedScreenId: String? = null

    internal fun resetBudget() {
        rebuilds = 0
        exhaustionReported = false
    }

    /** O monitor da HUD mudou. Só a troca vinda de fora da adoção zera o orçamento. */
    internal fun onScreenChanged(screenId: String?) {
        val adopted = adoptedScreenId
        adoptedScreenId = null
        if (adopted != null && adopted == screenId) {
            return
        }
        resetBudget()
    }

    /**
     * O alfa caiu. Registra e faz a janela renascer, se ainda há orçamento e o
     * gesto não está em curso — recriar a janela no meio de um arrasto
     * derrubaria os eventos de ponteiro.
     *
     * [onAdopt] recebe o monitor onde a janela está quando ele não é o
     * [configuredScreenId] e o ambiente segue o compositor; quem chama passa a
     * HUD para ele (e grava), e a janela renasce com a configuração **dele**.
     */
    suspend fun onAlphaLost(
        state: HudTranslucencyState,
        dragging: Boolean,
        configuredScreenId: String? = null,
        screens: () -> List<ScreenInfo> = ::availableScreens,
        onAdopt: (ScreenInfo) -> Unit = {}
    ) {
        if (!explicit || dragging) {
            return
        }
        if (rebuilds >= HUD_ALPHA_REBUILD_LIMIT) {
            if (!exhaustionReported) {
                exhaustionReported = true
                recordHudAlphaBudgetExhausted(state, rebuilds, appLog)
            }
            return
        }
        rebuilds += 1
        val adopted = hudRebirthScreen(followsCompositor, configuredScreenId, state.bounds, screens())
        // Registrado **antes** do renascimento: o bump cancela esta corrotina.
        if (adopted == null) {
            recordHudAlphaRebuild(state, rebuilds, appLog)
        } else {
            recordHudAlphaAdoption(state, configuredScreenId, adopted.id, rebuilds, appLog)
            adoptedScreenId = adopted.id
            onAdopt(adopted)
        }
        generation += 1
    }
}

/** A configuração com alfa do monitor da HUD, e o orçamento zerado a cada troca. */
@Composable
internal fun rememberHudWindowRebirth(screenId: String?, platform: AutoStartManager.Platform): HudWindowRebirth {
    val rebirth = remember(platform) {
        HudWindowRebirth(
            explicit = hudPicksGraphicsConfiguration(platform),
            followsCompositor = hudFollowsCompositorScreen(platform, linuxGraphicsEnvironment().desktop)
        )
    }
    rebirth.configuration = remember(screenId, rebirth.explicit) {
        if (rebirth.explicit) translucencyCapableConfigurationFor(screenId) else null
    }
    LaunchedEffect(screenId) { rebirth.onScreenChanged(screenId) }
    return rebirth
}

/**
 * Se a HUD renasce no monitor onde o **compositor** pôs a janela, em vez do
 * monitor gravado (issue #430).
 *
 * **Só no COSMIC**, que é onde o defeito foi visto: ele ignora a posição pedida
 * na abertura (`pedido 404,0 obtido 731,163` em 12 de 13 aberturas do relator da
 * #416). No KWin, medido na #426, a posição é respeitada e o renascimento no
 * monitor gravado é o certo; no GNOME e no Pantheon nada foi medido. Liberar
 * outro compositor é decisão com medição (regra do #340).
 *
 * [desktop] é o `XDG_CURRENT_DESKTOP` verbatim, uma lista separada por `:`.
 */
internal fun hudFollowsCompositorScreen(platform: AutoStartManager.Platform, desktop: String?): Boolean {
    if (platform != AutoStartManager.Platform.LINUX || desktop == null) {
        return false
    }
    return desktop.split(':').any { item -> item.trim().equals("COSMIC", ignoreCase = true) }
}

/**
 * O monitor em que a janela deve renascer quando não é o configurado; `null`
 * mantém o configurado (issue #430).
 *
 * É o monitor com a maior parte da janela ([screenForRect], a mesma regra de
 * "monitor de uma janela" do resto do app). Renascer ali com a configuração e a
 * posição encaixada **daquele** monitor é o que converge: renascer com a
 * configuração de um monitor e a posição de outro é a armadilha da A8 da #426.
 */
internal fun hudRebirthScreen(
    followsCompositor: Boolean,
    configuredScreenId: String?,
    actualBounds: java.awt.Rectangle?,
    screens: List<ScreenInfo>
): ScreenInfo? {
    if (!followsCompositor || actualBounds == null) {
        return null
    }
    val actual = screenForRect(
        screens,
        actualBounds.x.dp,
        actualBounds.y.dp,
        DpSize(actualBounds.width.dp, actualBounds.height.dp)
    ) ?: return null
    return actual.takeIf { screen -> screen.id != configuredScreenId }
}

/**
 * Quanto a janela antiga fica na tela depois de a nova nascer (issue #426).
 *
 * Quando a HUD renasce, o Compose descarta a janela antiga **antes** de a nova
 * ficar visível e pintar o primeiro quadro: sobra um vão em que não há janela
 * nenhuma, e o usuário vê a barra **piscar**. Medido por amostragem de 20 Hz,
 * o vão aparecia em quadros isolados — curto, mas visível a cada travessia de
 * monitor.
 *
 * 150 ms cobre a criação da janela nova e o primeiro quadro do Skia com folga,
 * e é curto o bastante para as duas janelas sobrepostas não darem tempo de
 * aparecer como duas: elas ocupam exatamente a mesma área, e a nova nasce por
 * cima.
 */
internal const val HUD_WINDOW_DISPOSE_DELAY_MILLIS = 150

/**
 * Descarta a janela substituída **depois** de a sucessora estar na tela.
 *
 * Sem o atraso, o descarte roda na mesma passada da EDT em que a nova janela
 * ainda está sendo montada. Com ele, a antiga segue mostrando o último quadro
 * dela até a nova pintar — a troca deixa de piscar.
 */
private fun disposeHudWindowAfterSuccessor(window: ComposeWindow) {
    val timer = javax.swing.Timer(HUD_WINDOW_DISPOSE_DELAY_MILLIS) { window.dispose() }
    timer.isRepeats = false
    timer.start()
}
