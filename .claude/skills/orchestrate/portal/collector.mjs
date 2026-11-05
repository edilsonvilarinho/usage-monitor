// Coletor do portal do escritório. Sem dependências.
// Lê os transcripts da sessão (principal + subagentes) e os eventos da AM (emit.mjs)
// e os dobra em um `Estado` (ver referencias/contrato-portal.md).
// O formato do transcript não é API pública: linha desconhecida é ignorada.

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = path.dirname(fileURLToPath(import.meta.url));

export const TIPOS_AGENTE = ['am', 'backend-dev', 'frontend-dev', 'prototype-dev', 'test-runner', 'explorer'];
export const ESTADOS_TAREFA = ['fila', 'andamento', 'validacao', 'aprovada', 'devolvida', 'bloqueada'];
export const RESULTADOS_VEREDITO = ['aprovada', 'devolvida', 'bloqueada'];
export const TIPOS_FALA = ['delegacao', 'retorno', 'devolucao', 'veredito', 'fala', 'atividade'];

const JANELA_ATIVO_MS = 120_000; // AM sem evento há mais que isso: ocioso
const JANELA_AGUARDANDO_MS = 20_000; // sem evento há mais que isso: aguardando (ferramenta aberta) ou pensando
const SESSAO_ENCERRADA_MS = 30 * 60_000; // principal parada há mais que isso: subagente sem fim vira erro
const MAX_FALAS = 300;
const MAX_PASSOS = 500;
const MAX_TEXTO_FALA = 240;

// ---------------------------------------------------------------- caminhos

// Project root being observed: PORTAL_RAIZ, then CLAUDE_PROJECT_DIR, then the current directory.
// Never derived from the skill path: installed in ~/.claude/skills it does not live inside the project.
export function raizRepo() {
  return path.resolve(process.env.PORTAL_RAIZ || process.env.CLAUDE_PROJECT_DIR || process.cwd());
}
export function dirEscritorio(raiz = raizRepo()) {
  return path.join(raiz, '.claude', 'escritorio');
}
export function dirProjetos() {
  return process.env.PORTAL_PROJETOS ? path.resolve(process.env.PORTAL_PROJETOS) : path.join(os.homedir(), '.claude', 'projects');
}
/** `<projeto>`: caminho do repositório com `:`, `\` e `/` trocados por `-`. */
export function slugProjeto(raiz = raizRepo()) {
  return raiz.replace(/[:\\/]/g, '-');
}
export function sessaoValida(s) {
  return typeof s === 'string' && /^[A-Za-z0-9_-]{1,80}$/.test(s);
}

export function parseArgs(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith('--')) {
      const k = a.slice(2);
      const prox = argv[i + 1];
      if (prox === undefined || prox.startsWith('--')) out[k] = true;
      else { out[k] = prox; i++; }
    } else out._.push(a);
  }
  return out;
}

// ---------------------------------------------------------------- texto

export function resumir(s, n = MAX_TEXTO_FALA) {
  const t = String(s ?? '').replace(/\s+/g, ' ').trim();
  return t.length <= n ? t : t.slice(0, Math.max(0, n - 1)).trimEnd() + '…';
}
function base(p) {
  return String(p ?? '').split(/[\\/]/).filter(Boolean).pop() || String(p ?? '');
}
function textoDe(c) {
  if (typeof c === 'string') return c;
  if (Array.isArray(c)) return c.map((b) => (b && b.type === 'text' && typeof b.text === 'string' ? b.text : '')).join(' ');
  return '';
}

