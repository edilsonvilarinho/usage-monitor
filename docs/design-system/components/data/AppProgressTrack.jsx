import React from 'react';

const LEVELS = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', neutral: 'var(--fg)' };

export function AppProgressTrack({ percent = 0, level = 'neutral', color, label, marker, style }) {
  const p = Math.max(0, Math.min(100, percent));
  const m = marker == null ? null : Math.max(0, Math.min(100, marker));
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
        position: 'relative',
        ...style
      }}
    >
      <span style={{ display: 'block', height: '100%', width: p + '%', background: color || LEVELS[level] || LEVELS.neutral, transition: 'width var(--spring-gentle), background var(--dur-select) var(--ease)' }} />
      {m != null && (
        <span aria-hidden="true" style={{ position: 'absolute', top: -1, bottom: -1, left: `calc(${m}% - 1px)`, width: 2, background: 'var(--fg)' }} />
      )}
    </div>
  );
}
