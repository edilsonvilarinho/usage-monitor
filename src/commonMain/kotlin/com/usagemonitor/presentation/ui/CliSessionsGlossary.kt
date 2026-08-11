package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage

/**
 * Vocabulário da tela de detalhe.
 *
 * Os números aqui vêm do faturamento da Anthropic, não do senso comum: "cache
 * lido" costuma ser mais de 95% do total de tokens e ainda assim é a parte
 * barata, e "janela de contexto" não é quanto foi gasto, é quanto do limite do
 * modelo já está ocupado. Sem essa tradução os valores são lidos ao contrário.
 *
 * Separado de [CliSessionsLabels] porque ali são rótulos — aqui são definições,
 * e definição que muda com o rótulo deixa de valer.
 */
internal enum class GlossaryTerm {
    CACHE_READ,
    CACHE_WRITE,
    CACHE_HIT_RATE,
    COST_DISTRIBUTION,
    SAVINGS,
    AVERAGE_CONTEXT,
    LIVE_CONTEXT,
    NEXT_INTERACTION,
    CONTEXT_WINDOW,
    CONTEXT_PER_TURN,
    COMPACTION,
    CACHE_WRITE_PER_TURN,
    COST_VERSUS_SAVINGS,
    TOTAL_TOKENS,
    ESTIMATED_COST,
    SIDECHAIN
}

internal data class GlossaryEntry(
    val title: String,
    val explanation: String
)

internal object CliSessionsGlossary {

    /** Ordem de leitura do painel: do que se vê primeiro para o que se apura depois. */
    val readingOrder: List<GlossaryTerm> = listOf(
        GlossaryTerm.ESTIMATED_COST,
        GlossaryTerm.TOTAL_TOKENS,
        GlossaryTerm.CACHE_READ,
        GlossaryTerm.CACHE_WRITE,
        GlossaryTerm.CACHE_HIT_RATE,
        GlossaryTerm.CONTEXT_WINDOW,
        GlossaryTerm.LIVE_CONTEXT,
        GlossaryTerm.AVERAGE_CONTEXT,
        GlossaryTerm.NEXT_INTERACTION,
        GlossaryTerm.COST_DISTRIBUTION,
        GlossaryTerm.SAVINGS,
        GlossaryTerm.CONTEXT_PER_TURN,
        GlossaryTerm.COMPACTION,
        GlossaryTerm.CACHE_WRITE_PER_TURN,
        GlossaryTerm.COST_VERSUS_SAVINGS,
        GlossaryTerm.SIDECHAIN
    )

    fun entry(term: GlossaryTerm, language: AppLanguage): GlossaryEntry {
        return if (language == AppLanguage.PT) portuguese(term) else english(term)
    }

    private fun portuguese(term: GlossaryTerm): GlossaryEntry {
        return when (term) {
            GlossaryTerm.CACHE_READ -> GlossaryEntry(
                title = "Cache lido",
                explanation = "O contexto que o modelo releu do cache neste turno. É a maior parte do " +
                    "total de tokens e a mais barata: custa cerca de um décimo do input normal."
            )
            GlossaryTerm.CACHE_WRITE -> GlossaryEntry(
                title = "Cache gravado",
                explanation = "Contexto novo guardado no cache para os próximos turnos. Custa mais que " +
                    "o input normal, e se paga quando é relido."
            )
            GlossaryTerm.CACHE_HIT_RATE -> GlossaryEntry(
                title = "Taxa de acerto de cache",
                explanation = "Que fração do contexto veio do cache em vez de ser cobrada como input " +
                    "cheio. Quanto mais alta, mais barata a sessão."
            )
            GlossaryTerm.COST_DISTRIBUTION -> GlossaryEntry(
                title = "Distribuição de custo",
                explanation = "Onde o dinheiro foi parar, por tipo de token. Não é a divisão do volume: " +
                    "cache lido costuma dominar o volume e não o custo."
            )
            GlossaryTerm.SAVINGS -> GlossaryEntry(
                title = "Economia do cache",
                explanation = "Quanto os tokens lidos do cache teriam custado como input normal, menos o " +
                    "que de fato custaram. É o que o cache poupou nesta sessão."
            )
            GlossaryTerm.AVERAGE_CONTEXT -> GlossaryEntry(
                title = "Contexto médio/turno",
                explanation = "Tamanho médio do contexto reenviado a cada mensagem da sessão."
            )
            GlossaryTerm.LIVE_CONTEXT -> GlossaryEntry(
                title = "Contexto vivo",
                explanation = "O contexto do último turno — o que vai junto na próxima mensagem. Cresce a " +
                    "cada turno e cai quando há compactação."
            )
            GlossaryTerm.NEXT_INTERACTION -> GlossaryEntry(
                title = "Custo da próxima msg",
                explanation = "Estimativa do que custará mandar mais uma mensagem, considerando o " +
                    "contexto vivo. É o número que decide se vale compactar."
            )
            GlossaryTerm.CONTEXT_WINDOW -> GlossaryEntry(
                title = "Janela de contexto",
                explanation = "Quanto do limite do modelo o contexto vivo já ocupa. Não é quota de conta " +
                    "nem gasto: é o teto de quanto cabe numa conversa."
            )
            GlossaryTerm.CONTEXT_PER_TURN -> GlossaryEntry(
                title = "Contexto por turno",
                explanation = "Como o contexto cresceu ao longo da sessão, turno a turno. Subida " +
                    "constante é normal; o que importa é a altura em que parou."
            )
            GlossaryTerm.COMPACTION -> GlossaryEntry(
                title = "Compactação",
                explanation = "O marcador ▼ no gráfico: um ponto em que o contexto encolheu, sinal de um " +
                    "/compact ou de um resumo. É o que baixa o custo daí em diante."
            )
            GlossaryTerm.CACHE_WRITE_PER_TURN -> GlossaryEntry(
                title = "Cache gravado por turno",
                explanation = "Quanto contexto novo entrou no cache em cada turno, separado por duração: " +
                    "5m expira rápido e é o padrão; 1h custa mais e dura mais."
            )
            GlossaryTerm.COST_VERSUS_SAVINGS -> GlossaryEntry(
                title = "Custo x economia acumulados",
                explanation = "As duas curvas somadas ao longo da sessão. Economia acima do custo " +
                    "significa que o cache já pagou mais do que a sessão gastou."
            )
            GlossaryTerm.TOTAL_TOKENS -> GlossaryEntry(
                title = "Tokens (com cache)",
                explanation = "Soma de tudo: input, output, cache lido e cache gravado. O cache lido " +
                    "domina esse número, então ele não mede conteúdo produzido."
            )
            GlossaryTerm.ESTIMATED_COST -> GlossaryEntry(
                title = "Custo",
                explanation = "Estimativa a preço de tabela, calculada dos tokens do transcript. Não é " +
                    "fatura e não desconta plano nem crédito."
            )
            GlossaryTerm.SIDECHAIN -> GlossaryEntry(
                title = "Turno de subagente",
                explanation = "Turno rodado por um subagente. Soma no custo, mas fica fora dos gráficos " +
                    "de contexto porque tem janela própria, separada da conversa principal."
            )
        }
    }

