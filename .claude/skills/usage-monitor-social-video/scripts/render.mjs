#!/usr/bin/env node
// Render final de uma variante do vídeo social (#542).
//   Vídeo:  node scripts/render.mjs --work <dir> --variant <v> --format 9:16 --out <arquivo.mp4> [--to <s>]
//   Áudio:  node scripts/render.mjs --work <dir> --variant <v> --audio-only <arquivo.m4a> [--to <s>]
// <dir>/<v>.js é o roteiro: WF_SCRIPT.duration dá a duração (nunca fixa); --to limita (teste).
// Mix: <dir>/<v>-voice.wav, -music.wav, -sfx.wav (os que existirem; ausente vira silêncio).
// Vídeo: assets/index.html?data=<dir>/<v>.js&format=<f>; espera window.__ready; window.renderAt(t) a cada quadro (30 fps);
// JPEG de page.screenshot pela entrada do ffmpeg; resolução cheia; H.264 yuv420p CRF 18; AAC 192 kbps.
// Trilha com sidechaincompress pela voz, efeitos, amix; loudnorm I=-14 TP=-1.5 em duas passagens (medição e linear).
// --out e --audio-only são relativos ao diretório atual.
import { spawn, execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, statSync } from 'node:fs'
import { once } from 'node:events'
import { dirname, join, resolve } from 'node:path'
import { parseArgs } from 'node:util'
import vm from 'node:vm'
import { FORMATS, isFatal, launch, openPage, pageUrl } from './browser.mjs'

const FPS = 30

const GAIN = { voice: 1, music: 0.6, sfx: 0.7 }
// TP -1.5 na normalização (não -1): o AAC 192k sobe o pico decodificado uns 0,4 dB; com -1 o arquivo ficava em -0,9 dBTP.
const TARGET = { I: -14, TP: -1.5, LRA: 11 }
const TRACKS = ['voice', 'music', 'sfx']
const FMT = 'aformat=sample_rates=48000:channel_layouts=stereo'
const LOUD = `loudnorm=I=${TARGET.I}:TP=${TARGET.TP}:LRA=${TARGET.LRA}`

const USAGE = 'uso:\n'
  + '  node scripts/render.mjs --work <dir> --variant <v> --format 16:9|9:16 --out <arquivo.mp4> [--to <s>]\n'
  + '  node scripts/render.mjs --work <dir> --variant <v> --audio-only <arquivo.m4a> [--to <s>]'

const { values } = parseArgs({
  options: {
    work: { type: 'string' },
    variant: { type: 'string' },
    format: { type: 'string' },
    out: { type: 'string' },
    'audio-only': { type: 'string' },
    to: { type: 'string' },
  },
})

function fail(message) {
  console.error(message)
  process.exit(2)
}

if (!values.work) fail(`uso: --work <dir>\n${USAGE}`)
const work = resolve(values.work)
if (!existsSync(work) || !statSync(work).isDirectory()) fail(`--work não é uma pasta: ${work}`)

const variant = values.variant ?? ''
if (!/^[a-z0-9][a-z0-9-]*$/.test(variant)) fail(`--variant <v> ausente ou inválido\n${USAGE}`)

let toLimit = null
if (values.to !== undefined) {
  toLimit = Number(values.to)
  if (!(Number.isFinite(toLimit) && toLimit > 0)) fail('--to pede segundos maiores que zero')
}

let mode
let outFile
if (values['audio-only'] !== undefined) {
  mode = 'audio'
  if (!/\.m4a$/i.test(values['audio-only'])) fail('--audio-only pede um arquivo .m4a')
  outFile = resolve(values['audio-only'])
} else {
  mode = 'video'
  if (!FORMATS[values.format]) fail(`--format 16:9|9:16\n${USAGE}`)
  if (!values.out) fail(`uso: --out <arquivo.mp4>\n${USAGE}`)
  if (!/\.mp4$/i.test(values.out)) fail('--out pede um arquivo .mp4')
  outFile = resolve(values.out)
}

const dataFile = join(work, `${variant}.js`)
if (!existsSync(dataFile)) fail(`roteiro ausente: ${dataFile}`)

