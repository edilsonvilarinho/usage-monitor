const { AppWindowFrame, AppSettingsNav, AppPanel, AppPanelHeader, AppPanelBody, AppSwitch, AppTextField, AppButton, AppKey, AppValue, AppDataRow, AppSourceMark, AppStatusIndicator, AppBanner, AppSegmentedControl, AppIconButton } = DS;

function Slider({ label, value, min, max, unit, onChange }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s3)' }}>
      <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', width: 190 }}>{label}</span>
      <input type="range" min={min} max={max} value={value} onChange={(e) => onChange(Number(e.target.value))}
        style={{ flex: 1, accentColor: 'var(--fg)' }} />
      <AppValue size="sm" style={{ width: 54, textAlign: 'right' }}>{value}{unit}</AppValue>
    </div>
  );
}

const APIS = [
  { nome: 'Anthropic', source: 'anthropic', origem: 'GET api.anthropic.com/api/oauth/usage', req: '~/.claude/.credentials.json', on: true },
  { nome: 'Codex', source: 'codex', origem: 'GET chatgpt.com/backend-api/codex/usage', req: '~/.codex/auth.json + cap_sid', on: true },
  { nome: 'MiniMax', source: 'minimax', origem: 'GET minimax.io/v1/token_plan/remains', req: 'MINIMAX_API_KEY', on: true },
  { nome: 'DeepSeek', source: 'deepseek', origem: 'GET api.deepseek.com/user/balance', req: 'DEEPSEEK_API_KEY', on: true },
  { nome: 'OpenCode Zen Free', source: 'opencode', origem: 'leitura local de opencode.db', req: 'base local do OpenCode', on: true },
  { nome: 'OpenCode Go', source: 'opencode', origem: 'GET opencode.ai/zen/go/v1/usage', req: 'chave da API do OpenCode', on: true },
  { nome: 'Kilo Free', source: 'kilo', origem: 'leitura local de kilo.db', req: 'base local do Kilo', on: false },
  { nome: 'OpenRouter', source: 'openrouter', origem: 'GET openrouter.ai/api/v1/credits', req: 'chave da API do OpenRouter', on: true }
];

