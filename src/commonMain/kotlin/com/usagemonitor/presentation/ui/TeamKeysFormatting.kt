package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage

/**
 * Rótulos da administração de chaves do time. Saíram de `TeamKeysAdminScreen.kt` (747 linhas)
 * pelo padrão de `TeamUsageFormatting.kt` e `TeamPresenceFormatting.kt`: tela num arquivo,
 * texto no outro.
 */
internal object TeamKeysLabels {

    fun loading(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Consultando as chaves no servidor…"
    } else {
        "Querying keys on the server…"
    }

    fun explanation(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "O rótulo é a relação do time daquela chave: escreva ali o e-mail da pessoa, e só a " +
            "conta com aquele e-mail poderá usá-la. Separe por vírgula para incluir mais de uma. " +
            "Rótulo sem e-mail aceita qualquer conta."
    } else {
        "The label is that key's team roster: type the person's e-mail there and only the " +
            "account with that e-mail may use it. Separate with commas to list more than one. " +
            "A label without an e-mail accepts any account."
    }

    fun empty(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Nenhuma chave emitida ainda."
    } else {
        "No keys issued yet."
    }

    fun labelField(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Rótulo" else "Label"

    fun labelPlaceholder(language: AppLanguage): String =
        if (language == AppLanguage.PT) "fulano@empresa.com" else "person@company.com"

    fun maxAccounts(language: AppLanguage): String =
        if (language == AppLanguage.PT) "contas" else "accounts"

    fun create(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Emitir" else "Issue"

    fun show(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Mostrar" else "Show"

    fun hide(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Ocultar" else "Hide"

    fun copy(language: AppLanguage): String =
        CommonLabels.copy(language)

    fun unclaimed(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Sem conta vinculada — o vínculo nasce no primeiro envio."
    } else {
        "No account linked yet — the link is created on the first upload."
    }

    fun unlink(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Desvincular" else "Unlink"

    fun regenerate(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Regerar" else "Regenerate"

    fun revoke(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Revogar" else "Revoke"

    fun revoked(language: AppLanguage): String =
        if (language == AppLanguage.PT) "revogada" else "revoked"

    fun rename(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Renomear" else "Rename"

    fun save(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Salvar" else "Save"

    fun cancel(language: AppLanguage): String =
        CommonLabels.cancel(language)

    fun dismiss(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Dispensar" else "Dismiss"

    fun retry(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Tentar de novo" else "Retry"

    fun noEmail(language: AppLanguage): String =
        if (language == AppLanguage.PT) "sem e-mail reportado" else "no reported e-mail"

    fun unauthorizedAccount(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Fora do rótulo" else "Outside the label"

    fun removeAccount(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Remover do time" else "Remove from team"

    fun removeAccountTitle(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Remover esta conta do time?"
    } else {
        "Remove this account from the team?"
    }

    /**
     * Diz as duas coisas que a ação faz, porque a segunda é irreversível e a
     * primeira, sozinha, seria desfeita pelo envio seguinte daquela máquina.
     */
    fun removeAccountWarning(account: String, language: AppLanguage): String =
        if (language == AppLanguage.PT) {
            "Apaga tudo o que $account enviou — integrantes, sessões e turnos — e passa a " +
                "recusá-la, mesmo que a máquina dela continue enviando. Os dados não voltam nem " +
                "depois de devolvê-la ao time."
        } else {
            "Deletes everything $account sent — members, sessions and turns — and starts " +
                "refusing it, even if that machine keeps uploading. The data does not come back, " +
                "not even after returning the account to the team."
        }

    fun confirmRemoveAccount(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Remover do time" else "Remove from team"

    fun blockedSection(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Contas fora do time" else "Accounts outside the team"

    fun blockedEmpty(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Nenhuma conta removida."
    } else {
        "No account removed."
    }

    fun unblock(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Devolver ao time" else "Return to team"

    fun blockedAt(timestamp: String, language: AppLanguage): String =
        if (language == AppLanguage.PT) "removida em $timestamp" else "removed on $timestamp"
}
