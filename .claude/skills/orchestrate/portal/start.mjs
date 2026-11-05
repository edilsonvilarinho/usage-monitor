// Sobe o portal em processo destacado. Idempotente.
// Uso: node start.mjs --sessao <id> [--porta 4599] [--abrir nao]
// Imprime somente a URL.

import { spawn } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { dirEscritorio, parseArgs, raizRepo, sessaoValida } from './collector.mjs';

const AQUI = path.dirname(fileURLToPath(import.meta.url));
const HOST = '127.0.0.1';

const args = parseArgs(process.argv.slice(2));
const sessao = args.sessao;
const porta = Number(args.porta ?? 4599);
const abrir = args.abrir !== 'nao';
const raiz = raizRepo();
const arquivoPortal = path.join(dirEscritorio(raiz), 'portal.json');

if (!sessaoValida(sessao) || !Number.isInteger(porta) || porta < 1 || porta > 65535) {
  console.error('uso: start.mjs --sessao <id> [--porta 4599] [--abrir nao]');
  process.exit(2);
}

function requisitar(portaAlvo, metodo, caminho, corpo = null) {
  return new Promise((resolve) => {
    const dados = corpo ? JSON.stringify(corpo) : null;
    const req = http.request({
      host: HOST, port: portaAlvo, path: caminho, method: metodo, timeout: 1500,
      headers: dados ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(dados), 'X-Portal': '1' } : {},
    }, (res) => {
      const partes = [];
      res.on('data', (c) => partes.push(c));
      res.on('end', () => {
        try { resolve({ status: res.statusCode, corpo: JSON.parse(Buffer.concat(partes).toString('utf8')) }); } catch { resolve(null); }
      });
    });
    req.on('timeout', () => { req.destroy(); resolve(null); });
    req.on('error', () => resolve(null));
    if (dados) req.write(dados);
    req.end();
  });
}

/** Retorna o estado se há um portal nosso respondendo na porta; senão null. */
async function sondar(portaAlvo) {
  const r = await requisitar(portaAlvo, 'GET', '/api/estado');
  return r && r.status === 200 && r.corpo && Array.isArray(r.corpo.agentes) ? r.corpo : null;
}

function lerPortalJson() {
  try { return JSON.parse(fs.readFileSync(arquivoPortal, 'utf8')); } catch { return null; }
}

function abrirNavegador(url) {
  const p = process.platform;
  const [cmd, argv] = p === 'win32' ? ['cmd', ['/c', 'start', '""', url]] : p === 'darwin' ? ['open', [url]] : ['xdg-open', [url]];
  try {
    const f = spawn(cmd, argv, { detached: true, stdio: 'ignore', windowsHide: true, windowsVerbatimArguments: p === 'win32' });
    f.on('error', () => { /* sem navegador: a URL já foi impressa */ });
    f.unref();
  } catch { /* ignora */ }
}

const espera = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const info = lerPortalJson();
  const portaAtiva = info && Number.isInteger(info.porta) ? info.porta : porta;

  // 1. Já existe portal nosso respondendo? Só garante a sessão.
  for (const p of new Set([portaAtiva, porta])) {
    const estado = await sondar(p);
    if (!estado) continue;
    if (estado.modo === 'replay') { console.error(`porta ${p} em modo replay`); process.exit(1); }
    if (estado.sessao !== sessao) {
      const r = await requisitar(p, 'POST', '/api/sessao', { sessao });
      if (!r || r.status !== 200) { console.error('falha ao trocar a sessão observada'); process.exit(1); }
    }
    console.log(`http://${HOST}:${p}/`);
    return;
  }

  // 2. Sobe um novo processo destacado.
  const filho = spawn(process.execPath, [path.join(AQUI, 'server.mjs'), '--sessao', sessao, '--porta', String(porta)], {
    detached: true, stdio: 'ignore', windowsHide: true, env: process.env,
  });
  filho.unref();

  // 3. Espera o portal responder. Se outro start venceu a corrida, o nosso filho sai por EADDRINUSE.
  const limite = Date.now() + 8000;
  while (Date.now() < limite) {
    await espera(150);
    const estado = await sondar(porta);
    if (!estado) continue; // se o filho saiu por EADDRINUSE, o vencedor pode ainda estar subindo
    if (estado.sessao !== sessao) await requisitar(porta, 'POST', '/api/sessao', { sessao });
    const vencedor = lerPortalJson();
    const url = `http://${HOST}:${porta}/`;
    console.log(url);
    if (abrir && vencedor && vencedor.pid === filho.pid) abrirNavegador(url);
    return;
  }
  console.error(`o portal não respondeu na porta ${porta} (porta ocupada por outro processo?)`);
  process.exit(1);
}

main().then(() => process.exit(0));
