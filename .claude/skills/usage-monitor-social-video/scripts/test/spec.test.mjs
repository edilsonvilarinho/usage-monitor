import { test } from 'node:test'
import assert from 'node:assert/strict'
import { VARIANTS, climaxIndex, outputNames, spokenTexts, toSpoken, validateSpec } from '../spec.mjs'

const ok = () => true
const IMAGE = 'img/hud.png'
const check = (spec, fileExists = ok) => validateSpec(spec, { fileExists })
const has = (errors, message) =>
    assert.ok(errors.includes(message), `esperado: ${message}\nrecebido:\n${errors.join('\n')}`)

const beat = (id, patch = {}) => ({
    id,
    kicker: { pt: 'Fluxo', en: 'Workflow' },
    title: { pt: 'Do pedido ao merge', en: 'From request to merge' },
    items: [{ pt: 'PR aberto', en: 'PR opened' }],
    image: IMAGE,
    climax: false,
    short: { pt: 'Texto curto.', en: 'Short text.' },
    long: { pt: 'Texto longo do beat.', en: 'Long text of the beat.' },
    ...patch,
})

const closing = (patch = {}) => {
    const { items, image, ...rest } = beat('fechamento')
    return { ...rest, ...patch }
}

const spec = (patch = {}) => ({
    slug: 'fluxo-agentico',
    link: 'https://github.com/edilsonvilarinho/usage-monitor',
    facts: ['24 testes novos', '3961', '212/212'],
    beats: [beat('abertura'), beat('fluxo', { items: [{ pt: '24 testes', en: '24 tests' }] }), closing()],
    ...patch,
})

test('contrato de exemplo passa sem erros', () => {
    assert.deepEqual(check(spec()), [])
})

