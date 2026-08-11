# Adiciona ao MSI gerado pelo jpackage o checkbox "iniciar agora" do ExitDialog.
#
# O template WixUI que o jpackage usa ja traz o controle OptionalCheckBox e a
# condicao que o exibe, mas nada preenche o texto do checkbox nem lanca o app.
# Este script fecha esse cabeamento diretamente nas tabelas do MSI.

param(
    [Parameter(Mandatory = $true)] [string] $MsiDirectory,
    [string] $MsiFileName,
    [string] $CheckboxText = 'Iniciar o Usage Monitor',
    [string] $LauncherFileName = 'Usage Monitor.exe',
    [switch] $VerifyOnly
)

$ErrorActionPreference = 'Stop'

# 32 (source Directory) + 2 (Exe) + 64 (Continue) + 128 (Async) = asyncNoWait,
# com working directory em INSTALLDIR e a linha de comando entre aspas.
$LaunchActionType = 226
$LaunchActionName = 'JpLaunchApplication'
$LaunchDirectory = 'INSTALLDIR'
$CheckboxTextProperty = 'WIXUI_EXITDIALOGOPTIONALCHECKBOXTEXT'
$CheckboxProperty = 'WIXUI_EXITDIALOGOPTIONALCHECKBOX'
$LaunchCondition = "$CheckboxProperty = 1 AND NOT Installed"
# O EndDialog do botao Finish tem ordering 999; o launch precisa vir antes.
$LaunchOrdering = 1

function Invoke-ComMember {
    param($Target, [string] $Name, [string] $MemberType, $Arguments)

    return $Target.GetType().InvokeMember($Name, $MemberType, $null, $Target, $Arguments)
}

function Read-MsiRows {
    param($Database, [string] $Sql)

    $view = Invoke-ComMember $Database 'OpenView' 'InvokeMethod' @($Sql)
    Invoke-ComMember $view 'Execute' 'InvokeMethod' $null | Out-Null

    $rows = @()
    while ($true) {
        $record = Invoke-ComMember $view 'Fetch' 'InvokeMethod' $null
        if ($null -eq $record) { break }

        $fieldCount = Invoke-ComMember $record 'FieldCount' 'GetProperty' $null
        $fields = @()
        for ($index = 1; $index -le $fieldCount; $index++) {
            $fields += [string](Invoke-ComMember $record 'StringData' 'GetProperty' $index)
        }
        $rows += , $fields
    }

    Invoke-ComMember $view 'Close' 'InvokeMethod' $null | Out-Null
    return , $rows
}

function New-MsiRecord {
    param($Installer, [object[]] $Values)

    $record = Invoke-ComMember $Installer 'CreateRecord' 'InvokeMethod' @($Values.Count)
    for ($index = 0; $index -lt $Values.Count; $index++) {
        $value = $Values[$index]
        $field = $index + 1
        if ($value -is [int]) {
            Invoke-ComMember $record 'IntegerData' 'SetProperty' @($field, $value) | Out-Null
        } else {
            Invoke-ComMember $record 'StringData' 'SetProperty' @($field, [string]$value) | Out-Null
        }
    }

    return $record
}

function Invoke-MsiSql {
    param($Database, [string] $Sql, $Record)

    $view = Invoke-ComMember $Database 'OpenView' 'InvokeMethod' @($Sql)
    if ($null -eq $Record) {
        Invoke-ComMember $view 'Execute' 'InvokeMethod' $null | Out-Null
    } else {
        Invoke-ComMember $view 'Execute' 'InvokeMethod' @($Record) | Out-Null
    }
    Invoke-ComMember $view 'Close' 'InvokeMethod' $null | Out-Null
}

