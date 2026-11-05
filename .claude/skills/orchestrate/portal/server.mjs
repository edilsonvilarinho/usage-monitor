// Servidor do portal do escritório. Somente 127.0.0.1, sem dependências.
// Uso: node server.mjs --sessao <id> [--porta 4599]
//      node server.mjs --replay <sessao> [--velocidade 20] [--porta 4599]

import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { Coletor, dirEscritorio, parseArgs, raizRepo, sessaoValida } from './collector.mjs';

const HOST = '127.0.0.1';
const DIR_WEB = path.join(path.dirname(fileURLToPath(import.meta.url)), 'web'); // página e kit da skill, independentes de `raiz`
const PORTA_PADRAO = 4599;
const TICK_MS = 500; // no máximo 2 estados por segundo
const PING_MS = 15_000;

const MIME = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8', '.json': 'application/json; charset=utf-8', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp', '.gif': 'image/gif',
  '.ico': 'image/x-icon', '.woff2': 'font/woff2', '.woff': 'font/woff', '.txt': 'text/plain; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8', '.mp3': 'audio/mpeg', '.wav': 'audio/wav', '.mp4': 'video/mp4', '.webm': 'video/webm',
};

const CABECALHOS_BASE = {
  'Cache-Control': 'no-store',
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
};

function responder(res, status, corpo, tipo = 'text/plain; charset=utf-8', extra = {}) {
  res.writeHead(status, { ...CABECALHOS_BASE, 'Content-Type': tipo, ...extra });
  res.end(corpo);
}
const json = (res, status, obj) => responder(res, status, JSON.stringify(obj), 'application/json; charset=utf-8');

/** Resolve `rel` dentro de `baseDir`. Retorna o caminho real ou null se sair da pasta. */
export function resolverDentro(baseDir, rel) {
  if (rel.includes('\0') || rel.includes('\\')) return null;
  const partes = rel.split('/').filter((p) => p !== '');
  for (const p of partes) if (p === '..' || p === '.' || p.startsWith('.')) return null;
  const alvo = path.resolve(baseDir, ...partes);
  const baseReal = (() => { try { return fs.realpathSync(baseDir); } catch { return null; } })();
  if (!baseReal) return null;
  let real;
  try { real = fs.realpathSync(alvo); } catch { return null; }
  const rel2 = path.relative(baseReal, real);
  if (rel2.startsWith('..') || path.isAbsolute(rel2)) return null;
  return real;
}

/**
 * Interpreta `Range: bytes=...` (faixa única). Devolve {inicio, fim}, 'invalida' (416) ou null (ignorar o cabeçalho).
 */
export function parseFaixa(cabecalho, tamanho) {
  const m = /^bytes=(\d*)-(\d*)$/.exec(cabecalho.trim());
  if (!m || (m[1] === '' && m[2] === '')) return /^bytes=/.test(cabecalho.trim()) && !cabecalho.includes(',') ? 'invalida' : null;
  if (tamanho === 0) return 'invalida';
  let inicio;
  let fim;
  if (m[1] === '') { // sufixo: últimos N bytes
    const n = Number(m[2]);
    if (n === 0) return 'invalida';
    inicio = Math.max(0, tamanho - n);
    fim = tamanho - 1;
  } else {
    inicio = Number(m[1]);
    fim = m[2] === '' ? tamanho - 1 : Math.min(Number(m[2]), tamanho - 1);
    if (inicio >= tamanho || inicio > fim) return 'invalida';
  }
  return { inicio, fim };
}

