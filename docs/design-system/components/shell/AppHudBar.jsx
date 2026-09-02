import React from 'react';
import { AppStatusIndicator } from '../data/AppStatusIndicator.jsx';
import { AppStatusDot } from '../data/AppStatusDot.jsx';

export function AppHudBar({
  level = 'ok',
  sources = [],
  fallbackLabel = 'Carregando',
  dotOnly = false,
  expanded = false,
  countdown,
  countdownLabel = 'Próxima atualização automática',
  onOpen,
  style
}) {
  // Parada mostra a primeira conta; com o ponteiro em cima, todas.
  const visible = expanded ? sources : sources.slice(0, 1);

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label="Abrir Usage Monitor"
      onClick={onOpen}
      style={{
        display: 'flex',
        flexDirection: 'column',
        maxWidth: 484,
        border: '1px solid var(--border)',
        borderRadius: 'var(--r2)',
        boxShadow: 'var(--shadow-8)',
        background: 'var(--surface)',
        overflow: 'hidden',
        cursor: 'default',
        ...style
      }}
    >
      {dotOnly ? (
        // Recolhida ao ponto: o padding da linha com texto faria um ponto de
        // 6px virar uma janela de 38px.
        <div style={{ display: 'flex', alignItems: 'center', height: 'var(--h-hud)', padding: '0 var(--s2)' }}>
          <AppStatusDot level={level} />
        </div>
      ) : (
        <div style={{ padding: 'var(--s1) 0' }}>
          {visible.length === 0 ? (
            <HudRow>
              {/* O flex mora no indicador, não num spacer: a linha espaça os
                  filhos, e um terceiro filho traria um vão que a medida da
                  janela não conta. */}
              <AppStatusIndicator level="off" style={{ flex: 1, minWidth: 0 }}>{fallbackLabel}</AppStatusIndicator>
              {countdown ? <HudCountdown label={countdownLabel}>{countdown}</HudCountdown> : null}
            </HudRow>
          ) : visible.map((source, index) => (
            <HudRow key={source.label}>
              <AppStatusIndicator level={source.level}>{source.statusLabel}</AppStatusIndicator>
              <span style={NAME}>{source.label}</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)' }}>
                {(source.quotas || []).map((chip) => (
                  <span key={chip.text} style={{ display: 'flex', alignItems: 'center', gap: 'var(--s1)' }}>
                    <AppStatusDot level={chip.level} />
                    <span style={VALUE}>{chip.text}</span>
                    {/* A hora do reinício, só no painel expandido: a pílula
                        parada é a que fica na tela capturando clique de quem
                        está atrás. Tom secundário e sem separador -- o vizinho
                        é o percentual, que é consumo, e é o tom que os separa.
                        Cota sem reset a mostrar não imprime nada no lugar. */}
                    {expanded && chip.reset ? <span style={RESET}>{chip.reset}</span> : null}
                  </span>
                ))}
              </span>
              {/* Uma vez só, na primeira linha: o polling é do app inteiro, e
                  uma contagem por linha diria que cada conta tem coleta
                  própria. */}
              {index === 0 && countdown ? <HudCountdown label={countdownLabel}>{countdown}</HudCountdown> : null}
            </HudRow>
          ))}
        </div>
      )}
    </div>
  );
}

const NAME = { flex: 1, minWidth: 0, fontFamily: 'var(--mono)', fontSize: 'var(--t12)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' };
const VALUE = { flex: 'none', fontFamily: 'var(--mono)', fontSize: 'var(--t12)' };
const RESET = { ...VALUE, color: 'var(--muted)' };

// A contagem até a próxima coleta. O ícone é o que diz de que tempo se trata:
// aqui não cabe tooltip -- popup nesta plataforma é camada dentro da janela e
// sai recortado sobre o próprio alvo --, e um `02:05` solto ao lado dos
// percentuais não se explica. A frase por extenso vai no rótulo acessível.
function HudCountdown({ label, children }) {
  return (
    <span
      title={label}
      aria-label={label}
      style={{ display: 'flex', alignItems: 'center', gap: 'var(--s1)', flex: 'none', color: 'var(--muted)' }}
    >
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
        <path d="M20 12a8 8 0 1 1-2.34-5.66" />
        <path d="M20 4v5h-5" />
      </svg>
      <span style={VALUE}>{children}</span>
    </span>
  );
}

// 20px por linha, e não AppDataRow: aquela primitiva floora em 32px mais
// padding, e seis cotas dariam ~288px de painel -- uma janela, não um HUD.
function HudRow({ children }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s3)', height: 20, padding: '0 var(--s3)' }}>
      {children}
    </div>
  );
}
