# Profundidade, movimento e HUD em notch — execução

## Contexto

O app lia como "chapado" e o movimento, como "sem fluidez". As duas coisas eram regra escrita, não
acidente: o design system proibia sombra em card (`AppElevation.card = 0`), gradiente, translucidez e
animação contínua, e as quatro superfícies ficavam dentro de ~14% de luminância — o hover do card ia
de `#1B1818` a `#211E1E`, que ninguém enxerga. Não havia `spring()` em lugar nenhum: a barra de cota
saltava de largura e cor no quadro da coleta, o reordenar de cards encaixava seco, abas, segmentados,
menu e diálogo trocavam sem transição, e o `AnimatedContent` do Dashboard e do Histórico ignorava o
argumento da lambda e desenhava o estado atual nos dois slots. A HUD redimensionava a janela AWT a
cada quadro.

Referências visuais pesquisadas: **Codenotch** (notch escuro colado à borda, um anel por fornecedor,
hover desdobra um balão com barras cápsula e "Resets in…") e **ai-usagebar** (cards com barras
cápsula, marcador de ritmo, "Next update in 2m").

## Decisões do usuário

1. **Evoluir o design system**, não trocar de linguagem: Plex, paletas e primitivas ficam; as regras
   de profundidade e movimento são reescritas.
2. **HUD vira notch de borda com anéis.**
3. **Animação contínua permitida atrás de política**, desligada em testes e geradores.
4. **Janela da HUD transparente, com cantos e sombra desenhados pelo Compose**, sem acrílico nem JNA.

## Atividades

| # | Atividade | Estado |
|---|---|---|
| C1 | Tokens de motion, `AppMotionPolicy` e "Reduzir animações" | feito |
| C2 | Profundidade: `AppDepth`, `AppSurfaceLadder`, brilho e highlight | pendente |
| C3 | Seleção animada: aba, segmentado, navegação lateral, chip | pendente |
| C4 | Overlays que entram e saem: menu, tooltip, diálogo | pendente |
| C5 | Estados de interação: foco, hover em camada, pressão | pendente |
| C6 | Números animados e recarga do card | pendente |
| C7 | `AppStateCrossfade` e o bug do `AnimatedContent` | pendente |
| C8 | Expandir/recolher e banners | pendente |
| C9 | Movimento da grade de cards | pendente |
| C10 | Limpeza de movimento do `ApiUsageCard` | pendente |
| C11 | Spike da janela transparente | pendente |
| C12 | Modelo puro da HUD | pendente |
| C13 | HUD em janela própria | pendente |
| C14 | Geometria de borda e migração da posição | pendente |
| C15 | O notch com anéis | pendente |
| C16 | Indicadores contínuos atrás da política | pendente |
| C17 | Verificação final | pendente |

## Pontos de situação

| Data | Atividade | Modelo | Comando | Resultado |
|---|---|---|---|---|
| 2026-09-24 | C1 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.theme.*" --tests "com.usagemonitor.ReducedMotionPreferencesTest" --tests "com.usagemonitor.ui.AppStatesTest" --tests "com.usagemonitor.ui.ComponentTest"` | Verde. Primeira passada teve 2 falhas nos testes novos de bitmap da barra: diferiam só os 4 pixels de canto do recorte arredondado, cujo alfa de antialiasing varia com o número de quadros compostos. O teste passou a ignorar os cantos; a largura do preenchimento não passa por eles. |

## C1 · Tokens de motion e política

- `AppMotion.Springs` (`GENTLE` 1.0/400, `SNAPPY` 1.0/1500, `EXPRESSIVE` 0.75/600), `exit = 90`,
  `emphasizedEasing`. `AppMotionPolicy` (`Static`, `Live`, `Reduced`) e `LocalAppMotionPolicy`.
- `appSpringSpec`/`appTweenSpec` são funções puras; `appSpring`/`appTween` as leem da composição.
  Com `reduced` as duas viram `snap()`.
- `AppTheme(motion = AppMotionPolicy.Static)` por default. O `Main` passa
  `AppMotionPolicy.forPreference(reducedMotion)` às oito chamadas de `AppTheme` dele e às janelas de
  Ajuda e Novidades.
- Primeiros consumidores: `AppProgressTrack` (largura por `GENTLE`, cor por tween) e `AppSwitch`
  (botão por `SNAPPY`; trilho, borda e botão mudam de cor juntos).
- **Por que ignorar os cantos no teste de bitmap.** A cena que animou compõe mais quadros que a que
  nasceu parada, e o antialiasing do recorte arredondado acumula alfa diferente nos quatro pixels de
  canto. O que o teste afirma é a largura do preenchimento; comparar os cantos testaria o compositor.
