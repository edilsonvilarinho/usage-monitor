/**
 * Exposicao em texto do OpenMetrics 1.0 e do formato Prometheus 0.0.4.
 *
 * Escrito a mao, sem `prom-client`: a biblioteca traz um registro **global** e
 * mutavel, e as series deste servidor sao derivadas de uma consulta por scrape —
 * nao ha contador de processo para acumular. Um registro global tambem
 * atravessaria os testes, que sobem varias `app` no mesmo processo. E o mesmo
 * criterio do `tools/ci/test-summary.mjs`, que le JUnit XML sem dependencia.
 *
 * Os dois formatos sao o mesmo texto; o OpenMetrics acrescenta `# EOF` no fim e
 * pede outro content-type. Por isso nao ha dois renderizadores.
 */

export const OPENMETRICS_CONTENT_TYPE = 'application/openmetrics-text; version=1.0.0; charset=utf-8';
export const PROMETHEUS_CONTENT_TYPE = 'text/plain; version=0.0.4; charset=utf-8';

/** Uma amostra: os rotulos e o valor ja resolvido. */
export interface MetricSample {
  labels: Record<string, string>;
  /**
   * Valor ja formatado quando vier como texto.
   *
   * Custo chega como string de seis casas vinda de `microsToUsdString`: passa-lo
   * como `number` faria o JSON do double reintroduzir o erro que o BigInt existe
   * para evitar.
   */
  value: number | string;
}

export interface MetricFamily {
  name: string;
  /**
   * `info` e exposto como **gauge de valor 1** com sufixo `_info`, e nao como o
   * tipo `info` do OpenMetrics: aquele tipo nao existe no formato 0.0.4, e a
   * convencao do gauge (`node_uname_info`) e lida pelas duas versoes sem ramo
   * especial. Quem consome faz a juncao no PromQL do mesmo jeito.
   */
  type: 'gauge' | 'counter';
  help: string;
  samples: MetricSample[];
}

/**
 * Escapa um valor de rotulo.
 *
 * `alias` e `host_name` sao texto livre digitado pelo usuario: uma aspa num
 * apelido produziria exposicao invalida e o scrape **inteiro** falharia — nao a
 * linha, o documento. Barra invertida primeiro, ou o escape das aspas seria
 * escapado de novo.
 */
export function escapeLabelValue(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n');
}

/** No `# HELP` a aspa nao e especial; a barra e a quebra de linha sao. */
export function escapeHelp(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/\n/g, '\\n');
}

function renderLabels(labels: Record<string, string>): string {
  const entries = Object.entries(labels).filter(([, value]) => value !== '');
  if (entries.length === 0) {
    return '';
  }
  const rendered = entries
    .map(([key, value]) => `${key}="${escapeLabelValue(value)}"`)
    .join(',');
  return `{${rendered}}`;
}

/**
 * O documento inteiro.
 *
 * Familia sem amostra **e omitida**, inclusive o `# TYPE`: um cabecalho sozinho
 * nao e invalido, mas nao diz nada e polui um documento que ja e longo.
 */
export function renderExposition(
  families: ReadonlyArray<MetricFamily>,
  options: { openMetrics: boolean },
): string {
  const lines: string[] = [];

  for (const family of families) {
    if (family.samples.length === 0) {
      continue;
    }
    lines.push(`# HELP ${family.name} ${escapeHelp(family.help)}`);
    lines.push(`# TYPE ${family.name} ${family.type}`);
    for (const sample of family.samples) {
      lines.push(`${family.name}${renderLabels(sample.labels)} ${sample.value}`);
    }
  }

  if (options.openMetrics) {
    lines.push('# EOF');
  }

  // O formato exige quebra de linha final; sem ela o ultimo registro pode ser
  // descartado por parsers estritos.
  return `${lines.join('\n')}\n`;
}

/**
 * O `Accept` do Prometheus traz os dois formatos com qualidades diferentes.
 *
 * A negociacao e por substring e nao por parser de `Accept`: o unico ramo que
 * importa e "o cliente entende OpenMetrics", e um parser completo de qualidade
 * seria maquinaria para uma decisao binaria.
 */
export function prefersOpenMetrics(accept: string | undefined): boolean {
  if (accept === undefined) {
    return false;
  }
  return accept.includes('application/openmetrics-text');
}
