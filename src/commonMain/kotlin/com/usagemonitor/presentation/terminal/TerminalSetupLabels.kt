package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized

/** Os textos do assistente `setup` (#440, 4.1) e do `update`, nos dois idiomas do app. */
internal class TerminalSetupLabels(val language: AppLanguage) {
    fun header(dataRoot: String): String = localized(language, " · configuração inicial · dados em $dataRoot", " · first-time setup · data in $dataRoot")

    fun step(number: Int, title: String): String = "$number/5  $title"

    val accountsTitle: String get() = localized(language, "Contas encontradas neste computador", "Accounts found on this computer")
    fun monitorAccounts(count: Int): String =
        localized(language, if (count == 1) "Monitorar a conta pronta?" else "Monitorar as $count contas prontas?", if (count == 1) "Monitor the ready account?" else "Monitor the $count ready accounts?")
    val noReadyAccounts: String
        get() = localized(language, "Nenhuma conta do Claude Code nem do Codex pronta aqui. Faça login no CLI e rode de novo, ou adicione depois com usage-monitor accounts add.", "No Claude Code or Codex account is ready here. Log in with the CLI and run again, or add one later with usage-monitor accounts add.")
    val keysTitle: String get() = localized(language, "Chaves de API", "API keys")
    val keysHint: String get() = localized(language, " · enter pula · a chave não aparece na tela", " · enter skips · the key is not shown")
    fun keyPrompt(name: String): String = localized(language, "     $name chave:", "     $name key:")
    val skipped: String get() = localized(language, "pulada", "skipped")
    val alertsTitle: String get() = localized(language, "Avisos", "Alerts")
    fun thresholdsPrompt(current: String): String = localized(language, "     Limiares de alerta [$current]:", "     Alert thresholds [$current]:")
    val telegramPrompt: String get() = localized(language, "     Avisar pelo Telegram? [s/N]", "     Alert through Telegram? [y/N]")
    val telegramTokenPrompt: String get() = localized(language, "     Token do bot (o que o @BotFather deu):", "     Bot token (the one @BotFather gave you):")
    val pairLater: String get() = localized(language, "o pareamento vem no fim, com o serviço de pé", "pairing comes at the end, with the service up")
    fun languageTitle(current: String): String = localized(language, "Idioma [pt/en] ($current):", "Language [pt/en] ($current):")
    val backgroundTitle: String get() = localized(language, "Segundo plano", "Background")
    val installPrompt: String get() = localized(language, "     Instalar como serviço que inicia com o sistema? [S/n]", "     Install as a service that starts with the system? [Y/n]")
    val notLinux: String
        get() = localized(language, "No Windows e no macOS, quem coleta é o app: abra-o e ligue Iniciar com o sistema.", "On Windows and macOS, the app collects: open it and turn on Start with the system.")
    val pairNowPrompt: String get() = localized(language, "     Parear uma conversa do Telegram agora? [S/n]", "     Pair a Telegram chat now? [Y/n]")
    val done: String get() = localized(language, "Pronto.", "Done.")
    val seeWith: String get() = localized(language, " Veja com ", " See it with ")
    val canRedo: String get() = localized(language, "Pode refazer tudo a qualquer momento com usage-monitor setup.", "You can redo everything any time with usage-monitor setup.")

    /** Sim e não nos dois idiomas; vazio é o padrão da pergunta. */
    fun answer(text: String?, default: Boolean): Boolean =
        when (text?.trim()?.lowercase()) {
            null, "" -> default
            "s", "sim", "y", "yes" -> true
            else -> false
        }

    fun upToDate(version: String): String = localized(language, "$version é a versão mais nova", "$version is the latest version")
    fun available(latest: String, current: String): String = localized(language, "$latest disponível (esta é a $current)", "$latest available (this is $current)")
    val updatePackage: String get() = localized(language, "Instalado pelo pacote: baixe o .deb ou o .rpm da versão e instale com o apt ou o dnf:", "Installed from the package: download the version's .deb or .rpm and install it with apt or dnf:")
    val updateScript: String get() = localized(language, "Instalado pelo script: rode o instalador da versão nova:", "Installed by the script: run the new version's installer:")
    val updateApp: String get() = localized(language, "O app se atualiza sozinho quando está aberto. Ou baixe em:", "The app updates itself while it is open. Or download it at:")
    val restartAfter: String get() = localized(language, "Depois, para o serviço usar a versão nova:", "Then, for the service to use the new version:")
    fun checkFailed(detail: String): String = localized(language, "Não deu para conferir agora: $detail", "Could not check now: $detail")
}
