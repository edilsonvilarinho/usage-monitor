package com.usagemonitor.presentation.ui

import androidx.compose.animation.core.Animatable
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
import com.usagemonitor.ScreenWorkArea
import com.usagemonitor.activateWindow
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.isWindowOpacitySupported
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.presentation.ui.theme.appSpringSpec
import com.usagemonitor.presentation.ui.theme.appTweenSpec
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * O que toda janela modal recebe igual: ícone, tema, escala, movimento e a área
 * útil da tela.
 *
 * Montado **uma vez** em `main()` e passado inteiro: eram cinco argumentos
 * repetidos em nove janelas, e a janela que esquecesse um deles — a escala, por
 * exemplo — renderizaria errado sem erro nenhum.
 */
internal data class ModalWindowEnvironment(
    val iconImage: Painter?,
    val themePreset: AppThemePreset,
    val uiScalePercent: Int,
    val motion: AppMotionPolicy,
    val screenWorkArea: ScreenWorkArea,
    val breadcrumbs: BreadcrumbRecorder
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
 * **A entrada espera o primeiro quadro pintado.** A escala que a moldura já
 * fazia começava ao compor, dentro de uma janela que o sistema mostrava de uma
 * vez, 100% opaca — os quadros iniciais se perdiam no custo da criação e o que
 * se via era o salto da janela. Agora a janela aparece **transparente**, o host
 * aguarda dois quadros e só então esmaece a janela de 0 a 1 (tween de
 * [AppMotion.normal]) com o conteúdo crescendo de [MODAL_ENTER_SCALE] a 1 pela
 * mola `GENTLE`. A opacidade é a da janela AWT, e não do conteúdo: conteúdo
 * esmaecendo numa janela opaca mostraria o fundo dela, não o que está atrás.
 *
 * **Todo fechamento passa pelo mesmo caminho**: ×, Alt+F4, Esc e os botões
 * "Fechar" do conteúdo só chamam [onCloseRequest], quem chama baixa [visible], e
 * é a queda de [visible] que esmaece e esconde (ver [ModalWindowHost]). Antes só o × da barra
 * esmaecia; o resto fechava seco.
 *
 * Com "Reduzir animações", ou numa plataforma sem translucidez de janela
 * (alguns Linux), abre e fecha na hora.
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
    content: @Composable () -> Unit
) {
    val host = rememberModalWindowHost(visible, openGeneration) ?: return

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
    content: @Composable () -> Unit
) {
    val host = rememberModalWindowHost(visible, openGeneration) ?: return

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
 * `null` até o primeiro pedido de abertura: janela que nunca foi aberta não
 * existe. Daí em diante ela só se esconde.
 */
@Composable
private fun rememberModalWindowHost(visible: Boolean, openGeneration: Int): ModalWindowHost? {
    val request = ModalRequest(visible, openGeneration)
    val holder = remember { LastValue<ModalWindowHost>() }
    if (holder.value == null && visible) {
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
    val scale = remember { Animatable(1f) }
    val currentEnvironment by rememberUpdatedState(environment)
    val currentDiagnosticName by rememberUpdatedState(diagnosticName)

    LaunchedEffect(host) {
        // `collectLatest`: um pedido novo cancela a transição em curso, como
        // reabrir no meio da saída.
        host.requests.collectLatest { request ->
            val motion = currentEnvironment.motion
            val animated = shouldAnimateModalWindow(motion, opacitySupported)
            if (request.visible) {
                if (host.onScreen) {
                    // Já na tela, ou saindo: volta à opacidade cheia e vem para a
                    // frente, sem refazer a entrada.
                    host.contentOnScreen = true
                    if (animated) {
                        coroutineScope {
                            launch { scale.animateTo(1f, appSpringSpec(AppMotion.Springs.GENTLE, motion)) }
                            fadeWindow(window, target = 1f, durationMillis = AppMotion.fast, motion)
                        }
                    } else {
                        setWindowOpacity(window, 1f)
                        scale.snapTo(1f)
                    }
                    activateWindow(window)
                    return@collectLatest
                }

                val requestedAt = System.nanoTime()
                if (animated) {
                    setWindowOpacity(window, 0f)
                    scale.snapTo(MODAL_ENTER_SCALE)
                } else {
                    setWindowOpacity(window, 1f)
                    scale.snapTo(1f)
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
                    coroutineScope {
                        launch { scale.animateTo(1f, appSpringSpec(AppMotion.Springs.GENTLE, motion)) }
                        fadeWindow(window, target = 1f, durationMillis = AppMotion.normal, motion)
                    }
                }
            } else if (host.onScreen) {
                if (animated) {
                    coroutineScope {
                        launch {
                            scale.animateTo(
                                MODAL_ENTER_SCALE,
                                appTweenSpec(MODAL_EXIT_MILLIS, motion, AppMotion.exitEasing)
                            )
                        }
                        fadeWindow(window, target = 0f, durationMillis = MODAL_EXIT_MILLIS, motion)
                    }
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
            onCloseRequest = onCloseRequest,
            contentScale = { scale.value }
        ) {
            CompositionLocalProvider(LocalModalWindowOnScreen provides host.contentOnScreen) {
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
 */
internal fun shouldAnimateModalWindow(motion: AppMotionPolicy, opacitySupported: Boolean): Boolean {
    return opacitySupported && !motion.reduced
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
        setWindowOpacity(window, value)
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

/** Mesma escala de partida do `AppMenu` e do `AppDialog`. */
internal const val MODAL_ENTER_SCALE = 0.96f

/** Um pouco mais longa que a saída de menu: a janela é uma superfície maior. */
private const val MODAL_EXIT_MILLIS = 140

/**
 * Tempo até uma janela modal fechada estar de fato fora da tela: a saída mais
 * a folga para o esconder chegar à janela AWT. Quem precisa da tela limpa — a
 * captura do relatório de bug — espera isto.
 */
internal const val MODAL_CLOSE_SETTLE_MILLIS = MODAL_EXIT_MILLIS + 80L

private const val FIRST_FRAME_TIMEOUT_MILLIS = 500L
