const { AppHudBar, AppGargantuaRing } = DS;

// Uma conta por anel, um arco por cota. A palavra é a da pior cota, e ao lado
// vai uma linha por anel com a janela, de fora para dentro ("7d 41%", "5h 68%").
// `reset` só aparece no balão; o saldo pré-pago não tem e nada é impresso.
const ACCOUNTS = [
  {
    label: 'Anthropic — Padrão', provider: 'anthropic', statusLabel: 'Atenção', level: 'warn', active: true,
    detail: 'Max 20x · via Claude Code',
    quotas: [
      { short: '5h', period: 'interval', title: 'Sessão 5h', percent: '68%', fraction: 0.68, level: 'warn', reset: '22h59', usedLeft: '68% usado · 32% restante' },
      { short: '7d', period: 'weekly', title: 'Semanal', percent: '41%', fraction: 0.41, level: 'ok', reset: 'Ter 21h00', usedLeft: '41% usado · 59% restante' }
    ]
  },
  {
    label: 'Anthropic — Sandbox', provider: 'anthropic', color: '#E2A5E9', statusLabel: 'Normal', level: 'ok',
    quotas: [
      { short: '5h', period: 'interval', percent: '12%', fraction: 0.12, level: 'ok', reset: '1h30' },
      { short: '7d', period: 'weekly', percent: '7%', fraction: 0.07, level: 'ok', reset: 'Qui 9h00' }
    ]
  },
  {
    label: 'DeepSeek', provider: 'deepseek', statusLabel: 'Sem projeção', level: 'off',
    quotas: [{ short: 'Saldo', percent: '$2.27', fraction: 0.3, level: 'off', forecast: false }]
  }
];

function Screen({ children, edge = 'top', tall = false }) {
  const place = {
    top: { top: 0, left: '50%', transform: 'translateX(-50%)' },
    bottom: { bottom: 0, left: '50%', transform: 'translateX(-50%)' },
    right: { right: 0, top: '50%', transform: 'translateY(-50%)' },
    left: { left: 0, top: '50%', transform: 'translateY(-50%)' }
  }[edge];
  return (
    <div style={{ position: 'relative', width: 720, height: tall ? 440 : 170, border: '1px solid var(--border)', borderRadius: 'var(--r3)', background: 'var(--bg)', overflow: 'hidden' }}>
      <div style={{ position: 'absolute', inset: '40px 24px 24px', border: '1px solid var(--border)', borderRadius: 'var(--r2)', background: 'var(--surface)', opacity: .5 }} />
      <div style={{ position: 'absolute', ...place }}>{children}</div>
    </div>
  );
}

function Caption({ children }) {
  return (
    <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: '.07em', textTransform: 'uppercase', color: 'var(--muted)' }}>
      {children}
    </span>
  );
}

export function Hud() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--s3)', alignItems: 'flex-start' }}>
      <Caption>barra HUD · Gargantua, uma cena por conta, um arco por cota</Caption>

      <Caption>0 · identidade preservada nas onze APIs — captura estática</Caption>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 24, maxWidth: 720, padding: 16 }}>
        {[
          ['Anthropic', 'anthropic'], ['Codex', 'codex'], ['MiniMax', 'minimax'],
          ['DeepSeek', 'deepseek'], ['OpenCode Zen', 'opencode'], ['OpenCode Go', 'opencode'],
          ['Kilo Free', 'kilo'], ['OpenRouter', 'openrouter'], ['Gemini CLI', 'gemini'],
          ['Cursor', 'cursor'], ['Antigravity', 'antigravity']
        ].map(([name, provider]) => <div key={name} style={{ width: 92, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
          <AppGargantuaRing provider={provider} arcs={[{ fraction: .68, level: 'ok' }, { fraction: .24, level: 'ok' }]} label={`${name} · Exemplo visual de cotas`} />
          <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>{name}</span>
        </div>)}
      </div>

      <Caption>1 · parado no topo — anel, uma linha por janela e a palavra de cada conta</Caption>
      <Screen>
        <AppHudBar accounts={ACCOUNTS} countdown="02:05" refreshFraction={0.21} />
      </Screen>

      <Caption>2 · ponteiro no primeiro anel — balão só daquela conta, alças nas pontas</Caption>
      <Screen tall>
        <AppHudBar accounts={ACCOUNTS} balloon={0} countdown="02:05" refreshFraction={0.21} actions={['⟲', '▣', '⚇', '◉']} />
      </Screen>

      <Caption>3 · colado na lateral direita — o balão abre para dentro, a cauda no anel</Caption>
      <Screen edge="right" tall>
        <AppHudBar accounts={ACCOUNTS} edge="right" balloon={0} countdown="02:05" refreshFraction={0.21} />
      </Screen>

      <Caption>3b · engrenagem — o que o rodapé oferece: versão instalada, contagem (só aqui, #269), modos, ações</Caption>
      <Screen tall>
        <AppHudBar accounts={ACCOUNTS} balloon="gear" countdown="02:05" version="38.2.0" refreshFraction={0.21} />
      </Screen>

      <Caption>3c · engrenagem com atualização pronta — versão instalada, ponto na engrenagem, banner e o botão da faixa do modo padrão</Caption>
      <Screen tall>
        <AppHudBar accounts={ACCOUNTS} balloon="gear" countdown="02:05" version="38.2.0" refreshFraction={0.21}
          update="Versão 38.1.0 pronta — será aplicada ao fechar o Usage Monitor"
          updateHeadline="Versão 38.1.0 pronta" updateDetail="Aplicada ao fechar o Usage Monitor"
          updateAction="Reiniciar o app e atualizar" />
      </Screen>

      <Caption>4 · antes da primeira coleta, com atualização pendente — parado, o sinal é o arco da engrenagem</Caption>
      <Screen>
        <AppHudBar accounts={[]} fallbackLabel="Carregando" countdown="02:05" refreshFraction={0.21} update="Atualização pronta" />
      </Screen>

      <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', maxWidth: '58ch', borderLeft: '2px solid var(--border)', paddingLeft: 'var(--s3)' }}>
        Janela própria, transparente e sempre no topo: o único modo de visualização do app, sem
        janela principal. O notch não cresce: o detalhe é o balão de uma conta, a do anel sob o
        ponteiro. Clique em pixel transparente é engolido no Windows (medido), então a janela só
        tem o tamanho da área aberta enquanto o ponteiro está no notch: cresce de uma vez ao
        entrar e encolhe depois de o balão sair, sem mover o notch. Só a mão move (solte perto de
        qualquer borda: ele gruda na mais próxima, gravado como borda + fração); a engrenagem abre
        as ações do rodapé no hover, como o anel (#317). Clique num anel atualiza aquela conta; botão direito não faz nada;
        "Abrir" da bandeja traz o notch para a frente. O arco
        de sessão ativa é um cometa azul externo (2,6s). O disco de acreção dourado, a lente gravitacional e
        os filamentos animam em 14s (4s ao atualizar); a atenção respira em 3,2s. O balão abre
        pelo jato relativístico: feixe do anel pela cauda e desdobrar a partir dele (520ms/240ms).
        Dado novo rola pelo horizonte: só os dígitos que mudaram, 480ms, cascata de 50ms. Os arcos de quota e
        as marcas dos provedores ficam ancorados. Movimento contínuo só com a política ligada e sem
        reduzir animações — esta referência mantém a cena completa e estática, como os testes.
      </span>
    </div>
  );
}
