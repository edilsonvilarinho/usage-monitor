/* Propostas da issue #383; dados sintéticos, sem ações de produção. */
const DIRECTIONS = [
  'Resumo e detalhes', 'Abas por tarefa', 'Resumo lateral', 'Tabela e detalhe',
  'Navegação por modelo', 'Seções por pergunta', 'Janelas em destaque',
  'Cotas lado a lado', 'Lista expansível', 'Relatório contínuo'
];
const SETTINGS = { tema: 'Automático', dados: 'Cotas', largura: 'Normal' };
const SHARED = { fg: 'var(--fg)', muted: 'var(--muted)', accent: 'var(--anthropic)' };
const POINTS = [4,11,19,26,34,41,47,52,58,63,69,74,6,12,17,23,29,36,44,51,57,62,66,68];
const WEEKLY = POINTS.map((_, index) => 18 + index);
const MODELS = ['modelo-local-1','modelo-local-2','modelo-local-3','modelo-local-4','modelo-local-5','modelo-local-6','modelo-local-7','modelo-local-8'];
const WINDOWS = Array.from({length:10}, (_, i) => {
  const localDate = new Date(Date.UTC(2026,9,6,11,5)-i*5*3600000);
  const first = `${String(localDate.getUTCDate()).padStart(2,'0')}/10 ${String(localDate.getUTCHours()).padStart(2,'0')}:05`;
  return {
  id:i, inicio:first,
  pico: `${[68,100,74,81,59,92,64,88,100,71][i]} %`,
  esgotou: [1,8].includes(i) ? '3h 12min' : '—', ritmo: `${[23,31,16,17,12,20,14,19,30,16][i]} %/h`
};});
const HOURLY = [2,0,0,0,0,0,0,0,6,18,30,34,22,28,48,40,30,20,12,8,6,4,2,2];

function ScopeMetrics({weekly=false, local=false, model=0}) {
  const entries = local ? [
    ['Últimas 5h', `${24*(model+1)} req`], ['Últimos 7 dias', `${96*(model+1)} req`],
    ['Tipo de medição','Atividade local'], ['Limite','Não informado pela fonte']
  ] : [
    ['Uso atual',weekly?'41 %':'68 %'],['Média por hora',weekly?'1 %/h':'5,6 %/h'],
    ['Janelas no intervalo',weekly?'1':'10 · 2 esgotaram'],
    ['Previsão',weekly?'A janela deve reiniciar antes do limite':'No ritmo observado, a janela deve reiniciar em 2h antes de esgotar']
  ];
  return <div className="hm-metric-rows">{entries.map(([label,value]) => <AppDataRow key={label} hoverable={false}>
    <span className="hm-key">{label}</span><span className="hm-number">{value}</span>
  </AppDataRow>)}</div>;
}

function Metrics({quota,local,model}) {
  const weekly = quota === '7d';
  const values = local ? [
    ['Últimas 5h',`${24*(model+1)} req`,'Atividade observada'],
    ['Últimos 7 dias',`${96*(model+1)} req`,'Sem cota informada'],
    ['Modelo',String(model+1),'de 8 disponíveis']
  ] : [
    ['Uso atual',weekly?'41 %':'68 %',weekly?'Cota semanal':'Cota de 5h'],
    ['Média por hora',weekly?'1 %/h':'5,6 %/h','No intervalo observado'],
    ['Janelas',weekly?'1':'10',weekly?'Sem esgotamento':'2 esgotaram'],
    ['Última coleta','12:00','06/10 · BRT']
  ];
  return <div className="hm-metrics">{values.map(([label,value,hint]) => <AppMetric key={label} label={label} value={value} hint={hint}/>)}</div>;
}

