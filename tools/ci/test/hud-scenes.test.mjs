import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { sceneProblem } from '../hud-pulses.mjs'

const SCRIPT = fileURLToPath(new URL('../hud-pulses.mjs', import.meta.url))
const SCENES_FILE = fileURLToPath(new URL('../../real-app/scenes.json', import.meta.url))
const WORKFLOW = fileURLToPath(new URL('../../../.github/workflows/real-app.yml', import.meta.url))
const HEADER = 't_ms,foreground,x,y,w,h'
const dir = mkdtempSync(join(tmpdir(), 'hud-scenes-'))
after(() => rmSync(dir, { recursive: true, force: true }))

// Medidas por cena (x, y, w, h, foreground, startupMax), espelho de tools/real-app/scenes.json.
const MEASURED = {
    'floating-idle': [481, 139, 62, 42, 1418, 1427],
    'floating-drag': [481, 139, 62, 42, 1418, 1427],
    'docked-top-idle': [394, 0, 239, 12, 1376, 1377],
    'docked-top-hover': [394, 0, 239, 12, 1376, 1377],
    'docked-bottom-idle': [394, 708, 239, 12, 1376, 1378],
    'docked-left-idle': [0, 266, 12, 188, 1069, 1071],
    'docked-right-idle': [1012, 266, 12, 188, 1069, 1071],
    'docked-top-hole-idle': [335, 0, 357, 106, 28368, 30706],
    'docked-top-hole-hover': [335, 0, 357, 106, 28368, 30706],
}
const HOVER = { enterMs: 4000, leaveMs: 6000 }
const DRAG = { pressMs: 5000, releaseMs: 5500, dx: -200, dy: 120 }
const MODES = [
    { name: 'idle', scene: 'floating-idle', expect: 'default', events: undefined },
    { name: 'hover', scene: 'docked-top-hover', expect: 'hover', events: HOVER },
    { name: 'drag', scene: 'floating-drag', expect: 'drag', events: DRAG },
]
const HOVER_FOREGROUND = 3000

function sceneOf(name) {
    const [x, y, w, h, foreground, startupMax] = MEASURED[name]
    return { x, y, w, h, foreground, startupMax }
}

// Gravacao boa do modo: repouso; hover abre entre enterMs e leaveMs; drag desloca a caixa a partir de releaseMs.
function goodFrame(mode, tMs) {
    const reference = sceneOf(mode.scene)
    const opened = mode.expect === 'hover' && tMs >= HOVER.enterMs && tMs <= HOVER.leaveMs
    const moved = mode.expect === 'drag' && tMs >= DRAG.releaseMs
    return {
        tMs,
        foreground: opened ? HOVER_FOREGROUND : reference.foreground,
        x: reference.x + (moved ? DRAG.dx : 0),
        y: reference.y + (moved ? DRAG.dy : 0),
        w: reference.w,
        h: reference.h,
    }
}

// Quadros a cada 50 ms de 0 ate durationMs; change(quadro) devolve o quadro alterado.
function build(mode, { durationMs = 12000, change = (frame) => frame } = {}) {
    const frames = []
    for (let tMs = 0; tMs <= durationMs; tMs += 50) frames.push(change(goodFrame(mode, tMs)))
    return frames
}

const at = (tMs, patch) => (frame) => (frame.tMs === tMs ? { ...frame, ...patch } : frame)
const inWindow = (apply) => (frame) => (frame.tMs >= 2000 && frame.tMs <= 7000 ? apply(frame) : frame)
const both = (...changes) => (frame) => changes.reduce((current, change) => change(current), frame)

function problemOf(mode, frames) {
    const problem = sceneProblem(frames, { scene: mode.scene, expect: mode.expect, events: mode.events })
    return problem === null ? null : problem.verdict
}

function toCsv(frames) {
    const rows = frames.map((frame) => `${frame.tMs},${frame.foreground},${frame.x},${frame.y},${frame.w},${frame.h}`)
    return [HEADER, ...rows].join('\n') + '\n'
}

function writeCsv(name, frames) {
    const path = join(dir, name)
    writeFileSync(path, toCsv(frames))
    return path
}

function cli(...args) {
    try {
        const stdout = execFileSync(process.execPath, [SCRIPT, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
        return { code: 0, stdout }
    } catch (error) {
        return { code: error.status, stdout: error.stdout }
    }
}

test('scenes.json: exatamente os 9 cenarios de ALL= do real-app.yml, com os valores medidos', () => {
    const scenes = JSON.parse(readFileSync(SCENES_FILE, 'utf8'))
    const all = /ALL='([^']+)'/.exec(readFileSync(WORKFLOW, 'utf8'))[1].split(',')
    assert.equal(all.length, 9)
    assert.deepEqual(Object.keys(scenes).sort(), [...all].sort())
    assert.deepEqual(Object.keys(MEASURED).sort(), [...all].sort())
    for (const [name, [x, y, w, h, foreground, startupMax]] of Object.entries(MEASURED)) {
        assert.deepEqual(scenes[name], { rest: { x, y, w, h, foreground }, startupMax }, name)
    }
})

