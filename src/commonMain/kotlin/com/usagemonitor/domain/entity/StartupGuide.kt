package com.usagemonitor.domain.entity

import kotlinx.datetime.LocalDate

/**
 * O que a abertura do app mostra além da HUD (#411).
 *
 * Três desfechos e nunca dois juntos: um modal de boas-vindas por cima de outro
 * na mesma abertura é ruído, e as Novidades, quando há, já ocupam esse lugar.
 */
enum class StartupGuideDecision {
    NOTHING,

    /** Guia de introdução: só na primeira abertura depois de instalar. */
    GUIDE,

    /** Dica do dia: no máximo uma por dia, enquanto as dicas estiverem ligadas. */
    TIP
}

/**
 * Decide o modal de arranque. Função pura, pelo mesmo motivo de
 * [releaseNotesDecision]: a regra cabe numa tabela, e a tabela vira teste.
 *
 * Precedência: Novidades > Guia > Dica.
 *
 * - **Novidades** vencem quando [releaseNotesDecision] diz `SHOW`. A conta é a
 *   mesma da janela delas, e não o resultado da busca: a busca é assíncrona e
 *   pode falhar, e esperar por ela atrasaria a dica sem motivo. Na falha, a dica
 *   fica para a abertura seguinte — o dia dela não foi consumido.
 * - **Guia** quando nem a marca das Novidades nem a do guia existem e não há
 *   recibo de instalador. O recibo não pode ser a condição principal: ele não
 *   existe no macOS e no Linux é escrito depois de o app subir (#127). Ele entra
 *   só como prova de que a máquina já atualizou alguma vez — o mesmo papel que
 *   tem nas Novidades. Quem atualiza à mão de uma versão anterior à marca das
 *   Novidades vê o guia uma vez; é aceito, porque também nunca o viu.
 * - **Dica** quando estão ligadas e a última foi mostrada em outro dia. Vale
 *   também na abertura pelo `--autostart` (decisão do usuário): a origem do
 *   arranque não entra aqui.
 *
 * [releaseNotesSeenVersion] tem de ser o valor lido **antes** de o controlador
 * das Novidades gravar a marca da versão corrente; lido depois, toda instalação
 * nova pareceria uma abertura comum.
 */
fun startupGuideDecision(
    currentVersion: String,
    releaseNotesSeenVersion: String?,
    onboardingCompletedVersion: String?,
    hasUpdateReceipt: Boolean,
    tipsEnabled: Boolean,
    lastTipDate: LocalDate?,
    today: LocalDate
): StartupGuideDecision {
    val notes = releaseNotesDecision(currentVersion, releaseNotesSeenVersion, hasUpdateReceipt)
    if (notes == ReleaseNotesDecision.SHOW) {
        return StartupGuideDecision.NOTHING
    }
    if (releaseNotesSeenVersion == null && onboardingCompletedVersion == null && !hasUpdateReceipt) {
        return StartupGuideDecision.GUIDE
    }
    if (tipsEnabled && lastTipDate != today) {
        return StartupGuideDecision.TIP
    }
    return StartupGuideDecision.NOTHING
}

/**
 * Qual dica mostrar, a partir do índice guardado.
 *
 * Índice ausente ou fora da lista volta ao começo: o catálogo pode ter encolhido
 * entre versões, e um índice velho não pode virar exceção na abertura.
 */
fun startupTipIndex(storedIndex: Int?, tipCount: Int): Int {
    if (tipCount <= 0) {
        return 0
    }
    return if (storedIndex != null && storedIndex in 0 until tipCount) storedIndex else 0
}

/** A dica seguinte, em roda: sem repetir até esgotar a lista. */
fun nextStartupTipIndex(currentIndex: Int, tipCount: Int): Int {
    if (tipCount <= 0) {
        return 0
    }
    return (currentIndex + 1) % tipCount
}
