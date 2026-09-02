import React from 'react';

const LEVELS = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', off: 'var(--muted)' };

export function AppStatusDot({ level = 'ok', title, style }) {
  const color = LEVELS[level] || LEVELS.ok;
  return (
    <span
      title={title}
      style={{
        width: 6,
        height: 6,
        borderRadius: '50%',
        flex: 'none',
        display: 'inline-block',
        // `off` sai vazado: sem cor para distinguir, o contorno é o que separa
        // "desconectado" de "conectado" numa captura em tons de cinza.
        background: level === 'off' ? 'transparent' : color,
        border: level === 'off' ? `1px solid ${color}` : 'none',
        ...style
      }}
    />
  );
}
