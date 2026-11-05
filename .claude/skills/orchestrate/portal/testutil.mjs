// Utilitários dos testes do portal: ambiente temporário com transcripts sintéticos.
import fs from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { slugProjeto } from './collector.mjs';

export const SESSAO = '11111111-2222-3333-4444-555555555555';

export function criarAmbiente() {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'portal-test-'));
  const raiz = path.join(tmp, 'repo');
  const projetos = path.join(tmp, 'projetos');
  fs.mkdirSync(path.join(raiz, 'prototipos', 'portal', 'p1'), { recursive: true });
  fs.writeFileSync(path.join(raiz, 'prototipos', 'portal', 'p1', 'index.html'), '<!doctype html><title>p1</title>');
  fs.writeFileSync(path.join(raiz, 'prototipos', 'portal', 'index.html'), '<!doctype html><title>galeria</title>');
  fs.writeFileSync(path.join(raiz, '.env'), 'SEGREDO=nao-pode-sair');
  fs.writeFileSync(path.join(tmp, 'fora.txt'), 'fora da pasta');
  const dirProjeto = path.join(projetos, slugProjeto(raiz));
  fs.mkdirSync(path.join(dirProjeto, SESSAO, 'subagents'), { recursive: true });
  return {
    tmp, raiz, projetos, dirProjeto,
    main: path.join(dirProjeto, `${SESSAO}.jsonl`),
    dirSub: path.join(dirProjeto, SESSAO, 'subagents'),
    limpar() { fs.rmSync(tmp, { recursive: true, force: true }); },
  };
}

export function gravarJsonl(arquivo, linhas, anexar = false) {
  const txt = linhas.map((l) => JSON.stringify(l)).join('\n') + '\n';
  if (anexar) fs.appendFileSync(arquivo, txt); else fs.writeFileSync(arquivo, txt);
}

const iso = (t) => new Date(t).toISOString();

export function linhaAssistant({ t, id, model = 'claude-sonnet-5-5', entrada = 1, saida = 1, leitura = 0, criacao = 0, blocos = [], agentId }) {
  return {
    type: 'assistant', timestamp: iso(t), ...(agentId ? { agentId, isSidechain: true } : { isSidechain: false }),
    message: {
      id, model, role: 'assistant', content: blocos,
      usage: { input_tokens: entrada, output_tokens: saida, cache_read_input_tokens: leitura, cache_creation_input_tokens: criacao },
    },
  };
}
export const bTool = (id, name, input) => ({ type: 'tool_use', id, name, input });
export const bTexto = (text) => ({ type: 'text', text });

export function linhaResultado({ t, toolUseId, erro = false, texto = 'ok', extra = {}, agentId }) {
  return {
    type: 'user', timestamp: iso(t), ...(agentId ? { agentId, isSidechain: true } : { isSidechain: false }),
    message: { role: 'user', content: [{ type: 'tool_result', tool_use_id: toolUseId, content: texto, ...(erro ? { is_error: true } : {}) }] },
    ...extra,
  };
}
export const linhaPrompt = (t, texto, agentId) => ({ type: 'user', timestamp: iso(t), isSidechain: true, agentId, message: { role: 'user', content: texto } });

export function metaDe(agentType, description, toolUseId, finished) {
  return { agentType, description, toolUseId, ...(finished ? { finished: true } : {}) };
}

/** Porta TCP livre em 127.0.0.1. */
export function portaLivre() {
  return new Promise((resolve, reject) => {
    const s = net.createServer();
    s.listen(0, '127.0.0.1', () => { const { port } = s.address(); s.close(() => resolve(port)); });
    s.on('error', reject);
  });
}

export const espera = (ms) => new Promise((r) => setTimeout(r, ms));

