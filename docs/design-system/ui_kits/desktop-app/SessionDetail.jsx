const { AppWindowFrame, AppPanel, AppPanelHeader, AppPanelBody, AppBanner, AppButton, AppMetric, AppDataTable, AppKey, AppValue, AppProgressTrack, AppStatusIndicator, AppDataRow } = DS;

const TURNS = [8, 14, 21, 26, 34, 41, 47, 52, 58, 63, 69, 74, 79, 83, 88, 92];

function ContextChart() {
  const W = 880, H = 130, max = 100;
  const pts = TURNS.map((v, i) => (i / (TURNS.length - 1)) * W + ',' + (H - (v / max) * H)).join(' ');
  return (
    <svg viewBox={'0 0 ' + W + ' ' + H} style={{ display: 'block', width: '100%', height: 'auto' }} role="img" aria-label="Crescimento do contexto por turno">
      <line x1="0" x2={W} y1={H * 0.1} y2={H * 0.1} stroke="var(--crit)" strokeWidth="1" strokeDasharray="4 4" />
      <polyline points={pts} fill="none" stroke="var(--anthropic)" strokeWidth="2" />
      {TURNS.map((v, i) => (
        <circle key={i} cx={(i / (TURNS.length - 1)) * W} cy={H - (v / max) * H} r="2.5" fill="var(--anthropic)" />
      ))}
    </svg>
  );
}

export function SessionDetail() {
  const [adv, setAdv] = React.useState(true);
  return (
    <AppWindowFrame title="Sessão 7c4a1f92 — api-gateway" style={{ width: 980 }}>
      <AppBanner level="crit" title="Recomendado rodar /compact" action={<AppButton variant="ghost">Copiar comando</AppButton>}>
        O contexto vivo está em 92% da janela do sonnet-4-5. A partir daqui cada turno descarta o começo da conversa.
      </AppBanner>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 'var(--s3)' }}>
        <AppMetric label="Turnos" value="48" />
        <AppMetric label="Custo estimado" value="US$ 3,1841" hint="preço de tabela, não é fatura" />
        <AppMetric label="Tempo ativo" value="2h 41m" />
        <AppMetric label="Saúde" value="Saturada" size="sm" hint="contexto 92% da janela" />
      </div>

      <AppPanel>
        <AppPanelHeader
          title="Crescimento do contexto"
          subtitle="linha tracejada: limite da janela do modelo"
          status={<AppStatusIndicator level="crit">92%</AppStatusIndicator>}
        />
        <AppPanelBody>
          <ContextChart />
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <AppKey>turno 1</AppKey><AppKey>turno 24</AppKey><AppKey>turno 48</AppKey>
          </div>
        </AppPanelBody>
      </AppPanel>

      <AppPanel>
        <AppPanelHeader
          title="Avançado"
          subtitle="composição dos tokens, distribuição do custo e economia do cache"
          actions={<AppButton variant="ghost" onClick={() => setAdv(!adv)}>{adv ? 'Recolher' : 'Expandir'}</AppButton>}
        />
        {adv ? (
          <AppPanelBody>
            <div style={{ display: 'flex', height: 16, border: '1px solid var(--border)', borderRadius: 'var(--r1)', overflow: 'hidden' }}>
              <span style={{ width: '22%', background: 'var(--input)' }} />
              <span style={{ width: '13%', background: 'var(--output)' }} />
              <span style={{ width: '55%', background: 'var(--cread)' }} />
              <span style={{ width: '10%', background: 'var(--cwrite)' }} />
            </div>
            <div style={{ display: 'flex', gap: 'var(--s4)', flexWrap: 'wrap' }}>
              {[['input', '--input', '282 480'], ['output', '--output', '166 920'], ['cache read', '--cread', '706 200'], ['cache write', '--cwrite', '128 400']].map(([l, v, n]) => (
                <span key={l} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>
                  <span style={{ width: 8, height: 8, borderRadius: 2, background: 'var(' + v + ')' }} />{l} · {n}
                </span>
              ))}
            </div>
            <AppDataTable
              columns={[{ key: 'eixo', label: 'Eixo' }, { key: 'tokens', label: 'Tokens', numeric: true }, { key: 'custo', label: 'Custo', numeric: true }, { key: 'fatia', label: 'Fatia', numeric: true }]}
              rows={[
                { id: 1, eixo: 'Input', tokens: '282 480', custo: 'US$ 0,8474', fatia: '27%' },
                { id: 2, eixo: 'Output', tokens: '166 920', custo: 'US$ 2,5038', fatia: '79%' },
                { id: 3, eixo: 'Cache read', tokens: '706 200', custo: 'US$ 0,2119', fatia: '7%' },
                { id: 4, eixo: 'Cache write', tokens: '128 400', custo: 'US$ 0,4815', fatia: '15%' }
              ]}
            />
            <AppDataRow last hoverable={false} style={{ padding: 'var(--s2) 0' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 3, flex: 1 }}>
                <div style={{ display: 'flex', gap: 'var(--s2)' }}>
                  <AppKey>Economia do cache</AppKey><span style={{ flex: 1 }} /><AppValue>US$ 12,40 evitados</AppValue>
                </div>
                <AppProgressTrack percent={72} level="ok" label="Economia do cache" />
                <AppKey dim>72% dos tokens de leitura vieram do cache</AppKey>
              </div>
            </AppDataRow>
          </AppPanelBody>
        ) : null}
      </AppPanel>
    </AppWindowFrame>
  );
}
