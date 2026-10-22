package com.usagemonitor

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized

/**
 * A ajuda da linha de comando, no idioma do app (decisão D1 da #439, que revogou o
 * "inglês e ASCII" do #418). O motivo daquela regra continua valendo e é tratado na
 * saída: console que não é UTF-8 recebe o texto sem acento (`TerminalConsole`).
 *
 * Lista só o que existe: `--autostart` fica de fora porque é a marca que a chave `Run`
 * deixa para o registro de arranque, e quem a digita à mão só falsifica a origem gravada.
 * Nomes de comandos e opções são sempre em inglês.
 */
internal fun commandLineHelp(language: AppLanguage, version: String = CURRENT_APP_VERSION): String =
    commandLineVersion(version) + "\n\n" + localized(language, HELP_PT, HELP_EN)

private val HELP_PT = """
    |Uso: usage-monitor [comando] [opções]
    |
    |Sem comando, abre o Usage Monitor ou traz o aberto para a frente.
    |
    |Comandos
    |  status [--json] [--max-age <tempo>]
    |                     Mostra as cotas de todas as contas e sai.
    |  check [--max <n>] [--max-age <tempo>] [--quiet]
    |                     Sai com 0 em dia, 1 atenção, 2 crítico, 3 sem leitura.
    |  wait <cota> --below <n> [--timeout <tempo>]
    |                     Espera a cota cair abaixo de n% e sai. O id de cada
    |                     cota sai no status --json.
    |  statusline <claude-code|tmux>
    |                     Uma linha de uso para o Claude Code ou para o tmux.
    |
    |  Os comandos leem a última coleta do app e não consultam as APIs.
    |  Tempo: 90s, 10m ou 2h.
    |
    |Opções
    |  -h, --help         Mostra esta ajuda e sai.
    |  -v, --version      Mostra a versão e sai.
    |  --diagnostics      Salva um pacote de diagnóstico na pasta pessoal e sai.
    |                     Só metadados: nenhum prompt, resposta ou credencial.
    |  --locale <pt|en>   Idioma só desta execução. A escolha gravada não muda.
    |                     Ignorado se o Usage Monitor já estiver aberto.
    |  --user-data-dir <dir>
    |                     Guarda configurações, histórico, chaves e logs desta
    |                     execução em <dir> em vez de ~/.usage-monitor. Outra
    |                     pasta roda ao lado da padrão; nela, a atualização
    |                     automática e o início com o sistema ficam desligados.
""".trimMargin()

private val HELP_EN = """
    |Usage: usage-monitor [command] [options]
    |
    |Without a command, opens Usage Monitor, or brings the running one to the front.
    |
    |Commands
    |  status [--json] [--max-age <time>]
    |                     Print the quotas of every account and exit.
    |  check [--max <n>] [--max-age <time>] [--quiet]
    |                     Exit with 0 on track, 1 warning, 2 critical, 3 no reading.
    |  wait <quota> --below <n> [--timeout <time>]
    |                     Wait until the quota drops below n% and exit. Each
    |                     quota id is in status --json.
    |  statusline <claude-code|tmux>
    |                     One usage line for Claude Code or for tmux.
    |
    |  The commands read the app's last collection and never call the APIs.
    |  Time: 90s, 10m or 2h.
    |
    |Options
    |  -h, --help         Print this help and exit.
    |  -v, --version      Print the version and exit.
    |  --diagnostics      Save a diagnostics package to the home folder and exit.
    |                     It holds metadata only: no prompt, response or credential.
    |  --locale <pt|en>   Interface language for this run only. The saved choice
    |                     does not change. Ignored if Usage Monitor is already open.
    |  --user-data-dir <dir>
    |                     Keep this run's settings, history, keys and logs in <dir>
    |                     instead of ~/.usage-monitor. Another folder runs side by
    |                     side with the default one; there, automatic updates and
    |                     start with the system stay off.
""".trimMargin()
