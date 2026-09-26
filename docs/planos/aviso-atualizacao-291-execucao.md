# Aviso de atualização mais claro — issue #291

Plano de execução. A issue traz três capturas da barra HUD com a v40.1.0 pronta e três
reclamações.

## 1. Diagnóstico

1. **A ação "Reiniciar… →" não parece botão.** No balão da engrenagem era `HudUpdateActionRow`:
   texto verde com seta sobre fundo transparente, e só o hover revelava o alvo. A faixa do modo
   padrão (`AppUpdateBanner`) tinha o mesmo desenho, com `Text("$label →")` e a faixa inteira
   clicável.
2. **O aviso ficava "direto na barra", sem forma de aviso.** No balão a frase era um `Text` verde
   solto em duas linhas. O usuário pediu algo mais sutil, "tipo um banner".
3. **O ícone no notch não diz "versão nova".** `HudUpdateBadge` usava `Icons.Rounded.SystemUpdate`,
   que é um celular com seta, e em 12dp parecia um retângulo verde qualquer.

## 2. Decisões

| # | Decisão | Por quê |
|---|---|---|
| D1 | No notch, a atualização vira **ponto na engrenagem**, e a faixa de anéis perde o ícone. | Escolha do usuário. O ponto não tenta dizer o quê, só que a engrenagem tem algo a mostrar, e o balão dela mostra. O notch recolhido fica igual com e sem atualização. |
| D2 | Parado, o **arco de dica** da engrenagem toma o tom do estado e ganha um ponto. | Com o notch parado, a engrenagem é só esse arco. Sem o sinal ali, o aviso só existiria para quem abrisse o notch. Cabe na caixa de 14dp que o arco já tem, sem mexer na geometria. |
| D3 | A frase inteira vai na **descrição da engrenagem**. | Cor nunca informa sozinha: é o desenho do `CardNoticeHint`. |
| D4 | O balão da engrenagem mostra `AppBanner` + `AppButton`. | São as primitivas do sistema. A cor fica só na barra de 2dp do banner, que é o "mais sutil" pedido. |
| D5 | O texto do balão vem **partido** de `updateBannerContent` (`headline` + `detail`). | O título de uma linha da faixa não cabe nos ~202dp de texto do banner. Dono único do texto: faixa e HUD não mantêm cópias. O percentual do download desce para o detalhe, porque "Downloading 138.100.100 — 100%" passa da largura. |
| D6 | **A faixa do modo padrão também ganha botão** e deixa de ser clicável inteira. | Escolha do usuário (escopo HUD + modo padrão). Com o botão dentro, a faixa clicável faria a mesma ação fora dele sem nada indicar. O botão é `DEFAULT`, porque `PRIMARY` é um por tela. |
| D7 | Nenhum clique no notch reinicia o app. | Mantém a #225: seria clique de rotina. |

**Custo aceito:** a faixa do modo padrão passa de ~34dp para ~46dp, pela altura de controle do
botão. O balão da engrenagem com a atualização pronta cresce ~48dp.

## 3. Atividades

| ID | Atividade | Arquivos | Validação |
|---|---|---|---|
| A01 | Conteúdo: `headline`/`detail` em `UpdateBannerContent`, nos quatro estados e nos dois idiomas. | `DashboardScreenWarnings.kt` | `AppUpdateBannerTest` |
| A02 | Faixa do modo padrão: ação como `AppButton` e a faixa sem clique. | `DashboardScreenWarnings.kt`, `AppUpdateBannerTest.kt`, `ComponentTest.kt` | `AppUpdateBannerTest`, `ComponentTest` |
| A03 | Balão da engrenagem: `AppBanner` + `AppButton`, e a geometria `HUD_APP_BALLOON_UPDATE_BANNER` + `AppChrome.control`. | `HudBalloon.kt`, `HudNotchGeometry.kt`, `HudWindow.kt` | `HudNotchTest` (altura medida = somada), `HudNotchTextFitTest` (100–200%) |
| A04 | Notch: sai o ícone da faixa, e entram o arco tingido e o ponto na engrenagem, com a frase na descrição. | `HudNotch.kt`, `HudHandles.kt`, `HudNotchGeometry.kt` | `HudNotchTest` |
| A05 | Documentação: CLAUDE.md, protótipo (3c, 4 e 4c), design system (`AppHudBar`, `AppBanner` com `ok`, `AppUpdateStrip`, kits) e ajuda. | `CLAUDE.md`, `docs/planos/prototipo-visual-opencode.html`, `docs/design-system/**`, `HelpCatalog.kt` | Revisão do diff |
| A06 | Suíte e capturas. | — | `gradlew.bat allTests`, `git diff --check` |

## 4. Pontos de situação

| ID | Atividade | Comando | Resultado |
|---|---|---|---|
| — | Plano a partir da issue e das 3 capturas | — | aprovado pelo usuário, com D1 e D6 escolhidas por ele |
| A01–A04 | Código e testes | `gradlew.bat desktopTest --tests HudNotchTest --tests HudNotchTextFitTest --tests AppUpdateBannerTest --tests ComponentTest` | 41 + 3 + 11 + 103 testes, 0 falhas |
| A03–A04 | Capturas | Sonda `ImageComposeScene` em densidade 2 (descartada, fora do repositório): faixa pronta e disponível, balão da engrenagem, notch parado e aberto | O botão da faixa e o do balão leem como botão. Arco verde com ponto parado, ponto no canto da engrenagem aberta. O título "pronta" da faixa continua cortado numa janela de 700dp, como antes |
| A05 | Documentação | revisão do diff | concluída. O JSX do design system não foi executado: o painel do navegador abre `file://` como imagem estática, e o `server/` está sem `node_modules` para o esbuild |
| A06 | Suíte | `gradlew.bat allTests` → exit 0; 2206 testes, 0 falhas, 0 erros. `git diff --check` limpo | concluída |
