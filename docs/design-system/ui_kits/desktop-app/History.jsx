const { AppWindowFrame, AppToolbar, AppPanel, AppPanelHeader, AppPanelBody, AppSegmentedControl, AppMenu, AppButton, AppDataTable, AppMetric, AppKey, AppSourceMark, AppStatusIndicator } = DS;

const SERIES = [12, 18, 15, 26, 31, 28, 44, 51, 47, 58, 66, 61, 72, 68, 74, 81, 77, 69, 58, 47, 39, 48, 59, 68];
const WEEKLY = [31, 31, 32, 33, 34, 34, 36, 37, 37, 38, 40, 40, 41, 41, 42, 43, 43, 43, 43, 43, 43, 43, 43, 43];
const PREV = [9, 14, 13, 21, 24, 22, 33, 39, 36, 44, 49, 46, 53, 51, 55, 59, 56, 51, 43, 36, 30, 24, 19, 15];

function Chart({ data, prev, weekly }) {
  const W = 900, H = 150, max = 100;
  const pt = (arr) => arr.map((v, i) => (i / (arr.length - 1)) * W + ',' + (H - (v / max) * H)).join(' ');
  // Massa sob a curva principal: preenchimento chapado (color-mix com o acento
  // da fonte, opacidade fixa via mistura com "transparent"), nunca gradiente —
  // mesma técnica da grade de atividade (CliSessions.jsx), estendida pro
  // gráfico de linha. A linha tracejada do período anterior fica sem
  // preenchimento: a massa é só da série que está sendo lida agora.
  const area = data.length > 0
    ? 'M0,' + H + ' L' + pt(data).replace(/ /g, ' L') + ' L' + W + ',' + H + ' Z'
    : '';
  return (
    <svg viewBox={'0 0 ' + W + ' ' + H} style={{ display: 'block', width: '100%', height: 'auto' }} role="img" aria-label="Consumo ao longo de 7 dias">
      {[0.25, 0.5, 0.75].map((g) => (
        <line key={g} x1="0" x2={W} y1={H * g} y2={H * g} stroke="var(--border)" strokeWidth="1" />
      ))}
      {[6, 12, 18].map((i) => (
        <line key={i} x1={(i / 23) * W} x2={(i / 23) * W} y1="0" y2={H} stroke="var(--border)" strokeWidth="1" strokeDasharray="2 4" />
      ))}
      <path d={area} fill="color-mix(in srgb, var(--anthropic) 14%, transparent)" stroke="none" />
      {/* Com a semanal sobreposta (issue #320) o tracejado do período anterior some. */}
      {weekly
        ? <polyline points={pt(weekly)} fill="none" stroke="var(--output)" strokeWidth="2" />
        : <polyline points={pt(prev)} fill="none" stroke="var(--muted)" strokeWidth="1.5" strokeDasharray="4 4" />}
      <polyline points={pt(data)} fill="none" stroke="var(--anthropic)" strokeWidth="2" />
    </svg>
  );
}

