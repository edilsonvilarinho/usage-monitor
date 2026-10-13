package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage

/**
 * As categorias do menu do bot. O Telegram não tem seção no `setMyCommands` (só nome e
 * descrição), então a categoria vai na **ordem** e no emoji que abre a descrição; no
 * `/ajuda` ela vira cabeçalho.
 */
internal enum class TelegramCommandCategory(val emoji: String, private val pt: String, private val en: String) {
    NOW("📊", "Agora", "Now"),
    ANALYSIS("📐", "Análises", "Analysis"),
    ALERTS("🔔", "Alertas", "Alerts"),
    CHAT("💬", "Conversa e PC", "Chat and PC");

    fun title(language: AppLanguage): String = if (language == AppLanguage.PT) pt else en
}

/**
 * Um comando do menu: o nome em cada idioma (o `parseBotCommand` entende os dois), a
 * descrição curta do botão Menu e a linha do `/ajuda`, com as variantes de argumento.
 */
internal class TelegramCommandEntry(
    val category: TelegramCommandCategory,
    val namePt: String,
    val nameEn: String,
    private val menuPt: String,
    private val menuEn: String,
    private val helpPt: String,
    private val helpEn: String
) {
    fun name(language: AppLanguage): String = if (language == AppLanguage.PT) namePt else nameEn
    fun menuDescription(language: AppLanguage): String = if (language == AppLanguage.PT) menuPt else menuEn
    fun helpLine(language: AppLanguage): String = if (language == AppLanguage.PT) helpPt else helpEn
}

/**
 * Dono único do menu de comandos do bot: o `setMyCommands` e o `/ajuda` saem daqui, na
 * ordem das categorias. Comando novo entra numa categoria — nunca solto no fim da lista.
 */
internal object TelegramCommandCatalog {

