// Testes do runner do piloto de IA (#653, A03c). Dependências falsas: nenhuma rede, processo ou escrita em disco.
// Os três últimos blocos leem fixtures do repositório (pilot-cases.json, pilot-rules.md, seed.ps1) sem escrever nada.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { childEnv, exitCodeOf, parseArgs, parseJobs, planEvidence, planRun, renderReport, runDry, runPaid } from '../ai-pilot-run.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const REAL_APP = resolve(HERE, '..', '..', 'real-app');
const CASES_PATH = resolve(REAL_APP, 'pilot-cases.json');
const RULES_PATH = resolve(REAL_APP, 'pilot-rules.md');
const SEED_PATH = resolve(REAL_APP, 'seed.ps1');
const SCREEN = { width: 1000, height: 800 };
const SCRIPT = 'roteiro de teste';
const RULES = 'regras do piloto';
const TAG = /^v\d+\.\d+\.\d+(-beta\.\d+)?$/;
const FULL_ARGV = ['--mode', 'dry', '--case', 'gear-balloon', '--side', 'bad', '--exe', 'C:/app/Usage Monitor.exe', '--data-dir', 'C:/data', '--out', 'C:/out'];

// Driver falso: screenshot devolve as imagens na ordem; perform grava as ações recebidas.
function fakeDriver(images = ['IMG-A', 'IMG-B', 'IMG-C', 'IMG-D', 'IMG-E', 'IMG-F', 'IMG-G', 'IMG-H']) {
    const driver = {
        performed: [],
        shots: 0,
        ready: async () => ({ ...SCREEN }),
        screenshot: async () => images[driver.shots++],
        perform: async (action) => { driver.performed.push(action) },
        close: () => {},
    };
    return driver;
}

const reply = (toolCalls, cost = 0.001) => ({ message: { content: null, tool_calls: toolCalls }, usage: { cost, prompt_tokens: 10, completion_tokens: 5 } });
const call = (id, name, args) => ({ id, type: 'function', function: { name, arguments: JSON.stringify(args) } });
const keyBody = (usage, limit = null) => ({ data: { limit, limit_remaining: limit === null ? null : limit - usage, usage } });
const fakeFetchKey = (...bodies) => async () => (bodies.length > 1 ? bodies.shift() : bodies[0]);
// Checks das nove regras ok na captura 1: libera done sem pendência.
const ALL_OK = [1, 2, 3, 4, 5, 6, 7, 8, 9].map((rule) => call(`k${rule}`, 'check', { rule, shot: 1, seen: 'texto', verdict: 'ok' }));

// Cliente falso: o laço consome `loopReplies` na ordem (a última repete); a confirmação responde `verdictReply`.
function fakeClient(loopReplies, verdictReply = reply([call('v', 'verdict', { confirmed: true, why: 'visível' })])) {
    const client = { calls: { loop: 0, verdict: 0 } };
    client.complete = async (request) => {
        if (request.tools[0].function.name === 'verdict') {
            client.calls.verdict += 1;
            return verdictReply;
        }
        const index = Math.min(client.calls.loop, loopReplies.length - 1);
        client.calls.loop += 1;
        return loopReplies[index];
    };
    return client;
}

// Parse de argv -----------------------------------------------------------

test('parseArgs aceita o conjunto completo e devolve as opções nomeadas', () => {
    const parsed = parseArgs(FULL_ARGV);
    assert.equal(parsed.ok, true);
    assert.deepEqual(parsed.options, {
        mode: 'dry', caseId: 'gear-balloon', side: 'bad', exe: 'C:/app/Usage Monitor.exe', dataDir: 'C:/data', out: 'C:/out',
    });
});