/** Atividade em português a partir de uma chamada de ferramenta. */
export function atividadeDe(nome, input) {
  const i = input && typeof input === 'object' ? input : {};
  switch (nome) {
    case 'Read': return `lendo ${base(i.file_path)}`;
    case 'Edit': return `editando ${base(i.file_path)}`;
    case 'Write': return `criando ${base(i.file_path)}`;
    case 'NotebookEdit': return `editando ${base(i.notebook_path)}`;
    case 'Bash': return `rodando ${resumir(String(i.command ?? '').split('\n')[0], 60)}`;
    case 'PowerShell': return `rodando ${resumir(String(i.command ?? '').split('\n')[0], 60)}`;
    case 'Grep': return `buscando "${resumir(i.pattern, 40)}"`;
    case 'Glob': return `listando ${resumir(i.pattern, 50)}`;
    case 'Agent': return `delegando ${resumir(i.description, 60)}`;
    case 'SendMessage': return `enviando mensagem a ${resumir(i.to, 24)}`;
    case 'SubagentHandback': return 'entregando relatório';
    case 'TaskStop': return 'encerrando tarefa';
    case 'WebFetch': case 'WebSearch': return 'consultando a web';
    case 'ToolSearch': return 'carregando ferramentas';
    case 'Skill': return `acionando ${resumir(i.skill, 40)}`;
    default: return `usando ${String(nome).replace(/^mcp__.*__/, '')}`;
  }
}

function resumoFerramenta(nome, input) {
  const i = input && typeof input === 'object' ? input : {};
  const alvo = i.file_path ?? i.notebook_path ?? i.command ?? i.pattern ?? i.description ?? i.to ?? i.query ?? i.skill ?? '';
  return resumir(alvo, 240);
}

function idTarefaDe(descricao, prompt) {
  const d = /^\s*([A-Z]{1,3}\d{1,3})\b/.exec(descricao ?? '');
  if (d) return d[1];
  const p = /\bTarefa\s+([A-Z]{1,3}\d{1,3})\b/.exec(prompt ?? '');
  return p ? p[1] : null;
}

// ---------------------------------------------------------------- linha -> eventos

function ms(ts) {
  const t = typeof ts === 'string' ? Date.parse(ts) : NaN;
  return Number.isFinite(t) ? t : null;
}

function parseNotificacao(texto) {
  const tag = (nome) => {
    const m = new RegExp(`<${nome}>([\\s\\S]*?)</${nome}>`).exec(texto);
    return m ? m[1].trim() : null;
  };
  const taskId = tag('task-id');
  const status = tag('status');
  if (!taskId || !status) return null;
  return { taskId, status, resultado: tag('result'), resumo: tag('summary') };
}

/**
 * Converte uma linha JSON do transcript em eventos enxutos.
 * origem: { tipo: 'main' | 'sub', agentId }
 */
export function eventosDaLinha(o, origem) {
  const saida = [];
  if (!o || typeof o !== 'object') return saida;
  const t = ms(o.timestamp);
  if (t === null) return saida;
  const agent = origem.tipo === 'main' ? 'am' : origem.agentId;

  if (origem.tipo === 'main') {
    if (o.isSidechain === true || o.agentId) return saida;
    if (o.type === 'queue-operation' && o.operation === 'enqueue' && typeof o.content === 'string' && o.content.includes('<task-notification>')) {
      const n = parseNotificacao(o.content);
      if (n) saida.push({ t, k: 'notif', agent: 'am', ...n });
      return saida;
    }
  }

  const msg = o.message;
  if (o.type === 'assistant' && msg && typeof msg === 'object') {
    const u = msg.usage;
    if (u && typeof u === 'object') {
      saida.push({
        t, k: 'usage', agent, msgId: msg.id ?? o.uuid ?? null, model: typeof msg.model === 'string' ? msg.model : null,
        entrada: Number(u.input_tokens) || 0,
        saida: Number(u.output_tokens) || 0,
        cache: (Number(u.cache_read_input_tokens) || 0) + (Number(u.cache_creation_input_tokens) || 0),
      });
    }
    const blocos = Array.isArray(msg.content) ? msg.content : [];
    for (const b of blocos) {
      if (!b || typeof b !== 'object') continue;
      if (b.type === 'tool_use' && typeof b.name === 'string') {
        const input = b.input && typeof b.input === 'object' ? b.input : {};
        saida.push({ t, k: 'tool', agent, id: b.id ?? null, nome: b.name, atividade: atividadeDe(b.name, input), resumo: resumoFerramenta(b.name, input) });
        if (b.name === 'SubagentHandback') saida.push({ t, k: 'handback', agent, texto: String(input.message ?? '') });
        if (origem.tipo === 'main' && b.name === 'Agent') {
          saida.push({ t, k: 'call', agent, toolUseId: b.id ?? null, subagentType: input.subagent_type ?? null, descricao: input.description ?? null, prompt: input.prompt ?? null });
        }
        if (origem.tipo === 'main' && b.name === 'SendMessage') {
          saida.push({ t, k: 'send', agent, id: b.id ?? null, para: String(input.to ?? ''), texto: String(input.message ?? '') });
        }
      } else if (b.type === 'text' && typeof b.text === 'string' && b.text.trim()) {
        saida.push({ t, k: 'text', agent, texto: b.text });
      }
    }
    return saida;
  }

  if (o.type === 'user' && msg && typeof msg === 'object') {
    if (typeof msg.content === 'string') {
      if (origem.tipo === 'sub' && !o.isMeta) saida.push({ t, k: 'prompt', agent, texto: msg.content });
      return saida;
    }
    const blocos = Array.isArray(msg.content) ? msg.content : [];
    for (const b of blocos) {
      if (!b || b.type !== 'tool_result') continue;
      saida.push({ t, k: 'result', agent, toolUseId: b.tool_use_id ?? null, erro: b.is_error === true, texto: resumir(textoDe(b.content), 240) });
      const r = o.toolUseResult;
      if (origem.tipo === 'main' && r && typeof r === 'object' && typeof r.agentId === 'string') {
        saida.push({
          t, k: 'launched', agent, toolUseId: b.tool_use_id ?? null, agentId: r.agentId,
          resolvedModel: typeof r.resolvedModel === 'string' ? r.resolvedModel : null,
          agentType: typeof r.agentType === 'string' ? r.agentType : null,
          concluido: r.status === 'completed',
        });
      }
    }
  }
  return saida;
}

