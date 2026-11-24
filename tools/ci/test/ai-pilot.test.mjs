// Testes do loop de IA do piloto (#653, A02). Tudo com fakes: nenhuma chamada de rede, nenhuma escrita em disco.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { inspect } from 'node:util';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
    LIMITS, TOOLS, checkKey, confirmReport, createBudget, createOpenRouterClient, runScript, validateAction,
} from '../ai-pilot.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const MODULE_PATH = resolve(HERE, '..', 'ai-pilot.mjs');
const SCREEN = { width: 1000, height: 800 };
const RULES = 'regras do piloto';
const SCRIPT = 'roteiro de teste';
const IMAGE = 'AAAA';
const IMAGE_URL = 'data:image/jpeg;base64,AAAA';
const KEY = 'sk-or-v1-CHAVE-DE-TESTE';
const DEFAULT_USAGE = { cost: 0.001, prompt_tokens: 10, completion_tokens: 5 };
const KEYS = ['Escape', 'Enter', 'Tab', 'Space', 'Up', 'Down', 'Left', 'Right', 'Home', 'End', 'PageUp', 'PageDown'];

const reply = (toolCalls, usage = DEFAULT_USAGE, content = null) => ({ message: { content, tool_calls: toolCalls }, usage });
const call = (id, name, args) => ({ id, type: 'function', function: { name, arguments: JSON.stringify(args) } });
const rawCall = (id, name, raw) => ({ id, type: 'function', function: { name, arguments: raw } });
const roles = (messages) => messages.map((message) => message.role);

// Cliente falso: grava uma cópia de cada requisicao e responde com a lista na ordem (repete a ultima).
function fakeClient(replies) {
    const calls = [];
    return {
        calls,
        async complete(request) {
            calls.push(structuredClone(request));
            const next = replies[Math.min(calls.length - 1, replies.length - 1)];
            if (next instanceof Error) throw next;
            return next;
        },
    };
}

// Driver falso: grava as acoes executadas; failOn faz a ferramenta indicada lancar erro.
function fakeDriver({ failOn = null } = {}) {
    const performed = [];
    let screenshots = 0;
    return {
        performed,
        get screenshots() { return screenshots },
        async screenshot() {
            screenshots += 1;
            if (failOn === 'screenshot') throw new Error('falha de captura');
            return IMAGE;
        },
        async perform(action) {
            if (failOn === action.tool) throw new Error('falha do driver');
            performed.push(action);
        },
    };
}

const run = (client, driver, budget = createBudget()) => runScript({ client, driver, script: SCRIPT, rules: RULES, screen: SCREEN, budget });

// Cliente falso de fetch: grava url e init; responde com o valor dado ou lanca se for Error.
function fakeFetch(response) {
    const calls = [];
    const fetch = async (url, init) => {
        calls.push({ url, init });
        if (response instanceof Error) throw response;
        return response;
    };
    return { fetch, calls };
}

// --- checkKey ---

test('checkKey: limite 5 e saldo igual ao piso passam', () => {
    assert.deepEqual(checkKey({ data: { limit: 5, limit_remaining: 0.3 } }), { ok: true });
});

test('checkKey: sem data ou corpo nulo e invalid-response', () => {
    assert.deepEqual(checkKey({}), { ok: false, reason: 'invalid-response' });
    assert.deepEqual(checkKey(null), { ok: false, reason: 'invalid-response' });
});

test('checkKey: limit ausente ou nao numerico e invalid-response', () => {
    assert.deepEqual(checkKey({ data: { limit_remaining: 1 } }), { ok: false, reason: 'invalid-response' });
    assert.deepEqual(checkKey({ data: { limit: '5', limit_remaining: 1 } }), { ok: false, reason: 'invalid-response' });
});

