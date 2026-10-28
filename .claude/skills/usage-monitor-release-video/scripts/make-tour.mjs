#!/usr/bin/env node
// Gera o tour em vídeo 16:9 (#505) a partir de docs/planos/video-tour/scenes.json.
// Uso: node make-tour.mjs [--stills] [--only en|pt] [--refresh-media] [--remix]
//   --stills         só os quadros de conferência (um por captura e por cena)
//   --only           um idioma só (dá para rodar os dois idiomas em paralelo, um processo cada)
//   --refresh-media  regrava as capturas (gradlew generateVideoMedia) mesmo se já existirem em build/video-media
//   --remix          refaz só o áudio, sobre o vídeo mudo do último render
// Saída: build/release-video/tour/ (intermediários e conferência) e ~/Videos/usage-monitor/tour/ (MP4 e .srt).
import { execFileSync, spawnSync } from 'node:child_process'
import { copyFileSync, cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { renderStills, renderVideo } from './render.mjs'
import { setupTools } from './setup-tools.mjs'
import { LANGUAGES, TOUR_PACING, buildTimeline, captionCues, soundEvents, toSrt } from './timeline.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const args = process.argv.slice(2)
const stillsOnly = args.includes('--stills')
const only = args.includes('--only') ? args[args.indexOf('--only') + 1] : null
const languages = only ? [only] : LANGUAGES
const remix = args.includes('--remix')
const FRAME = { width: 1920, height: 1080, page: 'tour.html' }
const VOICE_GAIN_DB = 8
const MUSIC_GAIN_DB = -12
const LOUDNESS = 'I=-16:TP=-1.5:LRA=11'

// Primeira passada do loudnorm: o relatório JSON sai no stderr, depois do log do filtro.
function loudnessReport(file) {
    const result = spawnSync('ffmpeg', [
        '-hide_banner', '-nostats', '-i', file, '-af', `loudnorm=${LOUDNESS}:print_format=json`, '-f', 'null', '-',
    ], { encoding: 'utf8' })
    const text = result.stderr
    return text.slice(text.lastIndexOf('{'), text.lastIndexOf('}') + 1)
}

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const docsDir = join(root, 'docs', 'planos', 'video-tour')
const work = join(root, 'build', 'release-video', 'tour')
const stage = join(work, `stage-${languages.join('-')}`)
const outDir = join(homedir(), 'Videos', 'usage-monitor', 'tour')
const project = JSON.parse(readFileSync(join(docsDir, 'scenes.json'), 'utf8'))
const log = (message) => process.stdout.write(`${message}\n`)

const tools = await setupTools({ kokoro: project.voice?.engine === 'kokoro' })

// Capturas: as demos da ajuda, a 2×, no idioma de cada vídeo (HelpMediaGenerator --frames).
const mediaRoot = join(root, 'build', 'video-media')
for (const language of languages) {
    if (args.includes('--refresh-media') || !existsSync(join(mediaRoot, language))) {
        log(`Gravando as capturas em ${language} (gradlew generateVideoMedia)…`)
        const gradle = join(root, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')
        execFileSync(gradle, ['generateVideoMedia', `-PvideoMediaLanguage=${language}`, '-q'], {
            cwd: root,
            stdio: 'inherit',
            shell: process.platform === 'win32',
        })
    }
}

// Palco: renderizador, fontes do app e as capturas usadas pelo roteiro.
rmSync(stage, { recursive: true, force: true })
mkdirSync(join(stage, 'fonts'), { recursive: true })
copyFileSync(join(here, '..', 'assets', 'tour.html'), join(stage, 'tour.html'))
const fontsDir = join(root, 'src', 'desktopMain', 'resources', 'fonts')
for (const font of readdirSync(fontsDir)) copyFileSync(join(fontsDir, font), join(stage, 'fonts', font))
const used = [...new Set(project.scenes.flatMap((scene) => (scene.media ?? []).map((shot) => shot.media)))]
const manifests = {}
for (const language of languages) {
    manifests[language] = {}
    for (const id of used) {
        const source = join(mediaRoot, language, id)
        if (!existsSync(join(source, 'frames.json'))) throw new Error(`Captura ausente: ${source} (rode com --refresh-media)`)
        cpSync(source, join(stage, 'media', language, id), { recursive: true })
        manifests[language][id] = JSON.parse(readFileSync(join(source, 'frames.json'), 'utf8'))
    }
}

// Voz: a duração medida de cada fala decide a linha do tempo, nos dois idiomas sempre.
log('Sintetizando as falas…')
const voiceDir = join(work, 'voice')
execFileSync(tools.python, [join(here, 'synthesize.py'), join(docsDir, 'scenes.json'), tools.voices, voiceDir], {
    stdio: ['ignore', 'ignore', 'inherit'],
})
const durations = JSON.parse(readFileSync(join(voiceDir, 'durations.json'), 'utf8'))
const timeline = buildTimeline(project.scenes, durations, TOUR_PACING)
writeFileSync(join(work, 'timeline.json'), JSON.stringify(timeline, null, 2))
const fastest = Math.max(...LANGUAGES.flatMap((language) => project.scenes.map((scene) =>
    (scene.lines[language].say ?? scene.lines[language].text).length / durations[language][scene.id])))
log(`Linha do tempo: ${timeline.total.toFixed(1)} s, ${timeline.scenes.length} cenas, fala mais rápida ${fastest.toFixed(1)} car/s`)

// Quadros de conferência: um por captura (no foco, quando há) e um por cena sem captura.
function checkTimes() {
    const times = []
    timeline.scenes.forEach((timing, index) => {
        const scene = project.scenes[index]
        if (timing.shots) {
            timing.shots.forEach((shot, position) => {
                const next = timing.shots[position + 1]?.at ?? timing.end
                times.push({ t: Math.min(next - 0.3, (shot.focusAt ?? shot.at) + 1.2), name: `${scene.id}-${position + 1}` })
            })
        } else if (scene.kind === 'commands') {
            times.push({ t: timing.end - 0.6, name: scene.id })
        } else {
            times.push({ t: Math.min(timing.end - 0.5, timing.voiceAt + 2.5), name: scene.id })
        }
    })
    times.push({ t: timeline.scenes[2].start + 0.7, name: 'lente' })
    return times
}

mkdirSync(outDir, { recursive: true })
mkdirSync(join(work, 'check'), { recursive: true })

for (const language of languages) {
    const cues = captionCues(project.scenes, timeline, durations, language, TOUR_PACING)
    const data = { scenes: project.scenes, timeline, cues, lang: language, media: manifests[language] }
    const checks = checkTimes()
    await renderStills({
        nodeModules: tools.nodeModules,
        stageDir: stage,
        data,
        frame: FRAME,
        times: checks.map((check) => check.t),
        outputFor: (t) => {
            const index = checks.findIndex((check) => check.t === t)
            return join(work, 'check', `${language}-${String(index + 1).padStart(2, '0')}-${checks[index].name}.png`)
        },
    })
    if (stillsOnly) continue

    // Trilha e efeitos na duração exata, com os efeitos nos instantes da linha do tempo.
    const soundDir = join(work, `sound-${language}`)
    mkdirSync(soundDir, { recursive: true })
    writeFileSync(join(soundDir, 'events.json'), JSON.stringify(soundEvents(project.scenes, timeline, language)))
    execFileSync(tools.python, [join(here, 'soundtrack.py'), timeline.total.toFixed(3), join(soundDir, 'events.json'), soundDir], {
        stdio: ['ignore', 'ignore', 'inherit'],
    })

    // --remix refaz só o áudio sobre o vídeo mudo já renderizado (o render leva ~20 min por idioma).
    const silent = join(work, `${language}-silent.mp4`)
    if (!(remix && existsSync(silent))) {
        log(`Renderizando ${language}…`)
        await renderVideo({
            nodeModules: tools.nodeModules,
            stageDir: stage,
            data,
            frame: FRAME,
            output: silent,
            onProgress: (frame, frames) => log(`  ${language}: ${Math.round((frame / frames) * 100)}%`),
        })
    }

    // Mixagem: falas no instante de cada cena; a trilha abaixa sob a fala (sidechain) e volta nos respiros.
    // Ganhos fixos (a voz do Kokoro sai ~ -24 dB de média, a trilha ~ -18): a trilha fica ~14 dB abaixo da fala.
    // O loudnorm de uma passada é dinâmico e levantava a trilha nos respiros até o nível da fala (medido: -22,3 dB
    // no respiro contra -21,4 dB com fala); por isso duas passadas, a segunda linear, só com o ganho medido.
    const inputs = []
    const filters = []
    project.scenes.forEach((scene, index) => {
        inputs.push('-i', join(voiceDir, language, `${scene.id}.wav`))
        const delay = Math.round(timeline.scenes[index].voiceAt * 1000)
        filters.push(`[${index}:a]aresample=48000,aformat=channel_layouts=stereo,adelay=${delay}:all=1[v${index}]`)
    })
    const music = project.scenes.length
    inputs.push('-i', join(soundDir, 'music.wav'), '-i', join(soundDir, 'effects.wav'))
    const voices = project.scenes.map((_, index) => `[v${index}]`).join('')
    filters.push(`${voices}amix=inputs=${project.scenes.length}:normalize=0,volume=${VOICE_GAIN_DB}dB,apad[speech]`)
    filters.push('[speech]asplit=2[voice][key]')
    filters.push(`[${music}:a]volume=${MUSIC_GAIN_DB}dB[bed]`)
    filters.push('[bed][key]sidechaincompress=threshold=0.02:ratio=4:attack=40:release=900[ducked]')
    filters.push(`[voice][ducked][${music + 1}:a]amix=inputs=3:normalize=0,alimiter=limit=0.9:level=disabled[audio]`)
    const total = timeline.total.toFixed(3)
    const mixed = join(soundDir, 'mix.wav')
    execFileSync('ffmpeg', [
        '-v', 'error', '-y', ...inputs, '-filter_complex', filters.join(';'), '-map', '[audio]', '-t', total,
        '-c:a', 'pcm_s16le', mixed,
    ], { stdio: 'inherit' })
    const measured = JSON.parse(loudnessReport(mixed))
    const linear = `loudnorm=${LOUDNESS}:measured_I=${measured.input_i}:measured_TP=${measured.input_tp}:` +
        `measured_LRA=${measured.input_lra}:measured_thresh=${measured.input_thresh}:offset=${measured.target_offset}:linear=true`
    const video = join(outDir, `usage-monitor-tour-${language}.mp4`)
    execFileSync('ffmpeg', [
        '-v', 'error', '-y', '-i', silent, '-i', mixed, '-map', '0:v', '-map', '1:a', '-af', `${linear},aresample=48000`,
        '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-t', total, '-movflags', '+faststart', video,
    ], { stdio: 'inherit' })
    writeFileSync(join(outDir, `usage-monitor-tour-${language}.srt`), toSrt(cues))
    const probe = execFileSync('ffprobe', [
        '-v', 'error', '-show_entries', 'format=duration:stream=codec_type,codec_name,width,height', '-of', 'compact', video,
    ], { encoding: 'utf8' })
    log(`${video}\n${probe.trim()}`)
}

log(`Quadros de conferência: ${join(work, 'check')}`)
if (!stillsOnly) log(`Entregáveis: ${outDir}`)
