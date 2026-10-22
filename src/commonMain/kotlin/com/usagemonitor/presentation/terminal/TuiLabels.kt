package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized

/**
 * Os textos da moldura e da ajuda do modo interativo (#441), nos dois idiomas do app. Objeto
 * de rótulos, como os da #439 e da #440: nenhum `== AppLanguage.PT` inline (decisão D4).
 */
internal class TuiLabels(val language: AppLanguage) {

    val title: String get() = "usage monitor"

    fun tab(tab: TuiTab): String =
        when (tab) {
            TuiTab.OVERVIEW -> localized(language, "Visão geral", "Overview")
            TuiTab.SESSIONS -> localized(language, "Sessões", "Sessions")
            TuiTab.HISTORY -> localized(language, "Histórico", "History")
            TuiTab.TEAM -> localized(language, "Time", "Team")
            TuiTab.SETTINGS -> localized(language, "Configurações", "Settings")
        }

    /** "14:23 BRT · ao vivo": a coleta está de pé e a última leitura deu certo. */
    fun statusLive(clock: String): String = localized(language, "$clock BRT · ao vivo", "$clock BRT · live")

    /** "leitura de 14:12": a última falhou, ou nada está coletando. */
    fun statusReadingOf(clock: String): String = localized(language, "leitura de $clock", "reading from $clock")

    val statusNoReading: String get() = localized(language, "sem leitura", "no reading")

    val keyTabs: String get() = "1–5"
    val keyTabsLabel: String get() = localized(language, "aba", "tab")
    val keyRefreshLabel: String get() = localized(language, "atualizar", "refresh")
    val keyHelpLabel: String get() = localized(language, "ajuda", "help")
    val keyQuitLabel: String get() = localized(language, "sair", "quit")
    val keyCloseHelpLabel: String get() = localized(language, "fecha a ajuda", "closes the help")
    val keyBackLabel: String get() = localized(language, "volta à lista", "back to the list")

    /** Os atalhos comuns, que vêm depois dos da aba no rodapé. */
    val commonShortcuts: List<TuiShortcut>
        get() = listOf(
            TuiShortcut(keyTabs, keyTabsLabel),
            TuiShortcut("r", keyRefreshLabel),
            TuiShortcut("?", keyHelpLabel),
            TuiShortcut("q", keyQuitLabel)
        )

    val helpTitle: String get() = localized(language, "Ajuda", "Help")
    val helpAnyTab: String get() = localized(language, "Em qualquer aba", "On any tab")
    val helpInLists: String get() = localized(language, "Dentro das listas", "Inside the lists")
    val helpTabsKey: String get() = localized(language, "1 a 5", "1 to 5")
    val helpTabs: String get() = localized(language, "troca de aba", "switches tab")
    val helpRefresh: String get() = localized(language, "lê de novo agora", "reads again now")
    val helpHelp: String get() = localized(language, "esta ajuda", "this help")
    val helpQuit: String get() = localized(language, "sai", "quits")
    val helpMove: String get() = localized(language, "move a seleção", "moves the selection")
    val helpOpen: String get() = localized(language, "abre o detalhe", "opens the detail")
    val helpPeriod: String get() = localized(language, "período ou valor", "period or value")
    val helpSpaceKey: String get() = localized(language, "espaço", "space")
    val helpToggle: String get() = localized(language, "liga e desliga", "turns on and off")

    /** A nota do fim da ajuda, em duas linhas para caber na caixa. */
    val helpSettingsNote: List<String>
        get() = listOf(
            localized(language, "Na aba 5 dá para mudar tudo o que as Configurações", "Tab 5 changes everything the app's Settings"),
            localized(language, "do app mudam, menos tema e HUD.", "change, except theme and HUD.")
        )

    fun tooSmall(columns: Int, rows: Int): String =
        localized(
            language,
            "O terminal tem ${columns}x$rows; o modo interativo precisa de pelo menos ${TuiSize.MIN_COLUMNS}x${TuiSize.MIN_ROWS}.",
            "The terminal is ${columns}x$rows; the interactive mode needs at least ${TuiSize.MIN_COLUMNS}x${TuiSize.MIN_ROWS}."
        )

    val needsTerminal: String
        get() = localized(
            language,
            "O modo interativo precisa de um terminal. Para ler o uso num script, rode usage-monitor status.",
            "The interactive mode needs a terminal. To read the usage in a script, run usage-monitor status."
        )
}
