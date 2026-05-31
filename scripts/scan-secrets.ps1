param(
    [ValidateSet("WorkingTree", "Staged", "Head")]
    [string]$Mode = "WorkingTree"
)

$ErrorActionPreference = "Stop"

$secretPatterns = @(
    @{ Name = "Ark API key"; Regex = "ark-[A-Za-z0-9_-]{12,}" },
    @{ Name = "OpenAI API key"; Regex = "sk-(proj-)?[A-Za-z0-9_-]{20,}" },
    @{ Name = "GitHub token"; Regex = "gh[pousr]_[A-Za-z0-9_]{20,}" },
    @{ Name = "Suspicious secret assignment"; Regex = "(?i)(api[_-]?key|token|secret|password)\s*[:=]\s*[`"']?[^`"'\s]{8,}" }
)

$skipPathPattern = '(^|/)(\.git|\.gradle|build|app/build|\.idea|tmp_tools)(/|$)'
$findings = New-Object System.Collections.Generic.List[object]

function Add-Finding {
    param(
        [string]$Type,
        [string]$File,
        [int]$Line
    )
    $findings.Add([pscustomobject]@{
        Type = $Type
        File = $File
        Line = $Line
    })
}

function Test-SourceLine {
    param(
        [string]$File,
        [int]$LineNumber,
        [string]$Line
    )

    foreach ($pattern in $secretPatterns) {
        if ($Line -notmatch $pattern.Regex) {
            continue
        }

        if ($pattern.Name -eq "Suspicious secret assignment") {
            if ($Line -match "BuildConfig\.|buildConfigString\(|localProperties\.getProperty\(") {
                continue
            }
            if ($Line -match "(?i)placeholder|example|sample|这里填写|你的|your") {
                continue
            }
        }

        Add-Finding -Type $pattern.Name -File $File -Line $LineNumber
    }
}

function Test-Lines {
    param(
        [string]$File,
        [string[]]$Lines
    )

    if ($File -replace "\\", "/" -match $skipPathPattern) {
        return
    }

    for ($i = 0; $i -lt $Lines.Count; $i++) {
        Test-SourceLine -File $File -LineNumber ($i + 1) -Line $Lines[$i]
    }
}

function Test-TrackedLocalProperties {
    $trackedLocalProperties = git ls-files local.properties
    if ($trackedLocalProperties) {
        Add-Finding -Type "local.properties is tracked" -File "local.properties" -Line 1
    }
}

function Get-WorkingTreeFiles {
    git ls-files --cached --others --exclude-standard
}

function Get-StagedFiles {
    git diff --cached --name-only --diff-filter=ACMR
}

Test-TrackedLocalProperties

if ($Mode -eq "WorkingTree") {
    foreach ($file in Get-WorkingTreeFiles) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
            continue
        }
        try {
            Test-Lines -File $file -Lines (Get-Content -LiteralPath $file -ErrorAction Stop)
        } catch {
            continue
        }
    }
} elseif ($Mode -eq "Staged") {
    foreach ($file in Get-StagedFiles) {
        try {
            $content = git show ":$file" 2>$null
            if ($LASTEXITCODE -ne 0) {
                continue
            }
            Test-Lines -File $file -Lines $content
        } catch {
            continue
        }
    }
} else {
    foreach ($file in git ls-tree -r --name-only HEAD) {
        try {
            $content = git show "HEAD:$file" 2>$null
            if ($LASTEXITCODE -ne 0) {
                continue
            }
            Test-Lines -File $file -Lines $content
        } catch {
            continue
        }
    }
}

if ($findings.Count -gt 0) {
    Write-Host ""
    Write-Host "[secret-scan] Blocked: possible private information found." -ForegroundColor Red
    Write-Host "[secret-scan] Values are intentionally hidden. Check these locations:" -ForegroundColor Yellow
    $findings | Sort-Object File, Line, Type -Unique | Format-Table -AutoSize
    Write-Host "Move real keys/tokens to local.properties or another ignored local secret store." -ForegroundColor Yellow
    exit 1
}

Write-Host "[secret-scan] OK: no obvious secrets found in $Mode."
exit 0
