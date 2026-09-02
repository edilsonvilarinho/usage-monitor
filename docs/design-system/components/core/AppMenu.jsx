import React from 'react';

// Menu suspenso ancorado num controle. Abre para cima quando não cabe abaixo:
// o consumidor de hoje mora na barra de estado, que é a última linha da janela.
export function AppMenu({
  open = false,
  options = [],
  value,
  onSelect,
  onDismiss,
  placement = 'top',
  children,
  style
}) {
  const anchored = placement === 'top'
    ? { bottom: 'calc(100% + var(--s1))', right: 0 }
    : { top: 'calc(100% + var(--s1))', right: 0 };

  return (
    <div style={{ position: 'relative', display: 'inline-flex', ...style }}>
      {children}
      {open ? (
        <>
          {/* Clique fora fecha: é o `focusable` do popup no app. */}
          <div onClick={onDismiss} style={{ position: 'fixed', inset: 0 }} />
          <div
            role="menu"
            style={{
              position: 'absolute',
              ...anchored,
              minWidth: 'max-content',
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--r2)',
              boxShadow: 'var(--shadow-2)',
              overflow: 'hidden',
              zIndex: 2
            }}
          >
            {options.map((option) => {
              const id = typeof option === 'string' ? option : option.id;
              const label = typeof option === 'string' ? option : option.label;
              const on = id === value;
              return (
                <button
                  key={id}
                  type="button"
                  role="menuitemradio"
                  aria-checked={on}
                  onClick={() => onSelect && onSelect(id)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--s2)',
                    width: '100%',
                    minHeight: 'var(--h-control)',
                    padding: 'var(--s1) var(--s2)',
                    border: 0,
                    background: on ? 'var(--raised)' : 'transparent',
                    color: on ? 'var(--fg)' : 'var(--muted)',
                    fontFamily: 'var(--mono)',
                    fontSize: 'var(--t12)',
                    whiteSpace: 'nowrap',
                    cursor: 'default'
                  }}
                >
                  {/* A marca ocupa lugar em toda linha: sem isso o rótulo da
                      selecionada andaria para o lado a cada troca de opção. */}
                  <span style={{ width: 12, textAlign: 'center' }}>{on ? '✓' : ''}</span>
                  <span>{label}</span>
                </button>
              );
            })}
          </div>
        </>
      ) : null}
    </div>
  );
}
