#!/usr/bin/env node
// Antes e depois das telas no PR (#651). Compara as capturas de duas geracoes
// (base e PR): igualdade de bytes primeiro, e so o PNG diferente e decodificado.
// GIF e comparado so por bytes. Informativo: nunca falha por causa de tela.
//
// Uso:
//   node tools/ci/screen-diff.mjs --base <dir> --head <dir> --out <dir>
//
// Saida 0 com ou sem tela alterada; 2 em erro de uso, diretorio ausente ou
// vazio, ou PNG que nao decodifica.
import { appendFileSync, existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { extname, join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { deflateSync, inflateSync, crc32 } from 'node:zlib'

const SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
// Tipo de cor do PNG -> bytes por pixel: so RGB (2) e RGBA (6), 8 bits.
const CHANNELS = { 2: 3, 6: 4 }
const KINDS = new Set(['.png', '.gif'])

export function decodePng(buffer) {
    if (!buffer.subarray(0, 8).equals(SIGNATURE)) throw new Error('nao e PNG')
    let header = null
    let ended = false
    const idat = []
    let offset = 8
    while (offset < buffer.length && !ended) {
        if (offset + 12 > buffer.length) throw new Error('PNG truncado')
        const length = buffer.readUInt32BE(offset)
        const type = buffer.toString('latin1', offset + 4, offset + 8)
        const data = buffer.subarray(offset + 8, offset + 8 + length)
        if (data.length !== length) throw new Error('PNG truncado')
        if (type === 'IHDR') header = parseHeader(data)
        else if (type === 'IDAT') idat.push(data)
        else if (type === 'IEND') ended = true
        offset += length + 12
    }
    if (!header || idat.length === 0 || !ended) throw new Error('PNG sem IHDR, IDAT ou IEND')
    const { width, height, bpp } = header
    const raw = inflateSync(Buffer.concat(idat))
    const stride = width * bpp
    if (raw.length !== height * (stride + 1)) throw new Error('dados de imagem com tamanho errado')
    const rgba = new Uint8Array(width * height * 4)
    let previous = new Uint8Array(stride)
    for (let y = 0; y < height; y++) {
        const start = y * (stride + 1)
        const line = unfilterLine(raw[start], raw.subarray(start + 1, start + 1 + stride), previous, bpp)
        for (let x = 0; x < width; x++) {
            const o = (y * width + x) * 4
            rgba[o] = line[x * bpp]
            rgba[o + 1] = line[x * bpp + 1]
            rgba[o + 2] = line[x * bpp + 2]
            rgba[o + 3] = bpp === 4 ? line[x * bpp + 3] : 255
        }
        previous = line
    }
    return { width, height, rgba }
}

function parseHeader(data) {
    if (data.length !== 13) throw new Error('IHDR com tamanho errado')
    const width = data.readUInt32BE(0)
    const height = data.readUInt32BE(4)
    const [depth, colorType, compression, filter, interlace] = data.subarray(8, 13)
    if (depth !== 8) throw new Error(`profundidade ${depth} nao suportada; so 8 bits`)
    if (!(colorType in CHANNELS)) throw new Error(`tipo de cor ${colorType} nao suportado; so RGB (2) e RGBA (6)`)
    if (compression !== 0 || filter !== 0 || interlace !== 0) throw new Error('PNG com compressao, filtro ou entrelacamento fora do padrao')
    if (width === 0 || height === 0) throw new Error('PNG com dimensao zero')
    return { width, height, bpp: CHANNELS[colorType] }
}

function unfilterLine(type, bytes, previous, bpp) {
    if (type > 4) throw new Error(`filtro ${type} desconhecido`)
    const out = new Uint8Array(bytes.length)
    for (let i = 0; i < bytes.length; i++) {
        const left = i >= bpp ? out[i - bpp] : 0
        const up = previous[i]
        const upLeft = i >= bpp ? previous[i - bpp] : 0
        const predictor = [0, left, up, (left + up) >> 1, paeth(left, up, upLeft)][type]
        out[i] = (bytes[i] + predictor) & 0xff
    }
    return out
}

function paeth(a, b, c) {
    const p = a + b - c
    const pa = Math.abs(p - a)
    const pb = Math.abs(p - b)
    const pc = Math.abs(p - c)
    if (pa <= pb && pa <= pc) return a
    return pb <= pc ? b : c
}

// PNG de 8 bits RGBA sem entrelacamento, filtro 0 em todas as linhas.
export function encodePng(width, height, rgba) {
    const stride = width * 4
    const raw = Buffer.alloc((stride + 1) * height)
    for (let y = 0; y < height; y++) raw.set(rgba.subarray(y * stride, (y + 1) * stride), y * (stride + 1) + 1)
    const header = Buffer.alloc(13)
    header.writeUInt32BE(width, 0)
    header.writeUInt32BE(height, 4)
    header.set([8, 6, 0, 0, 0], 8)
    return Buffer.concat([SIGNATURE, chunk('IHDR', header), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))])
}

