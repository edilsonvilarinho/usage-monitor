import React from 'react';
import { AppGargantuaRing } from '../data/AppGargantuaRing';
import { AppDataRow } from '../data/AppDataRow';
import { AppColumnHeader } from '../data/AppColumnHeader';

// Direção 02 (#379): legendas fixas, duas contagens neutras e só os modelos rolando.
function HudObservedTable({ models }) {
  const tokens = models[0]?.unit === 'tokens';
  const unit = tokens ? 'tokens' : 'requisições';
  const cells = [{ label: 'MODELO', flex: .42 }, { label: <span style={{ whiteSpace: 'pre-line' }}>ÚLTIMAS{'\n'}5H</span>, flex: .29, align: 'right' },
    { label: <span style={{ whiteSpace: 'pre-line' }}>ÚLTIMOS{'\n'}7 DIAS</span>, flex: .29, align: 'right' }];
  return <>
    <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', lineHeight: '17px' }}>{tokens ? 'Tokens observados' : 'Requisições observadas'}</span>
    <div style={{ paddingRight: 'var(--s3)' }}>
      <AppColumnHeader items={cells} offset={0} style={{ padding: 0, gap: 'var(--s2)', height: 29, lineHeight: '14px', alignItems: 'flex-end', borderBottom: '1px solid var(--border)' }} />
    </div>
    <style>{`.hud-observed-models::-webkit-scrollbar { width: var(--s3); }
      .hud-observed-models::-webkit-scrollbar-thumb { background: var(--border); border-radius: var(--r1); }`}</style>
    <div className="hud-observed-models" style={{ maxHeight: 196, overflowY: 'auto', scrollbarGutter: 'stable' }}>
      {models.map((model, index) => {
        const five = model.fiveHours.toLocaleString('pt-BR');
        const seven = model.sevenDays.toLocaleString('pt-BR');
        return <AppDataRow key={model.modelName} last={index === models.length - 1} style={{ height: 49, boxSizing: 'border-box', padding: 'var(--s2) 0', gap: 'var(--s2)', fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--fg)' }}>
          <span title={model.modelName} style={{ flex: .42, minWidth: 0, fontSize: 'var(--t12)', fontWeight: 600, lineHeight: '16px', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', overflowWrap: 'anywhere' }}>{model.modelName}</span>
          <span title={`Últimas 5h: ${five} ${unit}`} style={{ flex: .29, minWidth: 0, textAlign: 'right', fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{five}</span>
          <span title={`Últimos 7 dias: ${seven} ${unit}`} style={{ flex: .29, minWidth: 0, textAlign: 'right', fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{seven}</span>
        </AppDataRow>;
      })}
    </div>
    <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>Contagem local; limite oficial indisponível</span>
  </>;
}

const LEVELS = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', off: 'var(--muted)' };

// O notch da HUD (Compose: `HudNotch`). Colado numa borda da tela: reto e rente
// nela, cantos redondos do lado de dentro e ombros côncavos ligando os dois.
// O notch não cresce: por conta, anel + uma linha por anel com a janela
// ("7d 72%", "5h 45%", #286) + palavra. Com o
// ponteiro em cima aparecem as alças (mão e engrenagem) e, ao lado, o balão de
// UMA conta — a do anel sob o ponteiro — ou o da engrenagem.
// Relógio da contagem (#293): setor que esvazia no sentido horário a partir das 12h.
const clockIcon = (fraction) => (
  <svg width="12" height="12" viewBox="0 0 12 12" style={{ flex: 'none' }} aria-hidden="true">
    <circle cx="6" cy="6" r="5.4" fill="none" stroke="var(--muted)" strokeWidth="1.2" />
    {fraction > 0 ? (
      <path d={fraction >= 1 ? 'M6 2.4 A3.6 3.6 0 1 1 5.99 2.4 Z' : `M6 6 L6 2.4 A3.6 3.6 0 ${fraction > 0.5 ? 1 : 0} 1 ${6 + 3.6 * Math.sin(2 * Math.PI * fraction)} ${6 - 3.6 * Math.cos(2 * Math.PI * fraction)} Z`} fill="var(--muted)" />
    ) : null}
  </svg>
);

export function AppHudBar({
  accounts = [], edge = 'top', balloon, fallbackLabel = 'Carregando', countdown, refreshFraction, version = '38.2.0', update, updateHeadline, updateDetail, updateAction,
  actions = ['⟲', '▣'], continuous = false, reduced = false, autoRetract = false, style
}) {
  const horizontal = edge === 'top' || edge === 'bottom';
  const open = balloon !== undefined && balloon !== null;
  const shoulder = 8;
  const radius = 14;
  const flat = {
    top: { borderTop: 'none', borderRadius: `0 0 ${radius}px ${radius}px` },
    bottom: { borderBottom: 'none', borderRadius: `${radius}px ${radius}px 0 0` },
    left: { borderLeft: 'none', borderRadius: `0 ${radius}px ${radius}px 0` },
    right: { borderRight: 'none', borderRadius: `${radius}px 0 0 ${radius}px` }
  }[edge];

  const notch = (
    <div style={{
      display: 'flex', flexDirection: horizontal ? 'row' : 'column', alignItems: 'center',
      justifyContent: 'center', gap: 12, padding: horizontal ? `8px ${12 + shoulder}px` : `${12 + shoulder}px 8px`,
      // M1 · horizonte de eventos: núcleo escuro e anel de fótons com Doppler (--hud-* em colors.css).
      background: 'var(--hud-body), var(--hud-rim)', backgroundClip: 'padding-box, padding-box, border-box',
      border: '1px solid transparent', ...flat,
      boxShadow: 'var(--hud-inner-glow), var(--shadow-dialog)'
    }}>
      {accounts.length === 0 ? (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>
          <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--muted)' }} />{fallbackLabel}
        </span>
      ) : accounts.map((account) => {
        // A janela mais longa por fora (#278); estável entre janelas iguais.
        const rank = { monthly: 3, weekly: 2, interval: 1 };
        const observed = account.observedModels;
        const observedTotal = observed?.reduce((sum, model) => sum + model.fiveHours, 0);
        const abbreviatedTotal = observedTotal >= 1000000 ? `${(observedTotal / 1000000).toFixed(1).replace(/\.0$/, '')}M` : observedTotal >= 1000 ? `${(observedTotal / 1000).toFixed(1).replace(/\.0$/, '')}K` : observedTotal;
        const observedSummary = observed ? `5h ${abbreviatedTotal} ${observed[0]?.unit === 'tokens' ? 'tok' : 'req.'}` : null;
        const rings = (observed ? [] : account.quotas).slice(0, 3).map((q, i) => ({ q, i }))
          .sort((a, b) => (rank[b.q.period] || 0) - (rank[a.q.period] || 0) || a.i - b.i)
          .map(({ q }) => q);
        return (
          <div key={account.label} style={{ display: 'flex', flexDirection: horizontal ? 'row' : 'column', alignItems: 'center', gap: horizontal ? 6 : 0 }}>
            <AppGargantuaRing
              size={64} stroke={2.5} gap={1.5}
              provider={account.provider} providerColor={account.color}
              arcs={rings.map((q) => ({ fraction: q.fraction, level: q.level, forecast: q.forecast }))}
              active={account.active}
              refreshing={account.refreshing} continuous={continuous} reduced={reduced}
              attentionIndex={rings.findIndex((q) => q.level === account.level && ['warn', 'crit'].includes(q.level))}
              label={`${account.label} · ${observedSummary || rings.map((q) => `${q.short} ${q.percent}`).join(' · ')} · ${account.statusLabel}${account.active ? ' · Sessão ativa' : ''}`}
            />
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: horizontal ? 'flex-start' : 'center' }}>
              {observed ? <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--fg)' }}>{observedSummary}</span> : rings.length <= 1 ? (
                <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', color: 'var(--fg)' }}>{rings[0] && rings[0].percent}</span>
              ) : rings.map((q) => (
                <span key={q.short} style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: '.07em', color: 'var(--fg)' }}>
                  <span style={{ color: 'var(--muted)' }}>{q.short}</span> {q.percent}
                </span>
              ))}
              <AppStatusPill level={account.level || 'off'} style={{ whiteSpace: horizontal ? 'nowrap' : 'normal', textAlign: 'center', maxWidth: horizontal ? 'none' : 72 }}>{account.statusLabel}</AppStatusPill>
            </div>
          </div>
        );
      })}
      {/* A contagem não entra na faixa (#269): mora no cabeçalho do balão da engrenagem. */}
    </div>
  );

  // Alças: mão na ponta de perto (move), engrenagem na de longe (ações do app,
  // abertas no hover como o balão do anel — #317).
  // A atualização pendente (#291) é um ponto no canto da engrenagem, não um ícone
  // na faixa; a frase vai no rótulo, porque cor nunca informa sozinha.
  const handle = (glyph, title, dot) => (
    <span title={title} style={{
      position: 'relative',
      width: 32, height: 32, borderRadius: '50%', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--surface)', border: '1px solid var(--border-top)', boxShadow: 'var(--shadow-raised)',
      color: 'var(--muted)', fontSize: 14
    }}>{glyph}{dot ? (
      <span aria-hidden="true" style={{ position: 'absolute', top: 0, right: 0, width: 8, height: 8, borderRadius: '50%', background: 'var(--ok)', boxShadow: '0 0 0 1.5px var(--surface)' }} />
    ) : null}</span>
  );

  const account = typeof balloon === 'number' ? accounts[balloon] : null;
  const body = !open ? null : (
    <div style={{
      width: account?.observedModels ? 264 : 240, padding: 12, borderRadius: 'var(--r4)', background: 'linear-gradient(var(--sheen), transparent 56px), var(--surface)',
      border: '1px solid var(--border-top)', boxShadow: 'var(--shadow-overlay)', display: 'flex', flexDirection: 'column', gap: 10
    }}>
      {account ? (
        <>
          {account.observedModels ? <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--s1)', height: 34, color: 'var(--fg)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, height: 16 }}>
              <AppProviderMark source={account.provider} size={16} color={account.provider === 'gemini' ? 'var(--gemini)' : account.provider === 'kilo' ? 'var(--kilo)' : 'var(--oc)'} />
              <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', fontWeight: 600 }}>{account.label}</span>
            </div>
            <AppStatusIndicator level="off">{account.statusLabel}</AppStatusIndicator>
          </div> : <div style={{ display: 'flex', alignItems: 'center', gap: 6, height: 24 }}>
            <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t14)', fontWeight: 600, flex: 1 }}>{account.label}</span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: LEVELS[account.level] }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'currentColor' }} />{account.statusLabel}
            </span>
          </div>}
          {account.observedModels ? <HudObservedTable models={account.observedModels} /> : account.quotas.map((q) => (
            <div key={q.short}>
              <div style={{ display: 'flex', fontFamily: 'var(--mono)', fontSize: 'var(--t12)' }}>
                <span style={{ flex: 1, fontWeight: 600 }}>{q.title || q.short}</span>
                {q.reset ? <span style={{ fontSize: 'var(--t10)', color: 'var(--muted)' }}>Reinicia {q.reset}</span> : null}
              </div>
              <AppProgressTrack percent={q.fraction * 100} level={q.level === 'off' ? 'neutral' : q.level} style={{ margin: '4px 0' }} />
              <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>{q.usedLeft || q.percent}</span>
            </div>
          ))}
          {account.detail ? <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>{account.detail}</span> : null}
          <div style={{ display: 'flex', gap: 6 }}>
            {[...actions, '↻'].map((glyph) => (
              <span key={glyph} style={{ width: 28, height: 28, borderRadius: 'var(--r2)', border: '1px solid var(--border)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: 'var(--muted)' }}>{glyph}</span>
            ))}
          </div>
        </>
      ) : (
        <>
          <div style={{ display: 'flex', alignItems: 'center', height: 24 }}>
            <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t14)', fontWeight: 600, flex: 1 }}>Usage Monitor</span>
            <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)', whiteSpace: 'nowrap' }}>v{version}</span>
            {countdown ? <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>{refreshFraction !== undefined ? clockIcon(refreshFraction) : '↻'} {countdown}</span> : null}
          </div>
          <div style={{ display: 'flex', gap: 6, color: 'var(--muted)' }}>⤓ ↻ ⚙ ?</div>
          {update ? (
            <>
              <AppBanner level="ok" title={updateHeadline || update}>{updateDetail}</AppBanner>
              {updateAction ? (
                <div><AppButton>{updateAction}</AppButton></div>
              ) : null}
            </>
          ) : null}
        </>
      )}
    </div>
  );

  // Modo recolher (#400): parado, só a faixa de 10dp com um ponto por conta; a
  // forma diz o pior risco junto com a cor. Aberto, a íris (Z2) revela o notch.
  const dotShape = {
    warn: { width: 6, height: 6, background: 'var(--warn)', transform: 'rotate(45deg)' },
    crit: { width: 0, height: 0, borderLeft: '4px solid transparent', borderRight: '4px solid transparent', borderBottom: '7px solid var(--crit)' },
    ok: { width: 6, height: 6, borderRadius: '50%', background: 'var(--ok)' },
    off: { width: 7, height: 7, borderRadius: '50%', border: '1.5px solid var(--muted)', boxSizing: 'border-box' }
  };
  const retracted = autoRetract && !open ? (
    <div role="img" aria-label={['Barra HUD recolhida', ...accounts.map((a) => `${a.label}: ${a.statusLabel}`)].join(' · ')} style={{
      display: 'flex', flexDirection: horizontal ? 'row' : 'column', alignItems: 'center', justifyContent: 'space-around',
      [horizontal ? 'width' : 'height']: Math.max(120, accounts.length * 140), [horizontal ? 'height' : 'width']: 10,
      background: 'var(--hud-body), var(--hud-rim)', backgroundClip: 'padding-box, padding-box, border-box',
      border: '1px solid transparent', ...flat, borderRadius: { top: '0 0 5px 5px', bottom: '5px 5px 0 0', left: '0 5px 5px 0', right: '5px 0 0 5px' }[edge],
      boxShadow: 'var(--hud-inner-glow), var(--shadow-dialog)'
    }}>
      {accounts.map((a) => <span key={a.label} aria-hidden="true" style={{ flex: 'none', ...(dotShape[a.level] || dotShape.off) }} />)}
    </div>
  ) : null;

  const row = horizontal ? 'row' : 'column';
  const strip = retracted || (
    <div style={{ display: 'flex', flexDirection: row, alignItems: 'center', gap: 6 }}>
      {open ? handle('📌', autoRetract ? 'Manter a barra HUD aberta' : 'Recolher a barra HUD quando parada') : null}
      {open ? handle('✋', 'Mover a barra HUD') : null}
      {notch}
      {open ? handle('⚙', update ? `Ações do Usage Monitor · ${update}` : 'Ações do Usage Monitor', Boolean(update)) : null}
    </div>
  );
  const outer = edge === 'bottom' || edge === 'right' ? [body, strip] : [strip, body];
  return (
    <div style={{
      display: 'inline-flex', flexDirection: horizontal ? 'column' : 'row', alignItems: 'center', gap: 10,
      transition: reduced ? 'none' : 'all var(--spring-gentle)', ...style
    }}>
      {outer[0]}{outer[1]}
    </div>
  );
}
