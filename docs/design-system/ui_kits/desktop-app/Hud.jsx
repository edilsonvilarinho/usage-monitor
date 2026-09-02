const { AppHudBar } = DS;

const SOURCES = [
  { label: 'Anthropic — INFORMATA2', statusLabel: 'Crítico', level: 'crit', percentLabel: '92%', resetLabel: 'Ter 22h59' },
  { label: 'Anthropic — Padrão', statusLabel: 'Atenção', level: 'warn', percentLabel: '41%', resetLabel: '4h12' },
  { label: 'Codex', statusLabel: 'Normal', level: 'ok', percentLabel: '12%', resetLabel: 'Qua 09h00' },
  { label: 'OpenCode Go', statusLabel: 'Normal', level: 'ok', percentLabel: '3%' }
];

const FOOTER = '2 sessões ativas · $4.21 · 1,2M tok · 5h';

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
      <Caption>barra HUD · painel arrastável, uma linha por fonte, teto de 420dp</Caption>

      <Caption>1 · o estado normal — cotas e consumo, sem gesto nenhum</Caption>
      <HudScreen tall>
        <AppHudBar sources={SOURCES} footerLabel={FOOTER} />
      </HudScreen>

      <Caption>2 · tudo em ON_TRACK e sem o ponteiro — recolhido ao ponto</Caption>
      <HudScreen>
        <AppHudBar level="ok" dotOnly />
      </HudScreen>

      <Caption>3 · antes da primeira coleta — uma linha, e ela diz o que está acontecendo</Caption>
      <HudScreen>
        <AppHudBar sources={[]} fallbackLabel="Carregando" />
      </HudScreen>

      <Caption>4 · arrastado para a borda de baixo — logo acima da barra de tarefas</Caption>
      <HudScreen corner="bottom-right" tall>
        <AppHudBar sources={SOURCES.slice(0, 2)} footerLabel={FOOTER} />
      </HudScreen>

      <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', maxWidth: '54ch', borderLeft: '2px solid var(--border)', paddingLeft: 'var(--s3)' }}>
        Terceiro chrome, um passo além do modo somente cards (issue #164): a mesma janela principal
        redimensionada a um painel. Três versões de conteúdo foram achadas erradas ao vivo, uma por
        vez — uma linha só com a pior fonte, depois as outras atrás de um hover, depois a lista sem
        nenhum número de consumo. Agora cada fonte tem sua linha, com ponto, palavra, percentual e
        reset, e o rodapé diz o que a máquina queimou na janela de 5h: as linhas falam do teto do
        fornecedor, o rodapé fala do gasto real. A largura sai do conteúdo, com 420dp de teto, e o
        painel é arrastado para onde o usuário quiser — ao soltar ele gruda na borda mais próxima da
        área útil e a posição é gravada. Três saídas: clique curto em qualquer ponto, item na bandeja
        e Ctrl+Shift+H.
      </span>
    </div>
  );
}
