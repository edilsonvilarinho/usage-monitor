// Preview do vídeo de release no navegador (#633, --preview): o próprio assets/video.html, num quadro 1080×1920
// escalado para a tela, dirigido pela trilha da fala. Tudo é gerado como texto, sem servidor: a página do pai
// conversa com o quadro por postMessage, porque o Chrome isola iframes de arquivos locais.
import { join } from 'node:path'

export const LANGUAGES = ['en', 'pt']

export function frameFile(language) {
    return `stage/preview-${language}.html`
}

// JSON dentro de <script> não pode conter "</script>" nem separadores de linha: o texto da legenda vem do roteiro.
function inlineJson(value) {
    return JSON.stringify(value).replace(/[<\u2028\u2029]/g, (char) => '\\u' + char.charCodeAt(0).toString(16).padStart(4, '0'))
}

// Quadro de um idioma: o video.html como está, mais o driver que recebe o `data` do render e desenha o instante pedido.
export function frameHtml({ videoHtml, data }) {
    const driver = `<script>
const PREVIEW_DATA = ${inlineJson(data)}
setup(PREVIEW_DATA).then(() => {
  renderFrame(0)
  parent.postMessage({ type: 'ready' }, '*')
})
window.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'frame') renderFrame(event.data.t)
})
</script>
`
    const end = videoHtml.lastIndexOf('</body>')
    return end === -1 ? videoHtml + driver : videoHtml.slice(0, end) + driver + videoHtml.slice(end)
}

// Página do pai: barra com EN/PT, play/pausa e tempo; o quadro 1080×1920 escalado; a trilha do idioma escolhido.
// tokensCss são os tokens do design system (docs/design-system/tokens/colors.css), para o fundo e as cores.
export function previewHtml({ total, tokensCss = '', start = 0 }) {
    return `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Preview do vídeo de release</title>
<style>
${tokensCss}
html, body { margin: 0; height: 100%; overflow: hidden; background: var(--bg, #131010); color: var(--fg, #F2EDED); font-family: "Plex Sans", sans-serif; }
#player { position: fixed; left: 0; top: 0; right: 0; height: 56px; display: flex; gap: 12px; align-items: center; padding: 0 16px;
  background: var(--surface, #1B1818); border-bottom: 1px solid var(--border, #3D3838); }
#player button { font: 500 16px "Plex Mono", monospace; color: inherit; background: var(--raised, #211E1E);
  border: 1px solid var(--border, #3D3838); border-radius: 6px; padding: 6px 12px; cursor: pointer; }
#player button[aria-pressed="true"] { color: var(--gold, #E8AE63); border-color: var(--gold, #E8AE63); }
#player input[type="range"] { flex: 1; }
#clock { font: 500 16px "Plex Mono", monospace; min-width: 150px; text-align: right; }
#screen { position: absolute; top: 72px; left: 0; width: 1080px; height: 1920px; transform-origin: top left; }
#frame { width: 1080px; height: 1920px; border: 0; display: block; }
</style>
</head>
<body>
<div id="player">
  <button id="play" type="button">Play</button>
  <button id="en" type="button" aria-pressed="true">EN</button>
  <button id="pt" type="button" aria-pressed="false">PT</button>
  <input id="scrub" type="range" min="0" max="${total}" step="0.01" value="${start}">
  <span id="clock"></span>
</div>
<div id="screen"><iframe id="frame" src="${frameFile('en')}"></iframe></div>
<audio id="audio" preload="auto" src="en.mp3"></audio>
<script>
const TOTAL = ${total}
const LANGUAGES = ${JSON.stringify(LANGUAGES)}
const TRACKS = ${JSON.stringify(Object.fromEntries(LANGUAGES.map((code) => [code, code + '.mp3'])))}
const frame = document.getElementById('frame')
const screenBox = document.getElementById('screen')
const audio = document.getElementById('audio')
const scrub = document.getElementById('scrub')
const clock = document.getElementById('clock')
const playButton = document.getElementById('play')
let language = 'en'
let ready = false
let seconds = Number(new URLSearchParams(location.search).get('t')) || ${start}

function fit() {
  const scale = Math.min(1, (innerHeight - 72) / 1920)
  screenBox.style.transform = 'scale(' + scale + ')'
  screenBox.style.left = Math.max(0, (innerWidth - 1080 * scale) / 2) + 'px'
}

function choose(next) {
  language = next
  ready = false
  audio.pause()
  audio.src = TRACKS[next]
  frame.src = 'stage/preview-' + next + '.html'
  for (const code of LANGUAGES) document.getElementById(code).setAttribute('aria-pressed', String(code === next))
  playButton.textContent = 'Play'
}

playButton.addEventListener('click', () => {
  if (audio.paused) {
    audio.currentTime = seconds
    audio.play()
    playButton.textContent = 'Pause'
  } else {
    seconds = audio.currentTime
    audio.pause()
    playButton.textContent = 'Play'
  }
})
audio.addEventListener('ended', () => {
  seconds = TOTAL
  playButton.textContent = 'Play'
})
for (const code of LANGUAGES) document.getElementById(code).addEventListener('click', () => choose(code))
scrub.addEventListener('input', () => {
  seconds = Number(scrub.value)
  if (!audio.paused) audio.currentTime = seconds
})
window.addEventListener('message', (event) => {
  if (event.source === frame.contentWindow && event.data && event.data.type === 'ready') {
    ready = true
    frame.contentWindow.postMessage({ type: 'frame', t: seconds }, '*')
  }
})
window.addEventListener('resize', fit)

// Cada quadro da tela pede o instante atual: o da trilha, se toca; o da barra, se está parado.
function tick() {
  if (!audio.paused) seconds = audio.currentTime
  if (ready) frame.contentWindow.postMessage({ type: 'frame', t: seconds }, '*')
  scrub.value = String(seconds)
  clock.textContent = seconds.toFixed(1) + ' / ' + TOTAL.toFixed(1) + ' s'
  requestAnimationFrame(tick)
}
fit()
requestAnimationFrame(tick)
</script>
</body>
</html>
`
}

// Trilha de um idioma para o preview: cada fala no seu instante (voiceAt), sem drone, cortada no fim do vídeo.
// voiceDir é a pasta do idioma, com <id>.wav por cena.
export function previewAudioArgs({ scenes, timeline, voiceDir, output }) {
    const inputs = []
    const filters = []
    scenes.forEach((scene, index) => {
        inputs.push('-i', join(voiceDir, `${scene.id}.wav`))
        const delay = Math.round(timeline.scenes[index].voiceAt * 1000)
        filters.push(`[${index}:a]aresample=48000,adelay=${delay}:all=1[v${index}]`)
    })
    const labels = scenes.map((_, index) => `[v${index}]`).join('')
    filters.push(`${labels}amix=inputs=${scenes.length}:normalize=0,aresample=48000[audio]`)
    return [
        '-v', 'error', '-y', ...inputs, '-filter_complex', filters.join(';'), '-map', '[audio]',
        '-t', timeline.total.toFixed(3), '-c:a', 'libmp3lame', '-b:a', '192k', output,
    ]
}
