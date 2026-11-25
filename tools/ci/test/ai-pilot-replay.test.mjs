// Replay dos laços de zoom repetido do piloto (#676, item f e extra): fixtures só com nomes e argumentos; fakes determinísticos.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createBudget, runScript } from '../ai-pilot.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
// Tela dos runs pagos que geraram os laços; a barra flutuante tem 430 px de altura.
const SCREEN = Object.freeze({ width: 1024, height: 768 });
const IMAGE = 'AAAA';
const DEFAULT_USAGE = { cost: 0.001, prompt_tokens: 10, completion_tokens: 5 };
const REPEAT_REFUSAL = 'erro: ação repetida; faça outra coisa';
// Quantidade de chamadas, depois do início do laço, em que a recusa tem de cortar a execução.
const STUCK_WINDOW = 20;

const loadFixture = (name) => JSON.parse(readFileSync(resolve(HERE, 'fixtures', name), 'utf8'));

// Primeiro turno que repete o turno seguinte: início do laço, em número de chamadas ao modelo.
function loopStartOf(turns) {
    const key = (turn) => JSON.stringify(turn.toolCalls);
    for (let index = 0; index + 1 < turns.length; index += 1) {
        if (turns[index].toolCalls.length > 0 && key(turns[index]) === key(turns[index + 1])) return index;
    }
    return null;
}

// Cada turno do fixture vira uma resposta do modelo; depois do último, o modelo não chama ferramenta.
function replyOf(turn, index) {
    if (!turn) return { message: { content: null, tool_calls: [] }, usage: DEFAULT_USAGE };
    return {
        message: {
            content: null,
            tool_calls: turn.toolCalls.map((item, position) => ({
                id: `${index}-${position}`,
                type: 'function',
                function: { name: item.name, arguments: JSON.stringify(item.arguments) },
            })),
        },
        usage: DEFAULT_USAGE,
    };
}

// Roda o roteiro do fixture e devolve o resultado e cada requisição enviada ao modelo.
async function replay(fixture) {
    const requests = [];
    const client = {
        async complete(request) {
            requests.push(structuredClone(request));
            return replyOf(fixture.turns[requests.length - 1], requests.length - 1);
        },
    };
    const driver = {
        async screenshot() {
            return IMAGE;
        },
        async zoom() {
            return IMAGE;
        },
        async perform() {},
    };
    const result = await runScript({
        client,
        driver,
        script: 'roteiro de replay',
        rules: 'regras do piloto',
        screen: SCREEN,
        budget: createBudget(),
    });
    return { result, requests };
}

const sawRepeatRefusal = (requests) =>
    requests.some((request) => request.messages.some((message) => message.role === 'tool' && message.content === REPEAT_REFUSAL));

for (const name of ['pilot-loop.json', 'pilot-loop-bad-3.json']) {
    test(`replay ${name}: laço de zoom repetido vira stuck em até ${STUCK_WINDOW} chamadas do início do laço`, async () => {
        const fixture = loadFixture(name);
        const loopStart = loopStartOf(fixture.turns);
        assert.notEqual(loopStart, null, 'fixture sem laço');
        const { result, requests } = await replay(fixture);
        assert.equal(result.stop, 'stuck');
        assert.ok(requests.length <= loopStart + STUCK_WINDOW, `chamadas: ${requests.length}, início do laço: ${loopStart}`);
    });
}

for (const name of ['pilot-loop-good-1.json', 'pilot-loop-good-2.json']) {
    test(`replay ${name}: roteiro legítimo não é recusado pela guarda de repetição`, async () => {
        const { result, requests } = await replay(loadFixture(name));
        assert.notEqual(result.stop, 'stuck');
        assert.equal(sawRepeatRefusal(requests), false);
    });
}