function eventosDeEmit(o) {
  const t = ms(o && o.ts);
  if (t === null || !o || typeof o !== 'object') return [];
  switch (o.tipo) {
    case 'tarefa': return [{ t, k: 'e-tarefa', agent: 'emit', id: String(o.id ?? ''), titulo: String(o.titulo ?? ''), dono: String(o.dono ?? ''), estado: String(o.estado ?? '') }];
    case 'fala': return [{ t, k: 'e-fala', agent: 'emit', de: String(o.de ?? ''), para: String(o.para ?? ''), tarefa: o.tarefa ? String(o.tarefa) : null, tipo: String(o.tipoFala ?? 'fala'), texto: String(o.texto ?? '') }];
    case 'veredito': return [{ t, k: 'e-veredito', agent: 'emit', tarefa: String(o.tarefa ?? ''), resultado: String(o.resultado ?? ''), texto: String(o.texto ?? '') }];
    default: return [];
  }
}

// ---------------------------------------------------------------- dobra

function iso(t) {
  return t === null || t === undefined ? null : new Date(t).toISOString();
}

function novoAgente(id) {
  return {
    id, tipoRaw: null, modelo: null, descricao: null, inicio: null, ultimo: null, tarefa: null,
    tools: new Set(), abertas: new Map(), errosIds: new Set(), usos: new Map(),
    atividade: null, terminal: null, terminalT: null, handback: false, retorno: null, ultimoTexto: null,
    prompt: null, callToolUseId: null, processados: 0,
  };
}

/**
 * Função pura: eventos ordenados + metas -> Estado.
 * ate: instante virtual (replay); agora: relógio para ocioso/aguardando.
 */
