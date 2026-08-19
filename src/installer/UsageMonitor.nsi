!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"

SetCompressor zlib

; -----------------------------------------------
; General
; -----------------------------------------------
!ifndef PRODUCT_VERSION
!define PRODUCT_VERSION "36.0.0"
!endif

!define PRODUCT_NAME "Usage Monitor"
!define PRODUCT_PUBLISHER "Usage Monitor"
!define PRODUCT_UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}"
!define LOG_FILE "$INSTDIR\install.log"
!define APP_ICON "..\desktopMain\resources\icons\app_icon.ico"
; -----------------------------------------------
; Installer attributes
; -----------------------------------------------
Name "${PRODUCT_NAME} ${PRODUCT_VERSION}"
OutFile "..\..\build\installer\UsageMonitor-Setup-${PRODUCT_VERSION}.exe"
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
    ; Check if already installed
    ReadRegStr $0 HKCU "${PRODUCT_UNINST_KEY}" "UninstallString"
    StrCmp $0 "" done notdone

notdone:
    MessageBox MB_YESNO|MB_ICONQUESTION "${PRODUCT_NAME} ja esta instalado. Deseja remover a versao anterior?" IDYES uninst IDNO done

uninst:
    ExecWait '$0'
    DeleteRegKey HKCU "${PRODUCT_UNINST_KEY}"
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${PRODUCT_NAME}"
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "UsageMonitor"
    RMDir /r "$INSTDIR"

done:
    !insertmacro MUI_LANGDLL_DISPLAY
FunctionEnd

Function .onInstSuccess
FunctionEnd

; -----------------------------------------------
; Installer Sections
; -----------------------------------------------
Section "Usage Monitor" SEC_APP
    SectionIn RO
    SetShellVarContext current

    ; Limpar Run keys antigos (ambos os nomes ? migra??o de vers?es anteriores)
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${PRODUCT_NAME}"
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "UsageMonitor"

    ; Create log file
    DetailPrint "Initializing installation..."
    FileOpen $0 "${LOG_FILE}" w
    FileWrite $0 "Installation started$\r$\n"
    FileWrite $0 "Install dir: $INSTDIR$\r$\n"
    FileClose $0

    SetOutPath "$INSTDIR"
    SetDetailsPrint none
    File /r "..\..\build\installer\files\*.*"
    SetDetailsPrint both

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

    DetailPrint "Installation complete!"
SectionEnd

Section "Desktop Shortcut" SEC_DESKTOP
    SetShellVarContext current
    DetailPrint "Creating desktop shortcut..."
    CreateShortcut "$DESKTOP\${PRODUCT_NAME}.lnk" "$INSTDIR\Usage Monitor.exe" "" "$INSTDIR\Usage Monitor.exe" 0 SW_SHOWNORMAL "" "${PRODUCT_NAME}"
SectionEnd

Section "Start with Windows" SEC_AUTO_START
    SetShellVarContext current
    DetailPrint "Configuring auto-start..."
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "UsageMonitor" '"$INSTDIR\Usage Monitor.exe"'
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
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "UsageMonitor"

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


