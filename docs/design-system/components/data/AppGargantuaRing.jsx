import React from 'react';
import { AppProviderMark } from '../core/AppProviderMark';

const TONES = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', off: 'var(--muted)' };
const LIGHT = 'var(--gargantua-hot)';

// Meia elipse do disco: frente passa por baixo do horizonte, trás por cima.
function halfEllipse(cx, cy, rx, ry, front) {
  return `M${cx - rx} ${cy} A${rx} ${ry} 0 0 ${front ? 0 : 1} ${cx + rx} ${cy}`;
}

// O cenário é discreto; a cota é plasma num tubo de vidro e seu comprimento nunca anima.
export function AppGargantuaRing({
  arcs = [], size = 64, stroke = 2.5, gap = 1.5, provider, providerColor,
  active = false, refreshing = false, attentionIndex = -1,
  continuous = false, reduced = false, label, style
}) {
  const uid = React.useId().replace(/:/g, '');
  const rings = arcs.slice(0, 3);
  const moving = continuous && !reduced;
  const room = 32 - Math.max(1, rings.length) * (stroke + gap);
  const horizon = room * .62;
  const diskY = 32 + room * .08;
  const rx = room * .95;
  const duration = refreshing ? 'var(--dur-gargantua-refresh)' : 'var(--dur-gargantua-orbit)';
  const filaments = moving ? { animation: `gargantua-filaments ${duration} linear infinite` } : {};
  const orbit = moving ? { animation: 'gargantua-comet var(--dur-gargantua-active) linear infinite', transformOrigin: '32px 32px' } : {};
  const disk = (front) => <g transform="rotate(-12 32 32)">
    <path d={halfEllipse(32, diskY, rx, rx * .16, front)} fill="none" stroke={`url(#${uid}-disc)`} strokeWidth={room * .07} />
    <path className="gargantua-motion" d={halfEllipse(32, diskY, rx, rx * .16, front)} fill="none" stroke="#FFF8E6" strokeOpacity=".45"
      strokeWidth={room * .035} strokeLinecap="round" strokeDasharray="4 30" style={filaments} />
  </g>;
  const arc = (a1, a2, r) => {
    const p = (a) => [32 + r * Math.cos(a * Math.PI / 180), 32 + r * Math.sin(a * Math.PI / 180)];
    const [x1, y1] = p(a1); const [x2, y2] = p(a2);
    return `M${x1} ${y1} A${r} ${r} 0 ${a2 - a1 > 180 ? 1 : 0} 1 ${x2} ${y2}`;
  };
  return (
    <span role="img" aria-label={label} style={{ position: 'relative', width: size, height: size, display: 'inline-flex', flex: 'none', ...style }}>
      <style>{`
        @keyframes gargantua-filaments { to { stroke-dashoffset: -136; } }
        @keyframes gargantua-comet { to { transform: rotate(360deg); } }
        @keyframes gargantua-attention { 50% { opacity: .5; } }
        @keyframes gargantua-flow { from { stroke-dashoffset: 0; } to { stroke-dashoffset: var(--flow-to); } }
        @media (prefers-reduced-motion: reduce) { .gargantua-motion { animation: none !important; } }
      `}</style>
      <svg width={size} height={size} viewBox="0 0 64 64" overflow="visible" aria-hidden="true" style={{ overflow: 'visible' }}>
        <defs>
          <radialGradient id={`${uid}-well`}>
            <stop stopColor="var(--gargantua-gold)" stopOpacity=".16" /><stop offset=".56" stopColor="var(--gargantua-gold)" stopOpacity=".16" />
            <stop offset="1" stopColor="var(--gargantua-gold)" stopOpacity="0" />
          </radialGradient>
          {/* Doppler: o lado que se aproxima é mais claro que o que se afasta. */}
          <linearGradient id={`${uid}-disc`} x1="0" y1="0" x2="1" y2="0">
            <stop stopColor="var(--gargantua-ember)" stopOpacity=".25" /><stop offset=".5" stopColor="var(--gargantua-gold)" stopOpacity=".55" />
            <stop offset="1" stopColor="var(--gargantua-hot)" stopOpacity=".8" />
          </linearGradient>
          {/* Reflexo do vidro: luz do alto à esquerda. */}
          <linearGradient id={`${uid}-glass`} x1="0" y1="0" x2="1" y2="1">
            <stop stopColor="#fff" stopOpacity=".3" /><stop offset=".5" stopColor="#fff" stopOpacity=".05" /><stop offset="1" stopColor="#fff" stopOpacity="0" />
          </linearGradient>
        </defs>
        <circle cx="32" cy="32" r={room} fill={`url(#${uid}-well)`} />
        <path d={arc(194, 346, horizon + room * .1)} fill="none" stroke="#FFE6BE" strokeOpacity=".35" strokeWidth={room * .05} />
        <path d={arc(27, 153, horizon + room * .08)} fill="none" stroke="#FFE6BE" strokeOpacity=".18" strokeWidth={room * .025} />
        {disk(false)}
        <circle cx="32" cy="32" r={horizon} fill="var(--gargantua-core)" />
        <circle cx="32" cy="32" r={horizon + room * .01} fill="none" stroke={LIGHT} strokeOpacity=".8" strokeWidth={room * .022} />
        {disk(true)}
        {rings.map((ring, index) => {
          const radius = 32 - stroke / 2 - index * (stroke + gap);
          const circumference = 2 * Math.PI * radius;
          const fraction = Math.max(0, Math.min(1, ring.fraction || 0));
          const tone = TONES[ring.level] || TONES.off;
          const length = circumference * fraction;
          const dash = `${length} ${circumference}`;
          return <g key={index}>
            <circle cx="32" cy="32" r={radius} fill="none" stroke="#fff" strokeOpacity=".06" strokeWidth={stroke * 1.4} strokeDasharray={ring.forecast === false ? `${stroke} ${stroke * 1.4}` : undefined} />
            <circle cx="32" cy="32" r={radius - stroke / 2} fill="none" stroke={`url(#${uid}-glass)`} strokeWidth={stroke * .18} />
            <circle cx="32" cy="32" r={radius + stroke / 2} fill="none" stroke={`url(#${uid}-glass)`} strokeOpacity=".4" strokeWidth={stroke * .14} />
            {fraction > 0 ? <g transform="rotate(-90 32 32)">
              <mask id={`${uid}-arc${index}`} maskUnits="userSpaceOnUse">
                <circle cx="32" cy="32" r={radius} fill="none" stroke="#fff" strokeWidth={stroke * 2} strokeDasharray={dash} />
              </mask>
              <circle className="gargantua-motion" cx="32" cy="32" r={radius} fill="none" stroke={tone} strokeOpacity=".25" strokeWidth={stroke * 2.4} strokeLinecap="round" strokeDasharray={dash} style={moving && index === attentionIndex ? { animation: 'gargantua-attention var(--dur-gargantua-attention) ease-in-out infinite' } : {}} />
              <circle cx="32" cy="32" r={radius} fill="none" stroke={tone} strokeWidth={stroke * .9} strokeLinecap="round" strokeDasharray={dash} style={reduced ? {} : { transition: 'stroke-dasharray var(--spring-gentle)' }} />
              <circle cx="32" cy="32" r={radius} fill="none" stroke={`color-mix(in srgb, ${tone} 40%, white)`} strokeWidth={stroke * .35} strokeLinecap="round" strokeDasharray={dash} />
              {moving ? <circle className="gargantua-motion" mask={`url(#${uid}-arc${index})`} cx="32" cy="32" r={radius} fill="none" stroke="#fff" strokeOpacity=".8"
                strokeWidth={stroke * .8} strokeLinecap="round" strokeDasharray={`.1 ${length / 3}`}
                style={{ '--flow-to': -length / 3, animation: 'gargantua-flow calc(var(--dur-gargantua-flow) / 3) linear infinite' }} /> : null}
            </g> : null}
            {fraction > 0 ? <>
              <mask id={`${uid}-glass${index}`} maskUnits="userSpaceOnUse">
                <circle cx="32" cy="32" r={radius} fill="none" stroke="#fff" strokeWidth={stroke} strokeDasharray={dash} transform="rotate(-90 32 32)" />
              </mask>
              <circle mask={`url(#${uid}-glass${index})`} cx="32" cy="32" r={radius - stroke * .3} fill="none" stroke={`url(#${uid}-glass)`} strokeWidth={stroke * .16} />
            </> : null}
          </g>;
        })}
        {active ? <>
          <circle cx="32" cy="32" r="34.5" fill="none" stroke="var(--info)" strokeOpacity=".14" strokeWidth="1" />
          <g className="gargantua-motion" style={orbit}>
            <path d="M32 -2.5 A34.5 34.5 0 0 1 61.88 49.25" fill="none" stroke="var(--info)" strokeOpacity=".3" strokeWidth="2" />
            <path d="M66.5 32 A34.5 34.5 0 0 1 61.88 49.25" fill="none" stroke="var(--info)" strokeWidth="2" />
            <circle cx="61.88" cy="49.25" r="2.8" fill="var(--info)" fillOpacity=".2" /><circle cx="61.88" cy="49.25" r="1.5" fill="var(--info)" />
          </g>
        </> : null}
      </svg>
      <span aria-hidden="true" style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <AppProviderMark source={provider} size={Math.min(21, room * .88) * size / 64} color={providerColor || 'var(--gargantua-hot)'} />
      </span>
    </span>
  );
}