test('checkKey: limit definido sem limit_remaining finito e invalid-response', () => {
    assert.deepEqual(checkKey({ data: { limit: 5 } }), { ok: false, reason: 'invalid-response' });
    assert.deepEqual(checkKey({ data: { limit: 5, limit_remaining: '0.5' } }), { ok: false, reason: 'invalid-response' });
    assert.deepEqual(checkKey({ data: { limit: 5, limit_remaining: null } }), { ok: false, reason: 'invalid-response' });
});

test('checkKey: limit null e no-limit', () => {
    assert.deepEqual(checkKey({ data: { limit: null, limit_remaining: null } }), { ok: false, reason: 'no-limit' });
});

test('checkKey: limit acima de 5 e limit-too-high', () => {
    assert.deepEqual(checkKey({ data: { limit: 6, limit_remaining: 1 } }), { ok: false, reason: 'limit-too-high' });
});

test('checkKey: saldo abaixo do piso e remaining-too-low', () => {
    assert.deepEqual(checkKey({ data: { limit: 5, limit_remaining: 0.29 } }), { ok: false, reason: 'remaining-too-low' });
});

test('checkKey: ordem das razoes: invalid-response vence limit-too-high', () => {
    assert.deepEqual(checkKey({ data: { limit: 6, limit_remaining: 'x' } }), { ok: false, reason: 'invalid-response' });
});

// --- createBudget ---

test('createBudget: orcamento novo permite chamada', () => {
    assert.equal(createBudget().canCall(), null);
});

test('createBudget: script exatamente no teto e permitido', () => {
    const budget = createBudget();
    budget.charge({ cost: 0.25, prompt_tokens: 0, completion_tokens: 0 });
    assert.equal(budget.canCall(), null);
});

test('createBudget: script acima do teto e script-budget', () => {
    const budget = createBudget();
    budget.charge({ cost: 0.26, prompt_tokens: 0, completion_tokens: 0 });
    assert.equal(budget.canCall(), 'script-budget');
});

test('createBudget: piloto exatamente no teto e permitido', () => {
    assert.equal(createBudget({ pilotSpentUsd: 4.95 }).canCall(), null);
});

test('createBudget: piloto acima do teto e pilot-budget', () => {
    assert.equal(createBudget({ pilotSpentUsd: 4.96 }).canCall(), 'pilot-budget');
});

test('createBudget: prompt + completion + margem exatamente no teto e permitido', () => {
    const budget = createBudget();
    budget.charge({ cost: 0, prompt_tokens: 95000, completion_tokens: 1000 });
    assert.equal(budget.canCall(), null);
});

test('createBudget: prompt + completion + margem acima do teto e prompt-limit', () => {
    const budget = createBudget();
    budget.charge({ cost: 0, prompt_tokens: 95001, completion_tokens: 1000 });
    assert.equal(budget.canCall(), 'prompt-limit');
});

test('createBudget: startScript zera o limite de prompt do script anterior', () => {
    const budget = createBudget();
    budget.charge({ cost: 0, prompt_tokens: 95001, completion_tokens: 1000 });
    budget.startScript();
    assert.equal(budget.canCall(), null);
});

test('createBudget: uso ruim bloqueia para sempre com cost-unknown', () => {
    const badUsages = [
        undefined,
        { prompt_tokens: 10, completion_tokens: 5 },
        { cost: '0.1', prompt_tokens: 10, completion_tokens: 5 },
        { cost: -0.01, prompt_tokens: 10, completion_tokens: 5 },
        { cost: NaN, prompt_tokens: 10, completion_tokens: 5 },
        { cost: Infinity, prompt_tokens: 10, completion_tokens: 5 },
        { cost: 0.1, prompt_tokens: -1, completion_tokens: 0 },
        { cost: 0.1, prompt_tokens: 10 },
        { cost: 0.1, prompt_tokens: 10, completion_tokens: NaN },
    ];
    for (const usage of badUsages) {
        const budget = createBudget();
        budget.charge(usage);
        assert.equal(budget.canCall(), 'cost-unknown', JSON.stringify(usage));
        budget.startScript();
        budget.charge({ cost: 0, prompt_tokens: 1, completion_tokens: 1 });
        assert.equal(budget.canCall(), 'cost-unknown', 'cost-unknown nao se desfaz');
    }
});