function chunk(type, data) {
    const out = Buffer.alloc(data.length + 12)
    out.writeUInt32BE(data.length, 0)
    out.write(type, 4, 'latin1')
    data.copy(out, 8)
    out.writeUInt32BE(crc32(out.subarray(4, 8 + data.length)) >>> 0, 8 + data.length)
    return out
}

// Tamanhos diferentes: tudo conta como alterado e nao ha caixa.
export function comparePixels(a, b) {
    if (a.width !== b.width || a.height !== b.height) {
        return { changed: Math.max(a.width * a.height, b.width * b.height), box: null, sameSize: false }
    }
    let changed = 0
    let minX = Infinity
    let minY = Infinity
    let maxX = -1
    let maxY = -1
    for (let y = 0; y < a.height; y++) {
        for (let x = 0; x < a.width; x++) {
            if (!differs(a.rgba, b.rgba, (y * a.width + x) * 4)) continue
            changed++
            minX = Math.min(minX, x)
            minY = Math.min(minY, y)
            maxX = Math.max(maxX, x)
            maxY = Math.max(maxY, y)
        }
    }
    const box = changed === 0 ? null : { x: minX, y: minY, width: maxX - minX + 1, height: maxY - minY + 1 }
    return { changed, box, sameSize: true }
}

function differs(left, right, o) {
    return left[o] !== right[o] || left[o + 1] !== right[o + 1] || left[o + 2] !== right[o + 2] || left[o + 3] !== right[o + 3]
}

// Alterado em magenta; o resto, cinza claro, para a caixa se destacar.
export function diffImage(a, b) {
    const rgba = new Uint8Array(a.rgba.length)
    for (let o = 0; o < rgba.length; o += 4) {
        if (differs(a.rgba, b.rgba, o)) {
            rgba.set([255, 0, 255, 255], o)
            continue
        }
        const luminance = a.rgba[o] * 0.3 + a.rgba[o + 1] * 0.59 + a.rgba[o + 2] * 0.11
        const gray = Math.round(luminance * 0.3 + 178.5)
        rgba.set([gray, gray, gray, 255], o)
    }
    return { width: a.width, height: a.height, rgba }
}

// Arquivos em `Map` de nome -> Buffer. Igualdade de bytes antes de decodificar.
export function compareDirs(baseFiles, headFiles) {
    const names = [...new Set([...baseFiles.keys(), ...headFiles.keys()])].sort()
    return names.map((name) => compareFile(name, baseFiles.get(name), headFiles.get(name)))
}

function compareFile(name, base, head) {
    if (!base) return { name, status: 'nova', changed: null, box: null }
    if (!head) return { name, status: 'removida', changed: null, box: null }
    if (base.equals(head)) return { name, status: 'igual', changed: 0, box: null }
    if (extname(name).toLowerCase() !== '.png') return { name, status: 'mudou', changed: null, box: null }
    const result = comparePixels(decodePng(base), decodePng(head))
    const status = result.sameSize && result.changed === 0 ? 'igual' : 'mudou'
    return { name, status, changed: result.changed, box: result.box }
}

function boxText(box) {
    return box ? `${box.x},${box.y} ${box.width}x${box.height}` : '--'
}

export function renderMarkdown(rows) {
    const count = (status) => rows.filter((row) => row.status === status).length
    const lines = [
        '## Telas antes e depois',
        '',
        `**${count('mudou')} mudou**, ${count('igual')} igual, ${count('nova')} nova, ${count('removida')} removida.`,
        '',
        '| Tela | Estado | Pixels alterados | Caixa |',
        '|---|---|---|---|',
    ]
    for (const row of rows) {
        const name = row.name.replaceAll('|', '\\|')
        lines.push(`| \`${name}\` | ${row.status} | ${row.changed ?? '--'} | ${boxText(row.box)} |`)
    }
    return `${lines.join('\n')}\n`
}

