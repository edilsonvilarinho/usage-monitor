package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized

/** Os textos do `team` e do `web` no terminal (#440, abas Time e Rede), nos dois idiomas. */
internal class TerminalTeamLabels(val language: AppLanguage) {

    val keyPrompt: String
        get() = localized(language, "Cole a chave do time (a que o administrador deu) e tecle enter (não aparece na tela):", "Paste the team key (the one the admin gave you) and press enter (it is not shown):")

    val needsServer: String get() = localized(language, "Falta o endereço do servidor do time. Informe com:", "The team server address is missing. Give it with:")
    val needsAlias: String get() = localized(language, "Falta o seu apelido no time. Informe com:", "Your name on the team is missing. Give it with:")

    fun unknownAccounts(accounts: String): String =
        localized(language, "Estas contas não existem: $accounts. Veja as contas com:", "These accounts do not exist: $accounts. See the accounts with:")

    fun checkFailed(detail: String): String = localized(language, "A conferência com o servidor falhou, e nada mudou. $detail", "The check with the server failed, and nothing changed. $detail")

    val skipCheckHint: String get() = localized(language, "Para entrar sem conferir (por exemplo, sem rede agora):", "To join without checking (for example, with no network now):")

    fun joined(host: String, alias: String, accounts: Int): String =
        localized(
            language,
            "no time em $host como $alias, com " + if (accounts == 1) "1 conta" else "$accounts contas",
            "on the team at $host as $alias, with " + if (accounts == 1) "1 account" else "$accounts accounts"
        )

    val notChecked: String get() = localized(language, "entrou sem conferência", "joined without a check")
    val checked: String get() = localized(language, "servidor e vínculo das contas conferidos", "server and account links checked")
    val left: String get() = localized(language, "fora do time: nada mais é enviado, e a chave foi apagada", "off the team: nothing else is sent, and the key was deleted")

    val rowTeam: String get() = localized(language, "time", "team")
    val rowServer: String get() = localized(language, "servidor", "server")
    val rowAlias: String get() = localized(language, "apelido", "name")
    val rowKey: String get() = localized(language, "chave", "key")
    val rowAccounts: String get() = localized(language, "contas", "accounts")
    val rowConnection: String get() = localized(language, "conexão", "connection")
    val on: String get() = localized(language, "ligado", "on")
    val off: String get() = localized(language, "desligado", "off")
    val connectionOk: String get() = localized(language, "ok", "ok")
    val none: String get() = localized(language, "nenhuma", "none")

    fun webSwitched(enabled: Boolean, port: Int): String =
        if (enabled) localized(language, "acesso pela rede ligado na porta $port", "network access on at port $port") else localized(language, "acesso pela rede desligado", "network access off")

    val webTokenHint: String get() = localized(language, "O endereço com o token sai com:", "The address with the token comes out with:")
    val webTokenRegenerated: String get() = localized(language, "token novo: todo endereço já copiado deixou de valer", "new token: every address already copied stopped working")
    val webOff: String get() = localized(language, "O acesso pela rede está desligado. Ligue com:", "Network access is off. Turn it on with:")

    val rowAccess: String get() = localized(language, "acesso", "access")
    val rowPort: String get() = localized(language, "porta", "port")
    val rowAddresses: String get() = localized(language, "endereços", "addresses")
    val rowToken: String get() = localized(language, "token", "token")
    val noAddress: String get() = localized(language, "nenhum endereço na rede local", "no address on the local network")
}
