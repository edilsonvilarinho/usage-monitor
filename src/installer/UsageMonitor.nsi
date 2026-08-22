!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"

SetCompressor zlib

; -----------------------------------------------
; Semantica do NSIS medida nesta base (atividade A02 do plano de auto-update,
; docs/planos/atualizacao-automatica-windows-execucao.md). Medido com o makensis
; 3.x desta maquina, instalador compilado e executado de verdade -- nao e
; documentacao lembrada. Refazer as medidas antes de contrariar qualquer item.
;
; 1. MessageBox sem /SD EXIBE E BLOQUEIA mesmo com /S. O probe rodou 12s e foi
;    morto por timeout com o log parado antes do desvio. O MessageBox do .onInit
;    abaixo, portanto, TRAVA para sempre qualquer execucao silenciosa deste
;    instalador. Quem for rodar em /S precisa desviar dele antes.
;
; 2. Flag de erro:
;    - comando bem-sucedido NAO limpa a flag; ela e sticky ate ClearErrors ou
;      IfErrors (CreateDirectory e Rename com sucesso deixaram SET um SetErrors
;      anterior);
;    - IfErrors LIMPA a flag ao ler: duas leituras seguidas de um SetErrors dao
;      SET e depois clean;
;    - RMDir /r sobre diretorio inexistente NAO seta a flag;
;    - ${GetOptions} com a opcao ausente SETA a flag;
;    - ${GetOptions} "/UPDATE" CASA com "/UPDATEPID=123" e devolve "PID=123":
;      prefixo e ambiguo, opcoes precisam de nomes que nao sejam prefixo um do
;      outro (/UPDATE e /PID= servem, /UPDATE e /UPDATEPID= nao).
;    Consequencia: ClearErrors imediatamente antes da operacao cuja falha
;    importa, e IfErrors imediatamente depois -- que ja consome a flag.
;
; 3. Abort dentro de Section sob /S: exit code 2, .onInstFailed RODA,
;    .onInstSuccess nao roda, e as Sections seguintes nao rodam. Nao trava.
;    SetErrorLevel n + Quit sai com exit n e nao roda nenhuma das duas.
;
; 4. SectionSetFlags exige que a Function que a chama esteja declarada DEPOIS
;    das Sections -- indice de secao e resolvido em tempo de compilacao, e a
;    referencia adiantada nao compila. Guardar o corpo da Section por variavel
;    tem o mesmo efeito e nao exige reordenar o arquivo.
; -----------------------------------------------

; -----------------------------------------------
; General
; -----------------------------------------------
!ifndef PRODUCT_VERSION
!define PRODUCT_VERSION "37.0.0"
!endif

; Payload e destino parametrizaveis. Os defaults sao exatamente os caminhos que o
; buildNsisInstaller do Gradle usa hoje, entao o build de release nao muda: quem
; passa /D e o roteiro de cenarios (src/installer/test/Invoke-UpdateScenarios.ps1),
; que precisa compilar ESTE arquivo -- e nao uma copia dele -- contra um payload
; minusculo. Cenario que testa um .nsi paralelo nao testa o instalador que sai no
; release.
!ifndef APP_FILES_DIR
!define APP_FILES_DIR "..\..\build\installer\files"
!endif

!ifndef OUTPUT_FILE
!define OUTPUT_FILE "..\..\build\installer\UsageMonitor-Setup-${PRODUCT_VERSION}.exe"
!endif

; Estado do modo /UPDATE.
Var UpdateMode        ; 1 quando o instalador foi chamado com /UPDATE
Var UpdatePid         ; pid do processo que esta saindo; so vai para o recibo
Var UpdateStaging     ; $INSTDIR.new -- arvore nova antes da troca
Var UpdateBackup      ; $INSTDIR.old -- instalacao anterior durante a troca
Var PreviousVersion   ; DisplayVersion lido antes de o registro ser sobrescrito

; Parametrizavel pela mesma razao de APP_FILES_DIR, e com um motivo mais forte: a
; chave HKCU de desinstalacao, o atalho do Menu Iniciar e o do desktop derivam
; deste nome. Um cenario de teste rodando com o nome de producao APAGARIA o atalho
; e sobrescreveria o registro da instalacao real de quem roda a suite.
!ifndef PRODUCT_NAME
!define PRODUCT_NAME "Usage Monitor"
!endif

