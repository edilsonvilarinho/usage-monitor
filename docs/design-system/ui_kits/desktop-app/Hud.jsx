const { AppHudBar } = DS;

// Uma linha por CONTA, com um ponto por cota: a palavra é a da pior cota, e os
// pontos detalham o que ela resumiu -- o desenho do card. Cota sem projeção sai
// com ponto neutro e a palavra dizendo isso.
//
// `reset` é a hora do reinício (issue #189), desenhada só no painel expandido.
// O saldo pré-pago não tem reset a mostrar e vem sem o campo: nada é impresso
// no lugar.
const SOURCES = [
  {
    label: 'INFORMATA2', statusLabel: 'Crítico', level: 'crit',
    quotas: [
      { text: '5h 28%', level: 'ok', reset: '22h59' },
      { text: '7d 9%', level: 'crit', reset: 'Ter 21h00' }
    ]
  },
  {
    label: 'Padrão', statusLabel: 'Atenção', level: 'warn',
    quotas: [
      { text: '5h 88%', level: 'warn', reset: '1h30' },
      { text: '7d 41%', level: 'ok', reset: 'Qui 9h00' }
    ]
  },
  {
    label: 'OpenCode Go', statusLabel: 'Sem projeção', level: 'off',
    quotas: [
      { text: '5h 0%', level: 'off', reset: '22h59' },
      { text: 'mensal 47%', level: 'off', reset: 'Qua 21h00' }
    ]
  },
  {
    label: 'DeepSeek', statusLabel: 'Sem projeção', level: 'off',
    quotas: [{ text: 'Saldo $2.27', level: 'off' }]
  }
];

function HudScreen({ children, corner = 'top-right', tall = false }) {
  const anchor = corner === 'top-right' ? { top: 0, right: 0 } : { bottom: 0, right: 0 };
  return (
    <div style={{ position: 'relative', width: 620, height: tall ? 250 : 190, border: '1px solid var(--border)', borderRadius: 'var(--r3)', background: 'var(--bg)', overflow: 'hidden' }}>
      <div style={{ position: 'absolute', inset: '28px 16px 16px', border: '1px solid var(--border)', borderRadius: 'var(--r2)', background: 'var(--surface)' }} />
      <div style={{ position: 'absolute', ...anchor }}>{children}</div>
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
      <Caption>barra HUD · painel arrastável, uma linha por conta, teto de 484dp parada</Caption>

      <Caption>1 · parada — uma linha, a primeira da ordem de cards</Caption>
      <HudScreen>
        <AppHudBar sources={SOURCES} countdown="02:05" />
      </HudScreen>

      <Caption>2 · hover — a lista, a hora do reinício por cota, e a contagem só na primeira linha</Caption>
      <HudScreen tall>
        <AppHudBar sources={SOURCES} expanded countdown="02:05" />
      </HudScreen>

      <Caption>3 · tudo em ON_TRACK e sem o ponteiro — recolhido ao ponto</Caption>
      <HudScreen>
        <AppHudBar level="ok" dotOnly />
      </HudScreen>

      <Caption>4 · antes da primeira coleta — uma linha, e ela diz o que está acontecendo</Caption>
      <HudScreen>
        <AppHudBar sources={[]} fallbackLabel="Carregando" countdown="02:05" />
      </HudScreen>

      <Caption>5 · arrastado para a borda de baixo — logo acima da barra de tarefas</Caption>
      <HudScreen corner="bottom-right" tall>
        <AppHudBar sources={SOURCES.slice(0, 2)} expanded countdown="02:05" />
      </HudScreen>

      <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', maxWidth: '54ch', borderLeft: '2px solid var(--border)', paddingLeft: 'var(--s3)' }}>
        Terceiro chrome, um passo além do modo somente cards (issue #164): a mesma janela principal
        redimensionada a um painel. Três versões de conteúdo foram achadas erradas ao vivo, uma por
        vez — uma linha só com a pior fonte, depois as outras atrás de um hover, depois a lista sem
        nenhum número de consumo. Parada, a barra mostra uma linha; com o ponteiro em cima, todas as cotas, cada uma com a hora em que reinicia (issue #189) — a pílula parada não a
        mostra, porque é ela que fica capturando o clique de quem está atrás. A largura sai do
        conteúdo, com teto por estado: 484dp parada, mais três colunas de reset expandida, e o
        painel é arrastado para onde o usuário quiser — ao soltar ele gruda na borda mais próxima da
        área útil e a posição é gravada. Três saídas: clique curto em qualquer ponto, item na bandeja
        e Ctrl+Shift+H. A linha termina com a contagem até a próxima coleta (issue #185), que sai
        <b> uma vez só</b>, na primeira: o polling é do app inteiro, e uma contagem por linha diria
        que cada conta tem coleta própria. Recolhida ao ponto ela não aparece — ali não há texto
        nenhum, e o hover devolve o painel com ela. Cota sem reset a mostrar — o saldo que não
        expira — sai com o percentual e nada no lugar, nem um traço.
      </span>
    </div>
  );
}