test('createBudget: startScript zera o script e mantem o piloto', () => {
    const budget = createBudget({ pilotSpentUsd: 2 });
    budget.charge({ cost: 1, prompt_tokens: 1, completion_tokens: 1 });
    budget.startScript();
    assert.equal(budget.scriptUsd, 0);
    assert.equal(budget.pilotUsd, 3);
    budget.charge({ cost: 0.5, prompt_tokens: 1, completion_tokens: 1 });
    assert.equal(budget.pilotUsd, 3.5);
    assert.equal(budget.scriptUsd, 0.5);
});

// --- TOOLS ---

test('TOOLS: lista as nove ferramentas do briefing como schemas de funcao', () => {
    const names = TOOLS.map((tool) => tool.function.name);
    assert.deepEqual(names, ['screenshot', 'click', 'drag', 'move', 'type', 'key', 'wait', 'report', 'done']);
    assert.ok(TOOLS.every((tool) => tool.type === 'function'));
});

test('LIMITS: valores do briefing, congelados', () => {
    assert.deepEqual({ ...LIMITS }, {
        maxSteps: 60, maxPromptTokens: 100000, promptMargin: 4000, maxScriptUsd: 0.3, maxPilotUsd: 5,
        reserveUsd: 0.05, maxOutputTokens: 1024, maxWaitMs: 5000, maxTypeChars: 200,
    });
    assert.equal(Object.isFrozen(LIMITS), true);
});

// --- validateAction ---

const REJECTIONS = [
    ['scroll', {}, 'ferramenta desconhecida'],
    ['click', { x: 1 }, 'campo faltando (y)'],
    ['click', null, 'args nulos'],
    ['click', { x: 1.5, y: 2 }, 'coordenada nao inteira'],
    ['click', { x: '1', y: 2 }, 'coordenada string'],
    ['click', { x: 1000, y: 0 }, 'x igual a largura (0-based)'],
    ['click', { x: -1, y: 0 }, 'x negativo'],
    ['click', { x: 0, y: 800 }, 'y igual a altura'],
    ['drag', { x1: 0, y1: 0, x2: 1000, y2: 0 }, 'drag com x2 fora da tela'],
    ['move', { x: 5000, y: 5 }, 'move fora da tela'],
    ['wait', {}, 'wait sem ms'],
    ['wait', { ms: 5001 }, 'wait acima de 5000'],
    ['wait', { ms: -1 }, 'wait negativo'],
    ['wait', { ms: 1.5 }, 'wait nao inteiro'],
    ['type', { text: '' }, 'type vazio'],
    ['type', { text: 'a'.repeat(201) }, 'type acima de 200'],
    ['type', { text: 5 }, 'type nao string'],
    ['key', { name: 'Ctrl' }, 'tecla fora da lista'],
    ['key', { name: 'enter' }, 'tecla em minuscula'],
    ['report', { title: '', rule: 'r', where: 'w' }, 'report com titulo vazio'],
    ['report', { title: 't', rule: '   ', where: 'w' }, 'report com regra so espacos'],
    ['report', { title: 't', rule: 'r' }, 'report sem where'],
    ['done', { summary: '' }, 'done com resumo vazio'],
];

for (const [name, args, label] of REJECTIONS) {
    test(`validateAction rejeita: ${label}`, () => {
        const result = validateAction(name, args, SCREEN);
        assert.equal(result.ok, false);
        assert.equal(typeof result.error, 'string');
    });
}

test('validateAction aceita screenshot sem argumentos', () => {
    assert.deepEqual(validateAction('screenshot', {}, SCREEN), { ok: true, action: { tool: 'screenshot' } });
});