test('parseArgs recusa opção obrigatória ausente, modo ou lado fora da lista e flag desconhecida', () => {
    const without = (flag) => FULL_ARGV.filter((item, k) => k !== FULL_ARGV.indexOf(flag) && k !== FULL_ARGV.indexOf(flag) + 1);
    const withValue = (flag, value) => FULL_ARGV.map((item, k) => (FULL_ARGV[k - 1] === flag ? value : item));
    const missingCase = parseArgs(without('--case'));
    assert.equal(missingCase.ok, false);
    assert.match(missingCase.error, /--case/);
    assert.equal(parseArgs(without('--out')).ok, false);
    assert.equal(parseArgs(withValue('--mode', 'turbo')).ok, false);
    assert.equal(parseArgs(withValue('--side', 'ugly')).ok, false);
    assert.equal(parseArgs([...FULL_ARGV, '--extra', '1']).ok, false);
});

// Modo dry ----------------------------------------------------------------

const PLAN_CASES = { 'gear-balloon': { badTag: 'v1.0.0', goodTag: 'v1.1.0' }, script: 'roteiro', coverage: [] };

test('planRun recusa caso desconhecido e chave que não é caso, com saída 2', () => {
    assert.deepEqual(planRun({ caseId: 'nada', side: 'bad', mode: 'dry' }, PLAN_CASES, {}), { ok: false, error: 'caso desconhecido: nada', exit: 2 });
    assert.equal(planRun({ caseId: 'script', side: 'bad', mode: 'dry' }, PLAN_CASES, {}).ok, false);
    assert.equal(planRun({ caseId: 'coverage', side: 'bad', mode: 'dry' }, PLAN_CASES, {}).ok, false);
});

test('planRun escolhe badTag para o lado bad e goodTag para o lado good', () => {
    assert.equal(planRun({ caseId: 'gear-balloon', side: 'bad', mode: 'dry' }, PLAN_CASES, {}).version, 'v1.0.0');
    assert.equal(planRun({ caseId: 'gear-balloon', side: 'good', mode: 'dry' }, PLAN_CASES, {}).version, 'v1.1.0');
});

test('planRun pago sem OPENROUTER_API_KEY recusa com saída 2 e com a chave passa', () => {
    assert.deepEqual(planRun({ caseId: 'gear-balloon', side: 'bad', mode: 'paid' }, PLAN_CASES, {}), { ok: false, error: 'OPENROUTER_API_KEY ausente', exit: 2 });
    assert.equal(planRun({ caseId: 'gear-balloon', side: 'bad', mode: 'paid' }, PLAN_CASES, { OPENROUTER_API_KEY: 'k' }).ok, true);
});

test('parseJobs aceita inteiro maior que 0 e recusa ausente, zero, fracionário e texto', () => {
    assert.equal(parseJobs(['--jobs', '3']), 3);
    assert.equal(parseJobs(['--jobs', '1']), 1);
    assert.equal(parseJobs(['--plan-budget']), null);
    assert.equal(parseJobs(['--jobs', '0']), null);
    assert.equal(parseJobs(['--jobs', '1.5']), null);
    assert.equal(parseJobs(['--jobs', 'x']), null);
});

test('exitCodeOf: só done é 0; roteiro sem done aceito é 4', () => {
    assert.equal(exitCodeOf('done'), 0);
    for (const stop of ['done-incomplete', 'max-steps', 'no-tool-call']) assert.equal(exitCodeOf(stop), 4);
});

test('runDry recusa a lista inteira quando uma ação é inválida, antes de executar qualquer uma', async () => {
    const driver = fakeDriver();
    await assert.rejects(runDry({ driver, probe: [{ tool: 'click', x: 10, y: 10 }, { tool: 'nao-existe' }] }));
    assert.deepEqual(driver.performed, []);
    assert.equal(driver.shots, 0);
});

test('runDry captura antes e depois de cada ação; screenshot não é executado como ação', async () => {
    const driver = fakeDriver();
    const shots = await runDry({ driver, probe: [{ tool: 'screenshot' }, { tool: 'click', x: 10, y: 20 }] });
    assert.deepEqual(shots.map((shot) => shot.step), [0, 1, 2]);
    assert.deepEqual(shots.map((shot) => shot.image), ['IMG-A', 'IMG-B', 'IMG-C']);
    assert.deepEqual(driver.performed, [{ tool: 'click', x: 10, y: 20 }]);
});

