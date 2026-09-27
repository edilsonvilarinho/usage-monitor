<#
.SYNOPSIS
    Mede o contexto que o Claude Code carrega por sessão neste repositório. Somente leitura.

.DESCRIPTION
    Relata linhas, caracteres e tokens ESTIMADOS (caracteres / 3,5 — aproximação para texto
    misto PT/EN com código; não é a contagem do tokenizador) de:
      - CLAUDE.md do projeto, com o peso de cada seção ## e ###;
      - ~/.claude/CLAUDE.md (instruções globais);
      - memória do projeto (MEMORY.md + arquivos), com ponteiros quebrados e arquivos órfãos;
      - .claude/skills/*/SKILL.md, separando a description (carregada em toda sessão) do corpo
        (carregado só quando a skill é invocada).
    Nada é escrito em disco.
#>
param(
    [string]$RepoPath = (Get-Location).Path,
    [string]$ClaudeHome = (Join-Path $HOME ".claude"),
    [int]$TopSections = 25
)

$ErrorActionPreference = "Stop"
$CharsPerToken = 3.5

function Get-Estimate([int]$chars) { [int][Math]::Round($chars / $CharsPerToken) }

function Measure-File([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    $text = [System.IO.File]::ReadAllText($path)
    [pscustomobject]@{
        Path   = $path
        Lines  = ($text -split "`n").Count
        Chars  = $text.Length
        Tokens = Get-Estimate $text.Length
    }
}

$repo = (Resolve-Path -LiteralPath $RepoPath).Path
# O Claude Code nomeia a pasta do projeto trocando ':' e '\' por '-' no caminho absoluto.
$projectKey = $repo -replace '[:\\/]', '-'
$memoryDir = Join-Path $ClaudeHome "projects\$projectKey\memory"

Write-Output "== Resumo (tokens = caracteres / $CharsPerToken, ESTIMATIVA) =="
$summary = @()
$projectClaude = Measure-File (Join-Path $repo "CLAUDE.md")
$globalClaude = Measure-File (Join-Path $ClaudeHome "CLAUDE.md")
if ($projectClaude) { $summary += [pscustomobject]@{ Alvo = "CLAUDE.md do projeto (toda sessão)"; Linhas = $projectClaude.Lines; Chars = $projectClaude.Chars; Tokens = $projectClaude.Tokens } }
if ($globalClaude) { $summary += [pscustomobject]@{ Alvo = "CLAUDE.md global (toda sessão)"; Linhas = $globalClaude.Lines; Chars = $globalClaude.Chars; Tokens = $globalClaude.Tokens } }

$memoryIndex = Measure-File (Join-Path $memoryDir "MEMORY.md")
$memoryFiles = @()
if (Test-Path -LiteralPath $memoryDir) {
    $memoryFiles = @(Get-ChildItem -LiteralPath $memoryDir -Filter *.md | Where-Object { $_.Name -ne "MEMORY.md" } |
        ForEach-Object { Measure-File $_.FullName })
}
if ($memoryIndex) { $summary += [pscustomobject]@{ Alvo = "MEMORY.md (toda sessão)"; Linhas = $memoryIndex.Lines; Chars = $memoryIndex.Chars; Tokens = $memoryIndex.Tokens } }
$memoryChars = [int]($memoryFiles | Measure-Object -Property Chars -Sum).Sum
if ($memoryFiles.Count -gt 0) { $summary += [pscustomobject]@{ Alvo = "Arquivos de memória ($($memoryFiles.Count), sob demanda)"; Linhas = [int]($memoryFiles | Measure-Object -Property Lines -Sum).Sum; Chars = $memoryChars; Tokens = Get-Estimate $memoryChars } }

$skills = @()
$skillRoot = Join-Path $repo ".claude\skills"
if (Test-Path -LiteralPath $skillRoot) {
    foreach ($file in Get-ChildItem -LiteralPath $skillRoot -Recurse -Filter SKILL.md) {
        $text = [System.IO.File]::ReadAllText($file.FullName)
        $description = ""
        $match = [regex]::Match($text, '(?ms)\A---\s*\r?\n(.*?)\r?\n---')
        if ($match.Success) {
            $descMatch = [regex]::Match($match.Groups[1].Value, '(?m)^description:\s*(.*)$')
            if ($descMatch.Success) { $description = $descMatch.Groups[1].Value }
        }
        $skills += [pscustomobject]@{
            Skill       = $file.Directory.Name
            DescChars   = $description.Length
            DescTokens  = Get-Estimate $description.Length
            BodyChars   = $text.Length
            BodyTokens  = Get-Estimate $text.Length
        }
    }
    $descChars = [int]($skills | Measure-Object -Property DescChars -Sum).Sum
    $summary += [pscustomobject]@{ Alvo = "Descriptions das skills do repo (toda sessão)"; Linhas = $skills.Count; Chars = $descChars; Tokens = Get-Estimate $descChars }
}
$summary | Format-Table -AutoSize | Out-String -Width 200 | Write-Output

if ($projectClaude) {
    Write-Output "== CLAUDE.md do projeto: seções mais pesadas (top $TopSections) =="
    $lines = [System.IO.File]::ReadAllLines($projectClaude.Path)
    $sections = @()
    $current = $null
    $inFence = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^\s*```') { $inFence = -not $inFence }
        if (-not $inFence -and $line -match '^(#{2,3})\s+(.*)$') {
            if ($current) { $sections += $current }
            $current = [pscustomobject]@{ Linha = $i + 1; Nivel = $Matches[1]; Titulo = $Matches[2]; Linhas = 0; Chars = 0 }
        }
        if ($current) { $current.Linhas++; $current.Chars += $line.Length + 1 }
    }
    if ($current) { $sections += $current }
    # Bullets de primeiro nível dentro das seções também pesam: um único "- **X**" chega a milhares de chars.
    $sections | Sort-Object Chars -Descending | Select-Object -First $TopSections |
        Select-Object Linha, Nivel, @{ n = "Titulo"; e = { if ($_.Titulo.Length -gt 70) { $_.Titulo.Substring(0, 70) + "…" } else { $_.Titulo } } }, Linhas, Chars, @{ n = "Tokens"; e = { Get-Estimate $_.Chars } } |
        Format-Table -AutoSize | Out-String -Width 200 | Write-Output

    Write-Output "== CLAUDE.md do projeto: bullets de primeiro nível acima de 1500 chars =="
    $bullets = @()
    $bullet = $null
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $isTopBullet = $line -match '^- '
        $isBoundary = $isTopBullet -or $line -match '^#{1,6}\s' -or $line.Trim() -eq ''
        if ($isBoundary -and $bullet) { $bullets += $bullet; $bullet = $null }
        if ($isTopBullet) {
            $label = [regex]::Match($line, '\*\*(.+?)\*\*').Groups[1].Value
            if (-not $label) { $label = $line.Substring(0, [Math]::Min(60, $line.Length)) }
            $bullet = [pscustomobject]@{ Linha = $i + 1; Rotulo = $label; Chars = 0 }
        }
        if ($bullet) { $bullet.Chars += $line.Length + 1 }
    }
    if ($bullet) { $bullets += $bullet }
    $bullets | Where-Object { $_.Chars -gt 1500 } | Sort-Object Chars -Descending |
        Select-Object Linha, @{ n = "Rotulo"; e = { if ($_.Rotulo.Length -gt 70) { $_.Rotulo.Substring(0, 70) + "…" } else { $_.Rotulo } } }, Chars, @{ n = "Tokens"; e = { Get-Estimate $_.Chars } } |
        Format-Table -AutoSize | Out-String -Width 200 | Write-Output

    Write-Output "== CLAUDE.md do projeto: planos em docs/planos já citados (destino natural da narrativa) =="
    $text = [System.IO.File]::ReadAllText($projectClaude.Path)
    $linked = [regex]::Matches($text, 'docs/planos/([\w\-\.]+\.md)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    foreach ($plan in $linked) {
        $exists = Test-Path -LiteralPath (Join-Path $repo "docs\planos\$plan")
        Write-Output ("  {0} {1}" -f ($(if ($exists) { "[ok]     " } else { "[AUSENTE]" })), $plan)
    }
    Write-Output ""
}

Write-Output "== Memória do projeto =="
Write-Output "  Pasta: $memoryDir"
if (-not (Test-Path -LiteralPath $memoryDir)) {
    Write-Output "  (não existe)"
} else {
    $memoryFiles | Sort-Object Chars -Descending |
        Select-Object @{ n = "Arquivo"; e = { Split-Path $_.Path -Leaf } }, Lines, Chars, Tokens |
        Format-Table -AutoSize | Out-String -Width 200 | Write-Output
    if ($memoryIndex) {
        $indexText = [System.IO.File]::ReadAllText($memoryIndex.Path)
        $pointers = [regex]::Matches($indexText, '\]\(([^)]+\.md)\)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
        $names = $memoryFiles | ForEach-Object { Split-Path $_.Path -Leaf }
        $broken = $pointers | Where-Object { $names -notcontains $_ }
        $orphans = $names | Where-Object { $pointers -notcontains $_ }
        Write-Output ("  Ponteiros no MEMORY.md sem arquivo: {0}" -f ($(if ($broken) { $broken -join ", " } else { "nenhum" })))
        Write-Output ("  Arquivos sem ponteiro no MEMORY.md: {0}" -f ($(if ($orphans) { $orphans -join ", " } else { "nenhum" })))
        $issueRefs = foreach ($file in $memoryFiles) {
            $body = [System.IO.File]::ReadAllText($file.Path)
            $refs = [regex]::Matches($body, '#(\d{2,4})\b') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
            if ($refs) { "{0}: #{1}" -f (Split-Path $file.Path -Leaf), ($refs -join ", #") }
        }
        Write-Output "  Issues citadas (conferir estado com gh issue view N --json state):"
        if ($issueRefs) { $issueRefs | ForEach-Object { Write-Output "    $_" } } else { Write-Output "    nenhuma" }
    }
    Write-Output ""
}

if ($skills.Count -gt 0) {
    Write-Output "== Skills do repositório =="
    $skills | Sort-Object BodyChars -Descending | Format-Table -AutoSize | Out-String -Width 200 | Write-Output
}
