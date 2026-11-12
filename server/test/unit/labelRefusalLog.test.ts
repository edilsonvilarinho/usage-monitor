import { describe, expect, it } from 'vitest';
import { LABEL_REFUSAL_WINDOW_MS, LabelRefusalLog } from '../../src/http/labelRefusalLog.js';

const T0 = Date.UTC(2026, 10, 12, 12, 0, 0);
const MINUTE = 60_000;

describe('LabelRefusalLog', () => {
  it('avisa a primeira recusa do par chave-conta, sem supressao', () => {
    const log = new LabelRefusalLog();

    expect(log.register('key-a', 'conta-1', T0)).toEqual({ warn: true, suppressed: 0 });
  });

  it('suprime as repeticoes dentro da janela de 1 h', () => {
    const log = new LabelRefusalLog();
    log.register('key-a', 'conta-1', T0);

    expect(log.register('key-a', 'conta-1', T0 + MINUTE)).toEqual({ warn: false });
    expect(log.register('key-a', 'conta-1', T0 + 59 * MINUTE)).toEqual({ warn: false });
  });

  it('avisa de novo ao fim da janela, com a contagem do que foi suprimido', () => {
    const log = new LabelRefusalLog();
    log.register('key-a', 'conta-1', T0);
    log.register('key-a', 'conta-1', T0 + MINUTE);
    log.register('key-a', 'conta-1', T0 + 2 * MINUTE);

    expect(log.register('key-a', 'conta-1', T0 + LABEL_REFUSAL_WINDOW_MS)).toEqual({ warn: true, suppressed: 2 });
  });

  it('reinicia a janela no aviso: a repeticao logo apos volta a ser suprimida', () => {
    const log = new LabelRefusalLog();
    log.register('key-a', 'conta-1', T0);
    log.register('key-a', 'conta-1', T0 + LABEL_REFUSAL_WINDOW_MS);

    expect(log.register('key-a', 'conta-1', T0 + LABEL_REFUSAL_WINDOW_MS + MINUTE)).toEqual({ warn: false });
  });

  it('separa o par: outra conta ou outra chave avisa de novo', () => {
    const log = new LabelRefusalLog();
    log.register('key-a', 'conta-1', T0);

    expect(log.register('key-a', 'conta-2', T0)).toEqual({ warn: true, suppressed: 0 });
    expect(log.register('key-b', 'conta-1', T0)).toEqual({ warn: true, suppressed: 0 });
  });

  it('relogio que volta no tempo nao silencia a recusa para sempre', () => {
    const log = new LabelRefusalLog();
    log.register('key-a', 'conta-1', T0);
    log.register('key-a', 'conta-1', T0 + MINUTE);

    expect(log.register('key-a', 'conta-1', T0 - 5 * MINUTE)).toEqual({ warn: true, suppressed: 1 });
  });

  it('limita o mapa: a entrada menos recente sai e a conta dela volta a avisar', () => {
    const log = new LabelRefusalLog({ maxEntries: 2 });
    log.register('key-a', 'conta-1', T0);
    log.register('key-a', 'conta-2', T0);
    log.register('key-a', 'conta-3', T0);

    expect(log.size).toBe(2);
    expect(log.register('key-a', 'conta-1', T0)).toEqual({ warn: true, suppressed: 0 });
  });
});
