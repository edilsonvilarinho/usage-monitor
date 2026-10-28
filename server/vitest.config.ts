import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    include: ['test/**/*.test.ts'],
    // So liga com `--coverage` (`npm run test:coverage`): o `npm test` do dia a
    // dia nao paga a instrumentacao. `json-summary` e o que o relatorio da
    // skill `usage-monitor-test-report` le; `lcovonly` (cobertura por linha) e o
    // que o `tools/ci/diff-coverage.mjs` le (#496).
    coverage: {
      provider: 'v8',
      include: ['src/**/*.ts'],
      reporter: ['text-summary', 'json-summary', 'html', 'lcovonly'],
      reportsDirectory: 'coverage',
      // Catraca de linhas (plano `docs/planos/cobertura-95-execucao.md`): o piso
      // e o ultimo valor medido, arredondado para baixo, e so sobe. Meta: 95.
      thresholds: {
        lines: 95,
      },
    },
  },
});
