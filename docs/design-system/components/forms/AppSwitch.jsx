import React from 'react';

export function AppSwitch({ checked = false, onChange, label, hint, disabled = false, reason, style }) {
  const track = (
    <span
      role="switch"
      aria-checked={checked}
      aria-disabled={disabled || undefined}
      style={{
        width: 30,
        height: 17,
        borderRadius: 9,
        border: '1px solid ' + (checked ? 'var(--ok)' : 'var(--border)'),
        background: checked ? 'color-mix(in srgb, var(--ok) 30%, var(--raised))' : 'var(--raised)',
        position: 'relative',
        flex: 'none',
        transition: 'background var(--dur-select) var(--ease)'
      }}
    >
      <span
        style={{
          position: 'absolute',
          top: 2,
          left: checked ? 15 : 2,
          width: 11,
          height: 11,
          borderRadius: '50%',
          background: checked ? 'var(--ok)' : 'var(--muted)',
          transition: 'left var(--dur-select) var(--ease)'
        }}
      />
    </span>
  );
  if (!label) return track;
  return (
    <div
      onClick={disabled ? undefined : () => onChange && onChange(!checked)}
      title={disabled ? reason : undefined}
      style={{ display: 'flex', alignItems: 'flex-start', gap: 'var(--s2)', cursor: 'default', opacity: disabled ? .42 : 1, minWidth: 0, ...style }}
    >
      {track}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
        <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)' }}>{label}</span>
        {(disabled && reason) || hint ? (
          <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)' }}>{disabled && reason ? reason : hint}</span>
        ) : null}
      </div>
    </div>
  );
}
