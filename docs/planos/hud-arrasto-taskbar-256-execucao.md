# HUD: arrasto estável e sobreposição da barra de tarefas — issue #256

Base: `b600db0 fix: keep HUD visible outside hover`.

O commit base já removeu o recolhimento ao ponto. A correção desta issue congela a HUD em uma
linha durante o arrasto e usa os limites físicos do monitor somente para a HUD, permitindo que ela
ocupe a região da barra de tarefas.

| ID | Atividade | Commit / validação | Resultado |
|---|---|---|---|
| A01 | Plano e rastreador publicados na issue | comentário `#issuecomment-5658933926` | concluída |
| A02-A05 | Arrasto congelado, expansão restaurada e limites físicos aplicados | `bba5d85`; `desktopTest` focado | concluída |
| A06 | Documentação normativa sincronizada | commit desta documentação | concluída |
| A07 | Validação Windows | `./gradlew.bat allTests` + revisão do diff | concluída |
| A08 | Validação Linux/macOS | ambientes não disponíveis nesta máquina Windows | não executada |
| A09 | Rastreamento da issue | comentário rastreador atualizado | concluída |
| A10 | Suíte final e encerramento | `allTests`, `git diff --check` | concluída |

## Decisões

- A HUD continua sempre no topo somente no modo HUD.
- Durante `hudDragging`, a composição e a janela permanecem na geometria de uma linha.
- O hover volta a expandir depois do `onDragEnd`.
- Janelas normais continuam limitadas por `maximumWindowBounds`.
- A confirmação efetiva de sobreposição depende do gerenciador de janelas de cada sistema; não será
  declarada como validada sem teste real.
