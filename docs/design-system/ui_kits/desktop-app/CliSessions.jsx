const { AppWindowFrame, AppToolbar, AppTabs, AppSegmentedControl, AppTextField, AppButton, AppPanel, AppPanelHeader, AppPanelBody, AppDataRow, AppKey, AppValue, AppStatusIndicator, AppColumnHeader, AppDataTable, AppMetric, AppEmptyState, AppSourceMark } = DS;

const SESSIONS = [
  { id: '7c4a1f92', projeto: 'api-gateway', branch: 'feat/rate-limit', modelo: 'sonnet-4-5', turnos: 48, tokens: '1 284 000', custo: 'US$ 3,1841', ativo: '2h 41m', saude: 'crit', veredito: 'Saturada' },
  { id: 'b81e35c0', projeto: 'checkout-web', branch: 'main', modelo: 'sonnet-4-5', turnos: 26, tokens: '612 400', custo: 'US$ 1,6279', ativo: '1h 08m', saude: 'warn', veredito: 'Atenção' },
  { id: '3fd90a17', projeto: 'infra-terraform', branch: 'chore/tfsec', modelo: 'haiku-4-5', turnos: 12, tokens: '208 100', custo: 'US$ 0,1904', ativo: '0h 22m', saude: 'ok', veredito: 'Saudável' },
  { id: 'e02b7c44', projeto: 'api-gateway', branch: 'fix/timeout', modelo: 'opus-4-1', turnos: 9, tokens: '164 900', custo: 'US$ 2,4730', ativo: '0h 31m', saude: 'ok', veredito: 'Saudável' },
  { id: 'a5518d6b', projeto: 'docs-portal', branch: 'main', modelo: 'sonnet-4-5-legacy', turnos: 5, tokens: '61 200', custo: 'US$ 0,1620', ativo: '0h 14m', saude: null, veredito: '—' }
];

const COLS = [{ label: 'Sessão', flex: 2, width: 150 }, { label: 'Modelo', width: 112 }, { label: 'Turnos', width: 54, align: 'right' }, { label: 'Tokens', width: 84, align: 'right' }, { label: 'Custo', width: 84, align: 'right' }, { label: 'Ativo', width: 54, align: 'right' }, { label: 'Saúde', width: 88, align: 'right' }];

function SessionRow({ s, last, onOpen }) {
  return (
    <AppDataRow last={last} onClick={onOpen} style={{ gap: 'var(--s2)' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 0, flex: 2, minWidth: 150, overflow: 'hidden' }}>
        <AppValue size="sm">{s.id}</AppValue>
        <AppKey>{s.projeto} · {s.branch}</AppKey>
      </div>
      <AppValue size="sm" style={{ width: 112, flex: 'none' }} dim>{s.modelo}</AppValue>
      <AppValue size="sm" style={{ width: 54, flex: 'none', textAlign: 'right' }}>{s.turnos}</AppValue>
      <AppValue size="sm" style={{ width: 84, flex: 'none', textAlign: 'right' }}>{s.tokens}</AppValue>
      <AppValue size="sm" style={{ width: 84, flex: 'none', textAlign: 'right' }}>{s.custo}</AppValue>
      <AppValue size="sm" style={{ width: 54, flex: 'none', textAlign: 'right' }}>{s.ativo}</AppValue>
      <span style={{ width: 88, flex: 'none', display: 'flex', justifyContent: 'flex-end' }}>
        {s.saude ? (
          <AppStatusIndicator level={s.saude} title={'Contexto vivo contra a janela do modelo ' + s.modelo}>{s.veredito}</AppStatusIndicator>
        ) : (
          <AppKey dim>sem veredito</AppKey>
        )}
      </span>
    </AppDataRow>
  );
}

