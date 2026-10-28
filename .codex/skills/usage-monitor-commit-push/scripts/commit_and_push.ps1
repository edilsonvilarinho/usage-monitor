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

if ($Files.Count -eq 0) {
    throw "At least one file must be provided."
}

# A identidade temporaria vai por `-c` no proprio commit, nunca gravada na
# config (#498): numa worktree o `.git` e um arquivo e `--file .git/config` nao
# existe, e uma config gravada sobra se o processo morrer antes do restauro.
$identityArguments = @()
if (-not [string]::IsNullOrWhiteSpace($TempUserName)) {
    $identityArguments += @("-c", "user.name=$TempUserName")
}
if (-not [string]::IsNullOrWhiteSpace($TempUserEmail)) {
    $identityArguments += @("-c", "user.email=$TempUserEmail")
}

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

Write-Host ""
Write-Host "== Creating commit =="
$commitResult = Invoke-Git -Arguments ($identityArguments + @("commit", "-m", $Message))
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
