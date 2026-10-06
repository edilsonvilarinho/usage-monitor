package com.usagemonitor.presentation.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.usagemonitor.ApplyWindowMinimumSize
import com.usagemonitor.AutoStartManager
import com.usagemonitor.ScreenWorkArea
import com.usagemonitor.activateWindow
import com.usagemonitor.awaitAwtEventTurn
import com.usagemonitor.postAwtWindowOperation
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.isWindowOpacitySupported
import com.usagemonitor.presentation.ui.components.LocalModalReveal
import com.usagemonitor.presentation.ui.components.ModalRevealPhase
import com.usagemonitor.presentation.ui.components.ModalRevealState
import com.usagemonitor.presentation.ui.components.modalFilamentWindowAlpha
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.presentation.ui.theme.appTweenSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * O que toda janela modal recebe igual: ícone, tema, escala, movimento e a área
 * útil da tela.
 *
 * Montado **uma vez** em `runUsageMonitor` e passado inteiro: eram cinco argumentos
 * repetidos em nove janelas, e a janela que esquecesse um deles — a escala, por
 * exemplo — renderizaria errado sem erro nenhum.
 */
internal data class ModalWindowEnvironment(
    val iconImage: Painter?,
    val themePreset: AppThemePreset,
    val uiScalePercent: Int,
    val motion: AppMotionPolicy,
    val screenWorkArea: ScreenWorkArea,
    val breadcrumbs: BreadcrumbRecorder,
    /**
     * O arranque já assentou e as janelas que pedem pré-aquecimento podem nascer
     * escondidas (ver [AppDialogWindow]). Nasce `false`: o arranque já leva de 5 a
     * 8 s, e janela nenhuma disputa a CPU com ele.
     */
    val prewarmReady: Boolean = false
)

/**
 * `true` enquanto a janela modal está de fato na tela — e não só pedida.
 *
 * Existe para o conteúdo que roda laço próprio (a demo animada da Ajuda): a
 * janela escondida continua composta, e sem esta leitura o laço seguiria
 * decodificando quadros que ninguém vê.
 */
internal val LocalModalWindowOnScreen = compositionLocalOf { true }

/**
 * Janela modal com moldura própria: Histórico, Sessões, Time, Configurações,
 * Ajuda e as demais.
 *
 * **A janela nasce na primeira abertura e depois só se esconde.** Fechar
 * destruía a janela, e reabrir recriava peer nativo, contexto do Skia e a
 * composição inteira — medido no Windows 11 com a JVM aquecida: 200 a 460 ms
 * até o primeiro quadro, contra 30 a 46 ms para reexibir uma janela escondida.
 * Era essa espera que fazia os modais parecerem lentos. Os laços ao vivo não
 * dependem disso: quem os para é o `closeWindow()` de cada ViewModel, chamado
 * por quem fecha, como antes.
 *
 * **A entrada espera o primeiro quadro pintado.** A janela aparece
 * **transparente**, o host aguarda dois quadros e só então toca o E9
 * (filamentos de plasma, [AppGargantuaTokens.filamentOpenMillis]): a moldura
 * esmaece em ~50 ms e um filamento corre sob cada linha marcada com
 * `appModalRevealRow`, em ordem de leitura, revelando o texto atrás da cabeça.
 * A opacidade é a da janela AWT, e não do conteúdo: conteúdo esmaecendo numa
 * janela opaca mostraria o fundo dela, não o que está atrás. A escala de 0,96
 * que a entrada fazia saiu com o E9 — o dado não se move, só é revelado.
 *
 * **Todo fechamento passa pelo mesmo caminho**: ×, Alt+F4, Esc e os botões
 * "Fechar" do conteúdo só chamam [onCloseRequest], quem chama baixa [visible], e
 * é a queda de [visible] que recolhe os filamentos
 * ([AppGargantuaTokens.filamentCloseMillis]), esmaece e esconde (ver
 * [ModalWindowHost]).
 *
 * Com "Reduzir animações", fora do Windows ou sem translucidez de janela, abre e
 * fecha na hora ([shouldAnimateModalWindow]).
 *
 * **[prewarm] tira a primeira abertura do caminho do clique.** Mesmo viva, a
 * janela pagava na primeira abertura o peer nativo, o contexto do Skia, a carga
 * das classes do conteúdo e o primeiro layout — a trilha do 41.6.0-beta.2 media
 * 230 a 515 ms só do pedido ao quadro, fora a criação da janela, contra 42 a 89 ms
 * das reaberturas. Com [ModalWindowEnvironment.prewarmReady], a janela nasce,
 * aparece **transparente e sem foco** por dois quadros e se esconde: a primeira
 * abertura de verdade vira reabertura. Um pré-aquecimento por vez, e só onde a
 * janela esmaece ([shouldPrewarmModalWindow]). Opt-in porque cada janela viva
 * guarda contexto de GPU: só as abertas com frequência pedem.
 *
 * [diagnosticName] vai para a trilha junto com o tempo até o primeiro quadro,
 * e é fixo de propósito: o título pode carregar o apelido do perfil, que costuma
 * ser o e-mail da conta, e a trilha vira issue pública.
 */
