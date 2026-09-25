import React from 'react';

const LEVELS = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', off: 'var(--muted)' };

// O notch da HUD (Compose: `HudNotch`). Colado numa borda da tela: reto e rente
// nela, cantos redondos do lado de dentro e ombros côncavos ligando os dois.
// O notch não cresce: por conta, anel + percentual em foco + palavra. Com o
// ponteiro em cima aparecem as alças (mão e engrenagem) e, ao lado, o balão de
// UMA conta — a do anel sob o ponteiro — ou o da engrenagem.
export function AppHudBar({
  accounts = [], edge = 'top', balloon, fallbackLabel = 'Carregando', countdown, update, updateAction,
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
        const focus = account.quotas[account.focus || 0] || account.quotas[0];
        return (
          <div key={account.label} style={{ display: 'flex', flexDirection: horizontal ? 'row' : 'column', alignItems: 'center', gap: horizontal ? 6 : 0 }}>
            <AppUsageRing
              arcs={account.quotas.map((q) => ({ fraction: q.fraction, level: q.level, forecast: q.forecast }))}
              active={account.active}
              label={`${account.label} · ${account.statusLabel}`}
            />
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: horizontal ? 'flex-start' : 'center' }}>
              <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', color: 'var(--fg)' }}>{focus && focus.percent}</span>
              <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: '.07em', color: LEVELS[account.level] || LEVELS.off, textAlign: 'center', maxWidth: horizontal ? 'none' : 56 }}>{account.statusLabel}</span>
            </div>
          </div>
        );
      })}
      {update ? <span title={update} style={{ color: 'var(--ok)', fontSize: 12 }}>⤓</span> : null}
      {countdown ? (
        <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>↻ {countdown}</span>
      ) : null}
    </div>
  );

  // Alças: mão na ponta de perto (move), engrenagem na de longe (ações do app).
  const handle = (glyph, title) => (
    <span title={title} style={{
      width: 32, height: 32, borderRadius: '50%', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--surface)', border: '1px solid var(--border-top)', boxShadow: 'var(--shadow-raised)',
      color: 'var(--muted)', fontSize: 14
    }}>{glyph}</span>
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
              <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--ok)', lineHeight: '14px', maxHeight: 28, overflow: 'hidden' }}>{update}</span>
              {updateAction ? (
                <span role="button" style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', color: 'var(--ok)', height: 24, display: 'inline-flex', alignItems: 'center', padding: '0 4px', borderRadius: 'var(--r2)', cursor: 'pointer' }}>{updateAction} →</span>
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
      {open ? handle('⚙', 'Ações do Usage Monitor') : null}
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
