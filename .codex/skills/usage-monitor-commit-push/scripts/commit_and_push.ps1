param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath,

    [Parameter(Mandatory = $true)]
    [string]$Message,

    [Parameter(Mandatory = $true)]
    [string[]]$Files,

    [string]$Remote = "origin",

    [string]$TempUserName,

    [string]$TempUserEmail,

    [switch]$SkipPush,

    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $RepoPath)) {
    throw "Repository path not found: $RepoPath"
}

$resolvedRepoPath = (Resolve-Path -LiteralPath $RepoPath).Path
$safeDirectory = $resolvedRepoPath -replace "\\", "/"

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [switch]$IgnoreErrors
    )

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = "git"
    $startInfo.WorkingDirectory = $resolvedRepoPath
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $allArguments = @("-c", "safe.directory=$safeDirectory", "-C", $resolvedRepoPath) + $Arguments
    $quotedArguments = $allArguments | ForEach-Object {
        if ($_ -match '[\s"]') {
            '"' + $_.Replace('"', '\"') + '"'
        }
        else {
            $_
        }
    }
    $startInfo.Arguments = [string]::Join(" ", $quotedArguments)

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    $null = $process.Start()
    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $exitCode = $process.ExitCode

    $output = @()
    if (-not [string]::IsNullOrWhiteSpace($standardOutput)) {
        $output += $standardOutput.TrimEnd("`r", "`n").Split([Environment]::NewLine)
    }
    if (-not [string]::IsNullOrWhiteSpace($standardError)) {
        $output += $standardError.TrimEnd("`r", "`n").Split([Environment]::NewLine)
    }

    if (-not $IgnoreErrors -and $exitCode -ne 0) {
        throw ($output -join [Environment]::NewLine)
    }

    return @{
        ExitCode = $exitCode
        Output = @($output)
    }
}

function Get-LocalConfigValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Key
    )

    $result = Invoke-Git -Arguments @("config", "--file", ".git/config", "--get", $Key) -IgnoreErrors
    if ($result.ExitCode -ne 0) {
        return $null
    }

    $value = ($result.Output -join [Environment]::NewLine).Trim()
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $null
    }

    return $value
}

function Restore-LocalConfigValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Key,

        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        Invoke-Git -Arguments @("config", "--file", ".git/config", "--unset", $Key) -IgnoreErrors | Out-Null
        return
    }

    Invoke-Git -Arguments @("config", "--file", ".git/config", $Key, $Value) | Out-Null
}

if ($Files.Count -eq 0) {
    throw "At least one file must be provided."
}

$originalName = Get-LocalConfigValue -Key "user.name"
$originalEmail = Get-LocalConfigValue -Key "user.email"
$tempIdentityWasSet = $false

try {
    Write-Host "== Git status =="
    $status = Invoke-Git -Arguments @("status", "--short", "--branch")
    $status.Output

    Write-Host ""
    Write-Host "== Staging files =="
    foreach ($file in $Files) {
        Write-Host $file
    }

    if ($DryRun) {
        Write-Host ""
        Write-Host "Dry run enabled. No changes were staged, committed, or pushed."
        return
    }

    Invoke-Git -Arguments (@("add", "--") + $Files) | Out-Null

    $cachedStat = Invoke-Git -Arguments @("diff", "--cached", "--stat")
    $cachedText = ($cachedStat.Output -join [Environment]::NewLine).Trim()
    if ([string]::IsNullOrWhiteSpace($cachedText)) {
        throw "No staged changes found after staging the requested files."
    }

    Write-Host ""
    Write-Host "== Staged diff stat =="
    Write-Host $cachedText

    if (-not [string]::IsNullOrWhiteSpace($TempUserName)) {
        Invoke-Git -Arguments @("config", "--file", ".git/config", "user.name", $TempUserName) | Out-Null
        $tempIdentityWasSet = $true
    }

    if (-not [string]::IsNullOrWhiteSpace($TempUserEmail)) {
        Invoke-Git -Arguments @("config", "--file", ".git/config", "user.email", $TempUserEmail) | Out-Null
        $tempIdentityWasSet = $true
    }

    Write-Host ""
    Write-Host "== Creating commit =="
    $commitResult = Invoke-Git -Arguments @("commit", "-m", $Message)
    $commitResult.Output

    $branchResult = Invoke-Git -Arguments @("rev-parse", "--abbrev-ref", "HEAD")
    $branchName = ($branchResult.Output | Select-Object -Last 1).Trim()
    if ([string]::IsNullOrWhiteSpace($branchName)) {
        throw "Failed to determine current branch."
    }

    if (-not $SkipPush) {
        Write-Host ""
        Write-Host "== Pushing branch =="
        $pushResult = Invoke-Git -Arguments @("push", $Remote, "HEAD:$branchName")
        $pushResult.Output
    }

    Write-Host ""
    Write-Host "== Final status =="
    $finalStatus = Invoke-Git -Arguments @("status", "--short", "--branch")
    $finalStatus.Output

    Write-Host ""
    Write-Host "== Latest commit =="
    $latestCommit = Invoke-Git -Arguments @("log", "--oneline", "-1")
    $latestCommit.Output
}
finally {
    if ($tempIdentityWasSet) {
        Restore-LocalConfigValue -Key "user.name" -Value $originalName
        Restore-LocalConfigValue -Key "user.email" -Value $originalEmail
    }
}
