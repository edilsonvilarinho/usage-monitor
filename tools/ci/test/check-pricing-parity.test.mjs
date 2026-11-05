// Testa a guarda de paridade sobre copias dos tres arquivos em pasta temporaria.
// O script le caminhos relativos a raiz do repositorio, entao a copia reproduz
// os mesmos caminhos dentro de um diretorio novo, e cada caso muda um trecho
// existente do lado Kotlin (fonte da tabela).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '..', '..', '..');
const SCRIPT = resolve(HERE, '..', 'check-pricing-parity.mjs');

const KOTLIN_TABLE = 'src/commonMain/kotlin/com/usagemonitor/domain/entity/ModelPricingTable.kt';
const KOTLIN_PRICING = 'src/commonMain/kotlin/com/usagemonitor/domain/entity/ModelPricing.kt';
const SERVER_TABLE = 'server/src/domain/modelPricing.ts';

const FILES = [KOTLIN_TABLE, KOTLIN_PRICING, SERVER_TABLE];

// Cria uma pasta temporaria com a copia dos tres arquivos. `changes` troca um
// trecho por outro no arquivo indicado e falha se o trecho nao existir, para que
// um caso nunca passe por ter mutado o texto errado.
function scenario(changes = {}) {
    const dir = mkdtempSync(join(tmpdir(), 'pricing-parity-'));
    for (const file of FILES) {
        const target = join(dir, file);
        mkdirSync(dirname(target), { recursive: true });
        const original = readFileSync(join(REPO, file), 'utf8');
        let text = original;
        for (const [from, to] of changes[file] ?? []) {
            assert.ok(text.includes(from), `trecho ausente em ${file}: ${from}`);
            text = text.replace(from, to);
        }
        writeFileSync(target, text);
    }
    return dir;
}

function runScript(dir) {
    return spawnSync(process.execPath, [SCRIPT], { cwd: dir, encoding: 'utf8' });
}

test('copias identicas do repositorio passam', () => {
    const dir = scenario();
    try {
        const result = runScript(dir);
        assert.equal(result.status, 0, result.stderr || result.stdout);
        assert.match(result.stdout, /OK/);
    } finally {
        rmSync(dir, { recursive: true, force: true });
    }
});

test('divisor de cache read divergente sai 1', () => {
    // Primeira ocorrencia de `cacheReadDivisor = 20L` e o Opus 5.5 no Kotlin.
    const dir = scenario({
        [KOTLIN_TABLE]: [['cacheReadDivisor = 20L', 'cacheReadDivisor = 10L']]
    });
    try {
        const result = runScript(dir);
        assert.equal(result.status, 1);
        assert.match(result.stderr, /divisor/);
    } finally {
        rmSync(dir, { recursive: true, force: true });
    }
});

test('faixa de prompt longo divergente sai 1', () => {
    const dir = scenario({
        [KOTLIN_TABLE]: [['longPromptOutput = 2.50', 'longPromptOutput = 2.00']]
    });
    try {
        const result = runScript(dir);
        assert.equal(result.status, 1);
        assert.match(result.stderr, /faixa/);
    } finally {
        rmSync(dir, { recursive: true, force: true });
    }
});

test('faixa de prompt longo so de um lado sai 1', () => {
    const dir = scenario({
        [KOTLIN_TABLE]: [[', longPromptOutput = 2.50', '']]
    });
    try {
        const result = runScript(dir);
        assert.equal(result.status, 1);
        assert.match(result.stderr, /faixa/);
    } finally {
        rmSync(dir, { recursive: true, force: true });
    }
});

test('limite de prompt longo divergente sai 1', () => {
    const dir = scenario({
        [KOTLIN_PRICING]: [['LONG_PROMPT_THRESHOLD_TOKENS = 100_000L', 'LONG_PROMPT_THRESHOLD_TOKENS = 200_000L']]
    });
    try {
        const result = runScript(dir);
        assert.equal(result.status, 1);
        assert.match(result.stderr, /limite/);
    } finally {
        rmSync(dir, { recursive: true, force: true });
    }
});