/** Monta uma sessão pequena: AM delega 2 subagentes (um concluído, um Explore interrompido). */
export function popularSessao(amb, t0 = Date.now() - 600_000) {
  gravarJsonl(amb.main, [
    linhaAssistant({
      t: t0, id: 'm1', model: 'claude-opus-5-5', entrada: 10, saida: 20, leitura: 100, criacao: 50,
      blocos: [bTool('tu-a', 'Agent', { subagent_type: 'backend-dev', description: 'B11 coleta', prompt: 'Tarefa B11. faça isto' })],
    }),
    // mesma message.id repetida (um bloco por linha): usage não pode ser contado duas vezes
    linhaAssistant({
      t: t0, id: 'm1', model: 'claude-opus-5-5', entrada: 10, saida: 20, leitura: 100, criacao: 50,
      blocos: [bTool('tu-b', 'Agent', { subagent_type: 'Explore', description: 'Mapear coisas', prompt: 'varra' })],
    }),
    linhaResultado({ t: t0 + 100, toolUseId: 'tu-a', extra: { toolUseResult: { isAsync: true, status: 'async_launched', agentId: 'aaa111', resolvedModel: 'claude-sonnet-5-5', agentType: 'backend-dev' } } }),
    linhaResultado({ t: t0 + 200, toolUseId: 'tu-b', extra: { toolUseResult: { isAsync: true, status: 'async_launched', agentId: 'bbb222', resolvedModel: 'claude-opus-5-5', agentType: 'Explore' } } }),
    { type: 'queue-operation', operation: 'enqueue', timestamp: iso(t0 + 60_000), content: '<task-notification>\n<task-id>bbb222</task-id>\n<status>killed</status>\n<summary>Agent stopped</summary>\n</task-notification>' },
    { type: 'queue-operation', operation: 'enqueue', timestamp: iso(t0 + 120_000), content: '<task-notification>\n<task-id>aaa111</task-id>\n<status>completed</status>\n<summary>ok</summary>\n<result>feito</result>\n</task-notification>' },
  ]);
  gravarJsonl(path.join(amb.dirSub, 'agent-aaa111.jsonl'), [
    linhaPrompt(t0 + 300, 'Tarefa B11. faça isto', 'aaa111'),
    linhaAssistant({ t: t0 + 1000, id: 's1', entrada: 3, saida: 7, leitura: 11, criacao: 13, agentId: 'aaa111', blocos: [bTool('t1', 'Read', { file_path: 'C:\\x\\y\\config.ts' })] }),
    linhaAssistant({ t: t0 + 1000, id: 's1', entrada: 3, saida: 7, leitura: 11, criacao: 13, agentId: 'aaa111', blocos: [bTexto('lendo')] }),
    linhaResultado({ t: t0 + 1500, toolUseId: 't1', agentId: 'aaa111' }),
    linhaAssistant({ t: t0 + 2000, id: 's2', entrada: 5, saida: 9, leitura: 0, criacao: 2, agentId: 'aaa111', blocos: [bTool('t2', 'Bash', { command: 'npm test' })] }),
    linhaResultado({ t: t0 + 2500, toolUseId: 't2', erro: true, texto: 'falhou', agentId: 'aaa111' }),
    linhaAssistant({ t: t0 + 3000, id: 's3', entrada: 1, saida: 1, agentId: 'aaa111', blocos: [bTool('t3', 'SubagentHandback', { message: 'CONCLUÍDA B11.\nlinha 2' })] }),
    linhaResultado({ t: t0 + 3100, toolUseId: 't3', agentId: 'aaa111' }),
  ]);
  fs.writeFileSync(path.join(amb.dirSub, 'agent-aaa111.meta.json'), JSON.stringify(metaDe('backend-dev', 'B11 coleta', 'tu-a', true)));
  gravarJsonl(path.join(amb.dirSub, 'agent-bbb222.jsonl'), [
    linhaPrompt(t0 + 400, 'varra', 'bbb222'),
    linhaAssistant({ t: t0 + 1000, id: 'e1', model: 'claude-opus-5-5', entrada: 2, saida: 4, leitura: 6, criacao: 0, agentId: 'bbb222', blocos: [bTool('u1', 'Grep', { pattern: 'flicker' })] }),
  ]);
  fs.writeFileSync(path.join(amb.dirSub, 'agent-bbb222.meta.json'), JSON.stringify(metaDe('Explore', 'Mapear coisas', 'tu-b', false)));
  return t0;
}
