import { deflateSync as deflateForTest } from 'node:zlib'
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { deflateSync, crc32 } from 'node:zlib'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { compareDirs, comparePixels, decodePng, diffImage, encodePng, parseArgs, renderHtml, renderMarkdown } from '../screen-diff.mjs'

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

// Um canal alterado; os outros tres do pixel ficam como estavam.
function touchChannel(pixels, width, x, y, channel) {
    const copy = Uint8Array.from(pixels)
    const o = (y * width + x) * 4 + channel
    copy[o] = (copy[o] + 1) & 0xff
    return copy
}

test('comparePixels: cada canal (R, G, B, A) isolado conta como um pixel alterado', () => {
    const scene = fixturePixels(4, 3)
    for (let channel = 0; channel < 4; channel++) {
        const changed = touchChannel(scene, 4, 2, 1, channel)
        assert.deepEqual(comparePixels({ width: 4, height: 3, rgba: scene }, { width: 4, height: 3, rgba: changed }), {
            changed: 1,
            box: { x: 2, y: 1, width: 1, height: 1 },
            sameSize: true,
        }, `canal ${channel}`)
    }
})

test('comparePixels: canto superior esquerdo e inferior direito fecham a caixa inteira', () => {
    const scene = fixturePixels(5, 4)
    const changed = touchChannel(touchChannel(scene, 5, 0, 0, 0), 5, 4, 3, 0)
    assert.deepEqual(comparePixels({ width: 5, height: 4, rgba: scene }, { width: 5, height: 4, rgba: changed }), {
        changed: 2,
        box: { x: 0, y: 0, width: 5, height: 4 },
        sameSize: true,
    })
})

test('diffImage: alterado sai magenta puro; inalterado sai cinza pela luminancia', () => {
    const a = { width: 4, height: 1, rgba: Uint8Array.from([200, 0, 0, 255, 255, 255, 255, 255, 0, 0, 200, 255, 0, 200, 0, 255]) }
    const b = { width: 4, height: 1, rgba: Uint8Array.from([200, 0, 0, 255, 0, 0, 0, 255, 0, 0, 200, 255, 0, 200, 0, 255]) }
    assert.deepEqual([...diffImage(a, b).rgba], [197, 197, 197, 255, 255, 0, 255, 255, 185, 185, 185, 255, 214, 214, 214, 255])
})

test('decodePng: Paeth com empates (alfabeto de 4 valores) e os cinco filtros voltam aos pixels originais', () => {
    let seed = 7
    const pixels = Uint8Array.from({ length: 9 * 7 * 4 }, () => {
        seed = (Math.imul(seed, 1103515245) + 12345) >>> 0
        return (seed >>> 16) % 4
    })
    assert.deepEqual(decodePng(makePng(9, 7, pixels, { filter: (y) => y % 5 })), { width: 9, height: 7, rgba: pixels })
})

test('decodePng: recusa PNG truncado, sem IEND, dimensao zero e dados de tamanho errado', () => {
    const png = makePng(2, 2, fixturePixels(2, 2))
    assert.throws(() => decodePng(png.subarray(0, png.length - 5)), /PNG truncado/)
    assert.throws(() => decodePng(png.subarray(0, png.length - 12)), /sem IHDR, IDAT ou IEND/)
    assert.throws(() => decodePng(pngFromRaw(0, 2, 6, Buffer.alloc(0))), /dimensao zero/)
    assert.throws(() => decodePng(pngFromRaw(2, 0, 6, Buffer.alloc(0))), /dimensao zero/)
    assert.throws(() => decodePng(pngFromRaw(1, 1, 6, Buffer.from([0, 0, 0, 0]))), /dados de imagem com tamanho errado/)
})

test('encodePng: CRC de cada chunk confere com zlib.crc32; ordem IHDR, IDAT, IEND', () => {
    const png = encodePng(2, 2, fixturePixels(2, 2))
    const types = []
    let offset = 8
    while (offset < png.length) {
        const length = png.readUInt32BE(offset)
        const type = png.toString('latin1', offset + 4, offset + 8)
        assert.equal(png.readUInt32BE(offset + 8 + length), crc32(png.subarray(offset + 4, offset + 8 + length)), `CRC de ${type}`)
        types.push(type)
        offset += length + 12
    }
    assert.deepEqual(types, ['IHDR', 'IDAT', 'IEND'])
})

