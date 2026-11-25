// Janela do cover (#676, item d): a ação aceita é a última de ponteiro antes de after, e nenhuma captura inteira fica depois dela.
// Fakes determinísticos: sem rede, sem disco, sem processo.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createBudget, runScript } from '../ai-pilot.mjs';

const SCREEN = { width: 1920, height: 1080 };
const IMAGE = 'AAAA';
const DEFAULT_USAGE = { cost: 0.001, prompt_tokens: 10, completion_tokens: 5 };

const reply = (toolCalls) => ({ message: { content: null, tool_calls: toolCalls }, usage: DEFAULT_USAGE });
const call = (id, name, args) => ({ id, type: 'function', function: { name, arguments: JSON.stringify(args) } });
const shot = (id) => call(id, 'screenshot', {});
const moveA = (id) => call(id, 'move', { x: 10, y: 10 });
const moveB = (id) => call(id, 'move', { x: 20, y: 20 });
const clickAt = (id) => call(id, 'click', { x: 30, y: 30 });
const keyEsc = (id) => call(id, 'key', { name: 'Escape' });
const zoomArea = (id) => call(id, 'zoom', { x: 100, y: 100, w: 200, h: 200 });
const coverCall = (id, item, before, after, extra = {}) => call(id, 'cover', { item, before, after, seen: 'marca', ...extra });
const DONE = reply([call('done', 'done', { summary: 'fim' })]);

// Respostas na ordem; a última repete quando acabam.
function fakeClient(replies) {
    const calls = [];
    return {
        calls,
        async complete(request) {
            calls.push(structuredClone(request));
            return replies[Math.min(calls.length - 1, replies.length - 1)];
        },
    };
}

function fakeDriver() {
    const performed = [];
    return {
        performed,
        async screenshot() {
            return IMAGE;
        },
        async zoom() {
            return IMAGE;
        },
        async perform(action) {
            performed.push(action);
        },
    };
}

// Roda os turnos em ordem; a captura inicial é a 1. Sem cover aceito, o done termina o roteiro incompleto.
async function play(turns) {
    const client = fakeClient([...turns.map((calls) => reply(calls)), DONE]);
    return runScript({
        client,
        driver: fakeDriver(),
        script: 'roteiro',
        rules: 'regras',
        screen: SCREEN,
        budget: createBudget(),
        coverage: ['drag', 'minimize'],
    });
}

const accepted = (result) => result.coverage.map((cover) => cover.item);

test('janela: controle, move entre before e after aceita o cover', async () => {
    const result = await play([[moveA('m')], [shot('s2')], [coverCall('c', 'drag', 1, 2)]]);
    assert.deepEqual(accepted(result), ['drag']);
});

test('janela: sem ação dentro, cover normal com ação antes de before e recusado', async () => {
    const result = await play([[moveA('m')], [shot('s2')], [shot('s3')], [coverCall('c', 'drag', 2, 3)]]);
    assert.deepEqual(accepted(result), []);
});

test('janela: impossível exige ação dentro; ponteiro antes de before não conta', async () => {
    const result = await play([[moveA('m')], [shot('s2')], [shot('s3')], [coverCall('c', 'drag', 2, 3, { possible: false })]]);
    assert.deepEqual(accepted(result), []);
});

test('janela: tecla depois do ponteiro não tira a ação de ponteiro', async () => {
    const result = await play([[moveA('m')], [keyEsc('k')], [shot('s2')], [coverCall('c', 'drag', 1, 2)]]);
    assert.deepEqual(accepted(result), ['drag']);
});

test('janela: captura inteira antes da ação não recusa o cover', async () => {
    const result = await play([[shot('s2')], [moveA('m')], [shot('s3')], [coverCall('c', 'drag', 1, 3)]]);
    assert.deepEqual(accepted(result), ['drag']);
});

test('janela: captura inteira depois da ação recusa o cover normal', async () => {
    const result = await play([[moveA('m')], [shot('s2')], [shot('s3')], [coverCall('c', 'drag', 1, 3)]]);
    assert.deepEqual(accepted(result), []);
});

test('janela: zoom entre a ação e after não conta como captura inteira', async () => {
    const result = await play([[moveA('m')], [zoomArea('z')], [shot('s3')], [coverCall('c', 'drag', 1, 3)]]);
    assert.deepEqual(accepted(result), ['drag']);
});

test('janela: impossível aceita captura inteira depois da ação', async () => {
    const result = await play([[moveA('m')], [shot('s2')], [shot('s3')], [coverCall('c', 'drag', 1, 3, { possible: false })]]);
    assert.deepEqual(accepted(result), ['drag']);
});

test('janela: duas janelas com ações diferentes ficam isoladas', async () => {
    const result = await play([
        [moveA('m')],
        [shot('s2')],
        [clickAt('k')],
        [shot('s3')],
        [coverCall('c1', 'drag', 1, 2), coverCall('c2', 'minimize', 2, 3)],
    ]);
    assert.deepEqual(accepted(result), ['drag', 'minimize']);
});

test('janela: ação errada dentro (move para minimize) recusa, mesmo com clique certo antes de before', async () => {
    const result = await play([[clickAt('k')], [shot('s2')], [moveB('m')], [shot('s3')], [coverCall('c', 'minimize', 2, 3)]]);
    assert.deepEqual(accepted(result), []);
});

test('janela: tecla depois da captura 2 não entra na janela entre 1 e 2; ponteiro antes continua aceito', async () => {
    const result = await play([[moveA('m')], [shot('s2')], [keyEsc('k')], [shot('s3')], [coverCall('c', 'drag', 1, 2)]]);
    assert.deepEqual(accepted(result), ['drag']);
});
