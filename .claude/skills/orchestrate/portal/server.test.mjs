import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import test from 'node:test';
import { criarPortal, parseFaixa, resolverDentro } from './server.mjs';
import { SESSAO, bTool, criarAmbiente, espera, gravarJsonl, linhaAssistant, metaDe, popularSessao } from './testutil.mjs';

/** Requisição crua: o caminho vai exatamente como escrito, sem normalização do cliente. */
function req(porta, caminho, { metodo = 'GET', headers = {}, corpo = null } = {}) {
  return new Promise((resolve, reject) => {
    const r = http.request({ host: '127.0.0.1', port: porta, path: caminho, method: metodo, headers: { Host: `127.0.0.1:${porta}`, ...headers } }, (res) => {
      const partes = [];
      res.on('data', (c) => partes.push(c));
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, corpo: Buffer.concat(partes).toString('utf8') }));
    });
    r.on('error', reject);
    if (corpo) r.write(corpo);
    r.end();
  });
}

async function subir(opcoes = {}) {
  const amb = criarAmbiente();
  popularSessao(amb);
  const portal = criarPortal({ sessao: SESSAO, raiz: amb.raiz, projetos: amb.projetos, gravarPortalJson: false, ...opcoes });
  await portal.iniciar(0);
  return { amb, portal, porta: portal.porta, fim: async () => { await portal.parar(); amb.limpar(); } };
}

test('escuta somente em 127.0.0.1', async () => {
  const s = await subir();
  try {
    assert.equal(s.portal.servidor.address().address, '127.0.0.1');
  } finally { await s.fim(); }
});

test('GET /api/estado devolve o contrato e rejeita Host estranho', async () => {
  const s = await subir();
  try {
    const r = await req(s.porta, '/api/estado');
    assert.equal(r.status, 200);
    const e = JSON.parse(r.corpo);
    assert.equal(e.sessao, SESSAO);
    assert.equal(e.modo, 'ao-vivo');
    assert.equal(e.agentes.length, 3);
    for (const k of ['agentes', 'tarefas', 'falas', 'totais', 'atualizadoEm']) assert.ok(k in e);
    const ruim = await req(s.porta, '/api/estado', { headers: { Host: 'evil.example' } });
    assert.equal(ruim.status, 403);
  } finally { await s.fim(); }
});

test('estáticos não saem de prototipos/ nem expõem .env ou dotfiles', async () => {
  const s = await subir();
  try {
    const caminhos = [
      '/prototipos/../.env', '/prototipos/%2e%2e/.env', '/prototipos/..%2f.env', '/prototipos/..%5c.env',
      '/prototipos/portal/../../.env', '/prototipos/portal/%2e%2e/%2e%2e/.env', '/prototipos//../../fora.txt',
      '/prototipos/..%2f..%2ffora.txt', '/prototipos/portal/p1/..%00/..', '/portal/../.env', '/portal/%2e%2e/.env',
      '/.env', '/prototipos/.env',
    ];
    for (const c of caminhos) {
      const r = await req(s.porta, c);
      assert.ok(r.status >= 400, `${c} -> ${r.status}`);
      assert.ok(!r.corpo.includes('nao-pode-sair') && !r.corpo.includes('fora da pasta'), `${c} vazou conteúdo`);
    }
    // legítimos
    assert.equal((await req(s.porta, '/prototipos/portal/p1/index.html')).status, 200);
    assert.equal((await req(s.porta, '/portal/tokens.css')).status, 200);
    const dir = await req(s.porta, '/prototipos/portal/p1');
    assert.equal(dir.status, 301);
    assert.equal(dir.headers.location, '/prototipos/portal/p1/');
    assert.match((await req(s.porta, '/prototipos/portal/p1/')).corpo, /p1/);
  } finally { await s.fim(); }
});

test('resolverDentro rejeita traversal e symlink que sai da pasta', () => {
  const amb = criarAmbiente();
  try {
    const base = path.join(amb.raiz, 'prototipos');
    assert.equal(resolverDentro(base, '/../.env'), null);
    assert.equal(resolverDentro(base, '/portal/..'), null);
    assert.equal(resolverDentro(base, '/portal\\..\\..'), null);
    assert.ok(resolverDentro(base, '/portal/p1/index.html'));
    try {
      fs.symlinkSync(path.join(amb.tmp, 'fora.txt'), path.join(base, 'atalho.txt'));
      assert.equal(resolverDentro(base, '/atalho.txt'), null);
    } catch (e) {
      if (e.code !== 'EPERM' && e.code !== 'EACCES') throw e; // Windows sem privilégio de symlink
    }
  } finally { amb.limpar(); }
});