test('validateAction aceita os limites exatos da tela e dos tempos', () => {
    assert.equal(validateAction('click', { x: 999, y: 799 }, SCREEN).ok, true);
    assert.equal(validateAction('click', { x: 0, y: 0 }, SCREEN).ok, true);
    assert.equal(validateAction('drag', { x1: 0, y1: 0, x2: 999, y2: 799 }, SCREEN).ok, true);
    assert.equal(validateAction('move', { x: 5, y: 5 }, SCREEN).ok, true);
    assert.equal(validateAction('wait', { ms: 0 }, SCREEN).ok, true);
    assert.equal(validateAction('wait', { ms: 5000 }, SCREEN).ok, true);
    assert.equal(validateAction('type', { text: 'a'.repeat(200) }, SCREEN).ok, true);
});

for (const key of KEYS) {
    test(`validateAction aceita a tecla ${key}`, () => {
        assert.deepEqual(validateAction('key', { name: key }, SCREEN), { ok: true, action: { tool: 'key', name: key } });
    });
}

test('validateAction descarta campos extras da acao', () => {
    assert.deepEqual(
        validateAction('click', { x: 1, y: 2, extra: 'x' }, SCREEN),
        { ok: true, action: { tool: 'click', x: 1, y: 2 } },
    );
    assert.deepEqual(
        validateAction('report', { title: 't', rule: 'r', where: 'w', extra: 'x' }, SCREEN),
        { ok: true, action: { tool: 'report', title: 't', rule: 'r', where: 'w' } },
    );
});

// --- runScript ---

test('runScript: primeira mensagem leva regras no sistema e roteiro com a primeira captura', async () => {
    const client = fakeClient([reply([call('d', 'done', { summary: 'fim' })])]);
    await run(client, fakeDriver());
    assert.deepEqual(client.calls[0].messages[0], { role: 'system', content: RULES });
    assert.deepEqual(client.calls[0].messages[1], {
        role: 'user',
        content: [{ type: 'text', text: SCRIPT }, { type: 'image_url', image_url: { url: IMAGE_URL } }],
    });
});

test('runScript: done encerra com stop done, passos e relatorio', async () => {
    const client = fakeClient([
        reply([call('1', 'screenshot', {})]),
        reply([call('2', 'report', { title: 'T', rule: 'R', where: 'W' }), call('3', 'done', { summary: 'fim' })]),
    ]);
    const result = await run(client, fakeDriver());
    assert.equal(result.stop, 'done');
    assert.equal(result.steps, 2);
    assert.equal(result.reports.length, 1);
    assert.equal(result.reports[0].step, 2);
    assert.equal(result.reports[0].title, 'T');
    assert.equal(result.reports[0].screenshot, IMAGE);
});

test('runScript: 60 chamadas sem done param em max-steps', async () => {
    const client = fakeClient([reply([call('w', 'wait', { ms: 0 })])]);
    const driver = fakeDriver();
    const result = await run(client, driver);
    assert.equal(result.stop, 'max-steps');
    assert.equal(result.steps, 60);
    assert.equal(client.calls.length, 60);
    assert.equal(driver.performed.length, 60);
});

test('runScript: teto de script estourado no turno mantem o report do turno e para no portao seguinte', async () => {
    const client = fakeClient([reply([call('r', 'report', { title: 'T', rule: 'R', where: 'W' })], { cost: 0.26, prompt_tokens: 10, completion_tokens: 5 })]);
    const driver = fakeDriver();
    const result = await run(client, driver);
    assert.equal(result.stop, 'script-budget');
    assert.equal(client.calls.length, 1);
    assert.equal(result.reports.length, 1);
    assert.equal(result.reports[0].title, 'T');
    assert.ok(Math.abs(result.costUsd - 0.26) < 1e-9);
});

test('runScript: done no turno que esgota o teto de script encerra com done', async () => {
    const client = fakeClient([reply([call('d', 'done', { summary: 'fim' })], { cost: 0.26, prompt_tokens: 10, completion_tokens: 5 })]);
    const result = await run(client, fakeDriver());
    assert.equal(result.stop, 'done');
    assert.equal(client.calls.length, 1);
    assert.equal(result.steps, 1);
});

