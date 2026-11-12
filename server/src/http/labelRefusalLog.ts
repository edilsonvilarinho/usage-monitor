/** Janela em que a repeticao da recusa por rotulo sai so como contagem. */
export const LABEL_REFUSAL_WINDOW_MS = 60 * 60 * 1000;

/**
 * Teto do mapa de dedup. Sem ele, um servidor de longa duracao acumula um par
 * `(chave, conta)` para cada conta recusada, e nunca libera nenhum.
 */
export const LABEL_REFUSAL_MAX_ENTRIES = 1024;

export type LabelRefusalVerdict = { warn: false } | { warn: true; suppressed: number };

interface RefusalEntry {
  lastWarnedAt: number;
  suppressed: number;
}

/**
 * Dedup do `warn` da recusa por rotulo, por `(keyId, accountKey)`.
 *
 * So o log e deduplicado: a recusa (403) continua a cada requisicao. O relogio
 * entra como argumento, para o teste controlar o tempo.
 */
export class LabelRefusalLog {
  private readonly windowMs: number;
  private readonly maxEntries: number;
  private readonly entries = new Map<string, RefusalEntry>();

  constructor(options: { windowMs?: number; maxEntries?: number } = {}) {
    this.windowMs = options.windowMs ?? LABEL_REFUSAL_WINDOW_MS;
    this.maxEntries = options.maxEntries ?? LABEL_REFUSAL_MAX_ENTRIES;
  }

  get size(): number {
    return this.entries.size;
  }

  register(keyId: string, accountKey: string, nowMs: number): LabelRefusalVerdict {
    const key = JSON.stringify([keyId, accountKey]);
    const previous = this.entries.get(key);

    if (previous !== undefined && this.isInsideWindow(previous, nowMs)) {
      previous.suppressed += 1;
      this.touch(key, previous);
      return { warn: false };
    }

    const suppressed = previous?.suppressed ?? 0;
    this.touch(key, { lastWarnedAt: nowMs, suppressed: 0 });
    return { warn: true, suppressed };
  }

  /** Relogio que voltou no tempo (elapsed negativo) não conta como janela aberta. */
  private isInsideWindow(entry: RefusalEntry, nowMs: number): boolean {
    const elapsed = nowMs - entry.lastWarnedAt;
    return elapsed >= 0 && elapsed < this.windowMs;
  }

  /** Reinsere no fim do mapa (ordem de uso) e despeja a entrada menos recente. */
  private touch(key: string, entry: RefusalEntry): void {
    this.entries.delete(key);
    this.entries.set(key, entry);
    if (this.entries.size > this.maxEntries) {
      const oldest = this.entries.keys().next().value;
      if (oldest !== undefined) {
        this.entries.delete(oldest);
      }
    }
  }
}
