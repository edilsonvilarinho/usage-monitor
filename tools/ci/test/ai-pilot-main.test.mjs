// Testes de ponta a ponta do `main` do runner do piloto (#676, item 6b): dependências falsas por parâmetro e pasta temporária real.
// Nenhum processo, rede ou escrita fora da pasta temporária.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { main } from '../ai-pilot-run.mjs';

const KEY = 'sk-or-v1-CHAVE-SECRETA-676';
const ENV = { PATH: '/bin', OPENROUTER_API_KEY: KEY };
const SCREEN = { width: 1000, height: 800 };
// Bytes reais de JPEG (início e fim de marcador), um por captura.
const JPEGS = [Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00]), Buffer.from([0xff, 0xd8, 0xff, 0xd9]), Buffer.from([0xff, 0xd8, 0x42, 0x00, 0xff])];
const call = (id, name, args) => ({ id, type: 'function', function: { name, arguments: JSON.stringify(args) } });
const CASES = {
    'gear-balloon': { scenario: 'gear', badTag: 'v1.0.0', goodTag: 'v1.1.0', issue: 1, probe: [{ tool: 'click', x: 10, y: 20 }, { tool: 'screenshot' }] },
    script: 'roteiro',
    coverage: [],
    coverageLabels: {},
};
const RULES_2_TO_9 = [2, 3, 4, 5, 6, 7, 8, 9].map((rule) => call(`k${rule}`, 'check', { rule, shot: 1, seen: 'texto', verdict: 'ok' }));
const ALL_OK = [call('k1', 'check', { rule: 1, shot: 1, seen: 'texto', verdict: 'ok' }), ...RULES_2_TO_9];
const VIOLATED = call('v', 'check', { rule: 1, shot: 1, seen: 'texto', verdict: 'violated' });
const REPORT = call('r', 'report', { title: 'achado', rule: 1, shot: 1, seen: 'texto', expected: 'esperado' });
const DONE = call('d', 'done', { summary: 'ok' });

const response = (body) => ({ ok: true, status: 200, json: async () => body, text: async () => JSON.stringify(body) });
const reply = (toolCalls, cost = 0.001) => ({ choices: [{ message: { content: null, tool_calls: toolCalls } }], usage: { cost, prompt_tokens: 10, completion_tokens: 5 } });
const keyBody = (usage) => ({ data: { limit: null, limit_remaining: null, usage } });

// Rede falsa: a chave responde pelo corpo na ordem (a última repete); o chat responde pelo laço; a confirmação, pelo veredito.
function network({ keyBodies = [keyBody(0.2), keyBody(0.5)], replies = [], verdict = reply([call('v', 'verdict', { confirmed: true, why: 'visível' })], 0.0456) } = {}) {
    const net = { calls: [], keyReads: 0, chatTurns: 0 };
    net.fetch = async (url, init) => {
        net.calls.push({ url, init });
        if (url === 'https://openrouter.ai/api/v1/key') {
            const body = keyBodies[Math.min(net.keyReads, keyBodies.length - 1)];
            net.keyReads += 1;
            return response(body);
        }
        const request = JSON.parse(init.body);
        if (request.tools[0].function.name === 'verdict') return response(verdict);
        const body = replies[Math.min(net.chatTurns, replies.length - 1)];
        net.chatTurns += 1;
        return response(body);
    };
    return net;
}

// Driver falso: captura devolve base64 dos JPEGs na ordem; `failReady` simula driver que cai ao abrir a tela.
function fakeDriver({ failReady = false } = {}) {
    const driver = {
        closed: 0,
        shots: 0,
        ready: async () => {
            if (failReady) throw new Error('driver caiu');
            return { ...SCREEN };
        },
        screenshot: async () => JPEGS[driver.shots++ % JPEGS.length].toString('base64'),
        perform: async () => {},
        close: async () => { driver.closed += 1; },
    };
    return driver;
}

function harness({ driver = fakeDriver(), net = network(), cases = CASES, ledger = { spentUsd: 0.1104 } } = {}) {
    const h = { driver, net, seeds: [], spawns: [], out: [], err: [] };
    h.deps = {
        seed: async (args) => { h.seeds.push(args); },
        spawnDriver: (args) => { h.spawns.push(args); return driver; },
        fetch: net.fetch,
        readCases: async () => cases,
        readRules: async () => 'regras do piloto',
        readLedger: async () => ledger,
        stdout: (text) => { h.out.push(text); },
        stderr: (text) => { h.err.push(text); },
    };
    return h;
}

const argvFor = (dir, { mode = 'dry', caseId = 'gear-balloon', side = 'bad' } = {}) => [
    '--mode', mode, '--case', caseId, '--side', side, '--exe', 'C:/app/Usage Monitor.exe', '--data-dir', join(dir, 'data'), '--out', join(dir, 'out'),
];

async function withTemp(run) {
    const dir = await mkdtemp(join(tmpdir(), 'ai-pilot-main-'));
    try {
        await run(dir);
    } finally {
        await rm(dir, { recursive: true, force: true });
    }
}

