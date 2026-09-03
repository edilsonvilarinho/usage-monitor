package com.usagemonitor.domain.entity

/**
 * Por que o limiar percentual de alerta não alcança uma fonte.
 *
 * Duas mecânicas independentes, e as duas são consequência de mapeamentos
 * **corretos** — não há defeito a corrigir no `data`:
 *
 * - [PREPAID_BALANCE]: saldo pré-pago nasce com `used = 0` e `total = saldo`
 *   (`DeepSeekMapper`, `OpenRouterMapper`), então `QuotaInfo.percentageUsed` é
 *   sempre zero. É o mesmo desenho que fez `hasKnownResetAt = false` e a régua
 *   de tempo de autonomia existirem (issue #109): saldo não reseta, e não há
 *   teto de janela contra o qual medir percentual.
 * - [OBSERVED_ACTIVITY]: atividade observada nasce com `total = 0`
 *   (`KiloRepositoryImpl`, `OpenCodeRepositoryImpl`), porque a contagem local de
 *   requisições não conhece limite nenhum. `evaluateQuotaAlerts` descarta essas
 *   cotas no primeiro `if` do laço.
 *
 * O defeito da issue #194 não é o silêncio do avaliador: é a aba Alertas
 * oferecer o limiar sem dizer que quatro das oito fontes ficam de fora dele.
 *
 * O `when` de [quotaThresholdGap] é **exaustivo e sem `else`** de propósito: é
 * o erro de compilação que obriga a nona fonte a declarar se o limiar a alcança,
 * e é a única garantia de que o texto da tela continua verdadeiro depois dela.
 */
enum class QuotaThresholdGap {
    PREPAID_BALANCE,
    OBSERVED_ACTIVITY
}

/**
 * A razão pela qual o limiar percentual não alcança esta fonte, ou `null`
 * quando alcança.
 *
 * [ApiSource.OPENCODE_GO] devolve `null` deliberadamente: ele é percentual de
 * três janelas com `resetsAt`, exatamente a forma da Anthropic, e é avaliado
 * como qualquer outra cota com teto. Quem não tem teto é o Zen gratuito
 * ([ApiSource.OPENCODE]), que é outra fonte.
 */
fun ApiSource.quotaThresholdGap(): QuotaThresholdGap? {
    return when (this) {
        ApiSource.DEEPSEEK, ApiSource.OPENROUTER -> QuotaThresholdGap.PREPAID_BALANCE
        ApiSource.OPENCODE, ApiSource.KILO -> QuotaThresholdGap.OBSERVED_ACTIVITY
        ApiSource.ANTHROPIC,
        ApiSource.CODEX,
        ApiSource.MINIMAX,
        ApiSource.OPENCODE_GO -> null
    }
}

/**
 * As fontes de cada lacuna, na ordem de declaração de [ApiSource].
 *
 * A ordem sai do enum e não de uma lista escrita à mão pela razão de sempre:
 * duas listas do mesmo fato divergem, e aqui a divergência apareceria como uma
 * fonte citada na tela que o avaliador alcança — ou o contrário.
 */
fun sourcesWithQuotaThresholdGap(gap: QuotaThresholdGap): List<ApiSource> {
    return ApiSource.entries.filter { source -> source.quotaThresholdGap() == gap }
}
