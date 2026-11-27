#!/usr/bin/env node
// Orquestracao do video social (#542, B04).
//   node scripts/make.mjs check   <video.json>
//   node scripts/make.mjs preview <video.json> [--only <variante>]
//   node scripts/make.mjs voice   <video.json> [--only <variante>]     exige OPENROUTER_API_KEY
//   node scripts/make.mjs render  <video.json> [--only <variante>] [--to <s>] [--silent]
// Pasta de trabalho: <raiz do repo>/build/social-video/<slug>/. Saida: ~/Videos/usage-monitor/<slug>/.
// --to grava em <work>/smoke/ (teste). --silent gera faixa de voz muda quando <v>-voice.wav falta (teste).
import { execFileSync, spawn } from 'node:child_process'
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { basename, dirname, join, relative, resolve, sep } from 'node:path'
import { parseArgs } from 'node:util'
import { fileURLToPath, pathToFileURL } from 'node:url'
import vm from 'node:vm'
import { VARIANTS, outputNames, toSpoken, validateSpec } from './spec.mjs'
import { buildScript, toSrt } from './timeline.mjs'
import { estimateDuration, segmentName, synthesize } from './voice.mjs'
import { toolPaths } from '../../usage-monitor-release-video/scripts/setup-tools.mjs'
import { FORMATS, launch } from './browser.mjs'
import { buildPlayerHtml, playerBeats, playerSrc, writtenTexts } from './player.mjs'
import { createRepoServer } from './serve.mjs'
import { codeCardData, codeExcerpt } from './code-card.mjs'
import { renderCodeCard } from './code-render.mjs'

const SCRIPTS = dirname(fileURLToPath(import.meta.url))
const ASSETS = join(SCRIPTS, '..', 'assets')
const VOICE_CONCURRENCY = 3
const RENDER_CONCURRENCY = 2
const COMMANDS = ['check', 'stage', 'preview', 'serve', 'voice', 'render']
const USAGE = 'uso: node scripts/make.mjs <check|stage|preview|serve|voice|render> <video.json> [--only <variante>] [--to <s>] [--silent] [--port <porta>]'
// Mesma faixa de loudnorm da voz: I=-16 (fala), TP=-1.5.
const VOICE_LOUDNORM = 'loudnorm=I=-16:TP=-1.5:LRA=11'
// Quadros de QA: short-pt em 9:16 e long-pt em 16:9 (stills.mjs).
const STILLS = [['short-pt', '9:16'], ['long-pt', '16:9']]
const CELL_W = 640
const CELL_H = 360
const SHEET_COLS = 4

class Stop extends Error {}
const stop = (message) => {
  throw new Stop(message)
}

// Beats de cada tipo: longo, todos; curto, so os que tem short (como buildScript).
const sourceBeats = (spec, kind) => spec.beats.filter((beat) => kind === 'long' || beat.short)
const spokenOf = (beat, kind, lang) => {
  const entry = beat[kind]
  return toSpoken(entry[lang], lang, entry.say)
}
const charCount = (text) => [...text].length
const uniqueRows = (rows) => [...new Map(rows.map((row) => [row.variant, row])).values()]
const gapOf = (script) => {
  const gaps = script.beats.slice(1).map((beat, i) => beat.speechStart - script.beats[i].speechEnd)
  return gaps.length ? Math.min(...gaps).toFixed(2) : 'n/a'
}

function repoRoot() {
  try {
    return execFileSync('git', ['rev-parse', '--show-toplevel'], { cwd: SCRIPTS, encoding: 'utf8' }).trim()
  } catch {
    return stop('nao achei a raiz do repositorio (git rev-parse --show-toplevel)')
  }
}

