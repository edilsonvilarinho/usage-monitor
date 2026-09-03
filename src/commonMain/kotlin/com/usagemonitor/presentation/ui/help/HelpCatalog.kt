package com.usagemonitor.presentation.ui.help

import com.usagemonitor.domain.entity.AppLanguage

/**
 * Tópicos da janela de ajuda.
 *
 * Um por bloco de funcionalidade, e não um por controle: a lista existe para
 * responder "o que este app faz e como ligo isso", e vinte e cinco entradas
 * granulares respondem à pergunta errada — vira índice de Configurações, que já
 * tem navegação própria.
 */
enum class HelpTopic {
    DASHBOARD,
    HISTORY,
    CLI_SESSIONS,
    BREAKDOWN,
    BUDGET,
    ALERTS,
    EXPORT,
    TEAM,
    PRESENCE,
    WINDOW_MODES,
    APPEARANCE,
    UPDATES
}

/**
 * O que a ajuda mostra de um tópico.
 *
 * [steps] é o "como ativar" e é o motivo de esta tela existir: descrição sem
 * caminho de ativação descreve algo que o leitor não consegue alcançar. Os
 * passos citam o rótulo **real** do controle — trocar o rótulo na tela sem
 * trocar aqui manda o usuário procurar um botão que não existe.
 *
 * [mediaId] nomeia o recurso `help/<mediaId>.gif`. Não é anulável: todo tópico
 * tem demo, e quem trata ausência é o tocador, que degrada para texto.
 */
data class HelpEntry(
    val title: String,
    val summary: String,
    val description: String,
    val steps: List<String>,
    val mediaId: String
)

/**
 * Catálogo bilíngue das funcionalidades.
 *
 * Mesmo desenho de `CliSessionsGlossary`: enum de tópicos, [readingOrder] e uma
 * função que devolve a entrada no idioma. Aqui não são rótulos — são definições
 * e caminhos de ativação, e o `when` exaustivo do Kotlin só pega a entrada
 * faltando, nunca a entrada vazia ou não traduzida; por isso os testes.
 *
 * O texto sai do README, que é a documentação do produto, e os passos saem do
 * código: nome real da aba de Configurações, rótulo real do interruptor, atalho
 * real do `onKeyEvent`.
 */
object HelpCatalog {

    /**
     * Ordem de leitura: do que se vê ao abrir o app para o que se liga depois.
     *
     * Dashboard primeiro porque é a tela que já está na frente de quem procura
     * ajuda; os modos de janela e a aparência no fim porque são preferências, e
     * quem chega aqui pela primeira vez procura o que o app mede.
     */
    val readingOrder: List<HelpTopic> = listOf(
        HelpTopic.DASHBOARD,
        HelpTopic.HISTORY,
        HelpTopic.CLI_SESSIONS,
        HelpTopic.BREAKDOWN,
        HelpTopic.BUDGET,
        HelpTopic.ALERTS,
        HelpTopic.EXPORT,
        HelpTopic.TEAM,
        HelpTopic.PRESENCE,
        HelpTopic.WINDOW_MODES,
        HelpTopic.APPEARANCE,
        HelpTopic.UPDATES
    )

    fun entry(topic: HelpTopic, language: AppLanguage): HelpEntry {
        return if (language == AppLanguage.PT) portuguese(topic) else english(topic)
    }

    /** Recurso da demo, sem depender do idioma: as demos são geradas só em PT. */
    fun mediaId(topic: HelpTopic): String = portuguese(topic).mediaId