; O nome do valor na chave Run NAO deriva de PRODUCT_NAME -- ele e literal e o
; AutoStartManager do app le exatamente esta string. Por isso precisa de um
; !ifndef proprio: sem ele, um cenario de teste rodando com outro PRODUCT_NAME
; ainda assim sobrescreve a entrada de inicializacao REAL de quem roda a suite,
; apontando-a para o diretorio descartavel do teste. Aconteceu ao validar a
; atividade A16, e o valor teve de ser restaurado a mao.
!ifndef AUTO_START_VALUE_NAME
!define AUTO_START_VALUE_NAME "UsageMonitor"
!endif
!define PRODUCT_PUBLISHER "Usage Monitor"
!define PRODUCT_UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}"
!define LOG_FILE "$INSTDIR\install.log"
!define APP_ICON "..\desktopMain\resources\icons\app_icon.ico"
; -----------------------------------------------
; Installer attributes
; -----------------------------------------------
Name "${PRODUCT_NAME} ${PRODUCT_VERSION}"
OutFile "${OUTPUT_FILE}"
InstallDir "$LOCALAPPDATA\${PRODUCT_NAME}"
InstallDirRegKey HKCU "${PRODUCT_UNINST_KEY}" "InstallLocation"
RequestExecutionLevel user
Icon "${APP_ICON}"
UninstallIcon "${APP_ICON}"
!define MUI_ICON "${APP_ICON}"
!define MUI_UNICON "${APP_ICON}"

; -----------------------------------------------
; Pages
; -----------------------------------------------
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "license.txt"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\Usage Monitor.exe"
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

; -----------------------------------------------
; Languages
; -----------------------------------------------
!insertmacro MUI_LANGUAGE "PortugueseBR"
!insertmacro MUI_LANGUAGE "English"

; Evitar MUI_FINISHPAGE_RUN_TEXT com LangString aqui: esse caminho e o suspeito
; de esconder o checkbox no finish page em algumas builds.
LangString MUI_TEXT_FINISH_RUN ${LANG_PORTUGUESEBR} "&Iniciar o Usage Monitor agora"
LangString MUI_TEXT_FINISH_RUN ${LANG_ENGLISH} "&Launch Usage Monitor now"

; -----------------------------------------------
; Installer Functions
; -----------------------------------------------
Function .onInit
    SetShellVarContext current

    StrCpy $UpdateMode 0
    StrCpy $UpdatePid ""
    StrCpy $UpdateStaging "$INSTDIR.new"
    StrCpy $UpdateBackup "$INSTDIR.old"

    ${GetParameters} $R0
    ClearErrors
    ${GetOptions} $R0 "/UPDATE" $R1
    ${IfNot} ${Errors}
        StrCpy $UpdateMode 1
    ${EndIf}
    ; /PID= e nao /UPDATEPID=: medido na A02 que ${GetOptions} "/UPDATE" casa com
    ; "/UPDATEPID=123" e devolve "PID=123". Opcoes que sao prefixo uma da outra
    ; nao dao para distinguir.
    ClearErrors
    ${GetOptions} $R0 "/PID=" $UpdatePid

    ; Versao que esta instalada, lida ANTES de o registro ser sobrescrito: e ela
    ; que o recibo chama de previousVersion.
    ClearErrors
    ReadRegStr $PreviousVersion HKCU "${PRODUCT_UNINST_KEY}" "DisplayVersion"

    ${If} $UpdateMode == 1
        ; O caminho de update nao pergunta nada e nao desinstala nada. O
        ; MessageBox abaixo EXIBE E BLOQUEIA mesmo sob /S (medido na A02): passar
        ; por ele numa execucao silenciosa deixaria o processo pendurado num
        ; dialogo que ninguem ve, para sempre.
        Return
    ${EndIf}

    ; Check if already installed
    ReadRegStr $0 HKCU "${PRODUCT_UNINST_KEY}" "UninstallString"
    StrCmp $0 "" done notdone