@Composable
internal fun AppDialogWindow(
    visible: Boolean,
    title: String,
    state: WindowState,
    environment: ModalWindowEnvironment,
    diagnosticName: String,
    minWidthDp: Int,
    minHeightDp: Int,
    onCloseRequest: () -> Unit,
    /** Muda a cada pedido de abertura: traz para a frente a janela que já está aberta. */
    openGeneration: Int = 0,
    prewarm: Boolean = false,
    content: @Composable () -> Unit
) {
    val host = rememberModalWindowHost(visible, openGeneration, prewarmNow(prewarm, environment)) ?: return

    Window(
        onCloseRequest = onCloseRequest,
        title = title,
        icon = environment.iconImage,
        state = state,
        visible = host.onScreen,
        resizable = true,
        undecorated = true,
        onKeyEvent = { event -> closesModal(event, onCloseRequest) }
    ) {
        ModalWindowBody(
            host = host,
            title = title,
            windowState = state,
            environment = environment,
            diagnosticName = diagnosticName,
            minWidthDp = minWidthDp,
            minHeightDp = minHeightDp,
            onCloseRequest = onCloseRequest,
            content = content
        )
    }
}

/**
 * A mesma janela como diálogo do sistema — sem botão na barra de tarefas e sem
 * maximizar. É o que Configurações, Chaves, Ajuda e Novidades já eram.
 */
@Composable
internal fun AppDialogWindow(
    visible: Boolean,
    title: String,
    state: DialogState,
    environment: ModalWindowEnvironment,
    diagnosticName: String,
    minWidthDp: Int,
    minHeightDp: Int,
    onCloseRequest: () -> Unit,
    openGeneration: Int = 0,
    prewarm: Boolean = false,
    content: @Composable () -> Unit
) {
    val host = rememberModalWindowHost(visible, openGeneration, prewarmNow(prewarm, environment)) ?: return

    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = title,
        icon = environment.iconImage,
        state = state,
        visible = host.onScreen,
        resizable = true,
        undecorated = true,
        onKeyEvent = { event -> closesModal(event, onCloseRequest) }
    ) {
        ModalWindowBody(
            host = host,
            title = title,
            windowState = null,
            environment = environment,
            diagnosticName = diagnosticName,
            minWidthDp = minWidthDp,
            minHeightDp = minHeightDp,
            onCloseRequest = onCloseRequest,
            content = content
        )
    }
}

/** O que o pedido de fora diz: aberta ou não, e qual pedido de abertura é este. */
private data class ModalRequest(val visible: Boolean, val generation: Int)

/**
 * O estado que atravessa a fronteira entre a composição do app e a da janela.
 *
 * **O pedido chega por fluxo, e não por recomposição.** Janela escondida não
 * recompõe — o relógio de quadros dela para junto com a pintura —, e a primeira
 * versão, com um `LaunchedEffect(visible)` dentro da janela, abria e fechava uma
 * vez e nunca mais reabria: o efeito não via `visible` voltar a `true`. A sonda da
 * janela real pegou; os testes de componente, que não têm janela, não teriam
 * como. A corrotina que coleta [requests] nasce na primeira composição, que é
 * síncrona, e o despacho de corrotina continua vivo com a janela escondida.
 */
private class ModalWindowHost(initial: ModalRequest) {
    val requests = MutableStateFlow(initial)

