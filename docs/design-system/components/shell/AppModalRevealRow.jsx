import React from 'react';

// E9 · filamentos de plasma. Uma linha de modal revelada da esquerda para a
// direita atrás da cabeça de um filamento quente; o dado não se move, só é
// recortado. `index`/`count` dão a cascata em ordem de leitura, como no
// Compose (`appModalRevealRow`). Com prefers-reduced-motion, corte seco.
const KEYFRAMES = `
@keyframes app-modal-row { from { clip-path: inset(-3px 100% -3px -3px) } to { clip-path: inset(-3px 0 -3px -3px) } }
@keyframes app-modal-filament { 0% { width: 0; opacity: 1 } 85% { width: 100%; opacity: 1 } 100% { width: 100%; opacity: 0 } }
@media (prefers-reduced-motion: reduce) { .app-modal-row, .app-modal-row > i { animation: none !important } .app-modal-row > i { display: none } }
`;

const OPEN_MILLIS = 680;

export function AppModalRevealRow({ index = 0, count = 1, children, style }) {
  const delay = Math.round(OPEN_MILLIS * (0.08 + (index * 0.5) / Math.max(1, count)));
  const span = Math.round(OPEN_MILLIS * 0.35);
  return (
    <div
      className="app-modal-row"
      style={{ position: 'relative', animation: `app-modal-row ${span}ms cubic-bezier(.33,1,.68,1) ${delay}ms both`, ...style }}
    >
      <style>{KEYFRAMES}</style>
      {children}
      <i
        aria-hidden="true"
        style={{
          position: 'absolute',
          left: 0,
          bottom: 0,
          height: 1.2,
          background: 'linear-gradient(90deg, rgb(180 80 30 / 0), rgb(232 174 99 / .6) 70%, #FFF0CB)',
          boxShadow: '0 0 6px rgb(232 174 99 / .8)',
          animation: `app-modal-filament ${Math.round(OPEN_MILLIS * 0.42)}ms cubic-bezier(.33,1,.68,1) ${delay}ms both`
        }}
      />
    </div>
  );
}
