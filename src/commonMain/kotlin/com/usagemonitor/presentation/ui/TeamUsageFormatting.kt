package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionHealthTally
import com.usagemonitor.domain.entity.CliSessionRange
import kotlinx.datetime.Instant

internal fun teamUsageTitle(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Sessões do time" else "Team sessions"
}

/** Título da janela nomeando a conta: um time é uma conta Anthropic. */
internal fun teamUsageWindowTitle(language: AppLanguage, accountLabel: String?): String {
    val base = teamUsageTitle(language)
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
        language: AppLanguage
    ): String {
        if (range == CliSessionRange.ALL) {
            return CliSessionsLabels.estimatedTotal(language)
        }
        return CliSessionsLabels.estimatedTotalInRange(range, endsAt, language)
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

    fun removeMember(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Remover do time" else "Remove from team"
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
