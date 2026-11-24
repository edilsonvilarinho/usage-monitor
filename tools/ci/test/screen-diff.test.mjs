import { test } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { deflateSync, crc32 } from 'node:zlib'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { compareDirs, comparePixels, decodePng, encodePng, parseArgs, renderHtml, renderMarkdown } from '../screen-diff.mjs'

const SCRIPT = fileURLToPath(new URL('../screen-diff.mjs', import.meta.url))
const SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])

function fixturePixels(width, height) {
    return Uint8Array.from({ length: width * height * 4 }, (_, i) => (i * 37 + (i >> 3) * 11) & 0xff)
}

// Cópia com o canal vermelho do pixel (x, y) alterado.
function touch(pixels, width, x, y) {
    const copy = Uint8Array.from(pixels)
    copy[(y * width + x) * 4] = (copy[(y * width + x) * 4] + 1) & 0xff
    return copy
}

function paeth(a, b, c) {
    const p = a + b - c
    const pa = Math.abs(p - a)
    const pb = Math.abs(p - b)
    const pc = Math.abs(p - c)
    if (pa <= pb && pa <= pc) return a
    return pb <= pc ? b : c
}

// Filtra uma linha como um codificador PNG faria; o decodificador tem de desfazer.
function filterLine(type, line, previous, bpp) {
    const out = Buffer.alloc(line.length)
    for (let i = 0; i < line.length; i++) {
        const a = i >= bpp ? line[i - bpp] : 0
        const b = previous[i]
        const c = i >= bpp ? previous[i - bpp] : 0
        const predictor = [0, a, b, Math.floor((a + b) / 2), paeth(a, b, c)][type]
        out[i] = (line[i] - predictor) & 0xff
    }
    return out
}

function chunk(type, data) {
    const head = Buffer.alloc(8)
    head.writeUInt32BE(data.length, 0)
    head.write(type, 4, 'latin1')
    const crc = Buffer.alloc(4)
    crc.writeUInt32BE(crc32(Buffer.concat([head.subarray(4), data])) >>> 0, 0)
    return Buffer.concat([head, data, crc])
}

function pngFromRaw(width, height, colorType, raw, { depth = 8, interlace = 0 } = {}) {
    const header = Buffer.alloc(13)
    header.writeUInt32BE(width, 0)
    header.writeUInt32BE(height, 4)
    header.set([depth, colorType, 0, 0, interlace], 8)
    return Buffer.concat([SIGNATURE, chunk('IHDR', header), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))])
}

// PNG de teste: `pixels` e RGBA; `filter(y)` escolhe o filtro de cada linha.
function makePng(width, height, pixels, { colorType = 6, depth = 8, interlace = 0, filter = (y) => y % 5 } = {}) {
    const channels = colorType === 6 ? 4 : 3
    const stride = width * channels
    const raw = Buffer.alloc((stride + 1) * height)
    let previous = Buffer.alloc(stride)
    for (let y = 0; y < height; y++) {
        const line = Buffer.alloc(stride)
        for (let x = 0; x < width; x++) {
            for (let c = 0; c < channels; c++) line[x * channels + c] = pixels[(y * width + x) * 4 + c]
        }
        const type = filter(y)
        raw[y * (stride + 1)] = type
        filterLine(type, line, previous, channels).copy(raw, y * (stride + 1) + 1)
        previous = line
    }
    return pngFromRaw(width, height, colorType, raw, { depth, interlace })
}

test('decodePng: RGB e RGBA com os cinco filtros voltam aos pixels originais', () => {
    const pixels = fixturePixels(4, 5)
    const rgb = Uint8Array.from(pixels)
    for (let i = 3; i < rgb.length; i += 4) rgb[i] = 255
    assert.deepEqual(decodePng(makePng(4, 5, pixels, { colorType: 6, filter: (y) => y })), { width: 4, height: 5, rgba: pixels })
    assert.deepEqual(decodePng(makePng(4, 5, pixels, { colorType: 2, filter: (y) => y })), { width: 4, height: 5, rgba: rgb })
})

test('decodePng: recusa tipo de cor, profundidade, entrelacamento, assinatura e filtro desconhecido', () => {
    const pixels = fixturePixels(2, 2)
    assert.throws(() => decodePng(makePng(2, 2, pixels, { colorType: 0 })), /tipo de cor 0/)
    assert.throws(() => decodePng(makePng(2, 2, pixels, { depth: 16 })), /profundidade 16/)
    assert.throws(() => decodePng(makePng(2, 2, pixels, { interlace: 1 })), /entrelacamento/)
    assert.throws(() => decodePng(Buffer.from('nao e png')), /nao e PNG/)
    assert.throws(() => decodePng(pngFromRaw(1, 1, 6, Buffer.from([5, 0, 0, 0, 0]))), /filtro 5/)
})

test('encodePng: ida e volta preserva largura, altura e pixels', () => {
    const pixels = fixturePixels(3, 4)
    assert.deepEqual(decodePng(encodePng(3, 4, pixels)), { width: 3, height: 4, rgba: pixels })
})

