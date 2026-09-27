const { AppWindowFrame, AppToolbar, AppPanel, AppPanelHeader, AppPanelBody, AppSegmentedControl, AppButton, AppDataTable, AppMetric, AppKey, AppSourceMark, AppStatusIndicator } = DS;

const SERIES = [12, 18, 15, 26, 31, 28, 44, 51, 47, 58, 66, 61, 72, 68, 74, 81, 77, 69, 58, 47, 39, 31, 24, 19];
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

export function History() {
  const [range, setRange] = React.useState('7 dias');
  const [quota, setQuota] = React.useState('Ambas');
  return (
    <AppWindowFrame title="Histórico — Anthropic · Padrão" style={{ width: 1030 }}>
      <AppToolbar>
        <AppKey>Fonte</AppKey>
        <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)' }}>Anthropic · Padrão</span>
        <span style={{ width: 1, alignSelf: 'stretch', background: 'var(--border)' }} />
        <AppSegmentedControl items={['24h', '7 dias', '30 dias', 'Total']} value={range} onChange={setRange} />
        <AppKey>Cota</AppKey>
        <AppSegmentedControl items={['5h', '7d', 'Ambas']} value={quota} onChange={setQuota} />
        <span style={{ flex: 1 }} />
        <AppStatusIndicator level="warn">Esgota em 4h 12m</AppStatusIndicator>
        <AppButton variant="ghost">PDF</AppButton>
      </AppToolbar>

      <AppPanel>
        <AppPanelHeader
          mark={<AppSourceMark source="anthropic" />}
          title="Consumo da janela"
          subtitle="5h e 7d no mesmo gráfico"
        />
        <AppPanelBody>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--s3)', fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>
            <span><i style={{ display: 'inline-block', width: 14, height: 2, background: 'var(--anthropic)', marginRight: 6, verticalAlign: 'middle' }} />5h</span>
            <span><i style={{ display: 'inline-block', width: 14, height: 2, background: 'var(--output)', marginRight: 6, verticalAlign: 'middle' }} />7d</span>
          </div>
          <Chart
            data={quota === '7d' ? WEEKLY : SERIES}
            prev={PREV}
            weekly={quota === 'Ambas' ? WEEKLY : null}
          />
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
        {/* Distância até o hábito, e não até o teto: a contagem de dias vai junto
            porque ela é a régua — "3,4×" sem dizer acima de quê não permite julgar
            se o número merece atenção. Some com menos de três dias medidos. */}
        <AppMetric label="Hoje vs. mediana diária" value="3,4×" hint="mediana de 6 dias" />
      </div>

      <AppPanel>
        {/* Uma linha por janela do intervalo, mais recentes primeiro (issue #320).
            "Esgotou em" conta da primeira leitura da janela: o início nominal a
            API não informa. */}
        <AppPanelHeader title="Janelas 5h" subtitle="5 janelas no intervalo" />
        <AppDataTable
          columns={[
            { key: 'inicio', label: 'Início observado' },
            { key: 'pico', label: 'Pico', numeric: true },
            { key: 'esgotou', label: 'Esgotou em', numeric: true },
            { key: 'ritmo', label: 'Ritmo', numeric: true }
          ]}
          rows={[
            { id: 1, inicio: '13/08 08:05 BRT · atual', pico: '68 %', esgotou: '—', ritmo: '23 %/h' },
            { id: 2, inicio: '13/08 03:02 BRT', pico: '100 %', esgotou: '3h 12min', ritmo: '31 %/h' },
            { id: 3, inicio: '12/08 21:58 BRT', pico: '74 %', esgotou: '—', ritmo: '16 %/h' },
            { id: 4, inicio: '12/08 16:55 BRT', pico: '81 %', esgotou: '—', ritmo: '17 %/h' }
          ]}
        />
        <AppPanelBody>
          <AppKey>Consumo por hora do dia (BRT)</AppKey>
          <svg viewBox="0 0 700 58" style={{ display: 'block', width: '100%', height: 56 }} role="img" aria-label="Pico às 14h BRT">
            {[2, 0, 0, 0, 0, 0, 0, 0, 6, 18, 30, 34, 22, 28, 48, 40, 30, 20, 12, 8, 6, 4, 2, 2].map((h, i) => (
              <rect key={i} x={i * 29 + 4} y={56 - h} width="20" height={h} rx="2" fill="var(--anthropic)" />
            ))}
          </svg>
          <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)' }}>Pico às 14h BRT · 15% do consumo</span>
        </AppPanelBody>
      </AppPanel>
    </AppWindowFrame>
  );
}
