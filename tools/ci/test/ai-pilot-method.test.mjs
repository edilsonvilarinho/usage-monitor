// Método do piloto de IA (#653, A04b): orçamento menor, capturas numeradas, zoom, check, cover, report e done com recusa.
// Tudo com fakes: nenhuma chamada de rede, nenhuma escrita em disco.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LIMITS, createBudget, runScript, validateAction } from '../ai-pilot.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const SCREEN = { width: 1000, height: 800 };
const USAGE = { cost: 0.001, prompt_tokens: 10, completion_tokens: 5 };
const call = (id, name, args) => ({ id, type: 'function', function: { name, arguments: JSON.stringify(args) } });
const reply = (toolCalls) => ({ message: { content: null, tool_calls: toolCalls }, usage: USAGE });

// Cliente falso: grava cada requisição e repete a última resposta quando a lista acaba.
function scripted(replies) {
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
    let count = 0;
    return {
        async screenshot() {
            count += 1;
            return `IMG${count}`;
        },
        async perform() {},
    };
}

const toolContents = (request) => request.messages.filter((message) => message.role === 'tool').map((message) => message.content);

test('orçamento do roteiro é 0,1 USD e a reserva é 0,02 USD', () => {
    assert.equal(LIMITS.maxScriptUsd, 0.1);
    assert.equal(LIMITS.reserveUsd, 0.02);
});

test('captura N: a primeira capture antes do laço é a 1, a pedida pelo modelo é a 2', async () => {
    const client = scripted([reply([call('a', 'screenshot', {})]), reply([call('b', 'done', { summary: 'fim' })])]);
    await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: [] });
    assert.equal(toolContents(client.calls[1]).at(-1), 'captura 2');
});

test('done é recusado sem check e aceito na 6ª chamada como done-incomplete', async () => {
    const client = scripted([reply([call('d', 'done', { summary: 'fim' })])]);
    const result = await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: [] });
    assert.equal(client.calls.length, 6);
    assert.equal(result.stop, 'done-incomplete');
    assert.match(toolContents(client.calls[1]).at(-1), /check/);
});

test('report sem check violated é recusado e não entra em reports', async () => {
    const client = scripted([
        reply([call('r', 'report', { title: 't', rule: 1, shot: 1, seen: 's', expected: 'e' })]),
        reply([call('d', 'done', { summary: 'fim' })]),
    ]);
    const result = await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: [] });
    assert.equal(result.reports.length, 0);
});

test('runScript devolve checks, coverage e missing; missing cita o item de cobertura pendente', async () => {
    const client = scripted([reply([call('d', 'done', { summary: 'fim' })])]);
    const result = await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: ['gear'] });
    assert.ok(Array.isArray(result.checks));
    assert.ok(Array.isArray(result.coverage));
    assert.ok(Array.isArray(result.missing));
    assert.match(JSON.stringify(result.missing), /gear/);
});

test('zoom: retângulo inteiro dentro da tela, w e h entre 16 e metade da tela', () => {
    assert.equal(validateAction('zoom', { x: 0, y: 0, w: 500, h: 400 }, SCREEN).ok, true);
    assert.equal(validateAction('zoom', { x: 900, y: 0, w: 200, h: 100 }, SCREEN).ok, false);
    assert.equal(validateAction('zoom', { x: 0, y: 0, w: 10, h: 100 }, SCREEN).ok, false);
    assert.equal(validateAction('zoom', { x: 0, y: 0, w: 600, h: 100 }, SCREEN).ok, false);
    assert.equal(validateAction('zoom', { x: 0, y: 0, w: 100, h: 500 }, SCREEN).ok, false);
});

test('check: rule 1..9, verdict do enum, shot obrigatória exceto not_seen com seen', () => {
    assert.equal(validateAction('check', { rule: 1, shot: 1, seen: 'texto', verdict: 'ok' }, SCREEN).ok, true);
    assert.equal(validateAction('check', { rule: 10, shot: 1, seen: 'texto', verdict: 'ok' }, SCREEN).ok, false);
    assert.equal(validateAction('check', { rule: 1, shot: 1, seen: 'texto', verdict: 'maybe' }, SCREEN).ok, false);
    assert.equal(validateAction('check', { rule: 1, seen: 'texto', verdict: 'ok' }, SCREEN).ok, false);
    assert.equal(validateAction('check', { rule: 1, seen: 'não apareceu', verdict: 'not_seen' }, SCREEN).ok, true);
    assert.equal(validateAction('check', { rule: 1, seen: '  ', verdict: 'not_seen' }, SCREEN).ok, false);
});

test('cover: item e shot obrigatórios, exceto possible false', () => {
    assert.equal(validateAction('cover', { item: 'gear', shot: 2, seen: 'engrenagem' }, SCREEN).ok, true);
    assert.equal(validateAction('cover', { item: 'gear', seen: 'engrenagem' }, SCREEN).ok, false);
    assert.equal(validateAction('cover', { item: 'gear', possible: false, seen: 'sem botão' }, SCREEN).ok, true);
});

test('confirmação não zera o custo do roteiro, só o tamanho do prompt', () => {
    const budget = createBudget();
    budget.charge(USAGE);
    budget.startConversation();
    assert.equal(budget.scriptUsd, USAGE.cost);
    assert.equal(budget.lastPromptTokens, 0);
});

test('planBudget: pior caso = jobs x maxScriptUsd somado ao gasto do ledger', async () => {
    const { planBudget } = await import('../ai-pilot-run.mjs');
    const fits = planBudget({ ledgerUsd: 0.0108, jobs: 16, maxScriptUsd: 0.1, maxPilotUsd: 5 });
    assert.equal(fits.ok, true);
    assert.ok(Math.abs(fits.worstCaseUsd - 1.6108) < 1e-9);
    assert.equal(planBudget({ ledgerUsd: 4.95, jobs: 1, maxScriptUsd: 0.1, maxPilotUsd: 5 }).ok, false);
    assert.equal(planBudget({ ledgerUsd: 4.9, jobs: 1, maxScriptUsd: 0.1, maxPilotUsd: 5 }).ok, true);
});

test('pilot-ledger.json: spentUsd é a soma dos runs', () => {
    const ledger = JSON.parse(readFileSync(resolve(HERE, '..', '..', 'real-app', 'pilot-ledger.json'), 'utf8'));
    const sum = ledger.runs.reduce((total, item) => total + item.usd, 0);
    assert.ok(Math.abs(ledger.spentUsd - sum) <= 1e-9);
});
