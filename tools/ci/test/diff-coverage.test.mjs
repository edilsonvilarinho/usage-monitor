import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
    compactRanges,
    koverResolver,
    lcovResolver,
    measure,
    parseArgs,
    parseDiff,
    parseKover,
    parseLcov,
    passes,
    render,
} from '../diff-coverage.mjs'

const DIFF = [
    'diff --git a/src/commonMain/kotlin/com/x/Foo.kt b/src/commonMain/kotlin/com/x/Foo.kt',
    '--- a/src/commonMain/kotlin/com/x/Foo.kt',
    '+++ b/src/commonMain/kotlin/com/x/Foo.kt',
    '@@ -10,0 +11,3 @@ fun a()',
    '+linha',
    '@@ -20 +23 @@',
    '@@ -30,2 +33,0 @@',
    'diff --git a/src/commonTest/kotlin/com/x/FooTest.kt b/src/commonTest/kotlin/com/x/FooTest.kt',
    '+++ b/src/commonTest/kotlin/com/x/FooTest.kt',
    '@@ -1,0 +1,2 @@',
    'diff --git a/server/src/app.ts b/server/src/app.ts',
    '+++ b/server/src/app.ts',
    '@@ -5 +5,2 @@',
    'diff --git a/old.kt b/old.kt',
    '+++ /dev/null',
    '@@ -1,3 +0,0 @@',
].join('\n')

const KOVER = `<?xml version="1.0" ?>
<report name="r">
<package name="com/x">
<class name="com/x/Foo" sourcefilename="Foo.kt"><counter type="LINE" missed="1" covered="2"/></class>
<sourcefile name="Foo.kt">
<line nr="11" mi="0" ci="4" mb="0" cb="0"/>
<line nr="12" mi="3" ci="0" mb="0" cb="0"/>
<line nr="23" mi="1" ci="2" mb="1" cb="1"/>
<line nr="40" mi="5" ci="0" mb="0" cb="0"/>
</sourcefile>
</package>
</report>`

test('diff: linhas do lado novo, remocao pura e arquivo apagado nao entram', () => {
    const changed = parseDiff(DIFF)
    assert.deepEqual([...changed.get('src/commonMain/kotlin/com/x/Foo.kt')], [11, 12, 13, 23])
    assert.deepEqual([...changed.get('server/src/app.ts')], [5, 6])
    assert.equal(changed.has('old.kt'), false)
})

test('kover: linha coberta e a com instrucao executada, parcial inclusive', () => {
    const lines = parseKover(KOVER).get('com/x/Foo.kt')
    assert.deepEqual([...lines], [[11, true], [12, false], [23, true], [40, false]])
})

test('medicao: so linha de producao instrumentada; teste e linha sem instrucao ficam fora', () => {
    const result = measure(parseDiff(DIFF), koverResolver(parseKover(KOVER)))
    assert.deepEqual(result.files, [{ path: 'src/commonMain/kotlin/com/x/Foo.kt', total: 3, covered: 2, uncovered: [12] }])
    assert.equal(result.outsideReport, 0)
    assert.equal(passes(result, 66), true)
    assert.equal(passes(result, 90), false)
})

test('lcov: caminho absoluto ou relativo ao servidor casa por sufixo', () => {
    const lcov = 'TN:\nSF:C:\\repo\\server\\src\\app.ts\nDA:5,0\nDA:6,3\nend_of_record\nSF:src/other.ts\nDA:1,1\nend_of_record\n'
    const resolver = lcovResolver(parseLcov(lcov))
    assert.deepEqual([...resolver.lookup('server/src/app.ts')], [[5, false], [6, true]])
    assert.deepEqual([...resolver.lookup('server/src/other.ts')], [[1, true]])
    assert.equal(resolver.owns('server/test/app.test.ts'), false)
})

test('sem linha instrumentada passa e o resumo diz isso', () => {
    const result = measure(new Map([['README.md', new Set([1])]]), koverResolver(new Map()))
    assert.equal(passes(result, 90), true)
    assert.match(render(result, 90, 'T'), /passou[\s\S]*Nenhuma linha instrumentada/)
})

test('resumo reprovado lista as linhas descobertas em faixas', () => {
    const result = { files: [{ path: 'a.kt', total: 5, covered: 1, uncovered: [3, 4, 5, 9] }], total: 5, covered: 1, outsideReport: 0 }
    const text = render(result, 90, 'Kotlin')
    assert.match(text, /## Kotlin -- REPROVOU/)
    assert.match(text, /\*\*1 de 5\*\* linhas alteradas cobertas \(20,0%\); piso 90%/)
    assert.match(text, /\| `a.kt` \| 1\/5 \| 3-5, 9 \|/)
    assert.equal(compactRanges([]), '')
})

test('argumentos', () => {
    assert.equal(parseArgs(['--base', 'HEAD^1', '--kover', 'r.xml']).min, 90)
    assert.equal(parseArgs(['--base', 'b', '--lcov', 'l', '--min', '75']).min, 75)
    assert.throws(() => parseArgs(['--kover', 'r.xml']), /--base/)
    assert.throws(() => parseArgs(['--base', 'b']), /exatamente um/)
    assert.throws(() => parseArgs(['--base', 'b', '--kover', 'k', '--lcov', 'l']), /exatamente um/)
    assert.throws(() => parseArgs(['--base', 'b', '--kover', 'k', '--min', '101']), /entre 0 e 100/)
    assert.throws(() => parseArgs(['--base', 'b', '--outra', 'x']), /desconhecido/)
})

test('cada formato so conta os proprios arquivos: Kotlin no diff nao vira "fora do relatorio" do lcov', () => {
    const changed = parseDiff(DIFF)
    const lcov = lcovResolver(parseLcov('SF:src/app.ts\nDA:5,1\nDA:6,1\nend_of_record\n'))
    const server = measure(changed, lcov)
    assert.equal(server.outsideReport, 0)
    assert.deepEqual(server.files.map((file) => file.path), ['server/src/app.ts'])
    const kotlin = measure(changed, koverResolver(parseKover('<package name="com/y"><sourcefile name="Other.kt"></sourcefile></package>')))
    assert.equal(kotlin.outsideReport, 4)
})
