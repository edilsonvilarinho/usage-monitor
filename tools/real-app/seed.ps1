#Requires -Version 7.0
<#
.SYNOPSIS
  Seeds a --user-data-dir for the real app: dashboard-cache.json and the java.util.prefs registry node.

.DESCRIPTION
  Writes fake data only (no credentials, no network). The preferences node name is
  com.usagemonitor.data.<16 hex of SHA-256 of the canonical root path> (AppDataDirectory.kt:88-103).
  Key and value names are escaped the way java.util.prefs WindowsPreferences does it
  (toWindowsName / toWindowsValueString): '/' before each capital letter, '\' -> '//', '/' -> '\'.

.PARAMETER DataDir
  Folder passed to --user-data-dir. Created if missing.

.PARAMETER Version
  Release version written into releaseNotesSeenVersion and onboardingCompletedVersion.

.PARAMETER Scenario
  HUD placement to seed: floating-idle, floating-drag (same seed), docked-<top|bottom|left|right>-idle,
  docked-top-hover, or docked-top-hole-idle / docked-top-hole-hover (three accounts, two minimized).
  AI pilot scenarios: pilot-gear-floating (three accounts, floating), pilot-two-accounts (three accounts,
  docked top), pilot-seven-left (seven accounts, docked left), pilot-weekly-monthly (OpenCode Go with
  interval, weekly and monthly windows, plus Codex weekly, docked top).
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$DataDir,
    [Parameter(Mandatory)][string]$Version,
    [ValidateSet('floating-idle', 'floating-drag', 'docked-top-idle', 'docked-bottom-idle', 'docked-left-idle', 'docked-right-idle', 'docked-top-hover', 'docked-top-hole-idle', 'docked-top-hole-hover', 'pilot-gear-floating', 'pilot-two-accounts', 'pilot-seven-left', 'pilot-weekly-monthly')]
    [string]$Scenario = 'floating-idle'
)

$ErrorActionPreference = 'Stop'

# Scenario -> HUD placement. Formats: HudWindowPreferences.kt (hudFloating :178-191 as 'true'/'false';
# hudEdge :73 = HudEdge name TOP|BOTTOM|LEFT|RIGHT; hudEdgeOffset :74 = Float text; hudAutoRetract :122-131).
$placements = @{
    'floating-idle'      = @{ floating = 'true';  edge = 'TOP' }
    'floating-drag'      = @{ floating = 'true';  edge = 'TOP' }
    'docked-top-idle'    = @{ floating = 'false'; edge = 'TOP' }
    'docked-bottom-idle' = @{ floating = 'false'; edge = 'BOTTOM' }
    'docked-left-idle'   = @{ floating = 'false'; edge = 'LEFT' }
    'docked-right-idle'  = @{ floating = 'false'; edge = 'RIGHT' }
    'docked-top-hover'   = @{ floating = 'false'; edge = 'TOP' }
    'docked-top-hole-idle'  = @{ floating = 'false'; edge = 'TOP' }
    'docked-top-hole-hover' = @{ floating = 'false'; edge = 'TOP' }
    'pilot-gear-floating'   = @{ floating = 'true';  edge = 'TOP' }
    'pilot-two-accounts'    = @{ floating = 'false'; edge = 'TOP' }
    'pilot-seven-left'      = @{ floating = 'false'; edge = 'LEFT' }
    'pilot-weekly-monthly'  = @{ floating = 'false'; edge = 'TOP' }
}
$placement = $placements[$Scenario]
# Hole scenarios: three accounts, two minimized, auto-retract off, so the strip stays visible.
$isHole = $Scenario -like 'docked-top-hole-*'
# Pilot scenarios: same no-credentials race as the hole scenarios, so they share the refresh delay below.
$isPilot = $Scenario -like 'pilot-*'
# Three accounts (CODEX, CURSOR, ANTIGRAVITY): the hole scenarios and two pilot scenarios.
$hasThreeAccounts = $isHole -or $Scenario -in @('pilot-gear-floating', 'pilot-two-accounts')
# Auto-retract off keeps the strip visible for the pilot; weekly-monthly keeps the default (on).
$autoRetract = if ($isHole -or $Scenario -in @('pilot-gear-floating', 'pilot-two-accounts', 'pilot-seven-left', 'pilot-weekly-monthly')) { 'false' } else { 'true' }
if ($Scenario -eq 'pilot-seven-left') { $enabledApis = 'ANTHROPIC,MINIMAX,CODEX,DEEPSEEK,OPENCODE_GO,CURSOR,ANTIGRAVITY' }
elseif ($Scenario -eq 'pilot-weekly-monthly') { $enabledApis = 'OPENCODE_GO,CODEX' }
elseif ($hasThreeAccounts) { $enabledApis = 'CODEX,CURSOR,ANTIGRAVITY' }
else { $enabledApis = 'CODEX' }