// Roda um processo sem entrada e devolve a saida; falha com o fim da saida.
function run(cmd, args, label) {
  return new Promise((done, reject) => {
    const child = spawn(cmd, args, { stdio: ['ignore', 'pipe', 'pipe'] })
    let out = ''
    child.stdout.on('data', (chunk) => { out += chunk })
    child.stderr.on('data', (chunk) => { out += chunk })
    child.on('error', reject)
    child.on('close', (code) => {
      if (code === 0) done(out)
      else reject(new Error(`${label} saiu com codigo ${code}\n${out.slice(-2000)}`))
    })
  })
}

// Ate `limit` tarefas ao mesmo tempo.
async function pool(items, limit, worker) {
  let next = 0
  const lanes = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) {
      const item = items[next]
      next += 1
      await worker(item)
    }
  })
  await Promise.all(lanes)
}

// Imagem é relativa ao video.json; sem arquivo ali, à raiz do repositório.
function findImage(videoDir, image) {
  return [resolve(videoDir, image), resolve(repoRoot(), image)].find((path) => existsSync(path)) ?? null
}

// Copia as imagens para <work>/images/ e devolve { beatId: 'images/<arquivo>' }, relativo ao <variante>.js.
async function stageImages(spec, videoDir, work) {
  const dir = join(work, 'images')
  mkdirSync(dir, { recursive: true })
  const map = {}
  for (const beat of spec.beats) {
    if (!beat.image) continue
    const name = `${beat.id}-${basename(beat.image)}`
    copyFileSync(findImage(videoDir, beat.image), join(dir, name))
    map[beat.id] = `images/${name}`
  }
  // Beat com code: PNG do trecho de código (cartão em code-render.mjs), no mesmo diretório de imagens.
  const codeBeats = spec.beats.filter((beat) => beat.code)
  if (codeBeats.length) {
    const fontsDir = join(repoRoot(), 'src', 'desktopMain', 'resources', 'fonts')
    const fonts = {
      regular: pathToFileURL(join(fontsDir, 'IBMPlexMono-Regular.ttf')).href,
      medium: pathToFileURL(join(fontsDir, 'IBMPlexMono-Medium.ttf')).href,
    }
    mkdirSync(join(work, 'code'), { recursive: true })
    const browser = await launch()
    try {
      for (const beat of codeBeats) {
        const { file, from, to } = beat.code
        const lines = codeExcerpt(readFileSync(join(repoRoot(), file), 'utf8'), from, to)
        const name = `${beat.id}-code.png`
        const { font } = await renderCodeCard(browser, {
          fonts,
          data: codeCardData({ file, from, to, lines }),
          htmlFile: join(work, 'code', `${beat.id}.html`),
          out: join(dir, name),
        })
        console.log(`codigo ${beat.id}: fonte ${font} px`)
        map[beat.id] = `images/${name}`
      }
    } finally {
      await browser.close()
    }
  }
  return map
}

// Grava window.WF_SCRIPT; a imagem de cada beat vem do video.json (buildScript nao a leva).
function writeData(file, script, images) {
  const beats = script.beats.map((beat) => (images[beat.id] ? { ...beat, image: images[beat.id] } : beat))
  writeFileSync(file, `window.WF_SCRIPT = ${JSON.stringify({ ...script, beats }, null, 2)};\n`, 'utf8')
}

function readScript(file) {
  if (!existsSync(file)) stop(`roteiro ausente: ${file} (rode make.mjs preview ou voice antes)`)
  const sandbox = { window: {} }
  vm.runInNewContext(readFileSync(file, 'utf8'), sandbox, { filename: file })
  return sandbox.window.WF_SCRIPT
}

function probeSeconds(file) {
  const out = execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file], { encoding: 'utf8' })
  const seconds = Number(out.trim())
  if (!Number.isFinite(seconds) || seconds <= 0) stop(`ffprobe nao mediu a duracao de ${file}`)
  return seconds
}

// Roteiro com duracao estimada por caracteres (preview).
function previewScript(spec, row) {
  const durations = {}
  for (const beat of sourceBeats(spec, row.kind)) durations[beat.id] = estimateDuration(spokenOf(beat, row.kind, row.lang))
  return buildScript(spec, row.kind, row.lang, durations)
}