// Direção 01 aprovada na issue #383: resumo → gráfico → detalhes.
export function History() {
  const [range, setRange] = React.useState('7 dias');
  const [quota, setQuota] = React.useState('Ambas');
  const [preview, setPreview] = React.useState(false);
  const [account, setAccount] = React.useState('developer.account.with.long.name.1@example.test');
  const [menu, setMenu] = React.useState(null);
  const accounts = [1, 2, 3].map(i => {
    const email = `developer.account.with.long.name.${i}@example.test`;
    return { id: email, label: `${email} — ${email}'s Organization · Equipe ${i}` };
  });
  const accountLabel = accounts.find(item => item.id === account).label;
  const dropdown = (id, label, value, items, onSelect) => <AppMenu
    open={menu === id} options={items} value={value}
    onSelect={next => { onSelect(next); setMenu(null); }} onDismiss={() => setMenu(null)}
    placement="bottom" maxWidth="calc(100cqw - 32px)" maxHeight={420} wrapLabels
    style={{ minWidth: 0 }}>
    <AppButton aria-label={`${label}: ${id === 'account' ? accountLabel : value}`} onClick={() => setMenu(menu === id ? null : id)}
      style={{ width: '100%', minWidth: 0, justifyContent: 'flex-start' }}><span style={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{label}: {id === 'account' ? accountLabel : value} ▾</span></AppButton>
  </AppMenu>;
  const weeklyOnly = quota === '7d';
  const metrics = weeklyOnly
    ? [['Uso atual', '43% / 100%'], ['Janelas no intervalo', '1'], ['Pico médio por janela', '43%']]
    : [['Uso atual', '68% / 100%'], ['Janelas no intervalo', '5 · 1 esgotou'], ['Pico médio por janela', '74%']];
  const rows = (weekly) => [
    { id: 1, label: 'Uso atual', value: weekly ? '43% / 100%' : '68% / 100%' },
    { id: 2, label: 'Média por hora', value: weekly ? '1,0 pp/h' : '5,6 pp/h' },
    { id: 3, label: 'Previsão', value: weekly ? 'A janela deve reiniciar antes do limite' : 'Esgota Ter 06/10 16h00 BRT' },
    { id: 4, label: 'Janelas no intervalo', value: weekly ? '1' : '5 · 1 esgotou' },
    { id: 5, label: 'Hoje vs. mediana diária', value: weekly ? '1,1× (6 dias)' : '3,4× (6 dias)' }
  ];
  const metricColumns = [{ key: 'label', label: 'Métrica' }, { key: 'value', label: 'Valor', numeric: true }];
  return (
    <AppWindowFrame title="Histórico — Anthropic" style={{ width: 1030, containerType: 'inline-size' }}>
      <style>{`
        .history-account{flex:1;min-width:0;display:flex}.history-account>div{width:100%}
        .history-range-menu{display:none}.history-quotas,.history-ranges{flex-shrink:0}
        @container (max-width:719px){.history-account{flex-basis:calc(100% - 70px)}.history-ranges{order:3}.history-quotas{order:4}}
        @container (max-width:599px){.history-account{flex-basis:100%}.history-ranges{display:none}.history-range-menu{display:flex;flex:1;min-width:0}.history-range-menu>div{width:100%}.history-quotas{flex-basis:100%}}
      `}</style>
      <AppToolbar style={{ height: 'auto', flexWrap: 'wrap', overflow: 'visible', padding: 'var(--s2)', gap: 'var(--s2)', flexShrink: 0 }}>
        <div className="history-account">{dropdown('account', 'Conta', account, accounts, setAccount)}</div>
        <div className="history-ranges" aria-label="Intervalo"><AppSegmentedControl items={['24h', '7 dias', '30 dias', 'Total']} value={range} onChange={setRange} /></div>
        <div className="history-range-menu">{dropdown('range', 'Intervalo', range, ['24h', '7 dias', '30 dias', 'Total'], setRange)}</div>
        <div className="history-quotas" aria-label="Cota"><AppSegmentedControl items={['5h', '7d', 'Ambas']} value={quota} onChange={setQuota} /></div>
        <AppButton onClick={() => setPreview(!preview)}>PDF</AppButton>
      </AppToolbar>
      {preview && <p style={{ fontFamily: 'var(--sans)', margin: 0, overflowWrap: 'anywhere' }}>Prévia do escopo: Anthropic · {accountLabel} · {range} · {quota}. Inclui detalhes e todas as janelas disponíveis; zoom e expansão não retiram dados. Esta amostra não grava arquivo.</p>}
      <div style={{ maxHeight: 680, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 'var(--s3)' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--s3)' }}>{metrics.map(([label, value]) => <AppMetric key={label} label={label} value={value} style={{ minWidth: 148, maxWidth: 240 }} />)}</div>
        <AppPanel>
          <AppPanelHeader mark={<AppSourceMark source="anthropic" />} title="Claude" subtitle={quota === 'Ambas' ? '5h e 7d no mesmo gráfico' : quota} />
          <AppPanelBody>
            <div style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>{quota === 'Ambas' ? '5h · 7d' : quota + ' · período anterior tracejado'}</div>
            <Chart data={weeklyOnly ? WEEKLY : SERIES} prev={PREV} weekly={quota === 'Ambas' ? WEEKLY : null} />
          </AppPanelBody>
        </AppPanel>
        <details open>
          <summary style={{ fontFamily: 'var(--mono)', cursor: 'pointer' }}>Resumo das cotas</summary>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--s3)', marginTop: 'var(--s2)' }}>
            {quota !== '7d' && <AppPanel><AppPanelHeader title="Resumo intervalar" /><AppDataTable columns={metricColumns} rows={rows(false)} /></AppPanel>}
            {quota !== '5h' && <AppPanel><AppPanelHeader title="Resumo semanal" /><AppDataTable columns={metricColumns} rows={rows(true)} /></AppPanel>}
          </div>
        </details>
        <details open>
          <summary style={{ fontFamily: 'var(--mono)', cursor: 'pointer' }}>Janelas e distribuição horária</summary>
          <AppPanel style={{ marginTop: 'var(--s2)' }}>
            <AppPanelHeader title={weeklyOnly ? 'Janelas 7d' : 'Janelas 5h'} subtitle="Até oito janelas recentes na tela; todas no PDF" />
            {/* #392 (S9): lista de janelas à esquerda, detalhe da escolhida à direita. */}
            <div style={{ display: 'flex', gap: 'var(--s2)' }}>
              <div style={{ width: 200, flex: 'none', fontFamily: 'var(--mono)', fontSize: 'var(--t12)' }}>
                {(weeklyOnly ? [['03/10 21:05 BRT · atual', 'pico 43 %']] : [['06/10 12:05 BRT · atual', 'pico 68 %'], ['06/10 07:02 BRT', 'pico 100 % · esgotou em 3h 12min']]).map(([start, detail], i) => (
                  <div key={start} style={{ padding: 'var(--s2) var(--s3)', background: i === 0 ? 'var(--raised)' : 'transparent', borderTop: i ? '1px solid var(--border)' : 'none' }}>{start}<br /><span style={{ color: 'var(--muted)', fontSize: 'var(--t10)' }}>{detail}</span></div>
                ))}
              </div>
              <AppPanelBody style={{ flex: 1, minWidth: 0 }}>
                <AppDataTable columns={[{ key: 'k', label: 'Métrica' }, { key: 'v', label: 'Valor', numeric: true }]} rows={weeklyOnly ? [{ id: 1, k: 'Ativa', v: '21:05 → 11:40 · 2d 14h' }, { id: 2, k: 'Pico', v: '43 %' }, { id: 3, k: 'Esgotou em', v: '—' }, { id: 4, k: 'Ritmo', v: '1 %/h' }] : [{ id: 1, k: 'Ativa', v: '12:12 → 15:40 · 3h 28min' }, { id: 2, k: 'Pico', v: '68 %' }, { id: 3, k: 'Esgotou em', v: '—' }, { id: 4, k: 'Ritmo', v: '5,6 %/h' }]} />
                <AppKey>Consumo por hora do dia (BRT) · só desta janela</AppKey><svg viewBox="0 0 700 58" style={{ display: 'block', width: '100%', height: 56 }} role="img" aria-label="Pico às 14h BRT">{[2,0,0,0,0,0,0,0,6,18,30,34,22,28,48,40,30,20,12,8,6,4,2,2].map((h,i) => <rect key={i} x={i*29+4} y={56-h} width="20" height={h} rx="2" fill="var(--anthropic)" />)}</svg><span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)' }}>Pico às 14h BRT · 15% do consumo</span>
              </AppPanelBody>
            </div>
          </AppPanel>
        </details>
      </div>
      <div style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>Última coleta: Ter 06/10 15h00 BRT</div>
    </AppWindowFrame>
  );
}
