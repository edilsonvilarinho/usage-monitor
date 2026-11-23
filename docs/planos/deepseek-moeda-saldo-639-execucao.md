# Saldo DeepSeek na moeda com fundos — issue #639

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-23, branch `fix/issue-639-deepseek-currency`,
commits com a identidade `claude <claude@anthropic.com>`. Implementação delegada ao `backend-dev`; testes ao
`test-runner`.

## Objetivo

O card do DeepSeek mostra saldo 0 quando a entrada `USD` de `balance_infos` está zerada e outra moeda (CNY) tem
fundos. A seleção passa a preferir a entrada com saldo, sem mudar nada para quem tem USD com saldo ou uma moeda só.

## Fatos confirmados (2026-11-23, `05b400d7`)

- `src/commonMain/kotlin/com/usagemonitor/data/mapper/DeepSeekMapper.kt:19-21`: `firstOrNull { it.currency == "USD" }`,
  senão a primeira entrada; nenhuma checagem de saldo. Lido com `sed -n 1,80p`.
- A moeda escolhida vai para as duas cotas (`DeepSeekMapper.kt:33`, issue #195): trocar a entrada troca a moeda exibida
  junto, sem ajuste na apresentação.
- `DeepSeekBalanceInfoDto` (`data/dto/DeepSeekBalanceDto.kt:13-18`) traz `total_balance`, `granted_balance` e
  `topped_up_balance` como texto.
- Relato externo do mesmo defeito: vinzdg/codenotch#413 (conta com carteira USD zerada e CNY com saldo → 0%).
- `docs/integrations.md`, seção "DeepSeek": "Values are treated as USD" — desatualizado desde a #195.

## Decisões

- **Regra de seleção:** primeira entrada com `total_balance` > 0, preferindo `USD` entre as positivas; sem nenhuma
  positiva, a regra antiga (USD, senão a primeira). Só o caso "USD zerado e outra moeda com saldo" muda.
- `total_balance` decide, não `topped_up_balance`: conta só com saldo concedido também tem fundos.
- Fora: exibir duas moedas ao mesmo tempo e conversão de câmbio.

## Atividades

- **A01** — este plano.
- **A02** — TDD no `DeepSeekMapperTest`: vermelho com USD 0 + CNY > 0 esperando CNY; casos de não regressão (USD > 0
  com CNY > 0 → USD; tudo zero → USD; só CNY → CNY); correção no mapper; `docs/integrations.md` atualizado.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | `git worktree add .claude/worktrees/deepseek-639 -b fix/issue-639-deepseek-currency origin/main` | plano escrito |
| A02 | vermelho: `gradlew.bat desktopTest --tests "*DeepSeekMapperTest*" --tests "*ArchitectureRulesTest*"` | `DeepSeekMapperTest.kt:173` `prefers the currency with balance when USD is empty`: `expected:<10000> but was:<0>` |
| A02 | verde: mesmo comando | BUILD SUCCESSFUL; `DeepSeekMapperTest` 11/11 (4 novos), `ArchitectureRulesTest` 28/28 |

## Problemas em aberto e riscos

- Não há resposta real de `/user/balance` com USD zerado e CNY com saldo; o defeito está confirmado no código e no
  relato do codenotch, que lê outra API (a da plataforma web). A regra não muda nada quando USD tem saldo ou há uma
  moeda só, por isso o risco de regressão é baixo.
