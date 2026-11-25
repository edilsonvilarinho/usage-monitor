// Método do piloto de IA (#653, A04b): orçamento menor, capturas numeradas, zoom, check, cover, report e done com recusa.
// Tudo com fakes: nenhuma chamada de rede, nenhuma escrita em disco.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LIMITS, TOOLS, createBudget, runScript, validateAction } from '../ai-pilot.mjs';

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
        async zoom() {
            count += 1;
            return `IMG${count}`;
        },
        async perform() {},
    };
}

const toolContents = (request) => request.messages.filter((message) => message.role === 'tool').map((message) => message.content);

test('orçamento do roteiro é 0,2 USD e a reserva é 0,02 USD', () => {
    assert.equal(LIMITS.maxScriptUsd, 0.2);
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

test('zoom: limites exatos em 1024x768: 16 <= w <= 1024, 16 <= h <= 768, retângulo dentro da tela', () => {
    const screen = { width: 1024, height: 768 };
    const accepted = [{ x: 0, y: 0, w: 16, h: 16 }, { x: 0, y: 0, w: 1024, h: 768 }, { x: 1008, y: 752, w: 16, h: 16 }, { x: 0, y: 0, w: 600, h: 100 }, { x: 0, y: 0, w: 100, h: 500 }];
    const refused = [{ x: 0, y: 0, w: 15, h: 16 }, { x: 0, y: 0, w: 16, h: 15 }, { x: 0, y: 0, w: 1025, h: 16 }, { x: 0, y: 0, w: 16, h: 769 }, { x: 1, y: 0, w: 1024, h: 16 }];
    for (const rect of accepted) assert.equal(validateAction('zoom', rect, screen).ok, true, JSON.stringify(rect));
    for (const rect of refused) assert.equal(validateAction('zoom', rect, screen).ok, false, JSON.stringify(rect));
});

test('zoom recusado diz o recebido e o permitido', () => {
    assert.equal(
        validateAction('zoom', { x: 0, y: 0, w: 1025, h: 430 }, { width: 1024, height: 768 }).error,
        'campo inválido em zoom: x, y, w e h inteiros; retângulo dentro da tela; recebido x=0 y=0 w=1025 h=430; permitido 16 <= w <= 1024 e 16 <= h <= 768',
    );
});

test('check: rule 1..9, verdict do enum, shot obrigatória exceto not_seen com seen', () => {
    assert.equal(validateAction('check', { rule: 1, shot: 1, seen: 'texto', verdict: 'ok' }, SCREEN).ok, true);
    assert.equal(validateAction('check', { rule: 10, shot: 1, seen: 'texto', verdict: 'ok' }, SCREEN).ok, false);
    assert.equal(validateAction('check', { rule: 1, shot: 1, seen: 'texto', verdict: 'maybe' }, SCREEN).ok, false);
    assert.equal(validateAction('check', { rule: 1, seen: 'texto', verdict: 'ok' }, SCREEN).ok, false);
    assert.equal(validateAction('check', { rule: 1, seen: 'não apareceu', verdict: 'not_seen' }, SCREEN).ok, true);
    assert.equal(validateAction('check', { rule: 1, seen: '  ', verdict: 'not_seen' }, SCREEN).ok, false);
});

test('cover: before e after obrigatórios, antes < depois; possible false dispensa as capturas', () => {
    const valid = validateAction('cover', { item: 'gear', before: 2, after: 3, seen: 'engrenagem' }, SCREEN);
    assert.equal(valid.ok, true);
    assert.deepEqual(valid.action, { tool: 'cover', item: 'gear', before: 2, after: 3, seen: 'engrenagem' });
    assert.equal(validateAction('cover', { item: 'gear', before: 3, after: 2, seen: 'engrenagem' }, SCREEN).ok, false);
    assert.equal(validateAction('cover', { item: 'gear', before: 2, seen: 'engrenagem' }, SCREEN).ok, false);
    assert.equal(validateAction('cover', { item: 'gear', possible: false, seen: 'sem botão' }, SCREEN).ok, false);
});

test('cover impossível exige as mesmas capturas e diz que a tentativa é obrigatória', () => {
    const refused = validateAction('cover', { item: 'gear', possible: false, seen: 'sem botão' }, SCREEN);
    assert.equal(refused.ok, false);
    assert.match(refused.error, /impossível também exige a tentativa/);
    const accepted = validateAction('cover', { item: 'gear', before: 2, after: 3, possible: false, seen: 'sem botão' }, SCREEN);
    assert.equal(accepted.ok, true);
    assert.deepEqual(accepted.action, { tool: 'cover', item: 'gear', before: 2, after: 3, seen: 'sem botão', possible: false });
});

test('cover impossível sem ação entre as capturas é recusado com o aviso de tentativa', async () => {
    const client = scripted([
        reply([call('s2', 'screenshot', {})]),
        reply([call('m', 'move', { x: 10, y: 10 })]),
        reply([call('s3', 'screenshot', {})]),
        reply([call('c', 'cover', { item: 'minimize', before: 2, after: 3, possible: false, seen: 'sem botão' })]),
        reply([call('d', 'done', { summary: 'fim' })]),
    ]);
    const result = await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: ['minimize'] });
    assert.match(toolContents(client.calls[4]).at(-1), /ação entre as capturas 2 e 3.*impossível também exige a tentativa/);
    assert.deepEqual(result.coverage, []);
});

