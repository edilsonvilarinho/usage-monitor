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
    |Ver o uso
    |  status [--json] [--max-age <tempo>]
    |                     Mostra as cotas de todas as contas e sai.
    |  check [--max <n>] [--max-age <tempo>] [--quiet]
    |                     Sai com 0 em dia, 1 atenção, 2 crítico, 3 sem leitura.
    |  wait <cota> --below <n> [--timeout <tempo>]
    |                     Espera a cota cair abaixo de n% e sai. O id de cada
    |                     cota sai no status --json.
    |  statusline <claude-code|tmux>
    |                     Uma linha de uso para o Claude Code ou para o tmux.
    |  alerts [--since <tempo>]
    |                     Os avisos de hoje, quando e para onde foram.
    |
    |Configurar (os mesmos arquivos do app; com ele ou o serviço coletando,
    |quem grava é ele)
    |  setup              Configuração inicial, passo a passo.
    |  config list|get|set
    |                     Os ajustes das Configurações: alertas, teto mensal,
    |                     idioma, atualização, opções do bot e proxy.
    |  sources list|enable|disable
    |                     As fontes monitoradas.
    |  keys set|test|remove <fonte>
    |                     Chaves de API, lidas da entrada, nunca do comando.
    |  accounts list|add|rename|enable|disable|remove|rescan
    |                     Contas do Claude Code e do Codex.
    |  telegram setup|pair|test|status|enable|disable|remove|readonly
    |                     O bot do Telegram.
    |  team join|status|leave
    |                     A integração com o time.
    |  web enable|disable|status|token
    |                     O acesso pela rede local.
    |  update             Confere se há versão nova e diz como atualizar.
    |
    |Servidor sem tela
    |  service install|uninstall|start|stop|restart|status|logs
    |                     O serviço do usuário no systemd (só no Linux).
    |  service run        A coleta em primeiro plano, sem janela.
    |
    |  Os comandos de ver o uso leem a última coleta e não consultam as APIs.
    |  Tempo: 90s, 10m, 2h ou 7d.
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
    |See the usage
    |  status [--json] [--max-age <time>]
    |                     Print the quotas of every account and exit.
    |  check [--max <n>] [--max-age <time>] [--quiet]
    |                     Exit with 0 on track, 1 warning, 2 critical, 3 no reading.
    |  wait <quota> --below <n> [--timeout <time>]
    |                     Wait until the quota drops below n% and exit. Each
    |                     quota id is in status --json.
    |  statusline <claude-code|tmux>
    |                     One usage line for Claude Code or for tmux.
    |  alerts [--since <time>]
    |                     Today's alerts, when and where they went.
    |
    |Configure (the app's own files; with it or the service collecting, it is
    |the one that writes)
    |  setup              First-time setup, step by step.
    |  config list|get|set
    |                     The settings: alerts, monthly budget, language,
    |                     updates, bot options and proxy.
    |  sources list|enable|disable
    |                     The monitored sources.
    |  keys set|test|remove <source>
    |                     API keys, read from the input, never from the command.
    |  accounts list|add|rename|enable|disable|remove|rescan
    |                     Claude Code and Codex accounts.
    |  telegram setup|pair|test|status|enable|disable|remove|readonly
    |                     The Telegram bot.
    |  team join|status|leave
    |                     The team integration.
    |  web enable|disable|status|token
    |                     Access from the local network.
    |  update             Check for a new version and say how to update.
    |
    |Headless server
    |  service install|uninstall|start|stop|restart|status|logs
    |                     The systemd user service (Linux only).
    |  service run        The collection in the foreground, without a window.
    |
    |  The commands that show the usage read the last collection and never call
    |  the APIs. Time: 90s, 10m, 2h or 7d.
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