function Resolve-MsiPath {
    param([string] $Directory, [string] $FileName)

    if (-not (Test-Path -LiteralPath $Directory)) {
        throw "Diretorio do MSI nao encontrado: $Directory"
    }

    # Com o nome explicito nao importa se sobrou o MSI de uma versao anterior na pasta.
    if (-not [string]::IsNullOrWhiteSpace($FileName)) {
        $explicitPath = Join-Path -Path $Directory -ChildPath $FileName
        if (-not (Test-Path -LiteralPath $explicitPath)) {
            throw "MSI nao encontrado: $explicitPath"
        }
        return (Resolve-Path -LiteralPath $explicitPath).Path
    }

    $candidates = @(Get-ChildItem -LiteralPath $Directory -Filter '*.msi' -File)
    if ($candidates.Count -eq 0) {
        throw "Nenhum .msi encontrado em: $Directory"
    }
    if ($candidates.Count -gt 1) {
        $names = ($candidates | ForEach-Object { $_.Name }) -join ', '
        throw "Mais de um .msi em ${Directory}: $names. Use -MsiFileName ou limpe a pasta."
    }

    return $candidates[0].FullName
}

# Falha cedo se o template do jpackage mudar: melhor quebrar o build do que
# publicar um MSI silenciosamente sem o checkbox.
function Assert-Preconditions {
    param($Database)

    $checkbox = Read-MsiRows $Database "SELECT Control FROM Control WHERE Dialog_ = 'ExitDialog' AND Control = 'OptionalCheckBox'"
    if ($checkbox.Count -eq 0) {
        throw 'O MSI nao tem o controle ExitDialog/OptionalCheckBox. Template do jpackage mudou.'
    }

    $directory = Read-MsiRows $Database "SELECT Directory FROM Directory WHERE Directory = '$LaunchDirectory'"
    if ($directory.Count -eq 0) {
        throw "O MSI nao tem o diretorio $LaunchDirectory. Template do jpackage mudou."
    }
}

function Set-MsiProperty {
    param($Database, $Installer, [string] $Name, [string] $Value)

    $existing = Read-MsiRows $Database "SELECT Value FROM Property WHERE Property = '$Name'"
    if ($existing.Count -gt 0) {
        Invoke-MsiSql $Database "UPDATE Property SET Value = ? WHERE Property = '$Name'" (New-MsiRecord $Installer @($Value))
    } else {
        Invoke-MsiSql $Database 'INSERT INTO Property (Property, Value) VALUES (?, ?)' (New-MsiRecord $Installer @($Name, $Value))
    }
}

function Set-MsiLaunchAction {
    param($Database, $Installer, [string] $Target)

    $existing = Read-MsiRows $Database "SELECT Action FROM CustomAction WHERE Action = '$LaunchActionName'"
    if ($existing.Count -gt 0) {
        Invoke-MsiSql $Database "UPDATE CustomAction SET Type = ?, Source = ?, Target = ? WHERE Action = '$LaunchActionName'" (New-MsiRecord $Installer @([int]$LaunchActionType, $LaunchDirectory, $Target))
    } else {
        Invoke-MsiSql $Database 'INSERT INTO CustomAction (Action, Type, Source, Target) VALUES (?, ?, ?, ?)' (New-MsiRecord $Installer @($LaunchActionName, [int]$LaunchActionType, $LaunchDirectory, $Target))
    }
}

function Set-MsiLaunchControlEvent {
    param($Database, $Installer)

    $filter = "Dialog_ = 'ExitDialog' AND Control_ = 'Finish' AND Event = 'DoAction' AND Argument = '$LaunchActionName'"
    $existing = Read-MsiRows $Database "SELECT Condition FROM ControlEvent WHERE $filter"
    if ($existing.Count -gt 0) {
        Invoke-MsiSql $Database "UPDATE ControlEvent SET Condition = ?, Ordering = ? WHERE $filter" (New-MsiRecord $Installer @($LaunchCondition, [int]$LaunchOrdering))
    } else {
        Invoke-MsiSql $Database 'INSERT INTO ControlEvent (Dialog_, Control_, Event, Argument, Condition, Ordering) VALUES (?, ?, ?, ?, ?, ?)' (New-MsiRecord $Installer @('ExitDialog', 'Finish', 'DoAction', $LaunchActionName, $LaunchCondition, [int]$LaunchOrdering))
    }
}