    private fun portuguese(topic: HelpTopic): HelpEntry = when (topic) {
        HelpTopic.DASHBOARD -> HelpEntry(
            title = "Dashboard e integrações",
            summary = "Um card por conta ou integração, com as cotas e o semáforo de risco.",
            description = "A janela principal traz um card por fonte, recolhido a cada dez minutos " +
                "sozinho e com botão de atualizar por card. Cada cota mostra quanto já foi consumido, " +
                "quando a janela reinicia e um ponto de risco que compara o ritmo de consumo com o " +
                "tempo que falta até o reinício. Se uma fonte falhar, as outras continuam: o card com " +
                "problema mostra o motivo e o botão de tentar de novo só recarrega aquele alvo.",
            steps = listOf(
                "Abra as Configurações pela engrenagem do rodapé e vá à aba \"APIs\".",
                "Ligue as integrações em uso. MiniMax, DeepSeek, OpenCode Go e OpenRouter " +
                    "pedem a chave da API ali mesmo; ela é gravada em ~/.usage-monitor/api-keys.json.",
                "No mesmo diálogo, \"Testar chave\" faz a coleta de verdade e diz o motivo quando ela " +
                    "não passa — chave recusada, plano ausente, proxy ou falta de conexão.",
                "Anthropic e Codex leem a credencial que o próprio CLI já gravou na máquina — não há " +
                    "nada a preencher.",
                "Atrás de proxy corporativo, configure-o na aba \"Rede\" e reinicie o app: o cliente " +
                    "HTTP é montado uma vez, no arranque.",
                "Arraste um card para reordenar a grade; o botão \"Minimizar card\" o recolhe à linha " +
                    "do título."
            ),
            mediaId = "dashboard"
        )

        HelpTopic.HISTORY -> HelpEntry(
            title = "Histórico e previsão",
            summary = "O consumo ao longo do tempo, com projeção de esgotamento.",
            description = "Cada coleta é guardada num banco SQLite local. A tela de histórico desenha " +
                "o consumo do intervalo escolhido com os reinícios de janela marcados, a média por " +
                "hora, a previsão de quando a cota esgota e a comparação com o período anterior. A " +
                "comparação usa o consumo de cada janela, nunca o acumulado — o acumulado zera no " +
                "reinício e a conta viraria função de quando o reinício caiu.",
            steps = listOf(
                "No card da fonte, clique em \"Abrir histórico\".",
                "Escolha o intervalo na barra superior; sem ponto no período anterior não há " +
                    "comparativo, porque zero ali significaria \"não consumiu\".",
                "Nada é enviado para lugar nenhum: o histórico fica em ~/.usage-monitor/usage-history.db."
            ),
            mediaId = "history"
        )

        HelpTopic.CLI_SESSIONS -> HelpEntry(
            title = "Sessões do Claude Code",
            summary = "Uma linha por sessão, com custo estimado e veredito de saúde.",
            description = "O app indexa os transcripts que o Claude Code grava na máquina e " +
                "monta uma linha por sessão da janela: tokens, custo estimado a partir da tabela de " +
                "preços por modelo, tempo ativo e um veredito de saúde que diz se a sessão está " +
                "saturada ou pede atenção. A lista se atualiza sozinha enquanto a janela está aberta. " +
                "Só metadados de uso são lidos — nenhum texto de pergunta ou resposta.",
            steps = listOf(
                "No card de uma conta Anthropic, clique em \"Sessões CLI desta conta\".",
                "Use o seletor de janela (5h, 24h, 7 dias, 30 dias) para trocar o recorte.",
                "Clique numa sessão para ver o detalhe: crescimento do contexto turno a turno, " +
                    "composição dos tokens e economia do cache.",
                "O botão de copiar entrega o comando \"claude --resume\" com o identificador " +
                    "completo da sessão, que é o que retoma a conversa direto."
            ),
            mediaId = "cli-sessions"
        )

        HelpTopic.BREAKDOWN -> HelpEntry(
            title = "Resumo por eixo",
            summary = "A mesma janela recortada por projeto, modelo, branch ou ferramenta.",
            description = "O resumo soma os mesmos turnos da lista de sessões, agrupados pelo eixo " +
                "escolhido, e acrescenta o ritmo de queima em dólares e tokens por hora e uma grade " +
                "de atividade por dia da semana e hora. As listas descrevem os mesmos turnos: somar " +
                "baldes de eixos diferentes contaria o mesmo gasto mais de uma vez. O custo é " +
                "recalculado dos tokens, nunca rateado entre modelos.",
            steps = listOf(
                "Na tela de Sessões CLI, troque para a aba \"Resumo\".",
                "Escolha o eixo — projeto, modelo, branch, ferramenta ou atividade — na faixa de abas.",
                "Filtre por trecho do nome, troque a ordem e pagine na faixa de controles.",
                "A janela escolhida no topo vale para as duas abas."
            ),
            mediaId = "breakdown"
        )

        HelpTopic.BUDGET -> HelpEntry(
            title = "Orçamento mensal",
            summary = "Um teto em dólares contra o custo estimado do mês corrente.",
            description = "O orçamento compara o teto configurado com o custo estimado das " +
                "sessões do mês corrente, no fuso da apresentação. Ele independe da janela escolhida " +
                "na tela: orçamento é mensal, e amarrá-lo às últimas cinco horas daria um número sem " +
                "significado. Os créditos de uso da Anthropic aparecem numa linha separada, com a " +
                "moeda explícita, e nunca somados — eles podem vir em outra moeda.",
            steps = listOf(
                "Abra Configurações → \"Alertas\".",
                "Preencha \"Teto mensal em USD (vazio desliga)\".",
                "O acompanhamento aparece na aba \"Resumo\" da tela de Sessões CLI."
            ),
            mediaId = "budget"
        )

        HelpTopic.ALERTS -> HelpEntry(
            title = "Alertas e bandeja",
            summary = "Notificação nativa quando uma cota cruza um limiar ou uma sessão trava.",
            description = "O ícone da bandeja carrega um ponto de risco e o app dispara notificação " +
                "nativa quando uma cota cruza um dos limiares (75, 90 e 100% por padrão), quando uma " +
                "sessão do CLI satura, ou quando o último pedido de uma sessão fica sem resposta " +
                "acima do limiar escolhido. O limiar é piso: 89,9% não cruzou 90%. Há ainda um " +
                "aviso de natureza diferente: os anteriores medem distância até o teto da cota, e " +
                "este mede distância até o seu próprio hábito — ele sai quando o consumo do dia " +
                "passa de um múltiplo da mediana dos últimos dias, no mesmo horário. Sem pelo menos " +
                "três dias medidos, ou com consumo habitual perto de zero, ele não é emitido. No " +
                "período silenciado o aviso é adiado, não perdido — ele sai quando o silêncio " +
                "terminar. O limiar de cota não alcança as fontes de saldo pré-pago (DeepSeek, " +
                "OpenRouter), que não têm teto contra o qual medir percentual, nem as de atividade " +
                "observada (Kilo Free, OpenCode Zen Free), que não informam limite: nelas nenhum " +
                "limiar é avaliado. Os outros três avisos continuam valendo.",
            steps = listOf(
                "Abra Configurações → \"Alertas\".",
                "Ligue \"Avisar quando a quota cruzar um limiar\" e ajuste os limiares.",
                "Ligue \"Avisar quando uma sessão CLI saturar\" e \"Avisar quando uma sessão CLI " +
                    "ficar sem resposta\", com o tempo de espera ao lado.",
                "Ligue \"Avisar quando o consumo do dia fugir do habitual\" e escolha o múltiplo " +
                    "(2×, 3× ou 5×) ao lado.",
                "Use \"Silenciar num período do dia\" para o intervalo do dia em que a " +
                    "notificação não deve sair."
            ),
            mediaId = "alerts"
        )

        HelpTopic.EXPORT -> HelpEntry(
            title = "Exportação e relatório PDF",
            summary = "CSV, JSON e um relatório PDF do recorte que está na tela.",
            description = "CSV e JSON exportam a aba aberta — sessões ou resumo — na janela " +
                "escolhida. O relatório PDF não segue a aba: ele é a janela inteira, com totais, " +
                "eixos e sessões juntos. Turno sem tarifa conhecida exporta célula vazia, nunca zero: " +
                "zero afirmaria que não custou nada. Só metadados de uso saem daqui, nunca conteúdo " +
                "de pergunta ou resposta.",
            steps = listOf(
                "Abra a tela de Sessões CLI ou o modal de Sessões do time.",
                "Escolha a janela e a aba a exportar.",
                "Use os botões CSV, JSON ou PDF na barra superior e escolha onde salvar."
            ),
            mediaId = "export"
        )

        HelpTopic.TEAM -> HelpEntry(
            title = "Visão de time",
            summary = "O consumo da mesma conta somado entre as máquinas do time.",
            description = "Recurso opcional e desligado por padrão. Um servidor Node.js hospedado " +
                "pela empresa recebe os turnos indexados de cada máquina e devolve a visão " +
                "agregada por conta: consumo por integrante, sessões de cada um, resumo por eixo e " +
                "tendência de trinta dias. Cada pessoa usa a chave que o administrador emitiu para " +
                "ela. Só metadados de uso trafegam — nada de conteúdo de prompt ou resposta.",
            steps = listOf(
                "Abra Configurações → \"Time\" e ligue a integração.",
                "Preencha \"Servidor\", \"Chave do time\" e \"Seu apelido\".",
                "Marque as contas em \"Contas que fazem parte do time\".",
                "Clique em \"Testar conexão\": é ela que vincula a conta à chave.",
                "O card da conta ganha o botão \"Sessões do time nesta conta\"."
            ),
            mediaId = "team"
        )

        HelpTopic.PRESENCE -> HelpEntry(
            title = "Presença ao vivo",
            summary = "Quem está com o app aberto e quem está de fato rodando o CLI agora.",
            description = "A presença mostra dois estados separados, não uma escala: \"online\" é " +
                "ter o app aberto, com batida a cada trinta segundos; \"trabalhando agora\" é ter " +
                "produzido turno nos últimos cinco minutos. Colapsar os dois esconderia exatamente o " +
                "caso que a tela existe para mostrar — quem está com o app aberto e parado. As horas " +
                "vêm do relógio do servidor, para um computador atrasado não ver o time inteiro " +
                "online para sempre.",
            steps = listOf(
                "Com a integração de time ligada, clique em \"Quem está conectado agora\" no card " +
                    "da conta.",
                "Quem administra o servidor tem o mesmo botão no rodapé da janela principal, e ali a " +
                    "lista cobre todas as contas."
            ),
            mediaId = "presence"
        )

        HelpTopic.WINDOW_MODES -> HelpEntry(
            title = "Modos de janela",
            summary = "A janela reduzida aos cards, ou a uma barra flutuante sempre visível.",
            description = "O modo somente cards tira a barra de título e o rodapé e deixa a grade de " +
                "cards; a faixa de título volta enquanto o ponteiro está sobre o topo da janela. A " +
                "barra HUD vai mais longe: encolhe a janela a um painel de uma linha por conta, " +
                "sempre no topo das outras, arrastável e que gruda na borda mais próxima. Com tudo " +
                "normal ela se recolhe a um ponto; o ponteiro em cima devolve a lista inteira e " +
                "acrescenta, ao lado de cada cota, a hora em que ela reinicia — a cota que não " +
                "tem reset a mostrar, como um saldo pré-pago, sai só com o percentual. A " +
                "primeira linha termina com quanto falta para a próxima coleta automática. Os " +
                "dois modos são exclusivos: ligar um desliga o outro.",
            steps = listOf(
                "No rodapé da janela, clique no ícone \"Modo de janela\" e escolha \"Padrão\", " +
                    "\"Somente os cards\" ou \"Barra HUD\" — o menu só existe no modo padrão, " +
                    "porque é o único com rodapé.",
                "Pelas Configurações: abra \"Geral\" e use \"Somente os cards\" ou \"Barra HUD\".",
                "Pelo teclado: Ctrl+Shift+M alterna o modo somente cards e Ctrl+Shift+H alterna a " +
                    "barra HUD.",
                "O menu do ícone da bandeja tem as duas entradas — é o caminho de volta quando a " +
                    "janela está coberta por outra.",
                "Na barra HUD, arraste para escolher o canto; um clique curto devolve a janela normal."
            ),
            mediaId = "window-modes"
        )

        HelpTopic.APPEARANCE -> HelpEntry(
            title = "Aparência e janela",
            summary = "Tema, idioma, escala da interface, opacidade e arranque com o sistema.",
            description = "Todos os temas são desenhados a partir dos mesmos tokens, nos dois modos. " +
                "A escala troca a densidade da composição, e não só o tamanho do texto: ícone, " +
                "espaçamento e alvo de clique crescem junto, e as proporções da tela permanecem. A " +
                "janela principal guarda tamanho e posição entre execuções.",
            steps = listOf(
                "Abra Configurações → \"Geral\".",
                "Escolha o tema e o \"Idioma\" na seção Aparência.",
                "\"Tamanho da interface\" vai de 80% a 150%; a janela é reajustada junto.",
                "\"Opacidade da janela\", \"Manter sempre visível\" e \"Inicialização com Sistema\" " +
                    "ficam na seção Sistema."
            ),
            mediaId = "appearance"
        )

        HelpTopic.UPDATES -> HelpEntry(
            title = "Atualização automática",
            summary = "A versão nova baixada em segundo plano e trocada ao fechar o app.",
            description = "Ligada, a atualização baixa a versão nova em segundo plano, confere o " +
                "SHA-256 do arquivo contra o que a API do GitHub publica e faz a troca quando o app " +
                "fecha. No Windows vale para a instalação feita pelo instalador .exe; no Linux, para " +
                "a instalação por script em árvore gerenciada; no macOS o mecanismo não existe e o " +
                "interruptor explica o motivo. Depois de uma troca de versão, a janela de novidades " +
                "lista o que mudou.",
            steps = listOf(
                "Abra Configurações → \"Geral\" e ligue \"Atualização automática\".",
                "Se a instalação não suportar a troca, o interruptor aparece desabilitado com o " +
                    "motivo escrito ao lado.",
                "Com a versão baixada, a faixa no topo do dashboard oferece \"Reiniciar e atualizar " +
                    "agora\"; sem clicar nela, a troca acontece no próximo fechamento."
            ),
            mediaId = "updates"
        )
    }

