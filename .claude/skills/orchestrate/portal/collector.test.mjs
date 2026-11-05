import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { Coletor, atividadeDe, montarEstado, resumir } from './collector.mjs';
import { SESSAO, bTexto, bTool, criarAmbiente, gravarJsonl, linhaAssistant, linhaResultado, metaDe, popularSessao } from './testutil.mjs';

function coletar(amb, extra = {}) {
  const c = new Coletor({ sessao: SESSAO, raiz: amb.raiz, projetos: amb.projetos, ...extra });
  c.poll();
  return c;
}

test('sem transcript o estado vem vazio com apenas a AM e campos null', () => {
  const amb = criarAmbiente();
  try {
    const c = new Coletor({ sessao: 'sessao-inexistente', raiz: amb.raiz, projetos: amb.projetos });
    c.poll();
    const e = c.estado();
    assert.equal(e.agentes.length, 1);
    assert.equal(e.agentes[0].id, 'am');
    assert.equal(e.agentes[0].modelo, null);
    assert.equal(e.agentes[0].inicio, null);
    assert.deepEqual(e.tarefas, []);
    assert.deepEqual(e.falas, []);
    assert.equal(e.totais.agentesAtivos, 0);
  } finally { amb.limpar(); }
});

test('tokens somam usage sem contar duas vezes a mesma message.id; tipo e modelo literais', () => {
  const amb = criarAmbiente();
  try {
    popularSessao(amb);
    const e = coletar(amb).estado();
    const por = Object.fromEntries(e.agentes.map((a) => [a.id, a]));
    assert.deepEqual(por.am.tokens, { entrada: 10, saida: 20, cache: 150 });
    assert.deepEqual(por.aaa111.tokens, { entrada: 9, saida: 17, cache: 26 });
    assert.deepEqual(por.bbb222.tokens, { entrada: 2, saida: 4, cache: 6 });
    assert.equal(por.aaa111.tipo, 'backend-dev');
    assert.equal(por.bbb222.tipo, 'outro');
    assert.equal(por.bbb222.tipoOriginal, 'Explore');
    assert.equal(por.aaa111.modelo, 'claude-sonnet-5-5');
    assert.equal(por.bbb222.modelo, 'claude-opus-5-5');
    assert.equal(por.aaa111.tarefa, 'B11');
    assert.equal(por.bbb222.tarefa, null);
    assert.deepEqual(e.totais, { tokensEntrada: 21, tokensSaida: 41, tokensCache: 182, agentesAtivos: 0 });
  } finally { amb.limpar(); }
});

test('estados: handback conclui, killed vira erro; contagem de ferramentas e erros', () => {
  const amb = criarAmbiente();
  try {
    popularSessao(amb);
    const e = coletar(amb).estado();
    const por = Object.fromEntries(e.agentes.map((a) => [a.id, a]));
    assert.equal(por.aaa111.estado, 'concluido');
    assert.equal(por.aaa111.ferramentas, 3);
    assert.equal(por.aaa111.erros, 1);
    assert.equal(por.aaa111.atividade, 'concluído');
    assert.notEqual(por.aaa111.fim, null);
    assert.equal(por.bbb222.estado, 'erro');
    assert.equal(por.am.estado, 'ocioso');
  } finally { amb.limpar(); }
});

test('agente com evento recente está trabalhando e a atividade vem em português', () => {
  const amb = criarAmbiente();
  try {
    const agora = Date.now();
    gravarJsonl(amb.main, [
      linhaAssistant({ t: agora - 5000, id: 'm1', model: 'claude-opus-5-5', blocos: [bTool('tu-a', 'Agent', { subagent_type: 'frontend-dev', description: 'F9 tela', prompt: 'Tarefa F9' })] }),
      linhaResultado({ t: agora - 4900, toolUseId: 'tu-a', extra: { toolUseResult: { agentId: 'ccc333', status: 'async_launched' } } }),
    ]);
    gravarJsonl(path.join(amb.dirSub, 'agent-ccc333.jsonl'), [
      linhaAssistant({ t: agora - 1000, id: 's1', agentId: 'ccc333', blocos: [bTool('t1', 'Edit', { file_path: 'C:\\r\\frontend\\src\\App.vue' })] }),
    ]);
    const e = coletar(amb).estado({ agora });
    const a = e.agentes.find((x) => x.id === 'ccc333');
    assert.equal(a.estado, 'trabalhando');
    assert.equal(a.atividade, 'editando App.vue');
    assert.equal(a.tipo, 'frontend-dev');
    assert.equal(e.totais.agentesAtivos >= 1, true);
    // 30 s depois, com a ferramenta ainda aberta: aguardando; 5 min depois: ocioso
    assert.equal(coletar(amb).estado({ agora: agora + 30_000 }).agentes.find((x) => x.id === 'ccc333').estado, 'aguardando');
    const tarde = coletar(amb).estado({ agora: agora + 300_000 }).agentes.find((x) => x.id === 'ccc333');
    assert.equal(tarde.estado, 'aguardando'); // sem `finished` nem notificação: nunca ocioso
  } finally { amb.limpar(); }
});