test('compareDirs: extensao fora de png e gif vira nao comparado, com o motivo', () => {
    assert.deepEqual(compareDirs(new Map([['notas.txt', Buffer.from('a')]]), new Map([['notas.txt', Buffer.from('b')]])), [
        { name: 'notas.txt', status: 'não comparado', changed: null, box: null, reason: 'extensão .txt fora de png e gif' },
    ])
})

test('compareDirs: PNG que nao decodifica vira nao comparado e diz se o problema e antes ou depois', () => {
    const scene = fixturePixels(2, 2)
    assert.deepEqual(compareDirs(new Map([['a.png', Buffer.from('nao e png')]]), new Map([['a.png', makePng(2, 2, scene)]])), [
        { name: 'a.png', status: 'não comparado', changed: null, box: null, reason: 'antes: nao e PNG' },
    ])
    const after = compareDirs(new Map([['a.png', makePng(2, 2, scene)]]), new Map([['a.png', Buffer.from('nao e png')]]))
    assert.equal(after[0].reason, 'depois: nao e PNG')
})

test('renderMarkdown: nao comparado aparece com o motivo na linha e na contagem', () => {
    const markdown = renderMarkdown([{ name: 'notas.txt', status: 'não comparado', changed: null, box: null, reason: 'extensão .txt fora de png e gif' }])
    assert.match(markdown, /\| `notas\.txt` \| não comparado \(extensão \.txt fora de png e gif\) \| -- \| -- \|/)
    assert.match(markdown, /1 não comparado/)
})

test('CLI: nao comparado sai 2, grava o relatorio e nao copia o arquivo para a galeria', () => {
    const root = mkdtempSync(join(tmpdir(), 'screen-diff-'))
    try {
        const base = join(root, 'base')
        const head = join(root, 'head')
        const out = join(root, 'out')
        mkdirSync(base)
        mkdirSync(head)
        writeFileSync(join(base, 'notas.txt'), 'a')
        writeFileSync(join(head, 'notas.txt'), 'b')
        const result = runCli(['--base', base, '--head', head, '--out', out])
        assert.equal(result.status, 2)
        assert.match(result.stderr, /notas\.txt: extensão \.txt fora de png e gif/)
        assert.match(result.stdout, /\| `notas\.txt` \| não comparado/)
        assert.ok(existsSync(join(out, 'index.html')))
        assert.ok(!existsSync(join(out, 'after', 'notas.txt')))
    } finally {
        rmSync(root, { recursive: true, force: true })
    }
})

// Testes de comportamento dos sobreviventes da mutacao (#676, A05f).
function rawPng(chunks) {
    const parts = [Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])]
    for (const [type, data] of chunks) {
        const head = Buffer.alloc(8)
        head.writeUInt32BE(data.length, 0)
        head.write(type, 4, 'latin1')
        parts.push(head, data, Buffer.alloc(4))
    }
    return Buffer.concat(parts)
}

function ihdrData(width, height, depth, colorType, { compression = 0, filter = 0, interlace = 0 } = {}) {
    const data = Buffer.alloc(13)
    data.writeUInt32BE(width, 0)
    data.writeUInt32BE(height, 4)
    data.set([depth, colorType, compression, filter, interlace], 8)
    return data
}

function pngFromHeader(header) {
    const pixels = deflateForTest(Buffer.from([0, 1, 2, 3, 4]))
    return rawPng([['IHDR', header], ['IDAT', pixels], ['IEND', Buffer.alloc(0)]])
}

test('decodePng: sem IDAT recusa com a mensagem de IHDR, IDAT ou IEND', () => {
    const noIdat = rawPng([['IHDR', ihdrData(1, 1, 8, 6)], ['IEND', Buffer.alloc(0)]])
    assert.throws(() => decodePng(noIdat), /PNG sem IHDR, IDAT ou IEND/)
})

