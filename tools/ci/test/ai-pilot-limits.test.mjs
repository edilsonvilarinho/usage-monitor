// Limites exatos do motor do piloto (#676, item 6c): zoom, wait, campos do report, faixas de regra, shot, cliente, corte de erro e teto de orçamento.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { LIMITS, MODEL, TOOLS, createBudget, createOpenRouterClient, runScript, validateAction } from '../ai-pilot.mjs';

const SCREEN = { width: 1920, height: 1080 };
const RULES = '1. Regra um\n9. Regra nove';
const NO_COST = { cost: 0, prompt_tokens: 1, completion_tokens: 1 };

let sequence = 0;
// Chamada de ferramenta no formato da API: argumentos são string JSON.
const call = (name, args) => {
    sequence += 1;
    return { id: `call-${sequence}`, type: 'function', function: { name, arguments: JSON.stringify(args) } };
};
const reply = (...calls) => ({ message: { content: null, tool_calls: calls }, usage: NO_COST });

// Cliente falso: entrega as respostas na ordem; depois só responde done.
function scripted(replies) {
    const queue = [...replies];
    return {
        async complete() {
            return queue.length > 0 ? queue.shift() : reply(call('done', { summary: 'fim' }));
        },
    };
}

const driver = {
    async screenshot() { return 'IMG'; },
    async zoom() { return 'ZOOM'; },
    async perform() {},
};

const zoomOf = (args) => validateAction('zoom', args, SCREEN);
const waitOf = (ms) => validateAction('wait', { ms }, SCREEN);
const reportOf = (patch) => validateAction('report', { title: 'Título', rule: 3, shot: 2, seen: 'texto lido', expected: 'esperado', ...patch }, SCREEN);

test('zoom: lado mínimo 16 passa e 15 é recusado, em w e em h', () => {
    assert.equal(zoomOf({ x: 0, y: 0, w: 16, h: 16 }).ok, true);
    assert.equal(zoomOf({ x: 0, y: 0, w: 15, h: 16 }).ok, false);
    assert.equal(zoomOf({ x: 0, y: 0, w: 16, h: 15 }).ok, false);
});

test('zoom: lado igual à tela passa e um pixel a mais é recusado', () => {
    assert.equal(zoomOf({ x: 0, y: 0, w: 1920, h: 1080 }).ok, true);
    assert.equal(zoomOf({ x: 0, y: 0, w: 1921, h: 16 }).ok, false);
    assert.equal(zoomOf({ x: 0, y: 0, w: 16, h: 1081 }).ok, false);
});

test('zoom: borda direita e inferior são semiabertas (x+w = tela passa, +1 é recusado)', () => {
    assert.equal(zoomOf({ x: 1904, y: 1064, w: 16, h: 16 }).ok, true);
    assert.equal(zoomOf({ x: 1905, y: 0, w: 16, h: 16 }).ok, false);
    assert.equal(zoomOf({ x: 0, y: 1065, w: 16, h: 16 }).ok, false);
});

test('zoom: origem negativa e coordenada fracionada são recusadas', () => {
    assert.equal(zoomOf({ x: -1, y: 0, w: 16, h: 16 }).ok, false);
    assert.equal(zoomOf({ x: 0, y: -1, w: 16, h: 16 }).ok, false);
    assert.equal(zoomOf({ x: 1.5, y: 0, w: 16, h: 16 }).ok, false);
});

test('wait: 0 e o teto de maxWaitMs passam; negativo, fracionado e teto+1 são recusados', () => {
    assert.equal(LIMITS.maxWaitMs, 5000);
    assert.equal(waitOf(0).ok, true);
    assert.equal(waitOf(5000).ok, true);
    assert.equal(waitOf(-1).ok, false);
    assert.equal(waitOf(1.5).ok, false);
    assert.equal(waitOf(5001).ok, false);
});

test('wait: mensagem traz o intervalo exato', () => {
    assert.equal(waitOf(5001).error, 'campo inválido em wait: ms inteiro entre 0 e 5000');
});

test('type: texto de 1 a 200 caracteres; mensagem traz o intervalo exato', () => {
    const typeOf = (text) => validateAction('type', { text }, SCREEN);
    assert.equal(typeOf('a'.repeat(200)).ok, true);
    assert.equal(typeOf('a'.repeat(201)).ok, false);
    assert.equal(typeOf('').ok, false);
    assert.equal(typeOf('a'.repeat(201)).error, 'campo inválido em type: texto de 1 a 200 caracteres');
});

test('report: cada campo de texto vazio, sozinho, é recusado', () => {
    assert.equal(reportOf({}).ok, true);
    assert.equal(reportOf({ title: '' }).ok, false);
    assert.equal(reportOf({ seen: '' }).ok, false);
    assert.equal(reportOf({ expected: '' }).ok, false);
    assert.equal(reportOf({ title: '   ' }).ok, false);
});

test('report: rule 0 e 10 são recusados; 1 e 9 passam', () => {
    for (const rule of [1, 9]) assert.equal(reportOf({ rule }).ok, true, `report rule ${rule}`);
    for (const rule of [0, 10]) assert.equal(reportOf({ rule }).ok, false, `report rule ${rule}`);
});