// Modo paid ---------------------------------------------------------------

test('runPaid recusa a chave antes de criar qualquer cliente', async () => {
    let made = 0;
    const result = await runPaid({
        fetchKey: fakeFetchKey(keyBody(0.08, 0.1)),
        makeClient: () => { made += 1; return fakeClient([]); },
        driver: fakeDriver(), script: SCRIPT, rules: RULES, screen: SCREEN,
    });
    assert.deepEqual(result, { stop: 'key-refused', reason: 'remaining-too-low' });
    assert.equal(made, 0);
});

test('runPaid roda o laço e lê o gasto da chave de novo no fim', async () => {
    const result = await runPaid({
        fetchKey: fakeFetchKey(keyBody(0.2), keyBody(0.5)),
        makeClient: () => fakeClient([reply([...ALL_OK, call('1', 'done', { summary: 'ok' })])]),
        driver: fakeDriver(), script: SCRIPT, rules: RULES, screen: SCREEN,
    });
    assert.equal(result.stop, 'done');
    assert.equal(result.steps, 1);
    assert.equal(Number(result.costUsd.toFixed(6)), 0.001);
    assert.equal(result.keyUsageBefore, 0.2);
    assert.equal(result.keyUsageAfter, 0.5);
    assert.deepEqual(result.reports, []);
});

test('runPaid limita a três confirmações e marca o resto como confirm-cap', async () => {
    const report = (n) => call(`r${n}`, 'report', { title: `achado ${n}`, rule: 1, shot: 1, seen: 'texto', expected: 'esperado' });
    const violated = call('v', 'check', { rule: 1, shot: 1, seen: 'texto', verdict: 'violated' });
    const client = fakeClient([reply([violated, report(1), report(2), report(3), report(4), ...ALL_OK.slice(1), call('d', 'done', { summary: 'ok' })])]);
    const result = await runPaid({
        fetchKey: fakeFetchKey(keyBody(0.2), keyBody(0.3)),
        makeClient: () => client,
        driver: fakeDriver(), script: SCRIPT, rules: RULES, screen: SCREEN,
    });
    assert.deepEqual(result.reports.map((item) => item.confirmed), [true, true, true, null]);
    assert.deepEqual(result.reports.map((item) => item.why), ['visível', 'visível', 'visível', 'confirm-cap']);
    assert.equal(result.reports[0].shot, 'evidence/001.jpg');
    assert.equal(result.reports[0].screenshot, 'IMG-A');
    assert.equal(client.calls.verdict, 3);
    assert.equal(Number(result.costUsd.toFixed(6)), 0.004);
});

// Evidência e relatório ---------------------------------------------------

test('planEvidence tira o base64 do resultado e devolve os arquivos das capturas', () => {
    const result = {
        stop: 'done', steps: 2, costUsd: 0, keyUsageBefore: 0.1, keyUsageAfter: null,
        reports: [
            { step: 1, title: 'a', rule: '1', where: 'barra', shot: 'evidence/001.jpg', screenshot: 'AAAA', confirmed: true, why: 'ok' },
            { step: 2, title: 'b', rule: '4', where: 'balão', shot: 'evidence/002.jpg', screenshot: 'BBBB', confirmed: null, why: 'confirm-cap' },
        ],
    };
    const plan = planEvidence(result);
    assert.deepEqual(plan.files, [{ rel: 'evidence/001.jpg', base64: 'AAAA' }, { rel: 'evidence/002.jpg', base64: 'BBBB' }]);
    assert.equal(plan.result.reports[0].shot, 'evidence/001.jpg');
    assert.equal('screenshot' in plan.result.reports[0], false);
    assert.equal(JSON.stringify(plan.result).includes('AAAA'), false);
});

