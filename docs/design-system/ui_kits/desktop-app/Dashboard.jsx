const { AppWindowFrame, AppStatusBar, AppPanel, AppPanelHeader, AppPanelBody, AppSourceMark, AppDataRow, AppKey, AppValue, AppProgressTrack, AppStatusIndicator, AppIconButton, AppButton, AppBanner, AppUpdateStrip, AppMetric } = DS;

const NAV = [
  { glyph: '⏱', label: 'Histórico' },
  { glyph: '▣', label: 'Sessões CLI' },
  { glyph: '◫', label: 'Uso do time' },
  { glyph: '◉', label: 'Conectados agora' }
];

function Quota({ label, value, percent, level, reset, last }) {
  return (
    <AppDataRow last={last} hoverable={false}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 3, flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)' }}>
          <AppKey>{label}</AppKey>
          <span style={{ flex: 1 }} />
          <AppValue>{value}</AppValue>
        </div>
        <AppProgressTrack percent={percent} level={level} label={label} />
        {reset ? <AppKey dim>{reset}</AppKey> : null}
      </div>
    </AppDataRow>
  );
}

function Card({ card, onOpen, minimized, onToggle }) {
  const nav = NAV.slice(0, card.nav);
  return (
    <AppPanel>
      <AppPanelHeader
        mark={<AppSourceMark source={card.source} />}
        title={card.title}
        subtitle={card.subtitle}
        status={<AppStatusIndicator level={card.level} title={card.tooltip}>{card.status}</AppStatusIndicator>}
        actions={
          <React.Fragment>
            <AppIconButton glyph="↻" label={'Atualizar ' + card.title} />
            <AppIconButton glyph={minimized ? '+' : '–'} label={minimized ? 'Expandir card' : 'Minimizar card'} onClick={onToggle} />
          </React.Fragment>
        }
      />
      {minimized ? (
        <AppPanelBody dense>
          <div style={{ display: 'flex', gap: 'var(--s2)' }}>
            {card.quotas.slice(0, 3).map((q) => (
              <AppMetric key={q.label} label={q.label} value={q.value} size="lg" align="center" style={{ flex: 1 }} />
            ))}
          </div>
          {card.banner ? <AppBanner level={card.banner.level} title={card.banner.title}>{card.banner.body}</AppBanner> : null}
        </AppPanelBody>
      ) : (
        <React.Fragment>
          <AppPanelBody flush>
            {card.quotas.map((q, i) => (
              <Quota key={q.label} {...q} last={i === card.quotas.length - 1} />
            ))}
          </AppPanelBody>
          {card.banner ? (
            <AppPanelBody dense>
              <AppBanner level={card.banner.level} title={card.banner.title}>{card.banner.body}</AppBanner>
            </AppPanelBody>
          ) : null}
        </React.Fragment>
      )}
      {nav.length ? (
        <AppStatusBar>
          {nav.map((n, i) => (
            <AppIconButton key={n.label} glyph={n.glyph} label={n.label} onClick={() => onOpen && onOpen(i)} />
          ))}
        </AppStatusBar>
      ) : null}
    </AppPanel>
  );
}