test('GET / serve a página do portal direto, sem redirecionamento, e /portal/* serve o kit', async () => {
  const s = await subir();
  try {
    const a = await req(s.porta, '/');
    assert.equal(a.status, 200);
    assert.equal(a.headers.location, undefined);
    assert.match(a.headers['content-type'], /text\/html/);
    assert.match(a.corpo, /portal-dados\.js/);
    const kit = await req(s.porta, '/portal/portal-dados.js');
    assert.equal(kit.status, 200);
    assert.match(kit.headers['content-type'], /javascript/);
    assert.equal((await req(s.porta, '/portal/exemplo.json')).status, 200);
    for (const ruim of ['/portal/', '/portal/../server.mjs', '/portal/%2e%2e/server.mjs', '/portal/..%2fserver.mjs', '/portal/nao-existe.js']) {
      assert.equal((await req(s.porta, ruim)).status, 404, ruim);
    }
    assert.equal((await req(s.porta, '/portal/index.html')).status, 200);
  } finally { await s.fim(); }
});

test('GET /api/agente/:id devolve detalhe e 404 para desconhecido', async () => {
  const s = await subir();
  try {
    const r = await req(s.porta, '/api/agente/aaa111');
    assert.equal(r.status, 200);
    const d = JSON.parse(r.corpo);
    assert.equal(d.prompt, 'Tarefa B11. faça isto');
    assert.ok(Array.isArray(d.passos) && d.passos.length > 0);
    assert.equal((await req(s.porta, '/api/agente/zzz')).status, 404);
    assert.equal((await req(s.porta, '/api/agente/..%2f..')).status, 400);
  } finally { await s.fim(); }
});

test('SSE entrega o estado e, sem reiniciar, um subagente novo criado depois', async () => {
  const s = await subir();
  try {
    const recebidos = [];
    let buf = '';
    const res = await new Promise((resolve, reject) => {
      http.get({ host: '127.0.0.1', port: s.porta, path: '/api/eventos', headers: { Host: `127.0.0.1:${s.porta}` } }, resolve).on('error', reject);
    });
    assert.match(res.headers['content-type'], /text\/event-stream/);
    res.setEncoding('utf8');
    res.on('data', (d) => {
      buf += d;
      let i;
      while ((i = buf.indexOf('\n\n')) >= 0) {
        const bloco = buf.slice(0, i);
        buf = buf.slice(i + 2);
        const m = /^event: estado\ndata: (.*)$/m.exec(bloco);
        if (m) recebidos.push(JSON.parse(m[1]));
      }
    });
    await espera(300);
    assert.equal(recebidos.at(-1).agentes.length, 3);

    gravarJsonl(path.join(s.amb.dirSub, 'agent-eee555.jsonl'), [
      linhaAssistant({ t: Date.now(), id: 'k1', agentId: 'eee555', entrada: 1, saida: 2, blocos: [bTool('q1', 'Read', { file_path: 'x/novo.ts' })] }),
    ]);
    fs.writeFileSync(path.join(s.amb.dirSub, 'agent-eee555.meta.json'), JSON.stringify(metaDe('explorer', 'Novo', 'tu-n', false)));
    const limite = Date.now() + 4000;
    while (Date.now() < limite && recebidos.at(-1).agentes.length < 4) await espera(100);
    const ultimo = recebidos.at(-1);
    assert.equal(ultimo.agentes.length, 4);
    assert.equal(ultimo.agentes.find((a) => a.id === 'eee555').atividade, 'lendo novo.ts');
    // limite de 2 estados por segundo
    const t = recebidos.length;
    assert.ok(t <= 2 + Math.ceil((Date.now() - (limite - 4000)) / 500), `muitos eventos: ${t}`);
    res.destroy();
  } finally { await s.fim(); }
});

test('POST /api/sessao exige cabeçalhos e valida o id', async () => {
  const s = await subir();
  try {
    const corpo = JSON.stringify({ sessao: 'outra-sessao' });
    const h = { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(corpo) };
    assert.equal((await req(s.porta, '/api/sessao', { metodo: 'POST', headers: h, corpo })).status, 403);
    const ruim = JSON.stringify({ sessao: '../x' });
    assert.equal((await req(s.porta, '/api/sessao', { metodo: 'POST', headers: { ...h, 'X-Portal': '1', 'Content-Length': Buffer.byteLength(ruim) }, corpo: ruim })).status, 400);
    const ok = await req(s.porta, '/api/sessao', { metodo: 'POST', headers: { ...h, 'X-Portal': '1' }, corpo });
    assert.equal(ok.status, 200);
    const e = JSON.parse((await req(s.porta, '/api/estado')).corpo);
    assert.equal(e.sessao, 'outra-sessao');
    assert.equal(e.agentes.length, 1);
  } finally { await s.fim(); }
});

