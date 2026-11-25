import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { plantEdit } from '../../real-app/plant-defect.mjs'

const SCRIPT = fileURLToPath(new URL('../../real-app/plant-defect.mjs', import.meta.url))
const DEFECTS_PATH = fileURLToPath(new URL('../../real-app/defects.json', import.meta.url))
const WORKFLOW_PATH = fileURLToPath(new URL('../../../.github/workflows/real-app.yml', import.meta.url))
const DEFECTS = JSON.parse(readFileSync(DEFECTS_PATH, 'utf8'))
const DEFECT_NAMES = [
    'drag-not-kept',
    'drag-half-way',
    'hover-stays-open',
    'hover-opens-late',
    'hole-ignores-hover',
    'hole-opens-alone',
    'opens-and-stays',
    'opens-late-and-stays',
    'prewarm-visible',
]
// Veredictos que o detector imprime (hud-pulses.mjs); defeito com veredito fora disso nunca dispara.
const DETECTOR_VERDICTS = [
    'ok',
    'pulsed',
    'open-at-rest',
    'not-visible',
    'not-opened',
    'opened-late',
    'not-closed',
    'extra-pulse',
    'not-moved',
    'wrong-place',
    'wrong-scene',
    'flash',
]
const REPO_ROOT = fileURLToPath(new URL('../../../', import.meta.url))
const SOURCE_DIR = 'src/desktopMain/kotlin/com/usagemonitor'
const WINDOW_FILE = `${SOURCE_DIR}/HudWindow.kt`
const BALLOON_FILE = `${SOURCE_DIR}/presentation/ui/HudNotchBalloonState.kt`
const roots = []

after(() => {
    for (const root of roots) rmSync(root, { recursive: true, force: true })
})

function tempRoot() {
    const root = mkdtempSync(join(tmpdir(), 'plant-defect-'))
    roots.push(root)
    return root
}

function run(args, { env = process.env, cwd = process.cwd() } = {}) {
    const result = spawnSync(process.execPath, [SCRIPT, ...args], { encoding: 'utf8', env: { ...env }, cwd })
    return { code: result.status, stdout: result.stdout ?? '' }
}

function writeFixture(root, relativePath, text) {
    const path = join(root, relativePath)
    mkdirSync(dirname(path), { recursive: true })
    writeFileSync(path, text)
    return path
}

function workflowScenarios() {
    const match = /ALL='([^']+)'/.exec(readFileSync(WORKFLOW_PATH, 'utf8'))
    assert.ok(match, 'ALL= nao encontrado em real-app.yml')
    return match[1].split(',')
}

test('plantEdit: uma ocorrencia substitui e devolve o texto inteiro', () => {
    assert.equal(plantEdit('a = 1\nb = 2\n', 'a = 1', 'a = 9'), 'a = 9\nb = 2\n')
})

test('plantEdit: zero ocorrencias lanca erro', () => {
    assert.throws(() => plantEdit('a = 1', 'z = 1', 'z = 2'))
})

test('plantEdit: duas ocorrencias lanca erro e nao escolhe a primeira', () => {
    assert.throws(() => plantEdit('x\nx', 'x', 'y'))
})

test('plantEdit: arquivo CRLF casa find LF e preserva o fim de linha', () => {
    assert.equal(plantEdit('a\r\nb\r\nc\r\n', 'a\nb', 'x\ny'), 'x\r\ny\r\nc\r\n')
})

test('plantEdit: replace com $& e $$ e literal, sem expansao de padrao', () => {
    assert.equal(plantEdit('val = 1', 'val = 1', 'val = $& + $1 + $$'), 'val = $& + $1 + $$')
})

test('CLI: sem --root e sem GITHUB_ACTIONS=true recusa gravar (exit 2)', () => {
    const env = { ...process.env }
    delete env.GITHUB_ACTIONS
    const result = run(['--defect', 'hover-stays-open'], { env, cwd: tempRoot() })
    assert.equal(result.code, 2)
})