test('comparePixels: conta pixels e devolve a caixa exata do bloco alterado', () => {
    const scene = fixturePixels(6, 5)
    let changed = scene
    for (let y = 2; y <= 4; y++) for (let x = 1; x <= 2; x++) changed = touch(changed, 6, x, y)
    assert.deepEqual(comparePixels({ width: 6, height: 5, rgba: scene }, { width: 6, height: 5, rgba: changed }), {
        changed: 6,
        box: { x: 1, y: 2, width: 2, height: 3 },
        sameSize: true,
    })
    assert.deepEqual(comparePixels({ width: 6, height: 5, rgba: scene }, { width: 6, height: 5, rgba: scene }), {
        changed: 0,
        box: null,
        sameSize: true,
    })
    const other = comparePixels({ width: 2, height: 2, rgba: new Uint8Array(16) }, { width: 3, height: 2, rgba: new Uint8Array(24) })
    assert.equal(other.sameSize, false)
    assert.equal(other.box, null)
    assert.ok(other.changed > 0)
})

test('compareDirs: estados em ordem de nome; GIF so por bytes; mesmo pixel com outro filtro conta igual', () => {
    const scene = fixturePixels(6, 5)
    const oneChanged = touch(scene, 6, 1, 2)
    const base = new Map([
        ['a.png', makePng(6, 5, scene)],
        ['b.png', makePng(6, 5, scene)],
        ['c.gif', Buffer.from('GIF89a-antes')],
        ['d.png', makePng(6, 5, scene)],
    ])
    const head = new Map([
        ['a.png', makePng(6, 5, scene, { filter: () => 0 })],
        ['b.png', makePng(6, 5, oneChanged)],
        ['c.gif', Buffer.from('GIF89a-depois')],
        ['e.png', makePng(6, 5, scene)],
    ])
    assert.deepEqual(compareDirs(base, head), [
        { name: 'a.png', status: 'igual', changed: 0, box: null },
        { name: 'b.png', status: 'mudou', changed: 1, box: { x: 1, y: 2, width: 1, height: 1 } },
        { name: 'c.gif', status: 'mudou', changed: null, box: null },
        { name: 'd.png', status: 'removida', changed: null, box: null },
        { name: 'e.png', status: 'nova', changed: null, box: null },
    ])
})

test('renderMarkdown: uma linha por tela com estado, contagem e caixa', () => {
    const markdown = renderMarkdown([
        { name: 'a.png', status: 'igual', changed: 0, box: null },
        { name: 'b.png', status: 'mudou', changed: 1, box: { x: 1, y: 2, width: 1, height: 1 } },
        { name: 'd.png', status: 'removida', changed: null, box: null },
    ])
    assert.match(markdown, /\| `b\.png` \| mudou \| 1 \| 1,2 1x1 \|/)
    assert.match(markdown, /\| `d\.png` \| removida \| -- \| -- \|/)
})

test('renderHtml: escapa o nome e so aponta as imagens que existem', () => {
    const html = renderHtml([
        { name: '<x>.png', status: 'nova', changed: null, box: null },
        { name: 'b.png', status: 'mudou', changed: 1, box: { x: 1, y: 2, width: 1, height: 1 } },
    ])
    assert.match(html, /src="after\/%3Cx%3E\.png"/)
    assert.doesNotMatch(html, /before\/%3Cx/)
    assert.doesNotMatch(html, /<x>/)
    assert.match(html, /src="diff\/b\.png"/)
})

test('parseArgs: exige --base, --head e --out', () => {
    assert.throws(() => parseArgs(['--base', 'x', '--head', 'y']), /--out e obrigatorio/)
    assert.deepEqual(parseArgs(['--base', 'x', '--head', 'y', '--out', 'z']), { base: 'x', head: 'y', out: 'z' })
})

function runCli(args) {
    return spawnSync(process.execPath, [SCRIPT, ...args], { encoding: 'utf8', env: { ...process.env, GITHUB_STEP_SUMMARY: '' } })
}

test('CLI: 2 para diretorio ausente, vazio ou PNG ilegivel; 0 com tela alterada, gravando a saida', () => {
    const root = mkdtempSync(join(tmpdir(), 'screen-diff-'))
    try {
        const base = join(root, 'base')
        const head = join(root, 'head')
        const out = join(root, 'out')
        mkdirSync(base)
        mkdirSync(head)
        mkdirSync(join(root, 'vazio'))
        mkdirSync(join(root, 'ruim'))
        writeFileSync(join(root, 'ruim', 'a.png'), Buffer.from('nao e png'))
        const scene = fixturePixels(6, 5)
        writeFileSync(join(base, 'a.png'), makePng(6, 5, scene))
        writeFileSync(join(head, 'a.png'), makePng(6, 5, touch(scene, 6, 3, 3)))

        const missing = runCli(['--base', join(root, 'nao-existe'), '--head', head, '--out', out])
        assert.equal(missing.status, 2)
        assert.match(missing.stderr, /diretorio ausente/)

        const empty = runCli(['--base', join(root, 'vazio'), '--head', head, '--out', out])
        assert.equal(empty.status, 2)
        assert.match(empty.stderr, /nenhuma imagem/)

        const broken = runCli(['--base', join(root, 'ruim'), '--head', head, '--out', out])
        assert.equal(broken.status, 2)
        assert.match(broken.stderr, /a\.png/)

        const ok = runCli(['--base', base, '--head', head, '--out', out])
        assert.equal(ok.status, 0)
        assert.match(ok.stdout, /\| `a\.png` \| mudou \| 1 \| 3,3 1x1 \|/)
        for (const path of ['index.html', 'before/a.png', 'after/a.png', 'diff/a.png']) {
            assert.ok(existsSync(join(out, path)), `falta ${path}`)
        }
    } finally {
        rmSync(root, { recursive: true, force: true })
    }
})
