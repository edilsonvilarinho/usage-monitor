#!/usr/bin/env node
// Gera o vídeo de lançamento a partir de docs/planos/video-v<maior>-<menor>/scenes.json e social.json.
// Uso: node make-video.mjs vX.Y.Z [--stills] [--only en|pt] [--preview [--scenes <scenes.json>] [--out <dir>]]
//   --stills  só os quadros de conferência de cada cena (rápido; para revisar o roteiro)
//   --only    um idioma só
//   --preview  não renderiza: gera preview.html + <lang>.mp3 para abrir no navegador (#633)
//   --scenes   roteiro alternativo (padrão: docs/planos/video-vX-Y/scenes.json)
//   --out      pasta do preview (padrão: build/release-video/<versão>/preview)
// Saída: build/release-video/<versão>/ (intermediários e quadros de conferência) e
// ~/Videos/usage-monitor/<versão>/ (vídeos, .srt e social.md).
import { execFileSync } from 'node:child_process'
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { RELEASE_ENCODE, renderStills, renderVideo } from './render.mjs'
import { copyFrameSequences, sequenceHolds, withSequences } from './frames.mjs'
import { frameFile, frameHtml, previewAudioArgs, previewHtml } from './preview.mjs'
import { assertScenes } from './scene-check.mjs'
import { setupTools } from './setup-tools.mjs'
import { speakScenes } from './speak.mjs'
import { LANGUAGES, RELEASE_PACING, buildTimeline, captionCues, toSrt, voiceTracks } from './timeline.mjs'

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
const preview = args.includes('--preview')
const pathArg = (flag) => (args.includes(flag) && args[args.indexOf(flag) + 1] ? resolve(args[args.indexOf(flag) + 1]) : null)
const scenesOverride = pathArg('--scenes')
const outOverride = pathArg('--out')

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const [major, minor] = version.slice(1).split('.')
const docsDir = join(root, 'docs', 'planos', `video-v${major}-${minor}`)
const work = join(root, 'build', 'release-video', version)
const previewDir = preview ? (outOverride ?? join(work, 'preview')) : null
const stage = preview ? join(previewDir, 'stage') : join(work, 'stage')
const outDir = join(homedir(), 'Videos', 'usage-monitor', version)
const project = JSON.parse(readFileSync(scenesOverride ?? join(docsDir, 'scenes.json'), 'utf8'))
assertScenes(project.scenes)
const log = (message) => process.stdout.write(`${message}\n`)

const tools = await setupTools({ piper: false })

// Palco: renderizador, fontes do app e as imagens das cenas.
rmSync(stage, { recursive: true, force: true })
mkdirSync(join(stage, 'fonts'), { recursive: true })
copyFileSync(join(here, '..', 'assets', 'video.html'), join(stage, 'video.html'))
const fontsDir = join(root, 'src', 'desktopMain', 'resources', 'fonts')
for (const font of readdirSync(fontsDir)) copyFileSync(join(fontsDir, font), join(stage, 'fonts', font))
for (const [key, source] of Object.entries(project.sources ?? {})) {
    if (source.frames) continue // sequência de quadros por idioma: copyFrameSequences, abaixo
    const target = join(stage, key)
    mkdirSync(target, { recursive: true })
    if (source.gif) {
        execFileSync('ffmpeg', ['-v', 'error', '-i', join(root, source.gif), '-fps_mode', 'passthrough', join(target, '%03d.png')])
    } else {
        for (const file of source.files) copyFileSync(join(root, file), join(target, basename(file)))
    }
}
// Cena screen com "frames": demo de cada idioma (build/video-media/{lang}/<demo>) vai para o palco como <chave>/<lang>/NNN.png.
const sequences = copyFrameSequences({ root, stage, sources: project.sources })

// Voz: a duração medida de cada fala decide a linha do tempo.
log('Sintetizando as falas (OpenRouter, com cache por assinatura)…')
const voiceDir = join(work, 'voice')
const durations = await speakScenes({ project, outDir: voiceDir })
const timeline = buildTimeline(project.scenes, durations, RELEASE_PACING, sequenceHolds(project.scenes, sequences))
writeFileSync(join(work, 'timeline.json'), JSON.stringify(timeline, null, 2))
log(`Linha do tempo: ${timeline.total.toFixed(1)} s, ${timeline.scenes.length} cenas`)

if (preview) {
    // Preview (#633): o mesmo data do render, um quadro por idioma e a trilha de fala, sem drone e sem renderizar.
    const videoHtml = readFileSync(join(here, '..', 'assets', 'video.html'), 'utf8')
    const tokensCss = readFileSync(join(root, 'docs', 'design-system', 'tokens', 'colors.css'), 'utf8')
    for (const language of languages) {
        const cues = captionCues(project.scenes, timeline, durations, language)
        const data = { scenes: withSequences(project.scenes, sequences, language), timeline, cues, lang: language, label: project.label }
        writeFileSync(join(previewDir, frameFile(language)), frameHtml({ videoHtml, data }))
        const track = join(previewDir, `${language}.mp3`)
        execFileSync('ffmpeg', previewAudioArgs({ scenes: project.scenes, timeline, voiceDir: join(voiceDir, language), output: track }), { stdio: 'inherit' })
        log(`  ${language}: ${track}`)
    }
    writeFileSync(join(previewDir, 'preview.html'), previewHtml({ total: timeline.total, tokensCss }))
    log(`Preview: ${join(previewDir, 'preview.html')}`)
    process.exit(0)
}

mkdirSync(outDir, { recursive: true })
mkdirSync(join(work, 'check'), { recursive: true })
const name = `usage-monitor-${project.label}`

for (const language of languages) {
    const cues = captionCues(project.scenes, timeline, durations, language)
    const data = { scenes: withSequences(project.scenes, sequences, language), timeline, cues, lang: language, label: project.label }
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
        encode: RELEASE_ENCODE,
    })

    // Áudio: cada fala no seu instante, sobre um drone grave a ≈ −30 dBFS que entra e sai em 2 s.
    const inputs = ['-i', silent]
    const filters = []
    const tracks = voiceTracks(project.scenes, timeline)
    tracks.forEach((track, index) => {
        inputs.push('-i', join(voiceDir, language, `${track.key}.wav`))
        filters.push(`[${index + 1}:a]aresample=48000,adelay=${Math.round(track.at * 1000)}:all=1[v${index}]`)
    })
    const total = timeline.total.toFixed(3)
    const drone = tracks.length + 1
    inputs.push('-f', 'lavfi', '-i', `sine=frequency=55:duration=${total}:sample_rate=48000`)
    inputs.push('-f', 'lavfi', '-i', `sine=frequency=82.41:duration=${total}:sample_rate=48000`)
    filters.push(
        `[${drone}:a][${drone + 1}:a]amix=inputs=2:normalize=0,lowpass=f=180,volume=-16dB,` +
            `afade=t=in:d=2,afade=t=out:st=${(timeline.total - 2).toFixed(3)}:d=2[drone]`,
    )
    const voices = tracks.map((_, index) => `[v${index}]`).join('')
    filters.push(`${voices}[drone]amix=inputs=${tracks.length + 1}:normalize=0,` +
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
