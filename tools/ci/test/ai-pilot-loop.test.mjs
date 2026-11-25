// Limites do loop do piloto (#676, itens a, b e c): repetição, teto de ações, espera e código de saída. Fakes determinísticos: sem rede, sem disco.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { LIMITS, createBudget, runScript } from '../ai-pilot.mjs';
import { exitCodeOf } from '../ai-pilot-run.mjs';

const SCREEN = { width: 1920, height: 1080 };
const RULES = 'regras do piloto';
const SCRIPT = 'roteiro de teste';
const IMAGE = 'AAAA';
const DEFAULT_USAGE = { cost: 0.001, prompt_tokens: 10, completion_tokens: 5 };
const REPEAT_TEXT = 'erro: ação repetida; faça outra coisa';
const TURN_TEXT = 'ignorado: limite por turno';

const reply = (toolCalls, usage = DEFAULT_USAGE) => ({ message: { content: null, tool_calls: toolCalls }, usage });
const call = (id, name, args) => ({ id, type: 'function', function: { name, arguments: JSON.stringify(args) } });
const moveA = (id) => call(id, 'move', { x: 10, y: 10 });
const moveB = (id) => call(id, 'move', { x: 20, y: 20 });
const zoomR = (id) => call(id, 'zoom', { x: 100, y: 100, w: 200, h: 200 });
const doneCall = (id) => call(id, 'done', { summary: 'fim' });

// Respostas na ordem; repete a última quando acabam.
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

// Respostas em ciclo, para ações alternadas que nunca acabam.
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
    const zooms = [];
    return {
        performed,
        zooms,
        async screenshot() {
            return IMAGE;
        },
        async zoom(action) {
            zooms.push(action);
            return IMAGE;
        },
        async perform(action) {
            performed.push(action);
        },
    };
}

async function run(client, driver = fakeDriver(), coverage = []) {
    return runScript({ client, driver, script: SCRIPT, rules: RULES, screen: SCREEN, budget: createBudget(), coverage });
}

// Texto da resposta da ferramenta enviada no fim do turno indicado (0 = primeiro turno).
const lastToolText = (client, turn) => client.calls[turn].messages.at(-1).content;

test('ação repetida: a 3ª chamada seguida idêntica é recusada', async () => {
    const client = fakeClient([reply([moveA('1')]), reply([moveA('2')]), reply([moveA('3')]), reply([moveA('4')])]);
    const driver = fakeDriver();
    await run(client, driver);
    assert.equal(lastToolText(client, 3), REPEAT_TEXT);
    assert.equal(driver.performed.length, 2);
});

test('ação repetida: a sexta recusa por repetição encerra o roteiro com stuck', async () => {
    const result = await run(fakeClient([reply([moveA('m')])]));
    assert.equal(result.stop, 'stuck');
});

test('zoom do mesmo retângulo sem ação do ponteiro é recusado já na 2ª', async () => {
    const client = fakeClient([reply([zoomR('z1')]), reply([zoomR('z2')]), reply([doneCall('d')])]);
    const driver = fakeDriver();
    await run(client, driver);
    assert.equal(driver.zooms.length, 1);
});

test('zoom do mesmo retângulo depois de ação do ponteiro é aceito', async () => {
    const client = fakeClient([reply([zoomR('z1')]), reply([moveA('m')]), reply([zoomR('z2')]), reply([doneCall('d')])]);
    const driver = fakeDriver();
    await run(client, driver);
    assert.equal(driver.zooms.length, 2);
});

test('limite por turno: a 21ª chamada do turno recebe "ignorado: limite por turno"', async () => {
    const turn = Array.from({ length: 21 }, (_, index) => (index % 2 === 0 ? moveA(`t${index}`) : moveB(`t${index}`)));
    const client = fakeClient([reply(turn), reply([doneCall('d')])]);
    const driver = fakeDriver();
    await run(client, driver);
    assert.equal(lastToolText(client, 1), TURN_TEXT);
    assert.equal(driver.performed.length, 20);
});

test('teto de ações: 200 chamadas num turno terminam em max-actions com no máximo 20 execuções', async () => {
    const turn = Array.from({ length: 200 }, (_, index) => (index % 2 === 0 ? moveA(`t${index}`) : moveB(`t${index}`)));
    const driver = fakeDriver();
    const result = await run(cycleClient([reply(turn)]), driver);
    assert.equal(result.stop, 'max-actions');
    assert.ok(driver.performed.length <= 20, `execuções: ${driver.performed.length}`);
});

test('espera: o roteiro soma no máximo 120000 ms e recusa a espera que estoura', async () => {
    const waits = (id, first, second) => [call(`${id}a`, 'wait', { ms: first }), call(`${id}b`, 'wait', { ms: second })];
    const turns = Array.from({ length: 13 }, (_, index) => reply(waits(`w${index}`, 5000, 4000)));
    const client = fakeClient([...turns, reply([call('last', 'wait', { ms: 5000 })]), reply([doneCall('d')])]);
    const driver = fakeDriver();
    await run(client, driver);
    const total = driver.performed.reduce((sum, action) => sum + action.ms, 0);
    assert.equal(total, 117000);
});

test('exitCodeOf: tabela inteira; só done sai com 0', () => {
    const table = {
        done: 0,
        'client-error': 1,
        'driver-error': 1,
        'key-refused': 3,
        'script-budget': 3,
        'pilot-budget': 3,
        'prompt-limit': 3,
        'cost-unknown': 3,
        'done-incomplete': 4,
        'max-steps': 4,
        'max-actions': 4,
        'no-tool-call': 4,
        stuck: 4,
    };
    for (const [stop, code] of Object.entries(table)) {
        assert.equal(exitCodeOf(stop), code, `stop ${stop}`);
    }
});

test('LIMITS: teto de ações, por turno, de repetição e de espera', () => {
    assert.equal(LIMITS.maxActions, 150);
    assert.equal(LIMITS.maxActionsPerTurn, 20);
    assert.equal(LIMITS.maxRepeatRefusals, 6);
    assert.equal(LIMITS.maxTotalWaitMs, 120000);
});
