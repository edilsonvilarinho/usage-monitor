const { AppHudBar } = DS;

function HudScreen({ children }) {
  return (
    <div style={{ position: 'relative', width: 560, height: 200, border: '1px solid var(--border)', borderRadius: 'var(--r3)', background: 'var(--bg)', overflow: 'hidden' }}>
      <div style={{ position: 'absolute', inset: '28px 16px 16px', border: '1px solid var(--border)', borderRadius: 'var(--r2)', background: 'var(--surface)' }} />
      <div style={{ position: 'absolute', top: 0, right: 0 }}>{children}</div>
    </div>
  );
}

export function Hud() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--s3)', alignItems: 'flex-start' }}>
      <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: '.07em', textTransform: 'uppercase', color: 'var(--muted)' }}>
        barra HUD · pílula de 320dp ancorada no canto superior direito, sem barra de título, sem cards
      </span>
      <HudScreen>
        <AppHudBar
          level="crit" label="Crítico" sourceLabel="Anthropic · Padrão" resetLabel="reset em 42min"
          tooltipTitle={'Todas as fontes\nAnthropic — INFORMATA2 · Crítico\nAnthropic — Padrão · Atenção\nCodex · Normal'}
          style={{ width: 320 }}
        />
      </HudScreen>
      <HudScreen>
        <AppHudBar level="ok" label="Normal" sourceLabel="Codex" resetLabel="reset em 3h 12min" style={{ width: 320 }} />
      </HudScreen>
      <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', maxWidth: '52ch', borderLeft: '2px solid var(--border)', paddingLeft: 'var(--s3)' }}>
        Terceiro chrome, um passo além do modo somente cards (issue #164): a mesma janela principal
        redimensionada a uma pílula de 320×24dp. A primeira versão ocupava a largura inteira da tela
        e cobria os controles de outras janelas (achado testando ao vivo) — daqui em diante fica só
        no canto. Sem drag-to-reorder — não há cards. Três saídas: clique em qualquer ponto da
        pílula, item na bandeja e Ctrl+Shift+H. Hover lista todas as fontes monitoradas, não só a
        pior — o real usa HoverTooltipBox, aqui é o atributo nativo title.
      </span>
    </div>
  );
}
