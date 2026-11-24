# Regras em prosa que viram teste — issue #646

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `test/issue-646-prose-rules` (worktree `.claude/worktrees/prose-rules-646`, base `origin/main` `f07132c1`),
commits com a identidade `claude <claude@anthropic.com>`. Parte da #644.

## Objetivo

Regra que um teste consegue verificar deixa de depender de o agente ler e lembrar; e o `CLAUDE.md` passa a reprovar quando
cita algo que não existe mais.

## Fatos confirmados (2026-11-24, `f07132c1`)

- Conferência do `CLAUDE.md` por script: 193 identificadores entre crases, **nenhum ausente** do código; nenhum link
  quebrado. A única afirmação falsa achada ("a suíte só roda no Windows") foi corrigida na #649.
- "Monitor por `workAreaForPosition`, nunca `defaultScreenDevice`" é ampla demais: há 5 usos legítimos (3 arquivos:
  `ScreenLocator.kt`, `DiagnosticsExport.kt`, `WindowOpacityPreferences.kt`), nenhum escolhendo o monitor de uma janela
  sem passar pelo localizador.
- Sem violação em produção hoje: `AlertDialog`; `org.jetbrains.compose.resources`; `GlobalScope`;
  `currentSystemDefault()`; `RoundedCornerShape` com dp literal fora do `AppTheme.kt` (que usa 4, 6, 8 e 10).
- `X509TrustManager` só em `SystemTrustStore.kt`. Animação infinita em 6 arquivos: `ApiUsageCard.kt`,
  `ApiUsageCardHeader.kt`, `AppGargantuaRing.kt`, `AppUsageRing.kt`, `GargantuaHorizonBody.kt`,
  `SessionPulseIndicator.kt`.

## Classificação (recorte desta issue)

| Regra do `CLAUDE.md` | Destino |
|---|---|
| Camadas, tamanho, `Surface`, `UiState`, segredo no `toString`, `runCatching`, `SerialName`, idioma inline, comentário | já têm teste |
| Diálogo é `AppDialog`, nunca `AlertDialog` | R1, teste |
| Raio com teto 10, dp literal só no tema | R2, teste |
| Fonte do classpath, nunca `composeResources` | R3, teste |
| Escopo com dono, nunca `GlobalScope` | R4, teste (regra implícita em "escopos com `SupervisorJob`") |
| Fuso sempre `America/Sao_Paulo` | R5, teste: sem `currentSystemDefault()` |
| Nunca "confiar mesmo assim" no certificado | R6, teste: `X509TrustManager`/`HostnameVerifier` só em `SystemTrustStore.kt` |
| Animação infinita só atrás de `AppMotionPolicy.continuous` | R7, teste: lista fechada de arquivos (o uso da política é revisão) |
| Chave de fonte nunca por variável de ambiente | R8, teste: `getenv` sem literal de fonte com chave |
| `defaultScreenDevice` | R9, teste: só os 3 arquivos; texto corrigido |
| `CLAUDE.md` cita o que existe | R10, teste |
| Decisões de produto, textos de tela, ordem das listas, precedência do design system | julgamento: ficam em texto |

## Decisões

- Classe nova `ProseArchitectureRulesTest` no pacote `architecture`: o nome casa com `*ArchitectureRulesTest*`, que já está
  no recorte local obrigatório. `ArchitectureRulesTest.kt` tem 814 linhas e não cresce.
- Mensagem de falha diz **o que usar no lugar**. Toda regra tem um exemplo que reprova e um que passa.
- A regra encurta no `CLAUDE.md` só onde o teste a garante inteira.

## Atividades

- **A01** — este plano e a classificação.
- **A02** — R1 a R9. **A03** — R10. **A04** — `CLAUDE.md` encurtado e a frase do `defaultScreenDevice`.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | script de conferência e `grep` por regra | números acima |

## Problemas em aberto e riscos

- R7 e R9 são listas fechadas: arquivo novo legítimo exige editar o teste. É o ponto: vira decisão visível no diff.
- As 93 linhas do `CLAUDE.md` não foram todas convertidas; o que sobra depende de julgamento ou de harness que não existe.
