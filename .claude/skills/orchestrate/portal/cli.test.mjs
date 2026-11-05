import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { SESSAO, criarAmbiente, espera, popularSessao, portaLivre } from './testutil.mjs';

const AQUI = path.dirname(fileURLToPath(import.meta.url));
const script = (nome) => path.join(AQUI, nome);

function rodar(amb, nome, args) {
  return spawnSync(process.execPath, [script(nome), ...args], {
    encoding: 'utf8', timeout: 30_000, windowsHide: true,
    env: { ...process.env, PORTAL_RAIZ: amb.raiz, PORTAL_PROJETOS: amb.projetos },
  });
}

function pegar(porta, caminho) {
  return new Promise((resolve) => {
    http.get({ host: '127.0.0.1', port: porta, path: caminho }, (res) => {
      const p = [];
      res.on('data', (c) => p.push(c));
      res.on('end', () => resolve({ status: res.statusCode, corpo: Buffer.concat(p).toString('utf8') }));
    }).on('error', () => resolve(null));
  });
}

const vivo = (pid) => { try { process.kill(pid, 0); return true; } catch { return false; } };

test('emit.mjs grava tarefa, fala e veredito na sessão de portal.json', () => {
  const amb = criarAmbiente();
  try {
    const esc = path.join(amb.raiz, '.claude', 'escritorio');
    fs.mkdirSync(esc, { recursive: true });
    fs.writeFileSync(path.join(esc, 'portal.json'), JSON.stringify({ pid: 1, porta: 1, sessao: SESSAO }));
    assert.equal(rodar(amb, 'emit.mjs', ['tarefa', '--id', 'AG1', '--titulo', 'Portal', '--dono', 'prototype-dev', '--estado', 'andamento']).status, 0);
    assert.equal(rodar(amb, 'emit.mjs', ['fala', '--de', 'am', '--para', 'prototype-dev', '--tarefa', 'AG1', '--texto', 'corrija']).status, 0);
    assert.equal(rodar(amb, 'emit.mjs', ['veredito', '--tarefa', 'AG1', '--resultado', 'aprovada', '--texto', 'ok']).status, 0);
    const linhas = fs.readFileSync(path.join(esc, `${SESSAO}.jsonl`), 'utf8').trim().split('\n').map((l) => JSON.parse(l));
    assert.deepEqual(linhas.map((l) => l.tipo), ['tarefa', 'fala', 'veredito']);
    assert.equal(linhas[0].estado, 'andamento');
    assert.ok(Number.isFinite(Date.parse(linhas[0].ts)));
  } finally { amb.limpar(); }
});

test('emit.mjs rejeita estado inválido, argumento faltando e ausência de sessão', () => {
  const amb = criarAmbiente();
  try {
    const sem = rodar(amb, 'emit.mjs', ['tarefa', '--id', 'AG1', '--titulo', 't', '--dono', 'd', '--estado', 'andamento']);
    assert.equal(sem.status, 1);
    assert.match(sem.stderr, /portal\.json/);
    const ruim = rodar(amb, 'emit.mjs', ['tarefa', '--sessao', SESSAO, '--id', 'AG1', '--titulo', 't', '--dono', 'd', '--estado', 'feito']);
    assert.equal(ruim.status, 1);
    assert.match(ruim.stderr, /--estado/);
    assert.equal(rodar(amb, 'emit.mjs', ['fala', '--sessao', SESSAO, '--de', 'am']).status, 1);
    assert.equal(rodar(amb, 'emit.mjs', ['tarefa', '--sessao', '../x', '--id', 'a', '--titulo', 'b', '--dono', 'c', '--estado', 'fila']).status, 1);
  } finally { amb.limpar(); }
});

