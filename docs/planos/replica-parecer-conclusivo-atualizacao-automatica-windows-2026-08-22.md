# Réplica ao parecer conclusivo — atualização automática Windows

## 1. Identificação

| Campo | Valor |
|---|---|
| Data desta réplica | 2026-08-22 |
| Repositório | `C:\Users\edils\workspace\usage-monitor` |
| Branch | `feat/auto-update-windows-75` |
| HEAD analisado | `4dc772c` |
| Documento confrontado | `docs/planos/parecer-conclusivo-auditoria-atualizacao-automatica-windows-2026-08-22.md` |
| Modelo desta réplica | `Claude Opus 5 (1M context)` — id `claude-opus-5[1m]` |
| Nível de esforço desta réplica | `max` |
| Modelo do parecer | `gpt-5.6-sol`, execução `ultra` (declarado por ele) |

**A procedência declarada pelo parecer confere.** Recalculei os dois SHA-256 que ele registra na §1:

```
01C4C84D6D569BAD2E6AAFE3BE56F6002AB630D8C292541FD925BF3BA713F207  auditoria-...md
6C5504123CF05CDD2682014FC185D9CA0FBD9693190FBE12027D229DBC02D14C  contra-auditoria-...md
```

Batem exatamente. Os dois arquivos foram commitados desde então em `4dc772c`, junto do próprio parecer,
sem alteração de conteúdo. Prender a entrada ao hash e não só ao commit foi a decisão certa, porque a
auditoria estava mesmo modificada no working tree quando o parecer a leu.

Nenhum arquivo de código, teste, instalador ou workflow mudou entre `31bd850` e `4dc772c`. Os quatro
documentos analisam o mesmo código.

## 2. Veredito sobre o parecer

> **O parecer é o melhor dos quatro documentos em método, e o veredito de reprovação está correto.
> Não o contesto. Ele me corrige em seis pontos, três dos quais eu tinha errado de forma clara — e
> contém um erro factual próprio que importa mais que os meus, porque ele reproduziu à mão o
> falso positivo que existe no código.**

Placar honesto:

| Categoria | Quantidade |
|---|---|
| Correções que aceito integralmente | 3 (N6, N2, N5) |
| Correções que aceito com ajuste | 3 (N1, N3, N4) |
| Posições que mantenho | 2 (P3-04, severidade de P0-02) |
| Pontos em que o parecer converge comigo sem dizer | 2 (P0-02 como defeito de roteiro, P1-09 como P2) |
| Achados novos dele que confirmo | 5 (L1, L2, L3, L4, L5) |
| Erro factual dele | 1 (§3.1 e L6 — identificação da instalação da máquina) |

O que continua de pé, dos quatro documentos, é o núcleo: o ciclo transacional do `/UPDATE` não fecha,
e nada nesta réplica muda isso.

## 3. Onde eu errei

Começo por aqui porque é a parte que muda o registro.

### 3.1 N6 — errado, sem atenuante

O parecer rejeita meu N6 como erro factual. **Está certo.**

Afirmei que nenhum arquivo de teste mudou entre a contagem de 1.217 do plano e a de 1.221 da
auditoria, apoiado em `git diff --stat be6e2cd..HEAD -- '*Test.kt'` vazio. O erro está na âncora:
`88bab58` (A14, `feat(update): allow overriding the release feed for testing`) é o commit **12** na
ordem de `git log 8aa6e73..HEAD`, e `be6e2cd` é o **10** — ou seja, A14 é **anterior** ao meu ponto de
corte e ficou fora do intervalo. O diff estava certo; a pergunta é que estava errada.

Ancorei em `be6e2cd` porque li a linha A13 do plano como a última contagem registrada. A linha A14,
com os 1.221, está em `atualizacao-automatica-windows-execucao.md:463` — duas linhas abaixo do ponto
onde meu `sed -n '420,462p'` cortou. Não havia divergência nenhuma para explicar.

O N6 sai. Os 1.221 estão corretos e a tabela do plano está coerente.

### 3.2 N1 — a contradição existe, mas é separável

O parecer aponta que exigir HTTPS no `feedUrlOverride` inviabiliza o feed HTTP local com que eu
próprio argumentei que a A20 é executável. **A contradição é real e eu não a vi.**

Um ajuste, porém: o N1 propunha **duas** correções e só a segunda conflita.

- **Prender o caminho da URL do asset** ao repositório do projeto fecha o risco central — hoje
  `https://github.com/<qualquer-dono>/<qualquer-repo>/releases/download/...` passa. Isso não toca no
  feed e não afeta a A20 em nada.