New-Item -ItemType Directory -Force -Path $DataDir | Out-Null
# Canonical form: the app hashes File.canonicalFile.path (DataRootResolution.kt:38).
$root = [System.IO.Path]::GetFullPath($DataDir).TrimEnd('\')

# --- dashboard-cache.json (fake accounts, two PERCENTAGE quotas each, one for ANTIGRAVITY) ---
# DashboardCacheDto (src/commonMain/.../dto/DashboardCacheDto.kt): savedAtEpochMillis + entries.
# targetKey = UsageTargetKey(source, null).storageKey = the enum name (UsageTargetKey.kt:32-37).
# Enums are written by name (DashboardCacheMapper.kt:40-50): periodType INTERVAL|WEEKLY|MONTHLY, unit PERCENTAGE.
$nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$hourMs = 3600000L

# One quota like the CODEX ones: total 100, PERCENTAGE, known reset.
function New-SeedQuota([string]$label, [long]$used, [string]$periodType, [long]$periodEndMs, [long]$periodStartMs) {
    [ordered]@{
        label = $label; used = $used; total = 100L
        periodEndAtEpochMillis = $periodEndMs; hasKnownResetAt = $true
        periodType = $periodType; unit = 'PERCENTAGE'
        rawUsed = $used; rawTotal = 100L; currencyCode = 'USD'
        periodStartAtEpochMillis = $periodStartMs; blockReason = $null
    }
}

# One fake account; the caller passes its quotas from New-SeedQuota.
function New-SeedEntry([string]$source, [string]$apiName, [object[]]$quotas) {
    [ordered]@{
        targetKey = $source
        source = $source
        apiName = $apiName
        accountEmail = 'example@example.invalid'
        profileLabel = 'Example account'
        notices = @()
        planLabel = 'Example'
        fetchedAtEpochMillis = $nowMs
        quotas = @($quotas)
    }
}

if ($Scenario -eq 'pilot-weekly-monthly') {
    # OpenCode Go labels and periods from OpenCodeGoQuotaLabels.kt (Go 5h / Go semanal / Go mensal); resets at 2 h, 3 days, 12 days.
    $accounts = @(
        New-SeedEntry 'OPENCODE_GO' 'OpenCode Go' @(
            (New-SeedQuota 'Go 5h' 40L 'INTERVAL' ($nowMs + 2 * $hourMs) ($nowMs - 3 * $hourMs)),
            (New-SeedQuota 'Go semanal' 55L 'WEEKLY' ($nowMs + 3 * 24 * $hourMs) ($nowMs - 4 * 24 * $hourMs)),
            (New-SeedQuota 'Go mensal' 30L 'MONTHLY' ($nowMs + 12 * 24 * $hourMs) ($nowMs - 18 * 24 * $hourMs))
        )
        New-SeedEntry 'CODEX' 'Codex' @(
            (New-SeedQuota 'Example weekly' 60L 'WEEKLY' ($nowMs + 3 * 24 * $hourMs) ($nowMs - 4 * 24 * $hourMs))
        )
    )
}
elseif ($Scenario -eq 'pilot-seven-left') {
    # Seven sources (names as in ApiSource.kt), one INTERVAL quota each with a different used value.
    $sevenSources = @(
        @('ANTHROPIC', 'Anthropic'),
        @('MINIMAX', 'MiniMax'),
        @('CODEX', 'Codex'),
        @('DEEPSEEK', 'DeepSeek'),
        @('OPENCODE_GO', 'OpenCode Go'),
        @('CURSOR', 'Cursor'),
        @('ANTIGRAVITY', 'Antigravity')
    )
    $accounts = @()
    $index = 0
    foreach ($pair in $sevenSources) {
        $index++
        $accounts += (New-SeedEntry $pair[0] $pair[1] @(
            (New-SeedQuota 'Example session' (10L * $index) 'INTERVAL' ($nowMs + 5 * $hourMs) ($nowMs - 1 * $hourMs))
        ))
    }
}
else {
    $accounts = @(
        New-SeedEntry 'CODEX' 'Codex' @(
            (New-SeedQuota 'Example session' 35L 'INTERVAL' ($nowMs + 5 * $hourMs) ($nowMs - 1 * $hourMs)),
            (New-SeedQuota 'Example weekly' 60L 'WEEKLY' ($nowMs + 7 * 24 * $hourMs) ($nowMs - 24 * $hourMs))
        )
    )
    if ($hasThreeAccounts) {
        $accounts += New-SeedEntry 'CURSOR' 'Cursor' @(
            (New-SeedQuota 'Example session' 20L 'INTERVAL' ($nowMs + 5 * $hourMs) ($nowMs - 1 * $hourMs)),
            (New-SeedQuota 'Example weekly' 45L 'WEEKLY' ($nowMs + 7 * 24 * $hourMs) ($nowMs - 24 * $hourMs))
        )
        $accounts += New-SeedEntry 'ANTIGRAVITY' 'Antigravity' @(
            (New-SeedQuota 'Example session' 50L 'INTERVAL' ($nowMs + 5 * $hourMs) ($nowMs - 1 * $hourMs))
        )
    }
}
$cache = [ordered]@{
    savedAtEpochMillis = $nowMs
    entries = @($accounts)
}
$cache | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $DataDir 'dashboard-cache.json') -Encoding utf8

