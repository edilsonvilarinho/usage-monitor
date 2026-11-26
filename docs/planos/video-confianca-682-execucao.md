# Vídeo e posts da jornada de confiança — issue #682

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-26, branch
`media/issue-682-video-confianca` (worktree `.claude/worktrees/video-confianca`, base `origin/main` `1f9933ab`),
commits com a identidade `claude <claude@anthropic.com>`. Skill `usage-monitor-social-video`.

## Objetivo

Contar, em vídeo curto (LinkedIn PT, X EN) e em post, a jornada da #644: da palestra "All Code Is Slop (Except Mine)" às
auditorias por mutação da #676 e da #679, com o que foi feito, o que aprendemos e o que melhorou no app.

## Fatos confirmados

Cada número do vídeo está em `video-confianca-644/video.json` (`facts`) e foi conferido na linha do plano de origem com
`sed -n <linha>p docs/planos/<plano>` em 2026-11-26: #645 `defeitos-que-escapam-645-execucao.md:65` (15,5%, 11/71); #646
`regras-em-prosa-646-execucao.md:14` (193); #647 `catraca-comentarios-647-execucao.md:13` (23.426, 21,5%); #648
`cancelamento-648-execucao.md:40` (7 pontos); #649 `linux-bloqueia-merge-649-execucao.md:13` (128/3 em 150); #650
`propriedades-hud-650-execucao.md:52` (10/10; o explorer devolveu 5/5, errado); #652 `app-real-no-ci-652-execucao.md:65` (15/15);
#657 `gravacao-hud-pr-657-execucao.md:111` (155/155); #669 `flash-preaquecimento-669-execucao.md:57` (4,1% → 0 em 960); #676
`auditoria-testes-tela-676-execucao.md:45` (67,1% → 96,0%); #679 `auditoria-suite-compose-679-execucao.md` (A03 a A06).

## Decisões

- Só as variantes pedidas: `short-pt` 16:9 (LinkedIn) e `short-en` 16:9 (X). O longo fica escrito (a validação exige) e não é renderizado.
- O infográfico da palestra é de terceiro, sem licença: não entra no vídeo nem no post. A palestra é citada pelo nome e pela autora.
- Números da palestra (PRs por mês, revert) não entram: a #644 já registrou que vêm do infográfico, sem verificação.
- A chave da OpenRouter fica em arquivo temporário fora do repositório e entra só por variável de ambiente.

## Atividades

- **A01** este plano. **A02** `video.json`, `roteiro.md`, `social.md`; `make.mjs check` e prévia sem API.
- **A03** aprovação do dono do roteiro e da folha de quadros. **A04** voz e render das duas variantes; validação pelo MP4.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | `sed -n` nas linhas citadas acima | 11 números conferidos; 1 divergência do explorer corrigida (#650: 10/10) |
| A02 | `make.mjs check` e `make.mjs preview` em `video-confianca-644/video.json` | curto PT 622 e EN 615 caracteres falados (teto 630), longo PT 3358 e EN 3247; prévia 60 s, `gap` 1,24 s (PT) e 1,33 s (EN); folha `build/social-video/confianca-644/qa/sheet.jpg` sem texto cortado nem sobreposição; post do X 245 de 280 |