test('runScript: teto de piloto estourado no turno mantem o report e para no portao seguinte', async () => {
    const client = fakeClient([reply([call('r', 'report', { title: 'T', rule: 'R', where: 'W' })], { cost: 0.1, prompt_tokens: 10, completion_tokens: 5 })]);
    const result = await run(client, fakeDriver(), createBudget({ pilotSpentUsd: 4.9 }));
    assert.equal(result.stop, 'pilot-budget');
    assert.equal(client.calls.length, 1);
    assert.equal(result.reports.length, 1);
});

test('runScript: piloto no teto nao chama o cliente', async () => {
    const client = fakeClient([reply([call('d', 'done', { summary: 'fim' })])]);
    const result = await run(client, fakeDriver(), createBudget({ pilotSpentUsd: 4.96 }));
    assert.equal(result.stop, 'pilot-budget');
    assert.equal(result.steps, 0);
    assert.equal(client.calls.length, 0);
});

test('runScript: prompt acima do limite no turno mantem report e click do turno e para no portao seguinte', async () => {
    const client = fakeClient([reply([call('r', 'report', { title: 'T', rule: 'R', where: 'W' }), call('1', 'click', { x: 1, y: 1 })], { cost: 0.001, prompt_tokens: 96001, completion_tokens: 0 })]);
    const driver = fakeDriver();
    const result = await run(client, driver);
    assert.equal(result.stop, 'prompt-limit');
    assert.equal(client.calls.length, 1);
    assert.equal(result.reports.length, 1);
    assert.equal(driver.performed.length, 1);
});

test('runScript: uso sem custo para antes da ferramenta com cost-unknown', async () => {
    const client = fakeClient([reply([call('1', 'click', { x: 1, y: 1 })], { prompt_tokens: 10, completion_tokens: 5 })]);
    const driver = fakeDriver();
    const result = await run(client, driver);
    assert.equal(result.stop, 'cost-unknown');
    assert.equal(client.calls.length, 1);
    assert.equal(driver.performed.length, 0);
});

test('runScript: resposta sem tool_calls e no-tool-call', async () => {
    const withoutCalls = await run(fakeClient([reply(undefined, DEFAULT_USAGE, 'pensando')]), fakeDriver());
    assert.equal(withoutCalls.stop, 'no-tool-call');
    const emptyCalls = await run(fakeClient([reply([], DEFAULT_USAGE, 'pensando')]), fakeDriver());
    assert.equal(emptyCalls.stop, 'no-tool-call');
});

test('runScript: cliente que lanca vira client-error com detail, sem rejeitar', async () => {
    const result = await run(fakeClient([new Error('timeout no provedor')]), fakeDriver());
    assert.equal(result.stop, 'client-error');
    assert.equal(typeof result.detail, 'string');
});

test('runScript: driver que falha ao executar vira driver-error', async () => {
    const client = fakeClient([reply([call('1', 'click', { x: 1, y: 1 })])]);
    const result = await run(client, fakeDriver({ failOn: 'click' }));
    assert.equal(result.stop, 'driver-error');
    assert.equal(typeof result.detail, 'string');
});

test('runScript: captura que falha vira driver-error', async () => {
    const client = fakeClient([reply([call('1', 'screenshot', {})])]);
    const result = await run(client, fakeDriver({ failOn: 'screenshot' }));
    assert.equal(result.stop, 'driver-error');
});

test('runScript: JSON invalido vira mensagem de ferramenta e o driver nao roda', async () => {
    const client = fakeClient([
        reply([rawCall('1', 'click', '{x:')]),
        reply([call('2', 'done', { summary: 'fim' })]),
    ]);
    const driver = fakeDriver();
    const result = await run(client, driver);
    assert.equal(result.stop, 'done');
    assert.equal(driver.performed.length, 0);
    const toolMessage = client.calls[1].messages.find((message) => message.role === 'tool' && message.tool_call_id === '1');
    assert.equal(typeof toolMessage.content, 'string');
    assert.notEqual(toolMessage.content, 'ok');
});

