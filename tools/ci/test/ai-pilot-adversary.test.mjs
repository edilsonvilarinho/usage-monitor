// Modelos adversários do loop do piloto (#676, item e): todos terminam, saem com código != 0 e ficam no teto de custo. Fakes determinísticos.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { LIMITS, createBudget, runScript } from '../ai-pilot.mjs';
import { exitCodeOf } from '../ai-pilot-run.mjs';

const SCREEN = { width: 1920, height: 1080 };
const RULES = 'regras do piloto';
const SCRIPT = 'roteiro de teste';
const IMAGE = 'AAAA';
const DEFAULT_USAGE = { cost: 0.001, prompt_tokens: 10, completion_tokens: 5 };

const reply = (toolCalls, usage = DEFAULT_USAGE) => ({ message: { content: null, tool_calls: toolCalls }, usage });
const call = (id, name, args) => ({ id, type: 'function', function: { name, arguments: JSON.stringify(args) } });
const rawCall = (id, name, raw) => ({ id, type: 'function', function: { name, arguments: raw } });

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

function cycleClient(replies) {
    const calls = [];
    return {
        calls,
        async complete(request) {
            calls.push(structuredClone(request));
            return replies[(calls.length - 1) % replies.length];
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

async function run(client, { coverage = [], driver = fakeDriver() } = {}) {
    const result = await runScript({ client, driver, script: SCRIPT, rules: RULES, screen: SCREEN, budget: createBudget(), coverage });
    return { result, driver };
}

// Propriedades comuns: termina (já terminou), sai com código != 0 e custo dentro do teto do roteiro.
function assertFailsClosed(result) {
    assert.notEqual(exitCodeOf(result.stop), 0, `stop ${result.stop}`);
    assert.ok(result.costUsd <= LIMITS.maxScriptUsd + 1e-9, `custo ${result.costUsd}`);
}

test('adversário: repete zoom para sempre -> stuck', async () => {
    const zoom = call('z', 'zoom', { x: 100, y: 100, w: 200, h: 200 });
    const { result } = await run(fakeClient([reply([zoom])]));
    assert.equal(result.stop, 'stuck');
    assertFailsClosed(result);
});

test('adversário: alterna duas ações para sempre -> max-actions ou max-steps', async () => {
    const a = call('a', 'move', { x: 10, y: 10 });
    const b = call('b', 'move', { x: 20, y: 20 });
    const { result } = await run(cycleClient([reply([a]), reply([b])]));
    assert.ok(['max-actions', 'max-steps'].includes(result.stop), `stop ${result.stop}`);
    assertFailsClosed(result);
});

test('adversário: mente cobertura sem agir -> cover continua pendente', async () => {
    const lie = call('c', 'cover', { item: 'gear', before: 1, after: 2, seen: 'nada', possible: true });
    const { result } = await run(fakeClient([reply([call('s', 'screenshot', {})]), reply([lie]), reply([doneCall('d')])]), { coverage: ['gear'] });
    assert.ok(result.missing.includes('cover de gear'), `missing ${result.missing}`);
    assertFailsClosed(result);
});

test('adversário: done direto -> done-incomplete', async () => {
    const { result } = await run(fakeClient([reply([doneCall('d')])]));
    assert.equal(result.stop, 'done-incomplete');
    assertFailsClosed(result);
});

test('adversário: report sem captura não vira achado', async () => {
    const violated = call('v', 'check', { rule: 1, shot: 1, seen: 'texto', verdict: 'violated' });
    const report = call('r', 'report', { title: 'achado', rule: 1, shot: 9, seen: 'texto', expected: 'outro' });
    const client = fakeClient([reply([violated]), reply([report]), reply([doneCall('d')])]);
    const { result } = await run(client);
    assert.equal(result.reports.length, 0);
    assert.equal(client.calls[2].messages.at(-1).content, 'erro: captura 9 não existe');
    assertFailsClosed(result);
});

test('adversário: JSON quebrado para sempre -> stuck pela guarda de repetição, sem custo acima do teto', async () => {
    // A guarda roda antes da validação (#676): a mesma string bruta repetida conta mesmo inválida.
    const { result } = await run(fakeClient([reply([rawCall('x', 'move', '{x:')])]));
    assert.equal(result.stop, 'stuck');
    assertFailsClosed(result);
});

test('adversário: 200 chamadas num turno -> no máximo 20 execuções e max-actions', async () => {
    const turn = Array.from({ length: 200 }, (_, index) => call(`t${index}`, 'move', index % 2 === 0 ? { x: 10, y: 10 } : { x: 20, y: 20 }));
    const { result, driver } = await run(cycleClient([reply(turn)]));
    assert.equal(result.stop, 'max-actions');
    assert.ok(driver.performed.length <= 20, `execuções ${driver.performed.length}`);
    assertFailsClosed(result);
});

function doneCall(id) {
    return call(id, 'done', { summary: 'fim' });
}
