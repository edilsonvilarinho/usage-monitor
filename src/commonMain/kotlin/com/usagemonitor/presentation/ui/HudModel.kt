package com.usagemonitor.presentation.ui

import androidx.compose.runtime.Immutable
import com.usagemonitor.domain.entity.AntigravityQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CursorQuotaLabels
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.StalledCliSession
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.isExtraCreditsQuota
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AccountAccent
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.ui.components.compactPercentageLabel
import com.usagemonitor.presentation.ui.components.displayTitle
import com.usagemonitor.presentation.ui.components.expandedQuotaTitle
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
    val planLabel: String? = null,
    /**
     * De onde o dado veio: "via Codex", "via Antigravity CLI" — a nota do rodapé
     * do balão do Codenotch. Diz **qual** leitura está na tela, que é a primeira
     * pergunta quando um número parece errado. Dono único: [hudSourceOrigin].
     */
    val originLabel: String? = null,
    /** A conta do provedor, que o histórico filtra; `null` quando a fonte não a identifica. */
    val accountKey: UsageAccountKey? = null,
    /** Coleta desta conta em andamento: o anel fica pressionado até ela voltar. */
    val refreshing: Boolean = false,
    /**
     * A cor que o usuário deu à conta (issue #275); `null` é "Padrão". Tinge a
     * marca no miolo do anel e no cabeçalho do balão — só com escolha: sem ela o
     * miolo fica na cor do texto, como antes.
     */
    val accountAccent: AccountAccent? = null,
    /**
     * O emoji que o usuário deu à conta (issue #287); `null` é nenhum. Vira selo
     * no canto do anel e fica ao lado do título do balão.
     */
    val accountEmoji: AccountEmoji? = null,
    /**
     * Contexto crescendo ou saturado e sessão sem resposta nesta conta (issue
     * #265), já em texto. Vazio é "nada a dizer": o balão não abre a seção.
     * Dono único: [hudSessionSignals].
     */
    val sessionSignals: List<HudSessionSignal> = emptyList()
) {
    /** "Plus · via Codex": plano e origem numa linha só, cada um quando existe. */
    val detailLine: String?
        get() = listOfNotNull(planLabel, originLabel).joinToString(" · ").ifEmpty { null }

    /**
     * Os anéis concêntricos: as mesmas até [MAX_HUD_RINGS] primeiras cotas, **de
     * fora para dentro da janela mais longa para a mais curta** (issue #278). Na
     * ordem da API a 5h ficava por fora e a semanal por dentro, o contrário do que
     * se lê num alvo: o anel maior é o período maior. Saldo e créditos
     * (`REPORTED`, sem janela confiável) ficam por dentro. A ordenação é estável:
     * os dois grupos semanais do Antigravity mantêm a ordem do card. OpenCode Go é
     * o maior caso real (três janelas); cota além disso só aparece no balão.
     */
    val rings: List<HudQuota>
        get() = quotas.take(MAX_HUD_RINGS).sortedByDescending { quota -> ringRank(quota.periodType) }

    val focus: HudQuota?
        get() = quotas.getOrNull(focusIndex)

    /**
     * O texto ao lado do anel: uma linha por anel, **na ordem dos anéis** — de
     * fora para dentro —, cada uma com a janela ("7d 72%", "5h 45%"). Era um
     * percentual só, o da cota em foco, sem dizer a janela, e ele trocava de
     * janela sozinho quando o risco mudava (issue #286): o mesmo lugar dizia 45%
     * numa coleta e 72% na seguinte sem nada ter mudado no consumo.
     *
     * Com um anel só não há janela a distinguir, e a linha fica sem rótulo — o
     * visual das fontes de cota única não muda.
     */
    val stripLines: List<HudStripLine>
        get() {
            val current = rings
            if (current.size <= 1) {
                return listOf(focusLine)
            }
            return current.map { quota -> HudStripLine(quota.shortLabel, quota.percentText) }
        }

    /**
     * A cota em foco com a janela, numa linha só: a célula da faixa compacta e o
     * tooltip da bandeja, onde não cabe uma linha por anel. Sem rótulo quando a
     * conta tem uma cota só.
     */
    val focusLine: HudStripLine
        get() {
            val current = focus
            val label = if (quotas.size > 1) current?.shortLabel else null
            return HudStripLine(label, current?.percentText.orEmpty())
        }

    /**
     * O anel que pulsa em atenção: o da cota em foco, não mais o de fora fixo. Com
     * a semanal por fora, o índice 0 pulsaria a semanal com a 5h crítica. Foco
     * além dos anéis (quarta cota) cai no de fora, que é o que existia antes.
     */
    val attentionRingIndex: Int
        get() {
            val current = focus ?: return 0
            return rings.indexOfFirst { ring -> ring === current }.takeIf { index -> index >= 0 } ?: 0
        }

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
    val hasForecast: Boolean,
    /**
     * O título da cota no balão, o mesmo do bloco expandido do card ("Sessão 5h",
     * "Semanal") — sem o grupo, que no balão já é o cabeçalho da caixa.
     */
    val title: String = shortLabel,
    /** Grupo de modelos (Antigravity) ou franquia (Cursor); as cotas dele ficam numa caixa. */
    val group: String? = null,
    /** "87% usado · 13% restante"; `null` onde não há teto (saldo, atividade observada). */
    val usedLeftText: String? = null,
    /** A janela da cota; decide a posição do anel. `null` conta como `REPORTED`, por dentro. */
    val periodType: PeriodType? = null
)