test('arquivo de subagente novo é lido no poll seguinte, sem reiniciar', () => {
  const amb = criarAmbiente();
  try {
    popularSessao(amb);
    const c = coletar(amb);
    assert.equal(c.estado().agentes.length, 3);
    gravarJsonl(path.join(amb.dirSub, 'agent-ddd444.jsonl'), [
      linhaAssistant({ t: Date.now(), id: 'n1', agentId: 'ddd444', entrada: 4, saida: 5, blocos: [bTool('x1', 'Glob', { pattern: '**/*.ts' })] }),
    ]);
    fs.writeFileSync(path.join(amb.dirSub, 'agent-ddd444.meta.json'), JSON.stringify(metaDe('test-runner', 'Rodar testes', 'tu-z', false)));
    assert.equal(c.poll(), true);
    const e = c.estado();
    assert.equal(e.agentes.length, 4);
    const novo = e.agentes.find((a) => a.id === 'ddd444');
    assert.equal(novo.tipo, 'test-runner');
    assert.equal(novo.descricao, 'Rodar testes');
    assert.deepEqual(novo.tokens, { entrada: 4, saida: 5, cache: 0 });
    assert.equal(novo.estado, 'trabalhando');
  } finally { amb.limpar(); }
});

test('linha parcial só é consumida quando completa; linha inválida é ignorada', () => {
  const amb = criarAmbiente();
  try {
    const linha = JSON.stringify(linhaAssistant({ t: Date.now(), id: 'p1', model: 'claude-opus-5-5', entrada: 7, saida: 8, blocos: [bTexto('oi')] }));
    fs.writeFileSync(amb.main, 'lixo que não é json\n' + linha.slice(0, 40));
    const c = coletar(amb);
    assert.deepEqual(c.estado().agentes[0].tokens, { entrada: 0, saida: 0, cache: 0 });
    fs.appendFileSync(amb.main, linha.slice(40) + '\n');
    c.poll();
    assert.deepEqual(c.estado().agentes[0].tokens, { entrada: 7, saida: 8, cache: 0 });
    assert.equal(c.estado().agentes[0].modelo, 'claude-opus-5-5');
  } finally { amb.limpar(); }
});

test('falas: delegação, retorno, encerramento e devolução (mensagem a agente já encerrado)', () => {
  const amb = criarAmbiente();
  try {
    const t0 = popularSessao(amb);
    gravarJsonl(amb.main, [
      linhaAssistant({ t: t0 + 200_000, id: 'm9', model: 'claude-opus-5-5', blocos: [bTool('sm1', 'SendMessage', { to: 'aaa111', message: 'Tarefa DEVOLVIDA: corrija o teste' })] }),
      linhaAssistant({ t: t0 + 201_000, id: 'm10', model: 'claude-opus-5-5', blocos: [bTool('sm2', 'SendMessage', { to: 'aaa111', message: 'limite de uso renovado, continue\nsegunda linha: devolvida' })] }),
    ], true);
    const falas = coletar(amb).estado().falas;
    const tipos = falas.map((f) => f.tipo);
    assert.equal(tipos.filter((t) => t === 'delegacao').length, 2);
    assert.ok(falas.some((f) => f.tipo === 'retorno' && f.de === 'aaa111' && f.para === 'am' && f.texto === 'Retornou: CONCLUÍDA B11.' && f.tarefa === 'B11'));
    assert.ok(falas.some((f) => f.tipo === 'devolucao' && f.para === 'aaa111' && f.texto.startsWith('Devolveu: Tarefa DEVOLVIDA')));
    // "devolvida" só na segunda linha: não é devolução
    assert.ok(falas.some((f) => f.tipo === 'fala' && f.para === 'aaa111' && f.texto.startsWith('Retomou: limite de uso renovado')));
    assert.equal(falas.filter((f) => f.tipo === 'devolucao').length, 1);
    assert.ok(falas.some((f) => f.tipo === 'atividade' && f.de === 'bbb222'));
    for (const f of falas) assert.ok(f.texto.length <= 240);
    const ts = falas.map((f) => f.ts);
    assert.deepEqual(ts, [...ts].sort());
  } finally { amb.limpar(); }
});