export function montarEstado({ eventos, metas, sessao, agora, ate = null, modo = 'ao-vivo' }) {
  const total = new Map();
  for (const e of eventos) total.set(e.agent, (total.get(e.agent) ?? 0) + 1);

  const agentes = new Map();
  const calls = new Map();
  const tarefas = new Map();
  const falas = [];
  const notifVistas = new Set();

  const garantir = (id) => {
    let a = agentes.get(id);
    if (!a) { a = novoAgente(id); agentes.set(id, a); }
    return a;
  };
  garantir('am');

  const resolverCall = (a) => {
    const meta = metas.get(a.id);
    const tid = a.callToolUseId ?? meta?.toolUseId ?? null;
    return tid ? calls.get(tid) ?? null : null;
  };
  const tarefaDe = (id) => {
    if (id === 'am' || !agentes.has(id)) return null;
    const a = agentes.get(id);
    const c = resolverCall(a);
    return idTarefaDe(c?.descricao ?? metas.get(id)?.description, c?.prompt ?? a.prompt);
  };
  const agentePorChamada = new Map();
  for (const [agentId, m] of metas) if (m?.toolUseId) agentePorChamada.set(m.toolUseId, agentId);
  const lancados = new Set();
  const lancar = (agentId, toolUseId, t, modeloResolvido, tipo) => {
    const sub = garantir(agentId);
    sub.callToolUseId = toolUseId;
    if (sub.inicio === null) sub.inicio = t;
    if (sub.modelo === null) sub.modelo = modeloResolvido;
    if (!sub.tipoRaw) sub.tipoRaw = tipo;
    if (lancados.has(agentId)) return;
    lancados.add(agentId);
    const c = toolUseId ? calls.get(toolUseId) : null;
    falar(`del:${toolUseId ?? agentId}`, c?.t ?? t, 'am', agentId, tarefaDe(agentId), 'delegacao', `Delegou: ${c?.descricao ?? metas.get(agentId)?.description ?? 'tarefa sem descrição'}`);
  };
  const tipoDe = (a) => {
    const c = resolverCall(a);
    return c?.subagentType ?? metas.get(a.id)?.agentType ?? a.tipoRaw ?? null;
  };
  // 'prototype-dev' + tarefa 'PV1' -> agentId do dono da tarefa; sem correspondência, mantém o nome
  const resolverAgente = (nome, tarefa) => {
    if (!nome || nome === 'am' || agentes.has(nome) || !tarefa) return nome;
    let melhor = null;
    for (const a of agentes.values()) {
      if (a.id === 'am' || tarefaDe(a.id) !== tarefa || tipoDe(a) !== nome) continue;
      if (!melhor || (a.inicio ?? 0) >= (melhor.inicio ?? 0)) melhor = a;
    }
    return melhor ? melhor.id : nome;
  };
  const falar = (id, t, de, para, tarefa, tipo, texto) =>
    falas.push({ id, ts: iso(t), de, para, tarefa, tipo, texto: resumir(texto, MAX_TEXTO_FALA) });
  const tocar = (a, t) => { if (a.ultimo === null || t > a.ultimo) a.ultimo = t; if (a.inicio === null) a.inicio = t; };
  const reabrir = (a, t) => { if (a.terminal && t > a.terminalT) { a.terminal = null; a.terminalT = null; a.handback = false; } };
  const encerrar = (a, estado, t) => { a.terminal = estado; a.terminalT = t; };

  let ultimoMain = null;
  for (const e of eventos) {
    if (ate !== null && e.t > ate) break;
    if (e.agent === 'am') ultimoMain = e.t;
    const a = e.agent !== 'emit' ? garantir(e.agent) : null;
    if (a) a.processados++;

    switch (e.k) {
      case 'call': {
        if (!e.toolUseId) break;
        calls.set(e.toolUseId, e);
        // agente em primeiro plano só aparece no resultado; o meta já diz qual agentId nasceu desta chamada
        const id = agentePorChamada.get(e.toolUseId);
        if (id) lancar(id, e.toolUseId, e.t, null, null);
        break;
      }
      case 'launched':
        lancar(e.agentId, e.toolUseId, e.t, e.resolvedModel, e.agentType);
        break;
      case 'usage': {
        reabrir(a, e.t);
        if (e.msgId) a.usos.set(e.msgId, e);
        if (e.model && e.model !== '<synthetic>') a.modelo = e.model;
        tocar(a, e.t);
        break;
      }
      case 'tool': {
        reabrir(a, e.t);
        if (e.id) { a.tools.add(e.id); a.abertas.set(e.id, { t: e.t, nome: e.nome }); }
        a.atividade = e.atividade;
        tocar(a, e.t);
        break;
      }
      case 'handback': {
        a.handback = true;
        a.retorno = e.texto;
        const linha = e.texto.split('\n').find((l) => l.trim()) ?? '';
        falar(`ret:${e.agent}:${e.t}`, e.t, e.agent, 'am', tarefaDe(e.agent), 'retorno', `Retornou: ${linha}`);
        break;
      }
      case 'result': {
        const aberta = e.toolUseId ? a.abertas.get(e.toolUseId) : null;
        if (e.toolUseId) a.abertas.delete(e.toolUseId);
        if (e.erro && e.toolUseId) a.errosIds.add(e.toolUseId);
        else if (e.erro) a.errosIds.add(`${e.t}`);
        if (aberta && aberta.nome === 'SubagentHandback' && !e.erro && e.agent !== 'am') encerrar(a, 'concluido', e.t);
        tocar(a, e.t);
        break;
      }
      case 'text':
        reabrir(a, e.t);
        a.atividade = 'redigindo resposta';
        a.ultimoTexto = e.texto;
        tocar(a, e.t);
        break;
      case 'prompt':
        if (a.prompt === null) a.prompt = e.texto;
        tocar(a, e.t);
        break;
      case 'send': {
        // devolução só quando a primeira linha da mensagem diz "devolvida"; o resto é retomada
        const devolvida = /devolvida/i.test(e.texto.split(/\r?\n/)[0]);
        falar(`msg:${e.id ?? e.t}`, e.t, 'am', e.para, tarefaDe(e.para), devolvida ? 'devolucao' : 'fala', `${devolvida ? 'Devolveu' : 'Retomou'}: ${e.texto}`);
        break;
      }
      case 'notif': {
        const chave = `${e.taskId}|${e.status}|${e.resultado ?? ''}`;
        if (notifVistas.has(chave)) break;
        notifVistas.add(chave);
        // task-notification também cobre Bash em segundo plano: só vale para agente com transcript, meta ou chamada Agent
        if (!agentes.has(e.taskId) && !metas.has(e.taskId) && !total.has(e.taskId)) break;
        const sub = garantir(e.taskId);
        if (e.status === 'killed') {
          encerrar(sub, 'erro', e.t);
          falar(`kill:${e.taskId}:${e.t}`, e.t, e.taskId, 'am', tarefaDe(e.taskId), 'atividade', 'Interrompido pela orquestradora.');
        } else if (e.status === 'completed') {
          const jaRetornou = sub.handback;
          encerrar(sub, 'concluido', e.t);
          if (!jaRetornou) falar(`fim:${e.taskId}:${e.t}`, e.t, e.taskId, 'am', tarefaDe(e.taskId), 'retorno', 'Encerrou sem relatório final.');
        } else if (e.status === 'failed' || e.status === 'error') {
          encerrar(sub, 'erro', e.t);
        }
        break;
      }
      case 'e-tarefa': {
        if (!e.id || !ESTADOS_TAREFA.includes(e.estado)) break;
        const ant = tarefas.get(e.id);
        const t = ant ?? { id: e.id, titulo: e.titulo, dono: e.dono, estado: e.estado, devolucoes: 0, veredito: null, atualizadoEm: iso(e.t) };
        if (ant) { if (e.titulo) t.titulo = e.titulo; if (e.dono) t.dono = e.dono; }
        if (e.estado === 'devolvida' && (!ant || ant.estado !== 'devolvida')) t.devolucoes++;
        t.estado = e.estado;
        t.atualizadoEm = iso(e.t);
        tarefas.set(e.id, t);
        break;
      }
      case 'e-veredito': {
        const t = tarefas.get(e.tarefa);
        if (t && RESULTADOS_VEREDITO.includes(e.resultado)) {
          if (e.resultado === 'devolvida' && t.estado !== 'devolvida') t.devolucoes++;
          t.estado = e.resultado;
          t.veredito = resumir(e.texto, MAX_TEXTO_FALA) || e.resultado;
          t.atualizadoEm = iso(e.t);
        }
        falar(`ver:${e.tarefa}:${e.t}`, e.t, 'am', resolverAgente(t?.dono || 'am', e.tarefa || null), e.tarefa || null, 'veredito', e.texto || `Veredito: ${e.resultado}`);
        break;
      }
      case 'e-fala':
        falar(`emit:${e.t}:${falas.length}`, e.t, resolverAgente(e.de, e.tarefa), resolverAgente(e.para, e.tarefa), e.tarefa, TIPOS_FALA.includes(e.tipo) ? e.tipo : 'fala', e.texto);
        break;
      default: break;
    }
  }

  // ---- projeção dos agentes
  const lista = [];
  const subAtivos = () => [...agentes.values()].some((x) => x.id !== 'am' && estadoDe(x) !== 'ocioso' && estadoDe(x) !== 'concluido' && estadoDe(x) !== 'erro');
  const cacheEstado = new Map();
  function estadoDe(a) {
    if (cacheEstado.has(a.id)) return cacheEstado.get(a.id);
    let est;
    const meta = metas.get(a.id);
    const todosProcessados = a.processados >= (total.get(a.id) ?? 0);
    if (a.terminal) est = a.terminal;
    else if (a.id !== 'am' && meta?.finished === true && todosProcessados && a.processados > 0) est = 'concluido';
    else {
      const ref = a.ultimo ?? a.inicio;
      const ocioso = ref === null ? 0 : agora - ref;
      if (a.id === 'am') {
        cacheEstado.set(a.id, 'ocioso'); // evita recursão em subAtivos
        if (ref === null) est = 'ocioso';
        else if (ocioso <= JANELA_ATIVO_MS) est = ocioso > JANELA_AGUARDANDO_MS && subAtivos() ? 'aguardando' : 'trabalhando';
        else est = subAtivos() ? 'aguardando' : 'ocioso';
      } else if (ultimoMain !== null && agora - ultimoMain > SESSAO_ENCERRADA_MS) {
        est = 'erro'; // a sessão principal parou sem o subagente concluir
        a.encerradoSemFim = true;
      } else {
        // sem `finished` e sem notificação de fim: nunca ocioso (geração longa não grava evento)
        est = a.abertas.size > 0 && ocioso > JANELA_AGUARDANDO_MS ? 'aguardando' : 'trabalhando';
      }
    }
    cacheEstado.set(a.id, est);
    return est;
  }

  const totais = { tokensEntrada: 0, tokensSaida: 0, tokensCache: 0, agentesAtivos: 0 };
  const ordem = [...agentes.values()].sort((x, y) => (x.id === 'am' ? -1 : y.id === 'am' ? 1 : (x.inicio ?? Infinity) - (y.inicio ?? Infinity)));
  for (const a of ordem) {
    const meta = metas.get(a.id);
    const c = resolverCall(a);
    const tipoOriginal = a.id === 'am' ? 'am' : (c?.subagentType ?? meta?.agentType ?? a.tipoRaw ?? null);
    const tok = { entrada: 0, saida: 0, cache: 0 };
    for (const u of a.usos.values()) { tok.entrada += u.entrada; tok.saida += u.saida; tok.cache += u.cache; }
    const est = estadoDe(a);
    let atividade;
    if (est === 'concluido') atividade = 'concluído';
    else if (est === 'erro') atividade = a.encerradoSemFim ? 'sessão encerrada sem concluir' : 'interrompido';
    else if (est === 'ocioso') atividade = 'sem atividade';
    else if (a.id !== 'am' && !a.abertas.size && (a.ultimo ?? a.inicio) !== null && agora - (a.ultimo ?? a.inicio) > JANELA_AGUARDANDO_MS) atividade = 'pensando ou gerando resposta';
    else if (a.id === 'am' && est === 'aguardando' && !a.abertas.size) atividade = 'aguardando os agentes';
    else atividade = a.atividade ?? 'iniciando';
    totais.tokensEntrada += tok.entrada; totais.tokensSaida += tok.saida; totais.tokensCache += tok.cache;
    if (est === 'trabalhando' || est === 'aguardando') totais.agentesAtivos++;
    lista.push({
      id: a.id,
      tipo: a.id === 'am' ? 'am' : TIPOS_AGENTE.includes(tipoOriginal) ? tipoOriginal : 'outro',
      tipoOriginal,
      modelo: a.modelo,
      descricao: a.id === 'am' ? 'Orquestradora (sessão principal)' : (c?.descricao ?? meta?.description ?? ''),
      estado: est,
      atividade,
      inicio: iso(a.inicio),
      fim: est === 'concluido' || est === 'erro' ? iso(a.terminalT ?? a.ultimo) : null,
      ultimoEvento: iso(a.ultimo ?? a.inicio),
      tarefa: a.id === 'am' ? null : tarefaDe(a.id),
      ferramentas: a.tools.size,
      erros: a.errosIds.size,
      tokens: tok,
    });
  }

  falas.sort((x, y) => (x.ts < y.ts ? -1 : x.ts > y.ts ? 1 : 0));
  return {
    sessao,
    atualizadoEm: iso(ate ?? agora),
    modo,
    agentes: lista,
    tarefas: [...tarefas.values()],
    falas: falas.slice(-MAX_FALAS),
    totais,
  };
}