/** Uma linha de texto do notch: a janela (`null` sem janela a distinguir) e o percentual. */
@Immutable
data class HudStripLine(val label: String?, val percentText: String) {
    /** "7d 72%", ou só "72%": é o texto que a geometria mede. */
    val text: String
        get() = if (label == null) percentText else "$label $percentText"
}

/** Os anéis que cabem num notch sem virarem um alvo de tiro. */
const val MAX_HUD_RINGS = 3

/**
 * A troca automática para a HUD na instalação nova (issue #277): pendente, com
 * ao menos uma conta para o notch mostrar e **sem janela modal aberta**. Na
 * primeira execução quem está aberta costuma ser Configurações, e esconder a
 * janela principal no meio da configuração tiraria o chão de quem configura.
 * Sem conta nenhuma o notch diria "Carregando" para sempre.
 */
internal fun hudDefaultShouldSwitch(pending: Boolean, hasHudAccounts: Boolean, modalOpen: Boolean): Boolean {
    return pending && hasHudAccounts && !modalOpen
}

/**
 * A palavra do notch sem conta. "Carregando" é o estado de quem ainda vai ter
 * dado; sem API habilitada nenhuma coleta vem, e a palavra mentiria para sempre.
 * A saída — as Configurações — está no balão da engrenagem.
 */
internal fun hudFallbackLabel(noApisEnabled: Boolean, language: AppLanguage): String {
    val pt = language == AppLanguage.PT
    return when {
        noApisEnabled && pt -> "Nenhuma API"
        noApisEnabled -> "No APIs"
        pt -> "Carregando"
        else -> "Loading"
    }
}

/** Maior é mais para fora: o período mais longo é o anel maior. */
private fun ringRank(periodType: PeriodType?): Int = when (periodType) {
    PeriodType.MONTHLY -> 3
    PeriodType.WEEKLY -> 2
    PeriodType.INTERVAL -> 1
    PeriodType.REPORTED, null -> 0
}

/**
 * A posição do anel dita em palavra, de fora para dentro: "externo", "do meio",
 * "interno". Com um anel só não há posição a dizer — `null`.
 */
internal fun hudRingPositionLabel(index: Int, count: Int, language: AppLanguage): String? {
    if (count <= 1 || index !in 0 until count) {
        return null
    }
    val pt = language == AppLanguage.PT
    return when (index) {
        0 -> if (pt) "anel externo" else "outer ring"
        count - 1 -> if (pt) "anel interno" else "inner ring"
        else -> if (pt) "anel do meio" else "middle ring"
    }
}

/**
 * A descrição de acessibilidade do anel: conta, plano, estado e **cada anel com
 * a posição**, de fora para dentro — "anel externo 7d 9% · anel interno 5h 28%".
 * Cota além dos anéis entra depois, sem posição.
 */
internal fun hudRingDescription(account: HudAccount, language: AppLanguage): String {
    val rings = account.rings
    val beyond = account.quotas.filter { quota -> rings.none { ring -> ring === quota } }
    return buildString {
        append(account.label)
        account.planLabel?.let { plan -> append(" ($plan)") }
        append(" · ")
        append(account.statusLabel)
        rings.forEachIndexed { index, quota ->
            append(" · ")
            hudRingPositionLabel(index, rings.size, language)?.let { position -> append("$position ") }
            append("${quota.shortLabel} ${quota.percentText}")
        }
        beyond.forEach { quota -> append(" · ${quota.shortLabel} ${quota.percentText}") }
        account.sessionSignals.forEach { signal -> append(" · ${signal.text}") }
    }
}

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
    activeTargets: Set<UsageTargetKey> = emptySet(),
    refreshingTargets: Set<UsageTargetKey> = emptySet(),
    /** A cor escolhida por conta Claude, por `profileId`. */
    accountColors: Map<String, AccountAccent> = emptyMap(),
    /** O emoji escolhido por conta Claude, por `profileId`. */
    accountEmojis: Map<String, AccountEmoji> = emptyMap(),
    /** Sessões ativas em atenção ou saturadas, por alvo — o mesmo pulso do botão de sessões. */
    sessionPulses: Map<UsageTargetKey, SessionPulse> = emptyMap(),
    /** Sessões sem resposta desde o último pedido, de todas as contas. */
    stalledSessions: List<StalledCliSession> = emptyList()
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
                    hasForecast = entry.risk != null,
                    title = hudQuotaTitle(entry.quota, language),
                    group = quotaGroupOf(entry.quota),
                    usedLeftText = hudUsedLeftText(entry.quota, language),
                    periodType = entry.quota.periodType
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
                planLabel = first.stats.planLabel,
                originLabel = hudSourceOrigin(first.stats.source, language),
                accountKey = first.stats.accountContext?.key,
                refreshing = target in refreshingTargets,
                accountAccent = target.profileId?.let { profileId -> accountColors[profileId] },
                accountEmoji = target.profileId?.let { profileId -> accountEmojis[profileId] },
                sessionSignals = hudSessionSignals(target, sessionPulses[target], stalledSessions, language)
            )
        }
}

