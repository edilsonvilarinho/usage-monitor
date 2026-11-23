import { test } from 'node:test'
import assert from 'node:assert/strict'
import { frameFile, frameHtml, previewAudioArgs, previewHtml } from '../preview.mjs'
import { join } from 'node:path'

const videoHtml = '<!doctype html><html><body><div id="stage"></div><script>function renderFrame(t) {}</script></body></html>'

const data = {
    scenes: [{ id: 'title', kind: 'title', lines: { en: { text: 'Usage Monitor.' } } }],
    timeline: { total: 6.5, scenes: [{ id: 'title', start: 0, voiceAt: 1.4, end: 4 }] },
    cues: [{ start: 1.5, end: 3, lines: ['</script><b>x</b>'] }],
    lang: 'en',
    label: '42.3',
}

test('quadro do preview: video.html inteiro, dados do render inline e escapados, e o driver que responde ready', () => {
    const html = frameHtml({ videoHtml, data })
    assert.ok(html.includes('<div id="stage"></div>'))
    assert.ok(html.includes('const PREVIEW_DATA = '))
    assert.ok(!html.includes('</script><b>x'), 'legenda com </script> quebraria a página')
    assert.ok(html.includes('setup(PREVIEW_DATA)'))
    assert.ok(html.includes("postMessage({ type: 'ready' }"))
    assert.ok(html.includes("event.data.type === 'frame'") && html.includes('renderFrame(event.data.t)'))
    assert.ok(html.lastIndexOf('</body>') > html.indexOf('PREVIEW_DATA'), 'driver entra antes de fechar o body')
})

test('cada idioma tem o seu quadro, em stage/, para o iframe carregar', () => {
    assert.equal(frameFile('en'), 'stage/preview-en.html')
    assert.equal(frameFile('pt'), 'stage/preview-pt.html')
})

test('preview.html: iframe 1080x1920 escalado, botões EN e PT, play/pausa, barra de tempo e os dois mp3', () => {
    const html = previewHtml({ total: 90.25, tokensCss: ':root{--bg:#131010}' })
    assert.ok(html.includes('src="stage/preview-en.html"'))
    assert.ok(/width:\s*1080px/.test(html) && /height:\s*1920px/.test(html))
    assert.ok(html.includes('>EN<') && html.includes('>PT<'))
    assert.ok(html.includes('en.mp3') && html.includes('pt.mp3'))
    assert.ok(html.includes('type="range"') && html.includes('max="90.25"'))
    assert.ok(html.includes('requestAnimationFrame'))
    assert.ok(html.includes('renderFrame') === false, 'o pai não chama renderFrame: quem desenha é o quadro')
    assert.ok(html.includes(':root{--bg:#131010}'), 'fundo vem dos tokens do design system')
})

test('trilha do preview: uma fala por cena no instante voiceAt, sem drone, cortada no total', () => {
    const args = previewAudioArgs({
        scenes: [{ id: 'a' }, { id: 'b' }],
        timeline: { total: 10, scenes: [{ voiceAt: 1.4 }, { voiceAt: 5.25 }] },
        voiceDir: '/voz/en',
        output: '/out/en.mp3',
    })
    const filter = args[args.indexOf('-filter_complex') + 1]
    assert.ok(filter.includes('adelay=1400:all=1'))
    assert.ok(filter.includes('adelay=5250:all=1'))
    assert.ok(filter.includes('amix=inputs=2'))
    assert.ok(!args.some((arg) => arg.includes('sine')) && !filter.includes('lowpass'), 'sem drone no preview')
    assert.equal(args[args.indexOf('-t') + 1], '10.000')
    assert.ok(args.includes(join('/voz/en', 'a.wav')) && args.includes(join('/voz/en', 'b.wav')))
    assert.equal(args.at(-1), '/out/en.mp3')
})

test('o quadro pinta o instante da URL logo após o setup, antes de avisar ready (sem depender de um frame posterior)', () => {
    const html = frameHtml({ videoHtml, data })
    assert.match(html, /setup\(PREVIEW_DATA\)\.then\(\(\) => \{\s*renderFrame\(0\)\s*parent\.postMessage\(\{ type: 'ready' \}, '\*'\)/)
})