test('CLI: defeito desconhecido sai com 2', () => {
    const result = run(['--defect', 'nao-existe', '--root', tempRoot()])
    assert.equal(result.code, 2)
})

test('CLI: aplica a edicao e imprime o arquivo alterado (exit 0)', () => {
    const root = tempRoot()
    const windowPath = writeFixture(root, WINDOW_FILE, 'val x = 1\n            delay(hudOpenDelayMillis(now))\n')
    const result = run(['--defect', 'hover-opens-late', '--root', root])
    assert.equal(result.code, 0)
    assert.ok(result.stdout.includes('HudWindow.kt'), result.stdout)
    assert.equal(readFileSync(windowPath, 'utf8'), 'val x = 1\n            delay(2_000L + hudOpenDelayMillis(now))\n')
})

test('CLI: segunda edicao invalida nao grava a primeira (exit 2)', () => {
    const root = tempRoot()
    const original = '    LaunchedEffect(hovered, drag.dragging) {\n'
    const windowPath = writeFixture(root, WINDOW_FILE, original)
    writeFixture(root, BALLOON_FILE, 'sem o trecho alvo\n')
    const result = run(['--defect', 'hole-opens-alone', '--root', root])
    assert.equal(result.code, 2)
    assert.equal(readFileSync(windowPath, 'utf8'), original)
})

test('CLI: --expect devolve o veredito do defeito para o cenario', () => {
    const result = run(['--defect', 'hover-stays-open', '--expect', 'docked-top-hover'])
    assert.equal(result.code, 0)
    assert.equal(result.stdout, 'not-closed\n')
})

test('CLI: --expect de cenario fora do defeito imprime linha vazia e sai 0', () => {
    const result = run(['--defect', 'hover-stays-open', '--expect', 'floating-idle'])
    assert.equal(result.code, 0)
    assert.equal(result.stdout, '\n')
})

test('defects.json: os defeitos existem, com edits, expect, cenarios em ALL= e veredictos do detector', () => {
    assert.deepEqual(Object.keys(DEFECTS).sort(), [...DEFECT_NAMES].sort())
    const scenarios = workflowScenarios()
    for (const name of DEFECT_NAMES) {
        const defect = DEFECTS[name]
        assert.ok(Array.isArray(defect.edits) && defect.edits.length > 0, `${name}: edits vazio`)
        for (const edit of defect.edits) {
            assert.equal(typeof edit.file, 'string', `${name}: file`)
            assert.equal(typeof edit.find, 'string', `${name}: find`)
            assert.equal(typeof edit.replace, 'string', `${name}: replace`)
        }
        const entries = Object.entries(defect.expect ?? {})
        assert.ok(entries.length > 0, `${name}: expect vazio`)
        for (const [scenario, verdict] of entries) {
            assert.ok(scenarios.includes(scenario), `${name}: cenario ${scenario} fora de ALL=`)
            assert.ok(DETECTOR_VERDICTS.includes(verdict), `${name}: veredito ${verdict} nao e do detector`)
        }
    }
})

test('defects.json: cada find dos defeitos ocorre exatamente uma vez no codigo-fonte real', () => {
    for (const name of DEFECT_NAMES) {
        assert.ok(DEFECTS[name], `${name}: ausente do defects.json`)
        for (const edit of DEFECTS[name].edits) {
            const text = readFileSync(join(REPO_ROOT, edit.file), 'utf8')
            assert.doesNotThrow(() => plantEdit(text, edit.find, edit.replace), `${name}: ${edit.file}`)
        }
    }
})

test('defects.json: prewarm-visible espera flash nos cenarios de ALL=', () => {
    assert.ok(DEFECTS['prewarm-visible'], 'prewarm-visible ausente do defects.json')
    assert.deepEqual(DEFECTS['prewarm-visible'].expect, Object.fromEntries(workflowScenarios().map((scenario) => [scenario, 'flash'])))
})