/**
 * De onde a leitura de cada fonte vem, para a linha de rodapé do balão.
 *
 * `when` exaustivo: fonte nova sem origem não compila. A frase descreve o
 * **caminho** e não a empresa — o nome do fornecedor já está no título do balão.
 */
internal fun hudSourceOrigin(source: ApiSource, language: AppLanguage): String {
    val apiKey = if (language == AppLanguage.PT) "via chave de API" else "via API key"
    return when (source) {
        ApiSource.ANTHROPIC -> "via Claude Code"
        ApiSource.CODEX -> "via Codex"
        ApiSource.ANTIGRAVITY -> "via Antigravity CLI"
        ApiSource.GEMINI -> "via Gemini CLI"
        ApiSource.CURSOR -> "via Cursor"
        ApiSource.KILO -> "via Kilo Code"
        ApiSource.OPENCODE -> "via OpenCode"
        ApiSource.MINIMAX, ApiSource.DEEPSEEK, ApiSource.OPENCODE_GO, ApiSource.OPENROUTER -> apiKey
    }
}

/** O grupo da cota, pelos donos dos rótulos de cada fonte. */
private fun quotaGroupOf(quota: QuotaInfo): String? =
    AntigravityQuotaLabels.groupOf(quota.label) ?: CursorQuotaLabels.groupOf(quota.label)

/**
 * O título do bloco expandido do card sem o prefixo do grupo: "Gemini · Semanal"
 * vira "Semanal", porque no balão o grupo é o cabeçalho da caixa em volta.
 */
internal fun hudQuotaTitle(quota: QuotaInfo, language: AppLanguage): String {
    val title = expandedQuotaTitle(quota, language)
    val group = quotaGroupOf(quota) ?: return title
    return title.removePrefix("$group · ")
}

/**
 * "87% usado · 13% restante", a linha de baixo de cada cota no Codenotch.
 *
 * O usado é **truncado**, como [compactPercentageLabel] — o balão não pode dizer
 * 88% ao lado de um anel que diz 87% —, e o restante sai do usado exibido, para
 * os dois somarem 100. Entre 0 e 1% o truncamento daria "0% usado" com consumo
 * real, e a linha diz "<1%"; perto do teto, "<1% restante" pelo mesmo motivo.
 *
 * Sem teto não há restante: saldo pré-pago e atividade observada devolvem `null`.
 */
internal fun hudUsedLeftText(quota: QuotaInfo, language: AppLanguage): String? {
    if (quota.unit == UsageUnit.CURRENCY_USD || quota.isExtraCreditsQuota) return null
    if (quota.unit != UsageUnit.PERCENTAGE && quota.total <= 0L) return null
    val exact = quota.percentageUsed.coerceIn(0f, 1f) * 100f
    val usedWhole = exact.toInt()
    val used = if (exact > 0f && exact < 1f) "<1" else usedWhole.toString()
    val left = if (exact > 99f && exact < 100f) "<1" else (100 - usedWhole).coerceAtLeast(0).toString()
    return if (language == AppLanguage.PT) "$used% usado · $left% restante" else "$used% used · $left% left"
}

/**
 * O resumo da bandeja: "Anthropic — Padrão 7d 87% · Codex 0% · Antigravity CLI 5%",
 * uma entrada por conta com a cota em foco **e a janela dela** ([HudAccount.focusLine]).
 * Uma entrada por janela, como o notch faz, passaria dos 127 caracteres já com
 * três contas. É o tooltip do ícone, como o do Codenotch: dá para ler o estado
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
        if (account.focus == null) account.label else "${account.label} ${account.focusLine.text}"
    }
    val full = "$appName — $body"
    return if (full.length <= TRAY_TOOLTIP_MAX_CHARS) full else full.take(TRAY_TOOLTIP_MAX_CHARS - 1) + "…"
}

/** O limite do `szTip` do Windows, menos o terminador. */
internal const val TRAY_TOOLTIP_MAX_CHARS = 127
