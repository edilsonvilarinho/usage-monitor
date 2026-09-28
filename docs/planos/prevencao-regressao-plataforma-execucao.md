# Prevenção de regressões visuais por plataforma (#342) — execução

## Contexto

[Issue #342](https://github.com/edilsonvilarinho/usage-monitor/issues/342). A #340 — balão da HUD
cortado no elementary OS (X11), regressão da #294 publicada na v41.0.0 — passou por ~2.350 testes
verdes: o defeito estava no sistema de janelas, e nada no processo exigia abrir o build no Linux
antes do release. O plano leva para cá o que o [ai-usagebar](https://github.com/akitaonrails/ai-usagebar)
e o [Codenotch](https://github.com/vinzdg/codenotch) fazem: dizer em que plataforma se testou,
escrever o que o CI não exercita e testar no Linux antes da tag.

## Decisões do usuário

1. Usar as duas referências como base (pesquisa registrada na #342).
2. Fora do escopo: teste de janela real com Xvfb para a #340 (nenhuma referência faz; o Xvfb não
   tem o compositor do Gala) e o `hitTest` do Codenotch (sem equivalente no AWT/X11).

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A1 | "Platform reality" no `CONTRIBUTING.md`; PR template pede plataformas testadas, teste que falha na `main` e validação no Linux ao tocar host de janela; linha no `CLAUDE.md` | leitura do diff (`git diff`) | só documentação; sem teste a rodar |
| A2 | Seção "Fora do alcance dos testes" no `docs/hud-notch.md`: tabela comportamento × plataforma, só com medições já registradas no documento (célula vazia = não medido); link do `CONTRIBUTING.md` passa a apontar para ela | leitura do diff | só documentação |
| A3 | Job `tests-linux` no `ci.yml` (ubuntu-latest, `xvfb-run -a ./gradlew allTests -PtestForks=3`), depois do `tests` para reusar o filtro, `continue-on-error` e fora do `verified-tree` até provar verde — o comentário do job `tests` registra que a suíte nunca rodou headless em Linux | sem validador de YAML local; o spike é o primeiro run no PR | resultado do run na linha A3b |
| A4 | `HudWindowDiagnostics` grava em `diagnostics/hud-window.jsonl`, uma vez por processo, se a plataforma usa o recorte (`enabled`/`skipped`), falha de `setShape` e `shape = null` sem efeito do lado do Java (não pega o caso da #340, só visível na tela); mesmos limites de corte do `startup.jsonl`. `bug_report.yml` pede desktop/gerenciador de janelas e o `hud-window.jsonl` | `gradlew.bat desktopTest --tests "com.usagemonitor.HudWindowDiagnosticsTest" --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.HudNotchGeometryTest"` | verde. `gradlew.bat run` não feito: o app instalado estava aberto e a segunda instância só pede foco e sai — conferir a linha no primeiro uso da HUD com a versão nova |
| A5 | Skill de release: antes da tag, se `HudWindow.kt`, `DesktopWindowFrame.kt`, `Main.kt` ou `*WindowHost*.kt` mudou desde a última tag, lista as linhas de "Fora do alcance dos testes" e pergunta se o build foi aberto no Linux — a única confirmação da skill, declarada no guardrail | `git diff --name-only v40.2.0..v41.0.0` e `v41.2.0..v41.3.1` filtrados pelo padrão da regra | v41.0.0 (o release que quebrou a HUD): `HudWindow.kt`, `Main.kt`, `MainWindowHost.kt`, `SettingsWindowHost.kt` — teria parado. v41.3.1: `HudWindow.kt` — também |