test('renderReport escreve em português, escapa a tabela e não imprime base64', () => {
    const result = {
        stop: 'done', steps: 3, costUsd: 0.1234, keyUsageBefore: 1.5, keyUsageAfter: null,
        reports: [
            { step: 2, title: 'Balão | corta\nlinha', rule: '1', where: 'barra', shot: 'evidence/001.jpg', shotNumber: 1, seen: 'Balão | corta', expected: 'balão inteiro', confirmed: true, why: 'ok' },
            { step: 3, title: 'Texto sobreposto', rule: '5', where: 'balão', shot: 'evidence/002.jpg', confirmed: false, why: 'sem marca' },
            { step: 4, title: 'Conta fora', rule: '3', where: 'barra', shot: 'evidence/003.jpg', confirmed: null, why: 'confirm-cap', screenshot: 'SEGREDO-BASE64' },
        ],
    };
    const md = renderReport(result, { caseId: 'gear-balloon', side: 'bad', version: 'v42.3.0-beta.12' });
    for (const text of ['gear-balloon', 'v42.3.0-beta.12', '0.1234', '1.5', 'Balão \\| corta linha', 'evidence/001.jpg', 'confirmado', 'não confirmado', 'não medido: confirm-cap', '- Lido: Balão \\| corta', '- Pedido: balão inteiro', '- Captura nº 1']) {
        assert.ok(md.includes(text), text);
    }
    assert.equal(md.includes('SEGREDO-BASE64'), false);
});

// Ambiente do filho -------------------------------------------------------

test('childEnv tira a chave da OpenRouter do ambiente do filho sem mudar o original', () => {
    const env = { PATH: '/bin', OPENROUTER_API_KEY: 'sk-or-v1-x' };
    assert.deepEqual(childEnv(env), { PATH: '/bin' });
    assert.equal(env.OPENROUTER_API_KEY, 'sk-or-v1-x');
});

// Fixtures: casos e regras -----------------------------------------------

const rawCases = readFileSync(CASES_PATH, 'utf8');
const cases = JSON.parse(rawCases);
test('script manda levar o ponteiro até cada item de cobertura, com captura antes e depois, e clicar para minimizar e restaurar', () => {
    assert.match(cases.script, /marque cada item de cobertura com cover: leve o ponteiro até cada conta, até a engrenagem e até cada botão das pontas, com uma captura antes e outra depois; para minimizar e restaurar, clique no botão de menos e depois no de mais do balão da conta/);
});

test('renderReport cobertura: coberto com capturas A → B, impossível com motivo, não coberto', () => {
    const md = renderReport({
        steps: 1,
        costUsd: 0,
        reports: [],
        checks: [],
        coverage: [{ item: 'gear', before: 2, after: 3, seen: 'engrenagem' }, { item: 'minimize', before: 4, after: 5, possible: false, seen: 'sem botão' }],
    }, { caseId: 'c', side: 'lado', version: 'v', coverage: ['gear', 'minimize', 'restore'] });
    assert.match(md, /^- gear: coberto \(capturas 2 → 3\)$/m);
    assert.match(md, /^- minimize: impossível \(capturas 4 → 5\): sem botão$/m);
    assert.match(md, /^- restore: não coberto$/m);
});

const ids = Object.keys(cases).filter((key) => !['script', 'coverage', 'coverageLabels'].includes(key));
const seedLine = readFileSync(SEED_PATH, 'utf8').split('\n').find((line) => line.includes('ValidateSet('));
const SCENARIOS = [...seedLine.matchAll(/'([^']+)'/g)].map((match) => match[1]);

test('pilot-cases.json tem os quatro casos com ids únicos e tags válidas', () => {
    assert.deepEqual([...ids].sort(), ['balloon-stays', 'gear-balloon', 'reset-date', 'thin-bar']);
    for (const id of ids) assert.equal(rawCases.match(new RegExp(`"${id}"\\s*:`, 'g')).length, 1, id);
    assert.equal(typeof cases.script, 'string');
    for (const id of ids) {
        const item = cases[id];
        assert.equal(Number.isInteger(item.issue), true, id);
        assert.match(item.badTag, TAG, id);
        assert.match(item.goodTag, TAG, id);
    }
});

