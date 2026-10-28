#!/usr/bin/env node
// Gera o vídeo de lançamento a partir de docs/planos/video-v<maior>-<menor>/scenes.json e social.json.
// Uso: node make-video.mjs vX.Y.Z [--stills] [--only en|pt]
//   --stills  só os quadros de conferência de cada cena (rápido; para revisar o roteiro)
//   --only    um idioma só
// Saída: build/release-video/<versão>/ (intermediários e quadros de conferência) e
// ~/Videos/usage-monitor/<versão>/ (vídeos, .srt e social.md).
import { execFileSync } from 'node:child_process'
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { basename, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { renderStills, renderVideo } from './render.mjs'
import { setupTools } from './setup-tools.mjs'
import { LANGUAGES, buildTimeline, captionCues, toSrt } from './timeline.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const args = process.argv.slice(2)
const version = args.find((arg) => /^v\d+\.\d+\.\d+$/.test(arg))
if (!version) {
    process.stderr.write('Uso: node make-video.mjs vX.Y.Z [--stills] [--only en|pt]\n')
    process.exit(2)
}
const stillsOnly = args.includes('--stills')
const only = args.includes('--only') ? args[args.indexOf('--only') + 1] : null
const languages = only ? [only] : LANGUAGES

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const [major, minor] = version.slice(1).split('.')
const docsDir = join(root, 'docs', 'planos', `video-v${major}-${minor}`)
const work = join(root, 'build', 'release-video', version)
const stage = join(work, 'stage')
const outDir = join(homedir(), 'Videos', 'usage-monitor', version)
const project = JSON.parse(readFileSync(join(docsDir, 'scenes.json'), 'utf8'))
const log = (message) => process.stdout.write(`${message}\n`)

const tools = await setupTools()

// Palco: renderizador, fontes do app e as imagens das cenas.
rmSync(stage, { recursive: true, force: true })
mkdirSync(join(stage, 'fonts'), { recursive: true })
copyFileSync(join(here, '..', 'assets', 'video.html'), join(stage, 'video.html'))
const fontsDir = join(root, 'src', 'desktopMain', 'resources', 'fonts')
for (const font of readdirSync(fontsDir)) copyFileSync(join(fontsDir, font), join(stage, 'fonts', font))
for (const [key, source] of Object.entries(project.sources ?? {})) {
    const target = join(stage, key)
    mkdirSync(target, { recursive: true })
    if (source.gif) {
        execFileSync('ffmpeg', ['-v', 'error', '-i', join(root, source.gif), '-fps_mode', 'passthrough', join(target, '%03d.png')])
    } else {
        for (const file of source.files) copyFileSync(join(root, file), join(target, basename(file)))
    }
}

// Voz: a duração medida de cada fala decide a linha do tempo.
log('Sintetizando as falas (Piper)…')
const voiceDir = join(work, 'voice')
execFileSync(tools.python, [join(here, 'synthesize.py'), join(docsDir, 'scenes.json'), tools.voices, voiceDir], {
    stdio: ['ignore', 'ignore', 'inherit'],
})
const durations = JSON.parse(readFileSync(join(voiceDir, 'durations.json'), 'utf8'))
const timeline = buildTimeline(project.scenes, durations)
writeFileSync(join(work, 'timeline.json'), JSON.stringify(timeline, null, 2))
log(`Linha do tempo: ${timeline.total.toFixed(1)} s, ${timeline.scenes.length} cenas`)

mkdirSync(outDir, { recursive: true })
mkdirSync(join(work, 'check'), { recursive: true })
const name = `usage-monitor-${project.label}`

for (const language of languages) {
    const cues = captionCues(project.scenes, timeline, durations, language)
    const data = { scenes: project.scenes, timeline, cues, lang: language, label: project.label }
    const checkTimes = timeline.scenes.map((scene) => Math.min(scene.end - 0.5, scene.voiceAt + 1.6))
    await renderStills({
        nodeModules: tools.nodeModules,
        stageDir: stage,
        data,
        times: checkTimes,
        outputFor: (t) => {
            const index = checkTimes.indexOf(t)
            return join(work, 'check', `${language}-${String(index + 1).padStart(2, '0')}-${timeline.scenes[index].id}.png`)
        },
    })
    if (stillsOnly) continue

    log(`Renderizando ${language}…`)
    const silent = join(work, `${language}-silent.mp4`)
    await renderVideo({
        nodeModules: tools.nodeModules,
        stageDir: stage,
        data,
        output: silent,
        onProgress: (frame, frames) => log(`  ${language}: ${Math.round((frame / frames) * 100)}%`),
    })

    // Áudio: cada fala no seu instante, sobre um drone grave a ≈ −30 dBFS que entra e sai em 2 s.
    const inputs = ['-i', silent]
    const filters = []
    project.scenes.forEach((scene, index) => {
        inputs.push('-i', join(voiceDir, language, `${scene.id}.wav`))
        const delay = Math.round(timeline.scenes[index].voiceAt * 1000)
        filters.push(`[${index + 1}:a]aresample=48000,adelay=${delay}:all=1[v${index}]`)
    })
    const total = timeline.total.toFixed(3)
    const drone = project.scenes.length + 1
    inputs.push('-f', 'lavfi', '-i', `sine=frequency=55:duration=${total}:sample_rate=48000`)
    inputs.push('-f', 'lavfi', '-i', `sine=frequency=82.41:duration=${total}:sample_rate=48000`)
    filters.push(
        `[${drone}:a][${drone + 1}:a]amix=inputs=2:normalize=0,lowpass=f=180,volume=-16dB,` +
            `afade=t=in:d=2,afade=t=out:st=${(timeline.total - 2).toFixed(3)}:d=2[drone]`,
    )
    const voices = project.scenes.map((_, index) => `[v${index}]`).join('')
    filters.push(`${voices}[drone]amix=inputs=${project.scenes.length + 1}:normalize=0,` +
        'loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000,apad[audio]')
    const video = join(outDir, `${name}-${language}.mp4`)
    execFileSync('ffmpeg', [
        '-v', 'error', '-y', ...inputs, '-filter_complex', filters.join(';'), '-map', '0:v', '-map', '[audio]',
        '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-t', total, '-movflags', '+faststart', video,
    ], { stdio: 'inherit' })
    writeFileSync(join(outDir, `${name}-${language}.srt`), toSrt(cues))
    const probe = execFileSync('ffprobe', [
        '-v', 'error', '-show_entries', 'format=duration:stream=codec_type,codec_name,width,height', '-of', 'compact', video,
    ], { encoding: 'utf8' })
    log(`${video}\n${probe.trim()}`)
}

if (!stillsOnly && existsSync(join(docsDir, 'social.json'))) {
    execFileSync(process.execPath, [join(here, 'social.mjs'), join(docsDir, 'social.json'), version, join(outDir, 'social.md')], {
        stdio: 'inherit',
    })
}
log(`Quadros de conferência: ${join(work, 'check')}`)
if (!stillsOnly) log(`Entregáveis: ${outDir}`)
