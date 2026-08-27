const { AppWindowFrame, AppToolbar, AppPanel, AppPanelHeader, AppPanelBody, AppSegmentedControl, AppButton, AppDataTable, AppMetric, AppKey, AppSourceMark, AppStatusIndicator } = DS;

const SERIES = [12, 18, 15, 26, 31, 28, 44, 51, 47, 58, 66, 61, 72, 68, 74, 81, 77, 69, 58, 47, 39, 31, 24, 19];
const PREV = [9, 14, 13, 21, 24, 22, 33, 39, 36, 44, 49, 46, 53, 51, 55, 59, 56, 51, 43, 36, 30, 24, 19, 15];

function Chart({ data, prev }) {
  const W = 900, H = 150, max = 100;
  const pt = (arr) => arr.map((v, i) => (i / (arr.length - 1)) * W + ',' + (H - (v / max) * H)).join(' ');
  return (
    <svg viewBox={'0 0 ' + W + ' ' + H} style={{ display: 'block', width: '100%', height: 'auto' }} role="img" aria-label="Consumo ao longo de 7 dias">
      {[0.25, 0.5, 0.75].map((g) => (
        <line key={g} x1="0" x2={W} y1={H * g} y2={H * g} stroke="var(--border)" strokeWidth="1" />
      ))}
      {[6, 12, 18].map((i) => (
        <line key={i} x1={(i / 23) * W} x2={(i / 23) * W} y1="0" y2={H} stroke="var(--border)" strokeWidth="1" strokeDasharray="2 4" />
      ))}
      <polyline points={pt(prev)} fill="none" stroke="var(--muted)" strokeWidth="1.5" strokeDasharray="4 4" />
      <polyline points={pt(data)} fill="none" stroke="var(--anthropic)" strokeWidth="2" />
    </svg>
  );
}

export function History() {
  const [range, setRange] = React.useState('7 dias');
  return (
    <AppWindowFrame title="Histórico — Anthropic · Padrão" style={{ width: 1030 }}>
      <AppToolbar>
        <AppKey>Fonte</AppKey>
        <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)' }}>Anthropic · Padrão</span>
        <span style={{ width: 1, alignSelf: 'stretch', background: 'var(--border)' }} />
        <AppSegmentedControl items={['24h', '7 dias', '30 dias', 'Total']} value={range} onChange={setRange} />
        <span style={{ flex: 1 }} />
        <AppStatusIndicator level="warn">Esgota em 4h 12m</AppStatusIndicator>
        <AppButton variant="ghost">PDF</AppButton>
      </AppToolbar>

      <AppPanel>
        <AppPanelHeader
          mark={<AppSourceMark source="anthropic" />}
          title="Consumo da janela"
          subtitle="linha cheia: período atual · tracejada: 7 dias anteriores"
        />
        <AppPanelBody>
          <Chart data={SERIES} prev={PREV} />
          <div style={{ display: 'flex', gap: 'var(--s4)', fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>
            <span>Sáb 09/08</span><span>Dom 10/08</span><span>Ter 12/08</span><span>Qua 13/08</span>
          </div>
        </AppPanelBody>
      </AppPanel>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 'var(--s3)' }}>
        <AppMetric label="Média por hora" value="3,1%" hint="+0,4 vs. período anterior" />
        <AppMetric label="Pico" value="81%" hint="Ter 12/08 16h BRT" />
        <AppMetric label="Reinícios na janela" value="14" />
        <AppMetric label="Previsão de esgotamento" value="Qua 13h00" hint="antes do reset" />
      </div>

      <AppPanel>
        <AppPanelHeader title="Reinícios de janela" subtitle="cada linha é um snapshot de reset registrado no banco local" />
        <AppDataTable
          columns={[
            { key: 'quando', label: 'Reset' },
            { key: 'pico', label: 'Pico antes do reset', numeric: true },
            { key: 'media', label: 'Média/h', numeric: true },
            { key: 'delta', label: 'vs. anterior', numeric: true }
          ]}
          rows={[
            { id: 1, quando: 'Qua 13/08 08h00 BRT', pico: '81%', media: '3,4%', delta: '+9%' },
            { id: 2, quando: 'Ter 12/08 03h00 BRT', pico: '72%', media: '3,0%', delta: '+2%' },
            { id: 3, quando: 'Seg 11/08 22h00 BRT', pico: '70%', media: '2,9%', delta: '−4%' },
            { id: 4, quando: 'Seg 11/08 17h00 BRT', pico: '74%', media: '3,1%', delta: '+6%' }
          ]}
        />
      </AppPanel>
    </AppWindowFrame>
  );
}
