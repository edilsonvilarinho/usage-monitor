package com.usagemonitor.presentation.ui.help

import com.usagemonitor.presentation.ui.UPDATE_RESTART_ACTION_PT
import com.usagemonitor.presentation.ui.components.BETA_UPDATES_LABEL_PT

/**
 * Entradas da ajuda em português.
 *
 * Arquivo próprio desde a #411: os dois idiomas no mesmo arquivo passariam de
 * 800 linhas com os tópicos novos. Quem consulta é sempre [HelpCatalog.entry].
 */
internal fun portugueseHelpEntry(topic: HelpTopic): HelpEntry = when (topic) {
    HelpTopic.DASHBOARD -> PT_DASHBOARD
    HelpTopic.HISTORY -> PT_HISTORY
    HelpTopic.CLI_SESSIONS -> PT_CLI_SESSIONS
    HelpTopic.BREAKDOWN -> PT_BREAKDOWN
    HelpTopic.BUDGET -> PT_BUDGET
    HelpTopic.ALERTS -> PT_ALERTS
    HelpTopic.EXPORT -> PT_EXPORT
    HelpTopic.TEAM -> PT_TEAM
    HelpTopic.PRESENCE -> PT_PRESENCE
    HelpTopic.WINDOW_MODES -> PT_WINDOW_MODES
    HelpTopic.APPEARANCE -> PT_APPEARANCE
    HelpTopic.UPDATES -> PT_UPDATES
    HelpTopic.ACCOUNTS -> PT_ACCOUNTS
    HelpTopic.SESSION_ANALYSIS -> PT_SESSION_ANALYSIS
    HelpTopic.COMPARISON -> PT_COMPARISON
    HelpTopic.TELEGRAM_BOT -> PT_TELEGRAM_BOT
    HelpTopic.WEB_ACCESS -> PT_WEB_ACCESS
}

private val PT_DASHBOARD = HelpEntry(
    title = "Integrações e cotas",
    summary = "Cada conta ou integração ligada vira um anel, com as cotas e o risco.",
    description = "Cada fonte ligada vira um anel na barra HUD, e o balão dele mostra as cotas: " +
        "quanto já foi consumido, quando a janela reinicia e o risco, que compara o ritmo de " +
        "consumo com o tempo que falta até o reinício. A coleta é automática; clicar num anel " +
        "atualiza só aquela conta. Se uma fonte falhar, as outras continuam e a última leitura " +
        "fica na tela, com o motivo no balão.",
    steps = listOf(
        "Passe o ponteiro sobre a engrenagem na ponta do notch, abra \"Configurações\" e vá à " +
            "aba \"APIs\".",
        "Ligue as integrações em uso. MiniMax, DeepSeek, OpenCode Go e OpenRouter " +
            "pedem chave em Configurações → APIs, salva em ~/.usage-monitor/api-keys.json. " +
            "Gemini CLI lê sessões locais; Cursor usa a sessão local existente para buscar " +
            "o resumo pessoal; Antigravity lê as cotas pelo /usage do CLI já autenticado.",
        "No mesmo diálogo, \"Testar chave\" faz a coleta de verdade e diz o motivo quando ela " +
            "não passa — chave recusada, plano ausente, proxy ou falta de conexão.",
        "Anthropic e Codex leem a credencial que o próprio CLI já gravou na máquina — não há " +
            "nada a preencher.",
        "Atrás de proxy corporativo, configure-o na aba \"Rede\" e reinicie o Usage Monitor " +
            "(não o computador): o cliente HTTP é montado uma vez, no arranque."
    ),
    mediaId = "dashboard"
)

private val PT_HISTORY = HelpEntry(
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
        "O resumo precede o gráfico. Expanda \"Resumo das cotas\" e \"Janelas e distribuição horária\" para consultar os detalhes.",
        "Abra \"Conta\" para selecionar outra conta. Clique em \"PDF\" para salvar fonte, conta, intervalo e cotas selecionadas. Zoom e seções recolhidas não retiram dados do relatório.",
        "Nada é enviado para lugar nenhum: o histórico fica em ~/.usage-monitor/usage-history.db."
    ),
    mediaId = "history"
)