test('runScript: acao invalida vira mensagem de ferramenta e o driver nao roda', async () => {
    const client = fakeClient([
        reply([call('1', 'click', { x: 5000, y: 1 })]),
        reply([call('2', 'done', { summary: 'fim' })]),
    ]);
    const driver = fakeDriver();
    await run(client, driver);
    assert.equal(driver.performed.length, 0);
    assert.equal(client.calls[1].messages.some((message) => message.role === 'tool' && message.tool_call_id === '1'), true);
});

test('runScript: ferramentas depois de done nao executam', async () => {
    const client = fakeClient([reply([call('1', 'done', { summary: 'fim' }), call('2', 'click', { x: 1, y: 1 })])]);
    const driver = fakeDriver();
    const result = await run(client, driver);
    assert.equal(result.stop, 'done');
    assert.equal(driver.performed.length, 0);
});

test('runScript: mensagens de imagem vem depois de todas as tool messages do turno', async () => {
    const client = fakeClient([
        reply([call('a', 'wait', { ms: 0 }), call('b', 'screenshot', {})]),
        reply([call('c', 'done', { summary: 'fim' })]),
    ]);
    await run(client, fakeDriver());
    const messages = client.calls[1].messages;
    assert.deepEqual(roles(messages), ['system', 'user', 'assistant', 'tool', 'tool', 'user']);
    assert.equal(messages[4].tool_call_id, 'b');
    assert.equal(messages[4].content, 'captured');
    assert.ok(JSON.stringify(messages[5].content).includes(IMAGE_URL));
});

test('runScript: relatorio repetido (titulo, regra, onde) fica uma vez so', async () => {
    const client = fakeClient([
        reply([call('1', 'report', { title: 'T', rule: 'R', where: 'W' })]),
        reply([call('2', 'report', { title: 'T', rule: 'R', where: 'W' })]),
        reply([call('3', 'report', { title: 'T', rule: 'R', where: 'Outro' }), call('4', 'done', { summary: 'fim' })]),
    ]);
    const result = await run(client, fakeDriver());
    assert.equal(result.reports.length, 2);
    assert.equal(result.reports[0].step, 1);
    assert.equal(result.reports[1].where, 'Outro');
});

test('runScript: zera o script anterior antes da primeira chamada', async () => {
    const budget = createBudget();
    budget.charge({ cost: 0.29, prompt_tokens: 1, completion_tokens: 1 });
    const client = fakeClient([reply([call('d', 'done', { summary: 'fim' })])]);
    const result = await run(client, fakeDriver(), budget);
    assert.equal(result.stop, 'done');
    assert.equal(client.calls.length, 1);
});

test('runScript: costUsd e a parte do script somada', async () => {
    const client = fakeClient([
        reply([call('1', 'screenshot', {})], { cost: 0.1, prompt_tokens: 10, completion_tokens: 5 }),
        reply([call('2', 'done', { summary: 'fim' })], { cost: 0.05, prompt_tokens: 10, completion_tokens: 5 }),
    ]);
    const result = await run(client, fakeDriver());
    assert.equal(result.stop, 'done');
    assert.ok(Math.abs(result.costUsd - 0.15) < 1e-9);
});

// --- confirmReport ---

const VERDICT_USAGE = { cost: 0.02, prompt_tokens: 10, completion_tokens: 5 };
const verdictReply = (confirmed, why) => reply([call('v', 'verdict', { confirmed, why })], VERDICT_USAGE);

test('confirmReport: uso sem custo na propria cobranca vira null cost-unknown, nunca veredito', async () => {
    const client = fakeClient([reply([call('v', 'verdict', { confirmed: true, why: 'ok' })], { prompt_tokens: 10, completion_tokens: 5 })]);
    const result = await confirmReport({ client, report: REPORT, rules: RULES, budget: createBudget() });
    assert.deepEqual(result, { confirmed: null, why: 'cost-unknown' });
});
const REPORT = { title: 'T', rule: 'R', where: 'W', screenshot: IMAGE };