    private fun english(topic: HelpTopic): HelpEntry = when (topic) {
        HelpTopic.DASHBOARD -> HelpEntry(
            title = "Dashboard and integrations",
            summary = "One card per account or integration, with quotas and the risk semaphore.",
            description = "The main window shows one card per source, refreshed every ten minutes " +
                "on its own and with a per-card refresh button. Each quota shows how much was " +
                "consumed, when the window resets, and a risk dot that weighs the burn rate against " +
                "the time left before the reset. If one source fails the others keep going: the " +
                "failing card states the reason, and its retry button reloads only that target.",
            steps = listOf(
                "Open Settings from the footer gear and go to the \"APIs\" tab.",
                "Enable the integrations in use. MiniMax, DeepSeek, OpenCode Go and OpenRouter ask " +
                    "for the API key right there; it is stored in ~/.usage-monitor/api-keys.json.",
                "In the same dialog, \"Test key\" runs the real collection and states why it failed — " +
                    "key rejected, missing plan, proxy, or no connection.",
                "Anthropic and Codex read the credential the CLI already wrote on this machine — " +
                    "there is nothing to fill in.",
                "Behind a corporate proxy, set it up in the \"Network\" tab and restart the app: the " +
                    "HTTP client is built once, at startup.",
                "Drag a card to reorder the grid; \"Minimize card\" collapses it to its title row."
            ),
            mediaId = "dashboard"
        )

        HelpTopic.HISTORY -> HelpEntry(
            title = "History and forecast",
            summary = "Usage over time, with a projection of when the quota runs out.",
            description = "Every collection is stored in a local SQLite database. The history screen " +
                "plots the chosen range with window resets marked, the hourly average, the forecast " +
                "of when the quota runs out and a comparison with the previous period. The " +
                "comparison uses each window's own consumption, never the running total — the total " +
                "resets with the window, which would make the number depend on when the reset fell.",
            steps = listOf(
                "On the source card, click \"Open history\".",
                "Pick the range in the top bar; with no data point in the previous period there is " +
                    "no comparison, because zero there would mean \"consumed nothing\".",
                "Nothing is sent anywhere: the history lives in ~/.usage-monitor/usage-history.db."
            ),
            mediaId = "history"
        )

        HelpTopic.CLI_SESSIONS -> HelpEntry(
            title = "Claude Code sessions",
            summary = "One row per session, with estimated cost and a health verdict.",
            description = "The app indexes the transcripts Claude Code writes on this machine and " +
                "builds one row per session in the window: tokens, cost estimated from the " +
                "per-model price table, active time and a health verdict that says whether the " +
                "session is saturated or needs attention. The list refreshes itself while the window " +
                "is open. Only usage metadata is read — no prompt or reply text.",
            steps = listOf(
                "On an Anthropic account card, click \"CLI sessions for this account\".",
                "Use the window selector (5h, 24h, 7 days, 30 days) to change the range.",
                "Click a session for the detail: context growth turn by turn, token composition and " +
                    "cache savings.",
                "The copy button hands you the \"claude --resume\" command with the full session " +
                    "identifier, which is what resumes the conversation directly."
            ),
            mediaId = "cli-sessions"
        )

        HelpTopic.BREAKDOWN -> HelpEntry(
            title = "Breakdown by axis",
            summary = "The same window sliced by project, model, branch or tool.",
            description = "The breakdown sums the very same turns as the session list, grouped by " +
                "the chosen axis, and adds the burn rate in dollars and tokens per hour plus an " +
                "activity grid by weekday and hour. The lists describe the same turns: adding " +
                "buckets from different axes would count the same spend more than once. Cost is " +
                "recomputed from tokens, never split between models.",
            steps = listOf(
                "On the CLI sessions screen, switch to the \"Breakdown\" tab.",
                "Pick the axis — project, model, branch, tool or activity — in the tab strip.",
                "Filter by a fragment of the name, change the sort and page through the controls row.",
                "The window chosen at the top applies to both tabs."
            ),
            mediaId = "breakdown"
        )

        HelpTopic.BUDGET -> HelpEntry(
            title = "Monthly budget",
            summary = "A cap in dollars against the estimated cost of the current month.",
            description = "The budget compares the configured cap with the estimated cost of this " +
                "month's sessions, in the presentation time zone. It is independent of the window " +
                "chosen on screen: a budget is monthly, and tying it to the last five hours would " +
                "produce a meaningless number. Anthropic usage credits appear on a separate line " +
                "with the currency spelled out, never added in — they may come in another currency.",
            steps = listOf(
                "Open Settings → \"Alerts\".",
                "Fill in \"Monthly cap in USD (empty disables)\".",
                "The tracking shows up on the \"Breakdown\" tab of the CLI sessions screen."
            ),
            mediaId = "budget"
        )

        HelpTopic.ALERTS -> HelpEntry(
            title = "Alerts and tray",
            summary = "A native notification when a quota crosses a threshold or a session stalls.",
            description = "The tray icon carries a risk dot, and the app fires a native notification " +
                "when a quota crosses one of the thresholds (75, 90 and 100% by default), when a CLI " +
                "session saturates, or when a session's last request goes unanswered for longer than " +
                "the chosen limit. The threshold is a floor: 89.9% has not crossed 90%. There is " +
                "one more warning of a different nature: the ones above measure the distance to the " +
                "quota ceiling, and this one measures the distance to your own habit — it goes out " +
                "when the day's usage passes a multiple of the median of the last few days, at the " +
                "same time of day. With fewer than three measured days, or with usual consumption " +
                "near zero, it is not sent. During a muted period the warning is postponed, not " +
                "lost — it goes out once the silence ends. The quota threshold does not reach " +
                "prepaid balance sources (DeepSeek, OpenRouter), which have no ceiling to measure a " +
                "percentage against, nor observed activity ones (Kilo Free, OpenCode Zen Free), " +
                "which report no limit: on those, no threshold is ever evaluated. The other three " +
                "warnings still apply.",
            steps = listOf(
                "Open Settings → \"Alerts\".",
                "Turn on \"Warn when a quota crosses a threshold\" and adjust the thresholds.",
                "Turn on \"Warn when a CLI session saturates\" and \"Warn when a CLI session goes " +
                    "unanswered\", with the waiting time next to it.",
                "Turn on \"Warn when the day's usage departs from the usual\" and pick the " +
                    "multiple (2×, 3× or 5×) next to it.",
                "Use \"Mute during a time range\" for the part of the day when no notification " +
                    "should go out."
            ),
            mediaId = "alerts"
        )

        HelpTopic.EXPORT -> HelpEntry(
            title = "Export and PDF report",
            summary = "CSV, JSON and a PDF report of what is on screen.",
            description = "CSV and JSON export the open tab — sessions or breakdown — for the chosen " +
                "window. The PDF report does not follow the tab: it is the whole window, with " +
                "totals, axes and sessions together. A turn with no known price exports an empty " +
                "cell, never a zero: a zero would claim it cost nothing. Only usage metadata leaves " +
                "here, never prompt or reply content.",
            steps = listOf(
                "Open the CLI sessions screen or the team sessions modal.",
                "Choose the window and the tab to export.",
                "Use the CSV, JSON or PDF buttons in the top bar and pick where to save."
            ),
            mediaId = "export"
        )

        HelpTopic.TEAM -> HelpEntry(
            title = "Team view",
            summary = "The same account's usage added up across the team's machines.",
            description = "Optional and off by default. A Node.js server the company hosts receives " +
                "the indexed turns from each machine and returns the aggregated view per account: " +
                "usage per member, each one's sessions, the breakdown by axis and a thirty-day " +
                "trend. Each person uses the key the administrator issued for them. Only usage " +
                "metadata travels — no prompt or reply content.",
            steps = listOf(
                "Open Settings → \"Team\" and turn the integration on.",
                "Fill in \"Server\", \"Team key\" and \"Your alias\".",
                "Tick the accounts under \"Accounts in the team\".",
                "Click \"Test connection\": that is what binds the account to the key.",
                "The account card gains the \"Team sessions in this account\" button."
            ),
            mediaId = "team"
        )

        HelpTopic.PRESENCE -> HelpEntry(
            title = "Live presence",
            summary = "Who has the app open, and who is actually running the CLI right now.",
            description = "Presence shows two separate states, not one scale: \"online\" means the " +
                "app is open, with a heartbeat every thirty seconds; \"working now\" means a turn was " +
                "produced in the last five minutes. Collapsing them would hide exactly the case the " +
                "screen exists for — someone with the app open and idle. The timestamps come from " +
                "the server clock, so a machine with a slow clock does not see the whole team online " +
                "forever.",
            steps = listOf(
                "With the team integration on, click \"Who is connected now\" on the account card.",
                "Whoever administers the server has the same button in the main window footer, and " +
                    "there the list covers every account."
            ),
            mediaId = "presence"
        )

        HelpTopic.WINDOW_MODES -> HelpEntry(
            title = "Window modes",
            summary = "The window cut down to the cards, or to a floating always-on-top strip.",
            description = "Cards only mode drops the title bar and the footer and leaves the card " +
                "grid; the title strip comes back while the pointer is over the top of the window. " +
                "The HUD strip goes further: it shrinks the window to a panel with one line per " +
                "account, always above other windows, draggable and snapping to the nearest edge. " +
                "With everything on track it collapses to a single dot; hovering brings the whole " +
                "list back and adds, next to each quota, the time it resets — a quota with no " +
                "reset to show, such as a prepaid balance, keeps only its percentage. The first " +
                "row ends with how long is left until the next automatic " +
                "collection. The two modes are mutually exclusive: turning one on turns the other off.",
            steps = listOf(
                "In the window footer, click the \"Window mode\" icon and pick \"Standard\", " +
                    "\"Cards only\" or \"HUD strip\" — the menu exists only in standard mode, " +
                    "since that is the only one with a footer.",
                "From Settings: open \"General\" and use \"Cards only\" or \"HUD strip\".",
                "From the keyboard: Ctrl+Shift+M toggles cards only mode and Ctrl+Shift+H toggles " +
                    "the HUD strip.",
                "The tray icon menu carries both entries — that is the way back when the window is " +
                    "covered by another one.",
                "On the HUD strip, drag to choose the corner; a short click restores the normal window."
            ),
            mediaId = "window-modes"
        )

        HelpTopic.APPEARANCE -> HelpEntry(
            title = "Appearance and window",
            summary = "Theme, language, interface scale, opacity and start with the system.",
            description = "Every theme is drawn from the same tokens, in both modes. The scale " +
                "changes the density of the composition, not just the text size: icons, spacing and " +
                "click targets grow along with it, and the proportions of the screen stay put. The " +
                "main window remembers its size and position between runs.",
            steps = listOf(
                "Open Settings → \"General\".",
                "Pick the theme and the \"Language\" under Appearance.",
                "\"Interface size\" ranges from 80% to 150%; the window is resized along with it.",
                "\"Window opacity\", \"Always on top\" and \"System Startup\" live under System."
            ),
            mediaId = "appearance"
        )

        HelpTopic.UPDATES -> HelpEntry(
            title = "Automatic updates",
            summary = "The new version downloaded in the background and swapped in on exit.",
            description = "When enabled, the update downloads the new version in the background, " +
                "checks the file's SHA-256 against what the GitHub API publishes, and swaps it in " +
                "when the app closes. On Windows this covers the .exe installer setup; on Linux, the " +
                "script install in a managed tree; on macOS the mechanism does not exist and the " +
                "switch says why. After a version change, the what's new window lists what changed.",
            steps = listOf(
                "Open Settings → \"General\" and turn on \"Automatic updates\".",
                "If your install cannot be swapped, the switch shows up disabled with the reason " +
                    "written next to it.",
                "Once the version is downloaded, the strip at the top of the dashboard offers " +
                    "\"Restart and update now\"; without clicking it, the swap happens on the next exit."
            ),
            mediaId = "updates"
        )
    }
}