const CARDS = [
  {
    id: 'anthropic-default',
    source: 'anthropic',
    title: 'Anthropic · Padrão',
    subtitle: 'dev@example.com — Example Org',
    level: 'warn',
    status: 'Atenção',
    tooltip: 'Projeção de uso · Cota Sessão 5h · Status Atenção · No ritmo atual, a cota deve esgotar antes do reset. Previsão: Qua 13/08 13h00 BRT.',
    nav: 4,
    quotas: [
      { label: 'Sessão 5h', value: '68%', percent: 68, level: 'warn', reset: 'Reinício: Qua 13h00 BRT' },
      { label: 'Semanal', value: '41%', percent: 41, level: 'ok', reset: 'Reinício: Sáb 15/08 21h00 BRT' },
      { label: 'Créditos de uso', value: 'US$ 190,00 / 500,00', percent: 38, level: 'info', reset: 'Reinicia no início do mês' }
    ]
  },
  {
    id: 'anthropic-sandbox',
    source: 'anthropic',
    title: 'Anthropic · Sandbox',
    subtitle: 'qa@example.com — Example Org (Sandbox)',
    level: 'ok',
    status: 'Normal',
    tooltip: 'Projeção de uso · Cota Sessão 5h · Status Normal · No ritmo atual, a cota deve resetar antes de esgotar.',
    nav: 2,
    quotas: [
      { label: 'Sessão 5h', value: '12%', percent: 12, level: 'ok', reset: 'Reinício: Qua 15h00 BRT' },
      { label: 'Semanal', value: '7%', percent: 7, level: 'ok', reset: 'Reinício: Dom 16/08 21h00 BRT' }
    ]
  },
  {
    id: 'codex',
    source: 'codex',
    title: 'Codex',
    level: 'crit',
    status: 'Crítico',
    tooltip: 'Projeção de uso · Cota Codex 5h · Status Crítico · No ritmo atual, a cota deve esgotar antes do reset. Previsão: Qua 13/08 18h51 BRT.',
    nav: 1,
    quotas: [
      { label: 'Codex 5h', value: '75%', percent: 75, level: 'crit', reset: 'Reinício: Qua 20h51 BRT' },
      { label: 'Codex 7d', value: '12%', percent: 12, level: 'ok', reset: 'Reinício: Ter 01/09 15h51 BRT' }
    ]
  },
  {
    id: 'deepseek',
    source: 'deepseek',
    title: 'DeepSeek',
    level: 'ok',
    status: 'Normal',
    tooltip: 'Projeção de uso · Cota Saldo · Status Normal · No ritmo atual, os créditos devem acabar em Seg 02/11 13h38 BRT.',
    nav: 1,
    quotas: [
      { label: 'Saldo atual', value: 'US$ 1.284,00', percent: 100, level: 'ok', reset: 'Saldo não expira · autonomia estimada 81 dias' }
    ]
  },
  {
    id: 'opencode',
    source: 'opencode',
    title: 'OpenCode Zen Free',
    subtitle: 'base local · opencode.db',
    level: 'ok',
    status: 'Normal',
    tooltip: 'Atividade observada · janela de 5h · Status Normal.',
    nav: 1,
    quotas: [
      { label: 'Janela 5h', value: '23 mensagens', percent: 23, level: 'ok', reset: 'Reinício: Qua 13h00 BRT' },
      { label: 'Janela 7d', value: '186 mensagens', percent: 61, level: 'ok', reset: 'Reinício: Sáb 15/08 21h00 BRT' }
    ]
  },
  {
    id: 'minimax',
    source: 'minimax',
    title: 'MiniMax',
    level: 'warn',
    status: 'Atenção',
    tooltip: 'Projeção de uso · Cota MiniMax-M2 · Status Atenção · No ritmo atual, a cota deve esgotar antes do reset.',
    nav: 1,
    banner: { level: 'warn', title: 'MiniMax — coleta parcial', body: 'A resposta trouxe apenas o plano MiniMax-M2. As demais cotas não entram nesta coleta.' },
    quotas: [
      { label: 'MiniMax-M2', value: '82%', percent: 82, level: 'warn', reset: 'Reinício: Qui 14/08 00h00 BRT' }
    ]
  }
];

export function Dashboard({ onOpen }) {
  const [minimized, setMinimized] = React.useState({ 'anthropic-sandbox': true });
  const toggle = (id) => setMinimized((m) => Object.assign({}, m, { [id]: !m[id] }));
  return (
    <AppWindowFrame
      title="Usage Monitor"
      dense
      style={{ width: 1030 }}
      footer={
        <AppStatusBar
          left={
            <React.Fragment>
              <span>v38.2.0</span>
              <span>Próxima coleta em 06:41</span>
            </React.Fragment>
          }
          right={
            <React.Fragment>
              <AppButton variant="ghost">Atualizar tudo</AppButton>
              <AppButton variant="ghost" onClick={() => onOpen && onOpen(4)}>Configurações</AppButton>
            </React.Fragment>
          }
        />
      }
    >
      <AppUpdateStrip
        state="ready"
        message="38.3.0 pronta para instalar"
        action={<AppButton variant="ghost">Reiniciar e atualizar agora</AppButton>}
      />
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--s3)', alignItems: 'start' }}>
        {CARDS.map((c) => (
          <Card key={c.id} card={c} minimized={!!minimized[c.id]} onToggle={() => toggle(c.id)} onOpen={onOpen} />
        ))}
      </div>
    </AppWindowFrame>
  );
}
