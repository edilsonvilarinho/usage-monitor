import React from 'react';

const SOURCES = {
  anthropic: 'var(--anthropic)',
  codex: 'var(--codex)',
  deepseek: 'var(--deepseek)',
  minimax: 'var(--minimax)',
  opencode: 'var(--oc)',
  kilo: 'var(--kilo)',
  openrouter: 'var(--openrouter)',
  neutral: 'var(--muted)'
};

export function AppSourceMark({ source = 'neutral', color, style }) {
  return (
    <span
      aria-hidden="true"
      style={{
        width: 'var(--mark-w)',
        alignSelf: 'stretch',
        minHeight: 14,
        borderRadius: 1,
        flex: 'none',
        background: color || SOURCES[source] || SOURCES.neutral,
        ...style
      }}
    />
  );
}

export function AppSourceDot({ source = 'neutral', color, size = 8, style }) {
  return (
    <span
      aria-hidden="true"
      style={{
        width: size,
        height: size,
        borderRadius: 2,
        flex: 'none',
        display: 'inline-block',
        background: color || SOURCES[source] || SOURCES.neutral,
        ...style
      }}
    />
  );
}