// Lê <dir>/<variante>.js num contexto isolado (é script de navegador: define window.WF_SCRIPT).
function readVariantDuration(file) {
  const sandbox = { window: {} }
  vm.runInNewContext(readFileSync(file, 'utf8'), sandbox, { filename: file })
  const seconds = sandbox.window.WF_SCRIPT?.duration
  if (!(Number.isFinite(seconds) && seconds > 0)) fail(`${file}: WF_SCRIPT.duration inválida (${seconds})`)
  return seconds
}

function probeDuration(path) {
  const out = execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path], { encoding: 'utf8' })
  return Number(out.trim())
}

const variantDuration = readVariantDuration(dataFile)
const duration = toLimit === null ? variantDuration : Math.min(variantDuration, toLimit)

const present = {}
for (const track of TRACKS) {
  const path = join(work, `${variant}-${track}.wav`)
  if (existsSync(path)) present[track] = path
}
if (Object.keys(present).length === 0) {
  fail(`nenhum WAV em ${work} para ${variant}: esperado ${variant}-voice.wav, ${variant}-music.wav ou ${variant}-sfx.wav`)
}
for (const [track, path] of Object.entries(present)) {
  const d = probeDuration(path)
  if (Math.abs(d - variantDuration) > 0.05) {
    console.error(`aviso: ${track} dura ${d.toFixed(3)} s, a variante ${variantDuration} s; o mix corta ou completa com silêncio`)
  }
}

// Entradas na ordem voz, trilha, efeitos; `offset` é o índice da primeira (1 no vídeo, após os quadros).
// Ausente vira anullsrc com a mesma duração.
function buildMix(offset) {
  const inputs = []
  const index = {}
  TRACKS.forEach((track, i) => {
    index[track] = offset + i
    if (present[track]) inputs.push('-i', present[track])
    else inputs.push('-f', 'lavfi', '-i', `anullsrc=channel_layout=stereo:sample_rate=48000:duration=${duration}`)
  })
  const len = `apad,atrim=duration=${duration}`
  const chain = [
    `[${index.voice}:a]${FMT},volume=${GAIN.voice},${len},asplit=2[vsc][vmix]`,
    `[${index.music}:a]${FMT},volume=${GAIN.music},${len}[m0]`,
    '[m0][vsc]sidechaincompress=threshold=0.05:ratio=5:attack=20:release=500[md]',
    `[${index.sfx}:a]${FMT},volume=${GAIN.sfx},${len}[sfx]`,
    '[vmix][md][sfx]amix=inputs=3:duration=longest:normalize=0[mix]',
  ]
  return { inputs, chain: chain.join(';') }
}

function runFfmpeg(args) {
  return new Promise((resolveRun, reject) => {
    const child = spawn('ffmpeg', args, { stdio: ['ignore', 'ignore', 'pipe'] })
    let stderr = ''
    child.stderr.on('data', (chunk) => { stderr += chunk })
    child.on('error', reject)
    child.on('close', (code) => {
      if (code === 0) resolveRun(stderr)
      else reject(new Error(`ffmpeg saiu com código ${code}\n${stderr.slice(-2000)}`))
    })
  })
}

// O JSON do loudnorm vem no fim do log do ffmpeg; é o único objeto dele.
function parseLoudnorm(stderr) {
  const start = stderr.lastIndexOf('{')
  const end = stderr.lastIndexOf('}')
  if (start < 0 || end < start) throw new Error('loudnorm não devolveu medições')
  const m = JSON.parse(stderr.slice(start, end + 1))
  for (const key of ['input_i', 'input_tp', 'input_lra', 'input_thresh', 'target_offset']) {
    if (!Number.isFinite(Number(m[key]))) throw new Error(`loudnorm: ${key} ausente (${m[key]})`)
  }
  return m
}

// Passagem 1: medir o mix (mesma cadeia de voz, trilha e efeitos do render final).
async function measureMix() {
  const { inputs, chain } = buildMix(0)
  const args = [
    '-hide_banner', '-nostats', '-y', '-loglevel', 'info', ...inputs,
    '-filter_complex', `${chain};[mix]${LOUD}:print_format=json[out]`,
    '-map', '[out]', '-vn', '-t', String(duration), '-f', 'null', '-',
  ]
  const m = parseLoudnorm(await runFfmpeg(args))
  console.log(`medido: I=${m.input_i} LUFS, TP=${m.input_tp} dBTP, LRA=${m.input_lra}`)
  return m
}

