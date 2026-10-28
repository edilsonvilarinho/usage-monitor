import React from 'react';

const GLYPH = { done: '✓', failed: '×', notReached: '–', unknown: '?' };

function stepColor(status) {
  if (status === 'failed') return 'var(--crit)';
  if (status === 'done') return 'var(--fg)';
  return 'var(--muted)';
}

/* Compact: the steps on one wrapping line, "✓ Address › × Certificate". */
export function AppStepTrail({ steps, style }) {
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'center', gap: 'var(--s1)', fontFamily: 'var(--mono)', fontSize: 'var(--t11)', ...style }}>
      {steps.map((step, index) => (
        <React.Fragment key={step.label}>
          {index > 0 ? <span style={{ color: 'var(--muted)' }}>›</span> : null}
          <span style={{ color: stepColor(step.status), border: `1px solid ${step.status === 'failed' ? 'var(--crit)' : 'var(--border)'}`, borderRadius: 4, padding: '2px 8px', whiteSpace: 'nowrap' }}>
            {GLYPH[step.status]} {step.label}
          </span>
        </React.Fragment>
      ))}
    </div>
  );
}

/* Full: one step per line, glyph · name · what happened there. */
export function AppStepList({ steps, style }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2, fontFamily: 'var(--mono)', fontSize: 'var(--t11)', ...style }}>
      {steps.map((step) => (
        <div key={step.label} style={{ display: 'grid', gridTemplateColumns: '16px 112px 1fr', alignItems: 'baseline' }}>
          <span style={{ color: stepColor(step.status) }}>{GLYPH[step.status]}</span>
          <span style={{ color: 'var(--muted)' }}>{step.label}</span>
          <span style={{ color: stepColor(step.status) }}>{step.detail}</span>
        </div>
      ))}
    </div>
  );
}
