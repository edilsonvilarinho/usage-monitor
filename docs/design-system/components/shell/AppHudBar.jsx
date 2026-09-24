import React from 'react';

const LEVELS = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', off: 'var(--muted)' };

// O notch da HUD (Compose: `HudNotch`). Colado numa borda da tela: reto e rente
// nela, cantos redondos do lado de dentro e ombros côncavos ligando os dois.
// Parado: por conta, anel + percentual em foco + palavra. Aberto: um bloco por
// conta com uma linha por cota (rótulo, barra, percentual, reset).
export function AppHudBar({ accounts = [], edge = 'top', expanded = false, fallbackLabel = 'Carregando', countdown, update, style }) {
  const horizontal = edge === 'top' || edge === 'bottom';
  const shoulder = 8;
  const radius = 14;
  const flat = {
    top: { borderTop: 'none', borderRadius: `0 0 ${radius}px ${radius}px` },
    bottom: { borderBottom: 'none', borderRadius: `${radius}px ${radius}px 0 0` },
    left: { borderLeft: 'none', borderRadius: `0 ${radius}px ${radius}px 0` },
    right: { borderRight: 'none', borderRadius: `${radius}px 0 0 ${radius}px` }
  }[edge];

  const strip = (
    <div style={{
      display: 'flex', flexDirection: horizontal ? 'row' : 'column', alignItems: 'center',
      justifyContent: 'center', gap: 12, padding: horizontal ? `8px ${12 + shoulder}px` : `${12 + shoulder}px 8px`
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

  const panel = expanded && accounts.length > 0 ? (
    <div style={{ width: 320, padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
      {accounts.map((account) => (
        <div key={account.label}>
          <div style={{ height: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: '.07em', color: LEVELS[account.level] }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'currentColor' }} />{account.statusLabel}
            </span>
            <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)' }}>{account.label}</span>
          </div>
          {account.quotas.map((q) => (
            <div key={q.short} style={{ height: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ width: 48, fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>{q.short}</span>
              <AppProgressTrack percent={q.fraction * 100} level={q.level === 'off' ? 'neutral' : q.level} style={{ flex: 1 }} />
              <span style={{ width: 30, fontFamily: 'var(--mono)', fontSize: 'var(--t12)' }}>{q.percent}</span>
              <span style={{ width: 64, fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>{q.reset || ''}</span>
            </div>
          ))}
        </div>
      ))}
    </div>
  ) : null;

  const inner = edge === 'bottom' || edge === 'right' ? [panel, strip] : [strip, panel];
  return (
    <div style={{
      display: 'inline-flex', flexDirection: horizontal ? 'column' : 'row', alignItems: 'center',
      background: 'linear-gradient(var(--sheen), transparent 56px), var(--surface)',
      border: '1px solid var(--border-top)', ...flat,
      boxShadow: 'inset 0 1px 0 var(--highlight), var(--shadow-dialog)',
      transition: 'all var(--spring-expressive)',
      ...style
    }}>
      {inner[0]}{inner[1]}
    </div>
  );
}