test('cover impossível aceita imagens iguais quando a tentativa foi feita', async () => {
    const same = { ...fakeDriver(), async screenshot() { return 'SAME'; } };
    const client = scripted([
        reply([call('s2', 'screenshot', {})]),
        reply([call('m', 'click', { x: 10, y: 10 })]),
        reply([call('s3', 'screenshot', {})]),
        reply([call('c', 'cover', { item: 'minimize', before: 2, after: 3, possible: false, seen: 'sem botão' })]),
        reply([call('d', 'done', { summary: 'fim' })]),
    ]);
    const result = await runScript({ client, driver: same, script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: ['minimize'] });
    assert.deepEqual(result.coverage, [{ item: 'minimize', before: 2, after: 3, seen: 'sem botão', possible: false }]);
});

test('cover: schema declara before e after, não shot', () => {
    const cover = TOOLS.find((item) => item.function.name === 'cover').function;
    assert.ok(cover.parameters.properties.before);
    assert.ok(cover.parameters.properties.after);
    assert.equal(cover.parameters.properties.shot, undefined);
    assert.match(cover.description, /ação/);
});

test('zoom responde captura N com aviso de recorte', async () => {
    const client = scripted([reply([call('z', 'zoom', { x: 0, y: 0, w: 100, h: 100 })]), reply([call('d', 'done', { summary: 'fim' })])]);
    await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: [] });
    assert.equal(toolContents(client.calls[1]).at(-1), 'captura 2 (recorte: a borda do zoom corta o que fica fora dele; não é prova)');
});

test('report com shot de zoom é recusado e não entra em reports', async () => {
    const client = scripted([
        reply([call('v', 'check', { rule: 1, shot: 1, seen: 'texto', verdict: 'violated' })]),
        reply([call('z', 'zoom', { x: 0, y: 0, w: 100, h: 100 })]),
        reply([call('r', 'report', { title: 't', rule: 1, shot: 2, seen: 's', expected: 'e' })]),
        reply([call('d', 'done', { summary: 'fim' })]),
    ]);
    const result = await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: [] });
    assert.equal(toolContents(client.calls[3]).at(-1), 'erro: captura 2 é um recorte de zoom; cite a captura inteira');
    assert.equal(result.reports.length, 0);
});