test('confirmReport: veredito true volta confirmado', async () => {
    const result = await confirmReport({ client: fakeClient([verdictReply(true, 'motivo')]), report: REPORT, rules: RULES, budget: createBudget() });
    assert.deepEqual(result, { confirmed: true, why: 'motivo' });
});

test('confirmReport: veredito false volta nao confirmado', async () => {
    const result = await confirmReport({ client: fakeClient([verdictReply(false, 'nao e bug')]), report: REPORT, rules: RULES, budget: createBudget() });
    assert.deepEqual(result, { confirmed: false, why: 'nao e bug' });
});

test('confirmReport: envia uma unica ferramenta forcada e a captura do relatorio', async () => {
    const client = fakeClient([verdictReply(true, 'ok')]);
    await confirmReport({ client, report: REPORT, rules: RULES, budget: createBudget() });
    assert.deepEqual(client.calls[0].tools.map((tool) => tool.function.name), ['verdict']);
    assert.deepEqual(client.calls[0].toolChoice, { type: 'function', function: { name: 'verdict' } });
    assert.ok(JSON.stringify(client.calls[0].messages).includes(IMAGE_URL));
});

test('confirmReport: orcamento recusado vira null sem chamar o cliente', async () => {
    const client = fakeClient([verdictReply(true, 'ok')]);
    const result = await confirmReport({ client, report: REPORT, rules: RULES, budget: createBudget({ pilotSpentUsd: 4.96 }) });
    assert.deepEqual(result, { confirmed: null, why: 'pilot-budget' });
    assert.equal(client.calls.length, 0);
});

test('confirmReport: cliente que falha vira null', async () => {
    const result = await confirmReport({ client: fakeClient([new Error('HTTP 500')]), report: REPORT, rules: RULES, budget: createBudget() });
    assert.equal(result.confirmed, null);
    assert.equal(typeof result.why, 'string');
});

test('confirmReport: resposta sem tool_call e null', async () => {
    const result = await confirmReport({ client: fakeClient([reply(undefined, VERDICT_USAGE, 'sim')]), report: REPORT, rules: RULES, budget: createBudget() });
    assert.equal(result.confirmed, null);
});

test('confirmReport: confirmed fora de boolean e null, nunca false', async () => {
    const result = await confirmReport({ client: fakeClient([verdictReply('sim', 'x')]), report: REPORT, rules: RULES, budget: createBudget() });
    assert.equal(result.confirmed, null);
});

test('confirmReport: cobra o mesmo orcamento', async () => {
    const budget = createBudget();
    await confirmReport({ client: fakeClient([verdictReply(true, 'ok')]), report: REPORT, rules: RULES, budget });
    assert.ok(Math.abs(budget.pilotUsd - 0.02) < 1e-9);
});

// --- createOpenRouterClient ---

const jsonResponse = (status, body) => ({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
});

const OK_BODY = {
    choices: [{ message: { content: null, tool_calls: [call('1', 'done', { summary: 'fim' })] } }],
    usage: { cost: 0.01, prompt_tokens: 10, completion_tokens: 5 },
};

test('createOpenRouterClient: POST no endpoint com exatamente os dois headers', async () => {
    const { fetch, calls } = fakeFetch(jsonResponse(200, OK_BODY));
    const client = createOpenRouterClient({ fetch, apiKey: KEY, model: 'modelo-teste' });
    await client.complete({ messages: [], tools: TOOLS, maxTokens: 1024 });
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, 'https://openrouter.ai/api/v1/chat/completions');
    assert.equal(calls[0].init.method, 'POST');
    assert.deepEqual(calls[0].init.headers, { Authorization: `Bearer ${KEY}`, 'Content-Type': 'application/json' });
    assert.equal(Object.keys(calls[0].init.headers).length, 2);
});