notdone:
    ; /SD IDNO: uma execucao silenciosa que chegue aqui responde "nao" em vez de
    ; travar. Inerte no fluxo interativo, que continua perguntando.
    MessageBox MB_YESNO|MB_ICONQUESTION "${PRODUCT_NAME} ja esta instalado. Deseja remover a versao anterior?" /SD IDNO IDYES uninst IDNO done

uninst:
    ExecWait '$0'
    DeleteRegKey HKCU "${PRODUCT_UNINST_KEY}"
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${PRODUCT_NAME}"
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${AUTO_START_VALUE_NAME}"
    RMDir /r "$INSTDIR"

done:
    ${IfNot} ${Silent}
        !insertmacro MUI_LANGDLL_DISPLAY
    ${EndIf}
FunctionEnd

Function .onInstSuccess
    ${If} $UpdateMode == 1
        ; Exec e nao ExecWait: bloquear o fluxo de sucesso e o congelamento na
        ; tela final que a skill do instalador documenta. O recibo ja foi escrito
        ; na secao, antes daqui.
        Exec '"$INSTDIR\Usage Monitor.exe"'
    ${EndIf}
FunctionEnd

; Segunda linha de defesa. O desenho da secao ja deixa o $INSTDIR intacto em todo
; caminho de erro que ele controla, mas `File /r` sem /nonfatal ABORTA a
; instalacao por conta propria, sem passar por rotulo nenhum. Medido na A02:
; Abort dentro de Section sob /S roda este callback.
Function .onInstFailed
    ${If} $UpdateMode == 1
    ${AndIf} ${FileExists} "$UpdateBackup\*.*"
    ${AndIfNot} ${FileExists} "$INSTDIR\*.*"
        Rename "$UpdateBackup" "$INSTDIR"
        DetailPrint "Update failed; previous installation restored."
    ${EndIf}
FunctionEnd

; Recibo da tentativa, em $R5 (status) e $R6 (motivo). Escrito antes do
; relancamento e tambem nos caminhos de falha: atualizacao silenciosa que falha e
; invisivel por natureza, e sem isto nada no disco registra que houve tentativa.
Function WriteUpdateReceipt
    CreateDirectory "$PROFILE\.usage-monitor"
    ClearErrors
    FileOpen $R4 "$PROFILE\.usage-monitor\update-receipt.properties" w
    IfErrors receiptDone
    FileWrite $R4 "version=${PRODUCT_VERSION}$\r$\n"
    FileWrite $R4 "previousVersion=$PreviousVersion$\r$\n"
    FileWrite $R4 "status=$R5$\r$\n"
    FileWrite $R4 "reason=$R6$\r$\n"
    FileWrite $R4 "pid=$UpdatePid$\r$\n"
    FileClose $R4
receiptDone:
FunctionEnd

; -----------------------------------------------
; Installer Sections
; -----------------------------------------------
Section "Usage Monitor" SEC_APP
    SectionIn RO
    SetShellVarContext current

    StrCpy $UpdateStaging "$INSTDIR.new"
    StrCpy $UpdateBackup "$INSTDIR.old"

    ${If} $UpdateMode == 1
        Goto updateExtract
    ${EndIf}

    ; ---------- instalacao normal (interativa ou /S sem /UPDATE) ----------

    ; Limpar Run keys antigos (ambos os nomes ? migra??o de vers?es anteriores)
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${PRODUCT_NAME}"
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${AUTO_START_VALUE_NAME}"

    ; Create log file
    DetailPrint "Initializing installation..."
    FileOpen $0 "${LOG_FILE}" w
    FileWrite $0 "Installation started$\r$\n"
    FileWrite $0 "Install dir: $INSTDIR$\r$\n"
    FileClose $0

    SetOutPath "$INSTDIR"
    SetDetailsPrint none
    File /r "${APP_FILES_DIR}\*.*"
    SetDetailsPrint both
    Goto updateDone

    ; ---------- modo /UPDATE: extrair, entao trocar ----------
    ;
    ; A ordem e a garantia: nada destrutivo acontece antes de a arvore nova estar
    ; INTEIRA no disco. A unica janela em que a instalacao nao esta completa e a
    ; distancia entre dois Rename no mesmo volume, e a falha do segundo desfaz o
    ; primeiro. As Run keys nao sao tocadas aqui -- atualizacao silenciosa nao
    ; reimpoe escolha que o usuario desfez.