    val entries: List<TelegramCommandEntry> = listOf(
        // 📊 Agora: o estado das contas neste momento.
        entry(TelegramCommandCategory.NOW, "status", "status", "Cotas de todas as contas", "Quotas of every account",
            "<code>/status</code> — cotas de todas as contas · <code>/status img</code> — os anéis em imagem",
            "<code>/status</code> — quotas of every account · <code>/status img</code> — the rings as an image"),
        entry(TelegramCommandCategory.NOW, "conta", "account", "Uma conta por vez", "One account at a time",
            "<code>/conta</code> — escolha uma conta e veja só ela", "<code>/account</code> — pick one account and see only it"),
        entry(TelegramCommandCategory.NOW, "atualizar", "refresh", "Coleta agora e mostra as cotas", "Collect now and show the quotas",
            "<code>/atualizar</code> — coleta agora e mostra as cotas", "<code>/refresh</code> — collect now and show the quotas"),
        entry(TelegramCommandCategory.NOW, "qual", "which", "Que conta tem mais folga agora", "Which account has the most room now",
            "<code>/qual</code> — que conta de cada fonte tem mais folga", "<code>/which</code> — which account of each source has the most room"),
        entry(TelegramCommandCategory.NOW, "reinicios", "resets", "Próximos reinícios das cotas", "Next quota resets",
            "<code>/reinicios</code> — próximos reinícios, em ordem", "<code>/resets</code> — next resets, in order"),
        entry(TelegramCommandCategory.NOW, "quando", "when", "Quando cada cota estoura no ritmo atual", "When each quota runs out at the current pace",
            "<code>/quando</code> — quando cada cota estoura no ritmo atual", "<code>/when</code> — when each quota runs out at the current pace"),
        entry(TelegramCommandCategory.NOW, "ping", "ping", "O app está aberto? Versão e última coleta", "Is the app running? Version and last collection",
            "<code>/ping</code> — versão, tempo aberto e última coleta", "<code>/ping</code> — version, uptime and last collection"),
        // 📐 Análises: ritmo, vazão e histórico para entender o consumo.
        entry(TelegramCommandCategory.ANALYSIS, "ritmo", "pace", "Quanto da cota semanal cabe por dia", "How much of the weekly quota fits per day",
            "<code>/ritmo</code> — quanto das janelas longas cabe por dia", "<code>/pace</code> — how much of the long windows fits per day"),
        entry(TelegramCommandCategory.ANALYSIS, "vazao", "throughput", "Tokens por segundo por modelo (24h)", "Tokens per second per model (24h)",
            "<code>/vazao</code> — tokens por segundo por modelo, 24 h", "<code>/throughput</code> — tokens per second per model, 24 h"),
        entry(TelegramCommandCategory.ANALYSIS, "semana", "week", "Esta semana contra a anterior", "This week against the previous one",
            "<code>/semana</code> — últimos 7 dias contra os 7 anteriores", "<code>/week</code> — last 7 days against the 7 before"),
        entry(TelegramCommandCategory.ANALYSIS, "janelas", "windows", "Últimas janelas com pico (5h ou 7d)", "Latest windows with their peak (5h or 7d)",
            "<code>/janelas</code> · <code>/janelas 7d</code> — últimas janelas, pico e tempo ativo",
            "<code>/windows</code> · <code>/windows 7d</code> — latest windows, peak and active time"),
        entry(TelegramCommandCategory.ANALYSIS, "comparar", "compare", "Mapa de calor dos modelos (7d)", "Model heat map (7d)",
            "<code>/comparar</code> · <code>/comparar 30d</code> — mapa de calor dos modelos", "<code>/compare</code> · <code>/compare 30d</code> — model heat map"),
        entry(TelegramCommandCategory.ANALYSIS, "pico", "spike", "Consumo de hoje acima do seu hábito", "Today's usage above your habit",
            "<code>/pico</code> — o que está acima do seu hábito hoje", "<code>/spike</code> — what is above your habit today"),
        entry(TelegramCommandCategory.ANALYSIS, "grafico", "chart", "Gráfico do uso (24h ou 7d)", "Usage chart (24h or 7d)",
            "<code>/grafico</code> · <code>/grafico 7d</code> — gráfico do uso", "<code>/chart</code> · <code>/chart 7d</code> — usage chart"),
        entry(TelegramCommandCategory.ANALYSIS, "resumo", "summary", "Resumo do dia", "Summary of the day",
            "<code>/resumo</code> — resumo do dia", "<code>/summary</code> — summary of the day"),
        entry(TelegramCommandCategory.ANALYSIS, "exportar", "export", "Uso do Claude CLI em arquivo (7d csv)", "Claude CLI usage as a file (7d csv)",
            "<code>/exportar</code> · <code>/exportar 30d json</code> — uso do Claude CLI em arquivo",
            "<code>/export</code> · <code>/export 30d json</code> — Claude CLI usage as a file"),
        // 🔔 Alertas: o que chega sem pedir.
        entry(TelegramCommandCategory.ALERTS, "alertas", "alerts", "Alertas por tipo, ou todos (on ou off)", "Alerts by kind, or all (on or off)",
            "<code>/alertas</code> — liga e desliga cada tipo · <code>/alertas on</code> · <code>/alertas off</code> — todos",
            "<code>/alerts</code> — turn each kind on or off · <code>/alerts on</code> · <code>/alerts off</code> — all"),
        entry(TelegramCommandCategory.ALERTS, "limiar", "threshold", "Limiares de cota (75,90), ou por conta", "Quota thresholds (75,90), or per account",
            "<code>/limiar 75,90</code> — avisa em 75% e 90% · <code>/limiar</code> — limiar de uma conta",
            "<code>/threshold 75,90</code> — alert at 75% and 90% · <code>/threshold</code> — one account's threshold"),
        entry(TelegramCommandCategory.ALERTS, "silencio", "quiet", "Horário de silêncio (22-07 ou off)", "Quiet hours (22-07 or off)",
            "<code>/silencio</code> — 1 h, 4 h ou até 08:00 · <code>/silencio 22-07</code> · <code>/silencio off</code>",
            "<code>/quiet</code> — 1 h, 4 h or until 08:00 · <code>/quiet 22-07</code> · <code>/quiet off</code>"),
        entry(TelegramCommandCategory.ALERTS, "foco", "focus", "Só alertas críticos por um tempo (2h ou off)", "Only critical alerts for a while (2h or off)",
            "<code>/foco 2h</code> · <code>/foco off</code> — só alertas críticos por um tempo",
            "<code>/focus 2h</code> · <code>/focus off</code> — only critical alerts for a while"),
        entry(TelegramCommandCategory.ALERTS, "avise", "remind", "Lembrete antes do reinício (15 ou off)", "Reminder before resets (15 or off)",
            "<code>/avise 15</code> · <code>/avise off</code> — lembrete antes de reiniciar uma cota alta",
            "<code>/remind 15</code> · <code>/remind off</code> — reminder before a high quota resets"),
        // 💬 Conversa e PC: pareamento, atalhos, fontes e ações no computador.
        entry(TelegramCommandCategory.CHAT, "api", "api", "Fontes monitoradas", "Monitored sources",
            "<code>/api</code> — fontes monitoradas", "<code>/api</code> — monitored sources"),
        entry(TelegramCommandCategory.CHAT, "conversas", "chats", "Conversas pareadas", "Paired chats",
            "<code>/conversas</code> — conversas pareadas", "<code>/chats</code> — paired chats"),
        entry(TelegramCommandCategory.CHAT, "sair", "leave", "Desfaz o pareamento desta conversa", "Unpair this chat",
            "<code>/sair</code> — desfaz o pareamento desta conversa", "<code>/leave</code> — unpair this chat"),
        entry(TelegramCommandCategory.CHAT, "teclado", "keyboard", "Atalhos no lugar do teclado (on ou off)", "Shortcuts in place of the keyboard (on or off)",
            "<code>/teclado on</code> · <code>/teclado off</code> — atalhos no lugar do teclado",
            "<code>/keyboard on</code> · <code>/keyboard off</code> — shortcuts in place of the keyboard"),
        entry(TelegramCommandCategory.CHAT, "hud", "hud", "Traz a HUD à frente no PC", "Bring the HUD to the front on the PC",
            "<code>/hud</code> — traz a HUD à frente no PC", "<code>/hud</code> — bring the HUD to the front on the PC"),
        entry(TelegramCommandCategory.CHAT, "time", "team", "Quem do time está online", "Who from the team is online",
            "<code>/time</code> — quem do time está online", "<code>/team</code> — who from the team is online"),
        entry(TelegramCommandCategory.CHAT, "ajuda", "help", "Lista de comandos", "List of commands",
            "<code>/ajuda</code> — esta lista", "<code>/help</code> — this list")
    )

