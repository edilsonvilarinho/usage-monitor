<#
.SYNOPSIS
    Cenarios reais do modo /UPDATE do instalador NSIS.

.DESCRIPTION
    Compila DOIS mini-instaladores a partir do UsageMonitor.nsi de producao --
    nao de uma copia dele -- sobre um payload minusculo, e roda cada cenario
    contra um diretorio descartavel.

    Cenario que exercita um .nsi paralelo nao testa o instalador que sai no
    release. Por isso o arquivo real e compilado com APP_FILES_DIR, OUTPUT_FILE,
    PRODUCT_NAME, AUTO_START_VALUE_NAME e MSI_UPGRADE_CODE sobrescritos: os cinco
    tem !ifndef com o default de producao, entao o build do Gradle nao muda.

    O isolamento nao e detalhe. Sem PRODUCT_NAME proprio, os cenarios apagariam o
    atalho do Menu Iniciar da instalacao real; sem AUTO_START_VALUE_NAME proprio,
    sobrescreveriam a chave Run real apontando-a para o diretorio de teste. As
    duas coisas aconteceram durante o desenvolvimento da atividade A16. E sem
    MSI_UPGRADE_CODE proprio, a primeira instalacao do roteiro DESINSTALARIA o
    Usage Monitor real, caso a maquina ainda esteja no MSI.

    S7 precisa de WiX (candle/light) para compilar o fixture MSI. S9 precisa de
    um java.exe resolvivel, para servir de sentinela. Sem eles os dois sao
    pulados com aviso; os demais nao dependem disso.

    Fora do allTests de proposito: e lento e mexe no registro da maquina.