test('modo replay reproduz a sessão em tempo acelerado', async () => {
  const amb = criarAmbiente();
  popularSessao(amb, Date.now() - 600_000);
  const portal = criarPortal({ replay: SESSAO, velocidade: 100_000, raiz: amb.raiz, projetos: amb.projetos });
  await portal.iniciar(0);
  try {
    assert.equal(portal.sessao, SESSAO);
    await espera(1500); // 1,5 s * 100.000 cobre toda a sessão sintética
    const e = JSON.parse((await req(portal.porta, '/api/estado')).corpo);
    assert.equal(e.modo, 'replay');
    assert.equal(e.agentes.length, 3);
    assert.equal(e.agentes.find((a) => a.id === 'aaa111').estado, 'concluido');
    assert.equal((await req(portal.porta, '/api/sessao', { metodo: 'POST', headers: { 'Content-Type': 'application/json', 'X-Portal': '1' }, corpo: '{}' })).status, 409);
  } finally { await portal.parar(); amb.limpar(); }
});

test('estáticos respondem Range com 206, Content-Range e Accept-Ranges, em stream', async () => {
  const s = await subir();
  try {
    const dirVideo = path.join(s.amb.raiz, 'prototipos', 'video');
    fs.mkdirSync(dirVideo, { recursive: true });
    const tamanho = 3 * 1024 * 1024 + 7;
    const dados = Buffer.alloc(tamanho);
    for (let i = 0; i < tamanho; i++) dados[i] = i % 251;
    fs.writeFileSync(path.join(dirVideo, 'v.mp4'), dados);
    const get = (range) => new Promise((resolve, reject) => {
      http.get({ host: '127.0.0.1', port: s.porta, path: '/prototipos/video/v.mp4', headers: { Host: `127.0.0.1:${s.porta}`, ...(range ? { Range: range } : {}) } }, (res) => {
        const p = [];
        res.on('data', (c) => p.push(c));
        res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, buf: Buffer.concat(p) }));
      }).on('error', reject);
    });

    const a = await get('bytes=0-99');
    assert.equal(a.status, 206);
    assert.equal(a.buf.length, 100);
    assert.equal(a.headers['content-range'], `bytes 0-99/${tamanho}`);
    assert.equal(a.headers['content-length'], '100');
    assert.equal(a.headers['accept-ranges'], 'bytes');
    assert.equal(a.headers['content-type'], 'video/mp4');
    assert.ok(a.buf.equals(dados.subarray(0, 100)));

    const meio = await get('bytes=1000000-1000009');
    assert.ok(meio.buf.equals(dados.subarray(1000000, 1000010)));
    const aberto = await get(`bytes=${tamanho - 5}-`);
    assert.equal(aberto.headers['content-range'], `bytes ${tamanho - 5}-${tamanho - 1}/${tamanho}`);
    assert.equal(aberto.buf.length, 5);
    const sufixo = await get('bytes=-10');
    assert.equal(sufixo.status, 206);
    assert.ok(sufixo.buf.equals(dados.subarray(tamanho - 10)));
    const alemFim = await get(`bytes=0-${tamanho * 2}`);
    assert.equal(alemFim.headers['content-range'], `bytes 0-${tamanho - 1}/${tamanho}`);

    const fora = await get(`bytes=${tamanho}-`);
    assert.equal(fora.status, 416);
    assert.equal(fora.headers['content-range'], `bytes */${tamanho}`);

    const inteiro = await get();
    assert.equal(inteiro.status, 200);
    assert.equal(inteiro.headers['accept-ranges'], 'bytes');
    assert.equal(inteiro.buf.length, tamanho);
    assert.ok(inteiro.buf.equals(dados));
    const multi = await get('bytes=0-1,5-6');
    assert.equal(multi.status, 200);
  } finally { await s.fim(); }
});

test('parseFaixa', () => {
  assert.deepEqual(parseFaixa('bytes=0-99', 1000), { inicio: 0, fim: 99 });
  assert.deepEqual(parseFaixa('bytes=-100', 1000), { inicio: 900, fim: 999 });
  assert.equal(parseFaixa('bytes=1000-', 1000), 'invalida');
  assert.equal(parseFaixa('bytes=5-2', 1000), 'invalida');
  assert.equal(parseFaixa('bytes=-0', 1000), 'invalida');
  assert.equal(parseFaixa('bytes=0-1,3-4', 1000), null);
});
