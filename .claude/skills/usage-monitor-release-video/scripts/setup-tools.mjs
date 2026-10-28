#!/usr/bin/env node
// Instala, uma vez por máquina, o que o vídeo precisa e o repositório não versiona: Piper num venv,
// as duas vozes, o puppeteer-core e, para o tour, o Kokoro e o scipy da trilha. Fica fora de build/, que o `gradlew clean` apaga — foi assim
// que o pipeline da 41.11 se perdeu.
// Uso: node setup-tools.mjs   (idempotente: o que já existe não é baixado de novo)
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'

const VOICE_BASE = 'https://huggingface.co/rhasspy/piper-voices/resolve/main'
const VOICE_PATHS = ['en/en_US/ryan/high/en_US-ryan-high', 'pt/pt_BR/faber/medium/pt_BR-faber-medium']
const PUPPETEER = 'puppeteer-core@24'
// Kokoro 82M (tour em vídeo, #505): ~350 MB, só baixado quando o roteiro pede `voice.engine: "kokoro"`.
const KOKORO_BASE = 'https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0'
const KOKORO_FILES = ['kokoro-v1.0.onnx', 'voices-v1.0.bin']

export function toolPaths() {
    const base = process.env.LOCALAPPDATA
        ? join(process.env.LOCALAPPDATA, 'usage-monitor-video')
        : join(homedir(), '.cache', 'usage-monitor-video')
    const windows = process.platform === 'win32'
    return {
        base,
        python: windows ? join(base, 'venv', 'Scripts', 'python.exe') : join(base, 'venv', 'bin', 'python'),
        voices: join(base, 'voices'),
        nodeModules: join(base, 'node', 'node_modules'),
    }
}

export function chromePath() {
    const candidates = [
        process.env.CHROME_PATH,
        'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
        'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
        '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
        '/usr/bin/google-chrome',
        '/usr/bin/chromium',
    ].filter(Boolean)
    const found = candidates.find((candidate) => existsSync(candidate))
    if (!found) throw new Error('Chrome não encontrado: defina CHROME_PATH')
    return found
}

async function download(url, target) {
    const response = await fetch(url)
    if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`)
    writeFileSync(target, Buffer.from(await response.arrayBuffer()))
}

export async function setupTools({ kokoro = false } = {}) {
    const paths = toolPaths()
    mkdirSync(paths.voices, { recursive: true })
    if (!existsSync(paths.python)) {
        execFileSync(process.platform === 'win32' ? 'python' : 'python3', ['-m', 'venv', join(paths.base, 'venv')], {
            stdio: 'inherit',
        })
        execFileSync(paths.python, ['-m', 'pip', 'install', '-q', 'piper-tts'], { stdio: 'inherit' })
    }
    for (const voice of VOICE_PATHS) {
        const name = voice.split('/').pop()
        for (const suffix of ['.onnx', '.onnx.json']) {
            const target = join(paths.voices, `${name}${suffix}`)
            if (!existsSync(target)) await download(`${VOICE_BASE}/${voice}${suffix}`, target)
        }
    }
    if (kokoro) {
        try {
            execFileSync(paths.python, ['-c', 'import kokoro_onnx, scipy'], { stdio: 'ignore' })
        } catch {
            execFileSync(paths.python, ['-m', 'pip', 'install', '-q', 'kokoro-onnx', 'scipy'], { stdio: 'inherit' })
        }
        for (const file of KOKORO_FILES) {
            const target = join(paths.voices, file)
            if (!existsSync(target)) await download(`${KOKORO_BASE}/${file}`, target)
        }
    }
    if (!existsSync(join(paths.nodeModules, 'puppeteer-core'))) {
        const project = join(paths.base, 'node')
        mkdirSync(project, { recursive: true })
        if (!existsSync(join(project, 'package.json'))) writeFileSync(join(project, 'package.json'), '{"private":true}\n')
        execFileSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['install', '--silent', PUPPETEER], {
            cwd: project,
            stdio: 'inherit',
            shell: process.platform === 'win32',
        })
    }
    for (const tool of ['ffmpeg', 'ffprobe']) execFileSync(tool, ['-version'], { stdio: 'ignore' })
    chromePath()
    return paths
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    setupTools().then((paths) => process.stdout.write(`Ferramentas prontas em ${paths.base}\n`))
}