function Assert-MsiPatched {
    param($Database, [string] $Target)

    $failures = @()

    $textProperty = Read-MsiRows $Database "SELECT Value FROM Property WHERE Property = '$CheckboxTextProperty'"
    if ($textProperty.Count -eq 0 -or [string]::IsNullOrWhiteSpace($textProperty[0][0])) {
        $failures += "Property $CheckboxTextProperty ausente ou vazia"
    }

    $defaultProperty = Read-MsiRows $Database "SELECT Value FROM Property WHERE Property = '$CheckboxProperty'"
    if ($defaultProperty.Count -eq 0 -or $defaultProperty[0][0] -ne '1') {
        $failures += "Property $CheckboxProperty diferente de 1"
    }

    $action = Read-MsiRows $Database "SELECT Type, Source, Target FROM CustomAction WHERE Action = '$LaunchActionName'"
    if ($action.Count -eq 0) {
        $failures += "CustomAction $LaunchActionName ausente"
    } elseif ($action[0][0] -ne [string]$LaunchActionType -or $action[0][1] -ne $LaunchDirectory -or $action[0][2] -ne $Target) {
        $failures += "CustomAction $LaunchActionName com valores inesperados: $($action[0] -join ' | ')"
    }

    $controlEvent = Read-MsiRows $Database "SELECT Condition, Ordering FROM ControlEvent WHERE Dialog_ = 'ExitDialog' AND Control_ = 'Finish' AND Event = 'DoAction' AND Argument = '$LaunchActionName'"
    if ($controlEvent.Count -eq 0) {
        $failures += 'ControlEvent DoAction do ExitDialog ausente'
    } elseif ($controlEvent[0][0] -ne $LaunchCondition) {
        $failures += "ControlEvent com condicao inesperada: $($controlEvent[0][0])"
    }

    if ($failures.Count -gt 0) {
        throw "MSI sem o patch de launch:`n  - " + ($failures -join "`n  - ")
    }
}

$msiPath = Resolve-MsiPath -Directory $MsiDirectory
$launchTarget = '"[' + $LaunchDirectory + ']' + $LauncherFileName + '"'

$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $null

try {
    # 0 = somente leitura, 1 = transacional.
    $openMode = if ($VerifyOnly) { 0 } else { 1 }
    $database = Invoke-ComMember $installer 'OpenDatabase' 'InvokeMethod' @($msiPath, $openMode)

    if ($VerifyOnly) {
        Assert-MsiPatched -Database $database -Target $launchTarget
        Write-Output "OK: $([System.IO.Path]::GetFileName($msiPath)) tem o launch no ExitDialog."
    } else {
        Assert-Preconditions -Database $database

        Set-MsiProperty -Database $database -Installer $installer -Name $CheckboxTextProperty -Value $CheckboxText
        Set-MsiProperty -Database $database -Installer $installer -Name $CheckboxProperty -Value '1'
        Set-MsiLaunchAction -Database $database -Installer $installer -Target $launchTarget
        Set-MsiLaunchControlEvent -Database $database -Installer $installer

        Invoke-ComMember $database 'Commit' 'InvokeMethod' $null | Out-Null
        Assert-MsiPatched -Database $database -Target $launchTarget

        Write-Output "Patch aplicado: $([System.IO.Path]::GetFileName($msiPath))"
        Write-Output "  checkbox: $CheckboxText"
        Write-Output "  launch:   $launchTarget"
    }
} catch {
    Write-Error $_
    exit 1
} finally {
    # Sem liberar os objetos COM o arquivo .msi fica travado para os passos seguintes.
    if ($null -ne $database) {
        [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) | Out-Null
    }
    [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer) | Out-Null
    [System.GC]::Collect()
    [System.GC]::WaitForPendingFinalizers()
}
