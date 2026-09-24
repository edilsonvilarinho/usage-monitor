package com.usagemonitor.presentation.ui

import androidx.compose.runtime.Immutable
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.compactPercentageLabel
import com.usagemonitor.presentation.ui.components.displayTitle
import com.usagemonitor.presentation.ui.components.hudQuotaShortLabel
import com.usagemonitor.presentation.ui.components.resetShortLabel
import com.usagemonitor.presentation.ui.components.riskLevelLabel
import com.usagemonitor.presentation.ui.components.toneFor
import com.usagemonitor.presentation.viewmodel.HudQuotaEntry
import kotlinx.datetime.Instant

/**
 * Uma conta na barra HUD: o que o notch mostra em repouso e o que o painel
 * mostra aberto.
 *
 * Era montada inline em `main()`, que está no limite do backend JVM e onde a
 * regra não tinha teste nenhum além do que a tela deixava ver. Aqui ela é função
 * pura de `commonMain`, e a janela da HUD só a consome.
 */
@Immutable
data class HudAccount(
    val targetKey: UsageTargetKey,
    /**
     * O título do card: fornecedor e perfil ("Anthropic — Padrão"). Era só o
     * perfil, e "Padrão" sozinho não diz de quem é a conta.
     */
    val label: String,
    /** Palavra da **pior** cota — o papel do badge do card. */
    val statusLabel: String,
    val tone: AppTone,
    /** Todas as cotas, na ordem em que a API as devolve (5h antes de 7d). */
    val quotas: List<HudQuota>,
    /**
     * A cota cujo percentual o notch imprime. É a de **pior risco**, e no empate
     * a de maior percentual: o número ao lado do anel descreve consumo, e o
     * consumo que importa é o da cota que está mais perto de acabar.
     */
    val focusIndex: Int,
    /** Há turno de sessão CLI nos últimos 5 min nesta conta. */
    val sessionActive: Boolean = false,
    /** A marca e o acento do fornecedor saem daqui. */
    val source: ApiSource = targetKey.source,
    /** "Max 20x", "ChatGPT Plus"; `null` quando o fornecedor não informa. */
    val planLabel: String? = null
) {
    /**
     * Os anéis concêntricos: até [MAX_HUD_RINGS] cotas, de fora para dentro na
     * ordem da API. OpenCode Go é o maior caso real (três janelas); cota além
     * disso só aparece no painel aberto.
     */
    val rings: List<HudQuota>
        get() = quotas.take(MAX_HUD_RINGS)

    val focus: HudQuota?
        get() = quotas.getOrNull(focusIndex)

    /** Atenção ou pior: é o que acende o pulso âmbar do anel. */
    val needsAttention: Boolean
        get() = tone == AppTone.WARNING || tone == AppTone.CRITICAL
}

@Immutable
data class HudQuota(
    /** `5h`, `7d`, `Saldo` — a última palavra do rótulo, por [hudQuotaShortLabel]. */
    val shortLabel: String,
    /** O percentual do card, truncado ([compactPercentageLabel]). */
    val percentText: String,
    /** 0..1, recortado: o anel não dá mais de uma volta. */
    val fraction: Float,
    val tone: AppTone,
    /** Hora do reinício, curta; `null` quando não há reset a mostrar. */
    val resetText: String?,
    /** Sem projeção o anel fica tracejado: cor nenhuma informa um estado não calculado. */
    val hasForecast: Boolean
)

/** Os anéis que cabem num notch sem virarem um alvo de tiro. */
const val MAX_HUD_RINGS = 3

/**
 * As contas da HUD, **na ordem dos cards** que o usuário arrastou, uma por alvo.
 *
 * A ordem não é a do risco: com o risco mandando, a primeira conta trocava
 * sozinha e nunca se sabia de antemão quem estava ali. [orderedByCardOrder] é a
 * mesma função da grade — duas cópias divergiriam no item sem posição.
 */
internal fun buildHudAccounts(
    quotaRisks: List<HudQuotaEntry>,
    cardOrder: List<UsageTargetKey>,
    language: AppLanguage,
    now: Instant,
    activeTargets: Set<UsageTargetKey> = emptySet()
): List<HudAccount> {
    val noForecast = if (language == AppLanguage.PT) "Sem projeção" else "No forecast"
    return orderedByCardOrder(quotaRisks, cardOrder) { entry -> entry.stats.targetKey }
        .groupBy { entry -> entry.stats.targetKey }
        .map { (target, entries) ->
            val first = entries.first()
            val worst = entries.maxByOrNull { entry -> entry.risk?.level?.ordinal ?: -1 }
            val quotas = entries.map { entry ->
                HudQuota(
                    shortLabel = hudQuotaShortLabel(entry.quota.label),
                    percentText = compactPercentageLabel(entry.quota),
                    fraction = entry.quota.percentageUsed.coerceIn(0f, 1f),
                    tone = entry.risk?.let { risk -> toneFor(risk.level) } ?: AppTone.NEUTRAL,
                    resetText = resetShortLabel(entry.quota, language, now),
                    hasForecast = entry.risk != null
                )
            }
            val focusIndex = entries.indices.maxWithOrNull(
                compareBy<Int> { index -> entries[index].risk?.level?.ordinal ?: -1 }
                    .thenBy { index -> entries[index].quota.percentageUsed }
                    // Empate total: a primeira declarada, que é a janela curta.
                    .thenByDescending { index -> index }
            ) ?: 0
            HudAccount(
                targetKey = target,
                label = first.stats.displayTitle(),
                statusLabel = worst?.risk?.let { risk -> riskLevelLabel(risk.level, language) } ?: noForecast,
                tone = worst?.risk?.let { risk -> toneFor(risk.level) } ?: AppTone.NEUTRAL,
                quotas = quotas,
                focusIndex = focusIndex,
                sessionActive = target in activeTargets,
                source = first.stats.source,
                planLabel = first.stats.planLabel
            )
        }
}

/**
 * O resumo da bandeja: "Anthropic — Padrão 87% · Codex 0% · Antigravity CLI 5%",
 * uma entrada por conta com o percentual da cota em foco — o mesmo número que o
 * anel mostra. É o tooltip do ícone, como o do Codenotch: dá para ler o estado
 * sem abrir nada.
 *
 * O Windows corta tooltip de bandeja em 127 caracteres; o corte aqui é explícito,
 * com reticências, em vez de a plataforma cortar no meio de um número.
 */
internal fun hudTraySummary(appName: String, accounts: List<HudAccount>): String {
    if (accounts.isEmpty()) {
        return appName
    }
    val body = accounts.joinToString(" · ") { account ->
        val percent = account.focus?.percentText
        if (percent == null) account.label else "${account.label} $percent"
    }
    val full = "$appName — $body"
    return if (full.length <= TRAY_TOOLTIP_MAX_CHARS) full else full.take(TRAY_TOOLTIP_MAX_CHARS - 1) + "…"
}

/** O limite do `szTip` do Windows, menos o terminador. */
internal const val TRAY_TOOLTIP_MAX_CHARS = 127
