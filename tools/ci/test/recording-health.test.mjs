import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { judgeRecording } from '../recording-health.mjs'

const SCRIPT = fileURLToPath(new URL('../recording-health.mjs', import.meta.url))
const dir = mkdtempSync(join(tmpdir(), 'recording-health-'))
after(() => rmSync(dir, { recursive: true, force: true }))

// Linhas reais de app.log (gravação source, attempt 25): o erro de atualização é o único nível error permitido.
const SLF4J = '{"ts":"2026-11-25T13:52:50.060Z","pid":4292,"version":"1.0.0","level":"warn","source":"stderr","thread":"AWT-EventQueue-0","message":"SLF4J(W): No SLF4J providers were found."}'
const SOCKET = '{"ts":"2026-11-25T13:52:51.240Z","pid":4292,"version":"1.0.0","level":"error","source":"breadcrumb","thread":"DefaultDispatcher-worker-6","message":"consultar atualização do app falhou: SocketException: Permission denied: getsockopt"}'
const STALL = (ms) => `{"ts":"2026-11-25T13:55:48.235Z","pid":2224,"version":"1.0.0","level":"warn","source":"edt-watchdog","thread":"AWT-EventQueue-0","message":"EDT sem responder há ${ms} ms","error":"EdtStallSnapshot: EDT parado em RUNNABLE"}`
const RECOVER = '{"ts":"2026-11-25T13:55:49.102Z","pid":2224,"version":"1.0.0","level":"warn","source":"edt-watchdog","thread":"AWT-EventQueue-0","message":"EDT voltou a responder após 6782 ms"}'
const error = (source, message) => JSON.stringify({ ts: '2026-11-25T13:55:00.000Z', pid: 1, version: '1.0.0', level: 'error', source, thread: 'T', message })
const log = (...lines) => `${lines.join('\n')}\n`
const probe = (appExited) => JSON.stringify({ scenario: 'floating-idle', appExited, framesPerSecond: 9.3 })

test('healthy recording is ok with exit 0 and no lines', () => {
    const r = judgeRecording({ probeText: probe(false), logText: log(SLF4J, SOCKET) })
    assert.deepEqual([r.verdict, r.code, r.lines], ['ok', 0, []])
})

test('appExited true is app-exited with exit 1 and a motivo line', () => {
    const r = judgeRecording({ probeText: probe(true), logText: log(SLF4J) })
    assert.deepEqual([r.verdict, r.code], ['app-exited', 1])
    assert.equal(r.lines[0], 'motivo: appExited=true')
})

test('an error outside the closed list is app-error with exit 1 and its message', () => {
    const r = judgeRecording({ probeText: probe(false), logText: log(SOCKET, error('stderr', 'boom')) })
    assert.deepEqual([r.verdict, r.code], ['app-error', 1])
    assert.deepEqual(r.lines, ['erro: stderr: boom'])
})

test('the update-check SocketException breadcrumb is allowed only with that exact prefix', () => {
    const other = error('breadcrumb', 'consultar atualização do app falhou: TimeoutException: x')
    const r = judgeRecording({ probeText: probe(false), logText: log(other) })
    assert.deepEqual([r.verdict, r.code], ['app-error', 1])
    assert.deepEqual(r.lines, [`erro: breadcrumb: ${JSON.parse(other).message}`])
})

test('the same SocketException message from another source is app-error', () => {
    const r = judgeRecording({ probeText: probe(false), logText: log(error('stderr', 'consultar atualização do app falhou: SocketException: y')) })
    assert.equal(r.verdict, 'app-error')
})

test('a warn line with an error-looking message is not an error', () => {
    const warn = SOCKET.replace('"level":"error"', '"level":"warn"')
    const r = judgeRecording({ probeText: probe(false), logText: log(warn) })
    assert.deepEqual([r.verdict, r.code], ['ok', 0])
})

