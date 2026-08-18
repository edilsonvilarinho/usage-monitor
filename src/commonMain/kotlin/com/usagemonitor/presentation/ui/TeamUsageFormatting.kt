package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionHealthTally
import com.usagemonitor.domain.entity.CliSessionRange
import kotlinx.datetime.Instant

/**
 * Nome da janela.
 *
 * Um time é uma conta Anthropic, então a visão global do administrador mostra
 * vários times e o título vai no plural — no singular ele diria que a janela é
 * de um time só, que é justamente o que ela não é.
 */
internal fun teamUsageTitle(language: AppLanguage, isAdminOverview: Boolean = false): String {
    if (isAdminOverview) {
        return if (language == AppLanguage.PT) "Sessões dos times" else "All team sessions"
    }
    return if (language == AppLanguage.PT) "Sessões do time" else "Team sessions"
}

/**
 * Título da janela nomeando a conta.
 *
 * [isAdminOverview] vem de quem abriu a janela e não é inferido de
 * [accountLabel] em branco: rótulo vazio numa conta só cairia no mesmo ramo e
 * daria o plural para um time só.
 */
internal fun teamUsageWindowTitle(
    language: AppLanguage,
    accountLabel: String?,
    isAdminOverview: Boolean = false
): String {
    val base = teamUsageTitle(language, isAdminOverview)
    if (accountLabel.isNullOrBlank()) {
        return base
    }
    return "$base — $accountLabel"
}

/**
 * Rótulos da tela de time.
 *
 * O que já existe em [CliSessionsLabels] é reaproveitado — janelas, pílula "ao
 * vivo", carimbo de última alteração e as colunas da linha de sessão são os
 * mesmos das duas telas, e traduzir duas vezes acabaria em textos divergentes.
 */
internal object TeamUsageLabels {

    fun tabMembers(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Integrantes" else "Members"
    }

    fun tabTrend(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Tendência" else "Trend"
    }

