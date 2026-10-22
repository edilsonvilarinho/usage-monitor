package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized

/**
 * Os textos da aba 5, Configurações (4.2). O rótulo de cada campo é o do controle nas
 * Configurações do app (`AlertSettingsSection`, `TelegramBotOptions`, `NetworkSettingsSection`…):
 * quem conhece o app reconhece o campo, e a ajuda cita um texto só.
 */
internal class TuiSettingsLabels(val language: AppLanguage) {
    private fun text(pt: String, en: String): String = localized(language, pt, en)

    val headerSections: String get() = text("SEÇÕES", "SECTIONS")

    val sectionSources: String get() = text("Fontes e chaves", "Sources and keys")
    val sectionAccounts: String get() = text("Contas", "Accounts")
    val sectionAlerts: String get() = text("Alertas", "Alerts")
    val sectionTelegram: String get() = text("Telegram", "Telegram")
    val sectionTeam: String get() = text("Time", "Team")
    val sectionNetwork: String get() = text("Rede e proxy", "Network and proxy")
    val sectionWeb: String get() = text("Acesso web", "Web access")
    val sectionSystem: String get() = text("Sistema", "System")

    // Fontes e chaves (aba APIs do app).
    fun apiKey(name: String): String = text("Chave do $name", "$name key")
    fun removeKey(name: String): String = text("Remover chave do $name", "Remove the $name key")
    val keySaved: String get() = text("salva", "saved")
    val keyMissing: String get() = text("sem chave", "no key")
    val keyTested: String get() = text("testada antes de gravar", "tested before saving")

    // Contas.
    val rescan: String get() = text("Redetectar", "Rescan")
    val rescanNote: String get() = text("procura as contas do Claude Code de novo", "searches the Claude Code accounts again")
    val noLogin: String get() = text("sem login", "not logged in")
    val bySource: String get() = text("segue a fonte Codex", "follows the Codex source")
    val renameNote: String get() = text("enter renomeia", "enter renames")
    val accountsHint: String get() = text("adicionar ou remover: usage-monitor accounts add|remove", "add or remove: usage-monitor accounts add|remove")

    // Alertas (os rótulos do `AlertSettingsSection`).
    val quotaAlerts: String get() = text("Avisar quando a quota cruzar um limiar", "Warn when a quota crosses a threshold")
    val thresholds: String get() = text("Limiares (%)", "Thresholds (%)")
    val accountThresholds: String get() = text("Usar o geral em todas", "Use the general one everywhere")
    val resetAlerts: String get() = text("Avisar quando a cota reiniciar", "Warn when a quota resets")
    val reminder: String get() = text("Lembrar antes de a cota reiniciar", "Remind before a quota resets")
    val sessionAlerts: String get() = text("Avisar quando uma sessão CLI saturar", "Warn when a CLI session saturates")
    val stalledAlerts: String get() = text("Avisar quando uma sessão CLI ficar sem resposta", "Warn when a CLI session goes unanswered")
    val stalledAfter: String get() = text("sem resposta depois de", "unanswered after")
    val spikeAlerts: String get() = text("Avisar quando o consumo do dia fugir do habitual", "Warn when the day's usage departs from the usual")
    val spikeFactor: String get() = text("múltiplo do habitual", "multiple of the usual")
    val quietHours: String get() = text("Silenciar num período do dia", "Mute during a time range")
    val quietNote: String get() = text("22-08 · vazio desliga", "22-08 · empty disables")
    val budget: String get() = text("Teto mensal em USD (vazio desliga)", "Monthly cap in USD (empty disables)")
    fun minutesBefore(minutes: Int): String = text("$minutes min antes", "$minutes min before")