    /** Nasceu pelo pré-aquecimento, e não por um pedido de abertura. */
    val bornForPrewarm: Boolean = !initial.visible

    /** A janela AWT está visível — ainda que transparente, entrando ou saindo. */
    var onScreen by mutableStateOf(false)

    /**
     * O que o conteúdo lê como "na tela". Vira `true` só depois do primeiro quadro
     * e `false` antes de a janela se esconder, enquanto ela ainda recompõe.
     */
    var contentOnScreen by mutableStateOf(false)

    var opens: Int = 0
}

/**
 * `null` até o primeiro pedido de abertura — ou até o pré-aquecimento, quando a
 * janela o pede: janela que nunca foi aberta não existe. Daí em diante ela só se
 * esconde.
 */
@Composable
private fun rememberModalWindowHost(visible: Boolean, openGeneration: Int, prewarm: Boolean): ModalWindowHost? {
    val request = ModalRequest(visible, openGeneration)
    val holder = remember { LastValue<ModalWindowHost>() }
    if (holder.value == null && (visible || prewarm)) {
        holder.value = ModalWindowHost(request)
    }
    val host = holder.value ?: return null
    SideEffect {
        host.requests.value = request
    }
    return host
}

@Composable
private fun WindowScope.ModalWindowBody(
    host: ModalWindowHost,
    title: String,
    windowState: WindowState?,
    environment: ModalWindowEnvironment,
    diagnosticName: String,
    minWidthDp: Int,
    minHeightDp: Int,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    val opacitySupported = remember { isWindowOpacitySupported() }
    val platform = remember { AutoStartManager.currentPlatform() }
    val reveal = remember { ModalRevealState() }
    val currentEnvironment by rememberUpdatedState(environment)
    val currentDiagnosticName by rememberUpdatedState(diagnosticName)

    LaunchedEffect(host) {
        if (host.bornForPrewarm) {
            prewarmWindow(window, host, currentEnvironment.breadcrumbs, currentDiagnosticName)
        }
        // `collectLatest`: um pedido novo cancela a transição em curso, como
        // reabrir no meio da saída. Um pedido que chegou durante o pré-aquecimento
        // espera no fluxo e é atendido aqui.
        host.requests.collectLatest { request ->
            awaitAwtEventTurn()
            val motion = currentEnvironment.motion
            val animated = shouldAnimateModalWindow(motion, opacitySupported, platform)
            // Aba, faixa e dado novo só repetem o E9 numa janela que abriu com ele.
            reveal.replayEnabled = animated
            if (request.visible) {
                if (host.onScreen) {
                    // Já na tela, ou saindo: volta à opacidade cheia e vem para a
                    // frente, sem refazer a entrada.
                    host.contentOnScreen = true
                    reveal.settle()
                    if (animated) {
                        fadeWindow(window, target = 1f, durationMillis = AppMotion.fast, motion)
                    } else {
                        setWindowOpacity(window, 1f)
                    }
                    awaitAwtEventTurn()
                    activateWindow(window)
                    return@collectLatest
                }

                val requestedAt = System.nanoTime()
                if (animated) {
                    setWindowOpacity(window, 0f)
                    reveal.begin(ModalRevealPhase.OPENING)
                } else {
                    setWindowOpacity(window, 1f)
                    reveal.settle()
                }
                host.onScreen = true
                // Dois quadros: o primeiro recompõe o que mudou com a janela
                // escondida, o segundo garante pixel pintado. O teto existe para
                // janela minimizada, que não recebe quadro e ficaria transparente
                // para sempre.
                withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MILLIS) {
                    withFrameNanos { }
                    withFrameNanos { }
                }
                awaitAwtEventTurn()
                host.contentOnScreen = true
                host.opens += 1
                currentEnvironment.breadcrumbs.record(
                    BreadcrumbCategory.NAVIGATION,
                    modalOpenedBreadcrumb(
                        diagnosticName = currentDiagnosticName,
                        elapsedMillis = (System.nanoTime() - requestedAt) / 1_000_000,
                        firstOpen = host.opens == 1
                    )
                )
                activateWindow(window)
                if (animated) {
                    playFilaments(window, reveal, ModalRevealPhase.OPENING, AppGargantuaTokens.filamentOpenMillis, motion)
                }
            } else if (host.onScreen) {
                if (animated) {
                    playFilaments(window, reveal, ModalRevealPhase.CLOSING, AppGargantuaTokens.filamentCloseMillis, motion)
                }
                // O conteúdo larga os laços próprios enquanto a janela ainda
                // recompõe: escondida, a recomposição que os desligaria não vem.
                host.contentOnScreen = false
                withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MILLIS) {
                    withFrameNanos { }
                }
                // A opacidade fica onde parou: a próxima abertura a define antes
                // de mostrar a janela. Restaurá-la aqui, antes de o esconder
                // chegar à janela AWT, pintaria um quadro cheio no meio da saída.
                host.onScreen = false
            }
        }
    }

    ApplyWindowMinimumSize(
        window = window,
        widthDp = minWidthDp,
        heightDp = minHeightDp,
        uiScalePercent = environment.uiScalePercent,
        workArea = environment.screenWorkArea
    )
    AppTheme(
        preset = environment.themePreset,
        uiScalePercent = environment.uiScalePercent,
        motion = environment.motion
    ) {
        DesktopDialogFrame(
            title = title,
            iconPainter = environment.iconImage,
            windowState = windowState,
            onCloseRequest = onCloseRequest
        ) {
            CompositionLocalProvider(
                LocalModalWindowOnScreen provides host.contentOnScreen,
                LocalModalReveal provides reveal
            ) {
                content()
            }
        }
    }
}

