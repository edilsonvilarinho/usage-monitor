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
