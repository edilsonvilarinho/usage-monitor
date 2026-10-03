package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import com.usagemonitor.presentation.ui.theme.appTweenSpec

/**
 * Trecho de modal que refaz o E9 quando o conteúdo dele troca: seção das
 * Configurações, aba, faixa de tempo, página, ordenação — e o dado que chega
 * depois da abertura, porque o trecho composto pela primeira vez com a janela
 * já parada também toca.
 *
 * Só as linhas de dentro são revisitadas; o resto da janela fica parado. Toca
 * quando o trecho nasce e a cada mudança de [replayKey], e só com a janela
 * parada na tela ([ModalRevealState.canReplay]): durante a abertura as linhas já
 * estão na cascata da janela, e numa janela que abriu seca — "Reduzir
 * animações", fora do Windows — nada repete.
 *
 * **A chave é do dado carregado, não do clique.** Trocar a faixa de tempo relê o
 * banco; a chave que muda no clique tocaria sobre o conteúdo antigo esmaecido e
 * de novo quando o novo chegasse. Para isso existe [rememberSettledRevealKey].
 * O tique do laço ao vivo não troca a chave: filamento a cada 5 s seria o
 * pisca que o laço existe para evitar.
 *
 * Fora de modal ([LocalModalReveal] nulo) é só o conteúdo.
 */
@Composable
fun AppModalRevealScope(replayKey: Any?, content: @Composable () -> Unit) {
    val parent = LocalModalReveal.current
    if (parent == null) {
        content()
        return
    }
    val scope = remember(parent) { ModalRevealState(parent = parent) }
    // Na composição, e não num efeito: o primeiro quadro do conteúdo novo já
    // sai recortado. O estado escrito aqui só é lido no desenho e no efeito.
    remember(scope, replayKey) {
        if (scope.canReplay) {
            scope.begin(ModalRevealPhase.OPENING)
        }
        replayKey
    }
    val motion = LocalAppMotionPolicy.current
    LaunchedEffect(scope, replayKey) {
        if (scope.phase != ModalRevealPhase.OPENING) return@LaunchedEffect
        try {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = appTweenSpec(AppGargantuaTokens.filamentOpenMillis, motion, LinearEasing)
            ) { value, _ -> scope.progress = value }
        } finally {
            scope.settle()
        }
    }
    CompositionLocalProvider(LocalModalReveal provides scope, content = content)
}

/**
 * A chave de [AppModalRevealScope] que só anda com o dado assentado: enquanto
 * [settled] é falso (a leitura nova ainda não chegou) devolve a última chave
 * assentada.
 */
@Composable
fun rememberSettledRevealKey(key: Any?, settled: Boolean): Any? {
    val holder = remember { SettledKeyHolder(key) }
    if (settled) {
        holder.value = key
    }
    return holder.value
}

private class SettledKeyHolder(var value: Any?)