export function Settings() {
  const [sec, setSec] = React.useState('Geral');
  const [opacity, setOpacity] = React.useState(100);
  const [scale, setScale] = React.useState(115);
  const [autoStart, setAutoStart] = React.useState(true);
  const [dark, setDark] = React.useState(true);
  const [lang, setLang] = React.useState('PT');
  const [spike, setSpike] = React.useState('3×');
  const [team, setTeam] = React.useState(false);
  const [apis, setApis] = React.useState(APIS.map((a) => a.on));

  return (
    <AppWindowFrame title="Configurações" style={{ width: 900 }}>
      <div style={{ display: 'flex', border: '1px solid var(--border)', borderRadius: 'var(--r3)', overflow: 'hidden', background: 'var(--surface)', minHeight: 420 }}>
        <AppSettingsNav items={['Geral', 'Alertas', 'APIs', 'Contas', 'Time']} value={sec} onChange={setSec} />
        <div style={{ flex: 1, padding: 'var(--s4)', display: 'flex', flexDirection: 'column', gap: 'var(--s3)', minWidth: 0 }}>
          {sec === 'Geral' ? (
            <React.Fragment>
              <AppSwitch checked={autoStart} onChange={setAutoStart} label="Iniciar com o sistema" hint="Registro Run no Windows, .desktop no Linux, LaunchAgent no macOS." />
              <AppSwitch checked={dark} onChange={setDark} label="Tema escuro" />
              <AppSwitch checked={false} disabled label="Atualização automática"
                reason="Instalação .deb: aqueles arquivos pertencem ao gerenciador de pacotes, e escrever por cima deles produz uma árvore que o próximo apt upgrade desfaz." />
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s3)' }}>
                <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', width: 190 }}>Idioma</span>
                <AppSegmentedControl items={['PT', 'EN']} value={lang} onChange={setLang} />
              </div>
              <Slider label="Opacidade da janela" value={opacity} min={50} max={100} unit="%" onChange={setOpacity} />
              <Slider label="Tamanho da interface" value={scale} min={80} max={150} unit="%" onChange={setScale} />
              <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', borderLeft: '2px solid var(--border)', paddingLeft: 'var(--s3)' }}>
                Mudar a escala redimensiona a janela principal na mesma razão, para caber o mesmo conteúdo.
              </span>
            </React.Fragment>
          ) : null}

          {sec === 'Alertas' ? (
            <React.Fragment>
              <AppKey>Limiares de cota</AppKey>
              <div style={{ display: 'flex', gap: 'var(--s3)' }}>
                <AppTextField label="Atenção" value="75" style={{ width: 90 }} />
                <AppTextField label="Crítico" value="90" style={{ width: 90 }} />
                <AppTextField label="Esgotado" value="100" style={{ width: 90 }} />
              </div>
              {/* O alcance é declarado na tela (issue #194): saldo pré-pago nasce com
                  used = 0 e atividade observada com total = 0, então nessas quatro
                  fontes nenhum limiar é avaliado. A frase sai sempre, inclusive com o
                  alerta desligado — ali ela explica o que ligar não vai cobrir. */}
              <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)' }}>
                O limiar mede percentual contra o teto da cota. Saldo pré-pago não tem teto
                (DeepSeek, OpenRouter) e atividade observada não informa limite
                (OpenCode Zen Free, Kilo Free): nessas fontes nenhum limiar é avaliado.
              </span>
              <AppSwitch checked label="Alertar quando uma sessão CLI saturar" />
              <AppSwitch checked label="Notificação nativa do sistema" />
              {/* Mede distância até o hábito do usuário, não até o teto da cota: um dia
                  três vezes acima do normal não cruza limiar nenhum enquanto estiver
                  longe do limite. Segmentado porque o múltiplo é escolha única. */}
              <AppSwitch checked label="Avisar quando o consumo do dia fugir do habitual" />
              <AppKey>Múltiplo do habitual</AppKey>
              <AppSegmentedControl items={['2×', '3×', '5×']} value={spike} onChange={setSpike} />
              <AppKey>Período de silêncio</AppKey>
              <div style={{ display: 'flex', gap: 'var(--s3)' }}>
                <AppTextField label="De" value="22:00" style={{ width: 110 }} />
                <AppTextField label="Até" value="08:00" style={{ width: 110 }} />
              </div>
            </React.Fragment>
          ) : null}

          {sec === 'APIs' ? (
            <AppPanel>
              <AppPanelHeader title="APIs monitoradas" subtitle="uma linha por integração · remota ou local" />
              <AppPanelBody flush>
                {APIS.map((a, i) => (
                  <AppDataRow key={a.nome} mark={<AppSourceMark source={a.source} />} last={i === APIS.length - 1}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 0, flex: 1, minWidth: 0 }}>
                      <AppValue size="sm">{a.nome}</AppValue>
                      <AppKey>{a.origem}</AppKey>
                    </div>
                    <AppKey dim>{a.req}</AppKey>
                    <AppSwitch checked={apis[i]} onChange={(v) => setApis((s) => s.map((x, j) => (j === i ? v : x)))} />
                  </AppDataRow>
                ))}
              </AppPanelBody>
            </AppPanel>
          ) : null}

          {/* O diálogo da chave, aberto pelo lápis da linha ou ao ligar uma fonte
              sem chave. Não é primitiva própria: é painel sobre o modal do
              sistema. "Testar chave" faz a coleta real pelo repositório da fonte
              (issue #204) e o veredito sai como AppStatusIndicator junto do
              campo — nunca toast, que some antes de ser lido. Âmbar é o terceiro
              veredito: chave válida com plano/assinatura ausente, e também 429 e
              503, em que não houve veredito nenhum sobre a chave. */}
          {sec === 'APIs' ? (
            <AppPanel style={{ maxWidth: 520 }}>
              <AppPanelHeader title="Configurar MiniMax" subtitle="diálogo do lápis · chave nunca vem pré-preenchida" />
              <AppPanelBody>
                <AppKey>A chave é gravada localmente com acesso restrito ao dono.</AppKey>
                <AppTextField label="API key" value="••••••••••••••••" />
                <AppStatusIndicator level="warn">Chave válida, sem plano ativo na MiniMax.</AppStatusIndicator>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--s2)' }}>
                  <AppButton variant="ghost">Remover chave</AppButton>
                  <AppButton variant="ghost">Cancelar</AppButton>
                  <AppButton variant="ghost">Testar chave</AppButton>
                  <AppButton>Salvar</AppButton>
                </div>
              </AppPanelBody>
            </AppPanel>
          ) : null}

          {sec === 'Contas' ? (
            <React.Fragment>
              <AppBanner level="warn" title="1 perfil detectado aguardando confirmação">
                Perfis novos ficam desabilitados até você confirmar. A app apenas monitora: não executa login/logout e não remove arquivos de credenciais.
              </AppBanner>
              <AppPanel>
                <AppPanelHeader title="Contas Anthropic" subtitle="perfil padrão e CLAUDE_CONFIG_DIR adicionais" actions={<AppButton variant="ghost">Adicionar perfil</AppButton>} />
                <AppPanelBody flush>
                  {[
                    ['Padrão', '~/.claude', 'dev@example.com — Example Org', true, 'ok', 'Habilitado'],
                    ['Sandbox', '~/.claude-sandbox', 'qa@example.com — Example Org (Sandbox)', true, 'ok', 'Habilitado'],
                    ['Detectado', '~/.claude-informata', 'identidade não lida ainda', false, 'warn', 'Aguardando']
                  ].map(([nome, caminho, id, on, lvl, word], i) => (
                    <AppDataRow key={nome} mark={<AppSourceMark source="anthropic" />} last={i === 2}>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 0, flex: 1, minWidth: 0 }}>
                        <AppValue size="sm">{nome} · {caminho}</AppValue>
                        <AppKey>{id}</AppKey>
                      </div>
                      <AppStatusIndicator level={lvl}>{word}</AppStatusIndicator>
                      <AppIconButton variant="ghost" glyph="×" label={'Remover ' + nome} />
                    </AppDataRow>
                  ))}
                </AppPanelBody>
              </AppPanel>
            </React.Fragment>
          ) : null}

          {sec === 'Time' ? (
            <React.Fragment>
              <AppSwitch checked={team} onChange={setTeam} label="Integração com time" hint="Recurso opcional, desligado por default. A empresa opera o servidor; não há serviço gerenciado." />
              <AppTextField label="Servidor" placeholder="https://usage.example.com" disabled={!team} />
              <AppTextField label="Chave" type="password" value={team ? '••••••••••••' : ''} disabled={!team}
                hint="Guardada em ~/.usage-monitor/team.json com permissão restrita ao dono — não vai para as preferências do registro." />
              <AppTextField label="Apelido" placeholder="ana" disabled={!team} />
              <AppKey>Contas que participam</AppKey>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--s2)' }}>
                <AppSwitch checked={team} disabled={!team} reason="Ligue a integração com time primeiro." label="dev@example.com — Example Org" />
                <AppSwitch checked={false} disabled={!team} reason="Ligue a integração com time primeiro." label="qa@example.com — Example Org (Sandbox)" />
              </div>
            </React.Fragment>
          ) : null}
        </div>
      </div>
    </AppWindowFrame>
  );
}