function Resumo() {
  return (
    <React.Fragment>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 'var(--s3)' }}>
        <AppMetric label="Ritmo" value="US$ 1,84/h" hint="projeção US$ 9,20 no fechamento" />
        <AppMetric label="Tokens/h" value="412 900" />
        <AppMetric label="Economia do cache" value="US$ 12,40" hint="72% dos tokens vieram do cache" />
        <AppMetric label="Tempo ativo" value="5h 26m" hint="pausas > 5 min descartadas" />
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--s3)' }}>
        <AppPanel>
          <AppPanelHeader title="Por projeto" subtitle="ranking por custo" />
          <AppDataTable
            columns={[{ key: 'p', label: 'Projeto' }, { key: 't', label: 'Turnos', numeric: true }, { key: 'c', label: 'Custo', numeric: true }]}
            rows={[
              { id: 1, p: 'api-gateway', t: 57, c: 'US$ 5,6571' },
              { id: 2, p: 'checkout-web', t: 26, c: 'US$ 1,6279' },
              { id: 3, p: 'infra-terraform', t: 12, c: 'US$ 0,1904' },
              { id: 4, p: 'docs-portal', t: 5, c: 'US$ 0,1620' }
            ]}
          />
        </AppPanel>
        <AppPanel>
          <AppPanelHeader title="Por modelo" subtitle="mesmos turnos, outro eixo" />
          <AppDataTable
            columns={[{ key: 'm', label: 'Modelo' }, { key: 't', label: 'Turnos', numeric: true }, { key: 'c', label: 'Custo', numeric: true }]}
            rows={[
              { id: 1, m: 'sonnet-4-5', t: 74, c: 'US$ 4,8120' },
              { id: 2, m: 'opus-4-1', t: 9, c: 'US$ 2,4730' },
              { id: 3, m: 'haiku-4-5', t: 12, c: 'US$ 0,1904' },
              { id: 4, m: 'sonnet-4-5-legacy', t: 5, c: 'US$ 0,1620' }
            ]}
          />
        </AppPanel>
      </div>
      <AppPanel>
        <AppPanelHeader title="Grade de atividade" subtitle="dia da semana × hora, BRT" />
        <AppPanelBody>
          <div style={{ display: 'grid', gridTemplateColumns: '34px repeat(24, 1fr)', gap: 2, alignItems: 'center' }}>
            {['Seg', 'Ter', 'Qua', 'Qui', 'Sex'].map((d, r) => (
              <React.Fragment key={d}>
                <AppKey>{d}</AppKey>
                {Array.from({ length: 24 }).map((_, h) => {
                  const v = (Math.sin((h + r * 3) / 3.1) + 1) / 2 * (h > 7 && h < 20 ? 1 : 0.12);
                  return <span key={h} style={{ display: 'block', aspectRatio: '1', borderRadius: 2, border: '1px solid var(--border)', background: v < 0.08 ? 'var(--raised)' : 'color-mix(in srgb, var(--anthropic) ' + Math.round(v * 85) + '%, var(--raised))' }} />;
                })}
              </React.Fragment>
            ))}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <AppKey>00h</AppKey><AppKey>08h</AppKey><AppKey>16h</AppKey><AppKey>23h</AppKey>
          </div>
        </AppPanelBody>
      </AppPanel>
      <AppPanel>
        <AppPanelHeader title="Ferramentas mais chamadas" subtitle="por contagem de chamadas na janela" />
        <AppDataTable
          columns={[{ key: 'f', label: 'Ferramenta' }, { key: 'n', label: 'Chamadas', numeric: true }, { key: 's', label: 'Fatia', numeric: true }]}
          rows={[
            { id: 1, f: 'Read', n: 412, s: '38%' },
            { id: 2, f: 'Bash', n: 236, s: '22%' },
            { id: 3, f: 'Edit', n: 198, s: '18%' },
            { id: 4, f: 'Grep', n: 141, s: '13%' },
            { id: 5, f: 'Write', n: 92, s: '9%' }
          ]}
        />
      </AppPanel>
    </React.Fragment>
  );
}

export function CliSessions({ onOpenSession }) {
  const [tab, setTab] = React.useState('Sessões');
  const [win, setWin] = React.useState('5h');
  const [q, setQ] = React.useState('');
  const rows = SESSIONS.filter((s) => !q || (s.projeto + s.branch + s.modelo + s.id).toLowerCase().includes(q.toLowerCase()));
  return (
    <AppWindowFrame title="Sessões CLI — dev@example.com" style={{ width: 1030 }}>
      <AppTabs items={['Sessões', 'Resumo', 'Tendência']} value={tab} onChange={setTab} />
      <AppToolbar>
        <AppSegmentedControl items={['5h', '7 dias', '30 dias', 'Total']} value={win} onChange={setWin} />
        <AppTextField placeholder="Filtrar projeto, branch ou modelo" value={q} onChange={setQ} style={{ maxWidth: 250 }} />
        <span style={{ flex: 1 }} />
        <AppStatusIndicator level="warn">1 saturada · 1 em atenção</AppStatusIndicator>
        <AppButton variant="ghost">CSV</AppButton>
        <AppButton variant="ghost">JSON</AppButton>
        <AppButton variant="ghost">PDF</AppButton>
      </AppToolbar>

      {tab === 'Sessões' ? (
        <AppPanel>
          <AppPanelHeader title={'Janela ' + win + ' — ancorada no reset da conta'} subtitle={rows.length + ' sessões com atividade nesta janela'} />
          <AppPanelBody style={{ paddingBottom: 0 }}>
            <AppColumnHeader items={COLS} offset={0} style={{ padding: 0 }} />
          </AppPanelBody>
          <AppPanelBody flush>
            {rows.length ? rows.map((s, i) => (
              <SessionRow key={s.id} s={s} last={i === rows.length - 1} onOpen={() => onOpenSession && onOpenSession()} />
            )) : <AppEmptyState message="Nenhum turno nesta janela com esse filtro." />}
          </AppPanelBody>
        </AppPanel>
      ) : null}

      {tab === 'Resumo' ? <Resumo /> : null}

      {tab === 'Tendência' ? (
        <AppPanel>
          <AppPanelHeader mark={<AppSourceMark source="anthropic" />} title="Tendência diária" subtitle="30 dias · todas as barras na mesma escala" />
          <AppPanelBody>
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: 3, height: 150 }}>
              {Array.from({ length: 30 }).map((_, i) => {
                const v = 12 + Math.round(Math.abs(Math.sin(i / 2.7)) * 78);
                return <span key={i} title={v + '%'} style={{ flex: 1, height: v + '%', background: 'var(--anthropic)', borderRadius: '2px 2px 0 0', minWidth: 4 }} />;
              })}
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <AppKey>15/07</AppKey><AppKey>30/07</AppKey><AppKey>13/08</AppKey>
            </div>
          </AppPanelBody>
        </AppPanel>
      ) : null}
    </AppWindowFrame>
  );
}
