import React from 'react';

// A bolha de uma tooltip: superfície `--raised`, raio 6, borda de 1dp e
// sombra `--shadow-2`. Existe porque a anatomia estava escrita por extenso
// em quatro lugares do app (tooltip de texto, métricas do card, gráfico de
// turnos, gráfico de histórico) — e as quatro flutuavam sobre o mesmo tipo
// de conteúdo. Só o conteúdo é do chamador: cada bolha tem o próprio
// padding e a própria largura máxima, e é por isso que isto é superfície e
// não contêiner.
export function AppTooltipSurface({ children, style }) {
  return (
    <div
      role="tooltip"
      style={{
        background: 'var(--raised)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r2)',
        boxShadow: 'var(--shadow-2)',
        ...style
      }}
    >
      {children}
    </div>
  );
}
