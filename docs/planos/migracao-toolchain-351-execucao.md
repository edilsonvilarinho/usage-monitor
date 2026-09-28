# Migração de toolchain — Kotlin 2.4, Compose MP 1.12, kotlinx-datetime 0.8, Gradle 9 (#351) — execução

| | |
|---|---|
| **Modelo** | Claude Opus 5.5 — `claude-opus-5-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (desktop) |
| **Data** | 2026-09-28 |
| **Issue** | [#351](https://github.com/edilsonvilarinho/usage-monitor/issues/351) |
| **Branch** | `chore/toolchain-migration-351`, criada de `main` (`22f37f6`) |
| **Substitui** | PRs do Dependabot #350 (grupo `gradle-minor-patch`, que substituiu o #333) e #337 (Gradle wrapper) |

## Contexto

O Dependabot abriu o #350 com Kotlin 2.1.0 → 2.4.20, Compose MP 1.7.1 → 1.12.1, Ktor 3.0.3 →
3.6.0, kotlinx-serialization 1.7.3 → 1.11.0, kotlinx-datetime 0.6.1 → 0.8.0, kotlinx-coroutines
1.9.0 → 1.11.0 e commons-compress 1.27.1 → 1.28.0, num commit só. O CI quebrou com 53 erros de
compilação, quase todos `Unresolved reference 'System'`: a partir da 0.7 o `kotlinx-datetime` não
tem mais `Clock` nem `Instant`, que foram para `kotlin.time`. São 243 arquivos com o import antigo
(226 de `Instant`, 66 de `Clock`).

O #337 (Gradle 8.6 → 9.8.0) passou a suíte, mas o CI não empacota, e com Kotlin 2.4 o Gradle 8.6
fica deprecado — ele deixa de ser opcional.

## Regras

- **Uma atividade, um commit**, cada um compilando e passando `gradlew.bat allTests` sozinho. Se
  duas atividades só fecharem juntas, o commit junta as duas e o motivo fica na linha dela.
- A tabela abaixo é atualizada no mesmo commit da atividade, com o comando e o resultado medido.

## Atividades

- **A0** — este plano e a linha de base na `main`.
- **A1** — Kotlin 2.1.0 → 2.4.20 (plugins `multiplatform`, `serialization` e `compose-compiler`),
  Compose e kotlinx-* intactos.
- **A2** — kotlinx-coroutines 1.9.0 → 1.11.0.
- **A3** — kotlinx-serialization 1.7.3 → 1.11.0.
- **A4** — Ktor 3.0.3 → 3.6.0.
- **A5** — kotlinx-datetime 0.6.1 → 0.8.0 com `Clock`/`Instant` migrados para `kotlin.time` (um
  commit: a versão e o import não compilam separados).
- **A6** — acessores `compose.*` do plugin → coordenadas explícitas no version catalog, ainda na
  1.7.1; `material-icons-extended` fixado em 1.7.3.
- **A7** — Compose Multiplatform 1.7.1 → 1.12.1.
- **A8** — mídias de ajuda regeneradas, se o Compose novo mudar o desenho.
- **A9** — Gradle wrapper 8.6 → 9.8.0.
- **A10** — commons-compress 1.27.1 → 1.28.0.
- **A11** — empacotamento nas três plataformas (sem commit de código).
- **A12** — validação manual em Windows e Linux (X11), documentação e fechamento.

## Pontos de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A0 — plano e linha de base | `gradlew.bat allTests --rerun-tasks` na `main` (`22f37f6`), Windows 11, JDK 17, heap de 3 GB do `gradle.properties` | `BUILD SUCCESSFUL in 5m 41s`, 14 tarefas executadas; 2354 testes, 0 falhas, 0 ignorados |
| A1 — Kotlin 2.4.20 | `gradlew.bat compileKotlinDesktop compileTestKotlinDesktop`; `gradlew.bat allTests` | Compila com o Compose 1.7.1 (a saída de emergência não foi usada), 3m 2s. O KGP avisa `Deprecated Gradle Version` (8.6; mínimo vira 8.14.4 no Kotlin 2.5) — resolvido na A9. 76 avisos de compilação (50 de opt-in `ExperimentalCoroutinesApi` nos testes), nenhum erro; o heap de 3 GB bastou. `BUILD SUCCESSFUL in 4m 43s`, 2354 testes, 0 falhas |