test('decodePng: sem IEND recusa mesmo com IHDR e IDAT completos', () => {
    const noEnd = rawPng([['IHDR', ihdrData(1, 1, 8, 6)], ['IDAT', deflateForTest(Buffer.from([0, 1, 2, 3, 4]))]])
    assert.throws(() => decodePng(noEnd), /PNG sem IHDR, IDAT ou IEND/)
})

test('decodePng: profundidade 16 e recusada com a mensagem de 8 bits', () => {
    assert.throws(() => decodePng(pngFromHeader(ihdrData(1, 1, 16, 6))), /profundidade 16 nao suportada; so 8 bits/)
})

test('decodePng: tipo de cor fora de RGB e RGBA e recusado com a lista suportada', () => {
    assert.throws(() => decodePng(pngFromHeader(ihdrData(1, 1, 8, 0))), /tipo de cor 0 nao suportado; so RGB \(2\) e RGBA \(6\)/)
    assert.throws(() => decodePng(pngFromHeader(ihdrData(1, 1, 8, 3))), /tipo de cor 3 nao suportado; so RGB \(2\) e RGBA \(6\)/)
})

test('decodePng: compressao, filtro ou entrelacamento fora do padrao sao recusados um a um', () => {
    const variants = [{ compression: 1 }, { filter: 1 }, { interlace: 1 }]
    for (const variant of variants) {
        assert.throws(() => decodePng(pngFromHeader(ihdrData(1, 1, 8, 6, variant))), /compressao, filtro ou entrelacamento fora do padrao/)
    }
})

test('encodePng: chunk IEND sai com tamanho zero', () => {
    const out = encodePng(1, 1, new Uint8Array(4))
    assert.equal(out.subarray(out.length - 12).readUInt32BE(0), 0)
})

test('comparePixels: tamanhos diferentes contam o maior produto quando o primeiro e o grande', () => {
    const big = { width: 6, height: 6, rgba: new Uint8Array(144) }
    const small = { width: 2, height: 2, rgba: new Uint8Array(16) }
    assert.equal(comparePixels(big, small).changed, 36)
})

test('comparePixels: tamanhos diferentes contam o maior produto quando o segundo e o grande', () => {
    const big = { width: 6, height: 6, rgba: new Uint8Array(144) }
    const small = { width: 2, height: 2, rgba: new Uint8Array(16) }
    assert.equal(comparePixels(small, big).changed, 36)
})

test('compareDirs: bytes iguais viram igual com zero pixels alterados', () => {
    const bytes = Buffer.from('GIF89a')
    const rows = compareDirs(new Map([['a.gif', bytes]]), new Map([['a.gif', bytes]]))
    assert.deepEqual(rows, [{ name: 'a.gif', status: 'igual', changed: 0, box: null }])
})

test('renderMarkdown: o resumo conta cada estado uma vez', () => {
    const markdown = renderMarkdown([
        { name: 'a.png', status: 'mudou', changed: 3, box: null },
        { name: 'b.png', status: 'igual', changed: 0, box: null },
    ])
    assert.match(markdown, /\*\*1 mudou\*\*, 1 igual, 0 nova, 0 removida\./)
})

test('renderMarkdown: sem tela nao comparada, o resumo nao cita o estado', () => {
    const markdown = renderMarkdown([{ name: 'a.png', status: 'igual', changed: 0, box: null }])
    assert.doesNotMatch(markdown, /não comparado/)
})

test('renderHtml: tela sem numero de pixels mostra -- e nao null', () => {
    const html = renderHtml([{ name: 'a.png', status: 'nova', changed: null, box: null }])
    assert.match(html, /nova -- -- pixels/)
})

test('parseArgs: flag sem -- e chave desconhecida sao recusadas como argumento desconhecido', () => {
    assert.throws(() => parseArgs(['--nope', '1']), /argumento desconhecido ou sem valor: --nope/)
    assert.throws(() => parseArgs(['base', 'v']), /argumento desconhecido ou sem valor: base/)
})

test('parseArgs: flag sem valor e recusada como sem valor', () => {
    assert.throws(() => parseArgs(['--base']), /argumento desconhecido ou sem valor: --base/)
})