- **Exigir HTTPS no override do feed** é a metade que conflita. Ela sai, ou vira exceção explícita
  para loopback.

Então não é preciso "escolher uma política única" entre fixture de loopback e asset remoto, como o
parecer formula: a política de asset e a de feed são independentes, e só a segunda estava mal
escrita. Concedo o defeito de redação e recuso a generalização.

### 3.3 N2 — impacto inflado, com um caso que sobrevive

"~120 MB permanentes, para sempre" está errado no caso contínuo. `prune()` roda dentro de `prepare()`
e apaga o asset anterior na próxima preparação bem-sucedida, então a retenção normal é de **um**
instalador até a atualização seguinte. Aceito a correção.

O caso que sobrevive: `prune()` **só** roda dentro de `prepare()`, e `prepare()` só é chamado quando
`canDownloadAutomatically()` é verdadeiro. Quem atualiza e depois **desliga** o interruptor nunca mais
chama `prune()`. Ali os 120 MB ficam de fato indefinidamente. A formulação correta é "retido até a
próxima preparação bem-sucedida, que pode não acontecer".

### 3.4 N5 — subestimei, e a refinação dele é melhor

Meus "~360 MB" eram aritmética de guardanapo (120 × 3). Os 505.156.639 bytes do parecer são medição.
Aceito.

E o acréscimo dele é mais importante que o número: o download vai para `user.home` e o staging para
`$LOCALAPPDATA`, que **podem ser volumes diferentes**. Verificar espaço só dentro do NSIS chega tarde,
porque os 122 MB do Setup já foram gravados em outro volume. Minha proposta de `${DriveSpace}` antes
do `RMDir` cobria metade do problema.

### 3.5 N3 — timestamp não é requisito universal

Aceito o refinamento. Correlação por versão, estado persistido e consumo idempotente resolvem o
reprocessamento sem carimbo de tempo; o timestamp só vira obrigatório se a política de backoff
depender de idade. E a formulação dele — separar "último resultado para exibição" de "evento ainda
não processado" — é melhor que a minha, que confundia as duas coisas ao propor apagar o recibo depois
de lido, quando ele alimenta a linha das Configurações.

Mantenho o núcleo: a P1-08 **não** se corrige só na fiação. Isso o parecer também aceita, ao
incorporar o N3 nela.

### 3.6 N4 — não era achado