test('gravacao dentro da cena: null nos tres modos', () => {
    for (const mode of MODES) assert.equal(problemOf(mode, build(mode)), null, mode.name)
})

test('t0 e o 1o quadro com foreground >= 400: gravacao que aparece em 1 s passa', () => {
    const idle = MODES[0]
    assert.equal(problemOf(idle, build(idle, { change: (frame) => (frame.tMs < 1000 ? { ...frame, foreground: 0 } : frame) })), null)
})

test('low-rate por contagem: 149 quadros depois de t0 falha, 150 passa (nos tres modos)', () => {
    for (const mode of MODES) {
        assert.equal(problemOf(mode, build(mode, { durationMs: 7450 })), 'low-rate', `${mode.name} 149`)
        assert.notEqual(problemOf(mode, build(mode, { durationMs: 7500 })), 'low-rate', `${mode.name} 150`)
    }
})

test('low-rate por intervalo: 1000 ms passa, 1050 ms falha (nos tres modos)', () => {
    for (const mode of MODES) {
        const atLimit = build(mode).filter((frame) => !(frame.tMs > 5000 && frame.tMs < 6000))
        assert.notEqual(problemOf(mode, atLimit), 'low-rate', `${mode.name} 1000`)
        const overLimit = build(mode).filter((frame) => !(frame.tMs > 5000 && frame.tMs < 6050))
        assert.equal(problemOf(mode, overLimit), 'low-rate', `${mode.name} 1050`)
    }
})

test('vanished: foreground 399 depois de t0 falha, 400 nao (nos tres modos)', () => {
    for (const mode of MODES) {
        assert.equal(problemOf(mode, build(mode, { change: at(3000, { foreground: 399 }) })), 'vanished', mode.name)
        assert.notEqual(problemOf(mode, build(mode, { change: at(3000, { foreground: 400 }) })), 'vanished', `${mode.name} limite`)
    }
})

test('startup-pulse: acima de startupMax x 1,05 em t0..t0+2 s falha, no limite passa (nos tres modos)', () => {
    for (const mode of MODES) {
        const limit = Math.floor(sceneOf(mode.scene).startupMax * 1.05)
        assert.equal(problemOf(mode, build(mode, { change: at(500, { foreground: limit + 1 }) })), 'startup-pulse', mode.name)
        assert.notEqual(problemOf(mode, build(mode, { change: at(500, { foreground: limit }) })), 'startup-pulse', `${mode.name} limite`)
    }
})

test('wrong-box: mediana de x, y, w ou h 3 px fora falha, 2 px passa (nos tres modos)', () => {
    for (const mode of MODES) {
        for (const field of ['x', 'y', 'w', 'h']) {
            const shifted = (px) => build(mode, { change: inWindow((frame) => ({ ...frame, [field]: frame[field] + px })) })
            assert.equal(problemOf(mode, shifted(3)), 'wrong-box', `${mode.name} ${field} +3`)
            assert.notEqual(problemOf(mode, shifted(2)), 'wrong-box', `${mode.name} ${field} +2`)
        }
    }
})

test('wrong-size: mediana do foreground 2% fora falha, no limite passa (nos tres modos)', () => {
    for (const mode of MODES) {
        const limit = Math.floor(sceneOf(mode.scene).foreground * 1.02)
        assert.equal(problemOf(mode, build(mode, { change: inWindow((frame) => ({ ...frame, foreground: limit + 1 })) })), 'wrong-size', mode.name)
        assert.notEqual(problemOf(mode, build(mode, { change: inWindow((frame) => ({ ...frame, foreground: limit })) })), 'wrong-size', `${mode.name} limite`)
    }
})

test('unstable: um quadro parado 2% fora falha, no limite passa (nos tres modos)', () => {
    for (const mode of MODES) {
        const limit = Math.floor(sceneOf(mode.scene).foreground * 1.02)
        assert.equal(problemOf(mode, build(mode, { change: at(3000, { foreground: limit + 1 }) })), 'unstable', mode.name)
        assert.notEqual(problemOf(mode, build(mode, { change: at(3000, { foreground: limit }) })), 'unstable', `${mode.name} limite`)
    }
})

