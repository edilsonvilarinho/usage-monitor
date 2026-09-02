const { AppHudBar } = DS;

const SOURCES = [
  { label: 'Anthropic — INFORMATA2', statusLabel: 'Crítico', level: 'crit' },
  { label: 'Anthropic — Padrão', statusLabel: 'Atenção', level: 'warn' },
  { label: 'Codex', statusLabel: 'Normal', level: 'ok' }
];

function HudScreen({ children, corner = 'top-right' }) {
  const anchor = corner === 'top-right'
    ? { top: 0, right: 0 }
    : { bottom: 0, right: 0 };
  return (
    <div style={{ position: 'relative', width: 560, height: 200, border: '1px solid var(--border)', borderRadius: 'var(--r3)', background: 'var(--bg)', overflow: 'hidden' }}>
      <div style={{ position: 'absolute', inset: '28px 16px 16px', border: '1px solid var(--border)', borderRadius: 'var(--r2)', background: 'var(--surface)' }} />
      <div style={{ position: 'absolute', ...anchor, display: 'flex', flexDirection: corner === 'top-right' ? 'column' : 'column-reverse' }}>
        {children}
      </div>
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
      <Caption>barra HUD · pílula arrastável, medida pelo conteúdo, teto de 320dp</Caption>

      <Caption>1 · alguma fonte em risco — pílula com texto</Caption>
      <HudScreen>
        <AppHudBar level="crit" label="Crítico" sourceLabel="Anthropic · Padrão" resetLabel="reset em 42min" />
      </HudScreen>

      <Caption>2 · tudo em ON_TRACK — recolhida ao ponto</Caption>
      <HudScreen>
        <AppHudBar level="ok" dotOnly />
      </HudScreen>

      <Caption>3 · hover — a janela cresce e lista todas as fontes</Caption>
      <HudScreen>
        <AppHudBar
          level="crit" label="Crítico" sourceLabel="Anthropic · Padrão" resetLabel="reset em 42min"
          expanded sources={SOURCES}
        />
      </HudScreen>

      <Caption>4 · arrastada para a borda de baixo — o painel cresce para cima</Caption>
      <HudScreen corner="bottom-right">
        <AppHudBar
          level="warn" label="Atenção" sourceLabel="Anthropic · Padrão" resetLabel="reset em 2h"
          expanded sources={SOURCES}
        />
      </HudScreen>

      <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', maxWidth: '52ch', borderLeft: '2px solid var(--border)', paddingLeft: 'var(--s3)' }}>
        Terceiro chrome, um passo além do modo somente cards (issue #164): a mesma janela principal
        redimensionada a uma pílula. Duas versões foram achadas erradas testando ao vivo — largura de
        tela inteira, que cobria os controles de outras janelas, e depois 320dp fixos num canto, que
        ainda mediam 320dp para mostrar a palavra "Normal". Agora a largura sai do conteúdo, com
        320dp de teto, e a pílula é arrastada para onde o usuário quiser: ao soltar ela gruda na
        borda mais próxima da área útil — a de baixo fica logo acima da barra de tarefas — e a
        posição é gravada. O hover cresce a própria janela em vez de abrir popup: popup aqui é camada
        dentro da janela, e numa faixa de 24dp ele saía recortado sobre o próprio alvo, piscando.
        Sem drag-to-reorder — não há cards. Três saídas: clique curto em qualquer ponto da pílula,
        item na bandeja e Ctrl+Shift+H.
      </span>
    </div>
  );
}