function Chart({quota='Ambas',local=false,model=0,compact=false}) {
  const container = React.useRef(null);
  const [width,setWidth] = React.useState(700);
  React.useEffect(()=>{
    const observer=new ResizeObserver(entries=>{const w=entries[0].contentRect.width;if(w>0)setWidth(w);});
    observer.observe(container.current);
    return ()=>observer.disconnect();
  },[]);
  const [point,setPoint] = React.useState(null);
  const [zoom,setZoom] = React.useState(false);
  const series = local ? POINTS.map((_,i)=>(i+1)*(model+1)) : quota==='7d'?WEEKLY:POINTS;
  const overlay = !local && quota==='Ambas';
  const start = zoom ? 12 : 0;
  const shown = series.slice(start);
  const denominator = local ? 24*(model+1) : 100;
  const x = i => 46 + i/(shown.length-1)*(width-66);
  const y = value => 160 - value/denominator*136;
  const pts = values => values.map((value,i)=>`${x(i)},${y(value)}`).join(' ');
  const chartTitle = local?`${MODELS[model]} · requisições observadas`:'Consumo ao longo do intervalo';
  const index = point == null ? shown.length-1 : Math.min(point,shown.length-1);
  return <div ref={container} className={'hm-chart-wrap '+(compact?'hm-compact':'')}>
    <div className="hm-chart-tools"><span className="hm-key">{chartTitle}</span>
      <AppButton variant="ghost" onClick={()=>{setZoom(!zoom);setPoint(null);}}>{zoom?'Ver intervalo inteiro':'Ampliar trecho'}</AppButton>
    </div>
    <div className="hm-legend"><span><i style={{background:local?'var(--oc)':quota==='7d'?'var(--output)':'var(--anthropic)'}}/>{local?'Requisições':quota==='7d'?'7d':'5h'}</span>
      {overlay && <span><i style={{background:'var(--output)'}}/>7d</span>}
    </div>
    <svg viewBox={`0 0 ${width} 198`} role="img" aria-label={local?'Requisições crescem no período observado; não há limite informado':'Cota de 5h chega a 74%, reinicia e termina em 68%; cota semanal termina em 41%'} onPointerMove={event=>{
      const box=event.currentTarget.getBoundingClientRect();
      const t=(event.clientX-box.left)/box.width*width;
      setPoint(Math.max(0,Math.min(shown.length-1,Math.round((t-46)/(width-66)*(shown.length-1)))));
    }} onPointerLeave={()=>setPoint(null)}>
      {[0,25,50,75,100].map(v=><g key={v}>
        <line x1="46" x2={width-20} y1={y(v/100*denominator)} y2={y(v/100*denominator)} stroke="var(--border)"/>
        <text x="36" y={y(v/100*denominator)+4} textAnchor="end">{local?Math.round(v/100*denominator):v}</text>
      </g>)}
      {!local&&!zoom&&quota!=='7d'&&<g><line x1={x(12)} x2={x(12)} y1="22" y2="160" stroke="var(--muted)" strokeDasharray="3 4"/><text x={x(12)} textAnchor="middle" y="16">Reinício</text></g>}
      <polyline points={pts(shown)} fill="none" stroke={local?'var(--oc)':quota==='7d'?'var(--output)':'var(--anthropic)'} strokeWidth="2"/>
      {overlay&&<polyline points={pts(WEEKLY.slice(start))} fill="none" stroke="var(--output)" strokeWidth="2"/>}
      <circle cx={x(index)} cy={y(shown[index])} r="4" fill={local?'var(--oc)':quota==='7d'?'var(--output)':'var(--anthropic)'}/>
      {[0,shown.length-1].map((i,j)=><text key={i} x={x(i)} y="184" textAnchor={j===0?'start':'end'}>{['Início','Última coleta'][j]}</text>)}
    </svg>
    <div className="hm-chart-detail" aria-live="polite">{point==null?'Última coleta':`Leitura ${index+start+1}`} · {shown[index]} {local?'req':'%'}{overlay?` · Semanal ${WEEKLY[index+start]} %`:''}</div>
  </div>;
}

function Panel({title,subtitle,children,status,actions}) {
  return <AppPanel><AppPanelHeader title={title} subtitle={subtitle} status={status} actions={actions}/><AppPanelBody>{children}</AppPanelBody></AppPanel>;
}

function WindowTable({local=false,all=false}) {
  const [full,setFull] = React.useState(all);
  if(local) return <Panel title="Atividade por modelo" subtitle="Contagem local · sem limite informado">
    <AppDataTable columns={[{key:'nome',label:'Modelo'},{key:'curta',label:'5h',numeric:true},{key:'semanal',label:'7 dias',numeric:true}]}
      rows={MODELS.map((nome,i)=>({id:i,nome,curta:`${24*(i+1)} req`,semanal:`${96*(i+1)} req`}))}/>
  </Panel>;
  return <Panel title="Janelas de 5h" subtitle={full?'10 janelas no intervalo':'8 mais recentes de 10 · BRT'} actions={<AppButton variant="ghost" onClick={()=>setFull(!full)}>{full?'Mostrar recentes':'Ver todas'}</AppButton>}>
    <AppDataTable columns={[{key:'inicio',label:'Início observado'},{key:'pico',label:'Pico',numeric:true},{key:'esgotou',label:'Esgotou em',numeric:true},{key:'ritmo',label:'Ritmo',numeric:true}]} rows={WINDOWS.slice(0,full?10:8)}/>
    <span className="hm-caption">O início é a primeira leitura observada; “—” indica que não esgotou.</span>
  </Panel>;
}