// Passagem 2: ganho linear com as medições (não comprime a dinâmica).
function linearLoudnorm(m) {
  return `${LOUD}:measured_I=${m.input_i}:measured_TP=${m.input_tp}:measured_LRA=${m.input_lra}`
    + `:measured_thresh=${m.input_thresh}:offset=${m.target_offset}:linear=true`
}

async function encodeAudio(m) {
  const { inputs, chain } = buildMix(0)
  await runFfmpeg([
    '-hide_banner', '-nostats', '-y', '-loglevel', 'error', ...inputs,
    '-filter_complex', `${chain};[mix]${linearLoudnorm(m)}[out]`,
    '-map', '[out]', '-vn', '-c:a', 'aac', '-b:a', '192k', '-ar', '48000', '-ac', '2',
    '-t', String(duration), '-movflags', '+faststart', outFile,
  ])
}

async function renderVideo(m) {
  // Quadros: ceil para cobrir a duração inteira; o -t do ffmpeg corta no fim exato.
  const frames = Math.ceil(duration * FPS - 1e-6)
  const { inputs, chain } = buildMix(1)
  const format = FORMATS[values.format]
  const problems = []
  const browser = await launch()
  let encoder = null
  let finished = null
  try {
    const page = await openPage(browser, { width: format.w, height: format.h }, problems)
    await page.goto(pageUrl({ data: dataFile, format: values.format }), { waitUntil: 'load' })
    await page.waitForFunction('window.__ready === true || window.__error !== undefined', { timeout: 30000 })
    const error = await page.evaluate(() => window.__error)
    if (error) throw new Error(`carga: ${error}`)
    const hasRender = await page.evaluate(() => typeof window.renderAt === 'function')
    if (!hasRender) throw new Error('assets/index.html não expõe window.renderAt(t)')

    const args = [
      '-hide_banner', '-nostats', '-y', '-loglevel', 'error',
      '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-',
      ...inputs,
      '-filter_complex', `${chain};[mix]${linearLoudnorm(m)}[aout]`,
      '-map', '0:v', '-map', '[aout]',
      '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS),
      '-c:a', 'aac', '-b:a', '192k', '-ar', '48000', '-ac', '2',
      '-t', String(duration), '-movflags', '+faststart', outFile,
    ]
    encoder = spawn('ffmpeg', args, { stdio: ['pipe', 'ignore', 'pipe'] })
    let encoderLog = ''
    encoder.stderr.on('data', (chunk) => { encoderLog += chunk })
    encoder.stdin.on('error', () => {}) // a causa real sai pelo código do ffmpeg
    finished = new Promise((resolveDone, reject) => {
      encoder.on('error', reject)
      encoder.on('close', (code) => {
        if (code === 0) resolveDone()
        else reject(new Error(`ffmpeg saiu com código ${code}\n${encoderLog.slice(-2000)}`))
      })
    })
    finished.catch(() => {})

    for (let i = 0; i < frames; i += 1) {
      await page.evaluate((time) => window.renderAt(time), i / FPS)
      const jpg = await page.screenshot({ type: 'jpeg', quality: 92, encoding: 'binary' })
      if (!encoder.stdin.write(jpg)) await Promise.race([once(encoder.stdin, 'drain'), finished])
      if (i % (FPS * 10) === 0) console.log(`quadro ${i}/${frames}`)
    }
    encoder.stdin.end()
    await finished
    console.log(`ok: ${outFile} (${frames} quadros, ${duration} s, ${format.w}x${format.h}, faixas: ${Object.keys(present).join(', ')})`)
  } catch (err) {
    if (encoder) {
      encoder.stdin.destroy()
      encoder.kill()
    }
    throw err
  } finally {
    await browser.close()
    for (const p of problems) console.error(p)
    if (problems.some(isFatal)) process.exitCode = 1
  }
}

async function main() {
  mkdirSync(dirname(outFile), { recursive: true })
  const m = await measureMix()
  if (mode === 'audio') {
    await encodeAudio(m)
    console.log(`ok: ${outFile} (${duration} s, faixas: ${Object.keys(present).join(', ')})`)
  } else {
    await renderVideo(m)
  }
}

main().catch((err) => {
  console.error(err.message)
  process.exitCode = 1
})
