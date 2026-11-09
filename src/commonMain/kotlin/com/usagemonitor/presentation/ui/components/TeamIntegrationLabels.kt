package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized

/**
 * Textos da seção "Integração com time" (A08 do briefing #547). Um objeto por idioma
 * de tela, no lugar de `if (isPt)` inline: a seção só lê as propriedades.
 *
 * Os textos com dado variável são funções, para o nome ou o motivo entrar no meio da frase
 * sem concatenação espalhada pela composable.
 */
internal class TeamIntegrationLabels(private val language: AppLanguage) {
    val title = localized(language, "Integração com time", "Team integration")
    val description = localized(
        language,
        "Envia ao servidor da empresa apenas os totais de tokens das sessões — nunca o conteúdo das conversas.",
        "Sends only session token totals to your company server — never conversation content."
    )

    val teamNameLabel = localized(language, "Nome do time", "Team name")
    val teamNamePlaceholder = localized(language, "ex.: Trabalho", "e.g. Work")
    val teamNameEmpty = localized(language, "O nome não pode ficar vazio.", "The name cannot be empty.")

    val serverLabel = localized(language, "Servidor", "Server")
    val teamKeyLabel = localized(language, "Chave do time", "Team key")
    val showKey = localized(language, "Mostrar chave", "Show key")
    val hideKey = localized(language, "Ocultar chave", "Hide key")

    val aliasLabel = localized(language, "Seu apelido", "Your alias")
    val aliasPlaceholder = localized(language, "como o time vai te ver", "how the team sees you")
    val aliasEmpty = localized(language, "O apelido não pode ficar vazio.", "The alias cannot be empty.")

    val accountsTitle = localized(language, "Contas que fazem parte do time", "Accounts in the team")
    val noAccounts = localized(language, "Nenhuma conta Anthropic detectada.", "No Anthropic account detected.")

    val testConnection = localized(language, "Testar conexão", "Test connection")
    val statusConnected = localized(language, "Conectado", "Connected")
    val statusFailed = localized(language, "Falhou", "Failed")
    val statusChecking = localized(language, "Verificando", "Checking")
    val statusIncomplete = localized(language, "Incompleto", "Incomplete")
    val statusNotTested = localized(language, "Não testado", "Not tested")

    val adminSwitch = localized(language, "Eu sou admin do servidor", "I administer the server")
    val adminDescription = localized(
        language,
        "O token de administração dá acesso de leitura a todas as contas do servidor e " +
            "permite emitir chaves. Não é preciso participar de nenhum time.",
        "The admin token grants read access to every account on the server and allows " +
            "issuing keys. You do not need to belong to any team."
    )
    val adminTokenLabel = localized(language, "Token de administração", "Admin token")
    val showToken = localized(language, "Mostrar token", "Show token")
    val hideToken = localized(language, "Ocultar token", "Hide token")
    val validate = localized(language, "Validar", "Validate")
    val manageKeys = localized(language, "Configurar chaves das contas", "Manage account keys")
    val leaveAdmin = localized(language, "Sair do modo admin", "Leave admin mode")

    val addTeam = localized(language, "Adicionar time", "Add team")
    val emptyTitle = localized(language, "Nenhum time configurado", "No team configured")
    val emptyBody = localized(
        language,
        "Um time é um servidor com a própria chave. Adicione um para enviar os totais.",
        "A team is one server with its own key. Add one to send the token totals."
    )

    val removeTeam = localized(language, "Remover time", "Remove team")
    val removeConfirm = localized(language, "Remover", "Remove")
    val cancel = localized(language, "Cancelar", "Cancel")
    val removeMessage = localized(
        language,
        "Só a configuração deste time sai deste computador. Os dados que já foram " +
            "enviados continuam no servidor.",
        "Only this team's configuration leaves this computer. Data already sent stays on the server."
    )

    /** Título do diálogo de remoção, com o nome do time. */
    fun removeTitle(teamName: String): String =
        localized(language, "Remover o time $teamName?", "Remove team $teamName?")

    /** Aviso do envio em background que falhou, com o detalhe já traduzido. */
    fun lastUploadFailed(detail: String): String =
        localized(language, "Último envio ao time falhou: $detail", "Last team upload failed: $detail")

    /** Dica numa conta que já está em outro time. Ligar aqui move a conta. */
    fun moveHint(otherTeamName: String): String = localized(
        language,
        "No time $otherTeamName — ligar aqui move para este time",
        "In team $otherTeamName — turning it on here moves it to this team"
    )

    /** Motivo de recusa do servidor, mostrado na linha da conta. */
    fun rejected(reason: String): String =
        localized(language, "Recusada pelo servidor — $reason", "Refused by the server — $reason")

}