// Prévia jogável: qa/player-<variante>.html (iframe da cena, controles e lista de beats; sem áudio).
function writePlayer(spec, work, row, script) {
  const { w, h } = FORMATS[row.format]
  const qa = join(work, 'qa')
  mkdirSync(qa, { recursive: true })
  const model = {
    title: spec.slug,
    variant: row.variant,
    format: row.format,
    w,
    h,
    duration: script.duration,
    src: playerSrc({ fromDir: qa, indexFile: join(ASSETS, 'index.html'), dataFile: join(work, `${row.variant}.js`), format: row.format }),
    beats: playerBeats(script, writtenTexts(spec, row.kind, row.lang)),
  }
  const file = join(qa, `player-${row.variant}.html`)
  writeFileSync(file, buildPlayerHtml(readFileSync(join(ASSETS, 'player.html'), 'utf8'), model), 'utf8')
  return file
}

// Sirve o repositório em 127.0.0.1 (so GET), para a prévia abrir no Chrome com o iframe da cena na mesma origem.
function serve(spec, port) {
  const root = repoRoot()
  const work = join(root, 'build', 'social-video', spec.slug)
  const files = VARIANTS.map((row) => join(work, 'qa', `player-${row.variant}.html`)).filter((file) => existsSync(file))
  if (!files.length) stop(`nenhuma previa em ${join(work, 'qa')}; rode make.mjs preview antes`)
  const server = createRepoServer(root)
  server.listen(Number(port ?? 8765), '127.0.0.1', () => {
    const base = `http://127.0.0.1:${server.address().port}`
    for (const file of files) console.log(`${base}/${relative(root, file).split(sep).join('/')}`)
  })
  return 0
}

async function preview(spec, work, images, rows) {
  const scripts = {}
  for (const row of rows) {
    const script = previewScript(spec, row)
    writeData(join(work, `${row.variant}.js`), script, images)
    writeFileSync(join(work, `${row.variant}.srt`), toSrt(script.captions), 'utf8')
    scripts[row.variant] = script
    console.log(`${row.variant}: ${script.duration} s estimados, ${script.beats.length} beats, gap ${gapOf(script)} s`)
    console.log(`previa jogavel: ${writePlayer(spec, work, row, script)}`)
  }
  const qa = join(work, 'qa')
  mkdirSync(qa, { recursive: true })
  const shots = []
  for (const [variant, format] of STILLS) {
    if (!scripts[variant]) continue
    const times = scripts[variant].beats.map((beat) => Math.round(((beat.start + beat.end) / 2) * 10) / 10)
    const out = await run(process.execPath, [join(SCRIPTS, 'stills.mjs'), '--work', work, '--variant', variant, '--format', format, '--times', times.join(',')], 'stills.mjs')
    process.stdout.write(out)
    const slug = format.replace(':', 'x')
    for (const t of times) shots.push(join(qa, `${variant}-${slug}-${t}.jpg`))
  }
  if (shots.length < 2) return
  const sheet = join(qa, 'sheet.jpg')
  const inputs = shots.flatMap((file) => ['-i', file])
  const cells = shots.map((_, i) => `[${i}:v]scale=${CELL_W}:${CELL_H}:force_original_aspect_ratio=decrease,pad=${CELL_W}:${CELL_H}:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1[c${i}]`)
  const layout = shots.map((_, i) => `${(i % SHEET_COLS) * CELL_W}_${Math.floor(i / SHEET_COLS) * CELL_H}`).join('|')
  const stack = `${shots.map((_, i) => `[c${i}]`).join('')}xstack=inputs=${shots.length}:layout=${layout}[out]`
  await run('ffmpeg', ['-y', '-v', 'error', ...inputs, '-filter_complex', [...cells, stack].join(';'), '-map', '[out]', '-frames:v', '1', '-q:v', '3', sheet], 'ffmpeg sheet')
  console.log(`folha de contato: ${sheet}`)
}

