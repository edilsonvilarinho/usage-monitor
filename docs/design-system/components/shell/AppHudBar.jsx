import React from 'react';

const LEVELS = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', off: 'var(--muted)' };

// O notch da HUD (Compose: `HudNotch`). Colado numa borda da tela: reto e rente
// nela, cantos redondos do lado de dentro e ombros côncavos ligando os dois.
// O notch não cresce: por conta, anel + uma linha por anel com a janela
// ("7d 72%", "5h 45%", #286) + palavra. Com o
// ponteiro em cima aparecem as alças (mão e engrenagem) e, ao lado, o balão de
// UMA conta — a do anel sob o ponteiro — ou o da engrenagem.
export function AppHudBar({
  accounts = [], edge = 'top', balloon, fallbackLabel = 'Carregando', countdown, update, updateHeadline, updateDetail, updateAction,
  actions = ['⟲', '▣'], style
}) {
  const horizontal = edge === 'top' || edge === 'bottom';
  const open = balloon !== undefined && balloon !== null;
  const shoulder = 8;
  const radius = 14;
  const flat = {
    top: { borderTop: 'none', borderRadius: `0 0 ${radius}px ${radius}px` },
    bottom: { borderBottom: 'none', borderRadius: `${radius}px ${radius}px 0 0` },
    left: { borderLeft: 'none', borderRadius: `0 ${radius}px ${radius}px 0` },
    right: { borderRight: 'none', borderRadius: `${radius}px 0 0 ${radius}px` }
  }[edge];

  const notch = (
    <div style={{
      display: 'flex', flexDirection: horizontal ? 'row' : 'column', alignItems: 'center',
      justifyContent: 'center', gap: 12, padding: horizontal ? `8px ${12 + shoulder}px` : `${12 + shoulder}px 8px`,
      background: 'linear-gradient(var(--sheen), transparent 56px), var(--surface)',
      border: '1px solid var(--border-top)', ...flat,
      boxShadow: 'inset 0 1px 0 var(--highlight), var(--shadow-dialog)'
    }}>
      {accounts.length === 0 ? (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>
          <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--muted)' }} />{fallbackLabel}
        </span>
      ) : accounts.map((account) => {
        // A janela mais longa por fora (#278); estável entre janelas iguais.
        const rank = { monthly: 3, weekly: 2, interval: 1 };
        const rings = account.quotas.slice(0, 3).map((q, i) => ({ q, i }))
          .sort((a, b) => (rank[b.q.period] || 0) - (rank[a.q.period] || 0) || a.i - b.i)
          .map(({ q }) => q);
        return (
          <div key={account.label} style={{ display: 'flex', flexDirection: horizontal ? 'row' : 'column', alignItems: 'center', gap: horizontal ? 6 : 0 }}>
            <AppUsageRing
              arcs={rings.map((q) => ({ fraction: q.fraction, level: q.level, forecast: q.forecast }))}
              active={account.active}
              label={`${account.label} · ${account.statusLabel}`}
            />
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: horizontal ? 'flex-start' : 'center' }}>
              {rings.length <= 1 ? (
                <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', color: 'var(--fg)' }}>{rings[0] && rings[0].percent}</span>
              ) : rings.map((q) => (
                <span key={q.short} style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: '.07em', color: 'var(--fg)' }}>
                  <span style={{ color: 'var(--muted)' }}>{q.short}</span> {q.percent}
                </span>
              ))}
              <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: '.07em', color: LEVELS[account.level] || LEVELS.off, textAlign: 'center', maxWidth: horizontal ? 'none' : 56 }}>{account.statusLabel}</span>
            </div>
          </div>
        );
      })}
      {countdown ? (
        <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>↻ {countdown}</span>
      ) : null}
    </div>
  );

  // Alças: mão na ponta de perto (move), engrenagem na de longe (ações do app).
  // A atualização pendente (#291) é um ponto no canto da engrenagem, não um ícone
  // na faixa; a frase vai no rótulo, porque cor nunca informa sozinha.
  const handle = (glyph, title, dot) => (
    <span title={title} style={{
      position: 'relative',
      width: 32, height: 32, borderRadius: '50%', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--surface)', border: '1px solid var(--border-top)', boxShadow: 'var(--shadow-raised)',
      color: 'var(--muted)', fontSize: 14
    }}>{glyph}{dot ? (
      <span aria-hidden="true" style={{ position: 'absolute', top: 0, right: 0, width: 8, height: 8, borderRadius: '50%', background: 'var(--ok)', boxShadow: '0 0 0 1.5px var(--surface)' }} />
    ) : null}</span>
  );

  const account = typeof balloon === 'number' ? accounts[balloon] : null;
  const body = !open ? null : (
    <div style={{
      width: 240, padding: 12, borderRadius: 'var(--r4)', background: 'linear-gradient(var(--sheen), transparent 56px), var(--surface)',
      border: '1px solid var(--border-top)', boxShadow: 'var(--shadow-overlay)', display: 'flex', flexDirection: 'column', gap: 10
    }}>
      {account ? (
        <>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, height: 24 }}>
            <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t14)', fontWeight: 600, flex: 1 }}>{account.label}</span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: LEVELS[account.level] }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'currentColor' }} />{account.statusLabel}
            </span>
          </div>
          {account.quotas.map((q) => (
            <div key={q.short}>
              <div style={{ display: 'flex', fontFamily: 'var(--mono)', fontSize: 'var(--t12)' }}>
                <span style={{ flex: 1, fontWeight: 600 }}>{q.title || q.short}</span>
                {q.reset ? <span style={{ fontSize: 'var(--t10)', color: 'var(--muted)' }}>Reinicia {q.reset}</span> : null}
              </div>
              <AppProgressTrack percent={q.fraction * 100} level={q.level === 'off' ? 'neutral' : q.level} style={{ margin: '4px 0' }} />
              <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>{q.usedLeft || q.percent}</span>
            </div>
          ))}
          {account.detail ? <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>{account.detail}</span> : null}
          <div style={{ display: 'flex', gap: 6 }}>
            {[...actions, '↻'].map((glyph) => (
              <span key={glyph} style={{ width: 28, height: 28, borderRadius: 'var(--r2)', border: '1px solid var(--border)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: 'var(--muted)' }}>{glyph}</span>
            ))}
          </div>
        </>
      ) : (
        <>
          <div style={{ display: 'flex', alignItems: 'center', height: 24 }}>
            <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t14)', fontWeight: 600, flex: 1 }}>Usage Monitor</span>
            {countdown ? <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>↻ {countdown}</span> : null}
          </div>
          <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>Modo de janela</span>
          {['Padrão', 'Somente os cards', 'Barra HUD'].map((mode) => (
            <span key={mode} style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', color: mode === 'Barra HUD' ? 'var(--fg)' : 'var(--muted)' }}>
              <span style={{ display: 'inline-block', width: 16 }}>{mode === 'Barra HUD' ? '✓' : ''}</span>{mode}
            </span>
          ))}
          <div style={{ display: 'flex', gap: 6, color: 'var(--muted)' }}>⤓ ↻ ⚙ ?</div>
          {update ? (
            <>
              <AppBanner level="ok" title={updateHeadline || update}>{updateDetail}</AppBanner>
              {updateAction ? (
                <div><AppButton>{updateAction}</AppButton></div>
              ) : null}
            </>
          ) : null}
        </>
      )}
    </div>
  );

  const row = horizontal ? 'row' : 'column';
  const strip = (
    <div style={{ display: 'flex', flexDirection: row, alignItems: 'center', gap: 6 }}>
      {open ? handle('✋', 'Mover a barra HUD') : null}
      {notch}
      {open ? handle('⚙', update ? `Ações do Usage Monitor · ${update}` : 'Ações do Usage Monitor', Boolean(update)) : null}
    </div>
  );
  const outer = edge === 'bottom' || edge === 'right' ? [body, strip] : [strip, body];
  return (
    <div style={{
      display: 'inline-flex', flexDirection: horizontal ? 'column' : 'row', alignItems: 'center', gap: 10,
      transition: 'all var(--spring-expressive)', ...style
    }}>
      {outer[0]}{outer[1]}
    </div>
  );
}
