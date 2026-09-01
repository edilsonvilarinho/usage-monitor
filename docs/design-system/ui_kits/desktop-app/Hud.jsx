const { AppHudBar } = DS;

export function Hud() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--s3)', alignItems: 'flex-start' }}>
      <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: '.07em', textTransform: 'uppercase', color: 'var(--muted)' }}>
        barra HUD · ancorada no topo da tela, sem barra de título, sem cards
      </span>
      <div style={{ width: 520, border: '1px solid var(--border)', borderRadius: 'var(--r3)', overflow: 'hidden' }}>
        <AppHudBar level="crit" label="Crítico" sourceLabel="Anthropic · Padrão" resetLabel="reset em 42min" />
      </div>
      <div style={{ width: 520, border: '1px solid var(--border)', borderRadius: 'var(--r3)', overflow: 'hidden' }}>
        <AppHudBar level="ok" label="Normal" sourceLabel="Codex" resetLabel="reset em 3h 12min" />
      </div>
      <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', maxWidth: '52ch', borderLeft: '2px solid var(--border)', paddingLeft: 'var(--s3)' }}>
        Terceiro chrome, um passo além do modo somente cards (issue #164): a mesma janela principal
        redimensionada a uma faixa de 24dp. Sem drag-to-reorder — não há cards. Três saídas: clique em
        qualquer ponto da faixa, item na bandeja e Ctrl+Shift+H.
      </span>
    </div>
  );
}
