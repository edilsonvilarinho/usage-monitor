package com.usagemonitor.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.DialogModalityType
import com.usagemonitor.AutoStartManager
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy

/*
 * As decisões da janela modal, fora de `AppDialogWindow.kt`. Aquele arquivo é cola
 * de janela (`DialogWindow`/`Window`, opacidade AWT, pré-aquecimento) e sai do
 * Kover por nome (onda 6.5 do plano `cobertura-95-execucao.md`); o que decide
 * alguma coisa mora aqui e segue contado e testado.
 */

/**
 * `true` enquanto a janela modal está de fato na tela — e não só pedida.
 *
 * Existe para o conteúdo que roda laço próprio (a demo animada da Ajuda): a
 * janela escondida continua composta, e sem esta leitura o laço seguiria
 * decodificando quadros que ninguém vê.
 */
internal val LocalModalWindowOnScreen = compositionLocalOf { true }

/**
 * A modalidade dos diálogos (issue #434).
 *
 * **No COSMIC, `Modeless`.** O diálogo modal bloqueia as outras janelas do app,
 * e quem o mantém visível é o empilhamento do gerenciador de janelas, que põe o
 * modal acima do que ele bloqueia. O COSMIC não faz isso: medido no
 * `cosmic-comp` 1.10.0 aninhado, as Configurações vieram para a frente das
 * Novidades (modal) e deixaram de receber clique, sem nada na tela explicando
 * por quê — até as Novidades, escondidas atrás, serem fechadas.
 *
 * Fora dele, `DocumentModal`, o padrão do Compose e o de sempre (regra do #340).
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun dialogModality(cosmic: Boolean): DialogModalityType =
    if (cosmic) DialogModalityType.Modeless else DialogModalityType.DocumentModal

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

/** O que um pedido de [AppDialogWindow] faz com a janela, dado se ela já está na tela. */
internal enum class ModalRequestAction {
    /** Já na tela, ou saindo: volta à opacidade cheia e vem para a frente. */
    REFOCUS,

    /** Escondida e pedida: mostra, com a entrada animada se a plataforma a anima. */
    OPEN,

    /** Na tela e não pedida: recolhe os filamentos e esconde. */
    CLOSE,

    /** Escondida e não pedida: nada a fazer. */
    IGNORE
}

/**
 * A decisão de um pedido de abertura ou fechamento. Função pura, sem janela: a
 * regra de que pedir uma janela já na tela só a traz para a frente, e de que
 * pedir o fechamento de uma janela escondida não faz nada, fica testável.
 */
internal fun modalRequestAction(visible: Boolean, onScreen: Boolean): ModalRequestAction = when {
    visible && onScreen -> ModalRequestAction.REFOCUS
    visible -> ModalRequestAction.OPEN
    onScreen -> ModalRequestAction.CLOSE
    else -> ModalRequestAction.IGNORE
}

/**
 * Se o pré-aquecimento acontece: pedido por quem abre, permitido pela plataforma
 * e com o arranque já assentado. Qualquer um dos três falso adia a janela para a
 * primeira abertura de verdade.
 */
internal fun modalPrewarmRequested(requested: Boolean, allowed: Boolean, ready: Boolean): Boolean =
    requested && allowed && ready

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
internal fun closesModal(event: KeyEvent, onCloseRequest: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown || event.key != Key.Escape) {
        return false
    }
    onCloseRequest()
    return true
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

internal class LastValue<T : Any> {
    var value: T? = null
}
