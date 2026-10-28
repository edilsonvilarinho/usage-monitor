import { test } from 'node:test'
import assert from 'node:assert/strict'
import { groupChanges, parseCommitLine, releaseRange } from '../collect-changes.mjs'

test('le tipo, escopo, assunto e issues de um commit convencional', () => {
    assert.deepEqual(parseCommitLine('5e520f9a\tfeat(cli): keep the status open with status --watch (#453)'), {
        sha: '5e520f9a',
        type: 'feat',
        scope: 'cli',
        subject: 'keep the status open with status --watch',
        issues: [453],
    })
})

test('commit sem escopo, sem issue ou fora do formato nao quebra', () => {
    assert.deepEqual(parseCommitLine('aaaa111\tfix: escape paths'), {
        sha: 'aaaa111', type: 'fix', scope: '', subject: 'escape paths', issues: [],
    })
    assert.equal(parseCommitLine('bbbb222\tMerge pull request #502 from x/y').type, '')
})

test('agrupa feat e fix por issue e deixa de fora docs, testes, ci e skills', () => {
    const lines = [
        'a1\tfeat(cli): print every quota (#439)',
        'a2\tfix(cli): resolve symbolic links (#439)',
        'a3\tdocs(cli): document the commands (#439)',
        'a4\tfeat(cadence): poll a target every minute while its quota is moving',
        'a5\tfix(skills): pass the commit identity per command (#498)',
        'a6\ttest(cli): prove the headless server (#440)',
        'a7\tci: publish the test report',
        'a8\tfix(hud): show the reset date for weekly quotas',
    ]
    const changes = groupChanges(lines.map(parseCommitLine))
    assert.deepEqual(changes.issues.map((group) => [group.issue, group.feats.length, group.fixes.length]), [[439, 1, 1]])
    assert.deepEqual(changes.loose.map((change) => change.sha), ['a4', 'a8'])
    assert.equal(changes.counted, 8)
})

test('a faixa vai da estavel anterior ate a pedida, pulando betas', () => {
    const tags = ['v41.10.0', 'v41.11.0', 'v42.0.0-beta.1', 'v42.0.0-beta.7', 'v42.0.0']
    assert.deepEqual(releaseRange(tags, 'v42.0.0'), { from: 'v41.11.0', to: 'v42.0.0' })
    assert.deepEqual(releaseRange(tags), { from: 'v41.11.0', to: 'v42.0.0' })
    assert.deepEqual(releaseRange(tags, 'v41.11.0'), { from: 'v41.10.0', to: 'v41.11.0' })
})
