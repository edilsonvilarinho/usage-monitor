const { AppWindowFrame, AppToolbar, AppTabs, AppSegmentedControl, AppButton, AppPanel, AppPanelHeader, AppPanelBody, AppDataRow, AppKey, AppValue, AppIconButton, AppSourceMark, AppColumnHeader, AppMetric, AppBanner, AppStatusIndicator } = DS;

const MEMBERS = [
  {
    apelido: 'ana', maquina: 'MB-ANA', tokens: '2 418 000', custo: 'US$ 6,4120', fatia: '41%', ativo: '7h 12m', cor: 'var(--anthropic)',
    sessoes: [
      { id: '7c4a1f92', ctx: 'api-gateway · feat/rate-limit', turnos: 48, custo: 'US$ 3,1841', ativo: '2h 41m' },
      { id: 'b81e35c0', ctx: 'checkout-web · main', turnos: 26, custo: 'US$ 1,6279', ativo: '1h 08m' },
      { id: 'e02b7c44', ctx: 'api-gateway · fix/timeout', turnos: 9, custo: 'US$ 1,6000', ativo: '0h 31m' }
    ]
  },
  {
    apelido: 'bruno', maquina: 'DESKTOP-B2', tokens: '1 902 000', custo: 'US$ 4,8710', fatia: '31%', ativo: '5h 04m', cor: 'var(--codex)',
    sessoes: [
      { id: '3fd90a17', ctx: 'infra-terraform · chore/tfsec', turnos: 12, custo: 'US$ 0,1904', ativo: '0h 22m' },
      { id: 'c7712fa0', ctx: 'billing-svc · main', turnos: 31, custo: 'US$ 4,6806', ativo: '4h 42m' }
    ]
  },
  {
    apelido: 'dev-01', maquina: 'DESKTOP-A1', tokens: '1 214 000', custo: 'US$ 3,0940', fatia: '20%', ativo: '3h 18m', cor: 'var(--deepseek)',
    sessoes: [{ id: 'a5518d6b', ctx: 'docs-portal · main', turnos: 5, custo: 'US$ 0,1620', ativo: '0h 14m' }]
  },
  { apelido: 'carla', maquina: 'MB-CARLA', tokens: '486 000', custo: 'US$ 1,2410', fatia: '8%', ativo: '—', cor: 'var(--minimax)', sessoes: [] }
];

const COLS = [{ label: 'Integrante', flex: 2, width: 150 }, { label: 'Máquina', width: 108 }, { label: 'Tokens', width: 88, align: 'right' }, { label: 'Custo', width: 84, align: 'right' }, { label: 'Ativo', width: 54, align: 'right' }, { label: 'Fatia', width: 48, align: 'right' }];

