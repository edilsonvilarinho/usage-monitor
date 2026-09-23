# Auditoria de GitHub Actions e caches: Codenotch e Usage Monitor

| Campo | Valor |
|---|---|
| Issue | [#266](https://github.com/edilsonvilarinho/usage-monitor/issues/266) |
| Data da consulta | 2026-09-23 |
| Codenotch auditado | `aae2c1f77bd2f2fb6c03aa58ca6329c5d003fab4` |
| Usage Monitor auditado | `c42c3b67614a1ace150c6b2aac71933bd9600201` |

## Escopo e método

Comparei os arquivos de workflow no SHA indicado para cada repositório e consultei a API de caches do GitHub em 23/09/2026. Os números de cache são um retrato daquela consulta; entradas podem ser criadas ou expiradas depois.

## Codenotch

No SHA auditado, `.github/workflows/` contém quatro arquivos: `ci.yml` e `package.yml` para macOS, `windows.yml` e `windows-package.yml` para o port Windows em Rust. Os dois workflows Windows usam `Swatinem/rust-cache@v2`, com caches separados entre build e empacotamento, para Windows x64.

| Escopo listado pela API | Entradas | Conteúdo |
|---|---:|---|
| `refs/heads/main` | 4 | 2 de build e 2 de package |
| `refs/pull/322/merge` | 2 | 1 de build e 1 de package |
| `refs/pull/291/merge` | 2 | 1 de build e 1 de package |
| **Total consultado** | **8** | **6.008.830.862 bytes (5,60 GiB)** |

O issue registra aproximadamente 5,73 GiB para essas oito entradas. A soma dos tamanhos retornados pela API nesta auditoria foi 6.008.830.862 bytes (5,60 GiB); portanto, o total em GiB não foi reproduzido. O relatório usa a soma verificável desta consulta e mantém explícita a divergência.

O workflow `package.yml` também mantém uma release `preview` rolante, atualizada a partir da `main`, com permissão de escrita no conteúdo do repositório. Esse fluxo não é necessário para o Usage Monitor, cujos artefatos de release são versionados.

A API de Actions também lista `Linux` e `Linux Package`, mas os caminhos YAML associados não existem na árvore desse SHA. Esses registros da API não foram tratados como evidência do conteúdo de workflows atuais.

## Usage Monitor

No SHA auditado, os workflows versionados são `ci.yml`, `ci-server.yml`, `codeql.yml` e `release-linux.yml`.

- O CI desktop usa `gradle/actions/setup-gradle@v4.4.3`. Em `ci.yml` e `codeql.yml`, `cache-read-only` é `true` fora de `refs/heads/main`.
- O CI do servidor usa `actions/setup-node` com cache npm.
- A API retornou 31 entradas, totalizando 987.517.675 bytes (941,6 MiB, aproximadamente 942 MiB).
- A execução [CI #242](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/35557592728), acionada por pull request em 21/09/2026, restaurou entradas Gradle e terminou com sucesso. Isso comprova reutilização de cache naquele run; não é uma medição comparativa de duração.

## Decisão

Manter `gradle/actions/setup-gradle` e a política atual que permite gravação apenas na `main`. Os dados consultados mostram caches ativos e uma execução de PR que restaurou o cache com sucesso; não há evidência neste levantamento que justifique trocar a action, criar cache manual de `~/.gradle` ou habilitar gravações de cache por PR. Não adotar `Swatinem/rust-cache`, que atende a toolchain Rust do Codenotch, nem a publicação rolante `preview`.

A documentação dos workflows deve refletir o escopo real: um cache criado no ref de uma PR pode ser reutilizado por reexecuções da mesma PR, mas não por outras PRs nem pela branch base. Uma PR também pode restaurar caches da branch base. Portanto, a frase anterior de que “nenhum outro run” pode ler um cache de PR era ampla demais; a configuração de leitura/gravação permanece inalterada.

## Alterações desta implementação

1. Este arquivo registra o snapshot, as evidências, a divergência no total do Codenotch, a comparação e a decisão.
2. Os comentários próximos de `cache-read-only` em `ci.yml` e `codeql.yml` foram corrigidos para descrever o escopo de PR com precisão. As expressões e o comportamento dos workflows permanecem iguais.

## Fontes

- [Workflows do Codenotch no SHA auditado](https://github.com/vinzdg/codenotch/tree/aae2c1f77bd2f2fb6c03aa58ca6329c5d003fab4/.github/workflows)
- [Workflow Windows do Codenotch](https://github.com/vinzdg/codenotch/blob/aae2c1f77bd2f2fb6c03aa58ca6329c5d003fab4/.github/workflows/windows.yml)
- [Workflow Windows Package do Codenotch](https://github.com/vinzdg/codenotch/blob/aae2c1f77bd2f2fb6c03aa58ca6329c5d003fab4/.github/workflows/windows-package.yml)
- [Workflow Package do Codenotch](https://github.com/vinzdg/codenotch/blob/aae2c1f77bd2f2fb6c03aa58ca6329c5d003fab4/.github/workflows/package.yml)
- [Caches do Codenotch](https://github.com/vinzdg/codenotch/actions/caches)
- [CI do Usage Monitor no SHA auditado](https://github.com/edilsonvilarinho/usage-monitor/blob/c42c3b67614a1ace150c6b2aac71933bd9600201/.github/workflows/ci.yml)
- [CI do servidor no SHA auditado](https://github.com/edilsonvilarinho/usage-monitor/blob/c42c3b67614a1ace150c6b2aac71933bd9600201/.github/workflows/ci-server.yml)
- [CodeQL no SHA auditado](https://github.com/edilsonvilarinho/usage-monitor/blob/c42c3b67614a1ace150c6b2aac71933bd9600201/.github/workflows/codeql.yml)
- [Release multiplataforma no SHA auditado](https://github.com/edilsonvilarinho/usage-monitor/blob/c42c3b67614a1ace150c6b2aac71933bd9600201/.github/workflows/release-linux.yml)
- [Caches do Usage Monitor](https://github.com/edilsonvilarinho/usage-monitor/actions/caches)
- [Restrições de escopo dos caches no GitHub Actions](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching#restrictions-for-accessing-a-cache)
- [Documentação do `cache-read-only` do Gradle](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md#using-the-cache-read-only)