function Hourly({local=false}) {
  const container=React.useRef(null);
  const [width,setWidth]=React.useState(700);
  React.useEffect(()=>{
    if(!container.current)return;
    const observer=new ResizeObserver(entries=>{const w=entries[0].contentRect.width;if(w>0)setWidth(w);});
    observer.observe(container.current);
    return ()=>observer.disconnect();
  },[local]);
  if(local) return <Panel title="Sobre esta medição"><p className="hm-prose">Requisições observadas no índice local. A fonte não informa cota, percentual disponível ou previsão de reinício.</p></Panel>;
  return <Panel title="Consumo por hora do dia" subtitle="Distribuição em BRT · intervalo observado">
    <div ref={container}><svg className="hm-hourly" viewBox={`0 0 ${width} 90`} role="img" aria-label="Distribuição horária com maior consumo às 14h">
      {HOURLY.map((v,i)=><rect key={i} x={i*(width-24)/24+12} y={60-v} width={(width-24)/24*.65} height={v} fill="var(--anthropic)"/>)}
      {[0,6,12,18,23].map(h=><text key={h} x={h*(width-24)/24+16} y="82" textAnchor="middle">{h}h</text>)}
    </svg></div>
    <span className="hm-caption">Maior consumo observado às 14h BRT.</span>
  </Panel>;
}

function Detail({quota,local,model,chart=true}) {
  return <div className="hm-stack">
    {chart&&<Panel title={local?MODELS[model]:'Evolução do consumo'} subtitle={local?'Atividade local':quota==='Ambas'?'Cotas de 5h e 7d':`Cota de ${quota}`}><Chart quota={quota} local={local} model={model}/></Panel>}
    <div className={quota==='Ambas'&&!local?'hm-two':'hm-stack'}>
      <Panel title={local?'Resumo da atividade':quota==='7d'?'Resumo semanal':'Resumo de 5h'}><ScopeMetrics local={local} weekly={quota==='7d'} model={model}/></Panel>
      {quota==='Ambas'&&!local&&<Panel title="Resumo semanal"><ScopeMetrics weekly/></Panel>}
    </div>
  </div>;
}