    private fun english(term: GlossaryTerm): GlossaryEntry {
        return when (term) {
            GlossaryTerm.CACHE_READ -> GlossaryEntry(
                title = "Cache read",
                explanation = "The context the model re-read from cache on this turn. It is the bulk of " +
                    "the token total and the cheapest part: about a tenth of regular input."
            )
            GlossaryTerm.CACHE_WRITE -> GlossaryEntry(
                title = "Cache write",
                explanation = "New context stored in the cache for the next turns. Costs more than " +
                    "regular input, and pays for itself once it is re-read."
            )
            GlossaryTerm.CACHE_HIT_RATE -> GlossaryEntry(
                title = "Cache hit rate",
                explanation = "How much of the context came from cache instead of being billed as full " +
                    "input. The higher it is, the cheaper the session."
            )
            GlossaryTerm.COST_DISTRIBUTION -> GlossaryEntry(
                title = "Cost distribution",
                explanation = "Where the money went, by token type. Not the same as the volume split: " +
                    "cache read usually dominates volume, not cost."
            )
            GlossaryTerm.SAVINGS -> GlossaryEntry(
                title = "Cache savings",
                explanation = "What the cached tokens would have cost as regular input, minus what they " +
                    "actually cost. That is what the cache saved in this session."
            )
            GlossaryTerm.AVERAGE_CONTEXT -> GlossaryEntry(
                title = "Avg context/turn",
                explanation = "Average size of the context resent with each message of the session."
            )
            GlossaryTerm.LIVE_CONTEXT -> GlossaryEntry(
                title = "Live context",
                explanation = "The context of the last turn — what rides along with the next message. It " +
                    "grows every turn and drops on compaction."
            )
            GlossaryTerm.NEXT_INTERACTION -> GlossaryEntry(
                title = "Next message cost",
                explanation = "Estimate of what one more message will cost, given the live context. It is " +
                    "the number that decides whether compacting is worth it."
            )
            GlossaryTerm.CONTEXT_WINDOW -> GlossaryEntry(
                title = "Context window",
                explanation = "How much of the model limit the live context already takes. Not an account " +
                    "quota and not spend: it is the ceiling of what fits in one conversation."
            )
            GlossaryTerm.CONTEXT_PER_TURN -> GlossaryEntry(
                title = "Context per turn",
                explanation = "How the context grew across the session, turn by turn. A steady climb is " +
                    "normal; what matters is the height it settled at."
            )
            GlossaryTerm.COMPACTION -> GlossaryEntry(
                title = "Compaction",
                explanation = "The ▼ marker on the chart: a point where the context shrank, the sign of a " +
                    "/compact or a summary. That is what cuts the cost from there on."
            )
            GlossaryTerm.CACHE_WRITE_PER_TURN -> GlossaryEntry(
                title = "Cache write per turn",
                explanation = "How much new context entered the cache each turn, split by lifetime: 5m " +
                    "expires fast and is the default; 1h costs more and lasts longer."
            )
            GlossaryTerm.COST_VERSUS_SAVINGS -> GlossaryEntry(
                title = "Cumulative cost vs savings",
                explanation = "Both curves summed across the session. Savings above cost means the cache " +
                    "has already returned more than the session spent."
            )
            GlossaryTerm.TOTAL_TOKENS -> GlossaryEntry(
                title = "Tokens (with cache)",
                explanation = "Everything summed: input, output, cache read and cache write. Cache read " +
                    "dominates this number, so it does not measure produced content."
            )
            GlossaryTerm.ESTIMATED_COST -> GlossaryEntry(
                title = "Cost",
                explanation = "Estimated at list price from the transcript tokens. Not an invoice, and it " +
                    "discounts neither plan nor credit."
            )
            GlossaryTerm.SIDECHAIN -> GlossaryEntry(
                title = "Subagent turn",
                explanation = "A turn run by a subagent. It counts toward cost but stays out of the " +
                    "context charts, because it has its own window apart from the main conversation."
            )
        }
    }
}