async function filesUnder(dir) {
    const files = [];
    for (const entry of await readdir(dir, { withFileTypes: true })) {
        const path = join(dir, entry.name);
        if (entry.isDirectory()) files.push(...(await filesUnder(path)));
        else files.push(path);
    }
    return files;
}

const readOut = (dir, name) => readFile(join(dir, 'out', name), 'utf8');

// Dry ---------------------------------------------------------------------

test('dry grava result.json, report.md e evidence/000.jpg.. com os bytes reais, e fecha o driver', () => withTemp(async (dir) => {
    const h = harness();
    const code = await main(argvFor(dir), ENV, h.deps);
    assert.equal(code, 0);
    assert.deepEqual(JSON.parse(await readOut(dir, 'result.json')), {
        stop: 'done', steps: 2, actions: ['click', 'screenshot'], captures: ['evidence/000.jpg', 'evidence/001.jpg', 'evidence/002.jpg'],
    });
    assert.match(await readOut(dir, 'report.md'), /^# Piloto de IA \(dry\): gear-balloon$/m);
    for (const [index, bytes] of JPEGS.entries()) {
        assert.deepEqual(await readFile(join(dir, 'out', 'evidence', `00${index}.jpg`)), bytes);
    }
    assert.equal(h.driver.closed, 1);
}));

test('seed recebe badTag no lado bad e goodTag no lado good, e o relatório mostra a mesma versão', () => withTemp(async (dir) => {
    for (const [side, version] of [['bad', 'v1.0.0'], ['good', 'v1.1.0']]) {
        const h = harness();
        assert.equal(await main(argvFor(dir, { side }), ENV, h.deps), 0);
        assert.equal(h.seeds[0].version, version);
        assert.equal(h.seeds[0].scenario, 'gear');
        assert.equal(h.seeds[0].dataDir, join(dir, 'data'));
        assert.match(await readOut(dir, 'report.md'), new RegExp(`^- Versão: ${version.replace(/\./g, '\\.')}$`, 'm'));
    }
}));

test('driver é fechado mesmo quando o roteiro lança erro, e o erro vai para stderr com saída 1', () => withTemp(async (dir) => {
    const h = harness({ driver: fakeDriver({ failReady: true }) });
    assert.equal(await main(argvFor(dir, { mode: 'paid' }), ENV, h.deps), 1);
    assert.equal(h.driver.closed, 1);
    assert.ok(h.err.join('').includes('driver caiu'), h.err.join(''));
}));

test('seed que falha sai com 1, não abre driver e não fecha nada', () => withTemp(async (dir) => {
    const h = harness();
    h.deps.seed = async () => { throw new Error('seed quebrou'); };
    assert.equal(await main(argvFor(dir), ENV, h.deps), 1);
    assert.equal(h.spawns.length, 0);
    assert.ok(h.err.join('').includes('seed quebrou'), h.err.join(''));
}));

// Paid --------------------------------------------------------------------

const SUCCESS_REPLIES = [reply([VIOLATED, REPORT, ...RULES_2_TO_9, DONE], 0.0123)];

test('paid grava transcript com confirmação só nos turnos de confirmação, e o relatório com custo de 4 casas', () => withTemp(async (dir) => {
    const h = harness({ net: network({ replies: SUCCESS_REPLIES }) });
    assert.equal(await main(argvFor(dir, { mode: 'paid' }), ENV, h.deps), 0);
    const lines = (await readOut(dir, 'transcript.jsonl')).split('\n').filter(Boolean).map((line) => JSON.parse(line));
    assert.deepEqual(lines.map((line) => line.confirmation), [false, true]);
    assert.deepEqual(lines[1].toolCalls.map((item) => item.name), ['verdict']);
    const report = await readOut(dir, 'report.md');
    assert.match(report, /^- Custo: 0\.0579 USD$/m);
    assert.match(report, /^- Gasto da chave antes: 0\.2$/m);
    assert.match(report, /^- Gasto da chave depois: 0\.5$/m);
    assert.match(report, /^- Regra 1: violated \(captura 1\): texto$/m);
    assert.match(report, /^- Confirmação: confirmado$/m);
    assert.doesNotMatch(report, /Nenhum check registrado\./);
    assert.doesNotMatch(report, /Nenhum achado registrado\./);
    assert.equal(h.driver.closed, 1);
}));

test('"Nenhum check registrado." e "Nenhum achado registrado." aparecem só quando a lista está vazia', () => withTemp(async (dir) => {
    const refused = harness({ net: network({ keyBodies: [{ data: { limit: 0.1, limit_remaining: 0.05, usage: 0.08 } }] }) });
    assert.equal(await main(argvFor(dir, { mode: 'paid' }), ENV, refused.deps), 3);
    const refusedReport = await readOut(dir, 'report.md');
    assert.match(refusedReport, /^Nenhum check registrado\.$/m);
    assert.match(refusedReport, /^Nenhum achado registrado\.$/m);
    assert.match(refusedReport, /^- Passos: 0$/m);
    assert.match(refusedReport, /^- Custo: 0\.0000 USD$/m);

    const quiet = harness({ net: network({ replies: [reply([...ALL_OK, DONE])] }) });
    assert.equal(await main(argvFor(dir, { mode: 'paid' }), ENV, quiet.deps), 0);
    const quietReport = await readOut(dir, 'report.md');
    assert.doesNotMatch(quietReport, /Nenhum check registrado\./);
    assert.match(quietReport, /^Nenhum achado registrado\.$/m);
}));

test('a chave da OpenRouter não aparece em arquivo, stdout ou stderr, e não entra no ambiente do seed nem do driver', () => withTemp(async (dir) => {
    const h = harness({ net: network({ replies: SUCCESS_REPLIES }) });
    assert.equal(await main(argvFor(dir, { mode: 'paid' }), ENV, h.deps), 0);
    assert.equal(h.seeds[0].env.OPENROUTER_API_KEY, undefined);
    assert.equal(h.spawns[0].env.OPENROUTER_API_KEY, undefined);
    assert.equal('OPENROUTER_API_KEY' in h.seeds[0].env, false);
    assert.equal(h.seeds[0].env.PATH, '/bin');
    assert.equal(h.spawns[0].env.PATH, '/bin');
    assert.equal(JSON.stringify([h.seeds, h.spawns]).includes(KEY), false);
    assert.equal(h.out.join('').includes(KEY), false);
    assert.equal(h.err.join('').includes(KEY), false);
    const files = await filesUnder(join(dir, 'out'));
    assert.ok(files.length > 0);
    for (const file of files) assert.equal((await readFile(file)).includes(KEY), false, file);
}));

test('a cobertura do caso chega ao roteiro: done sem cobertura não vira done', () => withTemp(async (dir) => {
    const cases = { ...CASES, coverage: ['gear'], coverageLabels: { gear: 'engrenagem' } };
    const h = harness({ cases, net: network({ replies: [reply([...ALL_OK, DONE])] }) });
    assert.equal(await main(argvFor(dir, { mode: 'paid' }), ENV, h.deps), 4);
    const result = JSON.parse(await readOut(dir, 'result.json'));
    assert.equal(result.stop, 'done-incomplete');
    assert.deepEqual(result.missing, ['cover de gear']);
    assert.match(await readOut(dir, 'report.md'), /^- engrenagem: não coberto$/m);
}));

// Recusas: nada semeado, nada aberto -----------------------------------------

test('caso desconhecido, chave reservada, chave ausente e flag inválida saem com 2 sem semear nem abrir driver', () => withTemp(async (dir) => {
    const refusals = [
        [argvFor(dir, { caseId: 'nada' }), ENV, 'caso desconhecido: nada'],
        [argvFor(dir, { caseId: 'script' }), ENV, 'caso desconhecido: script'],
        [argvFor(dir, { caseId: 'coverage' }), ENV, 'caso desconhecido: coverage'],
        [argvFor(dir, { caseId: 'coverageLabels' }), ENV, 'caso desconhecido: coverageLabels'],
        [argvFor(dir, { mode: 'paid' }), { PATH: '/bin' }, 'OPENROUTER_API_KEY ausente'],
        [argvFor(dir, { mode: 'turbo' }), ENV, '--mode deve ser dry ou paid'],
        [[...argvFor(dir), '--extra', '1'], ENV, 'opção desconhecida: --extra'],
    ];
    for (const [argv, env, message] of refusals) {
        const h = harness();
        assert.equal(await main(argv, env, h.deps), 2, message);
        assert.equal(h.seeds.length, 0, message);
        assert.equal(h.spawns.length, 0, message);
        assert.equal(h.net.calls.length, 0, message);
        assert.ok(h.err.join('').includes(message), message);
    }
}));

// --plan-budget -----------------------------------------------------------

test('--plan-budget confere o pior caso contra o teto: cabe, não cabe e entrada inválida', async () => {
    const fits = harness();
    assert.equal(await main(['--plan-budget', '--jobs', '24'], {}, fits.deps), 0);
    assert.equal(fits.out.join(''), 'pior caso: 4.9104 USD de 5 USD\n');

    const over = harness();
    assert.equal(await main(['--plan-budget', '--jobs', '25'], {}, over.deps), 3);
    assert.equal(over.out.join(''), 'pior caso: 5.1104 USD de 5 USD\n');

    for (const argv of [['--plan-budget'], ['--plan-budget', '--jobs', '0'], ['--plan-budget', '--jobs', '1.5'], ['--plan-budget', '--jobs', 'x']]) {
        const bad = harness();
        assert.equal(await main(argv, {}, bad.deps), 2, argv.join(' '));
        assert.match(bad.err.join(''), /^uso: --plan-budget --jobs <inteiro maior que 0>$/m, argv.join(' '));
        assert.equal(bad.out.length, 0, argv.join(' '));
    }
});