test('eventos da AM (emit): tarefa, devolução contada, veredito, fala', () => {
  const amb = criarAmbiente();
  try {
    const esc = path.join(amb.raiz, '.claude', 'escritorio');
    fs.mkdirSync(esc, { recursive: true });
    const t = Date.now();
    gravarJsonl(path.join(esc, `${SESSAO}.jsonl`), [
      { ts: new Date(t).toISOString(), tipo: 'tarefa', id: 'B11', titulo: 'Coleta', dono: 'backend-dev', estado: 'andamento' },
      { ts: new Date(t + 1).toISOString(), tipo: 'veredito', tarefa: 'B11', resultado: 'devolvida', texto: 'teste falha' },
      { ts: new Date(t + 2).toISOString(), tipo: 'tarefa', id: 'B11', titulo: 'Coleta', dono: 'backend-dev', estado: 'andamento' },
      { ts: new Date(t + 3).toISOString(), tipo: 'veredito', tarefa: 'B11', resultado: 'aprovada', texto: 'ok' },
      { ts: new Date(t + 4).toISOString(), tipo: 'tarefa', id: 'X', titulo: 'inválida', dono: 'a', estado: 'invalido' },
      { ts: new Date(t + 5).toISOString(), tipo: 'fala', de: 'am', para: 'backend-dev', tarefa: 'B11', tipoFala: 'fala', texto: 'siga' },
    ]);
    const e = coletar(amb).estado();
    assert.equal(e.tarefas.length, 1);
    assert.deepEqual({ ...e.tarefas[0], atualizadoEm: null }, { id: 'B11', titulo: 'Coleta', dono: 'backend-dev', estado: 'aprovada', devolucoes: 1, veredito: 'ok', atualizadoEm: null });
    assert.deepEqual(e.falas.map((f) => f.tipo), ['veredito', 'veredito', 'fala']);
  } finally { amb.limpar(); }
});

test('replay: o estado em um instante anterior mostra menos agentes e tokens', () => {
  const amb = criarAmbiente();
  try {
    const t0 = popularSessao(amb);
    const c = coletar(amb);
    const cedo = c.estado({ agora: t0 + 150, ate: t0 + 150, modo: 'replay' });
    assert.equal(cedo.modo, 'replay');
    assert.equal(cedo.agentes.length, 3);
    assert.ok(cedo.agentes.every((a) => a.estado !== 'concluido' && a.estado !== 'erro'));
    const lim = c.limites();
    const fim = c.estado({ agora: lim.max, ate: lim.max, modo: 'replay' });
    assert.equal(fim.agentes.find((a) => a.id === 'aaa111').estado, 'concluido');
    assert.ok(fim.totais.tokensSaida > cedo.totais.tokensSaida);
  } finally { amb.limpar(); }
});

test('detalhe: prompt integral, passos, retorno', () => {
  const amb = criarAmbiente();
  try {
    popularSessao(amb);
    const d = coletar(amb).detalhe('aaa111');
    assert.equal(d.prompt, 'Tarefa B11. faça isto');
    assert.deepEqual(d.passos.map((p) => p.tipo), ['ferramenta', 'texto', 'resultado', 'ferramenta', 'resultado', 'ferramenta', 'resultado']);
    assert.equal(d.passos.find((p) => p.erro).ferramenta, 'Bash');
    assert.equal(d.retorno, 'CONCLUÍDA B11.\nlinha 2');
    assert.equal(coletar(amb).detalhe('nao-existe'), null);
  } finally { amb.limpar(); }
});

test('atividadeDe e resumir', () => {
  assert.equal(atividadeDe('Read', { file_path: 'C:\\a\\b\\tokens.css' }), 'lendo tokens.css');
  assert.equal(atividadeDe('Bash', { command: 'npm test\noutra' }), 'rodando npm test');
  assert.equal(atividadeDe('Write', { file_path: '/x/p1.html' }), 'criando p1.html');
  assert.equal(atividadeDe('mcp__srv__ferramenta', {}), 'usando ferramenta');
  assert.equal(resumir('a'.repeat(300), 240).length, 240);
  assert.equal(resumir('  a \n b  '), 'a b');
});

test('montarEstado não quebra com eventos vazios', () => {
  const e = montarEstado({ eventos: [], metas: new Map(), sessao: 'x', agora: 0 });
  assert.equal(e.agentes[0].id, 'am');
});

function sessaoSemFim(amb, agora, { finished = false } = {}) {
  gravarJsonl(amb.main, [
    linhaAssistant({ t: agora - 600_000, id: 'm1', model: 'claude-opus-5-5', blocos: [bTool('tu-p', 'Agent', { subagent_type: 'prototype-dev', description: 'PV1 gerar protótipo', prompt: 'Tarefa PV1' })] }),
    linhaResultado({ t: agora - 599_900, toolUseId: 'tu-p', extra: { toolUseResult: { agentId: 'ppp111', status: 'async_launched' } } }),
  ]);
  gravarJsonl(path.join(amb.dirSub, 'agent-ppp111.jsonl'), [
    linhaAssistant({ t: agora - 590_000, id: 's1', agentId: 'ppp111', blocos: [bTool('r1', 'Read', { file_path: 'x/BRIEFING.md' })] }),
    linhaResultado({ t: agora - 589_000, toolUseId: 'r1', agentId: 'ppp111' }),
  ]);
  fs.writeFileSync(path.join(amb.dirSub, 'agent-ppp111.meta.json'), JSON.stringify(metaDe('prototype-dev', 'PV1 gerar protótipo', 'tu-p', finished)));
}