test('start.mjs é idempotente: duas execuções mantêm um único processo; troca de sessão no mesmo processo', async () => {
  const amb = criarAmbiente();
  popularSessao(amb);
  const porta = await portaLivre();
  let pid = null;
  try {
    const a = rodar(amb, 'start.mjs', ['--sessao', SESSAO, '--porta', String(porta), '--abrir', 'nao']);
    assert.equal(a.status, 0, a.stderr);
    assert.equal(a.stdout.trim(), `http://127.0.0.1:${porta}/`);
    const portalJson = path.join(amb.raiz, '.claude', 'escritorio', 'portal.json');
    const infoA = JSON.parse(fs.readFileSync(portalJson, 'utf8'));
    pid = infoA.pid;
    assert.equal(infoA.porta, porta);
    assert.equal(infoA.sessao, SESSAO);
    assert.ok(vivo(pid));

    const b = rodar(amb, 'start.mjs', ['--sessao', SESSAO, '--porta', String(porta), '--abrir', 'nao']);
    assert.equal(b.status, 0, b.stderr);
    assert.equal(b.stdout.trim(), a.stdout.trim());
    const infoB = JSON.parse(fs.readFileSync(portalJson, 'utf8'));
    assert.equal(infoB.pid, pid, 'o segundo start não pode criar outro processo');

    const est = JSON.parse((await pegar(porta, '/api/estado')).corpo);
    assert.equal(est.sessao, SESSAO);
    assert.equal(est.agentes.length, 3);

    // sessão diferente: mesmo processo, sessão trocada
    const c = rodar(amb, 'start.mjs', ['--sessao', 'outra-sessao', '--porta', String(porta), '--abrir', 'nao']);
    assert.equal(c.status, 0, c.stderr);
    const infoC = JSON.parse(fs.readFileSync(portalJson, 'utf8'));
    assert.equal(infoC.pid, pid);
    assert.equal(infoC.sessao, 'outra-sessao');
    assert.equal(JSON.parse((await pegar(porta, '/api/estado')).corpo).sessao, 'outra-sessao');
  } finally {
    if (pid) { try { process.kill(pid); } catch { /* já encerrado */ } }
    await espera(200);
    amb.limpar();
  }
});

test('start.mjs sem --sessao válida sai com erro de uso', () => {
  const amb = criarAmbiente();
  try {
    const r = rodar(amb, 'start.mjs', ['--sessao', '../x']);
    assert.equal(r.status, 2);
    assert.equal(r.stdout, '');
  } finally { amb.limpar(); }
});

test('server.mjs --replay sobe em 127.0.0.1 e não escreve portal.json', async () => {
  const amb = criarAmbiente();
  popularSessao(amb);
  const porta = await portaLivre();
  const { spawn } = await import('node:child_process');
  const filho = spawn(process.execPath, [script('server.mjs'), '--replay', SESSAO, '--velocidade', '50000', '--porta', String(porta)], {
    stdio: 'ignore', windowsHide: true, env: { ...process.env, PORTAL_RAIZ: amb.raiz, PORTAL_PROJETOS: amb.projetos },
  });
  try {
    let r = null;
    for (let i = 0; i < 40 && !r; i++) { await espera(150); r = await pegar(porta, '/api/estado'); }
    assert.ok(r, 'replay não respondeu');
    assert.equal(JSON.parse(r.corpo).modo, 'replay');
    assert.equal(fs.existsSync(path.join(amb.raiz, '.claude', 'escritorio', 'portal.json')), false);
  } finally {
    filho.kill();
    await espera(200);
    amb.limpar();
  }
});

test('emit.mjs tarefa: título e dono só são obrigatórios na primeira vez; atualização parcial preserva ambos', async () => {
  const amb = criarAmbiente();
  try {
    const esc = path.join(amb.raiz, '.claude', 'escritorio');
    fs.mkdirSync(esc, { recursive: true });
    fs.writeFileSync(path.join(esc, 'portal.json'), JSON.stringify({ pid: 1, porta: 1, sessao: SESSAO }));
    const sem = rodar(amb, 'emit.mjs', ['tarefa', '--id', 'AG1', '--estado', 'andamento']);
    assert.equal(sem.status, 1);
    assert.match(sem.stderr, /--titulo/);
    assert.equal(rodar(amb, 'emit.mjs', ['tarefa', '--id', 'AG1', '--titulo', 'Portal', '--dono', 'prototype-dev', '--estado', 'andamento']).status, 0);
    const parcial = rodar(amb, 'emit.mjs', ['tarefa', '--id', 'AG1', '--estado', 'devolvida']);
    assert.equal(parcial.status, 0, parcial.stderr);
    assert.equal(rodar(amb, 'emit.mjs', ['tarefa', '--id', 'OUTRA', '--estado', 'fila']).status, 1);
    const { Coletor } = await import('./collector.mjs');
    const c = new Coletor({ sessao: SESSAO, raiz: amb.raiz, projetos: amb.projetos });
    c.poll();
    const t = c.estado().tarefas;
    assert.equal(t.length, 1);
    assert.deepEqual([t[0].id, t[0].titulo, t[0].dono, t[0].estado, t[0].devolucoes], ['AG1', 'Portal', 'prototype-dev', 'devolvida', 1]);
  } finally { amb.limpar(); }
});