updateExtract:
    DetailPrint "Preparing update..."
    ClearErrors
    RMDir /r "$UpdateStaging"
    CreateDirectory "$UpdateStaging"
    IfErrors updateStagingFailed updateStagingReady

updateStagingFailed:
    StrCpy $R6 "staging-unavailable"
    Goto updateFailIntact

updateStagingReady:
    SetOutPath "$UpdateStaging"
    SetDetailsPrint none
    ; Sem /nonfatal de proposito: falha aqui ABORTA a instalacao, e como nada foi
    ; movido ainda, o $INSTDIR continua intacto. O .onInstFailed cobre o resto.
    File /r "${APP_FILES_DIR}\*.*"
    SetDetailsPrint both
    WriteUninstaller "$UpdateStaging\Uninstall.exe"

    ; Tirar o diretorio de trabalho de dentro do staging ANTES de mexer nele. O
    ; SetOutPath acima aponta o CWD do proprio instalador para $INSTDIR.new, e o
    ; Windows nao renomeia nem apaga o diretorio de trabalho de um processo vivo:
    ; sem esta linha o segundo Rename falha sempre, e o cenario S2 reprovava com
    ; reason=swap-failed e um $INSTDIR.new orfao que o RMDir tambem nao removia.
    SetOutPath "$TEMP"

    ; O Rename E a sonda de liveness: no Windows nao se renomeia diretorio que
    ; contem imagem de executavel em uso, entao um Rename que FUNCIONA prova que
    ; o processo saiu. Nenhum taskkill: matar o app durante a escrita do SQLite e
    ; pior que nao atualizar.
    ClearErrors
    RMDir /r "$UpdateBackup"
    StrCpy $R7 0

updateRenameLoop:
    ClearErrors
    Rename "$INSTDIR" "$UpdateBackup"
    IfErrors updateRenameRetry updateRenameOk

updateRenameRetry:
    IntOp $R7 $R7 + 1
    IntCmp $R7 30 updateStillLocked updateRenameWait updateStillLocked

updateRenameWait:
    Sleep 500
    Goto updateRenameLoop

updateStillLocked:
    StrCpy $R6 "locked"
    Goto updateFailIntact

updateRenameOk:
    ClearErrors
    Rename "$UpdateStaging" "$INSTDIR"
    IfErrors updateSwapFailed updateSwapOk

updateSwapFailed:
    ; Desfaz o primeiro Rename. A partir daqui o $INSTDIR volta a ser o que era.
    Rename "$UpdateBackup" "$INSTDIR"
    StrCpy $R6 "swap-failed"
    Goto updateFailIntact

updateFailIntact:
    RMDir /r "$UpdateStaging"
    StrCpy $R5 "failed"
    Call WriteUpdateReceipt
    DetailPrint "Update aborted ($R6); the installed version was left untouched."
    SetErrorLevel 3
    Abort

updateSwapOk:
    SetOutPath "$INSTDIR"

updateDone:

    ; Write registry for uninstaller (user-level)
    DetailPrint "Writing registry entries..."
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "DisplayName" "${PRODUCT_NAME}"
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "UninstallString" '"$INSTDIR\Uninstall.exe" /S'
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "DisplayIcon" "$INSTDIR\Usage Monitor.exe"
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "DisplayVersion" "${PRODUCT_VERSION}"
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "Publisher" "${PRODUCT_PUBLISHER}"
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "InstallLocation" "$INSTDIR"
    WriteRegDWORD HKCU "${PRODUCT_UNINST_KEY}" "NoModify" 1
    WriteRegDWORD HKCU "${PRODUCT_UNINST_KEY}" "NoRepair" 1

    ; Create uninstaller
    DetailPrint "Creating uninstaller..."
    WriteUninstaller "$INSTDIR\Uninstall.exe"

    ; Create Start Menu shortcuts
    DetailPrint "Creating Start Menu shortcuts..."
    Delete "$SMPROGRAMS\${PRODUCT_NAME}.lnk"
    RMDir /r "$SMPROGRAMS\${PRODUCT_NAME}"
    CreateShortcut "$SMPROGRAMS\${PRODUCT_NAME}.lnk" "$INSTDIR\Usage Monitor.exe" "" "$INSTDIR\Usage Monitor.exe" 0 SW_SHOWNORMAL "" "${PRODUCT_NAME}"

    ; Finalize log
    FileOpen $0 "${LOG_FILE}" a
    FileWrite $0 "Installation completed successfully!$\r$\n"
    FileClose $0

    ${If} $UpdateMode == 1
        ; Recibo ANTES do relancamento: o .onInstSuccess dispara o app novo sem
        ; bloquear, e ele le este arquivo no arranque. Escrito depois disto, o
        ; recibo perderia a corrida.
        StrCpy $R5 "success"
        StrCpy $R6 ""
        Call WriteUpdateReceipt
        ; O backup so sai depois de o registro e o atalho ja apontarem para a
        ; instalacao nova.
        RMDir /r "$UpdateBackup"
        DetailPrint "Update applied."
    ${EndIf}

    DetailPrint "Installation complete!"