// Faixa de voz: cada fala em speechStart, duracao exata, 48 kHz estereo, loudnorm.
function buildVoiceWav(parts, duration, out) {
  const inputs = parts.flatMap((part) => ['-i', part.file])
  const chains = parts.map((part, i) => `[${i}:a]aresample=48000,aformat=channel_layouts=stereo,adelay=${Math.round(part.at * 1000)}:all=1[a${i}]`)
  const mix = `${parts.map((_, i) => `[a${i}]`).join('')}amix=inputs=${parts.length}:duration=longest:normalize=0,`
    + `apad=whole_dur=${duration},atrim=end=${duration},${VOICE_LOUDNORM}[out]`
  return run('ffmpeg', ['-y', '-v', 'error', ...inputs, '-filter_complex', [...chains, mix].join(';'), '-map', '[out]', '-ar', '48000', '-ac', '2', '-c:a', 'pcm_s24le', out], 'ffmpeg voz')
}

async function voice(spec, work, images, rows) {
  const key = process.env.OPENROUTER_API_KEY
  if (!key) stop('OPENROUTER_API_KEY ausente: defina a variavel de ambiente com a chave temporaria')
  const seg = join(work, 'seg')
  mkdirSync(seg, { recursive: true })
  const plan = rows.map((row) => ({
    row,
    beats: sourceBeats(spec, row.kind).map((beat) => {
      const text = spokenOf(beat, row.kind, row.lang)
      return { id: beat.id, text, lang: row.lang, file: join(seg, segmentName(row.kind, row.lang, beat.id, text)) }
    }),
  }))
  const jobs = [...new Map(plan.flatMap((item) => item.beats).map((job) => [job.file, job])).values()]
  let reused = 0
  let made = 0
  await pool(jobs, VOICE_CONCURRENCY, async (job) => {
    if (existsSync(job.file)) {
      reused += 1
      return
    }
    writeFileSync(job.file, await synthesize({ text: job.text, lang: job.lang, key }))
    made += 1
  })
  console.log(`segmentos: ${made} novos, ${reused} reaproveitados`)
  for (const { row, beats } of plan) {
    const durations = {}
    for (const beat of beats) durations[beat.id] = probeSeconds(beat.file)
    const script = buildScript(spec, row.kind, row.lang, durations)
    writeData(join(work, `${row.variant}.js`), script, images)
    writeFileSync(join(work, `${row.variant}.srt`), toSrt(script.captions), 'utf8')
    await buildVoiceWav(
      beats.map((beat) => ({ file: beat.file, at: script.beats.find((b) => b.id === beat.id).speechStart })),
      script.duration,
      join(work, `${row.variant}-voice.wav`),
    )
    const chars = beats.reduce((n, beat) => n + charCount(beat.text), 0)
    console.log(`${row.variant}: ${chars} caracteres falados, ${script.duration} s, gap ${gapOf(script)} s`)
  }
}