/** Detalhe de inspeção de um agente. */
export function montarDetalhe({ eventos, metas, id, ate = null }) {
  const proprios = eventos.filter((e) => e.agent === id && (ate === null || e.t <= ate));
  const calls = new Map();
  let prompt = null;
  let launched = null;
  for (const e of eventos) {
    if (ate !== null && e.t > ate) break;
    if (e.k === 'call' && e.toolUseId) calls.set(e.toolUseId, e);
    if (e.k === 'launched' && e.agentId === id) launched = e;
  }
  const meta = metas.get(id);
  const c = launched?.toolUseId ? calls.get(launched.toolUseId) : meta?.toolUseId ? calls.get(meta.toolUseId) : null;
  if (id !== 'am') prompt = c?.prompt ?? proprios.find((e) => e.k === 'prompt')?.texto ?? null;
  else if (!proprios.length) return null;
  if (id !== 'am' && !proprios.length && !launched && !meta) return null;

  const nomeDe = new Map();
  const passos = [];
  let retorno = null;
  let ultimoTexto = null;
  const vistos = new Set();
  for (const e of proprios) {
    if (e.k === 'tool') {
      if (e.id) { if (vistos.has(`t${e.id}`)) continue; vistos.add(`t${e.id}`); nomeDe.set(e.id, e.nome); }
      passos.push({ ts: iso(e.t), tipo: 'ferramenta', ferramenta: e.nome, resumo: e.resumo || e.atividade, erro: false });
    } else if (e.k === 'result') {
      passos.push({ ts: iso(e.t), tipo: 'resultado', ferramenta: nomeDe.get(e.toolUseId) ?? null, resumo: e.texto, erro: e.erro });
    } else if (e.k === 'text') {
      ultimoTexto = e.texto;
      passos.push({ ts: iso(e.t), tipo: 'texto', ferramenta: null, resumo: resumir(e.texto, 240), erro: false });
    } else if (e.k === 'handback') retorno = e.texto;
  }
  if (retorno === null && id !== 'am') {
    const n = eventos.filter((e) => e.k === 'notif' && e.taskId === id && (ate === null || e.t <= ate) && e.resultado && !/^NOTE: this agent stopped/.test(e.resultado)).pop();
    retorno = n?.resultado ?? ultimoTexto ?? null;
  }
  return { id, prompt, passos: passos.slice(-MAX_PASSOS), retorno };
}

