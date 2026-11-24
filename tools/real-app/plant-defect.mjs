// Planta um defeito conhecido no fonte da HUD, para provar que a fita de CI o pega.
// Uso: node tools/real-app/plant-defect.mjs --defect <nome> [--root <dir>]   (aplica as edicoes)
//      node tools/real-app/plant-defect.mjs --defect <nome> --expect <cenario> (imprime o veredito esperado)
// Saida: aplica tudo ou nada. Defeito desconhecido, find com 0 ou 2+ ocorrencias, ou gravacao fora do CI: exit 2.
// Sem --root, grava so com GITHUB_ACTIONS=true (no diretorio atual).
import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const DEFECTS_PATH = fileURLToPath(new URL('./defects.json', import.meta.url))
const USAGE = 'uso: node tools/real-app/plant-defect.mjs --defect <nome> [--root <dir> | --expect <cenario>]'
const FLAGS = ['--defect', '--root', '--expect']

// Troca uma unica ocorrencia de find por replace. find e replace usam LF; texto CRLF mantem o fim de linha do arquivo.
// A troca e por fatiamento e concatenacao, sem padrao de substituicao: $& e $1 saem literais.
export function plantEdit(text, find, replace) {
    if (find === '') throw new Error('find vazio')
    const lineEnding = text.includes('\r\n') ? '\r\n' : '\n'
    const needle = find.replace(/\n/g, lineEnding)
    const insert = replace.replace(/\n/g, lineEnding)
    const occurrences = text.split(needle).length - 1
    if (occurrences !== 1) throw new Error(`find ocorre ${occurrences} vezes (esperado 1): ${JSON.stringify(find)}`)
    const at = text.indexOf(needle)
    return text.slice(0, at) + insert + text.slice(at + needle.length)
}

function parseArgs(argv) {
    const flags = {}
    for (let index = 0; index < argv.length; index += 2) {
        if (!FLAGS.includes(argv[index]) || index + 1 >= argv.length) throw new Error(USAGE)
        flags[argv[index]] = argv[index + 1]
    }
    if (flags['--defect'] === undefined) throw new Error(USAGE)
    return flags
}

function loadDefect(name) {
    const defects = JSON.parse(readFileSync(DEFECTS_PATH, 'utf8'))
    if (!Object.hasOwn(defects, name)) throw new Error(`defeito desconhecido: ${name}`)
    return defects[name]
}

// Calcula o texto final de cada arquivo antes de gravar qualquer um: uma edicao ruim nao deixa arquivo pela metade.
function planEdits(root, edits) {
    const contents = new Map()
    for (const edit of edits) {
        const current = contents.has(edit.file) ? contents.get(edit.file) : readFileSync(join(root, edit.file), 'utf8')
        contents.set(edit.file, plantEdit(current, edit.find, edit.replace))
    }
    return contents
}

export function main(argv, env = process.env) {
    const flags = parseArgs(argv)
    const defect = loadDefect(flags['--defect'])
    if (flags['--expect'] !== undefined) {
        const expect = defect.expect ?? {}
        console.log(Object.hasOwn(expect, flags['--expect']) ? expect[flags['--expect']] : '')
        return 0
    }
    const root = flags['--root'] ?? (env.GITHUB_ACTIONS === 'true' ? process.cwd() : undefined)
    if (root === undefined) throw new Error('recusa gravar: use --root <dir> ou GITHUB_ACTIONS=true')
    const contents = planEdits(root, defect.edits)
    for (const [file, text] of contents) {
        writeFileSync(join(root, file), text)
        console.log(file)
    }
    return 0
}

if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
    try {
        process.exitCode = main(process.argv.slice(2))
    } catch (error) {
        console.error(error.message)
        process.exitCode = 2
    }
}
