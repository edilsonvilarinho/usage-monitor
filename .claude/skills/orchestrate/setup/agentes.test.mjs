import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { AGENTES, detectar, estado, instalar } from './agentes.mjs';

const AQUI = path.dirname(fileURLToPath(import.meta.url));

function projeto(arquivos) {
  const raiz = fs.mkdtempSync(path.join(os.tmpdir(), 'agentes-test-'));
  for (const [nome, conteudo] of Object.entries(arquivos)) fs.writeFileSync(path.join(raiz, nome), conteudo);
  return raiz;
}

const limpar = (raiz) => fs.rmSync(raiz, { recursive: true, force: true });

test('detectar lê package.json: stack, front end e scripts declarados', () => {
  const raiz = projeto({
    'package.json': JSON.stringify({ dependencies: { vue: '3' }, devDependencies: { typescript: '5' }, scripts: { test: 'vitest', build: 'vite build' } }),
    'CLAUDE.md': 'regras',
  });
  try {
    const d = detectar(raiz);
    assert.equal(d.stacks[0].nome, 'Node.js + TypeScript');
    assert.deepEqual(d.stacks[0].frontend, ['vue']);
    assert.equal(d.comandos.test, 'npm test');
    assert.equal(d.comandos.build, 'npm run build');
    assert.deepEqual(d.regrasProjeto, ['CLAUDE.md']);
  } finally { limpar(raiz); }
});

test('detectar marca comando de ecossistema como convenção, não como declarado', () => {
  const raiz = projeto({ 'pom.xml': '<project/>' });
  try {
    const d = detectar(raiz);
    assert.equal(d.stacks[0].nome, 'Java (Maven)');
    assert.equal(d.convencao.test, 'mvn test');
    assert.deepEqual(d.comandos, {});
  } finally { limpar(raiz); }
});

test('projeto sem manifesto: nenhuma stack inventada', () => {
  const raiz = projeto({ 'notas.txt': 'x' });
  try {
    const d = detectar(raiz);
    assert.deepEqual(d.stacks, []);
    assert.deepEqual(d.convencao, {});
  } finally { limpar(raiz); }
});

test('estado é pendente até instalar; instalar grava os 5 agentes com contexto do projeto', () => {
  const raiz = projeto({ 'package.json': JSON.stringify({ scripts: { test: 'node --test' } }) });
  try {
    assert.equal(estado(raiz).configurado, false);
    const r = instalar(raiz);
    assert.deepEqual(r.criados, AGENTES);
    const txt = fs.readFileSync(path.join(raiz, '.claude', 'agents', 'backend-dev.md'), 'utf8');
    assert.match(txt, /^---\nname: backend-dev/);
    assert.match(txt, /## Project context/);
    assert.match(txt, /`npm test`/);
    assert.equal(estado(raiz).configurado, true);
  } finally { limpar(raiz); }
});

test('instalar não sobrescreve agente existente sem --sobrescrever', () => {
  const raiz = projeto({});
  try {
    const dir = path.join(raiz, '.claude', 'agents');
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, 'explorer.md'), 'meu');
    const r = instalar(raiz);
    assert.deepEqual(r.mantidos, ['explorer']);
    assert.equal(fs.readFileSync(path.join(dir, 'explorer.md'), 'utf8'), 'meu');
    instalar(raiz, { sobrescrever: true });
    assert.notEqual(fs.readFileSync(path.join(dir, 'explorer.md'), 'utf8'), 'meu');
  } finally { limpar(raiz); }
});

test('CLI: verificar → PENDENTE, dispensar → CONFIGURADO', () => {
  const raiz = projeto({});
  const rodar = (...a) => spawnSync(process.execPath, [path.join(AQUI, 'agentes.mjs'), ...a], {
    encoding: 'utf8', env: { ...process.env, PORTAL_RAIZ: raiz }, windowsHide: true,
  });
  try {
    assert.match(rodar('verificar').stdout, /AGENTES: PENDENTE/);
    assert.equal(rodar('dispensar').status, 0);
    assert.match(rodar('verificar').stdout, /AGENTES: CONFIGURADO \(genericos\)/);
    assert.equal(rodar('invalido').status, 2);
  } finally { limpar(raiz); }
});