#>
[CmdletBinding()]
param(
    [string] $WorkDirectory = (Join-Path $env:TEMP 'usage-monitor-update-scenarios'),
    [string] $MakeNsisPath,
    # Diretorio com candle.exe e light.exe. So o S7 precisa deles; sem WiX esse
    # cenario e PULADO com aviso, e os demais continuam rodando.
    [string] $WixBinPath
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

# UpgradeCode do cenario, e o motivo dele existir e o mesmo de PRODUCT_NAME e de
# AUTO_START_VALUE_NAME, so que mais grave: compilar o .nsi sem sobrescrever este
# define deixaria os cenarios rodando com o UpgradeCode de PRODUCAO, e a primeira
# instalacao do roteiro DESINSTALARIA o Usage Monitor real da maquina de quem
# roda a suite. E o mesmo valor declarado em ScenarioMsi.wxs.
$MsiUpgradeCode = '{A7F2C6E1-4B3D-4E85-9C1A-6D2F8B0E3A57}'
$MsiProductName = 'UM Scenario MSI'
# Forma empacotada do GUID acima, que e como o Windows Installer indexa
# UpgradeCodes: primeiros tres campos com os nibbles invertidos, os dois ultimos
# com os bytes trocados aos pares.
$MsiUpgradeCodePacked = '1E6C2F7AD3B458E4C9A1D6F2B8E0A375'
$MsiUpgradeKey = "HKCU:\Software\Microsoft\Installer\UpgradeCodes\$MsiUpgradeCodePacked"

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
        "/DMSI_UPGRADE_CODE=$MsiUpgradeCode" `
        "/DAPP_FILES_DIR=$PayloadDirectory" `
        "/DOUTPUT_FILE=$OutputFile" `
        $scriptPath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "makensis falhou para a versao $Version." }
}

function Resolve-WixBin {
    foreach ($candidate in @(
        $WixBinPath,
        $(if ($env:WIX) { Join-Path $env:WIX 'bin' } else { $null })
    )) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'candle.exe'))) { return $candidate }
    }
    $onPath = Get-Command candle.exe -ErrorAction SilentlyContinue
    if ($onPath) { return (Split-Path $onPath.Source -Parent) }
    return $null
}

function New-ScenarioMsi {
    param([string] $OutputFile, [string] $BuildDirectory)
    New-Item -ItemType Directory -Force -Path $BuildDirectory | Out-Null
    $payload = Join-Path $BuildDirectory 'msi-marker.txt'
    Set-Content -Path $payload -Value 'msi-fixture' -NoNewline
    $wxs = Join-Path $PSScriptRoot 'ScenarioMsi.wxs'
    $obj = Join-Path $BuildDirectory 'ScenarioMsi.wixobj'
    & (Join-Path $script:WixBin 'candle.exe') -nologo "-dPayloadFile=$payload" -out $obj $wxs | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'candle falhou ao compilar o MSI do cenario.' }
    # -sval pula a validacao ICE: o fixture e minimo de proposito e as validacoes
    # cobram metadados que nao tem nada a ver com o que o cenario exercita.
    & (Join-Path $script:WixBin 'light.exe') -nologo -sval -out $OutputFile $obj | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'light falhou ao ligar o MSI do cenario.' }
}

function Install-ScenarioMsi {
    param([string] $MsiFile, [string] $TargetDirectory)
    $process = Start-Process -FilePath (Join-Path $env:SYSTEMROOT 'System32\msiexec.exe') `
        -ArgumentList '/i', "`"$MsiFile`"", "INSTALLFOLDER=$TargetDirectory", '/qn' -PassThru -Wait
    if ($process.ExitCode -ne 0) { throw "msiexec /i do fixture falhou com $($process.ExitCode)." }
}

# Limpeza pelo ARQUIVO e nao pelo ProductCode: o Id do produto e `*` no .wxs, ou
# seja, sorteado a cada build, e este fixture per-user nao aparece em
# "Aplicativos e recursos" de onde se pudesse le-lo.
function Uninstall-ScenarioMsi {
    param([string] $MsiFile)
    if (-not $MsiFile -or -not (Test-Path $MsiFile)) { return }
    if (-not (Test-Path $MsiUpgradeKey)) { return }
    Start-Process -FilePath (Join-Path $env:SYSTEMROOT 'System32\msiexec.exe') `
        -ArgumentList '/x', "`"$MsiFile`"", '/qn' -Wait | Out-Null
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

function Resolve-JavaExe {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $candidate) { return $candidate }
    }
    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    return $null
}

# Sentinela do S9. Uma JVM viva durante a rodada inteira, que nenhum cenario
# deveria encostar: e o teste da #88. Ate ela, a Section "Uninstall" rodava
# `taskkill /F /IM java.exe`, e o desinstalador e acionado de lado pelo
# ExecWait '$0 _?=' do .onInit em S5, S6 e S8 -- ou seja, cada rodada da suite
# derrubava o daemon do Gradle e a IDE de quem a executava.
#
# Precisa ser um processo cujo NOME DE IMAGEM seja java.exe, porque e por nome
# que o taskkill filtra. O single-file source launcher (Java 11+) da isso sem
# passo de compilacao.
function Start-JvmSentinel {
    param([string] $Root)
    $java = Resolve-JavaExe
    if (-not $java) { return $null }

    New-Item -ItemType Directory -Force -Path $Root | Out-Null
    $source = Join-Path $Root 'Sentinel.java'
    Set-Content -Path $source -Encoding ASCII -Value @'
public class Sentinel {
    public static void main(String[] args) throws Exception {
        Thread.sleep(900000L);
    }
}
'@
    return Start-Process -FilePath $java -ArgumentList $source -PassThru -WindowStyle Hidden
}

# ---------------------------------------------------------------- preparacao

$script:MakeNsis = Resolve-MakeNsis
Write-Host "makensis: $script:MakeNsis"
$script:WixBin = Resolve-WixBin
if ($script:WixBin) { Write-Host "wix: $script:WixBin" } else { Write-Host "wix: nao encontrado -- S7 sera pulado" -ForegroundColor Yellow }

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

$script:Sentinel = Start-JvmSentinel -Root (Join-Path $WorkDirectory 'sentinel')
if ($script:Sentinel) {
    Write-Host "sentinela: java.exe pid $($script:Sentinel.Id)"
} else {
    Write-Host "sentinela: java.exe nao encontrado -- S9 sera pulado" -ForegroundColor Yellow
}

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

# ------------------------------------------------------------------ S7

Write-Host "`n== S7: instalacao MSI por baixo, removida sem interacao =="
if ($null -eq $script:WixBin) {
    Write-Host "   PULADO -- WiX nao encontrado (candle.exe). Passe -WixBinPath ou defina WIX." -ForegroundColor Yellow
} else {
    Reset-Target -TargetDirectory $target
    $msiFile = Join-Path $outputDir 'ScenarioMsi.msi'
    New-ScenarioMsi -OutputFile $msiFile -BuildDirectory (Join-Path $WorkDirectory 'msi-build')
    Install-ScenarioMsi -MsiFile $msiFile -TargetDirectory $target

    Assert-Equal 'fixture MSI instalado' $true (Test-Path (Join-Path $target 'msi-marker.txt'))
    Assert-Equal 'produto registrado pelo UpgradeCode' $true (Test-Path $MsiUpgradeKey)

    # /S sem /UPDATE: e o duplo clique do usuario, so que sem janela. Se o
    # instalador perguntasse alguma coisa aqui, o processo nao retornaria.
    $code = Invoke-Installer -Setup $setupV1 -Arguments @('/S') -TargetDirectory $target
    Assert-Equal 'exit code' 0 $code
    Assert-Equal 'produto MSI desregistrado' $false (Test-Path $MsiUpgradeKey)
    Assert-Equal 'arquivo do MSI removido' $false (Test-Path (Join-Path $target 'msi-marker.txt'))
    Assert-Equal 'payload novo no lugar' 'v1' (Get-InstalledMarker $target)
    Assert-Equal 'desinstalador do NSIS presente' $true (Test-Path (Join-Path $target 'Uninstall.exe'))
    Assert-Equal 'uma unica entrada de desinstalacao' '1.0.0' (Get-RegistryValue $UninstallKey 'DisplayVersion')
}

# ------------------------------------------------------------------ S8

Write-Host "`n== S8: chave de desinstalacao orfa mais arvore estranha =="
Reset-Target -TargetDirectory $target
# Residuo tipico de um MSI que saiu pela metade: arquivos no lugar, nenhum
# Uninstall.exe, e uma chave HKCU apontando para um desinstalador que sumiu. Foi
# o estado real medido numa maquina que veio do NSIS e migrou para o MSI.
New-Item -ItemType Directory -Force -Path (Join-Path $target 'app') | Out-Null
Set-Content -Path (Join-Path $target 'app\version.txt') -Value 'residuo' -NoNewline
Set-Content -Path (Join-Path $target 'app\jar-orfao.jar') -Value 'x' -NoNewline
New-Item -Path $UninstallKey -Force | Out-Null
Set-ItemProperty -Path $UninstallKey -Name 'DisplayName' -Value $ProductName
Set-ItemProperty -Path $UninstallKey -Name 'DisplayVersion' -Value '0.9.0'
Set-ItemProperty -Path $UninstallKey -Name 'InstallLocation' -Value $target
Set-ItemProperty -Path $UninstallKey -Name 'UninstallString' -Value ('"' + (Join-Path $target 'Uninstall.exe') + '" /S')

Assert-Equal 'desinstalador ausente antes' $false (Test-Path (Join-Path $target 'Uninstall.exe'))

# Sem /SD nenhum MessageBox retornaria aqui: o processo terminar ja prova que o
# instalador nao perguntou nada.
$code = Invoke-Installer -Setup $setupV1 -Arguments @('/S') -TargetDirectory $target
Assert-Equal 'exit code' 0 $code
Assert-Equal 'payload novo no lugar' 'v1' (Get-InstalledMarker $target)
# A guarda por ausencia de Uninstall.exe limpa a arvore antes da copia; sem ela o
# jar da versao anterior sobreviveria ao lado do novo, que e o defeito da #78.
Assert-Equal 'jar orfao removido' $false (Test-Path (Join-Path $target 'app\jar-orfao.jar'))
Assert-Equal 'desinstalador escrito' $true (Test-Path (Join-Path $target 'Uninstall.exe'))
Assert-Equal 'registro atualizado para a versao nova' '1.0.0' (Get-RegistryValue $UninstallKey 'DisplayVersion')

# ------------------------------------------------------------------ S9

Write-Host "`n== S9: a rodada nao mata JVM alheia =="
if ($script:Sentinel) {
    # S5, S6 e S8 acionaram o desinstalador pelo caminho de reinstalacao. Se a
    # Section "Uninstall" voltar a filtrar por nome de imagem global, esta
    # assercao reprova -- foi assim que ela foi falsificada na #88.
    $script:Sentinel.Refresh()
    Assert-Equal 'JVM alheia sobreviveu aos cenarios' $false $script:Sentinel.HasExited
} else {
    Write-Host "   PULADO -- sem java.exe para servir de sentinela" -ForegroundColor Yellow
}

} finally {
    Write-Host "`n== limpeza =="
    if ($script:Sentinel -and -not $script:Sentinel.HasExited) {
        Stop-Process -Id $script:Sentinel.Id -Force -ErrorAction SilentlyContinue
    }
    Uninstall-ScenarioMsi -MsiFile (Join-Path $outputDir 'ScenarioMsi.msi')
    Remove-ScenarioArtifacts -TargetDirectory $target
}

Write-Host ""
Write-Host "Verificacoes: $script:Checks   Falhas: $script:Failures"
if ($script:Failures -gt 0) { exit 1 }
exit 0