async function render(spec, work, rows, { to, silent }) {
  const uniq = uniqueRows(rows)
  for (const row of uniq) {
    const wav = join(work, `${row.variant}-voice.wav`)
    if (existsSync(wav)) continue
    if (!silent) stop(`${row.variant}: falta ${basename(wav)}; rode make.mjs voice antes (ou --silent para teste)`)
    const { duration } = readScript(join(work, `${row.variant}.js`)) ?? {}
    if (!Number.isFinite(duration)) stop(`${row.variant}: roteiro sem duracao`)
    await run('ffmpeg', ['-y', '-v', 'error', '-f', 'lavfi', '-i', 'anullsrc=channel_layout=stereo:sample_rate=48000', '-t', String(duration), '-c:a', 'pcm_s24le', wav], 'ffmpeg silencio')
  }
  const python = toolPaths().python
  for (const row of uniq) {
    await run(python, [join(SCRIPTS, 'audio.py'), '--work', work, '--variant', row.variant], 'audio.py')
  }
  const testing = to !== null
  const tempDir = join(work, testing ? 'smoke' : 'render')
  const finalDir = testing ? join(work, 'smoke') : join(homedir(), 'Videos', 'usage-monitor', spec.slug)
  mkdirSync(tempDir, { recursive: true })
  mkdirSync(finalDir, { recursive: true })
  await pool(rows, RENDER_CONCURRENCY, async (row) => {
    const temp = join(tempDir, `${row.variant}-${row.format.replace(':', 'x')}.mp4`)
    const args = [join(SCRIPTS, 'render.mjs'), '--work', work, '--variant', row.variant, '--format', row.format, '--out', temp]
    if (testing) args.push('--to', String(to))
    await run(process.execPath, args, `render.mjs ${row.variant} ${row.format}`)
    for (const name of outputNames(spec.slug, row)) {
      copyFileSync(temp, join(finalDir, name))
      copyFileSync(join(work, `${row.variant}.srt`), join(finalDir, name.replace(/\.mp4$/, '.srt')))
      console.log(`gravado: ${join(finalDir, name)}`)
    }
  })
}

async function main() {
  const { positionals, values } = parseArgs({
    allowPositionals: true,
    options: {
      only: { type: 'string' },
      to: { type: 'string' },
      silent: { type: 'boolean', default: false },
      port: { type: 'string' },
    },
  })
  const [command, videoArg] = positionals
  if (!COMMANDS.includes(command) || !videoArg) stop(USAGE)
  const videoFile = resolve(videoArg)
  if (!existsSync(videoFile)) stop(`video.json ausente: ${videoFile}`)
  const spec = JSON.parse(readFileSync(videoFile, 'utf8'))
  const videoDir = dirname(videoFile)
  // Texto de arquivo relativo à raiz do repo (regra do code); null se não existe.
  const readText = (p) => {
    try {
      return readFileSync(resolve(repoRoot(), p), 'utf8')
    } catch {
      return null
    }
  }
  const errors = validateSpec(spec, { fileExists: (p) => findImage(videoDir, p) !== null, readText })
  if (errors.length) {
    for (const error of errors) console.error(`- ${error}`)
    return 1
  }

  if (command === 'check') {
    for (const kind of ['short', 'long']) {
      for (const lang of ['pt', 'en']) {
        const beats = sourceBeats(spec, kind)
        if (!beats.length) continue
        const chars = beats.reduce((n, beat) => n + charCount(spokenOf(beat, kind, lang)), 0)
        console.log(`${kind}-${lang}: ${chars} caracteres falados em ${beats.length} beats`)
      }
    }
    return 0
  }

  let rows = VARIANTS
  if (values.only !== undefined) {
    rows = VARIANTS.filter((row) => row.variant === values.only)
    if (!rows.length) stop(`variante desconhecida: ${values.only}`)
  }
  let to = null
  if (values.to !== undefined) {
    to = Number(values.to)
    if (!(Number.isFinite(to) && to > 0)) stop('--to pede segundos maiores que zero')
  }

  if (command === 'serve') return serve(spec, values.port)
  const work = join(repoRoot(), 'build', 'social-video', spec.slug)
  mkdirSync(work, { recursive: true })
  const images = await stageImages(spec, videoDir, work)
  if (command === 'stage') {
    for (const beat of spec.beats) if (images[beat.id]) console.log(`${beat.id}: ${join(work, images[beat.id])}`)
    return 0
  }
  if (command === 'preview') await preview(spec, work, images, uniqueRows(rows))
  if (command === 'voice') await voice(spec, work, images, uniqueRows(rows))
  if (command === 'render') await render(spec, work, rows, { to, silent: values.silent })
  return 0
}

main().then(
  (code) => { process.exitCode = code },
  (err) => {
    console.error(err instanceof Stop ? err.message : `erro: ${err.message}`)
    process.exitCode = 1
  },
)