// ---------------------------------------------------------------- coletor incremental

class Fonte {
  constructor(arquivo, origem) {
    this.arquivo = arquivo; this.origem = origem; this.offset = 0; this.resto = Buffer.alloc(0);
  }
}

export class Coletor {
  constructor({ sessao, raiz = raizRepo(), projetos = dirProjetos() }) {
    this.raiz = raiz;
    this.projetos = projetos;
    this.trocar(sessao);
  }

  trocar(sessao) {
    this.sessao = sessao;
    this.fontes = new Map();
    this.metas = new Map(); // agentId -> meta
    this.metaMtime = new Map();
    this.eventos = [];
    this.sujo = false;
    this.seq = 0;
    this.dirProjeto = null;
    this.ultimaBusca = 0;
  }

  /** Acha a pasta de projeto que contém a sessão. */
  localizar() {
    const primario = path.join(this.projetos, slugProjeto(this.raiz));
    const tem = (d) => fs.existsSync(path.join(d, `${this.sessao}.jsonl`)) || fs.existsSync(path.join(d, this.sessao));
    if (tem(primario)) return primario;
    const agora = Date.now();
    if (agora - this.ultimaBusca < 5000) return null;
    this.ultimaBusca = agora;
    try {
      for (const d of fs.readdirSync(this.projetos)) {
        const cand = path.join(this.projetos, d);
        if (tem(cand)) return cand;
      }
    } catch { /* sem pasta de projetos */ }
    return null;
  }