test('unstable: um quadro parado com x, y, w ou h 3 px fora falha, 2 px passa (nos tres modos)', () => {
    for (const mode of MODES) {
        for (const field of ['x', 'y', 'w', 'h']) {
            const reference = sceneOf(mode.scene)
            assert.equal(problemOf(mode, build(mode, { change: at(3000, { [field]: reference[field] + 3 }) })), 'unstable', `${mode.name} ${field} +3`)
            assert.notEqual(problemOf(mode, build(mode, { change: at(3000, { [field]: reference[field] + 2 }) })), 'unstable', `${mode.name} ${field} +2`)
        }
    }
})

test('hover: so conta parado; 6,5 s a 8,5 s apos sair nao e parado, depois de leaveMs + 2,5 s e', () => {
    const hover = MODES[1]
    assert.notEqual(problemOf(hover, build(hover, { change: at(7000, { foreground: 5000 }) })), 'unstable')
    assert.equal(problemOf(hover, build(hover, { change: at(9000, { foreground: 5000 }) })), 'unstable')
})

test('drag: pouso tolera 8 px em x e y, nao 9 px; w/h 2 px; caixa que nao andou falha', () => {
    const drag = MODES[2]
    const reference = sceneOf(drag.scene)
    assert.notEqual(problemOf(drag, build(drag, { change: at(10000, { x: reference.x + DRAG.dx + 8 }) })), 'unstable', 'x +8')
    assert.equal(problemOf(drag, build(drag, { change: at(10000, { x: reference.x + DRAG.dx + 9 }) })), 'unstable', 'x +9')
    assert.equal(problemOf(drag, build(drag, { change: at(10000, { y: reference.y + DRAG.dy - 9 }) })), 'unstable', 'y -9')
    assert.equal(problemOf(drag, build(drag, { change: at(10000, { w: reference.w + 3 }) })), 'unstable', 'w +3')
    const notMoved = (frame) => (frame.tMs >= 9500 ? { ...frame, x: reference.x, y: reference.y } : frame)
    assert.equal(problemOf(drag, build(drag, { change: notMoved })), 'unstable', 'caixa parada')
})

test('drag: entre soltar e soltar + 4 s o quadro nao e checado', () => {
    const drag = MODES[2]
    const reference = sceneOf(drag.scene)
    const notYetSettled = (frame) => (frame.tMs > 5500 && frame.tMs < 9500 ? { ...frame, x: reference.x, y: reference.y } : frame)
    assert.equal(problemOf(drag, build(drag, { change: notYetSettled })), null)
})

test('precedencia: low-rate > vanished > startup-pulse > wrong-box > wrong-size > unstable', () => {
    const idle = MODES[0]
    const reference = sceneOf(idle.scene)
    const vanishedFrame = at(3000, { foreground: 0 })
    const startupFrame = at(500, { foreground: reference.startupMax + 100 })
    const boxFrame = inWindow((frame) => ({ ...frame, x: frame.x + 3 }))
    const sizeFrame = inWindow((frame) => ({ ...frame, foreground: reference.foreground + 30 }))
    assert.equal(problemOf(idle, build(idle, { durationMs: 7450, change: vanishedFrame })), 'low-rate')
    assert.equal(problemOf(idle, build(idle, { change: both(vanishedFrame, startupFrame) })), 'vanished')
    assert.equal(problemOf(idle, build(idle, { change: both(startupFrame, boxFrame) })), 'startup-pulse')
    assert.equal(problemOf(idle, build(idle, { change: both(boxFrame, sizeFrame) })), 'wrong-box')
    assert.equal(problemOf(idle, build(idle, { change: both(sizeFrame, at(3000, { foreground: reference.foreground + 30 })) })), 'wrong-size')
})

test('motivo: traz o quadro medido e o esperado', () => {
    const idle = MODES[0]
    const problem = sceneProblem(build(idle, { change: at(3000, { foreground: 0 }) }), { scene: idle.scene, expect: idle.expect })
    assert.equal(problem.verdict, 'vanished')
    assert.match(problem.motivo, /t=3000 ms com foreground 0 /)
})

test('CLI --scene: ok com a cena (codigo 0); wrong-size e low-rate com linha motivo', () => {
    const idle = MODES[0]
    const good = cli(writeCsv('scene-good.csv', build(idle)), '--scene', 'floating-idle')
    assert.equal(good.code, 0)
    assert.equal(good.stdout, 'ok\n')
    const size = cli(writeCsv('scene-size.csv', build(idle, { change: inWindow((frame) => ({ ...frame, foreground: 1461 })) })), '--scene', 'floating-idle')
    assert.equal(size.code, 1)
    assert.equal(size.stdout.split('\n')[0], 'wrong-size')
    assert.match(size.stdout.split('\n')[1], /^motivo: /)
    const rate = cli(writeCsv('scene-rate.csv', build(idle, { durationMs: 7450 })), '--scene', 'floating-idle')
    assert.equal(rate.code, 6)
    assert.equal(rate.stdout.split('\n')[0], 'low-rate')
})