function Direction({number}) {
  const [quota,setQuota] = React.useState('Ambas');
  const [range,setRange] = React.useState('7 dias');
  const [tab,setTab] = React.useState('Visão geral');
  const [model,setModel] = React.useState(0);
  const [selected,setSelected] = React.useState('5h');
  const [settings,setSettings] = React.useState({...SETTINGS});
  const [outcome,setOutcome] = React.useState('');
  const [account,setAccount] = React.useState('Padrão');
  React.useEffect(()=>{
    const listener=()=>setSettings({...SETTINGS});
    document.getElementById('history-directions-383').addEventListener('hm-settings',listener);
    return ()=>document.getElementById('history-directions-383').removeEventListener('hm-settings',listener);
  },[]);
  const local=settings.dados==='Atividade local';
  const empty=settings.dados==='Sem dados';
  const detail=<Detail quota={quota} local={local} model={model}/>;
  const metrics=<Metrics quota={quota} local={local} model={model}/>;
  const windows=<WindowTable local={local}/>;
  const hourly=<Hourly local={local}/>;
  const status=local?<AppStatusIndicator level="info">Atividade local</AppStatusIndicator>:<AppStatusIndicator level="ok">Normal</AppStatusIndicator>;
  const summaries=local?MODELS:['Cota de 5h','Cota semanal'];
  let content;
  if(empty) content=<AppPanel><AppEmptyState message="Nenhuma coleta salva neste intervalo. Atualize a fonte para iniciar o histórico."/></AppPanel>;
  else if(number===0) content=<>{metrics}<Panel title="Evolução do consumo" subtitle={local?'Requisições por modelo':'5h e 7d · legenda explícita'}><Chart quota={quota} local={local} model={model}/></Panel>
    <details className="hm-expander" open><summary>Resumo das {local?'requisições':'cotas'}</summary><Detail quota={quota} local={local} model={model} chart={false}/></details>
    <details className="hm-expander"><summary>{local?'Todos os modelos':'Janelas e distribuição horária'}</summary><div className="hm-stack">{windows}{hourly}</div></details></>;
  else if(number===1) content=<><AppTabs items={['Visão geral',local?'Modelos':'Janelas','Distribuição']} value={tab} onChange={setTab}/>
    {tab==='Visão geral'?<>{metrics}{detail}</>:tab==='Distribuição'?hourly:windows}</>;
  else if(number===2) content=<><div className="hm-sidebar-right"><Panel title="Evolução do consumo"><Chart quota={quota} local={local} model={model}/></Panel>
    <Panel title="Resumo do intervalo" status={status}><ScopeMetrics local={local} weekly={quota==='7d'} model={model}/></Panel></div>{windows}{hourly}</>;
  else if(number===3) content=<><Panel title={local?'Modelos no intervalo':'Cotas no intervalo'} subtitle="Selecionar uma linha abre sua análise">
    {summaries.map((name,i)=><AppDataRow key={name} hoverable={false}><span className="hm-series-name">{name}</span><span className="hm-number">{local?`${24*(i+1)} req`:i===0?'68 %':'41 %'}</span>
    <AppButton variant={selected===String(i)?'default':'ghost'} onClick={()=>{setSelected(String(i));setModel(i);}}>Analisar</AppButton></AppDataRow>)}</Panel>
    <Detail quota={local?quota:selected==='1'?'7d':'5h'} local={local} model={model}/>
    <details className="hm-expander"><summary>{local?'Todos os modelos':'Janelas observadas'}</summary>{windows}</details>{hourly}</>;
  else if(number===4) content=<div className="hm-sidebar-left"><Panel title={local?'Modelos':'Famílias de cota'}>
    {summaries.map((name,i)=><AppButton key={name} fullWidth variant={model===i?'default':'ghost'} onClick={()=>setModel(i)}>{name}</AppButton>)}</Panel>
    <div className="hm-stack"><Detail quota={local?quota:model===1?'7d':'5h'} local={local} model={model}/>
    <details className="hm-expander"><summary>{local?'Todos os modelos':'Janelas observadas'}</summary>{windows}</details>{hourly}</div></div>;
  else if(number===5) content=<><div className="hm-question"><h3>{local?'Quanto foi observado?':'Quanto está em uso?'}</h3>{metrics}</div>
    <div className="hm-question"><h3>Como variou no intervalo?</h3><Panel title="Evolução"><Chart quota={quota} local={local} model={model}/></Panel></div>
    <div className="hm-question"><h3>{local?'Quais modelos foram usados?':'Quais janelas esgotaram?'}</h3>{windows}</div>
    <details className="hm-expander"><summary>Consultar métricas detalhadas</summary><Detail quota={quota} local={local} model={model} chart={false}/></details>
    <div className="hm-question"><h3>{local?'O que esta medida significa?':'Em quais horários houve consumo?'}</h3>{hourly}</div></>;
  else if(number===6) content=<>{metrics}{windows}<details className="hm-expander" open><summary>Consultar evolução e resumo</summary>{detail}</details>{hourly}</>;
  else if(number===7) content=<>{local?<><div className="hm-two">{[0,1].map(i=><Panel key={i} title={MODELS[i]}><Chart local model={i} compact/><ScopeMetrics local model={i}/></Panel>)}</div>{windows}</>:<>
    <div className="hm-two">{['5h','7d'].filter(q=>quota==='Ambas'||quota===q).map(q=><Panel key={q} title={q==='5h'?'Cota intervalar · 5h':'Cota semanal · 7d'}><Chart quota={q} compact/><ScopeMetrics weekly={q==='7d'}/></Panel>)}</div>{windows}{hourly}</>}</>;
  else if(number===8) content=<>{summaries.map((name,i)=><details className="hm-expander" key={name} open={i===0}>
    <summary><span>{name}</span><span>{local?`${24*(i+1)} req`:i===0?'68 %':'41 %'}</span></summary>
    <Detail quota={local?quota:i===0?'5h':'7d'} local={local} model={i}/></details>)}
    <details className="hm-expander"><summary>{local?'Todos os modelos':'Janelas observadas'}</summary>{windows}</details>{hourly}</>;
  else content=<><Panel title="Resumo do intervalo" status={status}><ScopeMetrics local={local} weekly={quota==='7d'} model={model}/></Panel>
    <Panel title="Evolução do consumo"><Chart quota={quota} local={local} model={model}/></Panel>
    {quota==='Ambas'&&!local&&<Panel title="Resumo semanal"><ScopeMetrics weekly/></Panel>}{windows}{hourly}</>;
  const theme=settings.tema==='Escuro'?'dark':settings.tema==='Claro'?'light':undefined;
  return <div className="hm-product" data-app-theme={theme} data-width={settings.largura}>
    <AppWindowFrame title={`Histórico · ${local?'OpenCode Zen Free':`Anthropic · ${account}`}`} chrome={false} footer={<AppStatusBar style={{height:'auto',minHeight:'var(--h-statusbar)',flexWrap:'wrap'}} left="Última coleta · 06/10/2026 12:00 BRT" right="Dados sintéticos"/>}>
      <div className="hm-heading"><div><h2>Histórico de uso</h2><span className="hm-caption">{local?'OpenCode Zen Free · requisições observadas':`Anthropic · ${account}`}</span></div>{status}</div>
      <div className="hm-commands">
        <AppToolbar style={{height:'auto',minHeight:'var(--h-toolbar)',flexWrap:'wrap',overflowX:'visible',padding:'var(--s1) var(--s2)'}}>
          {!local&&<label className="hm-account"><span className="hm-key">Conta</span><select value={account} onChange={e=>setAccount(e.target.value)} aria-label="Conta">
            <option>Padrão</option><option>Conta com identificação longa · engenharia de plataforma</option></select></label>}
          <div className="hm-filter"><span className="hm-key">Intervalo</span><AppSegmentedControl items={['24h','7 dias','30 dias','Total']} value={range} onChange={setRange}/></div>
          {!local&&<div className="hm-filter"><span className="hm-key">Cota</span><AppSegmentedControl items={['5h','7d','Ambas']} value={quota} onChange={setQuota}/></div>}
          <AppButton disabled={empty} onClick={()=>setOutcome('Prévia do relatório · nenhuma gravação realizada')}>Relatório PDF</AppButton>
          {empty&&<span className="hm-caption">PDF indisponível: não há dados.</span>}
        </AppToolbar>
        <span className="hm-context">{range} · {local?'Atividade local':quota==='Ambas'?'Cotas de 5h e 7d':`Cota de ${quota}`} · BRT</span>
      </div>
      <div className="hm-content">{content}</div>
      {outcome&&<Panel title="Prévia do relatório" actions={<AppButton variant="ghost" onClick={()=>setOutcome('')}>Fechar</AppButton>}>
        <span className="hm-caption" role="status">{outcome}</span><ScopeMetrics local={local} weekly={quota==='7d'} model={model}/></Panel>}
    </AppWindowFrame>
  </div>;
}

