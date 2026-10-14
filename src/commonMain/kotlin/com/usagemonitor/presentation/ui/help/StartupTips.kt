package com.usagemonitor.presentation.ui.help

import com.usagemonitor.domain.entity.AppLanguage

/**
 * Uma dica do dia (#411): uma frase sobre uma função, com o rótulo **real** do
 * controle entre aspas, e o tópico da Ajuda que a explica por inteiro — a demo
 * do cartão é a desse tópico, e "Ver na Ajuda" abre nele.
 */
data class StartupTip(val topic: HelpTopic, val pt: String, val en: String) {
    fun text(language: AppLanguage): String = if (language == AppLanguage.PT) pt else en
}

/**
 * As dicas, em roda. A ordem alterna assuntos para que dias seguidos não falem
 * da mesma tela; o índice guardado é posição nesta lista, e encolher a lista
 * apenas reinicia a roda (`startupTipIndex`).
 */
object StartupTipCatalog {
    val tips: List<StartupTip> = listOf(
        StartupTip(
            HelpTopic.WINDOW_MODES,
            "Passe o ponteiro sobre um anel: o balão mostra cada cota, quanto resta e a hora do reinício.",
            "Hover a ring: the balloon shows each quota, how much is left and the reset time."
        ),
        StartupTip(
            HelpTopic.ALERTS,
            "\"Silenciar num período do dia\", na aba \"Alertas\", adia os avisos daquele intervalo — nenhum se perde.",
            "\"Mute during a time range\", in the \"Alerts\" tab, postpones that range's warnings — none is lost."
        ),
        StartupTip(
            HelpTopic.TELEGRAM_BOT,
            "Em Configurações → \"Alertas\" → \"Bot do Telegram\", os avisos e o /status chegam no celular.",
            "In Settings → \"Alerts\" → \"Telegram bot\", the warnings and /status reach the phone."
        ),
        StartupTip(
            HelpTopic.SESSION_ANALYSIS,
            "No balão da conta, \"Análise da sessão: ritmo, vazão e janelas\" diz onde a cota chega no reinício.",
            "In the account balloon, \"Session analysis: pace, throughput and windows\" states where the quota lands at the reset."
        ),
        StartupTip(
            HelpTopic.WINDOW_MODES,
            "Arraste pela mão na ponta do notch e solte perto de qualquer borda, de qualquer monitor: a HUD gruda na mais próxima.",
            "Drag the hand at the end of the notch and drop it near any edge of any monitor: the HUD snaps to the closest one."
        ),
        StartupTip(
            HelpTopic.COMPARISON,
            "Na engrenagem, \"Comparar modelos e APIs\" põe tokens, custo, cache e vazão de cada modelo lado a lado.",
            "On the gear, \"Compare models and APIs\" sets each model's tokens, cost, cache and throughput side by side."
        ),
        StartupTip(
            HelpTopic.ACCOUNTS,
            "Em Configurações → \"Contas\" → \"Editar\", a \"Cor\" e o emoji separam duas contas Claude no notch.",
            "In Settings → \"Accounts\" → \"Edit\", the \"Color\" and the emoji tell two Claude accounts apart on the notch."
        ),
        StartupTip(
            HelpTopic.WEB_ACCESS,
            "Em Configurações → \"Rede\", \"Acesso pela rede local\" abre a HUD no navegador do celular, na mesma rede.",
            "In Settings → \"Network\", \"Local network access\" opens the HUD in the phone's browser, on the same network."
        ),
        StartupTip(
            HelpTopic.WINDOW_MODES,
            "Clique num anel para atualizar só aquela conta; \"Atualizar agora\", na engrenagem, atualiza todas.",
            "Click a ring to refresh only that account; \"Refresh now\", on the gear, refreshes them all."
        ),
        StartupTip(
            HelpTopic.ALERTS,
            "\"Lembrar antes de a cota reiniciar\" avisa pouco antes do reinício, enquanto ainda dá para usar o que sobrou.",
            "\"Remind before a quota resets\" warns shortly before the reset, while what is left can still be used."
        ),
        StartupTip(
            HelpTopic.CLI_SESSIONS,
            "No balão de uma conta Anthropic, \"Sessões CLI desta conta\" lista cada sessão do Claude Code com custo estimado e saúde.",
            "In an Anthropic account balloon, \"CLI sessions for this account\" lists each Claude Code session with estimated cost and health."
        ),
        StartupTip(
            HelpTopic.TELEGRAM_BOT,
            "No Telegram, /quando diz quando cada cota estoura no ritmo atual.",
            "On Telegram, /quando states when each quota runs out at the current pace."
        ),
        StartupTip(
            HelpTopic.DASHBOARD,
            "\"Testar chave\", na aba \"APIs\", faz a coleta de verdade e diz por que uma chave não passa.",
            "\"Test key\", in the \"APIs\" tab, runs the real collection and states why a key fails."
        ),
        StartupTip(
            HelpTopic.WINDOW_MODES,
            "O alfinete do notch, \"Recolher a barra HUD quando parada\", deixa só uma faixa fina com um ponto de risco por conta.",
            "The notch pin, \"Retract the HUD bar when idle\", leaves only a thin strip with one risk dot per account."
        ),
        StartupTip(
            HelpTopic.BUDGET,
            "\"Teto mensal em USD (vazio desliga)\" avisa antes de o gasto estimado do mês passar do limite.",
            "\"Monthly cap in USD (empty disables)\" warns before the month's estimated spending passes the limit."
        ),
        StartupTip(
            HelpTopic.HISTORY,
            "O histórico guarda cada coleta em ~/.usage-monitor/usage-history.db; nada sai da máquina.",
            "History keeps every collection in ~/.usage-monitor/usage-history.db; nothing leaves the machine."
        ),
        StartupTip(
            HelpTopic.BREAKDOWN,
            "A aba \"Resumo\" das Sessões CLI recorta a mesma janela por projeto, modelo, branch ou ferramenta.",
            "The \"Breakdown\" tab of CLI Sessions slices the same window by project, model, branch or tool."
        ),
        StartupTip(
            HelpTopic.TELEGRAM_BOT,
            "Uma conversa pareada pode ser \"Só leitura\": lê o status, mas não muda alertas nem age no PC.",
            "A paired chat can be \"Read only\": it reads the status but cannot change alerts or act on the PC."
        ),
        StartupTip(
            HelpTopic.EXPORT,
            "\"Relatório PDF\" e as exportações levam só metadados de uso — nunca prompt nem resposta.",
            "\"PDF report\" and the exports carry usage metadata only — never a prompt or a response."
        ),
        StartupTip(
            HelpTopic.APPEARANCE,
            "\"Reduzir animações\", na aba \"Aparência\", troca cada transição por uma troca seca.",
            "\"Reduce motion\", in the \"Appearance\" tab, turns every transition into an instant swap."
        ),
        StartupTip(
            HelpTopic.CLI_SESSIONS,
            "No detalhe de uma sessão, o botão de copiar entrega o \"claude --resume\" com o identificador completo.",
            "In a session's detail, the copy button gives the \"claude --resume\" with the full identifier."
        ),
        StartupTip(
            HelpTopic.UPDATES,
            "Com \"Atualização automática\", a versão nova baixa em segundo plano e é aplicada ao fechar o app.",
            "With \"Automatic updates\", the new version downloads in the background and is applied when the app closes."
        ),
        StartupTip(
            HelpTopic.TEAM,
            "A aba \"Time\" junta o uso de uma conta em várias máquinas num servidor próprio.",
            "The \"Team\" tab gathers one account's usage across several machines on a server of one's own."
        ),
        StartupTip(
            HelpTopic.APPEARANCE,
            "\"Tamanho da interface\", na aba \"Aparência\", aumenta todas as janelas juntas, a HUD inclusive.",
            "\"Interface size\", in the \"Appearance\" tab, scales every window together, the HUD included."
        )
    )
}

/** O contador escrito da dica, como o passo do guia. */
fun startupTipCounterLabel(index: Int, total: Int, language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Dica ${index + 1} de $total" else "Tip ${index + 1} of $total"
}

/** Título da janela, na barra da moldura. */
fun startupTipWindowTitle(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Dica do dia" else "Tip of the day"
}