    // Telegram (os rótulos do `TelegramBotSection` e do `TelegramBotOptions`).
    val bot: String get() = text("Bot do Telegram", "Telegram bot")
    val botToken: String get() = text("Token do bot", "Bot token")
    val botTokenNote: String get() = text("conferido no Telegram antes de gravar", "checked with Telegram before saving")
    val pair: String get() = text("Parear conversa", "Pair chat")
    val pairNote: String get() = text("pede o código ao bot; o coletor precisa estar de pé", "asks the bot for a code; the collector must be running")
    val sendTest: String get() = text("Enviar teste", "Send test")
    fun chatReadOnly(name: String): String = text("$name · só leitura", "$name · read only")
    fun removeChat(name: String): String = text("Remover $name", "Remove $name")
    val dailySummary: String get() = text("Resumo diário", "Daily summary")
    val livePanel: String get() = text("Painel fixado ao vivo", "Live pinned panel")
    val sourceControl: String get() = text("Permitir mudar fontes pelo bot", "Allow changing sources from the bot")
    val pcActions: String get() = text("Permitir ações no PC", "Allow PC actions")
    val machineName: String get() = text("Nome deste PC nas mensagens", "This PC's name in messages")
    val showTeam: String get() = text("Mostrar o time no bot", "Show the team in the bot")
    val remoteUpdate: String get() = text("Permitir atualizar pelo bot", "Allow updating from the bot")
    val soundOnlyCritical: String get() = text("Só alerta crítico toca", "Only critical alerts ring")
    val groupRepeated: String get() = text("Agrupar alertas repetidos", "Group repeated alerts")
    val batchAlerts: String get() = text("Juntar alertas", "Batch alerts")
    val compactPanel: String get() = text("Painel em uma linha", "One-line panel")
    val sourceHealth: String get() = text("Avisar fonte com falha", "Warn about a failing source")
    val noBot: String get() = text("salve o token primeiro", "save the token first")

    // Time (os rótulos do `TeamIntegrationSection`).
    val teamState: String get() = text("Integração com time", "Team integration")
    val server: String get() = text("Servidor", "Server")
    val alias: String get() = text("Seu apelido", "Your alias")
    val teamAccounts: String get() = text("Contas no time", "Accounts in the team")
    val teamKey: String get() = text("Chave do time", "Team key")
    val teamKeyNote: String get() = text("entra com o servidor e o apelido acima", "joins with the server and alias above")
    val leave: String get() = text("Sair do time", "Leave the team")
    val leaveNote: String get() = text("apaga a chave deste computador", "deletes the key on this computer")
    val teamHint: String get() = text("outro servidor ou apelido: usage-monitor team join", "another server or alias: usage-monitor team join")
    val on: String get() = text("ligada", "on")
    val off: String get() = text("desligada", "off")
    val none: String get() = text("nenhuma", "none")

    // Rede e proxy (os rótulos do `NetworkSettingsSection`).
    val proxyEnvironment: String get() = text("Usar variável de ambiente do sistema (HTTP_PROXY/HTTPS_PROXY)", "Use the system environment variable (HTTP_PROXY/HTTPS_PROXY)")
    val proxyHost: String get() = text("Host", "Host")
    val proxyPort: String get() = text("Porta", "Port")
    val proxyUser: String get() = text("Usuário (opcional)", "Username (optional)")
    val proxyPassword: String get() = text("Senha (opcional)", "Password (optional)")

    // Acesso web (os rótulos do `WebAccessSection`).
    val web: String get() = text("Acesso pela rede local", "Local network access")
    fun webPort(port: Int): String = text("porta $port", "port $port")
    val webAddress: String get() = text("Gerar novo endereço", "Generate new address")
    val webAddressNote: String get() = text("todo endereço já copiado deixa de valer", "every address already copied stops working")
    val webHint: String get() = text("o endereço com o token: usage-monitor web token", "the address with the token: usage-monitor web token")

    // Sistema (os rótulos do `SettingsSystemTab` e do `SettingsAppearanceTab`).
    val languageLabel: String get() = text("Idioma", "Language")
    val autoUpdate: String get() = text("Atualização automática", "Automatic updates")
    val betaUpdates: String get() = text("Receber versões beta", "Receive beta updates")
    val autostart: String get() = text("Inicialização com Sistema", "System Startup")
    val systemHint: String get() = text("tema, opacidade, escala e HUD continuam no app", "theme, opacity, scale and HUD stay in the app")

    val offValue: String get() = text("desligado", "off")
    val emptyValue: String get() = text("vazio", "empty")

    /** O rodapé já diz `enter grava · esc desiste`: a nota só avisa que o segredo não ecoa. */
    val editingSecret: String get() = text("não aparece na tela", "not shown on screen")
    val confirm: String get() = text("enter de novo confirma", "enter again confirms")

    val keyField: String get() = text("campo", "field")
    val keySection: String get() = text("seção", "section")
    val keyToggle: String get() = text("liga/desliga", "on/off")
    val keyValue: String get() = text("valor", "value")
    val keyEdit: String get() = text("edita", "edits")
    val keyBack: String get() = text("volta", "back")
    val keyRun: String get() = text("executa", "runs")
    val spaceKey: String get() = text("espaço", "space")
    val keyOpen: String get() = text("abre", "opens")
    val keySave: String get() = text("grava", "saves")
    val keyGiveUp: String get() = text("desiste", "gives up")
}