export function criarPortal(opcoes = {}) {
  const raiz = opcoes.raiz ?? raizRepo();
  const replay = opcoes.replay ?? null;
  const velocidade = Number(opcoes.velocidade) > 0 ? Number(opcoes.velocidade) : 20;
  let sessao = replay ?? opcoes.sessao ?? '';
  const coletor = new Coletor({ sessao, raiz, projetos: opcoes.projetos });
  const clientes = new Set();
  let porta = 0;
  let ultimoJson = '';
  let ultimoEstado = null;
  let relogioInicio = Date.now();
  let timer = null;
  let pingTimer = null;

  const modo = replay ? 'replay' : 'ao-vivo';

  function calcular() {
    if (!replay) return coletor.estado({ agora: Date.now(), modo });
    const lim = coletor.limites();
    if (!lim) return coletor.estado({ agora: Date.now(), ate: Date.now(), modo });
    const virtual = Math.min(lim.min + (Date.now() - relogioInicio) * velocidade, lim.max);
    return coletor.estado({ agora: virtual, ate: virtual, modo });
  }

  function tick() {
    coletor.poll();
    const est = calcular();
    ultimoEstado = est;
    const { atualizadoEm, ...resto } = est;
    const chave = JSON.stringify(resto);
    if (chave === ultimoJson) return;
    ultimoJson = chave;
    const dados = `event: estado\ndata: ${JSON.stringify(est)}\n\n`;
    for (const c of clientes) c.write(dados);
  }

  function hostValido(req) {
    const h = String(req.headers.host ?? '').toLowerCase();
    return h === `127.0.0.1:${porta}` || h === `localhost:${porta}`;
  }

  function servirArquivo(req, res, caminho) {
    let st;
    try { st = fs.statSync(caminho); } catch { return responder(res, 404, 'não encontrado'); }
    if (st.isDirectory()) {
      const idx = path.join(caminho, 'index.html');
      if (!fs.existsSync(idx)) return responder(res, 404, 'não encontrado');
      caminho = idx;
    }
    try { st = fs.statSync(caminho); } catch { return responder(res, 404, 'não encontrado'); }
    const tamanho = st.size;
    const tipo = MIME[path.extname(caminho).toLowerCase()] ?? 'application/octet-stream';
    const cab = { ...CABECALHOS_BASE, 'Content-Type': tipo, 'Accept-Ranges': 'bytes' };

    let inicio = 0;
    let fim = tamanho - 1;
    let status = 200;
    const faixa = req.headers.range ? parseFaixa(String(req.headers.range), tamanho) : null;
    if (faixa === 'invalida') {
      res.writeHead(416, { ...cab, 'Content-Range': `bytes */${tamanho}`, 'Content-Length': 0 });
      return res.end();
    }
    if (faixa) {
      ({ inicio, fim } = faixa);
      status = 206;
      cab['Content-Range'] = `bytes ${inicio}-${fim}/${tamanho}`;
    }
    cab['Content-Length'] = tamanho === 0 ? 0 : fim - inicio + 1;
    res.writeHead(status, cab);
    if (req.method === 'HEAD' || tamanho === 0) return res.end();
    // stream: arquivos grandes (mp4 de dezenas de MB) nunca são carregados em memória
    const leitura = fs.createReadStream(caminho, { start: inicio, end: fim });
    leitura.on('error', () => res.destroy());
    res.on('close', () => leitura.destroy());
    leitura.pipe(res);
  }

  function lerCorpo(req, limite = 1024) {
    return new Promise((resolve, reject) => {
      let total = 0;
      const partes = [];
      req.on('data', (c) => {
        total += c.length;
        if (total > limite) { reject(new Error('corpo grande demais')); req.destroy(); return; }
        partes.push(c);
      });
      req.on('end', () => resolve(Buffer.concat(partes).toString('utf8')));
      req.on('error', reject);
    });
  }

  async function tratar(req, res) {
    if (!hostValido(req)) return responder(res, 403, 'host não permitido');
    const bruto = req.url ?? '/';
    const q = bruto.indexOf('?');
    const caminhoBruto = q >= 0 ? bruto.slice(0, q) : bruto;
    let caminho;
    try { caminho = decodeURIComponent(caminhoBruto); } catch { return responder(res, 400, 'url inválida'); }

    if (req.method === 'POST' && caminho === '/api/sessao') {
      if (replay) return json(res, 409, { erro: 'portal em replay' });
      if (req.headers['x-portal'] !== '1' || !String(req.headers['content-type'] ?? '').startsWith('application/json')) {
        return json(res, 403, { erro: 'cabeçalhos ausentes' });
      }
      let corpo;
      try { corpo = JSON.parse(await lerCorpo(req)); } catch { return json(res, 400, { erro: 'json inválido' }); }
      if (!corpo || !sessaoValida(corpo.sessao)) return json(res, 400, { erro: 'sessão inválida' });
      trocarSessao(corpo.sessao);
      return json(res, 200, { sessao });
    }

    if (req.method !== 'GET' && req.method !== 'HEAD') return responder(res, 405, 'método não permitido');

    if (caminho === '/api/estado') return json(res, 200, calcular());
    if (caminho === '/api/eventos') {
      res.writeHead(200, { ...CABECALHOS_BASE, 'Content-Type': 'text/event-stream; charset=utf-8', Connection: 'keep-alive' });
      res.write(`retry: 2000\nevent: estado\ndata: ${JSON.stringify(ultimoEstado ?? calcular())}\n\n`);
      clientes.add(res);
      req.on('close', () => clientes.delete(res));
      return undefined;
    }
    if (caminho.startsWith('/api/agente/')) {
      const id = caminho.slice('/api/agente/'.length);
      if (!/^[A-Za-z0-9_-]{1,80}$/.test(id)) return json(res, 400, { erro: 'id inválido' });
      coletor.poll();
      let ate = null;
      if (replay) { const l = coletor.limites(); ate = l ? Math.min(l.min + (Date.now() - relogioInicio) * velocidade, l.max) : null; }
      const d = coletor.detalhe(id, { ate });
      return d ? json(res, 200, d) : json(res, 404, { erro: 'agente não encontrado' });
    }
    if (caminho.startsWith('/api/')) return json(res, 404, { erro: 'rota inexistente' });

    if (caminho === '/') return servirArquivo(req, res, path.join(DIR_WEB, 'index.html'));
    if (caminho.startsWith('/portal/')) {
      const real = resolverDentro(DIR_WEB, caminho.slice('/portal'.length));
      if (!real || fs.statSync(real).isDirectory()) return responder(res, 404, 'não encontrado');
      return servirArquivo(req, res, real);
    }
    if (caminho === '/prototipos' || caminho.startsWith('/prototipos/')) {
      const real = resolverDentro(path.join(raiz, 'prototipos'), caminho.slice('/prototipos'.length));
      if (!real) return responder(res, 404, 'não encontrado');
      // pasta sem barra final: redireciona para resolver caminhos relativos
      if (fs.statSync(real).isDirectory() && !caminho.endsWith('/')) {
        return responder(res, 301, '', 'text/plain; charset=utf-8', { Location: caminho + '/' });
      }
      return servirArquivo(req, res, real);
    }
    return responder(res, 404, 'não encontrado');
  }

  const servidor = http.createServer((req, res) => {
    tratar(req, res).catch(() => { if (!res.headersSent) responder(res, 500, 'erro interno'); else res.end(); });
  });

  function arquivoPortal() { return path.join(dirEscritorio(raiz), 'portal.json'); }
  function gravarPortalJson() {
    if (replay || opcoes.gravarPortalJson === false) return;
    fs.mkdirSync(dirEscritorio(raiz), { recursive: true });
    fs.writeFileSync(arquivoPortal(), JSON.stringify({ pid: process.pid, porta, sessao, iniciadoEm: new Date().toISOString() }, null, 2));
  }
  function removerPortalJson() {
    if (replay || opcoes.gravarPortalJson === false) return;
    try {
      const o = JSON.parse(fs.readFileSync(arquivoPortal(), 'utf8'));
      if (o.pid === process.pid) fs.unlinkSync(arquivoPortal());
    } catch { /* já removido */ }
  }

  function trocarSessao(nova) {
    sessao = nova;
    coletor.trocar(nova);
    ultimoJson = '';
    gravarPortalJson();
    tick();
  }

  function iniciar(portaDesejada = PORTA_PADRAO) {
    return new Promise((resolve, reject) => {
      servidor.once('error', reject);
      servidor.listen(portaDesejada, HOST, () => {
        servidor.off('error', reject);
        porta = servidor.address().port;
        relogioInicio = Date.now();
        coletor.poll();
        ultimoEstado = calcular();
        timer = setInterval(tick, TICK_MS);
        pingTimer = setInterval(() => { for (const c of clientes) c.write(': ping\n\n'); }, PING_MS);
        try { gravarPortalJson(); } catch (e) { servidor.close(); reject(e); return; }
        resolve({ porta, url: `http://${HOST}:${porta}/` });
      });
    });
  }

  function parar() {
    clearInterval(timer); clearInterval(pingTimer);
    removerPortalJson();
    for (const c of clientes) { try { c.end(); } catch { /* ignora */ } }
    clientes.clear();
    return new Promise((resolve) => { servidor.close(() => resolve()); servidor.closeAllConnections?.(); });
  }

  return { servidor, iniciar, parar, coletor, get porta() { return porta; }, get sessao() { return sessao; }, calcular };
}

// ---------------------------------------------------------------- execução direta

const direto = process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
if (direto) {
  const args = parseArgs(process.argv.slice(2));
  const porta = Number(args.porta ?? PORTA_PADRAO);
  const replay = typeof args.replay === 'string' ? args.replay : null;
  const sessao = typeof args.sessao === 'string' ? args.sessao : null;
  const id = replay ?? sessao;
  if (!sessaoValida(id) || !Number.isInteger(porta) || porta < 0 || porta > 65535) {
    console.error('uso: server.mjs --sessao <id> [--porta 4599] | --replay <sessao> [--velocidade 20]');
    process.exit(2);
  }
  const portal = criarPortal({ sessao, replay, velocidade: args.velocidade, raiz: args.raiz ? path.resolve(String(args.raiz)) : undefined });
  portal.iniciar(porta).then(({ url }) => {
    console.log(url);
    const sair = () => portal.parar().then(() => process.exit(0));
    process.on('SIGINT', sair);
    process.on('SIGTERM', sair);
  }).catch((e) => {
    console.error(e.code === 'EADDRINUSE' ? `porta ${porta} ocupada` : `falha ao iniciar: ${e.message}`);
    process.exit(1);
  });
}