test('check: rule 0 e 10 são recusados; 1 e 9 passam', () => {
    const checkOf = (rule) => validateAction('check', { rule, seen: 'x', verdict: 'ok', shot: 1 }, SCREEN);
    for (const rule of [1, 9]) assert.equal(checkOf(rule).ok, true, `check rule ${rule}`);
    for (const rule of [0, 10]) assert.equal(checkOf(rule).ok, false, `check rule ${rule}`);
});

test('schema de check: rule vai de 1 a 9', () => {
    const tool = TOOLS.find((item) => item.function.name === 'check');
    assert.deepEqual(tool.function.parameters.properties.rule, { type: 'integer', minimum: 1, maximum: 9 });
});

test('shot: 0 é recusado em report e em check; 1 é a primeira captura e passa', () => {
    assert.equal(reportOf({ shot: 0 }).ok, false);
    assert.equal(reportOf({ shot: 1 }).ok, true);
    assert.equal(validateAction('check', { rule: 1, seen: 'x', verdict: 'ok', shot: 0 }, SCREEN).ok, false);
    assert.equal(validateAction('check', { rule: 1, seen: 'x', verdict: 'ok', shot: 1 }, SCREEN).ok, true);
});

test('cover: item e seen vazios são recusados; before igual a after é recusado', () => {
    const coverOf = (patch) => validateAction('cover', { item: 'move', before: 1, after: 2, seen: 'x', ...patch }, SCREEN);
    assert.equal(coverOf({}).ok, true);
    assert.equal(coverOf({ item: '' }).ok, false);
    assert.equal(coverOf({ seen: '' }).ok, false);
    assert.equal(coverOf({ before: 2, after: 2 }).ok, false);
});

test('runScript: checks saem ordenados por regra, não pela ordem de chegada', async () => {
    const result = await runScript({
        client: scripted([
            reply(call('check', { rule: 9, verdict: 'ok', seen: 'nove', shot: 1 })),
            reply(call('check', { rule: 1, verdict: 'ok', seen: 'um', shot: 1 })),
        ]),
        driver,
        script: 'roteiro',
        rules: RULES,
        screen: SCREEN,
        budget: createBudget(),
    });
    assert.deepEqual(result.checks.map((check) => check.rule), [1, 9]);
});

test('cliente: chave vazia, 0 e ausente são recusadas antes da rede', () => {
    const failingFetch = async () => { throw new Error('nao deve chamar a rede'); };
    for (const apiKey of ['', 0, undefined]) {
        assert.throws(() => createOpenRouterClient({ fetch: failingFetch, apiKey, model: MODEL }), { message: 'chave de API ausente' }, String(apiKey));
    }
});

test('cliente: erro HTTP é cortado em 300 caracteres e começa pelo prefixo', async () => {
    const client = createOpenRouterClient({
        fetch: async () => ({ ok: false, status: 500, text: async () => 'x'.repeat(400) }),
        apiKey: 'chave-de-teste',
        model: MODEL,
    });
    await assert.rejects(client.complete({ messages: [], tools: TOOLS, maxTokens: 10 }), (error) => {
        assert.equal(error.message.length, 300);
        assert.ok(error.message.startsWith('OpenRouter HTTP 500: '));
        return true;
    });
});

test('cliente: choices[0].message que não é objeto é erro de resposta', async () => {
    const client = createOpenRouterClient({
        fetch: async () => ({ ok: true, status: 200, json: async () => ({ choices: [{ message: 'texto' }] }) }),
        apiKey: 'chave-de-teste',
        model: MODEL,
    });
    await assert.rejects(client.complete({ messages: [], tools: TOOLS, maxTokens: 10 }), { message: 'resposta sem choices[0].message' });
});

test('orçamento do roteiro: gasto + reserva exatamente no teto passa; um centavo a mais bloqueia', () => {
    const exact = createBudget();
    exact.charge({ cost: 0.18, prompt_tokens: 0, completion_tokens: 0 });
    assert.equal(exact.canCall(), null);
    const over = createBudget();
    over.charge({ cost: 0.19, prompt_tokens: 0, completion_tokens: 0 });
    assert.equal(over.canCall(), 'script-budget');
});

test('orçamento do piloto: gasto + reserva exatamente no teto passa', () => {
    assert.equal(createBudget({ pilotSpentUsd: 4.98 }).canCall(), null);
});

test('orçamento: tolerância de 1e-9 em dólar; 5e-10 acima do teto ainda passa', () => {
    const script = createBudget();
    script.charge({ cost: 0.18 + 5e-10, prompt_tokens: 0, completion_tokens: 0 });
    assert.equal(script.canCall(), null);
    assert.equal(createBudget({ pilotSpentUsd: 4.98 + 5e-10 }).canCall(), null);
});

test('orçamento novo começa com prompt e completion zerados; nova conversa zera completion', () => {
    const budget = createBudget();
    assert.equal(budget.lastPromptTokens, 0);
    assert.equal(budget.lastCompletionTokens, 0);
    budget.startConversation();
    assert.equal(budget.lastCompletionTokens, 0);
});
