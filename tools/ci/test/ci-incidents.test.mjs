import { test } from 'node:test'
import assert from 'node:assert/strict'
import { collectIncidents, localLogName, parseArgs, parseLocalLog } from '../ci-incidents.mjs'

const run = (databaseId, attempt, conclusion, createdAt = '2026-10-28T10:00:00Z') =>
    ({ databaseId, attempt, conclusion, createdAt, url: `https://example.test/runs/${databaseId}` })

test('conta run vermelho e ignora cancelado, verde e em andamento', () => {
    const incidents = collectIncidents(
        [run(1, 1, 'failure'), run(2, 1, 'cancelled'), run(3, 1, 'success'), run(4, 1, ''), run(5, 1, 'timed_out')],
        new Map(),
        [],
    )
    assert.deepEqual(incidents.map((incident) => incident.detail), [
        'https://example.test/runs/1 (tentativa 1: failure)',
        'https://example.test/runs/5 (tentativa 1: timed_out)',
    ])
})

test('tentativa vermelha reexecutada ate o verde continua contando', () => {
    const incidents = collectIncidents([run(7, 3, 'success')], new Map([[7, ['failure', 'success']]]), [])
    assert.equal(incidents.length, 1)
    assert.match(incidents[0].detail, /tentativa 1: failure/)
})

test('falha local entra na conta, em ordem de tempo', () => {
    const incidents = collectIncidents(
        [run(1, 1, 'failure', '2026-10-28T12:00:00Z')],
        new Map(),
        parseLocalLog('2026-10-28T11:00:00Z\tallTests: 2 falhas\n\n'),
    )
    assert.deepEqual(incidents.map((incident) => incident.source), ['local', 'ci'])
})

test('linha local sem tabulacao vira detalhe sem carimbo', () => {
    assert.deepEqual(parseLocalLog('falha solta'), [{ at: '', detail: 'falha solta' }])
})

test('nome do log local nao cria subdiretorio', () => {
    assert.equal(localLogName('chore/issue-496-workflow'), 'chore_issue-496-workflow.log')
})

test('argumentos', () => {
    assert.deepEqual(parseArgs([]), { branch: null, limit: 4, local: null })
    assert.deepEqual(parseArgs(['--branch', 'b', '--limit', '2', '--local', 'x']), { branch: 'b', limit: 2, local: 'x' })
    assert.throws(() => parseArgs(['--limit', '0']), /inteiro/)
    assert.throws(() => parseArgs(['--branch']), /sem valor/)
    assert.throws(() => parseArgs(['--outro', 'x']), /desconhecido/)
})