/**
 * Se a janela modal anima entrada e saída.
 *
 * Função pura pelo mesmo motivo de `appSpringSpec`: a regra fica testável sem
 * abrir janela. Sem translucidez a janela não tem como esmaecer, e animar só a
 * escala dentro de uma janela opaca é exatamente o salto que o host existe para
 * eliminar.
 *
 * **Só no Windows**, onde o esmaecimento foi medido (issue #340). No X11 a
 * opacidade da janela é a propriedade `_NET_WM_WINDOW_OPACITY`, aplicada pelo
 * compositor, e voltar a 1 é *apagar* a propriedade: no elementary OS o modal de
 * Configurações ficou translúcido depois de a opacidade ter mudado, e a janela que
 * nunca sai de 1 não depende de o compositor atender a remoção.
 */
internal fun shouldAnimateModalWindow(
    motion: AppMotionPolicy,
    opacitySupported: Boolean,
    platform: AutoStartManager.Platform
): Boolean {
    return platform == AutoStartManager.Platform.WINDOWS && opacitySupported && !motion.reduced
}

/**
 * Se a janela pode nascer pré-aquecida. Função pura, testada pela lista de
 * plataformas (issue #340): mostrar a janela transparente só esconde alguma coisa
 * onde a opacidade foi medida — no Windows. No X11 a opacidade é pedido ao
 * compositor, e uma janela "invisível" que ele não atendesse piscaria no arranque.
 */
internal fun shouldPrewarmModalWindow(opacitySupported: Boolean, platform: AutoStartManager.Platform): Boolean {
    return platform == AutoStartManager.Platform.WINDOWS && opacitySupported
}

@Composable
private fun prewarmNow(requested: Boolean, environment: ModalWindowEnvironment): Boolean {
    val allowed = remember {
        shouldPrewarmModalWindow(isWindowOpacitySupported(), AutoStartManager.currentPlatform())
    }
    return requested && allowed && environment.prewarmReady
}

/** Um pré-aquecimento por vez: juntos, eles disputariam a mesma thread de interface. */
private val modalPrewarmLock = Mutex()

/**
 * Mostra a janela transparente e sem foco por dois quadros e a esconde. É o que
 * paga, fora do clique, o peer nativo, o contexto do Skia, a carga das classes e
 * o primeiro layout. `contentOnScreen` fica `false`: conteúdo com laço próprio não
 * o liga. Não conta como abertura.
 */
