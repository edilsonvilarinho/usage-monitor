const { AppWindowFrame, AppPanel, AppPanelHeader, AppPanelBody, AppDataRow, AppKey, AppValue, AppStatusIndicator, AppSourceMark, AppColumnHeader, AppButton, AppToolbar, AppMetric } = DS;

const PEOPLE = [
  { apelido: 'ana', conta: 'dev@example.com', maquina: 'MB-ANA', estado: 'working', desde: '09h12', ultimo: 'há 40s', sessao: 'api-gateway · feat/rate-limit', cor: 'var(--anthropic)' },
  { apelido: 'bruno', conta: 'dev@example.com', maquina: 'DESKTOP-B2', estado: 'working', desde: '08h47', ultimo: 'há 2min', sessao: 'billing-svc · main', cor: 'var(--codex)' },
  { apelido: 'dev-01', conta: 'dev@example.com', maquina: 'DESKTOP-A1', estado: 'connected', desde: '07h55', ultimo: 'há 22min', sessao: '—', cor: 'var(--deepseek)' },
  { apelido: 'carla', conta: 'qa@example.com', maquina: 'MB-CARLA', estado: 'connected', desde: '10h03', ultimo: 'há 1h 12min', sessao: '—', cor: 'var(--minimax)' },
  { apelido: 'edu', conta: 'qa@example.com', maquina: 'DESKTOP-E5', estado: 'off', desde: '—', ultimo: 'ontem 18h31', sessao: '—', cor: 'var(--kilo)' }
];

const LABEL = { working: ['info', 'Trabalhando agora'], connected: ['ok', 'Conectado'], off: ['off', 'Desconectado'] };
const COLS = [{ label: 'Integrante', width: 86 }, { label: 'Conta', width: 152 }, { label: 'Máquina', width: 108 }, { label: 'Sessão atual', flex: 2, width: 150 }, { label: 'Desde', width: 54, align: 'right' }, { label: 'Último turno', width: 88, align: 'right' }, { label: 'Estado', width: 132, align: 'right' }];

export function Presence() {
  return (
    <AppWindowFrame title="Conectados agora — todas as contas" style={{ width: 1030 }}>
      <AppToolbar>
        <AppKey>batida a cada 30s · turno nos últimos 5 min = trabalhando</AppKey>
        <span style={{ flex: 1 }} />
        <AppButton variant="ghost">Atualizar</AppButton>
      </AppToolbar>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 'var(--s3)' }}>
        <AppMetric label="Trabalhando agora" value="2" />
        <AppMetric label="Conectados" value="4" hint="Usage Monitor aberto na máquina" />
        <AppMetric label="Contas monitoradas" value="2" />
      </div>
      <AppPanel>
        <AppPanelHeader title="Presença do time" subtitle="conectado e trabalhando são dois estados diferentes" />
        <AppPanelBody style={{ paddingBottom: 0 }}>
          <AppColumnHeader items={COLS} offset={6} style={{ padding: 0 }} />
        </AppPanelBody>
        <AppPanelBody flush>
          {PEOPLE.map((p, i) => {
            const [lvl, word] = LABEL[p.estado];
            return (
              <AppDataRow key={p.apelido} mark={<AppSourceMark color={p.cor} />} last={i === PEOPLE.length - 1} style={{ gap: 'var(--s2)' }}>
                <AppValue size="sm" style={{ width: 86, flex: 'none' }}>{p.apelido}</AppValue>
                <AppValue size="sm" dim style={{ width: 152, flex: 'none' }}>{p.conta}</AppValue>
                <AppValue size="sm" dim style={{ width: 108, flex: 'none' }}>{p.maquina}</AppValue>
                <AppValue size="sm" style={{ flex: 2, minWidth: 150 }}>{p.sessao}</AppValue>
                <AppValue size="sm" style={{ width: 54, flex: 'none', textAlign: 'right' }}>{p.desde}</AppValue>
                <AppValue size="sm" style={{ width: 88, flex: 'none', textAlign: 'right' }}>{p.ultimo}</AppValue>
                <span style={{ width: 132, flex: 'none', display: 'flex', justifyContent: 'flex-end' }}>
                  <AppStatusIndicator level={lvl}>{word}</AppStatusIndicator>
                </span>
              </AppDataRow>
            );
          })}
        </AppPanelBody>
      </AppPanel>
      <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', borderLeft: '2px solid var(--border)', paddingLeft: 'var(--s3)' }}>
        Não trafega conteúdo de prompt nem de resposta. Só metadados de uso: id de sessão, id de mensagem, timestamp, modelo, contagem de tokens, diretório do projeto, branch e nome da máquina.
      </span>
    </AppWindowFrame>
  );
}