test('subagente sem finished e sem notificação de fim nunca é ocioso: trabalhando, pensando, com ultimoEvento', () => {
  const amb = criarAmbiente();
  try {
    const agora = Date.now();
    sessaoSemFim(amb, agora);
    const a = coletar(amb).estado({ agora }).agentes.find((x) => x.id === 'ppp111');
    assert.equal(a.estado, 'trabalhando');
    assert.equal(a.atividade, 'pensando ou gerando resposta');
    assert.equal(a.ultimoEvento, new Date(agora - 589_000).toISOString());
    assert.equal(a.fim, null);
    assert.equal(coletar(amb).estado({ agora }).totais.agentesAtivos >= 1, true);
  } finally { amb.limpar(); }
});

test('subagente sem fim vira erro só quando a sessão principal parou; com finished segue concluído', () => {
  const amb = criarAmbiente();
  try {
    const agora = Date.now();
    sessaoSemFim(amb, agora);
    const tarde = coletar(amb).estado({ agora: agora + 40 * 60_000 }).agentes.find((x) => x.id === 'ppp111');
    assert.equal(tarde.estado, 'erro');
    assert.equal(tarde.atividade, 'sessão encerrada sem concluir');
    fs.writeFileSync(path.join(amb.dirSub, 'agent-ppp111.meta.json'), JSON.stringify(metaDe('prototype-dev', 'PV1 gerar protótipo', 'tu-p', true)));
    const ok = coletar(amb).estado({ agora: agora + 40 * 60_000 }).agentes.find((x) => x.id === 'ppp111');
    assert.equal(ok.estado, 'concluido');
  } finally { amb.limpar(); }
});

test('fala com de/para igual ao tipo resolve para o agente dono da tarefa', () => {
  const amb = criarAmbiente();
  try {
    const agora = Date.now();
    sessaoSemFim(amb, agora);
    const esc = path.join(amb.raiz, '.claude', 'escritorio');
    fs.mkdirSync(esc, { recursive: true });
    const ts = (d) => new Date(agora + d).toISOString();
    gravarJsonl(path.join(esc, `${SESSAO}.jsonl`), [
      { ts: ts(-1000), tipo: 'tarefa', id: 'PV1', titulo: 'Protótipo', dono: 'prototype-dev', estado: 'andamento' },
      { ts: ts(-900), tipo: 'fala', de: 'am', para: 'prototype-dev', tarefa: 'PV1', tipoFala: 'fala', texto: 'siga' },
      { ts: ts(-800), tipo: 'fala', de: 'prototype-dev', para: 'am', tarefa: 'PV1', tipoFala: 'retorno', texto: 'feito' },
      { ts: ts(-700), tipo: 'fala', de: 'am', para: 'prototype-dev', tarefa: null, tipoFala: 'fala', texto: 'sem tarefa' },
      { ts: ts(-600), tipo: 'veredito', tarefa: 'PV1', resultado: 'aprovada', texto: 'ok' },
    ]);
    const f = coletar(amb).estado({ agora }).falas.filter((x) => x.id.startsWith('emit') || x.tipo === 'veredito');
    assert.deepEqual(f.map((x) => [x.de, x.para]), [['am', 'ppp111'], ['ppp111', 'am'], ['am', 'prototype-dev'], ['am', 'ppp111']]);
  } finally { amb.limpar(); }
});

const notificacao = (id, status, resumo, atras) => ({
  type: 'queue-operation', operation: 'enqueue', timestamp: new Date(Date.now() - atras).toISOString(),
  content: ['<task-notification>', `<task-id>${id}</task-id>`, `<status>${status}</status>`, `<summary>${resumo}</summary>`, '</task-notification>'].join('\n'),
});

test('task-notification de Bash em segundo plano (sem transcript, meta ou chamada Agent) não vira agente', () => {
  const amb = criarAmbiente();
  try {
    popularSessao(amb);
    gravarJsonl(amb.main, [
      notificacao('b5v3xz14q', 'completed', 'Background command done', 1000),
      notificacao('b1t3nflph', 'killed', 'stopped', 900),
    ], true);
    const e = coletar(amb).estado();
    assert.deepEqual(e.agentes.map((a) => a.id).sort(), ['aaa111', 'am', 'bbb222']);
    assert.ok(!e.falas.some((f) => f.de === 'b5v3xz14q' || f.de === 'b1t3nflph'));
  } finally { amb.limpar(); }
});