private suspend fun prewarmWindow(
    window: java.awt.Window,
    host: ModalWindowHost,
    breadcrumbs: BreadcrumbRecorder,
    diagnosticName: String
) {
    modalPrewarmLock.withLock {
        awaitAwtEventTurn()
        // Pedida enquanto esperava a vez: a abertura normal cobre tudo.
        if (host.requests.value.visible) {
            return
        }
        val startedAt = System.nanoTime()
        val wasFocusable = window.focusableWindowState
        window.focusableWindowState = false
        setWindowOpacity(window, 0f)
        host.onScreen = true
        try {
            withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MILLIS) {
                withFrameNanos { }
                withFrameNanos { }
            }
        } finally {
            withContext(NonCancellable) {
                awaitAwtEventTurn()
                host.onScreen = false
                window.focusableWindowState = wasFocusable
            }
        }
        breadcrumbs.record(
            BreadcrumbCategory.NAVIGATION,
            modalPrewarmedBreadcrumb(diagnosticName, (System.nanoTime() - startedAt) / 1_000_000)
        )
    }
}

/** A linha da trilha do pré-aquecimento; não conta como abertura. */
internal fun modalPrewarmedBreadcrumb(diagnosticName: String, elapsedMillis: Long): String {
    return "janela $diagnosticName pré-aquecida em $elapsedMillis ms"
}

/** A linha da trilha com o tempo até o primeiro quadro. Sem título: ver [AppDialogWindow]. */
internal fun modalOpenedBreadcrumb(diagnosticName: String, elapsedMillis: Long, firstOpen: Boolean): String {
    val kind = if (firstOpen) "primeira abertura" else "reabertura"
    return "janela $diagnosticName pintada em $elapsedMillis ms ($kind)"
}

/** Esc fecha a janela. Só chega aqui o que nenhum elemento focado consumiu. */
private fun closesModal(event: KeyEvent, onCloseRequest: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown || event.key != Key.Escape) {
        return false
    }
    onCloseRequest()
    return true
}

private suspend fun fadeWindow(
    window: java.awt.Window,
    target: Float,
    durationMillis: Int,
    motion: AppMotionPolicy
) {
    val start = runCatching { window.opacity }.getOrDefault(target)
    val easing = if (target > start) AppMotion.enterEasing else AppMotion.exitEasing
    animate(
        initialValue = start,
        targetValue = target,
        animationSpec = appTweenSpec(durationMillis, motion, easing)
    ) { value, _ ->
        postAwtWindowOperation { if (window.isDisplayable) setWindowOpacity(window, value) }
    }
}

/**
 * Toca uma fase do E9: o relógio vai de 0 a 1 em [durationMillis], linear — a
 * curva de cada linha está no quadro puro — e a opacidade da janela segue
 * [modalFilamentWindowAlpha] no mesmo quadro. A abertura termina parada mesmo
 * cancelada (um fechar no meio): a saída parte do conteúdo inteiro.
 */
private suspend fun playFilaments(
    window: java.awt.Window,
    reveal: ModalRevealState,
    phase: ModalRevealPhase,
    durationMillis: Int,
    motion: AppMotionPolicy
) {
    reveal.begin(phase)
    try {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = appTweenSpec(durationMillis, motion, LinearEasing)
        ) { value, _ ->
            reveal.progress = value
            val alpha = modalFilamentWindowAlpha(phase, value)
            postAwtWindowOperation { if (window.isDisplayable) setWindowOpacity(window, alpha) }
        }
    } finally {
        if (phase == ModalRevealPhase.OPENING) {
            reveal.settle()
        }
    }
}

private fun setWindowOpacity(window: java.awt.Window, value: Float) {
    // Plataforma sem translucidez lança aqui; ficar opaca é melhor que não abrir.
    runCatching { window.opacity = value.coerceIn(0f, 1f) }
}

/**
 * O último valor não nulo que passou por aqui.
 *
 * É o que a janela viva precisa quando quem a abre guarda o assunto num
 * anulável (a fonte do histórico): ao fechar o valor vira `null` no mesmo
 * clique, e sem a memória a janela esmaeceria sem título e sem conteúdo.
 */
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    val holder = remember { LastValue<T>() }
    if (value != null) {
        holder.value = value
    }
    return holder.value
}

private class LastValue<T : Any> {
    var value: T? = null
}

/**
 * Tempo até uma janela modal fechada estar de fato fora da tela: a saída mais
 * a folga para o esconder chegar à janela AWT. Quem precisa da tela limpa — a
 * captura do relatório de bug — espera isto.
 */
internal const val MODAL_CLOSE_SETTLE_MILLIS = AppGargantuaTokens.filamentCloseMillis + 80L

private const val FIRST_FRAME_TIMEOUT_MILLIS = 500L
