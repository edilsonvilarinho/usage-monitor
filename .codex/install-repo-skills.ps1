param(
    [string]$TargetCodexHome,
    [string[]]$SkillNames
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$sourceRoot = Join-Path $repoRoot ".codex\skills"

if (-not (Test-Path -LiteralPath $sourceRoot)) {
    throw "Skill source directory not found: $sourceRoot"
}

if ([string]::IsNullOrWhiteSpace($TargetCodexHome)) {
    if ([string]::IsNullOrWhiteSpace($env:CODEX_HOME)) {
        $TargetCodexHome = Join-Path $HOME ".codex"
    }
    else {
        $TargetCodexHome = $env:CODEX_HOME
    }
}

$targetSkillRoot = Join-Path $TargetCodexHome "skills"
New-Item -ItemType Directory -Force -Path $targetSkillRoot | Out-Null

if ($SkillNames.Count -gt 0) {
    $skillDirectories = foreach ($skillName in $SkillNames) {
        $skillPath = Join-Path $sourceRoot $skillName
        if (-not (Test-Path -LiteralPath $skillPath)) {
            throw "Requested skill not found in repository: $skillName"
        }
        Get-Item -LiteralPath $skillPath
    }
}
else {
    $skillDirectories = Get-ChildItem -LiteralPath $sourceRoot -Directory | Sort-Object Name
}

foreach ($skillDirectory in $skillDirectories) {
    $destination = Join-Path $targetSkillRoot $skillDirectory.Name

    if (Test-Path -LiteralPath $destination) {
        Remove-Item -LiteralPath $destination -Recurse -Force
    }

    Copy-Item -LiteralPath $skillDirectory.FullName -Destination $destination -Recurse -Force
    Write-Host "[OK] Installed $($skillDirectory.Name) -> $destination"
}

Write-Host ""
Write-Host "Target Codex home: $TargetCodexHome"
Write-Host "Installed skills: $($skillDirectories.Name -join ', ')"
Write-Host "If Codex is already running, restart the app or open a new session to reload the installed skills."
