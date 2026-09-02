# Modal de ajuda — plano de execução

Issue [#184](https://github.com/edilsonvilarinho/usage-monitor/issues/184): uma janela de ajuda
dentro do app com todas as funcionalidades, cada uma com descrição detalhada, instrução de como
ativar e um GIF de apresentação.

## Problema

Não existe nenhuma porta de ajuda no app — `grep -rn "HelpOutline\|\"Ajuda\"" src` não devolve nada.
O que documenta o produto é o `README.pt-BR.md` (§ Recursos, § Telas), que vive fora do app e é
invisível para quem instalou pelo `.exe` e nunca abriu o repositório.

As funcionalidades mais afetadas são justamente as que **precisam ser ligadas** e não se anunciam na
tela: barra HUD, modo somente cards, alertas na bandeja, atualização automática, integração com time,
orçamento mensal. Sem uma lista, elas só são descobertas por acidente — ou nunca.

## Decisões

1. **GIFs embutidos no app**, em `src/desktopMain/resources/help/`, gerados offscreen pelo mesmo motor
   de `img/tour.gif`. Buscar do GitHub sob demanda esbarraria no proxy corporativo da #174 e faria de
   "mídia indisponível" o estado comum numa máquina offline. Custo estimado: ~100 KB por demo
   (640×400, ~50 quadros), ~1,2 MB somados ao jar, que hoje tem 5,3 MB.
2. **Doze tópicos**, um por bloco da seção "Recursos" do README, com o texto derivado dele. Vinte e
   cinco tópicos granulares seriam vinte e cinco blocos bilíngues e ~2,5 MB de mídia para manter em
   sincronia com a UI a cada mudança de tela.
3. **Três portas: ícone no rodapé, item na bandeja e `F1`.** Mesmo raciocínio já registrado para o
   modo somente cards — o rodapé some em somente-cards e no HUD, e aí a bandeja e o teclado são o que
   resta. `F1` não colide com `Ctrl+Shift+M` nem com `Ctrl+Shift+H`.
4. **Os GIFs saem só em português.** As capturas do README também são PT. Gerar os dois idiomas
   dobraria os arquivos para uma tela cujo conteúdo — o texto — já é bilíngue. Custo aceito e
   registrado aqui, não escondido.
5. **O laço de quadros mora em `desktopMain`, nunca no composable de conteúdo.** Animação infinita
   trava o `waitForIdle` dos testes de componente; `HelpContent` recebe o quadro pronto e é
   exercitado com quadro estático.
6. **Proxy corporativo não é tópico**, é passo de ativação do tópico 1: não é funcionalidade que se
   demonstre, é pré-requisito de rede das fontes remotas.

## O que foi verificado antes de decidir

- **Compose não anima GIF, mas o Skia sim.** `org.jetbrains.skia.Codec` (skiko **0.8.18**, já no
  classpath) expõe `frameCount`, `getFrameInfo(i).duration` e `readPixels(bitmap, frame, priorFrame)`
  — lido no `skiko-awt-0.8.18-sources.jar`. `priorFrame` é **otimização, não correção**: sem ele o
  codec decodifica sozinho a cadeia de quadros requeridos, o que num GIF delta de 50 quadros custa
  O(n²) por tique.
- **`org.jetbrains.skia.Bitmap.asComposeImageBitmap()` embrulha o mesmo bitmap** —
  `SkiaBackedImageBitmap(this)`, em `ui-graphics-desktop-1.7.1`. Mutar o bitmap de trabalho a cada
  quadro mutaria a imagem exibida sem invalidar nada. O quadro publicado tem de ser cópia imutável,
  com o bitmap de trabalho retido para servir de `priorFrame`.
- **`GifEncoder` já grava o que o `Codec` sabe compor**: paleta global, quadros delta com
  `disposalMethod = doNotDispose` e índice transparente.
- **`AppSettingsNav` é genérico** (`AppStructure.kt`): recebe `List<AppTab>`, índice e `onSelect`.
  Serve de trilho do modal sem primitiva nova.
- **`ReleaseNotesWindow.kt` é o molde da janela** e **`CliSessionsGlossary.kt` é o molde do catálogo
  bilíngue** — enum de termos, `readingOrder` e entradas PT/EN em `presentation/ui/`.

## Riscos declarados

1. **Mídia e UI saem de sincronia** quando uma tela muda e o GIF não é regerado. Mitigação: os GIFs
   nascem dos composables reais por `gradlew generateHelpMedia`, mesma disciplina de
   `generateScreenshots`, com o comando documentado no README.
2. **Peso do instalador.** Se um lote passar muito de ~100 KB por demo, cortar quadros ou duração
   antes de commitar. O tamanho de cada arquivo é registrado na tabela abaixo.
3. **A saída do gravador não é determinística byte a byte** — ele usa `Thread.sleep` real. Nenhum
   teste compara bytes de GIF; o teste do decodificador gera o próprio GIF sintético.

## Tópicos

| # | Tópico | Absorve |
|---|---|---|
| 1 | Dashboard e integrações | ligar fonte, informar chave em Configurações → APIs, proxy da #174 |
| 2 | Histórico e previsão | tendência, média por hora, projeção, comparativo período a período |
| 3 | Sessões do Claude Code | veredito de saúde, custo estimado, copiar `claude --resume` |
| 4 | Resumo por eixo | projeto, modelo, branch, ferramenta, ritmo de queima, grade de atividade |
| 5 | Orçamento mensal | teto em USD, créditos da Anthropic em linha separada |
| 6 | Alertas e bandeja | limiares, silêncio, sessão saturada, sessão sem resposta (#177) |
| 7 | Exportação e relatório PDF | CSV, JSON e o PDF do recorte em tela |
| 8 | Visão de time | servidor self-hosted, chave, apelido, tendência de 30 dias |
| 9 | Presença ao vivo | online × trabalhando agora |
| 10 | Modos de janela | barra HUD (`Ctrl+Shift+H`) e somente cards (`Ctrl+Shift+M`) |
| 11 | Aparência e janela | tema, idioma, escala 80–150%, opacidade, sempre visível, auto-start |
| 12 | Atualização automática | troca em segundo plano e novidades da versão |

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A01 | Plano e comentário-índice na issue | `gh issue comment 184` | plano criado; comentário-índice `5503612844`, editado a cada atividade por `gh api -X PATCH repos/:owner/:repo/issues/comments/5503612844` |
| A02 | Catálogo bilíngue dos 12 tópicos, sem UI | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.HelpCatalogTest"` | `BUILD SUCCESSFUL`, 6 testes. Os passos de ativação citam o rótulo real do controle — `"Teto mensal em USD (vazio desliga)"`, `"Sessões CLI desta conta"`, `Ctrl+Shift+H` —, lidos de `AlertSettingsSection.kt`, `ApiUsageCardFormatting.kt` e do `onKeyEvent` de `Main.kt`. A primeira redação usava segunda pessoa ("as integrações que você usa") e foi reescrita em voz impessoal antes do commit — regra de conteúdo do design system, que o texto do app inteiro segue |
| A03 | `HelpContent`: trilho de tópicos, demo, descrição e passos | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HelpContentTest"` | `BUILD SUCCESSFUL`, 6 testes. **A faixa da demo trocou de `aspectRatio` para altura fixa de 260dp**: com a proporção 640×400 ela media ~410dp na janela de 900dp e empurrava a seção "Como ativar" inteira para fora da vista — três asserts falharam com `not displayed` antes da troca. Trilho de 200dp e não os 150dp default: "Dashboard e integrações" não cabe em 150. Seção `14b` nova no protótipo mais o link em `nav.index`; tags balanceadas (17/17 `div`, 19/19 `span`, 13/13 `button`) |
