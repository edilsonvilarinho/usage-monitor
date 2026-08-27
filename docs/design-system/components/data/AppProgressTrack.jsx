import React from 'react';

const LEVELS = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', neutral: 'var(--fg)' };

export function AppProgressTrack({ percent = 0, level = 'neutral', color, label, style }) {
  const p = Math.max(0, Math.min(100, percent));
  return (
    <div
      role="progressbar"
      aria-valuenow={Math.round(p)}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={label}
      style={{
        height: 'var(--track-h)',
        borderRadius: 2,
        background: 'var(--raised)',
        border: '1px solid var(--border)',
        overflow: 'hidden',
        ...style
      }}
    >
      <span style={{ display: 'block', height: '100%', width: p + '%', background: color || LEVELS[level] || LEVELS.neutral }} />
    </div>
  );
}