export function TeamUsage() {
  const [tab, setTab] = React.useState('Sessões do time');
  const [win, setWin] = React.useState('7 dias');
  const [open, setOpen] = React.useState({ ana: true });
  return (
    <AppWindowFrame title="Sessões do time — dev@example.com" style={{ width: 1030 }}>
      <AppTabs items={['Sessões do time', 'Tendência do time']} value={tab} onChange={setTab} />
      <AppToolbar>
        <AppSegmentedControl items={['5h', '7 dias', '30 dias', 'Total']} value={win} onChange={setWin} />
        <span style={{ flex: 1 }} />
        <AppKey>última leitura há 4s · envio a cada 30s</AppKey>
        <AppButton variant="ghost">CSV</AppButton>
        <AppButton variant="ghost">PDF</AppButton>
      </AppToolbar>

      {tab === 'Sessões do time' ? (
        <React.Fragment>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 'var(--s3)' }}>
            <AppMetric label="Integrantes" value="4" />
            <AppMetric label="Tokens do time" value="6 020 000" />
            <AppMetric label="Custo do time" value="US$ 15,6180" />
            <AppMetric label="Tempo ativo" value="15h 34m" hint="pausas > 5 min descartadas" />
          </div>
          <AppPanel>
            <AppPanelHeader title="Consumo por integrante" subtitle="ordem alfabética pelo apelido · o consumo ordena dentro de cada um" />
            <AppPanelBody style={{ paddingBottom: 0 }}>
              <AppColumnHeader items={COLS} offset={20} style={{ padding: 0 }} />
            </AppPanelBody>
            <AppPanelBody flush>
              {MEMBERS.map((m, mi) => {
                const isOpen = !!open[m.apelido];
                const rows = [];
                rows.push(
                  <AppDataRow key={m.apelido} mark={<AppSourceMark color={m.cor} />} onClick={() => setOpen((o) => Object.assign({}, o, { [m.apelido]: !o[m.apelido] }))} last={mi === MEMBERS.length - 1 && !isOpen} style={{ gap: 'var(--s2)' }}>
                    <AppIconButton variant="ghost" glyph={isOpen ? '▾' : '▸'} label={(isOpen ? 'Recolher ' : 'Expandir ') + m.apelido} />
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 0, flex: 2, minWidth: 130, overflow: 'hidden' }}>
                      <AppValue size="sm">{m.apelido}</AppValue>
                      <AppKey>{m.sessoes.length ? m.sessoes.length + ' sessões na janela' : 'sem turnos nesta janela'}</AppKey>
                    </div>
                    <AppValue size="sm" dim style={{ width: 108, flex: 'none' }}>{m.maquina}</AppValue>
                    <AppValue size="sm" style={{ width: 88, flex: 'none', textAlign: 'right' }}>{m.tokens}</AppValue>
                    <AppValue size="sm" style={{ width: 84, flex: 'none', textAlign: 'right' }}>{m.custo}</AppValue>
                    <AppValue size="sm" style={{ width: 54, flex: 'none', textAlign: 'right' }}>{m.ativo}</AppValue>
                    <AppValue size="sm" style={{ width: 48, flex: 'none', textAlign: 'right' }}>{m.fatia}</AppValue>
                  </AppDataRow>
                );
                if (isOpen) {
                  m.sessoes.forEach((s, si) => {
                    rows.push(
                      <AppDataRow key={m.apelido + s.id} guide indent={26} last={mi === MEMBERS.length - 1 && si === m.sessoes.length - 1} style={{ gap: 'var(--s2)' }}>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 0, flex: 3, minWidth: 150, overflow: 'hidden' }}>
                          <AppValue size="sm">{s.id}</AppValue>
                          <AppKey>{s.ctx} · {s.turnos} turnos</AppKey>
                        </div>
                        <AppValue size="sm" style={{ width: 84, flex: 'none', textAlign: 'right' }}>{s.custo}</AppValue>
                        <AppValue size="sm" style={{ width: 54, flex: 'none', textAlign: 'right' }}>{s.ativo}</AppValue>
                        <span style={{ width: 48, flex: 'none' }} />
                      </AppDataRow>
                    );
                  });
                }
                return rows;
              })}
            </AppPanelBody>
          </AppPanel>
        </React.Fragment>
      ) : (
        <React.Fragment>
          <AppBanner level="info" title="Tendência exige servidor 0.6.0+">
            Contra um servidor anterior a aba não aparece. A coluna de tempo ativo exige 0.7.0+ e cai para "—".
          </AppBanner>
          <AppPanel>
            <AppPanelHeader title="Tendência diária do time" subtitle="30 dias · uma cor por pessoa · todas na mesma escala" />
            <AppPanelBody>
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 3, height: 170 }}>
                {Array.from({ length: 30 }).map((_, d) => (
                  <span key={d} style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', gap: 1, minWidth: 5 }}>
                    {MEMBERS.map((m, mi) => {
                      const v = Math.max(0, Math.round(Math.abs(Math.sin((d + mi * 4) / 3.3)) * (30 - mi * 6)));
                      return <span key={m.apelido} style={{ height: v + '%', background: m.cor, borderRadius: mi === 0 ? '2px 2px 0 0' : 0 }} />;
                    })}
                  </span>
                ))}
              </div>
              <div style={{ display: 'flex', gap: 'var(--s4)', flexWrap: 'wrap' }}>
                {MEMBERS.map((m) => (
                  <span key={m.apelido} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>
                    <span style={{ width: 8, height: 8, borderRadius: 2, background: m.cor }} />{m.apelido}
                  </span>
                ))}
                <span style={{ flex: 1 }} />
                <AppStatusIndicator level="ok">fecha com os números locais</AppStatusIndicator>
              </div>
            </AppPanelBody>
          </AppPanel>
        </React.Fragment>
      )}
    </AppWindowFrame>
  );
}