  lerNovo(fonte) {
    let st;
    try { st = fs.statSync(fonte.arquivo); } catch { return 0; }
    if (st.size < fonte.offset) { fonte.offset = 0; fonte.resto = Buffer.alloc(0); }
    if (st.size === fonte.offset) return 0;
    let fd;
    try {
      fd = fs.openSync(fonte.arquivo, 'r');
      const n = st.size - fonte.offset;
      const buf = Buffer.alloc(n);
      const lidos = fs.readSync(fd, buf, 0, n, fonte.offset);
      fonte.offset += lidos;
      const todo = Buffer.concat([fonte.resto, buf.subarray(0, lidos)]);
      const ultimoNl = todo.lastIndexOf(0x0a);
      if (ultimoNl < 0) { fonte.resto = todo; return 0; }
      fonte.resto = todo.subarray(ultimoNl + 1);
      let novos = 0;
      for (const linha of todo.subarray(0, ultimoNl).toString('utf8').split('\n')) {
        if (!linha.trim()) continue;
        let o;
        try { o = JSON.parse(linha); } catch { continue; }
        let evs;
        try {
          evs = fonte.origem.tipo === 'emit' ? eventosDeEmit(o) : eventosDaLinha(o, fonte.origem);
        } catch { continue; }
        for (const ev of evs) { ev.n = this.seq++; this.eventos.push(ev); novos++; }
      }
      if (novos) this.sujo = true;
      return novos;
    } catch {
      return 0;
    } finally {
      if (fd !== undefined) try { fs.closeSync(fd); } catch { /* ignora */ }
    }
  }