test('check violated com shot de zoom é recusado; check ok com shot de zoom é aceito', async () => {
    const client = scripted([
        reply([call('z', 'zoom', { x: 0, y: 0, w: 100, h: 100 })]),
        reply([call('v', 'check', { rule: 1, shot: 2, seen: 'texto', verdict: 'violated' })]),
        reply([call('k', 'check', { rule: 2, shot: 2, seen: 'texto', verdict: 'ok' })]),
        reply([call('d', 'done', { summary: 'fim' })]),
    ]);
    await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: [] });
    assert.equal(toolContents(client.calls[2]).at(-1), 'erro: captura 2 é um recorte de zoom; cite a captura inteira');
    assert.equal(toolContents(client.calls[3]).at(-1), 'ok');
});

test('cover sem ação entre as capturas é recusado e diz o que falta', async () => {
    const client = scripted([
        reply([call('s2', 'screenshot', {})]),
        reply([call('s3', 'screenshot', {})]),
        reply([call('c', 'cover', { item: 'gear', before: 2, after: 3, seen: 'engrenagem' })]),
        reply([call('d', 'done', { summary: 'fim' })]),
    ]);
    const result = await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: ['gear'] });
    assert.match(toolContents(client.calls[3]).at(-1), /ação entre as capturas 2 e 3/);
    assert.deepEqual(result.coverage, []);
});

test('cover com clique entre duas capturas diferentes grava before, after e seen', async () => {
    const client = scripted([
        reply([call('s2', 'screenshot', {})]),
        reply([call('m', 'click', { x: 10, y: 10 })]),
        reply([call('s3', 'screenshot', {})]),
        reply([call('c', 'cover', { item: 'gear', before: 2, after: 3, seen: 'engrenagem' })]),
        reply([call('d', 'done', { summary: 'fim' })]),
    ]);
    const result = await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: ['gear'] });
    assert.deepEqual(result.coverage, [{ item: 'gear', before: 2, after: 3, seen: 'engrenagem' }]);
});

test('cover de minimize sem clique entre as capturas é recusado', async () => {
    const client = scripted([
        reply([call('s2', 'screenshot', {})]),
        reply([call('m', 'move', { x: 10, y: 10 })]),
        reply([call('s3', 'screenshot', {})]),
        reply([call('c', 'cover', { item: 'minimize', before: 2, after: 3, seen: 'balão recolhido' })]),
        reply([call('d', 'done', { summary: 'fim' })]),
    ]);
    const result = await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: ['minimize'] });
    assert.match(toolContents(client.calls[4]).at(-1), /clique/);
    assert.deepEqual(result.coverage, []);
});

test('cover recusa capturas com imagens iguais', async () => {
    const same = { ...fakeDriver(), async screenshot() { return 'SAME'; } };
    const client = scripted([
        reply([call('s2', 'screenshot', {})]),
        reply([call('m', 'click', { x: 10, y: 10 })]),
        reply([call('s3', 'screenshot', {})]),
        reply([call('c', 'cover', { item: 'gear', before: 2, after: 3, seen: 'engrenagem' })]),
        reply([call('d', 'done', { summary: 'fim' })]),
    ]);
    await runScript({ client, driver: same, script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: ['gear'] });
    assert.match(toolContents(client.calls[4]).at(-1), /imagens iguais/);
});

test('cover recusa captura de zoom como antes ou depois', async () => {
    const client = scripted([
        reply([call('z', 'zoom', { x: 0, y: 0, w: 100, h: 100 })]),
        reply([call('m', 'click', { x: 10, y: 10 })]),
        reply([call('s3', 'screenshot', {})]),
        reply([call('c', 'cover', { item: 'gear', before: 2, after: 3, seen: 'engrenagem' })]),
        reply([call('d', 'done', { summary: 'fim' })]),
    ]);
    await runScript({ client, driver: fakeDriver(), script: 'roteiro', rules: 'regras', screen: SCREEN, budget: createBudget(), coverage: ['gear'] });
    assert.match(toolContents(client.calls[4]).at(-1), /recorte de zoom/);
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
