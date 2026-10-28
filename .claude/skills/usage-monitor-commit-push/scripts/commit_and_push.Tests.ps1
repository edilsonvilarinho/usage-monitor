# Testes do commit_and_push.ps1 (#498). Rodar com:
#   pwsh -NoProfile -Command "Invoke-Pester -Path .claude/skills/usage-monitor-commit-push/scripts -Output Detailed"
#
# Repositorio de verdade num diretorio temporario: o que quebrou foi a interacao
# com o git (worktree tem `.git` como arquivo), e um dublê de git nao a reproduz.

BeforeAll {
    $script:CommitScript = Join-Path $PSScriptRoot 'commit_and_push.ps1'

    function Invoke-TestGit {
        param([string]$Path, [string[]]$Arguments)
        $output = & git -C $Path @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') falhou: $output" }
        return ($output -join "`n").Trim()
    }

    function New-TestRepository {
        $root = Join-Path ([System.IO.Path]::GetTempPath()) ("commit-push-test-" + [guid]::NewGuid().ToString('N'))
        $main = Join-Path $root 'repo'
        $worktree = Join-Path $root 'worktree'
        New-Item -ItemType Directory -Path $main | Out-Null
        Invoke-TestGit $main @('init', '-q', '-b', 'main') | Out-Null
        Invoke-TestGit $main @('config', 'user.name', 'Original Owner') | Out-Null
        Invoke-TestGit $main @('config', 'user.email', 'owner@example.test') | Out-Null
        Set-Content -LiteralPath (Join-Path $main 'readme.txt') -Value 'inicio'
        Invoke-TestGit $main @('add', 'readme.txt') | Out-Null
        Invoke-TestGit $main @('commit', '-q', '-m', 'inicio') | Out-Null
        Invoke-TestGit $main @('worktree', 'add', '-q', $worktree, '-b', 'feature') | Out-Null
        return @{ Root = $root; Main = $main; Worktree = $worktree }
    }

    function Invoke-CommitScript {
        param([string]$RepoPath, [hashtable]$Extra = @{})
        Set-Content -LiteralPath (Join-Path $RepoPath 'change.txt') -Value ([guid]::NewGuid().ToString())
        $parameters = @{ RepoPath = $RepoPath; Message = 'test: change'; Files = @('change.txt'); SkipPush = $true } + $Extra
        & $script:CommitScript @parameters 6>$null | Out-Null
    }
}

Describe 'commit_and_push.ps1' {
    BeforeEach {
        $script:repo = New-TestRepository
    }

    AfterEach {
        Remove-Item -LiteralPath $script:repo.Root -Recurse -Force -ErrorAction SilentlyContinue
    }

    It 'commita numa worktree com a identidade temporaria' {
        Invoke-CommitScript $script:repo.Worktree @{ TempUserName = 'claude'; TempUserEmail = 'claude@anthropic.com' }

        Invoke-TestGit $script:repo.Worktree @('log', '-1', '--format=%an <%ae> %s') | Should -Be 'claude <claude@anthropic.com> test: change'
    }

    It 'nao muda a identidade configurada do repositorio, nem a partir da worktree' {
        Invoke-CommitScript $script:repo.Worktree @{ TempUserName = 'claude'; TempUserEmail = 'claude@anthropic.com' }
        Invoke-CommitScript $script:repo.Main @{ TempUserName = 'claude'; TempUserEmail = 'claude@anthropic.com' }

        Invoke-TestGit $script:repo.Main @('config', '--local', 'user.name') | Should -Be 'Original Owner'
        Invoke-TestGit $script:repo.Main @('config', '--local', 'user.email') | Should -Be 'owner@example.test'
    }

    It 'commita no checkout principal com a identidade temporaria' {
        Invoke-CommitScript $script:repo.Main @{ TempUserName = 'claude'; TempUserEmail = 'claude@anthropic.com' }

        Invoke-TestGit $script:repo.Main @('log', '-1', '--format=%an <%ae>') | Should -Be 'claude <claude@anthropic.com>'
    }

    It 'sem identidade temporaria usa a configurada' {
        Invoke-CommitScript $script:repo.Main

        Invoke-TestGit $script:repo.Main @('log', '-1', '--format=%an <%ae>') | Should -Be 'Original Owner <owner@example.test>'
    }
}