test('at most five error messages are printed', () => {
    const errors = Array.from({ length: 7 }, (_, i) => error('stderr', `e${i + 1}`))
    const r = judgeRecording({ probeText: probe(false), logText: log(...errors) })
    assert.equal(r.verdict, 'app-error')
    assert.equal(r.lines.length, 5)
    assert.equal(r.lines[0], 'erro: stderr: e1')
})

test('EDT stalls are advisory lines with the measured milliseconds and do not fail', () => {
    const r = judgeRecording({ probeText: probe(false), logText: log(SLF4J, STALL(5912), RECOVER, STALL(1200)) })
    assert.deepEqual([r.verdict, r.code], ['ok', 0])
    assert.deepEqual(r.lines, ['aviso edt-stall 5912', 'aviso edt-stall 1200'])
})

test('stall lines follow the error messages', () => {
    const r = judgeRecording({ probeText: probe(false), logText: log(error('stderr', 'boom'), STALL(900)) })
    assert.deepEqual(r.lines, ['erro: stderr: boom', 'aviso edt-stall 900'])
})

test('missing probe is no-data with exit 6 even when app.log has errors', () => {
    const r = judgeRecording({ probeText: null, logText: log(error('stderr', 'boom')) })
    assert.deepEqual([r.verdict, r.code], ['no-data', 6])
    assert.ok(r.lines[0].startsWith('motivo: '))
})

test('missing app.log is no-data with exit 6', () => {
    const r = judgeRecording({ probeText: probe(false), logText: null })
    assert.deepEqual([r.verdict, r.code], ['no-data', 6])
})

test('empty or blank app.log is no-data', () => {
    assert.equal(judgeRecording({ probeText: probe(false), logText: '' }).verdict, 'no-data')
    assert.equal(judgeRecording({ probeText: probe(false), logText: '\n   \n' }).verdict, 'no-data')
})

test('an app.log whose lines are all unreadable is no-data', () => {
    const r = judgeRecording({ probeText: probe(false), logText: log('abc', 'def') })
    assert.equal(r.verdict, 'no-data')
})

test('probe that is not JSON or has no boolean appExited is no-data', () => {
    assert.equal(judgeRecording({ probeText: '{nao json', logText: log(SLF4J) }).verdict, 'no-data')
    assert.equal(judgeRecording({ probeText: '{"appExited":"yes"}', logText: log(SLF4J) }).verdict, 'no-data')
})

test('app-exited wins over app-error', () => {
    const r = judgeRecording({ probeText: probe(true), logText: log(error('stderr', 'boom')) })
    assert.equal(r.verdict, 'app-exited')
})

function recordingDir(name, probeText, logText) {
    const folder = join(dir, name)
    mkdirSync(folder, { recursive: true })
    if (probeText !== null) writeFileSync(join(folder, 'probe.json'), probeText)
    if (logText !== null) writeFileSync(join(folder, 'app.log'), logText)
    return folder
}

function runCli(args) {
    const result = spawnSync(process.execPath, [SCRIPT, ...args], { encoding: 'utf8' })
    return { status: result.status, lines: result.stdout.split(/\r?\n/).filter((line) => line !== '') }
}

test('CLI prints the verdict on the first line and exits with its code', () => {
    const ok = runCli([recordingDir('cli-ok', probe(false), log(SLF4J, SOCKET))])
    assert.deepEqual(ok, { status: 0, lines: ['ok'] })
    const exited = runCli([recordingDir('cli-exited', probe(true), log(SLF4J))])
    assert.equal(exited.status, 1)
    assert.equal(exited.lines[0], 'app-exited')
    const errored = runCli([recordingDir('cli-error', probe(false), log(error('stderr', 'boom')))])
    assert.equal(errored.status, 1)
    assert.equal(errored.lines[0], 'app-error')
})

test('CLI on a missing folder is no-data with exit 6', () => {
    const r = runCli([join(dir, 'nao-existe')])
    assert.equal(r.status, 6)
    assert.equal(r.lines[0], 'no-data')
})

test('CLI without a folder argument is usage with exit 2', () => {
    const r = runCli([])
    assert.equal(r.status, 2)
})