    /** O botão Menu do Telegram: na ordem das categorias, a descrição aberta pelo emoji dela. */
    fun menu(language: AppLanguage): List<Pair<String, String>> =
        ordered().map { entry -> entry.name(language) to "${entry.category.emoji} ${entry.menuDescription(language)}" }

    /** O `/ajuda`: um cabeçalho por categoria e as linhas dela, com as variantes de argumento. */
    fun help(language: AppLanguage): String {
        val title = if (language == AppLanguage.PT) "<b>Comandos</b>" else "<b>Commands</b>"
        val tip = if (language == AppLanguage.PT) "<i>Toque num comando para copiar.</i>" else "<i>Tap a command to copy it.</i>"
        val sections = TelegramCommandCategory.entries.map { category ->
            val lines = entries.filter { entry -> entry.category == category }.joinToString("\n") { entry -> entry.helpLine(language) }
            "<b>${category.emoji} ${category.title(language)}</b>\n$lines"
        }
        return (listOf(title) + sections + tip).joinToString("\n\n")
    }

    private fun ordered(): List<TelegramCommandEntry> = entries.sortedBy { entry -> entry.category.ordinal }

    private fun entry(
        category: TelegramCommandCategory,
        namePt: String,
        nameEn: String,
        menuPt: String,
        menuEn: String,
        helpPt: String,
        helpEn: String
    ) = TelegramCommandEntry(category, namePt, nameEn, menuPt, menuEn, helpPt, helpEn)
}
