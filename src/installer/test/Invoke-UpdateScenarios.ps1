<#
.SYNOPSIS
    Cenarios reais do modo /UPDATE do instalador NSIS.

.DESCRIPTION
    Compila DOIS mini-instaladores a partir do UsageMonitor.nsi de producao --
    nao de uma copia dele -- sobre um payload minusculo, e roda cada cenario
    contra um diretorio descartavel.

    Cenario que exercita um .nsi paralelo nao testa o instalador que sai no
    release. Por isso o arquivo real e compilado com APP_FILES_DIR, OUTPUT_FILE,
    PRODUCT_NAME e AUTO_START_VALUE_NAME sobrescritos: os quatro tem !ifndef com
    o default de producao, entao o build do Gradle nao muda.

    O isolamento nao e detalhe. Sem PRODUCT_NAME proprio, os cenarios apagariam o
    atalho do Menu Iniciar da instalacao real; sem AUTO_START_VALUE_NAME proprio,
    sobrescreveriam a chave Run real apontando-a para o diretorio de teste. As
    duas coisas aconteceram durante o desenvolvimento da atividade A16.

    Fora do allTests de proposito: e lento e mexe no registro da maquina.
#>
[CmdletBinding()]
param(
    [string] $WorkDirectory = (Join-Path $env:TEMP 'usage-monitor-update-scenarios'),
    [string] $MakeNsisPath
)

$ErrorActionPreference = 'Stop'

# O falso "Usage Monitor.exe" e uma copia do where.exe: executavel de console
# real, que sobe e sai sozinho. O Exec do instalador so comprova que o
# CreateProcess funcionou, entao nao ha o que um binario mais elaborado provaria.
$FakeExecutableSource = Join-Path $env:WINDIR 'System32\where.exe'

$ProductName        = 'UM Scenario Test'
$AutoStartValueName = 'UsageMonitorScenarioTest'
$UninstallKey       = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\$ProductName"
$RunKey             = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
$ReceiptPath        = Join-Path $env:USERPROFILE '.usage-monitor\update-receipt.properties'

$script:Failures = 0
$script:Checks   = 0

function Assert-Equal {
    param([string] $What, $Expected, $Actual)
    $script:Checks++
    if ("$Expected" -eq "$Actual") {
        Write-Host "   ok    $What"
    } else {
        Write-Host "   FALHA $What -- esperado [$Expected], obtido [$Actual]" -ForegroundColor Red
        $script:Failures++
    }
}

function Resolve-MakeNsis {
    if ($MakeNsisPath -and (Test-Path $MakeNsisPath)) { return $MakeNsisPath }
    foreach ($candidate in @(
        'C:\Program Files (x86)\NSIS\makensis.exe',
        'C:\Program Files\NSIS\makensis.exe'
    )) {
        if (Test-Path $candidate) { return $candidate }
    }
    $onPath = Get-Command makensis -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    throw 'makensis nao encontrado. Instale o NSIS ou passe -MakeNsisPath.'
}

function New-Payload {
    param([string] $Root, [string] $Marker)
    $appDirectory = Join-Path $Root 'app'
    New-Item -ItemType Directory -Force -Path $appDirectory | Out-Null
    Copy-Item $FakeExecutableSource (Join-Path $Root 'Usage Monitor.exe') -Force
    Set-Content -Path (Join-Path $appDirectory 'version.txt') -Value $Marker -NoNewline
}

function Build-Installer {
    param([string] $Version, [string] $PayloadDirectory, [string] $OutputFile)
    $scriptPath = Join-Path $PSScriptRoot '..\UsageMonitor.nsi' | Resolve-Path
    & $script:MakeNsis `
        "/DPRODUCT_VERSION=$Version" `
        "/DPRODUCT_NAME=$ProductName" `
        "/DAUTO_START_VALUE_NAME=$AutoStartValueName" `
        "/DAPP_FILES_DIR=$PayloadDirectory" `
        "/DOUTPUT_FILE=$OutputFile" `
        $scriptPath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "makensis falhou para a versao $Version." }
}

function Invoke-Installer {
    param([string] $Setup, [string[]] $Arguments, [string] $TargetDirectory)
    # /D= precisa ser o ultimo parametro e nao aceita aspas: exigencia do NSIS.
    $all = @($Arguments) + @("/D=$TargetDirectory")
    $process = Start-Process -FilePath $Setup -ArgumentList $all -PassThru -Wait
    return $process.ExitCode
}