SectionEnd

Section "Desktop Shortcut" SEC_DESKTOP
    SetShellVarContext current
    ; Atalho de desktop e chave Run sao escolha do usuario. Recria-los a cada
    ; atualizacao silenciosa desfaria, sem aviso, quem os tivesse removido.
    ${If} $UpdateMode == 1
        DetailPrint "Update mode: desktop shortcut left as the user had it."
        Return
    ${EndIf}
    DetailPrint "Creating desktop shortcut..."
    CreateShortcut "$DESKTOP\${PRODUCT_NAME}.lnk" "$INSTDIR\Usage Monitor.exe" "" "$INSTDIR\Usage Monitor.exe" 0 SW_SHOWNORMAL "" "${PRODUCT_NAME}"
SectionEnd

Section "Start with Windows" SEC_AUTO_START
    SetShellVarContext current
    ${If} $UpdateMode == 1
        DetailPrint "Update mode: auto-start left as the user had it."
        Return
    ${EndIf}
    DetailPrint "Configuring auto-start..."
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${AUTO_START_VALUE_NAME}" '"$INSTDIR\Usage Monitor.exe"'
SectionEnd

; -----------------------------------------------
; Section descriptions
; -----------------------------------------------
!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
    !insertmacro MUI_DESCRIPTION_TEXT ${SEC_APP} "Usage Monitor Application"
    !insertmacro MUI_DESCRIPTION_TEXT ${SEC_DESKTOP} "Create desktop shortcut"
    !insertmacro MUI_DESCRIPTION_TEXT ${SEC_AUTO_START} "Start application with Windows"
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; -----------------------------------------------
; Uninstaller Section
; -----------------------------------------------
Section "Uninstall"
    SetShellVarContext current
    ; Kill application processes BEFORE removing files
    DetailPrint "Stopping application processes..."
    ExecWait 'taskkill /F /IM "Usage Monitor.exe"' $0
    DetailPrint "Usage Monitor killed (exit code: $0)"
    Sleep 500
    ExecWait 'taskkill /F /IM java.exe' $0
    DetailPrint "Java processes killed (exit code: $0)"
    Sleep 1000

    ; Remove registry keys
    DetailPrint "Removing registry entries..."
    DeleteRegKey HKCU "${PRODUCT_UNINST_KEY}"
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${PRODUCT_NAME}"
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${AUTO_START_VALUE_NAME}"

    ; Remove files and directories
    DetailPrint "Removing application files..."
    RMDir /r "$INSTDIR"

    ; Remove shortcuts
    DetailPrint "Removing shortcuts..."
    Delete "$DESKTOP\${PRODUCT_NAME}.lnk"
    Delete "$SMPROGRAMS\${PRODUCT_NAME}.lnk"
    RMDir /r "$SMPROGRAMS\${PRODUCT_NAME}"

    DetailPrint "Uninstallation complete!"
SectionEnd

; -----------------------------------------------
; Uninstaller Functions
; -----------------------------------------------
Function un.onInit
    SetShellVarContext current
    !insertmacro MUI_UNGETLANGUAGE
FunctionEnd


