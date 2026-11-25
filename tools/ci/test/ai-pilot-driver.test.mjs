// Testes do driver de tela do piloto (#653, A03a). Tudo com streams falsos: nenhum processo, nenhuma rede, nenhum disco.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { readFileSync } from 'node:fs';
import { PassThrough } from 'node:stream';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createScreenDriver } from '../ai-pilot-driver.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const MODULE_PATH = resolve(HERE, '..', 'ai-pilot-driver.mjs');

// Filho falso: stdin grava as linhas pedidas; stdout é um stream que o teste alimenta; kill() conta chamadas.
function fakeChild() {
    const child = new EventEmitter();
    child.stdin = {
        written: [],
        ended: false,
        write(text) { this.written.push(text) },
        end() { this.ended = true },
    };
    child.stdout = new PassThrough();
    child.killed = 0;
    child.kill = () => { child.killed += 1 };
    return child;
}

const settle = () => new Promise((resolve) => setImmediate(resolve));
const requests = (child) => child.stdin.written.map((line) => JSON.parse(line));
const reply = (child, message) => child.stdout.write(`${JSON.stringify(message)}\n`);

test('ready: resolve com largura e altura da linha ready', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child });
    reply(child, { ready: true, width: 1920, height: 1080 });
    assert.deepEqual(await driver.ready(), { width: 1920, height: 1080 });
});

test('ready: espera a linha ready quando ela chega depois da chamada', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child });
    const pending = driver.ready();
    await settle();
    reply(child, { ready: true, width: 1000, height: 700 });
    assert.deepEqual(await pending, { width: 1000, height: 700 });
});

test('ready sem linha no teto rejeita com tempo esgotado', async () => {
    const driver = createScreenDriver({ child: fakeChild(), timeoutMs: 20 });
    await assert.rejects(driver.ready(), /tempo/);
});

test('linha que nao e JSON e ignorada, inclusive entre pedido e resposta', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child });
    child.stdout.write('WARNING: aviso do pwsh\n');
    reply(child, { ready: true, width: 800, height: 600 });
    child.stdout.write('lixo {nao json\n\n');
    assert.deepEqual(await driver.ready(), { width: 800, height: 600 });
    const pending = driver.screenshot();
    await settle();
    child.stdout.write('ruido no meio\n');
    reply(child, { id: 1, ok: true, image: 'AAAA' });
    assert.equal(await pending, 'AAAA');
});

test('pedidos entram em fila: um por vez, ids crescentes, o segundo so sai depois da resposta do primeiro', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child });
    const first = driver.screenshot();
    const second = driver.perform({ tool: 'click', x: 1, y: 2 });
    await settle();
    assert.deepEqual(requests(child), [{ id: 1, tool: 'screenshot' }]);
    reply(child, { id: 1, ok: true, image: 'AAAA' });
    assert.equal(await first, 'AAAA');
    await settle();
    assert.deepEqual(requests(child), [{ id: 1, tool: 'screenshot' }, { id: 2, tool: 'click', x: 1, y: 2 }]);
    reply(child, { id: 2, ok: true });
    assert.equal(await second, undefined);
});

test('resposta com id diferente do pendente e ignorada', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child });
    const pending = driver.screenshot();
    await settle();
    reply(child, { id: 99, ok: true, image: 'ERRADA' });
    await settle();
    reply(child, { id: 1, ok: true, image: 'CERTA' });
    assert.equal(await pending, 'CERTA');
});

test('linha JSON partida em dois pedacos e remontada', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child });
    const pending = driver.screenshot();
    await settle();
    const line = `${JSON.stringify({ id: 1, ok: true, image: 'AAAA' })}\n`;
    const cut = Math.floor(line.length / 2);
    child.stdout.write(line.slice(0, cut));
    await settle();
    child.stdout.write(line.slice(cut));
    assert.equal(await pending, 'AAAA');
});

test('chunk em Buffer que corta um caractere UTF-8 e decodificado sem perda', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child });
    const pending = driver.perform({ tool: 'type', text: 'x' });
    await settle();
    const bytes = Buffer.from(`${JSON.stringify({ id: 1, ok: false, error: 'ação recusada' })}\n`);
    const cut = bytes.indexOf(0xc3) + 1;
    child.stdout.write(bytes.subarray(0, cut));
    await settle();
    child.stdout.write(bytes.subarray(cut));
    await assert.rejects(pending, { message: 'ação recusada' });
});

test('screenshot sem campo image rejeita', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child });
    const pending = driver.screenshot();
    await settle();
    reply(child, { id: 1, ok: true });
    await assert.rejects(pending, /imagem/);
});

test('perform rejeita com o erro do filho', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child });
    const pending = driver.perform({ tool: 'key', name: 'Enter' });
    await settle();
    reply(child, { id: 1, ok: false, error: 'janela sem foco' });
    await assert.rejects(pending, { message: 'janela sem foco' });
});

test('estouro de tempo rejeita o pendente e os proximos, e mata o filho', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child, timeoutMs: 20 });
    const first = driver.screenshot();
    const second = driver.perform({ tool: 'wait', ms: 0 });
    const firstCheck = assert.rejects(first, /tempo/);
    const secondCheck = assert.rejects(second, /tempo/);
    await firstCheck;
    await secondCheck;
    assert.equal(child.killed, 1);
    assert.equal(requests(child).length, 1, 'o proximo pedido nao chega ao filho');
});

test('saida do filho no meio rejeita o pendente e os proximos', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child, timeoutMs: 1000 });
    const first = driver.screenshot();
    const second = driver.screenshot();
    const firstCheck = assert.rejects(first, /saiu/);
    const secondCheck = assert.rejects(second, /saiu/);
    await settle();
    child.emit('exit', 3);
    await firstCheck;
    await secondCheck;
    assert.equal(requests(child).length, 1);
});

test('close fecha o stdin e resolve quando o filho sai, sem matar', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child, timeoutMs: 1000 });
    const closing = driver.close();
    assert.equal(child.stdin.ended, true);
    child.emit('exit', 0);
    await closing;
    assert.equal(child.killed, 0);
});

test('close mata o filho quando ele nao sai no teto', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child, timeoutMs: 20 });
    await driver.close();
    assert.equal(child.killed, 1);
});

test('pedido depois de close rejeita sem escrever no filho', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child, timeoutMs: 1000 });
    child.emit('exit', 0);
    await driver.close();
    await assert.rejects(driver.screenshot(), /fechado|saiu/);
    assert.equal(child.stdin.written.length, 0);
});

test('zoom: envia o retangulo e devolve a imagem ampliada', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child });
    const pending = driver.zoom({ x: 10, y: 20, w: 300, h: 200 });
    await settle();
    assert.deepEqual(requests(child), [{ id: 1, tool: 'zoom', x: 10, y: 20, w: 300, h: 200 }]);
    reply(child, { id: 1, ok: true, image: 'ZOOM' });
    assert.equal(await pending, 'ZOOM');
});

test('zoom sem campo image rejeita', async () => {
    const child = fakeChild();
    const driver = createScreenDriver({ child });
    const pending = driver.zoom({ x: 0, y: 0, w: 16, h: 16 });
    await settle();
    reply(child, { id: 1, ok: true });
    await assert.rejects(pending, /imagem/);
});

test('ai-pilot-driver.mjs: fonte sem fs, process.env nem child_process', () => {
    const source = readFileSync(MODULE_PATH, 'utf8');
    assert.equal(source.includes('node:fs'), false);
    assert.equal(source.includes('process.env'), false);
    assert.equal(source.includes('child_process'), false);
});
