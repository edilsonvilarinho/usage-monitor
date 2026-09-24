const { AppHudBar } = DS;

// Uma conta por anel, um arco por cota. A palavra é a da pior cota, e o
// percentual ao lado é o da cota em foco (pior risco, depois maior percentual).
// `reset` só aparece aberto; o saldo pré-pago não tem e nada é impresso.
const ACCOUNTS = [
  {
    label: 'Anthropic — Padrão', statusLabel: 'Atenção', level: 'warn', active: true,
    quotas: [
      { short: '5h', percent: '68%', fraction: 0.68, level: 'warn', reset: '22h59' },
      { short: '7d', percent: '41%', fraction: 0.41, level: 'ok', reset: 'Ter 21h00' }
    ]
  },
  {
    label: 'Anthropic — Sandbox', statusLabel: 'Normal', level: 'ok',
    quotas: [
      { short: '5h', percent: '12%', fraction: 0.12, level: 'ok', reset: '1h30' },
      { short: '7d', percent: '7%', fraction: 0.07, level: 'ok', reset: 'Qui 9h00' }
    ]
  },
  {
    label: 'DeepSeek', statusLabel: 'Sem projeção', level: 'off',
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
    <div style={{ position: 'relative', width: 720, height: tall ? 300 : 170, border: '1px solid var(--border)', borderRadius: 'var(--r3)', background: 'var(--bg)', overflow: 'hidden' }}>
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
      <Caption>barra HUD · notch colado numa borda, um anel por conta</Caption>

      <Caption>1 · parado no topo — anel, percentual em foco e a palavra de cada conta</Caption>
      <Screen>
        <AppHudBar accounts={ACCOUNTS} countdown="02:05" />
      </Screen>

      <Caption>2 · hover — desdobra pela mola, uma linha por cota com barra e reinício</Caption>
      <Screen tall>
        <AppHudBar accounts={ACCOUNTS} expanded countdown="02:05" />
      </Screen>

      <Caption>3 · colado na lateral direita — coluna de anéis, o painel abre para dentro</Caption>
      <Screen edge="right" tall>
        <AppHudBar accounts={ACCOUNTS} edge="right" expanded countdown="02:05" />
      </Screen>

      <Caption>4 · antes da primeira coleta, com atualização pendente</Caption>
      <Screen>
        <AppHudBar accounts={[]} fallbackLabel="Carregando" countdown="02:05" update="Atualização pronta" />
      </Screen>

      <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', maxWidth: '58ch', borderLeft: '2px solid var(--border)', paddingLeft: 'var(--s3)' }}>
        Janela própria, transparente e sempre no topo; a janela principal fica escondida com a
        geometria intacta. Clique em pixel transparente é engolido no Windows (medido), então a
        janela só tem o tamanho do painel enquanto o ponteiro está no notch: cresce de uma vez ao
        entrar, a mola roda dentro dela, e encolhe depois de o conteúdo recolher. Arraste e solte
        perto de qualquer borda: ele gruda na mais próxima e a posição é gravada como borda +
        fração. Clique abre a janela; botão direito vai direto a "Somente cards"; Ctrl+Shift+H e a
        bandeja também saem. O arco fino de sessão ativa gira e o anel de fora pulsa em atenção só
        com a animação contínua ligada — nunca em testes nem capturas.
      </span>
    </div>
  );
}