import { validateAction } from '../ai-pilot.mjs';

test('pilot-cases.json aponta só para cenários do ValidateSet do seed.ps1 e todo probe passa em validateAction numa tela 1024x768', () => {
    const PROBE_SCREEN = { width: 1024, height: 768 };
    assert.ok(SCENARIOS.length > 0);
    for (const id of ids) {
        const item = cases[id];
        assert.ok(SCENARIOS.includes(item.scenario), `${id}: ${item.scenario}`);
        assert.ok(Array.isArray(item.probe) && item.probe.length > 0, id);
        for (const action of item.probe) assert.equal(validateAction(action.tool, action, PROBE_SCREEN).ok, true, `${id}: ${action.tool}`);
    }
});

test('script e regras não citam número de issue nem a palavra defeito', () => {
    const rules = readFileSync(RULES_PATH, 'utf8');
    for (const text of [cases.script, rules]) {
        assert.doesNotMatch(text, /defeito/i);
        assert.doesNotMatch(text, /#\d+|issue/i);
    }
    for (const id of ids) assert.equal(cases.script.includes(String(cases[id].issue)), false, id);
});

test('pilot-rules.md tem o cabeçalho literal, as 9 regras numeradas e a regra 5 com a exceção de reticências e dica', () => {
    const rules = readFileSync(RULES_PATH, 'utf8');
    assert.match(rules, /^Regras do produto\. Um achado precisa citar a regra pelo número\./);
    assert.equal(rules.match(/^\d\. /gm).length, 9);
    assert.match(rules, /^5\. Nenhum texto aparece cortado no meio, sobreposto a outro ou fora da área dele\. Reticências no fim de um nome e a dica que aparece sobre um botão são normais\.$/m);
});

// Gravação de transcrição e resultado do modo dry (#653, 3c/3d): nunca imagem nem chave.
import * as runner from '../ai-pilot-run.mjs';

test('recordingClient chama onTurn a cada resposta com texto, ferramentas e uso, sem imagem', async () => {
    const turns = [];
    const inner = { complete: async () => ({ message: { content: 'vi a barra', tool_calls: [{ function: { name: 'click', arguments: '{"x":1}' } }] }, usage: { cost: 0.01 } }) };
    const client = runner.recordingClient(inner, (turn) => turns.push(turn));
    await client.complete({ messages: [{ role: 'user', content: [{ type: 'image_url', image_url: { url: 'data:image/jpeg;base64,IMG-SECRET' } }] }], tools: [] });
    assert.deepEqual(turns, [{ turn: 1, text: 'vi a barra', toolCalls: [{ name: 'click', arguments: '{"x":1}' }], usage: { cost: 0.01 } }]);
    assert.ok(!JSON.stringify(turns).includes('IMG-SECRET'));
});

test('recordingClient numera as respostas em sequência entre chamadas', async () => {
    const turns = [];
    const inner = { complete: async () => ({ message: { content: null, tool_calls: [] }, usage: null }) };
    const client = runner.recordingClient(inner, (turn) => turns.push(turn.turn));
    await client.complete({ messages: [], tools: [] });
    await client.complete({ messages: [], tools: [] });
    assert.deepEqual(turns, [1, 2]);
});

test('dryResult lista ações e capturas sem base64', () => {
    const shots = [{ step: 0, action: 'screenshot', image: 'IMG-A' }, { step: 1, action: 'move', image: 'IMG-B' }];
    const result = runner.dryResult(shots);
    assert.deepEqual(result, { stop: 'done', steps: 1, actions: ['move'], captures: ['evidence/000.jpg', 'evidence/001.jpg'] });
    assert.ok(!JSON.stringify(result).includes('IMG-'));
});

test('renderDryReport traz caso, lado, versão e a lista de capturas', () => {
    const result = { stop: 'done', steps: 1, actions: ['move'], captures: ['evidence/000.jpg', 'evidence/001.jpg'] };
    const text = runner.renderDryReport(result, { caseId: 'gear-balloon', side: 'bad', version: 'v42.3.0-beta.12' });
    assert.match(text, /gear-balloon/);
    assert.match(text, /bad/);
    assert.match(text, /v42\.3\.0-beta\.12/);
    assert.match(text, /evidence\/001\.jpg/);
});

test('transcriptJsonl grava uma linha por turno e marca as confirmações', () => {
    const turns = [
        { turn: 1, text: 'a', toolCalls: [], usage: null, confirmation: false },
        { turn: 2, text: 'b', toolCalls: [], usage: null, confirmation: true },
    ]
    const lines = runner.transcriptJsonl(turns).split('\n').filter(Boolean).map((line) => JSON.parse(line))
    assert.equal(lines.length, 2)
    assert.deepEqual(lines.map((line) => line.confirmation), [false, true])
});

test('report com shot inexistente é recusado e não entra em reports', async () => {
    const report = call('r', 'report', { title: 'achado fantasma', rule: 1, shot: 9, seen: 'texto', expected: 'esperado' });
    const violated = call('v', 'check', { rule: 1, shot: 1, seen: 'texto', verdict: 'violated' });
    const client = fakeClient([reply([violated, report, ...ALL_OK.slice(1), call('d', 'done', { summary: 'ok' })])]);
    const result = await runPaid({
        fetchKey: fakeFetchKey(keyBody(0.2), keyBody(0.3)),
        makeClient: () => client,
        driver: fakeDriver(), script: SCRIPT, rules: RULES, screen: SCREEN,
    });
    assert.equal(result.reports.length, 0);
    assert.equal(client.calls.verdict, 0);
});

test('confirmReport via runPaid recebe a imagem do shot do report, não a última captura', async () => {
    const report = call('r', 'report', { title: 'achado na captura 2', rule: 1, shot: 2, seen: 'texto', expected: 'esperado' });
    const violated = call('v', 'check', { rule: 1, shot: 2, seen: 'texto', verdict: 'violated' });
    const base = fakeClient([
        reply([call('s2', 'screenshot', {})]),
        reply([call('s3', 'screenshot', {})]),
        reply([violated, report, ...ALL_OK.slice(1), call('d', 'done', { summary: 'ok' })]),
    ]);
    const verdicts = [];
    const client = {
        calls: base.calls,
        complete: async (request) => {
            if (request.tools[0].function.name === 'verdict') verdicts.push(JSON.stringify(request));
            return base.complete(request);
        },
    };
    const result = await runPaid({
        fetchKey: fakeFetchKey(keyBody(0.2), keyBody(0.3)),
        makeClient: () => client,
        driver: fakeDriver(['IMG-A', 'IMG-B', 'IMG-C']), script: SCRIPT, rules: RULES, screen: SCREEN,
    });
    assert.equal(result.reports[0].screenshot, 'IMG-B');
    assert.equal(verdicts.length, 1);
    assert.ok(verdicts[0].includes('IMG-B'));
    assert.equal(verdicts[0].includes('IMG-C'), false);
});

test('laço e confirmações juntos não passam de maxScriptUsd: confirmação com orçamento esgotado vira script-budget', async () => {
    const report = call('r', 'report', { title: 'achado caro', rule: 1, shot: 1, seen: 'texto', expected: 'esperado' });
    const violated = call('v', 'check', { rule: 1, shot: 1, seen: 'texto', verdict: 'violated' });
    const client = fakeClient([reply([violated, report, ...ALL_OK.slice(1), call('d', 'done', { summary: 'ok' })], 0.19)]);
    const result = await runPaid({
        fetchKey: fakeFetchKey(keyBody(0.2), keyBody(0.3)),
        makeClient: () => client,
        driver: fakeDriver(), script: SCRIPT, rules: RULES, screen: SCREEN,
    });
    assert.equal(result.reports[0].confirmed, null);
    assert.equal(result.reports[0].why, 'script-budget');
    assert.equal(client.calls.verdict, 0);
    assert.ok(result.costUsd <= 0.2 + 1e-9);
});