function Get-InstalledMarker {
    param([string] $TargetDirectory)
    $file = Join-Path $TargetDirectory 'app\version.txt'
    if (-not (Test-Path $file)) { return '<ausente>' }
    return (Get-Content $file -Raw).Trim()
}

function Get-ReceiptField {
    param([string] $Field)
    if (-not (Test-Path $ReceiptPath)) { return '<sem recibo>' }
    foreach ($line in Get-Content $ReceiptPath) {
        if ($line -like "$Field=*") { return $line.Substring($Field.Length + 1).Trim() }
    }
    return '<sem campo>'
}

function Get-RegistryValue {
    param([string] $Key, [string] $Name)
    $item = Get-ItemProperty -Path $Key -Name $Name -ErrorAction SilentlyContinue
    if ($null -eq $item) { return '<ausente>' }
    return $item.$Name
}

function Reset-Target {
    param([string] $TargetDirectory)
    foreach ($path in @($TargetDirectory, "$TargetDirectory.new", "$TargetDirectory.old")) {
        if (Test-Path $path) { Remove-Item $path -Recurse -Force }
    }
    if (Test-Path $ReceiptPath) { Remove-Item $ReceiptPath -Force }
}

function Remove-ScenarioArtifacts {
    param([string] $TargetDirectory)
    Reset-Target -TargetDirectory $TargetDirectory
    foreach ($shortcut in @(
        (Join-Path ([Environment]::GetFolderPath('Desktop')) "$ProductName.lnk"),
        (Join-Path ([Environment]::GetFolderPath('Programs')) "$ProductName.lnk")
    )) {
        if (Test-Path $shortcut) { Remove-Item $shortcut -Force }
    }
    Remove-ItemProperty -Path $RunKey -Name $AutoStartValueName -ErrorAction SilentlyContinue
    Remove-Item -Path $UninstallKey -Recurse -Force -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------- preparacao

$script:MakeNsis = Resolve-MakeNsis
Write-Host "makensis: $script:MakeNsis"

if (Test-Path $WorkDirectory) { Remove-Item $WorkDirectory -Recurse -Force }
$payloadV1 = Join-Path $WorkDirectory 'payload-v1'
$payloadV2 = Join-Path $WorkDirectory 'payload-v2'
$outputDir = Join-Path $WorkDirectory 'out'
$target    = Join-Path $WorkDirectory 'install'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
New-Payload -Root $payloadV1 -Marker 'v1'
New-Payload -Root $payloadV2 -Marker 'v2'

$setupV1 = Join-Path $outputDir 'Setup-1.0.0.exe'
$setupV2 = Join-Path $outputDir 'Setup-2.0.0.exe'
Build-Installer -Version '1.0.0' -PayloadDirectory $payloadV1 -OutputFile $setupV1
Build-Installer -Version '2.0.0' -PayloadDirectory $payloadV2 -OutputFile $setupV2

try {

# ------------------------------------------------------------------ S1

Write-Host "`n== S1: instalacao limpa da v1 =="
Reset-Target -TargetDirectory $target
$code = Invoke-Installer -Setup $setupV1 -Arguments @('/S') -TargetDirectory $target
Assert-Equal 'exit code' 0 $code
Assert-Equal 'arquivos da v1' 'v1' (Get-InstalledMarker $target)
Assert-Equal 'desinstalador presente' $true (Test-Path (Join-Path $target 'Uninstall.exe'))
Assert-Equal 'registro em 1.0.0' '1.0.0' (Get-RegistryValue $UninstallKey 'DisplayVersion')
Assert-Equal 'InstallLocation aponta para o alvo' $target (Get-RegistryValue $UninstallKey 'InstallLocation')

# ------------------------------------------------------------------ S2

Write-Host "`n== S2: /S /UPDATE da v1 para a v2, com atalho e Run removidos pelo usuario =="
$desktopShortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) "$ProductName.lnk"
if (Test-Path $desktopShortcut) { Remove-Item $desktopShortcut -Force }
Remove-ItemProperty -Path $RunKey -Name $AutoStartValueName -ErrorAction SilentlyContinue
if (Test-Path $ReceiptPath) { Remove-Item $ReceiptPath -Force }

$code = Invoke-Installer -Setup $setupV2 -Arguments @('/S', '/UPDATE', '/PID=4321') -TargetDirectory $target
Assert-Equal 'exit code' 0 $code
Assert-Equal 'arquivos da v2' 'v2' (Get-InstalledMarker $target)
Assert-Equal 'registro em 2.0.0' '2.0.0' (Get-RegistryValue $UninstallKey 'DisplayVersion')
Assert-Equal 'sem staging orfao' $false (Test-Path "$target.new")
Assert-Equal 'sem backup orfao' $false (Test-Path "$target.old")
Assert-Equal 'recibo de sucesso' 'success' (Get-ReceiptField 'status')
Assert-Equal 'recibo traz a versao anterior' '1.0.0' (Get-ReceiptField 'previousVersion')
Assert-Equal 'recibo traz o pid recebido' '4321' (Get-ReceiptField 'pid')
# Atualizacao silenciosa nao reimpoe escolha desfeita pelo usuario.
Assert-Equal 'atalho do desktop continua ausente' $false (Test-Path $desktopShortcut)
Assert-Equal 'chave Run continua ausente' '<ausente>' (Get-RegistryValue $RunKey $AutoStartValueName)

# ------------------------------------------------------------------ S3

Write-Host "`n== S3: arquivo travado dentro do INSTDIR =="
if (Test-Path $ReceiptPath) { Remove-Item $ReceiptPath -Force }
$locked = [System.IO.File]::Open((Join-Path $target 'app\version.txt'), 'Open', 'Read', 'None')
try {
    $code = Invoke-Installer -Setup $setupV2 -Arguments @('/S', '/UPDATE', '/PID=4321') -TargetDirectory $target
} finally {
    $locked.Close()
}
Assert-Equal 'exit code de falha' 3 $code
Assert-Equal 'instalacao intacta' 'v2' (Get-InstalledMarker $target)
Assert-Equal 'recibo de falha' 'failed' (Get-ReceiptField 'status')
Assert-Equal 'motivo registrado' 'locked' (Get-ReceiptField 'reason')
Assert-Equal 'sem staging orfao' $false (Test-Path "$target.new")
Assert-Equal 'sem backup orfao' $false (Test-Path "$target.old")

# ------------------------------------------------------------------ S4

Write-Host "`n== S4: staging bloqueado por um ARQUIVO com o nome do diretorio =="
if (Test-Path $ReceiptPath) { Remove-Item $ReceiptPath -Force }
Set-Content -Path "$target.new" -Value 'nao sou um diretorio' -NoNewline
$blocker = [System.IO.File]::Open("$target.new", 'Open', 'Read', 'None')
try {
    $code = Invoke-Installer -Setup $setupV2 -Arguments @('/S', '/UPDATE') -TargetDirectory $target
} finally {
    $blocker.Close()
}
Assert-Equal 'exit code de falha' 3 $code
Assert-Equal 'instalacao intacta' 'v2' (Get-InstalledMarker $target)
Assert-Equal 'falhou antes da troca' 'staging-unavailable' (Get-ReceiptField 'reason')
Remove-Item "$target.new" -Force -ErrorAction SilentlyContinue

# ------------------------------------------------------------------ S5

Write-Host "`n== S5: /S /UPDATE duas vezes seguidas =="
$code = Invoke-Installer -Setup $setupV2 -Arguments @('/S', '/UPDATE') -TargetDirectory $target
Assert-Equal 'primeira passada' 0 $code
$code = Invoke-Installer -Setup $setupV2 -Arguments @('/S', '/UPDATE') -TargetDirectory $target
Assert-Equal 'segunda passada e idempotente' 0 $code
Assert-Equal 'arquivos continuam na v2' 'v2' (Get-InstalledMarker $target)
Assert-Equal 'sem staging orfao' $false (Test-Path "$target.new")
Assert-Equal 'sem backup orfao' $false (Test-Path "$target.old")

# ------------------------------------------------------------------ S6

Write-Host "`n== S6: instalador SEM /UPDATE sobre instalacao existente (nao regressao) =="
$code = Invoke-Installer -Setup $setupV1 -Arguments @('/S') -TargetDirectory $target
Assert-Equal 'exit code' 0 $code
Assert-Equal 'voltou para a v1' 'v1' (Get-InstalledMarker $target)
# O caminho manual continua fazendo o que sempre fez: recria atalho e auto-start.
Assert-Equal 'atalho do desktop recriado' $true (Test-Path $desktopShortcut)
Assert-Equal 'chave Run recriada' $true ((Get-RegistryValue $RunKey $AutoStartValueName) -ne '<ausente>')

} finally {
    Write-Host "`n== limpeza =="
    Remove-ScenarioArtifacts -TargetDirectory $target
}

Write-Host ""
Write-Host "Verificacoes: $script:Checks   Falhas: $script:Failures"
if ($script:Failures -gt 0) { exit 1 }
exit 0
