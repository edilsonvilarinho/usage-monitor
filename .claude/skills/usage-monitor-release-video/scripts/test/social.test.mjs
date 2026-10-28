import { test } from 'node:test'
import assert from 'node:assert/strict'
import { checkSocial, socialMarkdown, xLength } from '../social.mjs'

const URL = 'https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v42.0.0'
const valid = () => ({
    x: { pt: `Usage Monitor 42 chegou ao terminal. ${URL}`, en: `Usage Monitor 42 lands in the terminal. ${URL}` },
    instagram: { pt: 'Novidades da 42.\n\nLink na bio.\n\n#ClaudeCode #dev', en: 'What is new in 42.\n\nLink in bio.\n\n#ClaudeCode #dev' },
    tiktok: { pt: 'Usage Monitor 42 no terminal #dev', en: 'Usage Monitor 42 in the terminal #dev' },
})

test('no X o link conta 23 caracteres e emoji conta 2', () => {
    assert.equal(xLength(`abc ${URL}`), 4 + 23)
    assert.equal(xLength('ok 🚀'), 3 + 2)
})

test('descricoes validas nao tem problema', () => {
    assert.deepEqual(checkSocial(valid(), { releaseUrl: URL }), [])
})

test('acusa post do X longo, sem link e rede ou idioma faltando', () => {
    const social = valid()
    social.x.en = 'a'.repeat(300)
    delete social.tiktok.pt
    assert.deepEqual(checkSocial(social, { releaseUrl: URL }), [
        'x.en: 300 de 280 caracteres',
        'x.en: sem o link da release',
        'tiktok.pt: vazio',
    ])
})

test('Instagram e TikTok nao levam link e no maximo 5 hashtags', () => {
    const social = valid()
    social.instagram.pt = `Veja ${URL} #a #b #c #d #e #f`
    assert.deepEqual(checkSocial(social, { releaseUrl: URL }), [
        'instagram.pt: link não é clicável nesta rede — use "link na bio"',
        'instagram.pt: 6 hashtags (máximo 5)',
    ])
})

test('o markdown traz cada rede nos dois idiomas, em bloco copiavel', () => {
    const markdown = socialMarkdown(valid(), 'v42.0.0')
    assert.match(markdown, /^# Descrições — v42\.0\.0/)
    for (const heading of ['## X — PT', '## X — EN', '## Instagram — PT', '## TikTok — EN']) {
        assert.ok(markdown.includes(heading), heading)
    }
    assert.ok(markdown.includes('```text\nUsage Monitor 42 no terminal #dev\n```'))
})