private val PT_CLI_SESSIONS = HelpEntry(
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

private val PT_BREAKDOWN = HelpEntry(
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

private val PT_BUDGET = HelpEntry(
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

private val PT_ALERTS = HelpEntry(
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
        "observada (Gemini CLI, Kilo Free, OpenCode Zen Free), que não informam limite: nelas " +
        "nenhum limiar é avaliado. Os outros três avisos continuam valendo.",
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

private val PT_EXPORT = HelpEntry(
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

private val PT_TEAM = HelpEntry(
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

private val PT_PRESENCE = HelpEntry(
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

private val PT_WINDOW_MODES = HelpEntry(
    title = "Barra HUD",
    summary = "O app inteiro num notch flutuante, sempre visível.",
    description = "O app mora num notch colado numa borda da " +
        "tela, sempre no topo das outras, com um anel por conta — um arco por cota —, o " +
        "percentual de cada janela (7d, 5h) e a palavra do estado. Com o ponteiro sobre um anel abre-se o balão " +
        "daquela conta: cada cota com a barra, quanto foi usado e quanto resta e a hora " +
        "em que ela reinicia, o plano e de onde veio a leitura, e os mesmos botões do " +
        "card. Clicar num anel atualiza aquela conta. Nas pontas do notch ficam a mão, " +
        "que o move, e a engrenagem, que abre versão, atualização, exportação, " +
        "Configurações e Ajuda — inclusive quanto falta para a próxima coleta automática.",
    steps = listOf(
        "Passe o ponteiro sobre a engrenagem na ponta do notch para abrir \"Configurações\", " +
            "\"Ajuda\", \"Atualizar agora\" e a exportação; \"Sair do Usage Monitor\" fecha o app.",
        "O menu do ícone da bandeja tem \"Abrir\", que traz o notch para a frente quando " +
            "ele está coberto por outra janela.",
        "Arraste pela mão da ponta do notch (só ela move) e solte " +
            "perto de qualquer borda, de qualquer monitor — em cima, embaixo ou nas " +
            "laterais: ele gruda na mais próxima e fica ali na próxima abertura.",
        // Leitura, não ativação: fica no fim da lista, e a descrição mantém
        // o tamanho que deixa "Como ativar" acima da dobra.
        "Para ler os anéis: o de fora é a janela mais longa (a semanal) e o de dentro a " +
            "mais curta (a de 5h). No balão, o glifo ao lado de cada cota acende o anel dela.",
        "Para distinguir duas contas Claude: em Configurações → \"Contas\", clique em " +
            "\"Editar\" no perfil e escolha a \"Cor\" e o \"Emoji\" — o emoji fica no canto " +
            "do anel e ao lado do nome no balão e no card.",
        "Sessões do Claude Code com o contexto crescendo ou saturado, ou sem resposta " +
            "desde o último pedido, aparecem na seção \"Sessões CLI\" do balão da conta.",
        "Sem nenhuma API habilitada o notch diz \"Nenhuma API\" e as Configurações abrem " +
            "sozinhas no arranque."
    ),
    mediaId = "window-modes"
)

private val PT_APPEARANCE = HelpEntry(
    title = "Aparência e janela",
    summary = "Tema, idioma, escala da interface, opacidade e arranque com o sistema.",
    description = "Todos os temas são desenhados a partir dos mesmos tokens, nos dois modos. " +
        "A escala troca a densidade da composição, e não só o tamanho do texto: ícone, " +
        "espaçamento e alvo de clique crescem junto, e as proporções da tela permanecem. A " +
        "janela principal guarda tamanho e posição entre execuções.",
    steps = listOf(
        "Abra Configurações → \"Aparência\".",
        "Escolha o tema e o \"Idioma\". A \"Prévia da barra HUD\", no topo da aba, mostra a " +
            "barra com o tema, o tamanho e a opacidade escolhidos.",
        "\"Tamanho da interface\" vai de 80% a 150%; a janela é reajustada junto. " +
            "\"Opacidade da janela\" fica na mesma aba.",
        "\"Inicialização com Sistema\" fica em Configurações → \"Sistema\"."
    ),
    mediaId = "appearance"
)

private val PT_UPDATES = HelpEntry(
    title = "Atualização automática",
    summary = "A versão nova baixada em segundo plano e trocada ao fechar o app.",
    description = "Ligada, a atualização baixa a versão nova em segundo plano, confere o " +
        "SHA-256 do arquivo contra o que a API do GitHub publica e faz a troca quando o app " +
        "fecha. No Windows vale para a instalação feita pelo instalador .exe; no Linux, para " +
        "a instalação por script em árvore gerenciada; no macOS o mecanismo não existe e o " +
        "interruptor explica o motivo. Depois de uma troca de versão, a janela de novidades " +
        "lista o que mudou.",
    steps = listOf(
        "Abra Configurações → \"Sistema\" e ligue \"Atualização automática\".",
        "Se a instalação não suportar a troca, o interruptor aparece desabilitado com o " +
            "motivo escrito ao lado.",
        "Com a versão baixada, a faixa no topo do dashboard — ou, na barra HUD, o balão da " +
            "engrenagem, que ganha um ponto colorido — oferece o botão " +
            "\"$UPDATE_RESTART_ACTION_PT\"; sem clicar nele, a troca acontece no próximo " +
            "fechamento do app.",
        "Para testar versões antes da estável, ligue \"$BETA_UPDATES_LABEL_PT\" logo abaixo. " +
            "A faixa, o balão da HUD, as novidades e o rodapé dizem \"beta\"; desligar não " +
            "volta para a versão anterior — o app fica na beta até sair uma estável mais nova."
    ),
    mediaId = "updates"
)

private val PT_ACCOUNTS = HelpEntry(
    title = "Contas e perfis",
    summary = "Várias contas Claude e Codex na mesma máquina, com cor e emoji.",
    description = "Cada conta Anthropic detectada na máquina vira um perfil com anel próprio. " +
        "Apelido, cor e emoji separam duas contas Claude no notch, no balão e nas janelas — o " +
        "emoji fica no canto do anel. Contas Codex além da padrão entram pelo diretório de " +
        "configuração de cada uma. Nada disso muda o que é medido: é só identificação.",
    steps = listOf(
        "Abra \"Configurações\" pela engrenagem e vá à aba \"Contas\".",
        "Em \"Contas Anthropic\", clique em \"Editar\" no perfil para trocar \"Apelido\", " +
            "\"Cor\" e o emoji.",
        "\"Redetectar\" procura de novo as credenciais do Claude Code na máquina; " +
            "\"Remover do monitor\" tira o perfil sem apagar nada do CLI.",
        "Em \"Contas Codex extras\", \"Adicionar\" aponta o diretório de configuração de " +
            "outra conta Codex."
    ),
    mediaId = "accounts"
)

private val PT_SESSION_ANALYSIS = HelpEntry(
    title = "Análise da sessão",
    summary = "Ritmo, vazão e janelas de uma conta, num painel que se atualiza sozinho.",
    description = "A análise lê o histórico da conta e o uso local do CLI e responde se o " +
        "ritmo atual chega ao fim da janela: o percentual da 5h e da semanal, o ritmo na última " +
        "hora, o ritmo seguro até o reinício e onde a cota chega no reinício. Traz também " +
        "intervalos de 1 h a 7 dias, as 24 horas, a janela anterior no mesmo ponto e a vazão por " +
        "modelo. Com a janela aberta, ela relê a cada coleta da conta e a cada 30 segundos. Vale " +
        "para contas Anthropic e Codex; o que não foi medido aparece como não medido, nunca " +
        "como zero.",
    steps = listOf(
        "No balão da conta (ponteiro sobre o anel), clique em \"Análise da sessão: ritmo, " +
            "vazão e janelas\".",
        "Passe o ponteiro sobre as barras das \"Últimas 24 h\" para ver os pontos e os tok/s de cada hora.",
        "O selo \"Ao vivo\" diz a hora da leitura na tela; \"Relatório PDF\" salva a mesma análise numa página.",
        "A mesma análise chega pelo bot do Telegram com /analise."
    ),
    mediaId = "session-analysis"
)

private val PT_COMPARISON = HelpEntry(
    title = "Comparar modelos e APIs",
    summary = "Custo, tokens e vazão por modelo e por fonte, lado a lado.",
    description = "Uma linha por modelo do Claude e do Codex e uma por fonte sem CLI, no " +
        "recorte escolhido. A cor de cada célula é o valor sobre o maior da coluna, na cor da " +
        "fonte, e o número está sempre escrito. Nada é derivado: modelo sem tarifa mostra " +
        "\"sem tarifa\" e fonte que não informa tokens mostra \"—\". A leitura é feita ao abrir " +
        "e ao trocar o recorte, não ao vivo.",
    steps = listOf(
        "Passe o ponteiro sobre a engrenagem na ponta do notch e clique em \"Comparar modelos " +
            "e APIs\".",
        "Troque o recorte na barra superior: \"5h\", \"7 dias\", \"30 dias\" ou \"Total\".",
        "\"Relatório PDF\" salva a tabela, sem a cor.",
        "A coluna \"Cota da fonte\" é a cota corrente da conta, não um gasto do recorte."
    ),
    mediaId = "comparison"
)

private val PT_TELEGRAM_BOT = HelpEntry(
    title = "Bot do Telegram",
    summary = "Status, alertas e gráficos no celular, por um bot próprio.",
    description = "O app conversa direto com um bot do Telegram criado por quem o usa, sem " +
        "servidor no meio. Com uma conversa pareada, o bot repassa os alertas da bandeja e " +
        "responde a comandos como /status, /quando e /analise, com gráficos e botões na " +
        "própria mensagem. Só conversas pareadas são atendidas, e uma conversa pode ser \"Só " +
        "leitura\". Saem só metadados de uso, nunca prompt ou resposta.",
    steps = listOf(
        "Em Configurações → aba \"Alertas\" → \"Bot do Telegram\", ligue o interruptor.",
        "Crie o bot com o @BotFather e cole o token em \"Token do bot\".",
        "Em \"Parear uma conversa\", envie ao bot o /start com o código mostrado — \"Abrir no " +
            "Telegram\" já abre a conversa com ele.",
        "\"Enviar teste\" confirma a entrega; /ajuda, no Telegram, lista os comandos."
    ),
    mediaId = "telegram-bot"
)

private val PT_WEB_ACCESS = HelpEntry(
    title = "HUD no navegador",
    summary = "A mesma leitura da HUD no celular ou em outro computador da rede.",
    description = "Ligado, o app serve uma página com os anéis de cada conta para aparelhos " +
        "da mesma rede local. O endereço carrega um token, exigido em toda rota, e \"Gerar novo " +
        "endereço\" troca o token e invalida os links já copiados. A página mostra a hora da " +
        "última coleta e se atualiza sozinha. Nasce desligado; no Windows, o firewall pode " +
        "pedir permissão ao ligar.",
    steps = listOf(
        "Em Configurações → aba \"Rede\" → \"Acesso pela rede local\", ligue o interruptor.",
        "\"Copiar\" leva o endereço; abra-o no navegador do outro aparelho, na mesma rede.",
        "A \"Porta\" pode ser trocada se a padrão estiver ocupada.",
        "Para cortar o acesso de quem já tem o link, use \"Gerar novo endereço\"."
    ),
    mediaId = "web-access"
)