  fonte(arquivo, origem) {
    let f = this.fontes.get(arquivo);
    if (!f) { f = new Fonte(arquivo, origem); this.fontes.set(arquivo, f); }
    return f;
  }

  /** Lê o que mudou em disco. Retorna true se há dado novo. */
  poll() {
    let mudou = 0;
    if (!this.dirProjeto) this.dirProjeto = this.localizar();
    if (this.dirProjeto) {
      mudou += this.lerNovo(this.fonte(path.join(this.dirProjeto, `${this.sessao}.jsonl`), { tipo: 'main' }));
      const dirSub = path.join(this.dirProjeto, this.sessao, 'subagents');
      let nomes = [];
      try { nomes = fs.readdirSync(dirSub); } catch { /* ainda não existe */ }
      for (const nome of nomes) {
        const m = /^agent-(.+)\.(jsonl|meta\.json)$/.exec(nome);
        if (!m) continue;
        const arq = path.join(dirSub, nome);
        if (m[2] === 'jsonl') {
          mudou += this.lerNovo(this.fonte(arq, { tipo: 'sub', agentId: m[1] }));
        } else {
          let st;
          try { st = fs.statSync(arq); } catch { continue; }
          if (this.metaMtime.get(arq) === st.mtimeMs) continue;
          try {
            const meta = JSON.parse(fs.readFileSync(arq, 'utf8'));
            if (meta && typeof meta === 'object') { this.metas.set(m[1], meta); this.metaMtime.set(arq, st.mtimeMs); mudou++; this.sujo = true; }
          } catch { /* meta em escrita: tenta no próximo ciclo */ }
        }
      }
    }
    mudou += this.lerNovo(this.fonte(path.join(dirEscritorio(this.raiz), `${this.sessao}.jsonl`), { tipo: 'emit' }));
    return mudou > 0;
  }

  ordenados() {
    if (this.sujo) {
      this.eventos.sort((a, b) => a.t - b.t || a.n - b.n);
      this.sujo = false;
    }
    return this.eventos;
  }

  /** Primeiro e último instante dos eventos (para o replay). */
  limites() {
    const ev = this.ordenados();
    return ev.length ? { min: ev[0].t, max: ev[ev.length - 1].t } : null;
  }

  estado({ agora = Date.now(), ate = null, modo = 'ao-vivo' } = {}) {
    return montarEstado({ eventos: this.ordenados(), metas: this.metas, sessao: this.sessao, agora, ate, modo });
  }

  detalhe(id, { ate = null } = {}) {
    return montarDetalhe({ eventos: this.ordenados(), metas: this.metas, id, ate });
  }
}
