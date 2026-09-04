const { AppWindowFrame, AppPanel, AppPanelHeader, AppPanelBody, AppSourceMark, AppDataRow, AppKey, AppValue, AppProgressTrack, AppStatusIndicator } = DS;

const CARDS = [
  { source: 'anthropic', title: 'Anthropic · Padrão', quotas: [['Sessão 5h', '68%', 68, 'warn'], ['Semanal', '41%', 41, 'ok']], level: 'warn', status: 'Atenção' },
  { source: 'codex', title: 'Codex', quotas: [['Codex 5h', '75%', 75, 'crit']], level: 'crit', status: 'Crítico' },
  { source: 'opencode', title: 'OpenCode Zen Free', quotas: [['Janela 5h', '23 msg', 23, 'ok']], level: 'ok', status: 'Normal' }
];

export function CardsOnly() {
  const [hover, setHover] = React.useState(false);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--s3)', alignItems: 'flex-start' }}>
      <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: '.07em', textTransform: 'uppercase', color: 'var(--muted)' }}>
        modo somente cards · passe o mouse no topo para revelar a moldura
      </span>
      <div onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)} style={{ width: 380 }}>
        <AppWindowFrame chrome={hover} title="Usage Monitor" dense showMaximize={false}>
          {CARDS.map((c) => (
            <AppPanel key={c.title}>
              <AppPanelHeader
                mark={<AppSourceMark source={c.source} />}
                title={c.title}
                status={<AppStatusIndicator level={c.level}>{c.status}</AppStatusIndicator>}
              />
              <AppPanelBody flush>
                {c.quotas.map(([k, v, p, l], i) => (
                  <AppDataRow key={k} last={i === c.quotas.length - 1} hoverable={false}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 3, flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)' }}>
                        <AppKey>{k}</AppKey><span style={{ flex: 1 }} /><AppValue size="primary">{v}</AppValue>
                      </div>
                      <AppProgressTrack percent={p} level={l} label={k} />
                    </div>
                  </AppDataRow>
                ))}
              </AppPanelBody>
            </AppPanel>
          ))}
        </AppWindowFrame>
      </div>
      <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', maxWidth: '52ch', borderLeft: '2px solid var(--border)', paddingLeft: 'var(--s3)' }}>
        Três saídas, não uma: a faixa revelada no hover, o item na bandeja e Ctrl+Shift+M. A faixa só existe enquanto o ponteiro está nela — presente o tempo todo ela venceria a pressão longa que reordena o primeiro card.
      </span>
    </div>
  );
}
