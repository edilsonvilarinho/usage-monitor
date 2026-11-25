// Replay do loop de zoom repetido de gear-balloon-bad-1 (#676, item f). Fixture pequeno, sem imagens; fakes determinísticos.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createBudget, runScript } from '../ai-pilot.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const FIXTURE = JSON.parse(readFileSync(resolve(HERE, 'fixtures', 'pilot-loop.json'), 'utf8'));
const IMAGE = 'AAAA';
const DEFAULT_USAGE = { cost: 0.001, prompt_tokens: 10, completion_tokens: 5 };

// Cada turno do fixture vira uma resposta do modelo, com ids estáveis.
const replies = FIXTURE.turns.map((turn, index) => ({
    message: {
        content: null,
        tool_calls: turn.toolCalls.map((item, position) => ({
            id: `${index}-${position}`,
            type: 'function',
            function: { name: item.name, arguments: JSON.stringify(item.arguments) },
        })),
    },
    usage: DEFAULT_USAGE,
}));

test('replay: zoom repetido de gear-balloon-bad-1 termina em stuck em menos de 40 chamadas', async () => {
    const calls = [];
    const client = {
        async complete(request) {
            calls.push(structuredClone(request));
            return replies[Math.min(calls.length - 1, replies.length - 1)];
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
        screen: FIXTURE.screen,
        budget: createBudget(),
    });
    assert.equal(result.stop, 'stuck');
    assert.ok(calls.length < 40, `chamadas: ${calls.length}`);
});