test('regra 1: slug em minusculas, digitos e hifen; link https', () => {
    const errors = check(spec({ slug: 'Fluxo_Agentico', link: 'http://github.com/x' }))
    assert.equal(errors.length, 2, errors.join('\n'))
    assert.match(errors[0], /^slug: .*minúsculas, dígitos e hífen/)
    assert.match(errors[1], /^link: .*https:\/\//)
})

test('regra 2: entre 3 e 20 beats, id unico em minusculas e hifen, fechamento sem items nem image', () => {
    const few = check(spec({ beats: [beat('abertura'), closing()] }))
    has(few, 'beats: 2 beats; o mínimo é 3')
    const many = Array.from({ length: 21 }, (_, i) => beat(`b${'x'.repeat(i)}`))
    has(check(spec({ beats: many })), 'beats: 21 beats; o máximo é 20')
    has(check(spec({ beats: [beat('abertura'), beat('abertura'), closing()] })), 'beat "abertura" id: repetido')
    has(check(spec({ beats: [beat('Abertura'), beat('fluxo'), closing()] })), 'beat "Abertura" id: use minúsculas e hífen')
    has(
        check(spec({ beats: [beat('abertura'), beat('fluxo'), closing({ items: [{ pt: 'x', en: 'x' }] })] })),
        'beat "fechamento" items: o fechamento não tem itens',
    )
    has(
        check(spec({ beats: [beat('abertura'), beat('fluxo'), closing({ image: IMAGE })] })),
        'beat "fechamento" image: o fechamento não tem imagem',
    )
})

test('regra 3: kicker e title com pt e en; title ate 34 caracteres', () => {
    const errors = check(
        spec({
            beats: [
                beat('abertura', { kicker: { pt: 'Fluxo' } }),
                beat('fluxo', { title: { pt: 'a'.repeat(35), en: 'b'.repeat(34) } }),
                closing(),
            ],
        }),
    )
    assert.equal(errors.length, 2, errors.join('\n'))
    has(errors, 'beat "abertura" kicker.en: ausente')
    has(errors, 'beat "fluxo" title.pt: 35 caracteres; o máximo é 34')
})

test('regra 4: long em todo beat; short em ao menos 3 beats e no ultimo', () => {
    has(check(spec({ beats: [beat('a', { long: undefined }), beat('b'), closing()] })), 'beat "a" long: ausente')
    has(
        check(spec({ beats: [beat('a', { short: undefined }), beat('b'), beat('c', { short: undefined }), closing()] })),
        'beats: só 2 beats têm short; o mínimo é 3',
    )
    const noLastShort = check(
        spec({ beats: [beat('a'), beat('b'), beat('c'), closing({ short: undefined })] }),
    )
    assert.deepEqual(noLastShort, ['beat "fechamento" short: o último beat precisa de short'])
})

test('regra 5: teto falado de 650 (curto) e 3500 (longo); a mensagem diz quanto cortar', () => {
    const longErrors = check(
        spec({ beats: [beat('abertura', { long: { pt: 'a'.repeat(3501), en: 'b'.repeat(3500) } }), beat('fluxo'), closing()] }),
    )
    assert.equal(longErrors.length, 1, longErrors.join('\n'))
    has(longErrors, 'beat "abertura" long.pt: 3501 caracteres falados; cortar 1 (máximo 3500)')
    const shortErrors = check(
        spec({ beats: [beat('abertura', { short: { pt: 'a'.repeat(651), en: 'b'.repeat(650) } }), beat('fluxo'), closing()] }),
    )
    assert.equal(shortErrors.length, 1, shortErrors.join('\n'))
    has(shortErrors, 'beat "abertura" short.pt: 651 caracteres falados; cortar 1 (máximo 650)')
})

test('regra 6: items ate 8; pt e en ate 44, ou 90 quando mono; status ok ou crit', () => {
    const nine = Array.from({ length: 9 }, () => ({ pt: 'PR', en: 'PR' }))
    has(check(spec({ beats: [beat('abertura', { items: nine }), beat('fluxo'), closing()] })), 'beat "abertura" items: 9 itens; o máximo é 8')
    const plain = check(spec({ beats: [beat('abertura', { items: [{ pt: 'a'.repeat(45), en: 'ok' }] }), beat('fluxo'), closing()] }))
    has(plain, 'beat "abertura" items[0].pt: 45 caracteres; o máximo é 44')
    const mono = check(
        spec({ beats: [beat('abertura', { items: [{ pt: 'b'.repeat(90), en: 'c'.repeat(91), mono: true }] }), beat('fluxo'), closing()] }),
    )
    assert.equal(mono.length, 1, mono.join('\n'))
    has(mono, 'beat "abertura" items[0].en: 91 caracteres; o máximo é 90')
    const status = check(spec({ beats: [beat('abertura', { items: [{ pt: 'PR', en: 'PR', status: 'warn' }] }), beat('fluxo'), closing()] }))
    has(status, 'beat "abertura" items[0].status: use ok ou crit')
})

test('regra 7: com image, ate 3 itens; arquivo existe e e png, jpg ou jpeg', () => {
    const four = [1, 2, 3, 4].map(() => ({ pt: 'PR', en: 'PR' }))
    has(
        check(spec({ beats: [beat('abertura', { items: four }), beat('fluxo'), closing()] })),
        'beat "abertura" items: 4 itens com imagem; o máximo é 3',
    )
    const seen = []
    const found = check(spec(), (path) => {
        seen.push(path)
        return true
    })
    assert.deepEqual(found, [])
    assert.ok(seen.includes(IMAGE), `fileExists chamado com: ${seen.join(', ')}`)
    const missing = check(spec({ beats: [beat('abertura'), beat('fluxo', { image: undefined }), closing()] }), () => false)
    assert.deepEqual(missing, ['beat "abertura" image: arquivo não existe (img/hud.png)'])
    const gif = check(spec({ beats: [beat('abertura', { image: 'img/hud.gif' }), beat('fluxo', { image: undefined }), closing()] }))
    assert.deepEqual(gif, ['beat "abertura" image: extensão precisa ser .png, .jpg ou .jpeg'])
})

test('regra 8: no maximo um climax; sem nenhum, o climax e o penultimo', () => {
    has(
        check(spec({ beats: [beat('abertura', { climax: true }), beat('fluxo', { climax: true }), closing()] })),
        'beats: 2 beats com climax: true; no máximo 1',
    )
    assert.deepEqual(check(spec({ beats: [beat('abertura', { climax: true }), beat('fluxo'), closing()] })), [])
    assert.equal(climaxIndex(spec()), 1)
    assert.equal(climaxIndex(spec({ beats: [beat('abertura', { climax: true }), beat('fluxo'), closing()] })), 0)
})

test('regra 9: todo numero com 2+ digitos em title, items, short e long aparece em facts', () => {
    const errors = check(
        spec({
            beats: [
                beat('abertura', { title: { pt: 'Versão 42', en: 'Version 42' } }),
                beat('fluxo', { items: [{ pt: '3961 testes', en: '3961 tests' }], long: { pt: 'Teste 5 e 212', en: 'Test 5 and 212' } }),
                closing(),
            ],
        }),
    )
    assert.equal(errors.length, 2, errors.join('\n'))
    has(errors, 'beat "abertura" title.pt: número 42 não aparece em facts')
    has(errors, 'beat "abertura" title.en: número 42 não aparece em facts')
})

test('regra 10: nenhum texto contem sk-or-', () => {
    const errors = check(spec({ beats: [beat('abertura'), beat('fluxo', { long: { pt: 'Chave sk-or-v1-abc', en: 'ok' } }), closing()] }))
    assert.deepEqual(errors, ['beat "fluxo" long.pt: contém "sk-or-"'])
})

test('junta todos os erros sem parar no primeiro e exige fileExists', () => {
    const errors = check(
        spec({ slug: 'Fluxo Agentico', link: 'http://x', beats: [beat('abertura', { kicker: { pt: 'Fluxo' } }), beat('fluxo'), closing()] }),
    )
    assert.equal(errors.length, 3, errors.join('\n'))
    assert.throws(() => validateSpec(spec()), TypeError)
})

test('say explicito vence a regra padrao', () => {
    assert.equal(toSpoken('CLAUDE.md', 'pt', { pt: 'Claude M D' }), 'Claude M D')
    assert.equal(toSpoken('CLAUDE.md', 'en', { pt: 'Claude M D' }), 'Claude dot M D')
})

test('regra padrao: ci-ok, CI, TDD e PR', () => {
    assert.equal(toSpoken('ci-ok, CI, TDD e PR', 'pt'), 'C I OK, C I, T D D e P R')
    assert.equal(toSpoken('ci-ok, CI, TDD and PR', 'en'), 'C I OK, C I, T D D and P R')
})

test('regra padrao: CLAUDE.md por idioma e usage-monitor-workflow', () => {
    assert.equal(toSpoken('Veja CLAUDE.md e usage-monitor-workflow.', 'pt'), 'Veja Claude ponto M D e Usage Monitor Workflow.')
    assert.equal(toSpoken('See CLAUDE.md and usage-monitor-workflow.', 'en'), 'See Claude dot M D and Usage Monitor Workflow.')
})

test('substitui so palavra inteira, nunca dentro de outra', () => {
    assert.equal(
        toSpoken('PRs, TDDs, ACI, xci-ok, CI-OK e usage-monitor-workflows', 'pt'),
        'PRs, TDDs, ACI, xci-ok, CI-OK e usage-monitor-workflows',
    )
})

test('spokenTexts devolve a forma falada de cada beat que tem o texto', () => {
    const s = spec({
        beats: [
            beat('abertura', {
                short: { pt: 'PR aberto.', en: 'PR opened.' },
                long: { pt: 'CLAUDE.md', en: 'CLAUDE.md', say: { pt: 'Claude M D' } },
            }),
            beat('fluxo', { short: undefined }),
            closing({ short: { pt: 'CI verde.', en: 'CI green.' } }),
        ],
    })
    assert.deepEqual(spokenTexts(s, 'short', 'pt'), ['P R aberto.', 'C I verde.'])
    assert.deepEqual(spokenTexts(s, 'long', 'pt'), ['Claude M D', 'Texto longo do beat.', 'Texto longo do beat.'])
    assert.throws(() => spokenTexts(s, 'medio', 'pt'), /kind/)
})

test('VARIANTS segue a tabela e gera os nomes de saida', () => {
    assert.deepEqual(
        VARIANTS.map((v) => [v.variant, v.format]),
        [
            ['short-pt', '9:16'],
            ['short-pt', '16:9'],
            ['short-en', '16:9'],
            ['long-pt', '16:9'],
            ['long-en', '16:9'],
        ],
    )
    assert.deepEqual(VARIANTS.flatMap((v) => outputNames('fluxo-agentico', v)), [
        'fluxo-agentico-tiktok-pt.mp4',
        'fluxo-agentico-shorts-pt.mp4',
        'fluxo-agentico-linkedin-pt.mp4',
        'fluxo-agentico-x-en.mp4',
        'fluxo-agentico-youtube-pt.mp4',
        'fluxo-agentico-youtube-en.mp4',
    ])
})

test('VARIANTS usa matilda em PT e george em EN, no mesmo modelo', () => {
    assert.deepEqual(
        VARIANTS.map((v) => v.voice),
        ['matilda', 'matilda', 'george', 'matilda', 'george'],
    )
    assert.ok(VARIANTS.every((v) => v.model === 'elevenlabs/eleven-v4'))
})
