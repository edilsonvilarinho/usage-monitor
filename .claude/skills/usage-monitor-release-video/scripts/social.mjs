#!/usr/bin/env node
// Confere as descrições do vídeo para X, Instagram e TikTok e escreve o social.md para copiar e colar.
// Uso: node social.mjs <social.json> <vX.Y.Z> <saida.md>
import { readFileSync, writeFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

export const NETWORKS = [
    { key: 'x', name: 'X', limit: 280, link: true },
    { key: 'instagram', name: 'Instagram', limit: 2200, link: false },
    { key: 'tiktok', name: 'TikTok', limit: 4000, link: false },
]
const LANGUAGES = ['pt', 'en']
const MAX_HASHTAGS = 5
// O X encurta todo link para t.co: qualquer URL ocupa 23 caracteres.
const X_LINK_LENGTH = 23
const URL_PATTERN = /https?:\/\/\S+/g

export function xLength(text) {
    const withoutLinks = text.replace(URL_PATTERN, '')
    const links = (text.match(URL_PATTERN) ?? []).length
    let length = links * X_LINK_LENGTH
    for (const character of withoutLinks) length += /\p{Extended_Pictographic}/u.test(character) ? 2 : 1
    return length
}

export function checkSocial(social, { releaseUrl }) {
    const problems = []
    for (const network of NETWORKS) {
        for (const language of LANGUAGES) {
            const where = `${network.key}.${language}`
            const text = social[network.key]?.[language]?.trim()
            if (!text) {
                problems.push(`${where}: vazio`)
                continue
            }
            const length = network.key === 'x' ? xLength(text) : [...text].length
            if (length > network.limit) problems.push(`${where}: ${length} de ${network.limit} caracteres`)
            if (network.link && !text.includes(releaseUrl)) problems.push(`${where}: sem o link da release`)
            if (!network.link && URL_PATTERN.test(text)) {
                problems.push(`${where}: link não é clicável nesta rede — use "link na bio"`)
            }
            URL_PATTERN.lastIndex = 0
            const hashtags = (text.match(/(^|\s)#[\p{L}\p{N}_]+/gu) ?? []).length
            if (hashtags > MAX_HASHTAGS) problems.push(`${where}: ${hashtags} hashtags (máximo ${MAX_HASHTAGS})`)
        }
    }
    return problems
}

export function socialMarkdown(social, version) {
    const sections = [`# Descrições — ${version}\n`]
    for (const network of NETWORKS) {
        for (const language of LANGUAGES) {
            sections.push(`## ${network.name} — ${language.toUpperCase()}\n\n\`\`\`text\n${social[network.key][language].trim()}\n\`\`\`\n`)
        }
    }
    return sections.join('\n')
}

function main() {
    const [socialPath, version, outputPath] = process.argv.slice(2)
    const social = JSON.parse(readFileSync(socialPath, 'utf8'))
    const releaseUrl = `https://github.com/edilsonvilarinho/usage-monitor/releases/tag/${version}`
    const problems = checkSocial(social, { releaseUrl })
    if (problems.length > 0) {
        process.stderr.write(`Descrições recusadas:\n${problems.join('\n')}\n`)
        process.exit(1)
    }
    writeFileSync(outputPath, socialMarkdown(social, version))
    process.stdout.write(`${outputPath}\n`)
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main()
