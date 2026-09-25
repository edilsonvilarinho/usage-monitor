package com.usagemonitor.presentation.ui

import androidx.compose.runtime.Immutable
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.StalledCliSession
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.AppTone

/**
 * Um sinal de sessão CLI no balão da HUD (issue #265): contexto crescendo ou
 * saturado e sessão sem resposta. São dados que o app já tinha e que só as
 * janelas de sessões e a bandeja mostravam.
 *
 * **O texto nomeia o sinal, nunca o estado da conta.** "Atenção" já é a palavra
 * do risco de cota, e repeti-la aqui faria uma sessão com contexto crescendo ler
 * como cota perto do fim. Por isso "Contexto crescendo"/"Contexto saturado", as
 * palavras de `CliSessionsLabels.healthTitle`. E **nada diz "aguardando você"**: a
 * sessão sem resposta é o pedido do usuário esperando o **modelo**, o contrário
 * disso, e o app não vê o processo — só o transcript.
 */
@Immutable
data class HudSessionSignal(
    val text: String,
    val tone: AppTone
)

/**
 * Os sinais de uma conta, na ordem de gravidade: saturado, crescendo, sem
 * resposta. Só contas Anthropic têm sessão CLI indexada; as demais nunca têm
 * sinal. Lista vazia é "nada a dizer", e o balão não abre a seção.
 */
internal fun hudSessionSignals(
    target: UsageTargetKey,
    pulse: SessionPulse?,
    stalled: List<StalledCliSession>,
    language: AppLanguage
): List<HudSessionSignal> {
    if (target.source != ApiSource.ANTHROPIC) {
        return emptyList()
    }
    val pt = language == AppLanguage.PT
    val signals = mutableListOf<HudSessionSignal>()
    val saturated = pulse?.countOf(CliSessionHealth.SATURATED) ?: 0
    val attention = pulse?.countOf(CliSessionHealth.ATTENTION) ?: 0
    if (saturated > 0) {
        val text = if (pt) "Contexto saturado · ${sessions(saturated, pt)}" else "Context saturated · ${sessions(saturated, pt)}"
        signals += HudSessionSignal(text, AppTone.CRITICAL)
    }
    if (attention > 0) {
        val text = if (pt) "Contexto crescendo · ${sessions(attention, pt)}" else "Context growing · ${sessions(attention, pt)}"
        signals += HudSessionSignal(text, AppTone.WARNING)
    }
    // Perfil exato: sessão sem perfil não é "de todas as contas" — atribuí-la ao
    // padrão acenderia o sinal na conta errada.
    val mine = stalled.filter { session -> session.profileId == target.profileId }
    if (mine.isNotEmpty()) {
        val longest = mine.maxOf { session -> session.pendingMillis }
        val elapsed = formatActiveTime(longest)
        val text = when {
            mine.size == 1 -> CliSessionsLabels.stalledLabel(longest, language)
            pt -> "${mine.size} sem resposta · até $elapsed"
            else -> "${mine.size} with no reply · up to $elapsed"
        }
        signals += HudSessionSignal(text, AppTone.WARNING)
    }
    return signals
}

private fun sessions(count: Int, pt: Boolean): String = when {
    pt && count == 1 -> "1 sessão"
    pt -> "$count sessões"
    count == 1 -> "1 session"
    else -> "$count sessions"
}