function escapeHtml(text) {
    return text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;')
}

// Os caminhos sao os que `writeOutput` grava: before/, after/ e diff/ (so com caixa).
export function renderHtml(rows) {
    const sections = rows.map((row) => {
        const file = encodeURIComponent(row.name)
        const panels = [
            ['Antes', row.status === 'nova' ? null : `before/${file}`],
            ['Depois', row.status === 'removida' ? null : `after/${file}`],
            ['Diferenca', row.box === null ? null : `diff/${file}`],
        ]
        const figures = panels
            .filter(([, src]) => src !== null)
            .map(([label, src]) => `<figure><figcaption>${label}</figcaption><img src="${escapeHtml(src)}" alt=""></figure>`)
        const title = escapeHtml(row.name)
        return `<section><h2>${title}</h2><p>${escapeHtml(row.status)} -- ${row.changed ?? '--'} pixels</p><div class="trio">${figures.join('')}</div></section>`
    })
    return `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Telas antes e depois</title>
<style>
body { font-family: system-ui, sans-serif; margin: 24px; background: #f4f4f6; color: #1b1b1f; }
.trio { display: flex; flex-wrap: wrap; gap: 16px; }
figure { margin: 0; max-width: 100%; }
figcaption { font-size: 13px; margin-bottom: 4px; }
img { max-width: 560px; border: 1px solid #c8c8d0; background: #fff; }
section { margin-bottom: 32px; }
</style>
</head>
<body>
<h1>Telas antes e depois</h1>
${sections.join('\n')}
</body>
</html>
`
}

export function parseArgs(argv) {
    const options = { base: null, head: null, out: null }
    for (let index = 0; index < argv.length; index += 2) {
        const flag = argv[index]
        const value = argv[index + 1]
        const key = flag?.replace(/^--/, '')
        if (!flag?.startsWith('--') || !Object.hasOwn(options, key) || value === undefined) {
            throw new Error(`argumento desconhecido ou sem valor: ${flag}`)
        }
        options[key] = value
    }
    for (const [key, value] of Object.entries(options)) {
        if (!value) throw new Error(`--${key} e obrigatorio`)
    }
    return options
}

// Le as imagens do diretorio e confere que cada PNG decodifica.
function readScreens(dir) {
    if (!existsSync(dir) || !statSync(dir).isDirectory()) throw new Error(`diretorio ausente: ${dir}`)
    const files = new Map()
    for (const name of readdirSync(dir).sort()) {
        const path = join(dir, name)
        if (!KINDS.has(extname(name).toLowerCase()) || !statSync(path).isFile()) continue
        const bytes = readFileSync(path)
        try {
            if (extname(name).toLowerCase() === '.png') decodePng(bytes)
        } catch (error) {
            throw new Error(`${name}: ${error.message}`)
        }
        files.set(name, bytes)
    }
    if (files.size === 0) throw new Error(`nenhuma imagem em ${dir}`)
    return files
}

function writeOutput(out, rows, base, head) {
    for (const dir of ['before', 'after', 'diff']) mkdirSync(join(out, dir), { recursive: true })
    for (const row of rows) {
        if (base.has(row.name)) writeFileSync(join(out, 'before', row.name), base.get(row.name))
        if (head.has(row.name)) writeFileSync(join(out, 'after', row.name), head.get(row.name))
        if (row.box !== null) {
            const diff = diffImage(decodePng(base.get(row.name)), decodePng(head.get(row.name)))
            writeFileSync(join(out, 'diff', row.name), encodePng(diff.width, diff.height, diff.rgba))
        }
    }
    writeFileSync(join(out, 'index.html'), renderHtml(rows))
}

function main() {
    const options = parseArgs(process.argv.slice(2))
    const base = readScreens(options.base)
    const head = readScreens(options.head)
    const rows = compareDirs(base, head)
    writeOutput(options.out, rows, base, head)
    const text = renderMarkdown(rows)
    process.stdout.write(text)
    if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${text}\n`)
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
    try {
        main()
    } catch (error) {
        console.error(error.message)
        process.exitCode = 2
    }
}