    /**
     * O que a tendência **é**, e não só como lê-la.
     *
     * Sem esta frase o painel entrega barras roxas sem dizer que grandeza elas
     * medem, em que recorte, nem por que não obedecem ao filtro de janela logo
     * acima delas.
     */
    fun trendHint(dayCount: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Quanto cada integrante gastou por dia nos últimos $dayCount dias, uma barra por dia. " +
                "Serve para ver quem subiu ou parou ao longo do tempo — é história, não a janela de quota atual."
        } else {
            "How much each member spent per day over the last $dayCount days, one bar per day. " +
                "It answers who ramped up or stopped over time — it is history, not the current quota window."
        }
    }

    fun trendEmpty(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Nenhum consumo registrado nos dias cobertos pela tendência."
        } else {
            "No usage recorded in the days the trend covers."
        }
    }

    fun trendUnavailable(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Tendência indisponível: este servidor de time é anterior à rota que a serve."
        } else {
            "Trend unavailable: this team server predates the route that serves it."
        }
    }

    /**
     * O resumo descreve os **mesmos** turnos da lista, por outros eixos. Sem
     * dizê-lo, os dois totais parecem duas medidas concorrentes.
     */
    fun breakdownHint(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Os mesmos turnos da lista de integrantes, recortados por eixo. Custo estimado a preço de tabela, calculado nesta máquina."
        } else {
            "The same turns from the member list, sliced by axis. Estimated cost at list price, computed on this machine."
        }
    }

    fun trendTitle(dayCount: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Tendência dos últimos $dayCount dias"
        } else {
            "Trend over the last $dayCount days"
        }
    }

    /**
     * A tendência não segue o filtro de janela da lista, e a tela precisa
     * dizê-lo: um gráfico de dias ao lado de números de 5h seria lido como o
     * mesmo recorte.
     */
    fun trendNotice(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Custo estimado por dia (BRT), independente do filtro de janela acima. Barras na mesma escala para comparar integrantes."
        } else {
            "Estimated cost per day (BRT), independent of the window filter above. Bars share one scale so members are comparable."
        }
    }


    fun memberCount(count: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            if (count == 1) "1 integrante" else "$count integrantes"
        } else {
            if (count == 1) "1 member" else "$count members"
        }
    }

    fun sessionCount(count: Int, language: AppLanguage): String {
        return CliSessionsLabels.sessionCount(count, language)
    }

    /** Cabeçalho da coluna de integrantes no totalizador da conta. */
    fun columnMembers(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Integrantes" else "Members"
    }

    fun allAccounts(count: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            if (count == 1) "Todas as contas (1)" else "Todas as contas ($count)"
        } else {
            if (count == 1) "All accounts (1)" else "All accounts ($count)"
        }
    }

    /** Conta que ainda não tem chave emitida — só o uuid a identifica. */
    fun unlabeledAccount(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Conta sem chave" else "Account without key"
    }

    /**
     * Aviso do recorte de 5h na visão global.
     *
     * Sem ele o número diverge do que cada pessoa vê no próprio modal, e a
     * diferença parece defeito: lá a janela começa no reset de quota da conta,
     * aqui ela não pode começar no reset de nenhuma, porque são várias.
     */
    fun slidingWindowNotice(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Nesta visão o recorte de 5h é deslizante: cada conta reseta a quota " +
                "numa hora diferente, então os números podem não bater com o modal de uma conta."
        } else {
            "In this view the 5h range is sliding: each account resets its quota at a " +
                "different time, so numbers may differ from a single-account modal."
        }
    }

    fun columnShare(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "do time" else "of team"
    }

    fun columnMachine(language: AppLanguage): String {
        return CliSessionsLabels.machine(language)
    }

    fun columnTokens(language: AppLanguage): String {
        return CliSessionsLabels.columnTokens(language)
    }

    fun columnCost(language: AppLanguage): String {
        return CliSessionsLabels.columnCost(language)
    }

    /** Mesmo rótulo da tela da máquina: é a mesma medida, com o mesmo corte. */
    fun columnActiveTime(language: AppLanguage): String {
        return CliSessionsLabels.activeTime(language)
    }

    fun columnStatus(language: AppLanguage): String {
        return CliSessionsLabels.columnStatus(language)
    }

    fun healthShort(health: CliSessionHealth, language: AppLanguage): String {
        return CliSessionsLabels.healthShort(health, language)
    }

    fun back(language: AppLanguage): String {
        return CliSessionsLabels.back(language)
    }

    fun detailLoading(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Consultando a sessão no servidor do time…"
        } else {
            "Querying the session on the team server…"
        }
    }

    /**
     * Servidor sem a rota de detalhe, ou sessão fora da retenção dele.
     *
     * Diz o que falta e o que fazer: o app não quebra contra um servidor antigo,
     * mas quem lê a tela precisa saber por que os gráficos não estão lá.
     */
    fun missingTurnsNotice(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Este servidor de time não devolve os turnos desta sessão (versão anterior a 0.2.0 " +
                "ou sessão já expirada na retenção). Só os agregados do período estão disponíveis; " +
                "os gráficos por turno voltam depois de atualizar o servidor."
        } else {
            "This team server does not return the turns for this session (older than 0.2.0 or " +
                "the session already fell out of retention). Only the period aggregates are " +
                "available; the per-turn charts come back once the server is updated."
        }
    }

    fun rangeLabel(range: CliSessionRange, language: AppLanguage): String {
        return CliSessionsLabels.rangeLabel(range, language)
    }

    fun live(language: AppLanguage): String {
        return CliSessionsLabels.live(language)
    }

    fun lastChange(instantLabel: String?, language: AppLanguage): String {
        return CliSessionsLabels.lastChange(instantLabel, language)
    }

    fun estimatedTotalInRange(
        range: CliSessionRange,
        endsAt: Instant?,
        isAnchored: Boolean,
        language: AppLanguage
    ): String {
        if (range == CliSessionRange.ALL) {
            return CliSessionsLabels.estimatedTotal(language)
        }
        return CliSessionsLabels.estimatedTotalInRange(range, endsAt, isAnchored, language)
    }

    /** Integrante conhecido pelo servidor, mas sem nenhum turno na janela. */
    fun noActivityInRange(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "sem uso no período" else "no usage in range"
    }

    /**
     * Nenhum integrante consumiu na janela.
     *
     * Diferente de "ninguém configurou a integração": o time pode existir e
     * simplesmente não ter usado o CLI nas últimas horas.
     */
    fun emptyInRange(range: CliSessionRange, isAnchored: Boolean, language: AppLanguage): String {
        if (range == CliSessionRange.ALL) {
            return if (language == AppLanguage.PT) {
                "Nenhum integrante do time enviou dados ainda. Confira o servidor nas Configurações."
            } else {
                "No team member has reported data yet. Check the server in Settings."
            }
        }
        val window = rangeLabel(range, language)
        if (isAnchored) {
            return if (language == AppLanguage.PT) {
                "Nenhum uso do time nesta janela de quota ($window). Escolha uma janela maior."
            } else {
                "No team usage in the current quota window ($window). Pick a wider range."
            }
        }
        return if (language == AppLanguage.PT) {
            "Nenhum uso do time no período ($window). Escolha uma janela maior."
        } else {
            "No team usage in the last $window. Pick a wider range."
        }
    }

    /** Prefixo do aviso de falha, para o usuário saber que o dado está velho. */
    fun serverError(message: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Não foi possível falar com o servidor de time: $message"
        } else {
            "Could not reach the team server: $message"
        }
    }

    fun expand(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Ver sessões" else "Show sessions"
    }

    fun collapse(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Ocultar sessões" else "Hide sessions"
    }

    fun expandAccount(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Ver integrantes" else "Show members"
    }

    fun collapseAccount(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Ocultar integrantes" else "Hide members"
    }

    fun removeMember(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Remover do time" else "Remove from team"
    }

    fun removeSession(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Excluir sessão" else "Delete session"
    }

    fun removeSessionTitle(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Excluir sessão?" else "Delete session?"
    }

    fun removeSessionWarning(
        sessionId: String,
        projectName: String?,
        language: AppLanguage
    ): String {
        val identity = if (projectName.isNullOrBlank()) {
            shortSessionId(sessionId)
        } else {
            "${shortSessionId(sessionId)} ($projectName)"
        }
        return if (language == AppLanguage.PT) {
            "A sessão $identity e todo o consumo já enviado por ela serão apagados do " +
                "servidor. O histórico antigo não será reenviado e isto não tem volta. " +
                "Se a sessão continuar ativa, novos turnos poderão recriá-la."
        } else {
            "Session $identity and all usage already reported by it will be deleted from " +
                "the server. The old history will not be sent again and this cannot be " +
                "undone. If the session remains active, new turns may recreate it."
        }
    }

    fun confirmSessionRemoval(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Excluir" else "Delete"
    }

    fun removeMemberTitle(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Remover integrante?" else "Remove member?"
    }

    /**
     * Texto da confirmação, com o custo real da ação.
     *
     * A remoção apaga os turnos no servidor, e a máquina de origem não os
     * reenvia — o marcador local dela já os considera entregues. Omitir isso
     * transformaria um botão de limpeza em perda silenciosa de histórico.
     */
    fun removeMemberWarning(alias: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "\"$alias\" e todo o consumo já enviado por essa máquina serão apagados do " +
                "servidor. A máquina de origem não reenvia o histórico: isto não tem volta. " +
                "Use apenas para tirar da lista uma máquina que não existe mais."
        } else {
            "\"$alias\" and all usage already reported by that machine will be deleted from " +
                "the server. The source machine does not resend its history: this cannot be " +
                "undone. Use it only to drop a machine that no longer exists."
        }
    }

    fun confirmRemoval(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Remover" else "Remove"
    }

    fun cancel(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Cancelar" else "Cancel"
    }

    /** Esta máquina não pode se remover: o próximo envio a recriaria. */
    fun cannotRemoveSelf(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "esta máquina"
        } else {
            "this machine"
        }
    }

    fun removalError(message: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Não foi possível remover o integrante: $message"
        } else {
            "Could not remove the member: $message"
        }
    }

    fun sessionRemovalError(message: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Não foi possível excluir a sessão: $message"
        } else {
            "Could not delete the session: $message"
        }
    }

    /** Mesma contagem do modal local: "1 saturada · 2 em atenção". */
    fun healthTally(tally: CliSessionHealthTally, language: AppLanguage): String? {
        return CliSessionsLabels.healthTally(tally, language)
    }

    /** Deixa explícito de onde vem o número, já que a fonte não é esta máquina. */
    fun lastSeen(instantLabel: String?, language: AppLanguage): String {
        if (instantLabel == null) {
            return if (language == AppLanguage.PT) "nunca reportou" else "never reported"
        }
        return if (language == AppLanguage.PT) {
            "último envio $instantLabel"
        } else {
            "last report $instantLabel"
        }
    }
}

