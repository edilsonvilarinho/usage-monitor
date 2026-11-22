import { test } from 'node:test'
import assert from 'node:assert/strict'
import { toolSteps } from '../setup-tools.mjs'

test('padrao: instala Piper e as vozes, como o tour sempre fez', () => {
    const steps = toolSteps()
    assert.ok(steps.includes('piper'))
    assert.ok(steps.includes('voices'))
    assert.ok(!steps.includes('kokoro'))
})

test('piper: false (vídeo de release, fala pela API): nada de Piper nem vozes baixadas', () => {
    const steps = toolSteps({ piper: false })
    assert.ok(!steps.includes('piper'))
    assert.ok(!steps.includes('voices'))
    assert.ok(steps.includes('puppeteer'))
})

test('kokoro só entra quando o roteiro do tour pede', () => {
    assert.ok(toolSteps({ kokoro: true }).includes('kokoro'))
    assert.ok(toolSteps({ kokoro: true, piper: false }).includes('kokoro'))
    assert.ok(!toolSteps({ piper: false }).includes('kokoro'))
})
