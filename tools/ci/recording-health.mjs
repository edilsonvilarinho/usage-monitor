#!/usr/bin/env node
// Julga uma pasta de gravação do app real (probe.json + app.log) e imprime o veredito.
// Códigos: ok 0, app-exited 1, app-error 1, no-data 6. Aviso de stall do EDT não reprova.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'

export const EXIT = { ok: 0, 'app-exited': 1, 'app-error': 1, 'no-data': 6 }

// Único erro de nível error aceito: consulta de atualização sem rede (texto do app).
const ALLOWED_ERROR_PREFIX = 'consultar atualização do app falhou: SocketException'
const MAX_ERROR_LINES = 5
const STALL_PATTERN = /^EDT sem responder há (\d+) ms$/

export function judgeRecording({ probeText, logText }) {
    const probe = parseProbe(probeText)
    if (probe === null) return verdict('no-data', ['motivo: probe.json ausente ou ilegível'])
    const entries = parseLog(logText)
    if (entries === null) return verdict('no-data', ['motivo: app.log ausente, vazio ou sem linha JSON'])
    const errorLines = entries.filter(isUnexpectedError).slice(0, MAX_ERROR_LINES).map(errorLine)
    const details = [...errorLines, ...entries.filter(isStall).map(stallLine)]
    if (probe.appExited) return verdict('app-exited', ['motivo: appExited=true', ...details])
    if (errorLines.length > 0) return verdict('app-error', details)
    return verdict('ok', details.filter((line) => line.startsWith('aviso ')))
}

function verdict(name, lines) {
    return { verdict: name, code: EXIT[name], lines }
}

function parseProbe(text) {
    const parsed = parseJson(text)
    if (parsed === null || typeof parsed.appExited !== 'boolean') return null
    return { appExited: parsed.appExited }
}

function parseLog(text) {
    if (text === null || text.trim() === '') return null
    const entries = text.split(/\r?\n/).map(parseJson).filter((entry) => entry !== null)
    return entries.length > 0 ? entries : null
}

function parseJson(text) {
    if (text === null) return null
    try {
        const value = JSON.parse(text)
        return typeof value === 'object' && value !== null ? value : null
    } catch {
        return null
    }
}

function isUnexpectedError(entry) {
    if (entry.level !== 'error') return false
    const allowed = entry.source === 'breadcrumb' && String(entry.message).startsWith(ALLOWED_ERROR_PREFIX)
    return !allowed
}

function isStall(entry) {
    return entry.source === 'edt-watchdog' && STALL_PATTERN.test(String(entry.message))
}

function errorLine(entry) {
    return `erro: ${entry.source}: ${entry.message}`
}

function stallLine(entry) {
    return `aviso edt-stall ${String(entry.message).match(STALL_PATTERN)[1]}`
}

function readOptional(path) {
    try {
        return readFileSync(path, 'utf8')
    } catch {
        return null
    }
}

export function main(argv) {
    const [folder] = argv
    if (!folder) {
        process.stderr.write('uso: recording-health.mjs <pasta-da-gravacao>\n')
        return 2
    }
    const result = judgeRecording({
        probeText: readOptional(join(folder, 'probe.json')),
        logText: readOptional(join(folder, 'app.log')),
    })
    process.stdout.write([result.verdict, ...result.lines].join('\n') + '\n')
    return result.code
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
    process.exitCode = main(process.argv.slice(2))
}
