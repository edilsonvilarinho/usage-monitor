package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage

/** Nome da janela de presença. */
internal fun teamPresenceTitle(language: AppLanguage, isAdminOverview: Boolean = false): String {
    if (isAdminOverview) {
        return if (language == AppLanguage.PT) {
            "Conectados agora — todas as contas"
        } else {
            "Connected now — all accounts"
        }
    }
    return if (language == AppLanguage.PT) "Conectados agora" else "Connected now"
}

/**
 * Título da janela nomeando a conta.
 *
 * [isAdminOverview] vem de quem abriu a janela e não é inferido de
 * [accountLabel] em branco, pelo mesmo motivo de [teamUsageWindowTitle]: rótulo
 * vazio numa conta só cairia no ramo global.
 */
internal fun teamPresenceWindowTitle(
    language: AppLanguage,
    accountLabel: String?,
    isAdminOverview: Boolean = false
): String {
    val base = teamPresenceTitle(language, isAdminOverview)
    if (accountLabel.isNullOrBlank()) {
        return base
    }
    return "$base — $accountLabel"
}

/**
 * Rótulos da tela de presença.
 *
 * Reaproveita [CliSessionsLabels] e [TeamUsageLabels] onde o texto já existe —
 * máquina, status, pílula "ao vivo" e carimbo de última alteração são os mesmos
 * das outras telas, e traduzi-los de novo acabaria em textos divergentes.
 */
internal object TeamPresenceLabels {

    fun online(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Conectado" else "Connected"
    }

    fun offline(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Desconectado" else "Disconnected"
    }

    /** Legenda da coluna de identidade, na faixa de cabeçalho da lista. */
    fun columnMember(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Integrante" else "Member"
    }

    fun columnState(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Estado" else "State"
    }

    fun columnWorking(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Trabalhando agora" else "Working now"
    }

    /** Valor da coluna "trabalhando agora" para quem não tem sessão ativa. */
    fun idle(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Parado" else "Idle"
    }

    fun activeSessions(count: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            if (count == 1) "1 sessão" else "$count sessões"
        } else {
            if (count == 1) "1 session" else "$count sessions"
        }
    }

    fun onlyOnline(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Somente conectados" else "Connected only"
    }

    fun thisMachine(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "esta máquina" else "this machine"
    }

    fun lastSignal(instantLabel: String?, language: AppLanguage): String {
        if (instantLabel == null) {
            return neverReported(language)
        }
        return if (language == AppLanguage.PT) {
            "último sinal $instantLabel"
        } else {
            "last signal $instantLabel"
        }
    }

    fun lastTurn(instantLabel: String?, language: AppLanguage): String {
        if (instantLabel == null) {
            return if (language == AppLanguage.PT) "sem turno recente" else "no recent turn"
        }
        return if (language == AppLanguage.PT) {
            "último turno $instantLabel"
        } else {
            "last turn $instantLabel"
        }
    }

    fun neverReported(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "nunca reportou" else "never reported"
    }

    /** Resumo do cabeçalho: quantos trabalham, quantos estão conectados, quantos há. */
    fun workingSummary(count: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            if (count == 1) "1 trabalhando" else "$count trabalhando"
        } else {
            if (count == 1) "1 working" else "$count working"
        }
    }

    fun onlineSummary(count: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            if (count == 1) "1 conectado" else "$count conectados"
        } else {
            if (count == 1) "1 connected" else "$count connected"
        }
    }

    fun knownSummary(count: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            if (count == 1) "1 conhecido" else "$count conhecidos"
        } else {
            if (count == 1) "1 known" else "$count known"
        }
    }

    /** Quantos da conta estão conectados, para a faixa da visão global. */
    fun groupSummary(online: Int, total: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "$online de $total conectados"
        } else {
            "$online of $total connected"
        }
    }

    fun empty(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Ninguém apareceu ainda. Cada máquina reporta ao abrir o app."
        } else {
            "Nobody has shown up yet. Each machine reports when the app opens."
        }
    }

    fun emptyFiltered(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Ninguém conectado agora. Desligue o filtro para ver o time inteiro."
        } else {
            "Nobody connected right now. Turn off the filter to see the whole team."
        }
    }

    fun error(message: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Não foi possível ler a presença do time: $message"
        } else {
            "Could not read team presence: $message"
        }
    }

    fun deleteAccount(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Apagar conta do servidor" else "Delete account data"
    }

    fun deleteAccountTitle(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Apagar a conta inteira?" else "Delete whole account?"
    }

    /**
     * Texto da confirmação, com o custo real e com a única trava que existe.
     *
     * Apagar não impede a conta de voltar: envio e presença reivindicam sozinhos,
     * então uma máquina que ainda participe dela a recria na batida seguinte.
     * Omitir isso faria o administrador achar que a limpeza falhou.
     */
    fun deleteAccountWarning(
        accountKey: String,
        memberCount: Int,
        language: AppLanguage
    ): String {
        return if (language == AppLanguage.PT) {
            "A conta $accountKey, os $memberCount integrante(s) dela e todo o consumo já " +
                "enviado serão apagados do servidor. Isto não tem volta. Se alguma máquina " +
                "ainda participar dessa conta, ela reaparece no próximo sinal — desmarque a " +
                "conta lá, ou reduza o limite de contas da chave."
        } else {
            "Account $accountKey, its $memberCount member(s) and all usage already reported " +
                "will be deleted from the server. This cannot be undone. If any machine still " +
                "reports for this account it will come back on the next signal — unselect the " +
                "account there, or lower the key's account limit."
        }
    }

    fun confirmAccountDeletion(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Apagar" else "Delete"
    }

    /** A própria conta não pode ser apagada: esta máquina a recriaria. */
    fun cannotDeleteOwnAccount(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "esta máquina participa desta conta"
        } else {
            "this machine reports for this account"
        }
    }

    fun actionError(message: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Não foi possível concluir a remoção: $message"
        } else {
            "Could not complete the removal: $message"
        }
    }

    /**
     * Aviso de relógios divergentes.
     *
     * A tela avisa em vez de mostrar um número errado com cara de certo: o
     * carimbo de presença vem do relógio do servidor, e sem NTP nos dois lados a
     * coluna de estado deixa de significar o que promete.
     */
    fun clockSkewNotice(minutes: Long, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Os relógios desta máquina e do servidor divergem em cerca de " +
                "$minutes min. A coluna Estado pode estar errada."
        } else {
            "This machine's clock and the server's differ by about " +
                "$minutes min. The State column may be wrong."
        }
    }
}
