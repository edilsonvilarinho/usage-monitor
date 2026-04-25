# NSIS Installer Skill

## Descrição
Cria um instalador NSIS para projetos Kotlin Multiplatform Desktop (KMP). Inclui suporte multilíngue (PT/EN), opções de auto-start com Windows, atalhos no desktop e menu iniciar, e desinstalador completo.

## Triggers
- `/create-installer`
- `criar instalador`
- `crie o instalador`
- `gerar instalador`
- `build installer`
- `package installer`

## Pré-requisitos
- NSIS 3.x instalado em `C:\Program Files\NSIS\makensis.exe` ou `C:\Program Files (x86)\NSIS\makensis.exe`
- Projeto configurado com `desktopJar` task funcional

## Instruções

### 1. Verificar NSIS
```bash
Test-Path "C:\Program Files\NSIS\makensis.exe" -or Test-Path "C:\Program Files (x86)\NSIS\makensis.exe"
```

### 2. Estrutura de diretórios
O instalador espera esta estrutura:
```
src/installer/
├── UsageMonitor.nsi           # Script principal NSIS
├── license.txt                # Licença do projeto
├── files/
│   ├── UsageMonitor.bat      # Launcher batch (javaw -jar)
│   ├── app_icon.ico          # Ícone da aplicação
│   └── {artifact}.jar        # JAR gerado pelo desktopJar
└── languages/
    ├── Portuguese.nsh        # Traduções PT-BR
    └── English.nsh           # Traduções EN
```

### 3. Parâmetros configuráveis
Editar `src/installer/UsageMonitor.nsi`:
| Parâmetro | Descrição | Default |
|---|---|---|
| `PRODUCT_NAME` | Nome do produto | Usage Monitor |
| `PRODUCT_VERSION` | Versão | 1.0.0 |
| `PRODUCT_PUBLISHER` | Nome do desenvolvedor | Usage Monitor |
| `INSTALLDIR` | Diretório de instalação | $PROGRAMFILES64\Usage Monitor |
| `RUN_KEY` | Registry key para auto-start | HKCU\...\Run |

### 4. Comandos de build
```bash
gradlew.bat prepareInstallerFiles   # Copia arquivos para build/installer/files
gradlew.bat packageInstaller        # Gera o .exe (executa makensis)
```

### 5. Output
- `build/installer/UsageMonitor-Setup-{VERSION}.exe`

## Arquivos gerados pela skill

### UsageMonitor.nsi
Script NSIS com:
- MUI2 (Modern UI 2)
- Deteccao automatica de idioma do Windows (PT-BR/EN)
- 3 secoes: App (principal), Desktop Shortcut, Start with Windows
- Uninstaller que remove registry e arquivos
- Deteccao de instalacao previa com pergunta para desinstalar
- Checkbox "Executar Usage Monitor" na pagina de conclusao
- User-level install (HKCU registry, nao precisa admin)
- Suporte a PT-BR e EN

### license.txt
Arquivo de licença (MIT por padrão).

### UsageMonitor.bat
```batch
@echo off
cd /d "%~dp0"
start javaw -jar "usage-monitor-desktop.jar"
```

### languages/Portuguese.nsh e English.nsh
Traduções para todos os textos da interface.

## Fluxo de execução

```
1. Verificar se NSIS está instalado
2. Verificar/gerar desktopJar
3. Preparar arquivos (prepareInstallerFiles)
4. Executar makensis (buildNsisInstaller)
5. Reportar output e path do instalador
```

## Opções da skill (parâmetros)

| Parâmetro | Tipo | Descrição |
|---|---|---|
| `version` | string | Versão do instalador (default: ler do build.gradle.kts) |
| `name` | string | Nome do produto (default: "Usage Monitor") |
| `publisher` | string | Nome do publicador (default: do projeto) |
| `installDir` | string | Diretório de instalação customizado |

## Exemplo de uso
```
/create-installer version=2.0.0 name="Meu App"
```

## Erros comuns

| Erro | Solução |
|---|---|
| `NSIS not found` | Instalar NSIS de https://nsis.sourceforge.io/ |
| `File not found: files\*.jar` | Executar `gradlew.bat prepareInstallerFiles` primeiro |
| `Access denied` | Executar como Administrador |

## Status atual do projeto
- ✅ Script NSIS: `src/installer/UsageMonitor.nsi`
- ✅ Launcher batch: `src/installer/files/UsageMonitor.bat` (com verificacao de Java)
- ✅ License: `src/installer/license.txt`
- ✅ Gradle tasks: `prepareInstallerFiles`, `buildNsisInstaller`, `packageInstaller`
- ✅ Opcao "Executar ao finalizar" (MUI_FINISHPAGE_RUN)
- ✅ Deteccao de instalacao previa e desinstalacao automatica
- ✅ Instalacao user-level (HKCU) - nao precisa de admin

## Notas sobre o script NSIS
- `RequestExecutionLevel user` - instalacao sem admin (HKCU registry)
- `InstallDir` usa `$LOCALAPPDATA` para instalacao local
- OutFile: `..\..\build\installer\UsageMonitor-Setup-{VERSION}.exe`
- File paths: `..\..\build\installer\files\` (relativo ao src/installer/)
- Desinstalador detecta instalacao previa e oferece remover antes de instalar
- MUI_FINISHPAGE_RUN configurado para executar `UsageMonitor.bat` ao finalizar
- Batch verifica se `javaw` existe antes de executar

## Limites atuais
- Section descriptions hardcoded em English (sem LangString dinamico)
- Nao suporta install dir customizado via parametro de linha de comando
- Auto-start usa sempre HKCU (ok para user-level install)