test('createOpenRouterClient: corpo com model, tool_choice padrao required e usage include', async () => {
    const { fetch, calls } = fakeFetch(jsonResponse(200, OK_BODY));
    const client = createOpenRouterClient({ fetch, apiKey: KEY, model: 'modelo-teste' });
    await client.complete({ messages: [{ role: 'user', content: 'oi' }], tools: TOOLS, maxTokens: 1024 });
    const body = JSON.parse(calls[0].init.body);
    assert.equal(body.model, 'modelo-teste');
    assert.equal(body.tool_choice, 'required');
    assert.equal(body.max_tokens, 1024);
    assert.deepEqual(body.usage, { include: true });
    assert.equal(body.messages[0].content, 'oi');
    assert.equal(body.tools.length, TOOLS.length);
});

test('createOpenRouterClient: toolChoice informado substitui o padrao', async () => {
    const { fetch, calls } = fakeFetch(jsonResponse(200, OK_BODY));
    const client = createOpenRouterClient({ fetch, apiKey: KEY, model: 'modelo-teste' });
    const toolChoice = { type: 'function', function: { name: 'verdict' } };
    await client.complete({ messages: [], tools: TOOLS, maxTokens: 1024, toolChoice });
    assert.deepEqual(JSON.parse(calls[0].init.body).tool_choice, toolChoice);
});

test('createOpenRouterClient: devolve message e usage da resposta', async () => {
    const { fetch } = fakeFetch(jsonResponse(200, OK_BODY));
    const client = createOpenRouterClient({ fetch, apiKey: KEY, model: 'modelo-teste' });
    const result = await client.complete({ messages: [], tools: TOOLS, maxTokens: 1024 });
    assert.deepEqual(result.message, OK_BODY.choices[0].message);
    assert.deepEqual(result.usage, OK_BODY.usage);
});

test('createOpenRouterClient: status 401 checa ok antes de parsear e redige a chave do corpo', async () => {
    const response = {
        ok: false,
        status: 401,
        json: async () => { throw new Error('nao deveria parsear') },
        text: async () => `chave invalida ${KEY}`,
    };
    const { fetch, calls } = fakeFetch(response);
    const client = createOpenRouterClient({ fetch, apiKey: KEY, model: 'modelo-teste' });
    await assert.rejects(client.complete({ messages: [], tools: TOOLS, maxTokens: 1024 }), (error) => {
        assert.equal(error.message.includes(KEY), false);
        assert.equal(error.message.includes('[redacted]'), true);
        assert.equal(error.message.includes('HTTP 401'), true);
        assert.equal(error.cause, undefined);
        return true;
    });
    assert.equal(calls.length, 1, 'sem retry');
});

test('createOpenRouterClient: falha do fetch com a chave na mensagem vira erro redigido sem cause', async () => {
    const { fetch } = fakeFetch(new Error(`falha de rede usando ${KEY}`));
    const client = createOpenRouterClient({ fetch, apiKey: KEY, model: 'modelo-teste' });
    await assert.rejects(client.complete({ messages: [], tools: TOOLS, maxTokens: 1024 }), (error) => {
        assert.equal(error.message.includes(KEY), false);
        assert.equal(error.message.includes('[redacted]'), true);
        assert.equal(error.cause, undefined);
        return true;
    });
});

test('createOpenRouterClient: chave nao aparece em JSON, Object.keys nem inspect do cliente', () => {
    const { fetch } = fakeFetch(jsonResponse(200, OK_BODY));
    const client = createOpenRouterClient({ fetch, apiKey: KEY, model: 'modelo-teste' });
    assert.equal(JSON.stringify(client).includes(KEY), false);
    assert.equal(inspect(client, { showHidden: true, depth: 5 }).includes(KEY), false);
    assert.equal(Object.keys(client).some((name) => client[name] === KEY || String(client[name]).includes(KEY)), false);
});

test('ai-pilot.mjs: fonte sem fs, process.env nem child_process', () => {
    const source = readFileSync(MODULE_PATH, 'utf8');
    assert.equal(source.includes('node:fs'), false);
    assert.equal(source.includes('process.env'), false);
    assert.equal(source.includes('child_process'), false);
});
