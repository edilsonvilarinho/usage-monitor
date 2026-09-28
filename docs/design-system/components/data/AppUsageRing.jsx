import React from 'react';

const LEVELS = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', off: 'var(--muted)' };

// Anel de uso: um arco por cota, concêntricos — o de fora é a janela mais longa
// (#278). Cota sem projeção tem a trilha tracejada. `active` desenha o cometa de
// sessão ativa em órbita por fora, sobre uma pista fina, sem roubar o miolo.
// Profundidade estática: brilho do tom sob cada arco, ponta acesa com um
// ponto de luz no fim do valor e um poço radial no miolo. O kit é estático: no
// Compose o cometa gira e o arco em foco respira só com a política contínua.
export function AppUsageRing({ arcs = [], size = 28, stroke = 3, gap = 1.5, active = false, label, style }) {
  const rings = arcs.slice(0, 3);
  const mid = size / 2;
  const coreR = size / 2 - Math.max(1, rings.length) * (stroke + gap);
  const wellId = React.useId ? React.useId() : 'ring-well';
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={label} overflow="visible" style={{ flex: 'none', overflow: 'visible', ...style }}>
      <defs>
        <radialGradient id={wellId}>
          <stop offset="0" stopColor="var(--pressed-layer)" />
          <stop offset="1" stopColor="var(--pressed-layer)" stopOpacity="0" />
        </radialGradient>
      </defs>
      {coreR > 0 ? <circle cx={mid} cy={mid} r={coreR} fill={`url(#${wellId})`} /> : null}
      {rings.map((arc, index) => {
        const r = size / 2 - stroke / 2 - index * (stroke + gap);
        if (r <= 0) return null;
        const c = 2 * Math.PI * r;
        const p = Math.max(0, Math.min(1, arc.fraction || 0));
        const tone = LEVELS[arc.level] || LEVELS.off;
        // Ponta: arco cheio não tem fim, então não ganha ponto.
        const tipAngle = (-90 + 360 * p) * Math.PI / 180;
        const hasTip = p > 4 / 360 && p < 359 / 360;
        return (
          <g key={index}>
            <g transform={`rotate(-90 ${mid} ${mid})`}>
              <circle cx={mid} cy={mid} r={r} fill="none" stroke="var(--pressed-layer)" strokeWidth={stroke}
                strokeDasharray={arc.forecast === false ? `${stroke} ${stroke * 1.4}` : undefined} />
              <circle cx={mid} cy={mid} r={r} fill="none" stroke={tone} strokeOpacity=".12" strokeWidth={stroke * 2}
                strokeLinecap="round" strokeDasharray={`${c * p} ${c}`} />
              <circle cx={mid} cy={mid} r={r} fill="none" stroke={tone} strokeWidth={stroke}
                strokeLinecap="round" strokeDasharray={`${c * p} ${c}`}
                style={{ transition: 'stroke-dasharray var(--spring-gentle)' }} />
            </g>
            {hasTip ? (
              <circle cx={mid + r * Math.cos(tipAngle)} cy={mid + r * Math.sin(tipAngle)} r={stroke * 0.28} fill="#fff" fillOpacity=".85" />
            ) : null}
          </g>
        );
      })}
      {active ? (() => {
        const orbit = stroke * 0.8;
        const r = size / 2 + gap + orbit / 2;
        const c = 2 * Math.PI * r;
        const head = (-90 + 130) * Math.PI / 180;
        const hx = mid + r * Math.cos(head);
        const hy = mid + r * Math.sin(head);
        return (
          <g>
            <circle cx={mid} cy={mid} r={r} fill="none" stroke="var(--info)" strokeOpacity=".14" strokeWidth={orbit * 0.5} />
            <circle cx={mid} cy={mid} r={r} fill="none" stroke="var(--info)" strokeWidth={orbit}
              strokeDasharray={`${c * 130 / 360} ${c}`} transform={`rotate(-90 ${mid} ${mid})`} />
            <circle cx={hx} cy={hy} r={orbit * 1.4} fill="var(--info)" fillOpacity=".3" />
            <circle cx={hx} cy={hy} r={orbit * 0.75} fill="var(--info)" />
            <circle cx={hx} cy={hy} r={orbit * 0.34} fill="#fff" fillOpacity=".7" />
          </g>
        );
      })() : null}
    </svg>
  );
}
