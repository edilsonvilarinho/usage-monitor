# Selo do XERJ Corpus Hub no README — issue #507

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-10-28, branch
`docs/issue-507-xerj-badge`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O repositório foi incluído no XERJ Corpus Hub; a página de colaborador
(`https://hub.xerj.org/contributors/edilsonvilarinho`) oferece um selo para o README. Exibi-lo na fileira de selos.

## Fatos confirmados (2026-10-28, `f9f8c35e`)

- Snippet oferecido pela página (WebFetch): `[![XERJ Corpus Hub contributor](https://hub.xerj.org/contributors/edilsonvilarinho/badge.svg)](https://hub.xerj.org/contributors/edilsonvilarinho)`.
- `curl -w "%{http_code} %{content_type}" …/badge.svg`: `200 image/svg+xml`, 751 bytes.
- `README.md` e `README.pt-BR.md` têm a fileira de selos em HTML (`<p align="center">` com `<a><img></a>`), linhas 5–11.

## Decisões

- Selo em HTML, no formato dos vizinhos, ao fim da fileira; mesmo URL de imagem e de link do snippet.
- `alt` traduzido no README em português, como os demais selos dele.

## Atividades

- A01 — este plano.
- A02 — selo nos dois READMEs.

## Ponto de situação

| Atividade | Estado | Validação |
|---|---|---|
| A01 | feito | sem harness de teste (documentação); `git diff --stat` mostra só este arquivo |

## Problemas em aberto e riscos

- A imagem é servida por terceiro (`hub.xerj.org`); se sair do ar, o selo aparece quebrado no GitHub. Sem efeito no app.