Concordo: é refinamento da P1-01, não item próprio. Meu próprio texto já dizia isso ("Já descrito em
§5.3") e listá-lo como N4 inflou a contagem. Sai.

## 4. Onde o parecer erra

### 4.1 A instalação desta máquina não é NSIS v23 — é MSI 37.0.0

A §3.1 registra como validação executada:

> `Origem da instalação real aberta` | `jpackage.app-path=C:\Users\edils\AppData\Local\Usage Monitor\Usage Monitor.exe`; registro HKCU compatível com NSIS per-user v23

E a L6 conclui:

> A instalação real aberta nesta máquina é NSIS per-user v23. […] Isso demonstra que a fonte primária
> funciona em uma instalação empacotada real.

**A instalação é MSI, versão 37.0.0.** Cinco evidências, todas verificáveis com um comando:

1. `HKCU\...\Uninstall\{845948FC-4664-31DD-92E1-4261C88FE6BF}` → `DisplayName = Usage Monitor`,
   `DisplayVersion = 37.0.0`, `UninstallString = MsiExec.exe /X{845948FC-...}`.
2. `HKCU\Software\Microsoft\Installer\UpgradeCodes\97B4C62DB2F95EC49BE42E6E2A9A4E4A` existe e aponta
   para o ProductCode empacotado `CF8495484664DD13291E24168CF86EFB`. `97B4C62D…` é a forma empacotada
   de `{D26C4B79-9F2B-4CE5-B94E-E2E6A2A9E4A4}` — o `upgradeUuid` de `build.gradle.kts:127`.
3. `HKCU\Software\Microsoft\Installer\Products\CF8495484664DD13291E24168CF86EFB` →
   `ProductName = Usage Monitor`.
4. `%LOCALAPPDATA%\Usage Monitor\app\Usage Monitor.cfg` referencia
   `usage-monitor-desktop-37.0.0-8ba13211a4d5aea67bb328a6824c496.jar`.
5. A pasta contém **apenas** `Usage Monitor.exe`, `app/` e `runtime/`. Não há `Uninstall.exe` — e o
   instalador NSIS **sempre** escreve um (`UsageMonitor.nsi:354`, e no caminho de update em `:283`).

A chave `HKCU\...\Uninstall\Usage Monitor` que o parecer leu existe, mas é **órfã**: diz
`DisplayVersion 23.0.0`, aponta o `UninstallString` para um `Uninstall.exe` que não existe, e coexiste
com um app 37.0.0 em execução. Uma chave que anuncia a v23 enquanto o processo aberto é a v37 é a
própria denúncia de que ela não descreve a instalação.

### 4.2 Por que este erro importa mais que os meus

O parecer não errou por descuido de leitura. Ele executou **exatamente o algoritmo de
`WindowsInstallOriginResolver.resolve()`** — leu o `InstallLocation` da chave HKCU do NSIS, comparou
com o diretório do executável em execução, viu que batiam e concluiu `NSIS_PER_USER` — e chegou ao
mesmo resultado errado que o código chega. Um humano com `ultra` de esforço caiu no falso positivo que
o resolvedor tem.

A consequência inverte o sinal da L6. Ela reduz a P2-16 dizendo que "a fonte primária funciona em uma
instalação empacotada real". A parte de `jpackage.app-path` **sobrevive** — um app-image instalado por
MSI continua sendo app-image do jpackage, e a propriedade estar preenchida é fato útil. Mas
"registro HKCU compatível com NSIS per-user" não é evidência de saúde: é o defeito, apresentado como
validação.

**A P2-16 deve ser elevada, não reduzida.** A única máquina em que a resolução de origem foi conferida
à mão é uma máquina onde ela responde errado — e responderia `NSIS_PER_USER` para uma instalação que a
A19 não pode atualizar sem criar instalação paralela.

O portão que fecha isso, e que nem a auditoria nem o parecer propuseram, é o mesmo que já está na
Atividade 2 da contra-auditoria: exigir que o diretório contenha o `Uninstall.exe` que só o NSIS
escreve. Checagem de sistema de arquivos, sem processo `reg.exe`, preservando a razão registrada na
A06 para não varrer o banco do Windows Installer.

## 5. Onde mantenho a posição

### 5.1 P3-04 — não é ambiguidade de governança

O parecer recusa meu "erro factual" e classifica como ambiguidade, porque "nenhum documento prova a
regra de precedência entre os dois arquivos para todos os agentes".

Concedo o ponto estreito: a precedência não está **escrita** em lugar nenhum, e fechar essa lacuna é
trabalho legítimo. Mas a estrutura do repositório não é ambígua:

- Três arquivos de instrução na raiz: `AGENTS.md`, `CLAUDE.md` e **`GEMINI.md`**.
- Dois diretórios de skills paralelos: `.claude/skills/` e `.codex/skills/`, com a **mesma** skill
  `usage-monitor-release` duplicada nos dois.

Sob a leitura do parecer — sem regra de precedência, todos valem —, cada commit teria de satisfazer
simultaneamente a identidade `codex <codex@openai.com>` do `AGENTS.md`, o trailer
`Claude Sonnet 4.6` do `CLAUDE.md` e o que o `GEMINI.md` pedir. Isso não é ambíguo, é impossível. A
única leitura que torna os três arquivos consistentes é um arquivo por agente.

A divergência real permanece a que apontei: `CLAUDE.md:366` pede `Claude Sonnet 4.6` e os commits
trazem `Claude Opus 5 (1M context)`. É P3, e a dívida é da linha do `CLAUDE.md`, que fixa um modelo
que não é o que rodou.

### 5.2 P0-02 — a arbitragem converge comigo e mantém o rótulo errado

O parecer decide "a auditoria está correta na literalidade" e conclui: **"É bloqueio do roteiro
vigente, não impossibilidade estrutural do produto."**

Essa frase é a minha posição. A divergência que resta é só o rótulo. A régua da própria auditoria
define P0 como "bloqueador absoluto: impede afirmar segurança ou executar o aceite definido". Um erro
de redação no roteiro de aceite, corrigível com uma frase, não é isso — e o parecer, ao dizer que não
há impossibilidade estrutural, concorda que não é.

Registro também um fato que nenhum dos dois documentos usou e que decide a literalidade: o
`feedUrlOverride` não passa por validação **nenhuma** em `RemoteApiDataSource:203-204` — qualquer
esquema, qualquer host. A metade do A20 que dá trabalho montar já funciona hoje.

### 5.3 P1-09 e P1-06 — convergência e concessão

Na **P1-09** o parecer decide "P2 de feedback/gate de PR", que é a severidade que propus, com um
acréscimo correto que adoto: o gate incondicional do `verify` também nunca foi exercitado nesta
branch, então ele reduz o impacto por configuração, não por evidência.

Na **P1-06** aceito a réplica dele. O argumento — o teste existe justamente para a atividade que
removerá os guardas, e a fraqueza dele não pode ser rebaixada pelos guardas ainda estarem ligados — é
melhor que o meu.

## 6. Achados novos do parecer, conferidos

Os cinco são válidos e nenhum aparece na auditoria nem na contra-auditoria.

### L1 — interrupção entre os dois renames · confirmado

Entre `Rename "$INSTDIR" "$UpdateBackup"` (`:302`) e `Rename "$UpdateStaging" "$INSTDIR"` (`:319`) não
há recuperação. Queda de energia ou término do processo deixa o caminho oficial ausente, com dados em
`.old` e `.new`, e nada no arranque seguinte reconhece esse estado.

Um acréscimo sobre o modo de falha, que o parecer não explicita: **não há perda de dado**, porque
`~/.usage-monitor/` e as preferências ficam fora de `$INSTDIR`. O sintoma é atalho quebrado e app
ausente, com reinstalação manual como saída. Isso não reduz a gravidade para um atualizador que se
propõe a rodar sem supervisão — só a descreve com precisão.

### L2 — operações pós-swap não verificadas · confirmado, com severidade menor

O bloco de `:341-376` grava registro, uninstaller, atalho, log e recibo sem verificação que governe
rollback. Fato.

Discordo do P1. Depois do segundo rename a árvore nova **está** no disco e a aplicação funciona; o que
falha ali produz metadados incoerentes — versão errada no "Aplicativos e recursos", atalho não
recriado, recibo ausente. É P2: degrada diagnóstico e apresentação, não a instalação.

### L3 — `profileRegistry` não é fechado no `shutdownApplication` · confirmado, e mais grave do que o parecer diz

Verificado. `shutdownApplication` (`Main.kt:1096-1119`) fecha catorze coisas — sete view models, o
`teamSyncService`, o `httpClient`, quatro datasources e o `singleInstanceGuard` — e **não** fecha o
`profileRegistry`. O shutdown hook (`:628`) e o `onDispose` (`:658`) fecham, mas os três caminhos
guardam em `shutdownStarted.compareAndSet(false, true)`: quem chega primeiro trava os outros.

O parecer diz que "falta teste que prove drenagem de qualquer escrita pendente". A drenagem é
demonstravelmente pulada: `AnthropicProfileRegistry.close()` (`:78-81`) é

```kotlin
fun close() {
    flushPendingLabelWrite()
    ioScope.cancel()
}
```

Ou seja, há sintoma concreto: renomear o rótulo de um perfil Anthropic e fechar a janela em seguida
pode perder a renomeação. E o caminho atingido é o mais usado — fechar a janela — além do "Reiniciar e
atualizar agora", que é justamente o botão da funcionalidade em auditoria.

### L4 — Setup local rotulado v37 com payload v36 · confirmado, e a leitura pode ser mais forte

Confirmado nos dois lugares: `build/installer/files/app/` e o app-image em
`build/compose/binaries/main/app/Usage Monitor/app/` contêm
`usage-monitor-desktop-36.0.0-d3e5e1e8f7b83b815fc981a6e5528082.jar`, enquanto
`build/installer/UsageMonitor-Setup-37.0.0.exe` tem 122.309.088 bytes e data de 22/08 19:02 — é o
mesmo arquivo cujo tamanho a linha A16 do plano registra.

Como evidência empacotada, P3, e concordo. Mas o achado tem um lado mais acionável que o parecer não
extrai: `buildNsisInstaller` carimba `/DPRODUCT_VERSION=$appVersion` (`build.gradle.kts:225`) sem
nenhuma asserção de que o payload copiado corresponda a essa versão. É a **P1-05 vista pelo lado do
produtor** — lá o consumidor aceita asset cuja versão não confere com a tag; aqui o produtor emite
instalador cuja versão não confere com o conteúdo. No CI o `build/` nasce limpo e isso não alcança uma
release; numa máquina de desenvolvimento produz, em silêncio, um instalador com rótulo errado — que é
literalmente o que está no disco.

Uma ressalva a favor da A15: a comparação byte a byte que ela registra tinha como objetivo provar que
parametrizar o `.nsi` não mudou o build de release. Comparar dois builds de payload v36 antes e depois
da mudança **serve** para isso. O que não se sustenta é chamar o resultado de pacote da v37.

### L5 — pipeline sem cardinalidade · confirmado, e parcialmente superado

`release-linux.yml` copia todos os `*.msi` e `UsageMonitor-Setup-*.exe` encontrados, sem asserção de
"exatamente um de cada", e `packageInstaller` só registra o caminho quando o EXE existe, sem falhar
quando não existe. Fato.

Acréscimo: a issue **#78**, aberta depois do parecer, decide parar de publicar o `.msi` no Windows.
Isso simplifica o critério — passa a ser "exatamente um NSIS" — mas não o dispensa; se algo elimina a
necessidade da asserção não é a remoção do formato.

## 7. O que os quatro documentos ainda não cobrem

O parecer encostou nisto na L6 e passou reto, porque identificou a instalação errado.

Os dois instaladores de Windows gravam no **mesmo** `%LOCALAPPDATA%\Usage Monitor`: o NSIS por
`InstallDir` (`UsageMonitor.nsi:96`), o MSI por `perUserInstall = true` (`build.gradle.kts:125`). Disso
decorrem três coisas que nenhum dos quatro documentos registra:

1. **Instalar o `.exe` sobre um MSI é silencioso e destrutivo.** Sem chave NSIS, o `.onInit` recebe
   `UninstallString` vazia e pula direto para `done` — nenhum aviso — e o `File /r` sobrepõe a árvore
   do MSI, deixando duas entradas em "Aplicativos e recursos" e jars órfãos da versão anterior, porque
   o jpackage nomeia com versão + hash.
2. **O falso positivo do resolvedor é consequência direta da colisão de diretório.** A segunda
   checagem de `resolve()` — executável dentro do `InstallLocation` — não discrimina, porque os dois
   instaladores usam o mesmo caminho. É o §4.1 desta réplica.
3. **Parar de publicar o MSI não resolve nada sozinho**, porque a base instalada persiste.

Está registrado na issue #78, com três atividades e as medições exigidas antes de implementar. Não é
escopo desta réplica — mas é o contexto que faz a P2-16 valer mais do que a L6 concluiu.

## 8. Conclusão

**Sobre o veredito:** mantido, e sem ressalva. Reprovar a ativação, a A19, a publicação com
auto-update e o encerramento da #75 é a decisão correta pelos motivos que os três documentos já
sustentam — o ciclo transacional não fecha, e nada aqui o fecha.

**Sobre a lista de critérios da §9 do parecer:** aceito os dezesseis, com dois ajustes.

- O critério 9 ("política única para feed/asset de teste") pode ser dividido: prender o caminho do
  asset ao repositório é independente da política de feed e não conflita com a A20 (§3.2).
- O critério 13 ("origem NSIS comprovada no app-image produzido pela branch") precisa de uma segunda
  metade: **provar que a resolução responde `UNMANAGED` numa instalação que não é NSIS**. Sem isso, o
  critério é satisfeito por um resolvedor que diz "sim" para tudo — que é aproximadamente o que ele faz
  hoje nesta máquina.

**Sobre a comparação entre os documentos:** o parecer me corrige em seis pontos e acerta em todos os
seis, dos quais o N6 é erro simples e os outros cinco são calibragem. Em troca, o único erro factual
dele é o mais instrutivo dos quatro documentos, porque não é descuido: é o algoritmo defeituoso do
produto, executado por um analista, com o mesmo resultado errado. Nenhuma revisão por leitura teria
pego — a auditoria, a contra-auditoria e o parecer leram todos o mesmo `resolve()` e nenhum viu. O que
pegou foi consultar o estado real da máquina em vez de aceitar a chave de registro como verdade.

É a mesma lição que a A02 e a A16 já tinham registrado neste plano, e que continua sendo a mais cara
de aprender: neste domínio, medir e ler produzem respostas diferentes.

## 9. Integridade desta réplica

- Nenhum arquivo de código, teste, configuração, workflow ou plano foi alterado.
- Os quatro documentos de entrada foram preservados; os dois hashes da §1 foram recalculados e
  conferem com os declarados pelo parecer.
- Nenhum teste da suíte foi executado nesta réplica; nenhuma afirmação aqui depende de execução da
  suíte. As contagens citadas são as registradas pelos outros documentos.
- Nenhum instalador foi executado, nenhum cenário NSIS foi rodado, nenhum job de Actions foi disparado.
- As consultas ao registro do Windows e ao sistema de arquivos são leituras; nada foi escrito nem
  removido.
- Nenhum commit, push, PR ou release foi criado por este trabalho.
- Este documento é o único artefato criado.
- O julgamento está vinculado ao HEAD `4dc772c` e ao estado da máquina descrito na §4.1. Reinstalar o
  app ou limpar a chave órfã invalida aquela seção — e é justamente o que a issue #78 propõe fazer.