const galleryRoot=document.getElementById('history-directions-383');
function applySettings() {
  galleryRoot.dispatchEvent(new Event('hm-settings'));
  if(window.openai?.setWidgetState) window.openai.setWidgetState({modelContent:{issue:383,settings:{...SETTINGS}},privateContent:null}).catch(()=>{});
}
if(window.openai?.widgetState?.modelContent?.settings) Object.assign(SETTINGS,window.openai.widgetState.modelContent.settings);
galleryRoot.querySelectorAll('[data-mock-root]').forEach((element,index)=>ReactDOM.createRoot(element).render(<Direction number={index}/>));
window.addEventListener('openai:set_globals',event=>{
  const saved=event.detail?.globals?.widgetState?.modelContent?.settings;
  if(saved){Object.assign(SETTINGS,saved);galleryRoot.dispatchEvent(new Event('hm-settings'));}
});
if(globalThis.Tweak) {
  const tweak=new Tweak({container:galleryRoot.querySelector('[data-design-settings]'),onChange:applySettings});
  tweak.addSelect(SETTINGS,'tema',{label:'Tema',options:['Automático','Escuro','Claro']});
  tweak.addSelect(SETTINGS,'dados',{label:'Dados',options:['Cotas','Atividade local','Sem dados']});
  tweak.addSelect(SETTINGS,'largura',{label:'Largura',options:['Normal','Estreita']});
}
/* A galeria de revisão standalone expõe estes controles fora da UI proposta. */
globalThis.issue383Gallery={settings:SETTINGS,applySettings,names:DIRECTIONS};