test('emit.mjs conta reprovações pelo log: 10 no haiku pedem escalada, 3 no sonnet pedem parada', async () => {
  const amb = criarAmbiente();
  try {
    const esc = path.join(amb.raiz, '.claude', 'escritorio');
    fs.mkdirSync(esc, { recursive: true });
    fs.writeFileSync(path.join(esc, 'portal.json'), JSON.stringify({ pid: 1, porta: 1, sessao: SESSAO }));
    assert.equal(rodar(amb, 'emit.mjs', ['tarefa', '--id', 'AG1', '--titulo', 'Portal', '--dono', 'prototype-dev', '--estado', 'andamento']).status, 0);
    const reprovar = () => rodar(amb, 'emit.mjs', ['veredito', '--tarefa', 'AG1', '--resultado', 'devolvida', '--texto', 'falhou']);

    for (let i = 1; i <= 9; i++) {
      const r = reprovar();
      assert.equal(r.status, 0, r.stderr);
      assert.match(r.stdout, new RegExp(`reprovação ${i}/10 no haiku.*devolver ao mesmo agente`));
    }
    const cedo = rodar(amb, 'emit.mjs', ['escalada', '--tarefa', 'AG1']);
    assert.equal(cedo.status, 1);
    assert.match(cedo.stderr, /9\/10/);

    assert.match(reprovar().stdout, /reprovação 10\/10 no haiku.*escalar para sonnet high/);
    assert.equal(rodar(amb, 'emit.mjs', ['escalada', '--tarefa', 'AG1']).status, 0);
    assert.equal(rodar(amb, 'emit.mjs', ['escalada', '--tarefa', 'AG1']).status, 1);

    assert.match(reprovar().stdout, /reprovação 1\/3 no sonnet.*devolver ao mesmo agente/);
    assert.match(reprovar().stdout, /reprovação 2\/3 no sonnet.*devolver ao mesmo agente/);
    assert.match(reprovar().stdout, /reprovação 3\/3 no sonnet.*parar e reportar/);

    // aprovação não conta, e outra tarefa tem contagem própria
    assert.equal(rodar(amb, 'emit.mjs', ['tarefa', '--id', 'AG2', '--titulo', 'Outra', '--dono', 'backend-dev', '--estado', 'andamento']).status, 0);
    assert.equal(rodar(amb, 'emit.mjs', ['veredito', '--tarefa', 'AG2', '--resultado', 'aprovada', '--texto', 'ok']).stdout, '');
    assert.match(rodar(amb, 'emit.mjs', ['veredito', '--tarefa', 'AG2', '--resultado', 'devolvida', '--texto', 'x']).stdout, /reprovação 1\/10 no haiku/);

    const { Coletor } = await import('./collector.mjs');
    const c = new Coletor({ sessao: SESSAO, raiz: amb.raiz, projetos: amb.projetos });
    c.poll();
    const ag1 = c.estado().tarefas.find((t) => t.id === 'AG1');
    assert.equal(ag1.devolucoes, 13, 'o portal precisa contar o mesmo que o emit');
  } finally { amb.limpar(); }
});

test('emit.mjs funciona sem portal: --sessao basta e a contagem de reprovações vem do log', () => {
  const amb = criarAmbiente();
  try {
    const esc = path.join(amb.raiz, '.claude', 'escritorio');
    assert.equal(fs.existsSync(path.join(esc, 'portal.json')), false);
    const base = ['--sessao', SESSAO];
    assert.equal(rodar(amb, 'emit.mjs', ['tarefa', ...base, '--id', 'NP1', '--titulo', 'Sem portal', '--dono', 'backend-dev', '--estado', 'andamento']).status, 0);
    const r = rodar(amb, 'emit.mjs', ['veredito', ...base, '--tarefa', 'NP1', '--resultado', 'devolvida', '--texto', 'falhou']);
    assert.equal(r.status, 0);
    assert.match(r.stdout, /reprovação 1\/10 no haiku\. Ação: devolver ao mesmo agente/);
    assert.equal(fs.existsSync(path.join(esc, 'portal.json')), false);
    assert.equal(rodar(amb, 'emit.mjs', ['fala', '--de', 'am', '--para', 'x', '--texto', 'y']).status, 1);
  } finally { amb.limpar(); }
});