# --- java.util.prefs node (Windows registry, HKCU\Software\JavaSoft\Prefs) -----------
function ConvertTo-JavaPrefsName([string]$text) {
    # Simple encoding of WindowsPreferences.toWindowsName, valid for ASCII names only.
    $sb = [System.Text.StringBuilder]::new()
    foreach ($ch in $text.ToCharArray()) {
        if ([int]$ch -lt 0x20 -or [int]$ch -gt 0x7f) { throw "non-ASCII name needs altBase64: '$text'" }
        if ($ch -eq '\') { [void]$sb.Append('//') }
        elseif ($ch -eq '/') { [void]$sb.Append('\') }
        elseif ($ch -ge 'A' -and $ch -le 'Z') { [void]$sb.Append('/').Append($ch) }
        else { [void]$sb.Append($ch) }
    }
    $sb.ToString()
}

$sha = [System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($root))
$hash = -join ($sha | Select-Object -First 8 | ForEach-Object { $_.ToString('x2') })
$node = "com.usagemonitor.data.$hash"
$keyPath = "HKCU:\Software\JavaSoft\Prefs\$node"
New-Item -Path $keyPath -Force | Out-Null

# enabledApis: comma-joined ApiSource.name, read by split(",") + valueOf (AppPreferenceKeys.kt:44-53; writer :55-64).
# hudMinimizedAccounts: same comma-joined storage keys (HudMinimizedAccounts.kt:17-33, HudWindowPreferences.kt:169).
# Version strings are stored without the tag's 'v' prefix.
$bareVersion = $Version.TrimStart('v')
$values = [ordered]@{
    hudFloating               = $placement.floating
    hudAutoRetract            = $autoRetract
    hudEdge                   = $placement.edge
    hudEdgeOffset             = '0.5'
    enabledApis               = $enabledApis
    releaseNotesSeenVersion   = $bareVersion
    onboardingCompletedVersion = $bareVersion
    startupTipsEnabled        = 'false'
    reducedMotion             = 'false'
}
if ($isHole) { $values['hudMinimizedAccounts'] = 'CURSOR,ANTIGRAVITY' }
# Hole: no credentials, so the first collection would race the cache; a refresh due in 4 minutes keeps it.
# Pilot: an AI script may run longer than 4 minutes, so the refresh is due in 55 minutes.
if ($isHole) { $values['nextRefreshAtMillis'] = [string]($nowMs + 4 * 60000L) }
if ($isPilot) { $values['nextRefreshAtMillis'] = [string]($nowMs + 55 * 60000L) }
foreach ($name in $values.Keys) {
    # Value strings use the same simple encoding (toWindowsValueString): 'CODEX' -> '/C/O/D/E/X'.
    $regName = ConvertTo-JavaPrefsName $name
    $regValue = ConvertTo-JavaPrefsName ([string]$values[$name])
    New-ItemProperty -Path $keyPath -Name $regName -Value $regValue -PropertyType String -Force | Out-Null
}

[pscustomobject]@{ root = $root; node = $node; keyPath = $keyPath } | ConvertTo-Json
