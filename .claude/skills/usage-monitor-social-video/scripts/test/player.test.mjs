import { test } from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { beatIndexAt, playerBeats, writtenTexts, buildPlayerHtml, playerSrc } from '../player.mjs'
import { createRepoServer } from '../serve.mjs'

// ---------- beatIndexAt ----------

test('beatIndexAt: beat do instante t, com limites (antes do início e depois do fim)', () => {
  const beats = [{ start: 0, end: 5 }, { start: 5, end: 12 }, { start: 12, end: 20 }]
  assert.equal(beatIndexAt(beats, 0), 0)
  assert.equal(beatIndexAt(beats, 4.99), 0)
  assert.equal(beatIndexAt(beats, 5), 1)
  assert.equal(beatIndexAt(beats, 12), 2)
  assert.equal(beatIndexAt(beats, 19.9), 2)
  assert.equal(beatIndexAt(beats, 99), 2)
  assert.equal(beatIndexAt(beats, -1), 0)
  assert.equal(beatIndexAt([], 3), -1)
})

// ---------- playerBeats ----------

test('playerBeats: título, texto falado inteiro e início de cada beat, na ordem do roteiro', () => {
  const script = {
    beats: [
      { id: 'abre', index: 0, start: 0, end: 5, kicker: 'ABRE', title: 'Um', speechStart: 0.5, speechEnd: 4, items: [] },
      { id: 'fecha', index: 1, start: 5, end: 9, kicker: 'FIM', title: 'Dois', speechStart: 5.5, speechEnd: 8, items: [] },
    ],
  }
  assert.deepEqual(playerBeats(script, ['Texto um.', 'Texto dois.']), [
    { index: 0, id: 'abre', kicker: 'ABRE', title: 'Um', text: 'Texto um.', start: 0, end: 5 },
    { index: 1, id: 'fecha', kicker: 'FIM', title: 'Dois', text: 'Texto dois.', start: 5, end: 9 },
  ])
  assert.throws(() => playerBeats(script, ['so um']), /beats/)
})

// ---------- buildPlayerHtml ----------

const TEMPLATE = '<script>window.PLAYER = /*__PLAYER_JSON__*/null;</script>'

test('buildPlayerHtml: JSON injetado sem conseguir fechar o script', () => {
  const html = buildPlayerHtml(TEMPLATE, { title: 'x', text: '</script><b>oi</b>' })
  assert.ok(!html.includes('</script><b>'), 'o texto de dados fechou o script')
  assert.ok(html.includes('\\u003c/script\\u003e'), 'o < do dado devia sair como \\u003c')
  assert.ok(html.includes('window.PLAYER = {'))
  assert.ok(!html.includes('__PLAYER_JSON__'))
})

test('buildPlayerHtml: sem o marcador no modelo, erro', () => {
  assert.throws(() => buildPlayerHtml('<p>sem marcador</p>', {}), /__PLAYER_JSON__/)
})

// ---------- playerSrc ----------

test('playerSrc: iframe relativo ao qa/ e ao index.html; data relativo ao index.html; format', () => {
  const root = resolve('repo-raiz')
  const src = playerSrc({
    fromDir: join(root, 'build', 'social-video', 'slug', 'qa'),
    indexFile: join(root, '.claude', 'skills', 'usage-monitor-social-video', 'assets', 'index.html'),
    dataFile: join(root, 'build', 'social-video', 'slug', 'short-pt.js'),
    format: '9:16',
  })
  assert.equal(
    src,
    '../../../../.claude/skills/usage-monitor-social-video/assets/index.html'
      + '?data=..%2F..%2F..%2F..%2Fbuild%2Fsocial-video%2Fslug%2Fshort-pt.js&format=9%3A16',
  )
})

// ---------- createRepoServer ----------

test('createRepoServer: GET de arquivo do repo; só GET; nada fora da raiz; sem listagem de pasta', async () => {
  const base = mkdtempSync(join(tmpdir(), 'prev-'))
  const root = join(base, 'repo')
  mkdirSync(join(root, 'sub'), { recursive: true })
  writeFileSync(join(root, 'sub', 'a.txt'), 'ok', 'utf8')
  writeFileSync(join(base, 'fora.txt'), 'segredo', 'utf8')
  const server = createRepoServer(root)
  await new Promise((done) => server.listen(0, '127.0.0.1', done))
  const { port } = server.address()
  const request = (method, path) => new Promise((done, fail) => {
    const req = http.request({ host: '127.0.0.1', port, method, path }, (res) => {
      let body = ''
      res.on('data', (chunk) => { body += chunk })
      res.on('end', () => done({ status: res.statusCode, body }))
    })
    req.on('error', fail)
    req.end()
  })
  try {
    const file = await request('GET', '/sub/a.txt')
    assert.equal(file.status, 200)
    assert.equal(file.body, 'ok')
    assert.equal((await request('POST', '/sub/a.txt')).status, 405)
    assert.equal((await request('GET', '/..%2Ffora.txt')).status, 403)
    assert.equal((await request('GET', '/sub')).status, 404)
  } finally {
    server.close()
    rmSync(base, { recursive: true, force: true })
  }
})

// ---------- writtenTexts ----------

test('writtenTexts: a lista recebe o texto escrito de cada beat do tipo, não a forma falada', () => {
  const spec = {
    beats: [
      { id: 'a', long: { pt: 'O CLAUDE.md viraram P&R.', en: 'The CLAUDE.md became Q&A.', say: { pt: 'Claude ponto M D' } } },
      { id: 'b', short: { pt: 'Limite.', en: 'Limit.' }, long: { pt: 'Limite semanal.', en: 'Weekly limit.' } },
    ],
  }
  assert.deepEqual(writtenTexts(spec, 'long', 'pt'), ['O CLAUDE.md viraram P&R.', 'Limite semanal.'])
  assert.deepEqual(writtenTexts(spec, 'long', 'en'), ['The CLAUDE.md became Q&A.', 'Weekly limit.'])
  assert.deepEqual(writtenTexts(spec, 'short', 'pt'), ['Limite.'])
})
