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
  HUD placement to seed: floating-idle, docked-<top|bottom|left|right>-idle, or docked-top-hover.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$DataDir,
    [Parameter(Mandatory)][string]$Version,
    [ValidateSet('floating-idle', 'docked-top-idle', 'docked-bottom-idle', 'docked-left-idle', 'docked-right-idle', 'docked-top-hover')]
    [string]$Scenario = 'floating-idle'
)

$ErrorActionPreference = 'Stop'

# Scenario -> HUD placement. Formats: HudWindowPreferences.kt (hudFloating :178-191 as 'true'/'false';
# hudEdge :73 = HudEdge name TOP|BOTTOM|LEFT|RIGHT; hudEdgeOffset :74 = Float text; hudAutoRetract :122-131).
$placements = @{
    'floating-idle'      = @{ floating = 'true';  edge = 'TOP' }
    'docked-top-idle'    = @{ floating = 'false'; edge = 'TOP' }
    'docked-bottom-idle' = @{ floating = 'false'; edge = 'BOTTOM' }
    'docked-left-idle'   = @{ floating = 'false'; edge = 'LEFT' }
    'docked-right-idle'  = @{ floating = 'false'; edge = 'RIGHT' }
    'docked-top-hover'   = @{ floating = 'false'; edge = 'TOP' }
}
$placement = $placements[$Scenario]

New-Item -ItemType Directory -Force -Path $DataDir | Out-Null
# Canonical form: the app hashes File.canonicalFile.path (DataRootResolution.kt:38).
$root = [System.IO.Path]::GetFullPath($DataDir).TrimEnd('\')

# --- dashboard-cache.json (fake CODEX account, two PERCENTAGE quotas) ---------------
# DashboardCacheDto (src/commonMain/.../dto/DashboardCacheDto.kt): savedAtEpochMillis + entries.
# targetKey = UsageTargetKey(CODEX, null).storageKey = "CODEX" (UsageTargetKey.kt:32-37).
# Enums are written by name (DashboardCacheMapper.kt:40-50): periodType INTERVAL|WEEKLY, unit PERCENTAGE.
$nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$hourMs = 3600000L
$cache = [ordered]@{
    savedAtEpochMillis = $nowMs
    entries = @(
        [ordered]@{
            targetKey = 'CODEX'
            source = 'CODEX'
            apiName = 'Codex'
            accountEmail = 'example@example.invalid'
            profileLabel = 'Example account'
            notices = @()
            planLabel = 'Example'
            fetchedAtEpochMillis = $nowMs
            quotas = @(
                [ordered]@{
                    label = 'Example session'; used = 35L; total = 100L
                    periodEndAtEpochMillis = $nowMs + 5 * $hourMs; hasKnownResetAt = $true
                    periodType = 'INTERVAL'; unit = 'PERCENTAGE'
                    rawUsed = 35L; rawTotal = 100L; currencyCode = 'USD'
                    periodStartAtEpochMillis = $nowMs - 1 * $hourMs; blockReason = $null
                }
                [ordered]@{
                    label = 'Example weekly'; used = 60L; total = 100L
                    periodEndAtEpochMillis = $nowMs + 7 * 24 * $hourMs; hasKnownResetAt = $true
                    periodType = 'WEEKLY'; unit = 'PERCENTAGE'
                    rawUsed = 60L; rawTotal = 100L; currencyCode = 'USD'
                    periodStartAtEpochMillis = $nowMs - 24 * $hourMs; blockReason = $null
                }
            )
        }
    )
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
# Version strings are stored without the tag's 'v' prefix.
$bareVersion = $Version.TrimStart('v')
$values = [ordered]@{
    hudFloating               = $placement.floating
    hudAutoRetract            = 'true'
    hudEdge                   = $placement.edge
    hudEdgeOffset             = '0.5'
    enabledApis              = 'CODEX'
    releaseNotesSeenVersion   = $bareVersion
    onboardingCompletedVersion = $bareVersion
    startupTipsEnabled        = 'false'
    reducedMotion             = 'false'
}
foreach ($name in $values.Keys) {
    # Value strings use the same simple encoding (toWindowsValueString): 'CODEX' -> '/C/O/D/E/X'.
    $regName = ConvertTo-JavaPrefsName $name
    $regValue = ConvertTo-JavaPrefsName ([string]$values[$name])
    New-ItemProperty -Path $keyPath -Name $regName -Value $regValue -PropertyType String -Force | Out-Null
}

[pscustomobject]@{ root = $root; node = $node; keyPath = $keyPath } | ConvertTo-Json
